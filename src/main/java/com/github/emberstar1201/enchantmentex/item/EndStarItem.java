package com.github.emberstar1201.enchantmentex.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// ========================================================================
// 【终界之星】自定义物品类
//
// 继承原版 Item，唯一重写的方法是 isFoil() 返回 true，
// 使终界之星拥有与下界之星（Nether Star）相同的动态附魔光效（闪亮紫色光泽）。
//
// 附魔光效（也叫 foil/glint）在以下场景可见：
//   - 创造模式/背包物品图标上（紫白色闪烁叠加层）
//   - 手持时第一人称物品模型上
//   - 物品展示框中
//   - 地上的 ItemEntity 物品实体上（物品掉落时）
//
// 其他效果（飞行、减伤、伤害加成、粒子等）通过 EndStarHandler 事件驱动，
// 不写在本类中，保持职责分离。
// ========================================================================
public class EndStarItem extends Item {

    public EndStarItem(Properties properties) {
        super(properties);
    }

    // ========================================================================
    // 重写 isFoil()，使物品始终带有附魔光效
    // 与原版下界之星（NetherStarItem）的实现方式相同
    // 返回 true 时，物品模型会叠加一层彩色闪烁的附魔纹理
    //
    // 原版 Item.isFoil() 默认返回 stack.isEnchanted()，
    // 即只有物品有附魔时才显示光效；
    // 这里重写为无条件返回 true，无论是否附魔都始终有光效。
    // ========================================================================
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
