package com.github.emberstar1201.enchantmentex.mixin;

import com.github.emberstar1201.enchantmentex.MobBuffConfig;
import net.minecraft.world.entity.projectile.LargeFireball;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 只缩放恶魂大型火球的初始速度，不影响雪球、箭和其它弹射物。
@Mixin(LargeFireball.class)
public class GhastFireballSpeedMixin {

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;DDDI)V",
            at = @At("TAIL"))
    private void enchantmentEx$slowFireball(CallbackInfo ci) {
        LargeFireball fireball = (LargeFireball) (Object) this;
        if (MobBuffConfig.enabled) {
            fireball.setDeltaMovement(fireball.getDeltaMovement()
                    .scale(MobBuffConfig.ghastFireballSpeedMultiplier));
        }
    }
}
