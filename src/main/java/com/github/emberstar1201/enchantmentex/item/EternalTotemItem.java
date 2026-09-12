package com.github.emberstar1201.enchantmentex.item;

import net.minecraft.world.item.Item;

// ========================================================================
// 【永恒图腾】自定义物品类
//
// 设计要点：
//   - 复用原版不死图腾（minecraft:totem_of_undying）的纹理，由模型 JSON 指定。
//   - 耐久 20 点，每抵挡一次致命伤消耗 1 点，耐久耗尽则图腾破碎消失。
//   - 始终带附魔光效（isFoil=true），作为"强化版不死图腾"的视觉区分。
//   - 与生命之星联动：回满时使用 player.getMaxHealth()，
//     若玩家已嵌入/手持生命之星，会自动回满到加成后的上限（50 点）。
//
// 效果实现见 EternalTotemHandler（LivingDamageEvent 驱动）
// ========================================================================
public class EternalTotemItem extends Item {

    /** 耐久上限：可抵挡 20 次致命伤 */
    public static final int MAX_DURABILITY = 20;

    public EternalTotemItem(Properties properties) {
        super(properties);
    }

    // 始终显示附魔光效，与原版不死图腾（无附魔光效）形成视觉区分
    @Override
    public boolean isFoil(net.minecraft.world.item.ItemStack stack) {
        return true;
    }

    // 有耐久概念，可被消耗
    @Override
    public boolean canBeDepleted() {
        return true;
    }
}
