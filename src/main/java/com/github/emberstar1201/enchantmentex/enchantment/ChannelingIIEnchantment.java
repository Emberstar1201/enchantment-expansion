package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;

// ========================================================================
// 【引雷 II（Channeling II）】附魔类
//
// 适用物品：三叉戟（TRIDENT）
// 最高等级：1级（仅 II 级）
// 获取方式：钓鱼、沙漠神殿、废弃矿洞（宝箱战利品）
// 效果：下雨天（普通雨天即可，无需雷暴）投掷三叉戟击中实体时召唤闪电
// 与原版引雷 I 的关系：互斥附魔，效果覆盖引雷 I
// ========================================================================
public class ChannelingIIEnchantment extends Enchantment {

    public ChannelingIIEnchantment() {
        // Rarity.UNCOMMON：稀有度介于普通和稀有之间
        // EnchantmentCategory.TRIDENT：仅三叉戟
        super(Rarity.UNCOMMON, EnchantmentCategory.TRIDENT,
                new net.minecraft.world.entity.EquipmentSlot[0]);
    }

    @Override
    public int getMaxLevel() {
        return 1; // 仅 II 级，无 I 级
    }

    @Override
    public int getMinCost(int level) {
        return 20; // 与引雷 I 的 II 级虚拟成本对齐
    }

    @Override
    public int getMaxCost(int level) {
        return 50;
    }

    // ================================================================
    // 附魔互斥规则：
    //   引雷 I、II、III 为互斥附魔，同一把三叉戟只能有一个
    // ================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other)
                && other != Enchantments.CHANNELING               // 不与原版引雷 I 共存
                && other != ModEnchantments.CHANNELING_III.get(); // 不与引雷 III 共存
    }

    // 附魔台不可获取（仅战利品获取）
    @Override
    public boolean isDiscoverable() {
        return false;
    }

    // 不与村民交易（避免从图书管理员处刷取）
    @Override
    public boolean isTradeable() {
        return false;
    }

    // 允许附魔书存在（战利品和钓鱼需要附魔书形式）
    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // 非宝藏附魔（通过自定义战利品注入来控制具体获取方式）
    @Override
    public boolean isTreasureOnly() {
        return false;
    }
}