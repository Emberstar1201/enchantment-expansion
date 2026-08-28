package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// 末影箭：箭矢命中后将射手传送至命中位置。
public class EnderArrowEnchantment extends Enchantment {

    public EnderArrowEnchantment() {
        // Forge 的单个附魔只能声明一个分类；BOW 作为主分类，弩由 canEnchant 显式支持。
        super(Rarity.RARE, EnchantmentCategory.BOW,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND});
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
        return 20;
    }

    @Override
    public int getMaxCost(int level) {
        return 45;
    }

    @Override
    public boolean isTreasureOnly() {
        // 非宝藏附魔，确保可在附魔台获得；同样允许出现在结构战利品中。
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

    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return canEnchant(stack);
    }

    @Override
    protected boolean checkCompatibility(Enchantment other) {
        // 三种箭矢命中效果同时触发会造成传送位置与取消命中事件的冲突。
        if (other == ModEnchantments.EXPLOSIVE_ARROW.get()
                || other == ModEnchantments.CHAIN_ARROW.get()) {
            return false;
        }
        return super.checkCompatibility(other);
    }
}
