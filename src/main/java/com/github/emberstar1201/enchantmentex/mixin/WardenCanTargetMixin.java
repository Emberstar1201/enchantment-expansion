package com.github.emberstar1201.enchantmentex.mixin;

import com.github.emberstar1201.enchantmentex.enchantment.DarkWalkerHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

// ========================================================================
// 【幽匿行者 · 监守者无视】Warden.canTargetEntity Mixin
//
// 目标：net.minecraft.world.entity.monster.warden.Warden#canTargetEntity(Entity)
//
// 为什么选这一个方法：
//   监守者(Warden) 的一切"检测到玩家"的途径最终都会汇聚到
//   canTargetEntity(Entity)——它作为统一过滤器被用于：
//
//   a) WardenEntitySensor 实体传感器（24 格范围内感知可攻击实体）：
//      getClosest() 内部用 canTargetEntity() 过滤 → 玩家不会被列为
//      NEAREST_ATTACKABLE。
//   b) 振动接收（VibrationUser.canReceiveVibration）：
//      可接收振动前也调用 canTargetEntity 过滤振动源 → 玩家无效。
//   c) 贴脸碰撞（doPush → increaseAngerAt → canTargetEntity）：
//      撞到玩家不会涨怒气。
//   d) 被攻击（entity.hurt → increaseAngerAt → canTargetEntity）：
//      玩家打监守者不会涨怒气（但监守者仍可能因其他生物/机制行动）。
//
//   因此只要把这个方法的返回值对"幽匿行者庇护的玩家"改为 false，
//   监守者就彻底无法检测到该玩家——
//   "即使玩家在它面前走动、奔跑、挖掘，甚至贴脸"，监守者也会当
//   玩家不存在。
// ========================================================================
@Mixin(Warden.class)
public class WardenCanTargetMixin {

    @Inject(method = "canTargetEntity(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void enchantmentEx$darkWalkerInvisible(@Nullable Entity target,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof Player player && DarkWalkerHandler.isProtected(player)) {
            // 幽匿行者庇护：监守者无法将玩家视为可攻击/可检测目标
            cir.setReturnValue(false);
        }
    }
}