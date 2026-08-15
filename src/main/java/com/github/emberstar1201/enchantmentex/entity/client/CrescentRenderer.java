package com.github.emberstar1201.enchantmentex.entity.client;

import com.github.emberstar1201.enchantmentex.entity.CrescentEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

// ========================================================================
// 月牙形剑气渲染器
//
// 剑气实体本身不渲染模型，视觉表现完全由粒子特效完成。
// 此渲染器仅返回一个空纹理，避免客户端崩溃（实体必须有渲染器注册）。
// ========================================================================
public class CrescentRenderer extends EntityRenderer<CrescentEntity> {

    public CrescentRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(CrescentEntity entity) {
        // 返回空纹理（视觉完全由粒子负责）
        return ResourceLocation.of("minecraft:textures/particle/note.png", ':');
    }
}