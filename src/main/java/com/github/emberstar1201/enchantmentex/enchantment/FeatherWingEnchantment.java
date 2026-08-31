package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 羽翼（Feather Wing）
//
// 适用物品：鞘翅（仅限）
// 最高等级：I
// 获取：仅末地船 / 末地城宝箱（20% 概率刷出），宝藏附魔
// 效果：使用鞘翅飞行时免疫撞墙伤害
// 冲突：无（不与任何鞘翅附魔冲突）
// ========================================================================
public class FeatherWingEnchantment extends Enchantment {

    public FeatherWingEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.ARMOR_CHEST,
                new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int level) {
        return 25;
    }

    @Override
    public int getMaxCost(int level) {
        return 50;
    }

    // 宝藏附魔：仅末地船/末地城宝箱
    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    // 不可交易
    @Override
    public boolean isTradeable() {
        return false;
    }

    // 不可在附魔台出现
    @Override
    public boolean isDiscoverable() {
        return false;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // 仅限鞘翅
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof ElytraItem;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return canEnchant(stack);
    }

    // 不与任何附魔冲突
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other);
    }
}
