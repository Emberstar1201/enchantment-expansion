package com.github.emberstar1201.enchantmentex.mixin;

import com.github.emberstar1201.enchantmentex.enchantment.SandevistanHandler;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ========================================================================
// 【斯安维斯坦】AI 慢放 Mixin（服务端/客户端通用，逻辑只对服务端生效）
//
// 目标：Mob#serverAiStep()
//
// 为什么需要这个 Mixin：
//   斯安维斯坦对生物的减速原本靠取消 LivingEvent.LivingTickEvent 实现。
//   但该事件在 LivingEntity.tick() 顶部触发，cancel 只能中断 baseTick 的
//   「移动」逻辑；怪物的「AI」——拉弓蓄力、弩装填、寻路目标选择等——
//   跑在 Mob#serverAiStep() 里，它由 Mob.tick() 在 super.tick() 之后单独
//   调用，LivingTickEvent 的 cancel 根本拦不住。
//   结果就是：移动慢放了、但骷髅/掠夺者照常满速拉弓装填（玩家观察到的现象）。
//
// 方案：
//   与 SandevistanHandler.shouldStallAI() 用完全相同的「慢放分母 + 相位错开」
//   判定，在应冻结的 tick 上提前取消 serverAiStep，使 AI 与移动 1/N 同步慢放。
//   激活者本人与 Boss 不在冻结范围（Boss 走缓慢药水路线），由判定函数过滤。
//
// 性能：shouldStallAI 先判服务端/玩家，再遍历 ACTIVE 表；时缓未激活时
//       ACTIVE 为空，立即返回 false，每个实体仅一次空 map 遍历，开销可忽略。
// ========================================================================
@Mixin(Mob.class)
public class SandevistanAIStallMixin {

    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void enchantmentEx$sandevistanStallServerAI(CallbackInfo ci) {
        // (Object)(Object)this 强转规避「this 引用未初始化的合成字段」编译告警
        Mob self = (Mob) (Object) this;
        // 该 tick 应冻结则该实体 AI 不推进
        if (SandevistanHandler.shouldStallAI(self)) {
            ci.cancel();
        }
    }
}
