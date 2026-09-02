package com.github.emberstar1201.enchantmentex.mixin;

import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.enchantment.ModEnchantments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

// ========================================================================
// 【耐久强化 / 中国制造】概率不消耗耐久 Mixin
//
// 原版 Unbreaking 机制是在 ItemStack.hurt() 中根据附魔等级概率跳过扣耐久，
// 但原版 Unbreaking 与本模组两附魔均互斥（见 checkCompatibility），
// 因此直接在 RETURN 前注入：若原版已判定跳过（已 return true/false），
// 此处只处理"原版没跳过，再由模组附魔判定要不要额外跳过"的情况。
//
// 耐久强化（Durability Boost）概率不消耗耐久：
//   I 级 → 50%    II 级 → 66%    III 级 → 75%
// （对应等效耐久 ×2 / ×3 / ×4，与最大耐久提升叠加后，实际续航更强）
//
// 中国制造（Made in China）概率不消耗耐久：
//   I 级 → 60%（默认配置，可改）
// （原版耐久 III 级为 40%，本附魔更强，体现"三合一"）
// ========================================================================
@Mixin(ItemStack.class)
public class ItemStackHurtDurabilityMixin {

    @Inject(
            method = "hurt(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    private void enchantmentEx$hurtIgnoreDurability(
            int pDamage,
            RandomSource pRandom,
            @Nullable ServerPlayer pUser,
            CallbackInfoReturnable<Boolean> cir
    ) {
        // 非耐久损耗伤害直接放行（pDamage <= 0 时 Minecraft 本身也不会扣）
        if (pDamage <= 0) {
            return;
        }

        ItemStack stack = (ItemStack) (Object) this;
        if (stack.isEmpty()) {
            return;
        }

        // 耐久强化附魔：按等级概率跳过整次耐久扣减
        int durabilityLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DURABILITY_BOOST.get(), stack);
        if (durabilityLevel > 0) {
            double chance = switch (durabilityLevel) {
                case 1 -> 0.50;   // I 级 50%
                case 2 -> 0.66;   // II 级 66%
                default -> 0.75;  // III 级+ 75%
            };
            if (pRandom.nextDouble() < chance) {
                // 不扣耐久：返回 false（表示"没有耐久损耗发生"）
                cir.setReturnValue(false);
                return;
            }
        }

        // 中国制造附魔：按配置概率跳过整次耐久扣减
        int chinaLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.MADE_IN_CHINA.get(), stack);
        if (chinaLevel > 0) {
            double chance = Config.madeInChinaIgnoreDurabilityChance;
            if (pRandom.nextDouble() < chance) {
                cir.setReturnValue(false);
            }
        }
    }
}
