package com.github.emberstar1201.enchantmentex.util;

import net.minecraft.server.level.ServerLevel;
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
//   - collectMaids(ServerLevel)    —— 枚举维度内全部已加载女仆（唯一正确的全维度枚举方式）
//   - getMaidOwnerUUID(Entity)     —— 获取女仆主人的 UUID（可用于定位主人）
//   - collectMaidEquipments(Entity)—— 收集女仆身上的主手/副手/装甲槽 ItemStack
//   - collectMaidBaubles(Entity)   —— 收集女仆"饰品栏"（MaidBaubleInventory）物品
//   - getMaidExperience(Entity)    —— 读取女仆「自身经验」（终界之星经验加伤的平替数据源）
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

    // 女仆「自身经验」反射缓存
    private static Class<?> MAID_EXP_CLASS = null;
    private static Method MAID_GET_EXP = null;
    private static boolean MAID_EXP_RESOLVED = false;

    // 女仆饰品栏（BaubleItemHandler）反射缓存
    private static Method MAID_GET_BAUBLE = null;
    private static Method BAUBLE_GET_SLOTS = null;
    private static Method BAUBLE_GET_STACK = null;
    private static boolean MAID_BAUBLE_RESOLVED = false;

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
     * 收集某个维度中所有已加载的女仆实体。
     *
     * ★ 为什么不写成 level.getEntitiesOfClass(LivingEntity.class, 无穷大 AABB, ...) ★
     *
     * 形如 new AABB(-Infinity, -Infinity, -Infinity, +Infinity, +Infinity, +Infinity)
     * 的包围盒看似能「覆盖整个维度」，实际恒为空结果。原因在 1.20.1 的 Mth#floor(double)：
     *
     *     public static int floor(double value) {
     *         int i = (int) value;
     *         return value < (double) i ? i - 1 : i;
     *     }
     *
     * (int) Double.NEGATIVE_INFINITY == Integer.MIN_VALUE，
     * 且 -Infinity < (double) Integer.MIN_VALUE 成立，于是返回 i - 1，
     * 即 Integer.MIN_VALUE - 1 —— 整数溢出，结果为 Integer.MAX_VALUE。
     * Double.POSITIVE_INFINITY 同样得到 Integer.MAX_VALUE。
     * 两者换算成 section 坐标都是 134217727，于是查询落在同一个「不存在」的
     * 区块段上：不报错、不卡顿，只是永远扫不到任何实体。
     *
     * 反过来，改用 ±3.0E7 这类「有限但巨大」的 AABB 同样不可取：
     * EntitySectionStorage 内部要按 section 逐列循环，单次调用就是约 375 万次迭代，
     * 会把服务端 tick 击穿（参见 ProtectedItemHandler 中记录的历史教训）。
     *
     * 正确做法即本方法：直接遍历 ServerLevel#getAllEntities()，再用 isTouhouMaid 过滤。
     * 由于先把结果收集进 List，调用方在循环中修改实体（回血、加 modifier 等）
     * 不会造成迭代期间的并发修改。未安装车万女仆时列表恒为空，等同空转。
     */
    public static List<LivingEntity> collectMaids(ServerLevel level) {
        if (level == null) return Collections.emptyList();

        List<LivingEntity> maids = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            // 先做 instanceof 过滤，可省下大量非生物实体的注册名查询
            if (entity instanceof LivingEntity living && isTouhouMaid(living)) {
                maids.add(living);
            }
        }
        return maids;
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

    /**
     * 收集女仆「饰品栏」内的全部物品。
     *
     * 【为什么不用字段反射】
     *   车万女仆 1.5.2 的 BaubleItemHandler 内部字段 baubles 并不是 List<ItemStack>，
     *   而是 Int2ObjectSortedMap<IMaidBauble>（饰品实例的缓存）。
     *   真正存放 ItemStack 的是它的父类 ItemStackHandler 的 itemStacks。
     *   因此正确做法是走公开 API：EntityMaid#getMaidBauble() → ItemStackHandler，
     *   再用 getSlots() / getStackInSlot(i) 读取。
     *
     * 未安装车万女仆、或反射签名变化时返回空列表，不会抛异常。
     */
    public static List<ItemStack> collectMaidBaubles(LivingEntity maid) {
        if (maid == null) return Collections.emptyList();
        if (!isTouhouMaid(maid)) return Collections.emptyList();
        if (!MAID_BAUBLE_RESOLVED) resolveMaidBaubleReflect(maid);
        if (MAID_GET_BAUBLE == null || BAUBLE_GET_SLOTS == null || BAUBLE_GET_STACK == null) {
            return Collections.emptyList();
        }

        try {
            Object handler = MAID_GET_BAUBLE.invoke(maid);
            if (handler == null) return Collections.emptyList();

            int slots = (Integer) BAUBLE_GET_SLOTS.invoke(handler);
            List<ItemStack> result = new ArrayList<>(slots);
            for (int i = 0; i < slots; i++) {
                Object stack = BAUBLE_GET_STACK.invoke(handler, i);
                result.add(stack instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY);
            }
            return result;
        } catch (Throwable t) {
            // 反射失败：退回"没有饰品"的安全结论
            return Collections.emptyList();
        }
    }

    /**
     * 读取女仆「自身经验」。
     *
     * 【为什么用它】
     *   终界之星的玩家版加成依赖 Player#experienceLevel，女仆没有「经验等级」。
     *   但车万女仆自带独立经验系统：EntityMaid 用 NBT 标签 MaidExperience 记录经验，
     *   拾取经验球（pickupXPOrb）时累加，死亡时按此值掉落经验。
     *   因此这是语义上最贴近「经验加伤」的平替数据源。
     *
     * 【量纲提醒】
     *   该值是「原始经验点」（一个经验球就是若干点，甚至上百点），
     *   而玩家 experienceLevel 是「等级」。两者不能共用同一个除数，
     *   调用方需按自己的档位除数（如 Config#endStarMaidXpPerTier）换算。
     *
     * 未安装车万女仆、实体不是女仆、或反射失败时返回 -1，调用方据此跳过加成。
     */
    public static int getMaidExperience(LivingEntity maid) {
        if (maid == null) return -1;
        if (!isTouhouMaid(maid)) return -1;
        if (!MAID_EXP_RESOLVED) resolveMaidExperienceReflect(maid);
        if (MAID_EXP_CLASS == null || MAID_GET_EXP == null) return -1;
        if (!MAID_EXP_CLASS.isInstance(maid)) return -1;

        try {
            Object result = MAID_GET_EXP.invoke(maid);
            return (result instanceof Integer value) ? value : -1;
        } catch (Throwable t) {
            // 反射失败时不再尝试，直接退回「读不到经验」
            MAID_GET_EXP = null;
            return -1;
        }
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

    // 解析女仆自身经验读取方法：无参、返回 int 的 getExperience()。
    // IMaid 接口里就带该方法（默认返回 0），EntityMaid 覆写了它，因此优先在接口上取。
    private static synchronized void resolveMaidExperienceReflect(LivingEntity sample) {
        if (MAID_EXP_RESOLVED) return;
        MAID_EXP_RESOLVED = true;

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
                            && m.getReturnType() == int.class
                            && m.getName().equals("getExperience")) {
                        m.setAccessible(true);
                        MAID_EXP_CLASS = c;
                        MAID_GET_EXP = m;
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

    // 解析 EntityMaid#getMaidBauble() 以及饰品栏的槽位读取方法。
    // 说明：只缓存 Method，不缓存实例——每个女仆各有一个饰品栏实例。
    private static synchronized void resolveMaidBaubleReflect(LivingEntity sample) {
        if (MAID_BAUBLE_RESOLVED) return;
        MAID_BAUBLE_RESOLVED = true;

        try {
            Class<?> maidClass = null;
            String[] maidClasses = {
                    "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid",
                    sample.getClass().getName()
            };
            for (String cn : maidClasses) {
                try {
                    maidClass = Class.forName(cn);
                    break;
                } catch (Throwable ignored) {
                    // continue
                }
            }
            if (maidClass == null) return;

            // getMaidBauble() 是车万女仆自己的方法，方法名在发布版中不会被混淆，可直取；
            // 若未来改名，则退回"无参且返回类型名含 Bauble"的模糊匹配。
            Method getBauble = null;
            try {
                getBauble = maidClass.getMethod("getMaidBauble");
            } catch (NoSuchMethodException ignored) {
                // 走模糊匹配
            }
            if (getBauble == null) {
                for (Method m : maidClass.getMethods()) {
                    if (m.getParameterCount() == 0
                            && m.getReturnType().getSimpleName().contains("Bauble")) {
                        getBauble = m;
                        break;
                    }
                }
            }
            if (getBauble == null) return;

            // BaubleItemHandler 继承 net.minecraftforge.items.ItemStackHandler，
            // 故可直接用 getSlots() / getStackInSlot(int)。
            Class<?> handlerClass = getBauble.getReturnType();
            Method getSlots = handlerClass.getMethod("getSlots");
            Method getStack = handlerClass.getMethod("getStackInSlot", int.class);

            MAID_GET_BAUBLE = getBauble;
            BAUBLE_GET_SLOTS = getSlots;
            BAUBLE_GET_STACK = getStack;
        } catch (Throwable ignored) {
            // 反射失败：collectMaidBaubles 返回空列表（等同于"没戴饰品"）
        }
    }

    private static ItemStack nullSafe(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack;
    }
}
