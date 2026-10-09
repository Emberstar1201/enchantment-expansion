package com.github.emberstar1201.enchantmentex.mixin;

import com.github.emberstar1201.enchantmentex.MobBuffConfig;
import com.github.emberstar1201.enchantmentex.MobBuffRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// 恶魂原版发射逻辑没有 Forge 事件参数，因此只在这里修改两个硬编码计时值。
@Mixin(targets = "net.minecraft.world.entity.monster.Ghast$GhastShootFireballGoal")
public class GhastFireballGoalMixin {

    @ModifyConstant(method = "tick", constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 20))
    private int enchantmentEx$shortenCharge(int original) {
        if (!MobBuffRuntime.isEnabled()) {
            return original;
        }
        return Math.max(1, MobBuffConfig.ghastFireballInterval / 3);
    }

    @ModifyConstant(method = "tick", constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = -40))
    private int enchantmentEx$shortenCooldown(int original) {
        if (!MobBuffRuntime.isEnabled()) {
            return original;
        }
        int charge = Math.max(1, MobBuffConfig.ghastFireballInterval / 3);
        return -(Math.max(charge, MobBuffConfig.ghastFireballInterval - charge));
    }
}
