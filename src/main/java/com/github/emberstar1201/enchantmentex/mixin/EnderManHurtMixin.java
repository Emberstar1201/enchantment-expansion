package com.github.emberstar1201.enchantmentex.mixin;

import com.github.emberstar1201.enchantmentex.MobBuffConfig;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// ========================================================================
// 【末影人 · 可被弹射物命中】EnderMan.hurt Mixin
//
// 原版逻辑（已用 javap 反编译确认）：
//     boolean potion = source.getDirectEntity() instanceof ThrownPotion;
//     if (!source.is(DamageTypeTags.IS_PROJECTILE) && !potion) {
//         boolean result = super.hurt(source, amount);   // 普通受伤
//         ... 90% 概率瞬移 ...
//         return result;
//     } else {
//         boolean result = potion ? this.hurtWithCleanWater(...) : false;
//         for (int i = 0; i < 64; i++) {                  // 弹射物：只瞬移，不吃伤害
//             if (this.teleport()) return true;
//         }
//         return result;
//     }
//
// 实现思路：
//   Redirect 那一次 `source.is(IS_PROJECTILE)` 判定，在开关开启时让它返回 false，
//   弹射物就会走进上面的「普通受伤」分支，由 Monster.hurt 正常结算伤害。
//
// 为什么不会影响投掷药水：
//   走 else 分支的条件是 `IS_PROJECTILE || potion`。药水的 potion 判定为 true，
//   所以即使这里返回 false，药水依旧进入 hurtWithCleanWater，逻辑保持原版。
//
// hurt 方法内只有这一处 DamageSource.is(...) 调用，@Redirect 不会误伤其它判定。
// ========================================================================
@Mixin(EnderMan.class)
public class EnderManHurtMixin {

    @Redirect(method = "hurt",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean enchantmentEx$projectileCanHurt(DamageSource source, TagKey<DamageType> tag) {
        boolean original = source.is(tag);
        if (original
                && DamageTypeTags.IS_PROJECTILE.equals(tag)
                && MobBuffConfig.enabled
                && MobBuffConfig.enderManProjectileVulnerable) {
            return false;
        }
        return original;
    }
}
