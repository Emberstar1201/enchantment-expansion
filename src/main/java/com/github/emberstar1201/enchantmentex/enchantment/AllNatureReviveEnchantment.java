package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 【万物回春】附魔 - 锄头专用（ALL_NATURE_REVIVE）
//
// 【效果】（数值见 AgricultureConfig）
//   右键未成熟作物时，自动对范围内的所有未成熟作物施加原版骨粉催熟效果
//     I : 范围 3×3，催熟几率 60%
//     II: 范围 5×5，催熟几率 85%
//   催熟采用原版 getBonemealAgeIncrease 随机跳阶段逻辑，与骨粉机制一致
//
// 【获取】附魔台 / 宝箱 / 村民交易（UNCOMMON）
// 【最高等级】II
// 【冲突】无（可与春华秋实叠加：右键成熟→收获、右键未熟→催熟，自动分流）
// ========================================================================
public class AllNatureReviveEnchantment extends Enchantment {

    private static final int MAX_LEVEL = 2;

    public AllNatureReviveEnchantment() {
        super(
                Rarity.UNCOMMON,
                EnchantmentCategory.DIGGER,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND}
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
        return 12 + (level - 1) * 10;  // Lv1=12, Lv2=22
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
    }

    @Override
    public boolean isTreasureOnly() {
        return false;
    }

    @Override
    public boolean isTradeable() {
        return true;
    }

    @Override
    public boolean isDiscoverable() {
        return true;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // 仅锄头可附魔
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof HoeItem;
    }
}