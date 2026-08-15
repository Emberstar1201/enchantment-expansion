package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;

// ========================================================================
// 【无中生有·重制】附魔 - 镐/剪刀专属
//
// 适用物品：镐类工具 + 剪刀（额外判定）
// 最高等级：III（3级）
//
// 【镐效果】
//   挖掘石质基底方块（石头/花岗岩/深板岩/地狱岩/末地石等及其变种）时，
//   概率额外爆出矿物：
//   - 常规掉落（未触发稀有时 100%）：煤炭/铁锭/铜锭/紫水晶/金粒/铁粒（随机一种）
//   - 稀有掉落（触发时）：钻石（权重最高）> 下界合金碎片 > 下界合金锭
//
// 【剪刀效果】
//   采集树叶时，保留金苹果和附魔金苹果的掉落（不会因剪刀而丢失苹果掉落）
//
// 【冲突】精准采集（Silk Touch）
// ========================================================================
public class ExNihiloEnchantment extends Enchantment {

    // 最高等级 3 级
    private static final int MAX_LEVEL = 3;

    public ExNihiloEnchantment() {
        // Rarity.RARE：稀有度稀有
        // EnchantmentCategory.DIGGER：工具类基础类别
        // 注意：剪刀不在 DIGGER 类别中，需在 canEnchant 中额外判断
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

    // 附魔成本：Lv1=15, Lv2=23, Lv3=31
    @Override
    public int getMinCost(int level) {
        return 15 + (level - 1) * 8;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 10;
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

    // ========================================================================
    // 【可附魔判定】镐类工具或剪刀均可附魔
    // DIGGER 类别包含镐/斧/铲/锄，额外允许剪刀
    // ========================================================================
    @Override
    public boolean canEnchant(ItemStack stack) {
        // 父类检查 DIGGER 类别
        if (super.canEnchant(stack)) {
            return true;
        }
        // 额外允许剪刀
        return stack.is(Items.SHEARS);
    }

    // ========================================================================
    // 【冲突设置】与精准采集互斥
    // 精准采集会改变方块掉落，干扰石质方块的识别和额外掉落机制
    // ========================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        if (other == Enchantments.SILK_TOUCH) return false;
        return super.checkCompatibility(other);
    }
}