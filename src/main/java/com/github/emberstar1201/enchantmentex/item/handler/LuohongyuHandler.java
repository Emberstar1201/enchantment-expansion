package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.item.ModItems;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class LuohongyuHandler {
    private static final int EFFECT_DURATION_TICKS = 100;

    private LuohongyuHandler() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }

        if (!player.getMainHandItem().is(ModItems.LUOHONGYU.get())) {
            return;
        }

        LivingEntity target = event.getEntity();
        if (!target.isAlive() || target == player) {
            return;
        }

        // 服务端直接给命中目标施加效果，保证单人和联机行为一致。
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, EFFECT_DURATION_TICKS, 2));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_DURATION_TICKS, 2));
    }
}
