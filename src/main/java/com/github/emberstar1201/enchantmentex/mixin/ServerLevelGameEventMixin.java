package com.github.emberstar1201.enchantmentex.mixin;

import com.github.emberstar1201.enchantmentex.enchantment.DarkWalkerHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ========================================================================
// 【幽匿行者 · 振动抑制】ServerLevel.gameEvent Mixin
//
// 目标：net.minecraft.server.level.ServerLevel#gameEvent(GameEvent, Vec3,
//       GameEvent.Context) —— 世界内所有振动（vibration）的统一分派入口。
//
// 原理：
//   原版所有\"振动\"本质上是一个 GameEvent（游戏事件），从振动源传播到
//   监听方（幽匿感测体、幽匿尖啸体、监守者等）。例如：
//     - 玩家走动/奔跑  →  STEP / FLAP（落地拍动）
//     - 玩家挖掘       →  BLOCK_DESTROY / BLOCK_PLACE
//     - 玩家掷出/命中  →  PROJECTILE_SHOOT / PROJECTILE_LAND
//   这些事件最终都汇聚到 ServerLevel.gameEvent() 做分派。
//
//   本 Mixin 在分派入口检查事件来源实体：
//     若来源是「幽匿行者」激活状态下的玩家（深暗之域 + 靴子附魔），
//     直接拦截（不向任何监守者/幽匿方块传播）。
//   → 效果：玩家在深暗之域中完全\"不产生振动\"，幽匿感测体、幽匿尖啸体
//     永远不会因玩家而触发，监守者也收不到来自玩家的振动信号。
//
// 注意：客户端振动不在此列（服务端才是监守者 AI / 方块触发的主逻辑所在），
//      因此只需 Mixin 服务端 ServerLevel 即可。
// ========================================================================
@Mixin(ServerLevel.class)
public class ServerLevelGameEventMixin {

    // 使用带完整描述符的方法名，避免与 Level 中其他 gameEvent 重载产生歧义
    @Inject(method = "gameEvent(Lnet/minecraft/world/level/gameevent/GameEvent;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
            at = @At("HEAD"), cancellable = true)
    private void enchantmentEx$suppressDarkWalkerVibrations(
            GameEvent event, Vec3 position, GameEvent.Context context, CallbackInfo ci) {
        // 提取振动来源实体：行走/挖掘等由玩家直接触发的振动，
        // 来源实体就是玩家本身（Context.sourceEntity()）
        Entity sourceEntity = context.sourceEntity();
        if (sourceEntity instanceof Player player
                && DarkWalkerHandler.isProtected(player)) {
            // 幽匿行者激活中：一律不产生振动 → 直接取消本次振动分派
            ci.cancel();
        }
    }
}