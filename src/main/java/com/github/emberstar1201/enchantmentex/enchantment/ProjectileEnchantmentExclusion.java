package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.item.enchantment.Enchantment;

/** 弓、弩特殊效果附魔的统一互斥规则。 */
public final class ProjectileEnchantmentExclusion {

    private ProjectileEnchantmentExclusion() {
    }

    public static boolean isIncompatible(Enchantment first, Enchantment second) {
        if (isSniper(first) && isYunLai(second)
                || isSniper(second) && isYunLai(first)) {
            return false;
        }
        return isProjectileEnchantment(first) && isProjectileEnchantment(second)
                && first != second;
    }

    private static boolean isProjectileEnchantment(Enchantment enchantment) {
        return enchantment == ModEnchantments.ANCIENT_YUNLAI.get()
                || enchantment == ModEnchantments.YUNLAI_ARCHERY.get()
                || enchantment == ModEnchantments.GLACIAL_ARROW.get()
                || enchantment == ModEnchantments.EXPLOSIVE_ARROW.get()
                || enchantment == ModEnchantments.CHAIN_ARROW.get()
                || enchantment == ModEnchantments.ENDER_ARROW.get()
                || enchantment == ModEnchantments.SWIFT_CROSSBOW.get()
                || enchantment == ModEnchantments.SNIPER.get();
    }

    private static boolean isSniper(Enchantment enchantment) {
        return enchantment == ModEnchantments.SNIPER.get();
    }

    private static boolean isYunLai(Enchantment enchantment) {
        return enchantment == ModEnchantments.ANCIENT_YUNLAI.get()
                || enchantment == ModEnchantments.YUNLAI_ARCHERY.get();
    }
}
