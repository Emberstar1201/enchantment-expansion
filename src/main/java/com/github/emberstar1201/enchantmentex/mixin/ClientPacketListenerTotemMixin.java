package com.github.emberstar1201.enchantmentex.mixin;

import com.github.emberstar1201.enchantmentex.item.ModItems;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// ========================================================================
// 【永恒图腾】触发动画贴图 Mixin（仅客户端）
//
// 原版处理实体事件 35 时，会调用 private static findTotem(Player) 来决定
// 屏幕中央"举起图腾"动画要渲染哪个物品。该方法把判定硬编码为
// Items.TOTEM_OF_UNDYING，因此永恒图腾触发时只会显示原版不死图腾。
//
// 本 Mixin 在 findTotem 头部注入：若玩家主手/副手持有的第一件物品是
// 永恒图腾，则直接返回该 ItemStack，使动画使用永恒图腾自身的贴图；
// 否则放行，保持原版行为不变。
//
// 注意：ClientPacketListener 为客户端专有类，故本 Mixin 必须登记在
// enchantment_expansion.mixin.json 的 "client" 数组中，避免在专用服务器上加载。
// ========================================================================
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerTotemMixin {

    @Inject(method = "findTotem", at = @At(value = "HEAD"), cancellable = true)
    private static void enchantmentEx$findEternalTotem(Player player, CallbackInfoReturnable<ItemStack> cir) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.isEmpty() && stack.is(ModItems.ETERNAL_TOTEM.get())) {
                cir.setReturnValue(stack);
                return;
            }
        }
        // 手上没有永恒图腾 —— 放行，沿用原版不死图腾的查找结果
    }
}
