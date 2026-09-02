package com.github.emberstar1201.enchantmentex.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// ========================================================================
// 【强化卷轴】自定义物品类
//
// 一次性消耗品：在铁砧中与附魔装备 / 附魔书一同放入时，
// 随机选一个已有附魔提升一级；若该附魔已满级，则额外添加一个随机附魔。
// 仅能从遗迹宝箱中以 10% 概率获得（见 EnhancementScrollHandler 战利品注入）。
//
// 继承原版 Item，重写：
//   1. isFoil() 返回 true → 始终带有附魔光效（紫色闪烁）
//   2. isFireResistant() 返回 true → 防火 + 防岩浆（宝物防摧毁）
// ========================================================================
public class EnhancementScrollItem extends Item {

    public EnhancementScrollItem(Properties properties) {
        super(properties);
    }

    // 附魔光效：卷轴带有神秘紫色闪烁
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // 防火 + 防岩浆：防止宝物被销毁
    @Override
    public boolean isFireResistant() {
        return true;
    }
}
