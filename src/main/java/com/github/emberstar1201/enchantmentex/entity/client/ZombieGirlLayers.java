package com.github.emberstar1201.enchantmentex.entity.client;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

/**
 * 丧尸娘模型层定位。
 * 刻意不提供任何盔甲模型层：丧尸娘 / 溺尸娘穿戴盔甲时不显示外观，
 * 装备属性（护甲 / 韧性 / 击退抗性）仍由装备槽正常结算。
 */
public class ZombieGirlLayers {
    public static final ModelLayerLocation MAIN =
            new ModelLayerLocation(new ResourceLocation(EnchantmentExpansion.MODID, "zombie_girl"), "main");
}
