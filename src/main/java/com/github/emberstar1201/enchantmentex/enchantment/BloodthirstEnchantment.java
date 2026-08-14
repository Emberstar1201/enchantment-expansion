package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// "嗜血"附魔 - 武器通用
//
// 【效果】
//   攻击命中怪物时，回复自身 10% 最大生命值
//   冷却 5 秒（触发后进入冷却，冷却期间不触发）
//
// 【获取】附魔台可获得（仅 I 级）
// ========================================================================
public class BloodthirstEnchantment extends Enchantment {

    private static final int MAX_LEVEL = 1;

    public BloodthirstEnchantment() {
        // Rarity.UNCOMMON：附魔台可正常出现
        // EnchantmentCategory.WEAPON：剑、斧、三叉戟等武器
        // EquipmentSlot.MAINHAND：仅主手生效
        super(Rarity.UNCOMMON, EnchantmentCategory.WEAPON,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND});
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
        return 15;
    }

    @Override
    public int getMaxCost(int level) {
        return 30;
    }

    // 非宝藏附魔：附魔台可获得
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

    // ========================================================================
    // 对外 API
    // ========================================================================
    public static int getMaxLevelStatic() {
        return MAX_LEVEL;
    }
}