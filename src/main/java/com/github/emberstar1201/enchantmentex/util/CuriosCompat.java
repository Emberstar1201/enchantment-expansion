package com.github.emberstar1201.enchantmentex.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;

import java.lang.reflect.Method;

// Curios 软兼容工具：不直接引用 Curios 类型，未安装时安全返回 false。
public final class CuriosCompat {
    private static final Method GET_INVENTORY;
    private static final Method GET_EQUIPPED_HANDLER;
    private static final Method GET_SLOTS;
    private static final Method GET_STACK_IN_SLOT;

    static {
        Method getInventory = null;
        Method getEquippedHandler = null;
        Method getSlots = null;
        Method getStackInSlot = null;
        try {
            Class<?> curiosApi = Class.forName(
                    "top.theillusivec4.curios.api.CuriosApi");
            Class<?> curiosHandler = Class.forName(
                    "top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            Class<?> itemHandler = Class.forName(
                    "net.minecraftforge.items.IItemHandler");

            // Curios 5.11.x：CuriosApi -> ICuriosItemHandler -> IItemHandler。
            getInventory = curiosApi.getMethod(
                    "getCuriosInventory", LivingEntity.class);
            getEquippedHandler = curiosHandler.getMethod("getEquippedCurios");
            getSlots = itemHandler.getMethod("getSlots");
            getStackInSlot = itemHandler.getMethod("getStackInSlot", int.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // 未安装 Curios 或 API 不兼容时保持禁用。
        }
        GET_INVENTORY = getInventory;
        GET_EQUIPPED_HANDLER = getEquippedHandler;
        GET_SLOTS = getSlots;
        GET_STACK_IN_SLOT = getStackInSlot;
    }

    private CuriosCompat() {
    }

    public static boolean isInstalled() {
        return GET_INVENTORY != null
                && GET_EQUIPPED_HANDLER != null
                && GET_SLOTS != null
                && GET_STACK_IN_SLOT != null;
    }

    public static boolean isWearing(LivingEntity entity, Item item) {
        if (entity == null || item == null || !isInstalled()) {
            return false;
        }
        try {
            Object inventoryResult = GET_INVENTORY.invoke(null, entity);
            if (!(inventoryResult instanceof LazyOptional<?> optional)
                    || !optional.isPresent()) {
                return false;
            }

            Object curiosHandler = optional.resolve().orElse(null);
            if (curiosHandler == null) {
                return false;
            }

            Object equippedHandler = GET_EQUIPPED_HANDLER.invoke(curiosHandler);
            if (equippedHandler == null) {
                return false;
            }

            int slots = (int) GET_SLOTS.invoke(equippedHandler);
            for (int slot = 0; slot < slots; slot++) {
                Object stack = GET_STACK_IN_SLOT.invoke(equippedHandler, slot);
                if (stack instanceof ItemStack itemStack && itemStack.is(item)) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Curios 版本差异或能力未就绪时不影响其他触发方式。
        }
        return false;
    }
}
