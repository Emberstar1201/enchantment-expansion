package com.github.emberstar1201.enchantmentex.item.handler;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;

// ========================================================================
// 【星星嵌入工具类】用于在盔甲物品提示中显示嵌入的星星信息
// ========================================================================
public class StarEmbedUtils {

    /**
     * 根据盔甲的 NBT 标签，向物品提示列表中添加嵌入星星的信息
     * 
     * @param armorStack 盔甲物品
     * @param tooltip 物品提示列表（会直接修改）
     */
    public static void appendEmbeddedStarInfo(ItemStack armorStack, List<Component> tooltip) {
        if (!armorStack.hasTag()) {
            return;
        }

        String embeddedStar = armorStack.getTag().getString("EmbeddedStar");
        if (embeddedStar == null || embeddedStar.isEmpty()) {
            return;
        }

        // 根据不同的星星类型，显示对应的效果提示
        switch (embeddedStar) {
            case "end_star":
                // 终界之星嵌入效果提示
                tooltip.add(Component.literal(""));  // 空行分隔
                tooltip.add(Component.translatable("item.enchantment_expansion.embedded_end_star.desc"));
                break;
            case "ocean_star":
                // 海洋之星嵌入效果提示
                tooltip.add(Component.literal(""));  // 空行分隔
                tooltip.add(Component.translatable("item.enchantment_expansion.embedded_ocean_star.desc"));
                break;
            case "life_star":
                // 生命之星嵌入效果提示
                tooltip.add(Component.literal(""));  // 空行分隔
                tooltip.add(Component.translatable("item.enchantment_expansion.embedded_life_star.desc"));
                break;
        }
    }

    /**
     * 判断盔甲是否嵌入了指定类型的星星
     * 
     * @param armorStack 盔甲物品
     * @param starType 星星类型（"end_star" / "ocean_star" / "life_star"）
     * @return 是否嵌入了该星星
     */
    public static boolean hasEmbeddedStar(ItemStack armorStack, String starType) {
        if (!armorStack.hasTag()) {
            return false;
        }

        String embedded = armorStack.getTag().getString("EmbeddedStar");
        return starType.equals(embedded);
    }
}
