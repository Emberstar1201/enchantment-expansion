package com.github.emberstar1201.enchantmentex.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// ========================================================================
// 车万女仆（Touhou Little Maid）软兼容工具
//
// 设计原则：
//   1. 不 import 任何 touhou_little_maid 包下的类，避免成为硬前置；
//   2. 未安装车万女仆时：反射目标类不存在，所有方法返回安全默认值；
//   3. 仅在调用点触发 Class.forName + Method 缓存，对性能影响可控。
//
// 本工具目前提供：
//   - isTouhouMaid(Entity)         —— 通过命名空间识别女仆实体
//   - getMaidOwnerUUID(Entity)     —— 获取女仆主人的 UUID（可用于定位主人）
//   - collectMaidEquipments(Entity)—— 收集女仆身上的主手/副手/装甲槽 ItemStack
//
// 如需读取女仆自定义背包（"护符栏"等），可在此类中继续追加反射方法。
// ========================================================================
public final class TLMSafe {

    private static final String NAMESPACE_TLM = "touhou_little_maid";

    // 反射缓存：降低 Class.forName / getMethod 开销
    private static Class<?> MAID_BASE_CLASS = null;
    private static Method MAID_GET_OWNER_UUID = null;
    private static boolean MAID_OWNER_RESOLVED = false;

    private static Class<?> INVENTORY_MAID_CLASS = null;
    private static Method MAID_GET_INVENTORY = null;
    private static Method INVENTORY_GET_STACK = null;
    private static boolean MAID_INV_RESOLVED = false;

    // 女仆主手 / 副手 / 四个装备槽 在 MaidInventory 中的槽位索引范围
    // 实际以反射的 getStack(slot) 读取；若超范围则跳过，不抛错。
    private static final int INVENTORY_READ_LIMIT = 16;
    private static final int ARMOR_SLOT_COUNT = 4;

    private TLMSafe() {
        // 工具类
    }

    /**
     * 基于实体注册名命名空间判断该实体是否属于车万女仆。
     * 这里会同时匹配：女仆本体、可能的"女仆子实体"、以及车万女仆下的其它生物。
     */
    public static boolean isTouhouMaid(Entity entity) {
        if (entity == null) return false;
        var key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return key != null && NAMESPACE_TLM.equals(key.getNamespace());
    }

    /**
     * 读取女仆主人 UUID。
     * 车万女仆主人不一定是 `OwnableEntity#getOwnerUUID`，因此反射女仆自己的 API。
     * 反射失败（类不存在、方法签名变化、主人为空）时返回 null。
     */
    public static UUID getMaidOwnerUUID(Entity maid) {
        if (maid == null) return null;
        if (!MAID_OWNER_RESOLVED) resolveMaidOwnerReflect(maid);
        if (MAID_BASE_CLASS == null || MAID_GET_OWNER_UUID == null) return null;
        if (!MAID_BASE_CLASS.isInstance(maid)) return null;

        try {
            Object result = MAID_GET_OWNER_UUID.invoke(maid);
            return (result instanceof UUID) ? (UUID) result : null;
        } catch (Throwable t) {
            // 反射失败时不再尝试
            MAID_GET_OWNER_UUID = null;
            return null;
        }
    }

