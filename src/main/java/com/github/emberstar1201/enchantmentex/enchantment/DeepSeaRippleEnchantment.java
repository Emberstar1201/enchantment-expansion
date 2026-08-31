package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;

// ========================================================================
// 【深海的涟漪】附魔 - 靴子专属
// 效果：
//   1. 按住 Shift（潜行）+ 鼠标滚轮，可调节水中游泳速度倍率
//   2. 倍率区间：1.0（原版）~ 3.0（可配置上限）
//   3. 滚轮上调 = 加快游泳速度；滚轮下调 = 减慢游泳速度
// 获取：附魔台 + 村民交易 + 宝箱（非宝藏附魔）
// 冲突：风踏涟漪、深海探索者、冰霜行者、灵魂疾行、迅捷潜行、飞轮效应
// ========================================================================
public class DeepSeaRippleEnchantment extends Enchantment {

    private static final int MAX_LEVEL = 1;

    public DeepSeaRippleEnchantment() {
        super(
                Rarity.RARE,
                EnchantmentCategory.ARMOR_FEET,
                new EquipmentSlot[]{EquipmentSlot.FEET}
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

    // I 级附魔台出现成本：15~30 区间
    @Override
    public int getMinCost(int level) {
        return 15 + (level - 1) * 10;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
    }

    // 非宝藏：附魔台可直接获得
    @Override
    public boolean isTreasureOnly() {
        return false;
    }

    // 允许村民图书管理员交易
    @Override
    public boolean isTradeable() {
        return true;
    }

    // 允许宝箱 / 钓鱼 / 附魔台随机产出
    @Override
    public boolean isDiscoverable() {
        return true;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // ========================================================================
    // 冲突：同为靴子移动类附魔互斥
    //   - 风踏涟漪：靴子移动类，功能重叠（陆地/水面）
    //   - 深海探索者：同样影响水中移动速度
    //   - 冰霜行者：水面结冰 vs 水下加速，同为靴子水域类
    //   - 灵魂疾行：靴子移动类附魔
    //   - 迅捷潜行：靴子潜行类
    //   - 飞轮效应：靴子加速类附魔
    // ========================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        if (other == Enchantments.DEPTH_STRIDER) return false;
        if (other == Enchantments.FROST_WALKER) return false;
        if (other == Enchantments.SOUL_SPEED) return false;
        if (other == Enchantments.SWIFT_SNEAK) return false;
        if (other == ModEnchantments.WIND_RIPPLE.get()) return false;
        if (other == ModEnchantments.FLYWHEEL_EFFECT.get()) return false;
        return super.checkCompatibility(other);
    }

    // 只能附魔在靴子类护甲上
    @Override
    public boolean canEnchant(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armorItem) {
            return armorItem.getEquipmentSlot() == EquipmentSlot.FEET;
        }
        return false;
    }
}
