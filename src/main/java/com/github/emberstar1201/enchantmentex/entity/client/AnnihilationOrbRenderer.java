package com.github.emberstar1201.enchantmentex.entity.client;

import com.github.emberstar1201.enchantmentex.entity.AnnihilationOrbEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** 大球本体不绘制模型，视觉由低数量粒子提供。 */
public class AnnihilationOrbRenderer extends EntityRenderer<AnnihilationOrbEntity> {
    public AnnihilationOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(AnnihilationOrbEntity entity) {
        return ResourceLocation.of("minecraft:textures/particle/note.png", ':');
    }
}
