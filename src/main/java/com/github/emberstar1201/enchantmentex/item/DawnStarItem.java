package com.github.emberstar1201.enchantmentex.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// ========================================================================
// 【晨曦之星】自定义物品类
//
// 特性：
//   - 无附魔光效（isFoil=false）
//   - 手持/盔甲嵌入效果：击杀积累「晨光」，满层自动爆发「晨曦」
//   - 获取方式：完成成就「我们逝去，我们永恒」后，于日落到日出之间
//     手持附魔拂晓的武器再度击杀凋零（详见 DawnStarHandler）
//
// 层数与状态显示在物品 lore 上（写入 ItemStack NBT display.Lore）
// 效果实现见 DawnStarHandler（纯事件驱动）
// ========================================================================
public class DawnStarItem extends Item {

    public DawnStarItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canBeDepleted() {
        return false;  // 无耐久概念
    }

    // lore（晨光条 / 状态倒计时）会定期重写 display.Lore，属纯显示用的 NBT 变动。
    // 若沿用默认实现（!oldStack.equals(newStack)），手持时每次刷新都会触发换手动画，
    // 表现为物品上下抽动。这里只在真正切换物品（槽位变化 / 物品类型不同）时播放动画。
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (slotChanged) return true;
        return !oldStack.is(newStack.getItem());
    }
}
