package com.github.emberstar1201.enchantmentex.item;

import net.minecraft.world.item.Item;

/**
 * 星火之绳手环的物品定义。
 *
 * 饰品栏适配和被动效果由 EmberstarCordBraceletHandler 统一处理，
 * 这样副手、Curios 槽位和后续可能增加的装备入口可以共享同一套判定。
 */
public class EmberstarCordBraceletItem extends Item {
    public EmberstarCordBraceletItem(Properties properties) {
        super(properties);
    }
}
