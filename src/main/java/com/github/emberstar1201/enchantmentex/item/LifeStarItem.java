package com.github.emberstar1201.enchantmentex.item;

import net.minecraft.world.item.Item;

// ========================================================================
// 【生命之星】自定义物品类
//
// 特性：
//   - 无附魔光效（isFoil=false），描述已隐藏（不重写 appendHoverText）
//   - 手持效果：生命值上限+30 (20→50)、回血速度×2、饥饿盾
//   - 盔甲嵌入：铁砧合并后效果相同
//   - 工作台合成：9种花卉（严格颜色匹配）
// ========================================================================
public class LifeStarItem extends Item {

    public LifeStarItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFireResistant() {
        return false;  // 生命之星无防火需求
    }

    @Override
    public boolean canBeDepleted() {
        return false;  // 无耐久概念
    }
}
