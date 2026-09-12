package com.github.emberstar1201.enchantmentex.item;

import net.minecraft.world.item.Item;

// ========================================================================
// 【星辉之星】自定义物品类
//
// 特性：
//   - 无附魔光效（isFoil=false）
//   - 手持效果：夜间移速 +20%、夜视、击杀经验掉落 ×2
//   - 盔甲嵌入：铁砧合并后效果相同
//   - 工作台合成：荧石 ×4 + 紫水晶碎片 ×4 + 钻石 ×1
//
// 效果实现见 StarlightStarHandler（纯事件驱动）
// ========================================================================
public class StarlightStarItem extends Item {

    public StarlightStarItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canBeDepleted() {
        return false;  // 无耐久概念
    }
}
