package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 【难度馈赠】附魔 - 武器 + 盔甲专用（DIFFICULTY_GIFT）
//
// 【效果】（数值见 DifficultyGiftConfig）
//   根据当前游戏难度提供固定数值加成（检测 player.level.getDifficulty()）：
//     和平：无加成
//     简单：伤害+1，攻速+0.2，护甲+1，韧性+0.5
//     普通：伤害+2，攻速+0.4，护甲+2，韧性+1.0
//     困难：伤害+4，攻速+0.6，护甲+3，韧性+1.5
//
//   武器（剑/斧/三叉戟）提供：伤害 + 攻击速度
//   盔甲（头盔/胸甲/护腿/靴子）提供：护甲 + 韧性
//
// 【获取】附魔台 / 宝箱 / 村民交易（RARE 稀有）
// 【最高等级】I
// ========================================================================
public class DifficultyGiftEnchantment extends Enchantment {

    private static final int MAX_LEVEL = 1;

    public DifficultyGiftEnchantment() {
        // 构造类别用 WEAPON（仅影响部分默认行为），附魔台兼容性由 canEnchant 决定
        super(
                Rarity.RARE,
                EnchantmentCategory.WEAPON,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
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
        return 20;
    }

    @Override
    public int getMaxCost(int level) {
        return 40;
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

    // 武器（剑/斧/三叉戟）或盔甲可附魔
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof ArmorItem;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return canEnchant(stack);
    }
}