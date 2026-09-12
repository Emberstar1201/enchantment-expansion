package com.github.emberstar1201.enchantmentex.item;

import net.minecraft.world.item.Item;

// ========================================================================
// 【虚空之星】自定义物品类
//
// 特性：
//   - 无附魔光效（isFoil=false）
//   - 手持效果：免疫摔落伤害、免疫虚空伤害、坠入虚空时传送回出生点
//   - 盔甲嵌入：铁砧合并后效果相同
//   - 工作台合成：黑曜石 ×4 + 末影珍珠 ×4 + 紫颂果 ×1
//
// 效果实现见 VoidStarHandler（纯事件驱动）
// ========================================================================
public class VoidStarItem extends Item {

    public VoidStarItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canBeDepleted() {
        return false;  // 无耐久概念
    }
}
