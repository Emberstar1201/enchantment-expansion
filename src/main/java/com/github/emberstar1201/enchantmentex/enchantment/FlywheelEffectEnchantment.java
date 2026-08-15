package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 【飞轮效应】附魔类
//
// 适用位置：靴子（ARMOR_FEET）
// 最高等级：III（3级）
// 获取方式：仅宝箱获取（isTreasureOnly = true，附魔台不可获得）
// 效果：按 R 键朝准心冲刺，期间无敌可穿透实体，撞墙停下
// ========================================================================
public class FlywheelEffectEnchantment extends Enchantment {

    public FlywheelEffectEnchantment() {
        // Rarity.VERY_RARE → 宝藏附魔，附魔台不可获取，仅遗迹宝箱生成
        // EnchantmentCategory.ARMOR_FEET → 仅靴子
        super(Rarity.VERY_RARE, EnchantmentCategory.ARMOR_FEET,
                new EquipmentSlot[]{EquipmentSlot.FEET});
    }

    @Override
    public int getMaxLevel() {
        return 3; // I~III 级
    }

    // 仅宝箱生成（宝藏附魔），附魔台不可获取
    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    // 可与村民交易获得（附魔书）
    @Override
    public boolean isTradeable() {
        return true;
    }

    // 附魔台不可发现（与 isTreasureOnly 配合）
    @Override
    public boolean isDiscoverable() {
        return false;
    }

    // 允许附魔书存在
    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }
}