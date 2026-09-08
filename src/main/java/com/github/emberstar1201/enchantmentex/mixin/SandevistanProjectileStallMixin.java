package com.github.emberstar1201.enchantmentex.mixin;

import com.github.emberstar1201.enchantmentex.enchantment.SandevistanHandler;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ========================================================================
// 【斯安维斯坦】弹射物慢放 Mixin（服务端/客户端通用，逻辑只对服务端生效）
//
// 目标：Projectile#tick() 与 AbstractArrow#tick()
//
// 为什么需要同时拦截两个类：
//   箭/三叉戟（AbstractArrow）重写了 tick()，根本不走父类 Projectile#tick()，
//   所以若只注入 Projectile 会漏掉弓/弩射出的箭（玩家实测：普通弓射箭没被暂停）。
//   其他不重写 tick 的投掷物（雪球、末影之眼等）走 Projectile#tick。
//   因此对两个类各注入相同判定，覆盖全部弹射物。
//
// 方案（与 AI 慢放同理）：
//   在 tick() 的 HEAD 用 shouldStallProjectile() 判定，返回 true 时取消这一 tick
//   → 弹射物不位移、不加速、不重力、不重算朝向，保持原飞行姿态缓慢飘浮；
//     时停解除后 tick 恢复，按原本 deltaMovement（原方向/原速度）继续飞行。
//
// 性能：shouldStallProjectile 先判服务端/激活表/配置，未激活时 ACTIVE 为空
//       立即返回 false，开销可忽略。
// ========================================================================
@Mixin(Projectile.class)
public abstract class SandevistanProjectileStallMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void enchantmentEx$sandevistanStallProjectileTick(CallbackInfo ci) {
        // (Object)(Object)this 强转规避「this 引用未初始化的合成字段」编译告警
        Projectile self = (Projectile) (Object) this;
        if (SandevistanHandler.shouldStallProjectile(self)) {
            ci.cancel();
        }
    }

    // AbstractArrow 重写了 tick，必须在子类上单独拦截，否则箭头漏判
    @Mixin(AbstractArrow.class)
    public static class AbstractArrowInject {

        @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
        private void enchantmentEx$sandevistanStallArrowTick(CallbackInfo ci) {
            AbstractArrow self = (AbstractArrow) (Object) this;
            if (SandevistanHandler.shouldStallProjectile(self)) {
                ci.cancel();
            }
        }
    }
}
