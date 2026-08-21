package com.github.emberstar1201.enchantmentex.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// ========================================================================
// 「海洋之星」自定义物品类
//
// 仅定义物品自身的属性与展示（名称/浮光/描述提示），
// 所有被动效果（水下挖掘、水流免疫、守卫者中立等）由
// OceanStarHandler 通过事件驱动实现，保持职责分离。
//
// 外观：附魔浮光（isFoil=true），与终界之星一致。
// 描述：读取语言文件 item.enchantment_expansion.ocean_star.desc。
// ========================================================================
public class OceanStarItem extends Item {

    public OceanStarItem(Properties properties) {
        super(properties);
    }

    // 始终带附魔浮光（闪亮光效），彰显其宝物身份
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // 悬停提示：显示海洋之星的效果说明（多语言由语言文件提供）
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.enchantment_expansion.ocean_star.desc"));
    }
}