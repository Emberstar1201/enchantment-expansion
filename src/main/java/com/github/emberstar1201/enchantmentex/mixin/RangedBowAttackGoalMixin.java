package com.github.emberstar1201.enchantmentex.mixin;

import com.github.emberstar1201.enchantmentex.MobBuffConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// ========================================================================
// 【骷髅系 · 拉弓更快】RangedBowAttackGoal.tick Mixin
//
// 原版逻辑（已用 javap 反编译确认）：
//     int i = this.mob.getTicksUsingItem();
//     if (i >= 20) {                       // ← 20 是硬编码，没有配置项
//         this.mob.stopUsingItem();
//         ((RangedAttackMob) this.mob).performRangedAttack(target, BowItem.getPowerForTime(i));
//         this.attackTime = this.attackIntervalMin;
//     }
//
// 为什么不用 setMinAttackInterval：
//   那个 API 只改「两箭之间的间隔」，改不了「拉满弓需要多久」。
//
// 实现思路（为什么是 @Redirect 而不是 @ModifyConstant）：
//   直接把常量 20 改小，会让 getPowerForTime(i) 收到更小的 i，
//   箭会变成「半蓄力」——伤害和初速度都会掉，反而削弱了骷髅。
//   这里改为 Redirect getTicksUsingItem()，把「实际拉弓 tick 按比例放大」：
//     实际拉 10 tick → 返回 20 → 既满足 i >= 20 的判定提前放箭，
//     又让 getPowerForTime 拿到 20，射出的仍是满蓄力箭。
//
// 只对 AbstractSkeleton（骷髅 / 流浪者 / 凋灵骷髅）生效，
// 避免影响其它模组里用同一个 AI 的生物。
// ========================================================================
@Mixin(RangedBowAttackGoal.class)
public class RangedBowAttackGoalMixin {

    @Redirect(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Mob;getTicksUsingItem()I"))
    private int enchantmentEx$fasterBowDraw(Mob mob) {
        int actualTicks = mob.getTicksUsingItem();
        if (!MobBuffConfig.enabled || !(mob instanceof AbstractSkeleton)) {
            return actualTicks;
        }
        int drawTicks = MobBuffConfig.getSkeletonBowDrawTicks();
        // drawTicks >= 20 表示维持原版行为，直接返回原值，避免做无意义的乘法
        if (drawTicks >= 20 || drawTicks <= 0) {
            return actualTicks;
        }
        // 用 long 运算防止极端数值下溢出；结果最大 20，不会超出原版阈值语义
        return (int) ((long) actualTicks * 20L / drawTicks);
    }
}
