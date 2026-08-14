package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// "匠心传承"附魔 - 镐、斧、锹通用
//
// 【效果】
//   工具会"记住"挖掘过的方块类型（最多5种），对同类方块挖掘速度逐渐提升，
//   最高2倍。对已记忆方块有几率额外掉落1个，最高约20%。
//   数据持久化存储在工具NBT中，丢出/存放不丢失。
//
// 【获取】仅遗迹宝箱（isTreasureOnly = true，附魔台无法获得）
// ========================================================================
public class ArtisanLegacyEnchantment extends Enchantment {

    private static final int MAX_LEVEL = 1;

    public ArtisanLegacyEnchantment() {
        // Rarity.RARE：稀有度"稀有"
        // EnchantmentCategory.DIGGER：镐、斧、锹
        // EquipmentSlot.MAINHAND：仅主手生效
        super(Rarity.RARE, EnchantmentCategory.DIGGER,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND});
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
        return 25;
    }

    @Override
    public int getMaxCost(int level) {
        return 45;
    }

    // 宝藏附魔：仅遗迹宝箱可获得，附魔台不可获得
    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    @Override
    public boolean isTradeable() {
        return true; // 村民交易可获得（宝箱也能出）
    }

    @Override
    public boolean isDiscoverable() {
        return true; // 宝箱中可发现
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // 镐、斧、锹均可附魔
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof DiggerItem;
    }

    // ========================================================================
    // 对外 API
    // ========================================================================
    public static int getMaxLevelStatic() {
        return MAX_LEVEL;
    }
}