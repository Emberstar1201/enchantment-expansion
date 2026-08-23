package com.github.emberstar1201.enchantmentex.mixin;

import com.github.emberstar1201.enchantmentex.SwiftCrossbowConfig;
import com.github.emberstar1201.enchantmentex.enchantment.ModEnchantments;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// ========================================================================
// 【迅捷之弩】装填时间 Mixin
//
// 目标：net.minecraft.world.item.CrossbowItem#getChargeDuration(ItemStack)
//       （原版：装填耗时，默认 25 tick = 1.25 秒；快速装填附魔会先减一部分）
//
// 原理：在方法 RETURIN 处拦截，若弩上带有「迅捷之弩」附魔，
//       则在原版返回值基础上再减去配置的装填减少量（至少保留下限 tick）。
//       无需修改原版逻辑，也无需复制装填完成代码，原版音效/动画/判定
//       全部自动适配新时长。
//
// 注：1.20.1 Forge 的 dev 环境类为 mojang 官方命名，mixin 直接用
//     "getChargeDuration" 即可命中；生产环境由 refmap 自动映射到 srg 名。
// ========================================================================
@Mixin(CrossbowItem.class)
public class CrossbowChargeDurationMixin {

    @Inject(method = "getChargeDuration", at = @At("RETURN"), cancellable = true)
    private static void enchantmentEx$swiftCharge(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        // 读取原版计算好的装填时间（已包含快速装填附魔的减免）
        int base = cir.getReturnValue();

        // 检查弩上是否有「迅捷之弩」附魔
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.SWIFT_CROSSBOW.get(), stack);
        if (level <= 0) return;

        // 应用配置的装填减少量（最终装填时间不低于配置下限）
        cir.setReturnValue(SwiftCrossbowConfig.computeFinalChargeTicks(base, level));
    }
}