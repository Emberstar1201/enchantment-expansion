package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 【春华秋实】附魔 - 锄头专用（SPRING_HARVEST）
//
// 【效果】（数值见 AgricultureConfig）
//   一、一键开垦：右键未耕地时，自动开垦以目标为中心的范围
//        I : 3×3   II: 5×5
//   二、一键收获：右键成熟作物时，自动收割范围内所有成熟作物，
//       并将种子原地补种（收获物直接进入背包，背包满则掉落）
//
// 【获取】附魔台 / 宝箱 / 村民交易（普通稀有度，UCOMMON）
// 【最高等级】II
// 【冲突】无（与锄头其他附魔可叠加，包括万物回春、丰饶之息）
// ========================================================================
public class SpringHarvestEnchantment extends Enchantment {

    private static final int MAX_LEVEL = 2;

    public SpringHarvestEnchantment() {
        // EnchantmentCategory.DIGGER：工具类（实际判定用 canEnchant 限制为锄头）
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
        return 10 + (level - 1) * 8;  // Lv1=10, Lv2=18
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 12;
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