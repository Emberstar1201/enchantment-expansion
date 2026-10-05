package com.github.emberstar1201.enchantmentex.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * NearestAttackableTargetGoal.targetType 访问器：
 * 用于在铁傀儡 Mixin 中区分「以 Mob.class 搜索的目标」与
 * 「以 Player.class 搜索的目标」，只替换前者。
 */
@Mixin(NearestAttackableTargetGoal.class)
public interface NearestAttackableTargetGoalAccessor {
    @Accessor("targetType")
    Class<? extends LivingEntity> getTargetType();
}
