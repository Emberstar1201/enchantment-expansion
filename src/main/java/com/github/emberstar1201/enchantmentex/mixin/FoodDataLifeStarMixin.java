package com.github.emberstar1201.enchantmentex.mixin;

import com.github.emberstar1201.enchantmentex.item.handler.LifeStarHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ========================================================================
// 【生命之星】饥饿/饱和度下降速度 −50%  Mixin（自然回血侧）
//
// 原版自然回血写在 FoodData#tick(Player) 内部，直接调用
// FoodData#addExhaustion，不经过 Player#causeFoodExhaustion，
// 所以 PlayerFoodExhaustionMixin 覆盖不到它。
//
// 例：饱食度 ≥20 且饱和度 >0 时，每 10 tick 回 min(饱和度,6)/6 点血，
//     同时 addExhaustion(min(饱和度,6))——回血越快，饱和度掉得越快。
//     用户明确要求这部分消耗同样减半。
//
// 实现：FoodData 本身不持有玩家引用，因此先在 tick 开头把玩家存进
//       @Unique 字段，再用 @ModifyArg 把 addExhaustion 的参数乘 0.5。
//       FoodData 是每个玩家一份的实例，字段不存在串号问题。
// ========================================================================
@Mixin(FoodData.class)
public class FoodDataLifeStarMixin {

    // 记录当前正在 tick 的玩家（每个 FoodData 实例对应一个玩家）
    @Unique
    private Player enchantmentEx$lifeStarTickPlayer;

    @Inject(method = "tick", at = @At("HEAD"))
    private void enchantmentEx$captureTickPlayer(Player player, CallbackInfo ci) {
        this.enchantmentEx$lifeStarTickPlayer = player;
    }

    // 同时作用于 tick 里的两处调用：
    //   ① addExhaustion(min(饱和度, 6))   ② addExhaustion(6.0F)
    @ModifyArg(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V"),
            index = 0
    )
    private float enchantmentEx$halveRegenExhaustion(float amount) {
        Player player = this.enchantmentEx$lifeStarTickPlayer;
        if (player != null && LifeStarHandler.isHoldingOrWearingLifeStar(player)) {
            return amount * 0.5F;
        }
        return amount;
    }
}
