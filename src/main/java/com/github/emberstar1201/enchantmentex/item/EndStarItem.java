package com.github.emberstar1201.enchantmentex.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// ========================================================================
// 【终界之星】自定义物品类
//
// 继承原版 Item，重写以下核心方法：
//   1. isFoil() 返回 true → 始终带有附魔光效（闪亮紫色光泽）
//   2. isFireResistant() 返回 true → 防火 + 防岩浆（双重保险）
//   3. canBeDepleted() 返回 false → 不存在“耐久耗尽摧毁”的风险
// ========================================================================
public class EndStarItem extends Item {

    public EndStarItem(Properties properties) {
        // 注意：ModItems 注册时已额外配置 .fireResistant() / .rarity / stacksTo
        // 这里保留父类构造，属性以 Properties 为准，但本类对防火做双保险覆盖
        super(properties);
    }

    // ========================================================================
    // 附魔光效（紫色闪烁附魔纹理叠加层）
    // 与原版下界之星 NetherStarItem 相同的实现方式
    // ========================================================================
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // ========================================================================
    // 【防摧毁 1/3 + 2/3】isFireResistant()
    // 防火 + 防岩浆：即使外部构造 Item.Properties 时忘记 .fireResistant()，
    // 此方法也会强制返回 true，防止终界之星被火焰 / 岩浆销毁。
    // （ItemEntity 判定是否可燃主要依据此项）
    // ========================================================================
    @Override
    public boolean isFireResistant() {
        return true;
    }

    // ========================================================================
    // canBeDepleted() 返回 false：终界之星没有“耐久”概念，
    // 任何正常游戏流程都无法“用光它的耐久把它销毁”。
    // 配合仙人掌/岩浆/火焰保护实现三重防摧毁。
    // ========================================================================
    @Override
    public boolean canBeDepleted() {
        return false;
    }
}

