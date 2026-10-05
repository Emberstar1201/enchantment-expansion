package com.github.emberstar1201.enchantmentex.entity.client;

import com.github.emberstar1201.enchantmentex.entity.ZombieGirlEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * 丧尸娘渲染器：
 * 主体模型为 ZombieGirlModel（Alex 细手玩家皮肤 UV）。
 * 刻意不注册任何盔甲渲染层——穿戴盔甲时不显示盔甲外观，
 * 但装备槽提供的护甲 / 韧性 / 击退抗性属性修饰符仍然正常生效
 * （属性由 LivingEntity 装备系统自动结算，与渲染无关）。
 * 贴图从实体按变种索引动态获取。
 */
public class ZombieGirlRenderer extends HumanoidMobRenderer<ZombieGirlEntity, ZombieGirlModel<ZombieGirlEntity>> {

    public ZombieGirlRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombieGirlModel(context.bakeLayer(ZombieGirlLayers.MAIN)), 0.5F);
        // 手持武器渲染层（不添加 HumanoidArmorLayer，盔甲不显示外观）
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(ZombieGirlEntity entity) {
        // 按实体变种索引动态选择贴图
        return entity.getVariantTexture();
    }
}
