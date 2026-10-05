package com.github.emberstar1201.enchantmentex.mixin;

import com.github.emberstar1201.enchantmentex.entity.ZombieGirlEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;

/**
 * 铁傀儡攻击目标 Mixin：
 *   原版铁傀儡在 registerGoals 中注册了
 *   NearestAttackableTargetGoal(Mob.class, 5, false, false,
 *       target -> target instanceof Enemy && !(target instanceof Creeper))。
 *   丧尸娘是 Enemy 型亡灵，会被该目标当成敌对生物攻击。
 *
 *   这里在 registerGoals 执行完毕后，把该目标替换为
 *   额外排除 ZombieGirlEntity 的版本（Player.class 目标不受影响）。
 */
@Mixin(IronGolem.class)
public abstract class IronGolemTargetMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void enchantmentExpansion$excludeZombieGirl(CallbackInfo ci) {
        IronGolem golem = (IronGolem) (Object) this;
        Set<WrappedGoal> goals = golem.targetSelector.getAvailableGoals();

        // 找出以 Mob.class 为搜索类型的目标（不找 Player.class 的 isAngryAt 目标）
        List<WrappedGoal> mobGoals = goals.stream().filter(wrapped -> {
            Goal goal = wrapped.getGoal();
            return goal instanceof NearestAttackableTargetGoal<?> target
                    && ((NearestAttackableTargetGoalAccessor) target).getTargetType() == Mob.class;
        }).toList();

        for (WrappedGoal wrapped : mobGoals) {
            int priority = wrapped.getPriority();
            goals.remove(wrapped);
            golem.targetSelector.addGoal(priority, new NearestAttackableTargetGoal<>(
                    golem, Mob.class, 5, false, false,
                    target -> target instanceof Enemy
                            && !(target instanceof Creeper)
                            && !(target instanceof ZombieGirlEntity)));
        }
    }
}
