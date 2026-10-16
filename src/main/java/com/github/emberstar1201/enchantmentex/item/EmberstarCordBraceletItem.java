package com.github.emberstar1201.enchantmentex.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

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

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        // 用轮播文本展示能力，避免把整段效果说明压在一条提示中。
        int frame = (int) ((System.currentTimeMillis() / 700L) % 4L);
        tooltip.add(Component.translatable("tooltip.enchantment_expansion.emberstar_cord_bracelet.effect." + frame));
        tooltip.add(Component.literal("星尘为证，此心虽空，亦映微光")
                .withStyle(style -> style.withColor(0x9AA7D0)));
    }
}
