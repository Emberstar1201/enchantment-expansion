package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;

// ========================================================================
// 【雷电之戟 Lightning Spear】附魔类（统一引雷 II/III 为两等级）
//
// 中文：雷电之戟    英文：Lightning Spear    ID: enchantment_expansion:lightning_spear
//
// 适用物品：三叉戟（EnchantmentCategory.TRIDENT）
// 最高等级：II（2 级）
//   - I：雨天（无需雷暴）投掷三叉戟击中实体召唤闪电。
//   - II：任意天气、任意维度（包括下界和末地）投掷三叉戟击中实体召唤闪电。
// 获取方式：钓鱼 / 沙漠神殿宝箱 / 废弃矿洞宝箱（II 级钓鱼更稀有）
//
// 互斥：原版引雷 I（minecraft:channeling）—— 同系只允许一个
// ========================================================================
public class LightningSpearEnchantment extends Enchantment {

    public LightningSpearEnchantment() {
        // 稀有度沿用 VERY_RARE（因为含维度级 II 级）。
        super(Rarity.VERY_RARE, EnchantmentCategory.TRIDENT,
                new net.minecraft.world.entity.EquipmentSlot[0]);
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }

    @Override
    public int getMinCost(int level) {
        // I 级=20（=原II），II 级=30（=原III）
        return level == 1 ? 20 : 30;
    }

    @Override
    public int getMaxCost(int level) {
        return level == 1 ? 50 : 60;
    }

    // ========================================================================
    // 互斥：与原版引雷 I（Channeling）互斥。
    //   由于原先的 II/III 已合并为本附魔的两个等级，不再检查旧注册对象。
    // ========================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other)
                && other != Enchantments.CHANNELING;
    }

    // 钓鱼/宝箱获取，不走附魔台生成。
    @Override
    public boolean isDiscoverable() {
        return false;
    }

    @Override
    public boolean isTradeable() {
        return false;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    @Override
    public boolean isTreasureOnly() {
        return false;
    }
}
