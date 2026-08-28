package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 【蓄积】附魔 - 剑/斧/三叉戟专用（ACCUMULATE）
//
// 【效果】（数值见 AccumulateConfig）
//   长按右键蓄力，每 5 秒完成一阶蓄力：
//     I   : 最多蓄 1 阶
//     II  : 最多蓄 2 阶
//     III : 最多蓄 3 阶
//   蓄力完成后下一次攻击：
//     伤害倍率 = 阶级数（I×1 / II×2 / III×3，可配置）
//     附带小范围击退（距离随阶级提升）
//   蓄力中断：切换物品、受伤、10 秒未攻击则重置
//   视觉反馈：蓄力时金色光晕粒子 + 屏幕显示层数（蓄力 I / III）
//
// 【获取】附魔台 / 宝箱 / 村民交易（RARE 稀有）
// 【最高等级】III
// ========================================================================
public class AccumulateEnchantment extends Enchantment {

    private static final int MAX_LEVEL = 3;

    public AccumulateEnchantment() {
        // EnchantmentCategory.WEAPON：适用于剑、斧、三叉戟
        super(
                Rarity.RARE,
                EnchantmentCategory.WEAPON,
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

    @Override
    public int getMinCost(int level) {
        // 附魔消耗：Lv1=15, Lv2=27, Lv3=39
        return 15 + (level - 1) * 12;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 20;
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

    // 仅剑/斧/三叉戟可附魔
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return canEnchant(stack);
    }
}