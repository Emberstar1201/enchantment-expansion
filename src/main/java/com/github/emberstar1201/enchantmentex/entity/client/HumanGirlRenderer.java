package com.github.emberstar1201.enchantmentex.entity.client;

import com.github.emberstar1201.enchantmentex.entity.SurvivorGirlEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * 幸存者少女渲染器：
 * 完全复用丧尸娘的 Alex 细手 64x64 玩家皮肤 UV 模型（同一套骨骼与动画），
 * 仅贴图换成 human_girl 系列人类皮肤。
 * 与丧尸娘一致不注册盔甲渲染层：盔甲只提供装备属性，不显示外观。
 */
public class HumanGirlRenderer
        extends HumanoidMobRenderer<SurvivorGirlEntity, ZombieGirlModel<SurvivorGirlEntity>> {

    public HumanGirlRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombieGirlModel<>(context.bakeLayer(ZombieGirlLayers.MAIN)), 0.5F);
        // 手持武器渲染层（不添加 HumanoidArmorLayer，盔甲不显示外观）
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(SurvivorGirlEntity entity) {
        // 按变种索引动态选择 human_girl / human_girl_1 ~ 5
        return entity.getVariantTexture();
    }
}
