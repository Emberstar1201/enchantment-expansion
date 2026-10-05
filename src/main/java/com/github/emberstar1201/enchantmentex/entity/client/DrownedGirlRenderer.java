package com.github.emberstar1201.enchantmentex.entity.client;

import com.github.emberstar1201.enchantmentex.entity.DrownedGirlEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * 溺尸娘渲染器：复用玩家 UV 模型，同时保留溺尸的外层鳃/耳朵视觉。
 * 与丧尸娘一致：不注册盔甲渲染层，穿盔甲只加属性、不显示外观。
 */
public class DrownedGirlRenderer extends HumanoidMobRenderer<DrownedGirlEntity, ZombieGirlModel<DrownedGirlEntity>> {
    public DrownedGirlRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombieGirlModel(context.bakeLayer(ZombieGirlLayers.MAIN)), 0.5F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(DrownedGirlEntity entity) {
        return entity.getVariantTexture();
    }
}
