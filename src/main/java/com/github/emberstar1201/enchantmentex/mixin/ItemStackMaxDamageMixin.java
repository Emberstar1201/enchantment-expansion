package com.github.emberstar1201.enchantmentex.mixin;

import com.github.emberstar1201.enchantmentex.enchantment.ModEnchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// ========================================================================
// 【耐久强化】最大耐久 Mixin
//
// 耐久上限由 ItemStack#getMaxDamage() 决定，Forge 1.20.1 没有对应事件，
// 因此在此处修改返回值，确保耐久条、F3+H 与实际损耗判定使用同一上限。
// ========================================================================
@Mixin(ItemStack.class)
public class ItemStackMaxDamageMixin {

    @Inject(method = "getMaxDamage", at = @At("RETURN"), cancellable = true)
    private void enchantmentEx$durabilityBoostMaxDamage(
            CallbackInfoReturnable<Integer> cir
    ) {
        ItemStack stack = (ItemStack) (Object) this;
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DURABILITY_BOOST.get(), stack
        );

        if (level <= 0) {
            return;
        }

        // I/II/III 分别将最大耐久提升至原版的 150%/200%/250%。
        int baseMaxDamage = cir.getReturnValue();
        int boostedMaxDamage = baseMaxDamage * (2 + level) / 2;
        cir.setReturnValue(boostedMaxDamage);
    }
}
