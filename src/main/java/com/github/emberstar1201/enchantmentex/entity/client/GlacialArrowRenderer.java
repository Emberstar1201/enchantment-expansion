package com.github.emberstar1201.enchantmentex.entity.client;

import com.github.emberstar1201.enchantmentex.entity.GlacialArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

// ========================================================================
// 琉璃冰魄箭渲染器
//
// 复用原版箭矢的模型，暂使用原版箭头纹理
// 如需自定义纹理，替换 TEXTURE 为 mod 专属路径并放置对应 png 文件
// ========================================================================
public class GlacialArrowRenderer extends ArrowRenderer<GlacialArrowEntity> {

    // 使用原版箭头纹理（无需额外资源即可正常渲染）
    private static final ResourceLocation TEXTURE =
            ResourceLocation.of("minecraft:textures/entity/projectiles/arrow.png", ':');

    public GlacialArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(GlacialArrowEntity entity) {
        return TEXTURE;
    }
}