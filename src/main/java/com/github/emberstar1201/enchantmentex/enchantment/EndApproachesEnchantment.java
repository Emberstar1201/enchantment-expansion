package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 【终末将至】附魔 - 近战武器通用（剑、斧、三叉戟）
//
// 效果（配置值见 Config.java）：
//   1. 武器造成的伤害提升（默认 +250%，最终伤害 × 3.5）
//   2. 攻击冷却时间缩短（默认 90%，攻速 ×10）
//
// 获取方式：仅在要塞图书馆（Stronghold Library）的箱子中以附魔书形式生成
//   - isTreasureOnly() = true   → 附魔台无法获得
//   - isDiscoverable() = false  → 不参与随机宝箱战利品（手动注入要塞图书馆）
//   - isTradeable() = false     → 村民不卖
//   - isAllowedOnBooks() = true → 附魔书可在铁砧应用
//
// 稀有度：非常稀有（Rarity.VERY_RARE）
// 最高等级：I（1级）
// ========================================================================
public class EndApproachesEnchantment extends Enchantment {

    public EndApproachesEnchantment() {
        super(
                Rarity.VERY_RARE,
                EnchantmentCategory.WEAPON,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND}
        );
    }

    @Override
    public int getMinCost(int level) {
        return 50;
    }

    @Override
    public int getMaxCost(int level) {
        return 100;
    }

    // 宝藏附魔：附魔台无法获得
    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    // 不可通过村民交易获得
    @Override
    public boolean isTradeable() {
        return false;
    }

    // 不参与随机宝箱战利品——通过 LootTableLoadEvent 手动注入要塞图书馆
    @Override
    public boolean isDiscoverable() {
        return false;
    }

    // 剑、斧、三叉戟等近战武器
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem;
    }
}
