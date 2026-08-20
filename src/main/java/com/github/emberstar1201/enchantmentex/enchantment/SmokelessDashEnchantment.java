package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// "无烟冲击"（Smokeless Dash）鞘翅附魔
//
// 【适用物品】鞘翅（ARMOR_CHEST 分类 + 运行时检查仅鞘翅生效）
// 【最高等级】I（1级）
// 【获取方式】附魔台 / 宝箱 / 村民交易
//
// 【效果】
//   鞘翅飞行时无需烟花火箭即可加速/减速。
//   鼠标滚轮：向上滚加速，向下滚减速。
//   速度范围：原版滑翔速度 → 烟花火箭速度 × 1.5（可配置）
//   速度调整平滑无突变。
// ========================================================================
public class SmokelessDashEnchantment extends Enchantment {

    public SmokelessDashEnchantment() {
        // Rarity.RARE：附魔台可出，宝箱常见
        // EnchantmentCategory.ARMOR_CHEST：胸甲/鞘翅
        // EquipmentSlot.CHEST：仅胸甲位生效
        super(Rarity.RARE, EnchantmentCategory.ARMOR_CHEST,
                new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 1; // 仅 I 级
    }

    @Override
    public int getMinCost(int level) {
        return 20;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 30;
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
}