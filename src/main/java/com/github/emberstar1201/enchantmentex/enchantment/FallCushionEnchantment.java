package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 【坠落缓冲】(Fall Cushion) 附魔 - 靴子专属
//
// 效果：
//   高坠落地时大幅减免摔落伤害，并产生冲击波击退周围敌人：
//     I ：伤害减少至 50%，击退强度 1.0，冲击波半径 3 格
//     II：伤害减少至 25%，击退强度 2.0，冲击波半径 5 格
//   摔落距离不足 3 格时仅轻微减伤，不产生冲击波（避免平地跳跃滥用）。
//
// 实现：LivingFallEvent（减伤 + 击退）见 FallCushionHandler，
//      纯事件方案，无需 Mixin。
//
// 获取：附魔台 + 宝箱 / 村民（可发现）
// 互斥：与「优雅猫步」(elegant_catwalk) 互斥——优雅猫步已提供完全免疫
//      摔落伤害，两者同为靴子免伤类，同装会互相覆盖
// ========================================================================
public class FallCushionEnchantment extends Enchantment {

    // 最高等级 II
    private static final int MAX_LEVEL = 2;

    public FallCushionEnchantment() {
        // Rarity.RARE：稀有
        // EnchantmentCategory.ARMOR_FEET：仅靴子类
        super(
                Rarity.RARE,
                EnchantmentCategory.ARMOR_FEET,
                new EquipmentSlot[]{EquipmentSlot.FEET}
        );
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    @Override
    public int getMinCost(int level) {
        return 15 + (level - 1) * 10;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
    }

    // 非宝藏附魔：附魔台直接可获得
    @Override
    public boolean isTreasureOnly() {
        return false;
    }

    @Override
    public boolean isDiscoverable() {
        return true;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // 仅靴子类护甲
    @Override
    public boolean canEnchant(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armorItem) {
            return armorItem.getEquipmentSlot() == EquipmentSlot.FEET;
        }
        return false;
    }

    // ========================================================================
    // 【互斥】与「优雅猫步」互斥：同为靴子免伤类附魔
    // ========================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        if (other == ModEnchantments.ELEGANT_CATWALK.get()) {
            return false;
        }
        // 与其他靴子附魔（深海探索者/灵魂疾行/幽匿行者等）保持兼容
        return super.checkCompatibility(other);
    }
}