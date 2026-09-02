package com.github.emberstar1201.enchantmentex.mixin;

import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.enchantment.ModEnchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// ========================================================================
// 【耐久强化 / 中国制造】最大耐久 Mixin
//
// 耐久上限由 ItemStack#getMaxDamage() 决定，Forge 1.20.1 没有对应事件，
// 因此在此处修改返回值，确保耐久条、F3+H 与实际损耗判定使用同一上限。
//
// 耐久强化（Durability Boost）：
//   I 级 → ×1.5    II 级 → ×2.0    III 级 → ×2.5
// 中国制造（Made in China）：
//   I 级 → ×2.0（对应耐久强化 II 级，体现"三合一"强度）
//
// 两者互斥，所以不会同时出现，分别独立判断即可。
// ========================================================================
@Mixin(ItemStack.class)
public class ItemStackMaxDamageMixin {

    @Inject(method = "getMaxDamage", at = @At("RETURN"), cancellable = true)
    private void enchantmentEx$durabilityBoostMaxDamage(
            CallbackInfoReturnable<Integer> cir
    ) {
        ItemStack stack = (ItemStack) (Object) this;
        int baseMaxDamage = cir.getReturnValue();

        // --------------------------------------------------------------
        // 耐久强化（Durability Boost）：I/II/III → ×1.5/×2/×2.5
        // --------------------------------------------------------------
        int durabilityLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DURABILITY_BOOST.get(), stack
        );
        if (durabilityLevel > 0) {
            int boostedMaxDamage = baseMaxDamage * (2 + durabilityLevel) / 2;
            cir.setReturnValue(boostedMaxDamage);
            return;
        }

        // --------------------------------------------------------------
        // 中国制造（Made in China）：I 级 → ×2.0
        //   对应耐久强化 II 级强度，体现"三合一"
        // --------------------------------------------------------------
        int madeInChinaLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.MADE_IN_CHINA.get(), stack
        );
        if (madeInChinaLevel > 0) {
            int multiplierPercent = Config.madeInChinaMaxDurabilityPercent;
            int boostedMaxDamage = baseMaxDamage * multiplierPercent / 100;
            cir.setReturnValue(boostedMaxDamage);
        }
    }
}
