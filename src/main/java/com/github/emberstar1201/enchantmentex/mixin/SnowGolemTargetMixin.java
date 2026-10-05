package com.github.emberstar1201.enchantmentex.mixin;

import com.github.emberstar1201.enchantmentex.entity.ZombieGirlEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;

/**
 * 雪傀儡攻击目标 Mixin：
 *   原版雪傀儡目标为 NearestAttackableTargetGoal(Mob.class, 10, true, false,
 *       target -> target instanceof Enemy)。
 *   替换为额外排除 ZombieGirlEntity 的版本，雪傀儡不再朝她扔雪球。
 */
@Mixin(SnowGolem.class)
public abstract class SnowGolemTargetMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void enchantmentExpansion$excludeZombieGirl(CallbackInfo ci) {
        SnowGolem golem = (SnowGolem) (Object) this;
        Set<WrappedGoal> goals = golem.targetSelector.getAvailableGoals();

        List<WrappedGoal> mobGoals = goals.stream().filter(wrapped -> {
            Goal goal = wrapped.getGoal();
            return goal instanceof NearestAttackableTargetGoal<?> target
                    && ((NearestAttackableTargetGoalAccessor) target).getTargetType() == Mob.class;
        }).toList();

        for (WrappedGoal wrapped : mobGoals) {
            int priority = wrapped.getPriority();
            goals.remove(wrapped);
            golem.targetSelector.addGoal(priority, new NearestAttackableTargetGoal<>(
                    golem, Mob.class, 10, true, false,
                    target -> target instanceof Enemy
                            && !(target instanceof ZombieGirlEntity)));
        }
    }
}
