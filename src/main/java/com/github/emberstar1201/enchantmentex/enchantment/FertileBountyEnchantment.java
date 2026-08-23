package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 【丰饶之息】附魔 - 锄头专用（FERTILE_BOUNTY）
//
// 【效果】（数值见 AgricultureConfig）
//   一、生长光环：手持附魔锄头时，周围 auraRadius（默认 10 格）内的
//       未成熟作物每 growthIntervalTick（默认 20 tick）有 growthChance
//       概率被催熟一阶段（原版骨粉跳阶段逻辑）
//   二、收获翻倍：配合春华秋实一键收获时，掉落物数量 ×2
//       （单独手持不影响原版手动收获，需与春华秋实搭配）
//
// 【获取】附魔台 / 宝箱 / 村民交易（RARE）
// 【最高等级】II
// 【冲突】无（与春华秋实、万物回春可叠加在同一把锄头）
// ========================================================================
public class FertileBountyEnchantment extends Enchantment {

    private static final int MAX_LEVEL = 2;

    public FertileBountyEnchantment() {
        super(
                Rarity.RARE,
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
        return 15 + (level - 1) * 12;  // Lv1=15, Lv2=27
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 18;
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