    /**
     * 收集女仆"应当享受装备附魔效果"的所有物品：
     *   [0] 主手
     *   [1] 副手
     *   [2] 头盔
     *   [3] 胸甲
     *   [4] 护腿
     *   [5] 靴子
     *
     * 未安装车万女仆时返回空列表，保证外层处理对普通世界无性能影响。
     */
    public static List<ItemStack> collectMaidEquipments(LivingEntity maid) {
        if (maid == null) return Collections.emptyList();
        if (!isTouhouMaid(maid)) return Collections.emptyList();
        if (!MAID_INV_RESOLVED) resolveMaidInventoryReflect(maid);

        List<ItemStack> result = new ArrayList<>(6);

        // 方案 A：优先用 LivingEntity 提供的 getMainHandItem/getOffhandItem/getArmorSlots。
        // 车万女仆通常是 LivingEntity 的子类，因此多数情况下这些值已由模组写入。
        result.add(nullSafe(maid.getMainHandItem()));
        result.add(nullSafe(maid.getOffhandItem()));
        for (ItemStack armor : maid.getArmorSlots()) {
            result.add(nullSafe(armor));
        }

        // 方案 B：若反射取到了更可靠的 MaidInventory，则在 0~15 槽里再尝试对齐
        // （不对结果主槽进行覆盖，仅为了防止 TLM 某些自定义装备没同步到 vanilla 槽）。
        if (INVENTORY_GET_STACK != null && MAID_GET_INVENTORY != null
                && MAID_BASE_CLASS != null
                && MAID_BASE_CLASS.isInstance(maid)) {
            try {
                Object inv = MAID_GET_INVENTORY.invoke(maid);
                if (inv != null) {
                    for (int i = 0; i < Math.min(6, INVENTORY_READ_LIMIT); i++) {
                        Object stack = INVENTORY_GET_STACK.invoke(inv, i);
                        if (stack instanceof ItemStack itemStack
                                && !itemStack.isEmpty()
                                && (result.get(i) == null || result.get(i).isEmpty())) {
                            result.set(i, itemStack);
                        }
                    }
                }
            } catch (Throwable t) {
                INVENTORY_GET_STACK = null;
            }
        }

        // 把所有空 null 替换成 ItemStack.EMPTY 便于调用方直接 isEmpty()
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i) == null) {
                result.set(i, ItemStack.EMPTY);
            }
        }
        return result;
    }

    // ========================================================================
    // 反射解析：类加载 + 方法解析（每个 JVM 生命周期最多尝试一次）
    // ========================================================================

    private static synchronized void resolveMaidOwnerReflect(Entity sample) {
        if (MAID_OWNER_RESOLVED) return;
        MAID_OWNER_RESOLVED = true;

        String[] candidateClasses = {
                "com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid",
                "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid",
                sample != null ? sample.getClass().getName() : null
        };

        for (String cn : candidateClasses) {
            if (cn == null) continue;
            try {
                Class<?> c = Class.forName(cn);
                for (Method m : c.getMethods()) {
                    if (m.getParameterCount() == 0
                            && m.getReturnType() == UUID.class
                            && (m.getName().equals("getOwnerUUID")
                            || m.getName().equals("getOwnerId"))) {
                        m.setAccessible(true);
                        MAID_BASE_CLASS = c;
                        MAID_GET_OWNER_UUID = m;
                        return;
                    }
                }
            } catch (Throwable ignored) {
                // 类未加载/方法缺失：跳过，继续下一个候选
            }
        }
    }

    private static synchronized void resolveMaidInventoryReflect(LivingEntity sample) {
        if (MAID_INV_RESOLVED) return;
        MAID_INV_RESOLVED = true;

        try {
            String[] maidClasses = {
                    "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid",
                    sample.getClass().getName()
            };

            Class<?> maidClass = null;
            Method getInventory = null;
            for (String cn : maidClasses) {
                try {
                    maidClass = Class.forName(cn);
                    break;
                } catch (Throwable ignored) {
                    // continue
                }
            }
            if (maidClass == null) return;

            for (Method m : maidClass.getMethods()) {
                if (m.getParameterCount() == 0
                        && m.getName().toLowerCase().contains("inventory")) {
                    m.setAccessible(true);
                    getInventory = m;
                    break;
                }
            }
            if (getInventory == null) return;

            Class<?> invClass = getInventory.getReturnType();
            Method getStack = null;
            for (Method m : invClass.getMethods()) {
                if (m.getName().equals("getStackInSlot")
                        || m.getName().equals("getItem")
                        || m.getName().equals("get")) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 1 && params[0] == int.class
                            && m.getReturnType() == ItemStack.class) {
                        m.setAccessible(true);
                        getStack = m;
                        break;
                    }
                }
            }
            if (getStack == null) return;

            INVENTORY_MAID_CLASS = maidClass;
            MAID_GET_INVENTORY = getInventory;
            INVENTORY_GET_STACK = getStack;
        } catch (Throwable ignored) {
            // 所有反射失败：Maid inventory 读取回退到 vanilla 槽（已提供）
        }
    }

    private static ItemStack nullSafe(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack;
    }
}
