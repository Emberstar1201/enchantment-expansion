package com.github.emberstar1201.enchantmentex.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// ========================================================================
// 「海洋之星」自定义物品类
//
// 防摧毁三重保险：
//   1. ModItems 注册时使用 .fireResistant()（Item.Properties 层面）
//   2. 本类重写 isFireResistant() 返回 true（类层面再次双保险，防 火 + 岩浆）
//   3. 重写 canBeDepleted() 返回 false，没有耐久耗尽摧毁路径
//
// 外观：附魔浮光（isFoil=true）。
// 描述：已隐藏（不重写 appendHoverText，物品提示中不再显示任何说明文本）。
// 被动效果（水下挖掘、水流免疫、守卫者中立等）由 OceanStarHandler 驱动。
// ========================================================================
public class OceanStarItem extends Item {

    public OceanStarItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // ========================================================================
    // 防火 + 防岩浆：类层面重写强制返回 true，与 Properties.fireResistant() 构成双保险
    // ========================================================================
    @Override
    public boolean isFireResistant() {
        return true;
    }

    // ========================================================================
    // 没有耐久概念：任何正常流程都不会将其“用光”
    // ========================================================================
    @Override
    public boolean canBeDepleted() {
        return false;
    }
}
