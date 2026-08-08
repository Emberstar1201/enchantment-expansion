package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;

import javax.annotation.ParametersAreNonnullByDefault;

// ========================================================================
// 【无中生有】附魔 - 工具专属（镐/斧/铲/锄）
// 效果：用带有此附魔的工具以正确方式破坏对应方块时，有概率额外掉落稀有物品
//   - 破坏 base_stone_overworld 标签方块（需镐）：掉落钻石或下界合金碎片
//   - 破坏 leaves 标签方块（需锄等正确工具）：掉落金苹果或附魔金苹果
// 额外掉落独立于方块本身的掉落物（不影响原版掉落逻辑）
// 与精准采集互斥，与时运兼容
// ========================================================================
public class CreationFromNothingEnchantment extends Enchantment {

    // 最高等级 3 级
    private static final int MAX_LEVEL = 3;

    public CreationFromNothingEnchantment() {
        // Rarity.RARE：稀有度稀有（与模组其他附魔保持一致）
        // EnchantmentCategory.DIGGER：工具类（镐/斧/铲/锄）
        // EquipmentSlot.MAINHAND：仅主手生效
        super(
                Rarity.RARE,
                EnchantmentCategory.DIGGER,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND}
        );
    }

    // 获取附魔最低等级（从1级开始）
    @Override
    public int getMinLevel() {
        return 1;
    }

    // 获取附魔最高等级（3级）
    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    // 附魔在附魔台出现的最低经验成本
    // Lv1=10, Lv2=18, Lv3=26，成本较高反映其强大效果
    @Override
    public int getMinCost(int level) {
        return 10 + (level - 1) * 8;
    }

    // 附魔在附魔台出现的最高经验成本
    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 10;
    }

    // 非宝藏附魔：附魔台可直接获得（与模组其他附魔保持一致）
    @Override
    public boolean isTreasureOnly() {
        return false;
    }

    // 可通过村民图书管理员交易获得附魔书
    @Override
    public boolean isTradeable() {
        return true;
    }

    // 可通过宝箱/钓鱼/附魔台等随机发现
    @Override
    public boolean isDiscoverable() {
        return true;
    }

    // 允许附魔书在铁砧上应用到工具
    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // ========================================================================
    // 【冲突设置】与精准采集（Silk Touch）互斥
    // 原因：精准采集会改变方块掉落物（直接掉落石头方块而非圆石），
    //       与"无中生有"的额外掉落机制在语义上冲突，故设为互斥
    // 时运不受影响，可共存（时运影响方块本身掉落，额外掉落独立计算）
    // ========================================================================
    @Override
    protected boolean checkCompatibility(@ParametersAreNonnullByDefault Enchantment other) {
        if (other == Enchantments.SILK_TOUCH) return false;
        return super.checkCompatibility(other);
    }
}
