package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// "兵长的回声"附魔 - 武器通用
//
// 【效果】
//   攻击实体叠加标记，3层后引爆，造成基于最大生命值的魔法伤害。
//   伤害公式：5% + (等级-1) × 2% 最大生命值
//   触发条件：目标生命值 ≥ 50 点（25❤️）
//
// 【视觉反馈】（可在配置中关闭）
//   1层标记 → 白色溅射粒子
//   2层标记 → 紫色附魔粒子
//   3层引爆 → 环形龙息爆炸粒子 + 中心冲击波
//
// 【获取】附魔台可获得（I~V级）
// ========================================================================
public class LevisEchoEnchantment extends Enchantment {

    private static final int MAX_LEVEL = 5;

    public LevisEchoEnchantment() {
        // Rarity.UNCOMMON：附魔台可正常出现
        // EnchantmentCategory.WEAPON：剑、斧、三叉戟等所有攻击性武器
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
        // I: 12, II: 19, III: 26, IV: 33, V: 40
        return 12 + (level - 1) * 7;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
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