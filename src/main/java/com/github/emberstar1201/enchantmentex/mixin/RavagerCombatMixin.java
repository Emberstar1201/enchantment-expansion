package com.github.emberstar1201.enchantmentex.mixin;

import com.github.emberstar1201.enchantmentex.MobBuffConfig;
import net.minecraft.world.entity.monster.Ravager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 劫掠兽战斗节奏调整。
 *
 * 原版将盾击眩晕时间直接写成 40 tick，Forge 事件没有可修改参数，
 * 因此这里只对 Ravager 的两个原版常量做定点替换。
 */
@Mixin(Ravager.class)
public abstract class RavagerCombatMixin {

    @ModifyConstant(method = "blockedByShield", constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 40))
    private int enchantmentEx$increaseShieldStun(int original) {
        if (!MobBuffConfig.enabled) {
            return original;
        }
        return original + MobBuffConfig.ravagerStunTicks;
    }

    @ModifyConstant(method = "doHurtTarget", constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 10))
    private int enchantmentEx$increaseAttackCharge(int original) {
        if (!MobBuffConfig.enabled) {
            return original;
        }
        // 延长攻击动作锁定时间，使冲撞后的再次攻击间隔更长。
        return original + MobBuffConfig.ravagerAttackLockTicks;
    }
}
