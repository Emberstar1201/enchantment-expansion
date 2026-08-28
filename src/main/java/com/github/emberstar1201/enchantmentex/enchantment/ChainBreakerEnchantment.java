package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 【连锁挖掘】附魔 - 镐/斧/锹专用（CHAIN_BREAKER）
//
// 【效果】（数值见 ChainBreakerConfig）
//   按住 ~ 键（可在 Controls 中自定义）挖掘方块时，自动连锁破坏
//   周围与原方块相同的方块，掉落物正常生成：
//     I   : 3×3×3 区域
//     II  : 5×5×5 区域
//     III : 9×9×9 区域
//   每个额外方块消耗 0.5 耐久，单次挖掘最大消耗 ≤ 10（低耐久保护）
//
// 【获取】附魔台 / 宝箱 / 村民交易（RARE 稀有）
// 【最高等级】III
// ========================================================================
public class ChainBreakerEnchantment extends Enchantment {

    private static final int MAX_LEVEL = 3;

    public ChainBreakerEnchantment() {
        // EnchantmentCategory.DIGGER：适用于镐、斧、锹
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

    // 仅镐/斧/锹可附魔
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof DiggerItem;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return canEnchant(stack);
    }
}