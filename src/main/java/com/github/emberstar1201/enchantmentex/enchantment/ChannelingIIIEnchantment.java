package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;

// ========================================================================
// 【引雷 III（Channeling III）】附魔类
//
// 适用物品：三叉戟（TRIDENT）
// 最高等级：1级（仅 III 级）
// 获取方式：仅限钓鱼获得（无法从宝箱或交易获取）
// 效果：在任何天气下（包括晴天）以及下界和末地，
//       投掷三叉戟击中实体时召唤闪电
// 备注：引雷系列的终极版本
// ========================================================================
public class ChannelingIIIEnchantment extends Enchantment {

    public ChannelingIIIEnchantment() {
        // Rarity.VERY_RARE：最高稀有度，与终极版本定位匹配
        // EnchantmentCategory.TRIDENT：仅三叉戟
        super(Rarity.VERY_RARE, EnchantmentCategory.TRIDENT,
                new net.minecraft.world.entity.EquipmentSlot[0]);
    }

    @Override
    public int getMaxLevel() {
        return 1; // 仅 III 级，无 I、II 级
    }

    @Override
    public int getMinCost(int level) {
        return 30; // 成本高于引雷 II
    }

    @Override
    public int getMaxCost(int level) {
        return 60;
    }

    // ================================================================
    // 附魔互斥规则：
    //   引雷 I、II、III 为互斥附魔，同一把三叉戟只能有一个
    // ================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other)
                && other != Enchantments.CHANNELING               // 不与原版引雷 I 共存
                && other != ModEnchantments.CHANNELING_II.get();  // 不与引雷 II 共存
    }

    // 附魔台不可获取
    @Override
    public boolean isDiscoverable() {
        return false;
    }

    // 不与村民交易
    @Override
    public boolean isTradeable() {
        return false;
    }

    // 允许附魔书形式存在（钓鱼需要附魔书）
    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // 非宝藏附魔（通过自定义战利品注入精确控制获取途径）
    @Override
    public boolean isTreasureOnly() {
        return false;
    }
}