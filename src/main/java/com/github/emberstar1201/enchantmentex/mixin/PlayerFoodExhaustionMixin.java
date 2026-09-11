package com.github.emberstar1201.enchantmentex.mixin;

import com.github.emberstar1201.enchantmentex.item.handler.LifeStarHandler;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ========================================================================
// 【生命之星】饥饿/饱和度下降速度 −50%  Mixin（玩家侧入口）
//
// 为什么必须用 Mixin：
//   原版的饥饿消耗统一走 Player#causeFoodExhaustion(float)
//   （冲刺 0.1、跳跃 0.05、挖掘 0.005、受到伤害 0.1 等），
//   该方法最终调用 FoodData#addExhaustion。
//   Forge 1.20.1 没有针对 causeFoodExhaustion / FoodData 的事件，
//   事件方案无法实现，所以在这里把参数直接乘 0.5。
//
// FoodData#tick 里"自然回血"所消耗的饱和度不经过本方法，
// 由同包的 FoodDataLifeStarMixin 处理。
// ========================================================================
@Mixin(Player.class)
public class PlayerFoodExhaustionMixin {

    @Inject(method = "causeFoodExhaustion", at = @At("HEAD"), cancellable = true)
    private void enchantmentEx$lifeStarHalveExhaustion(float amount, CallbackInfo ci) {
        Player self = (Player) (Object) this;

        // 复刻原版前置条件：客户端不处理、创造/无敌模式不消耗
        // （这里如果不复刻，取消原方法后就会绕过这两个判断，属于改变原版行为）
        if (self.level().isClientSide() || self.getAbilities().invulnerable) {
            return;
        }

        // 未手持/未装备生命之星 → 走原版逻辑，不做任何改动
        if (!LifeStarHandler.isHoldingOrWearingLifeStar(self)) {
            return;
        }

        // 手持（主/副手）或盔甲内嵌生命之星 → 消耗减半
        self.getFoodData().addExhaustion(amount * 0.5F);
        ci.cancel(); // 取消原方法，避免再加满一份
    }
}
