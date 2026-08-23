package com.github.emberstar1201.enchantmentex.mixin;

import com.github.emberstar1201.enchantmentex.AgricultureConfig;
import com.github.emberstar1201.enchantmentex.enchantment.ModEnchantments;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ========================================================================
// 【渔获大师】咬钩节奏 + 自动收杆 Mixin
//
// 目标：net.minecraft.world.entity.projectile.FishingHook#tick()
//
// 原版机制（依据 1.20.1 反编译源码，catchingFish 每 tick 调用）：
//   timeUntilLured（抛竿后等待 5~30 秒）→ 归零后进入
//   timeUntilHooked（"鱼已入钩"过渡 1~4 秒）→ 归零瞬间才设置
//   nibble = 20~40（咬钩窗口仅 1~2 秒）并 biting=true（鱼咬住钩）
//   → 此后 nibble 每 tick -1，减到 0 则 biting=false，循环重新等待。
//
// 注意：biting 在原版 tick 的【中段】（BOBBING 分支的 catchingFish）才置位，
// 若在 tick 开头（HEAD）检测，存在一轮错失窗口；因此本 Mixin 改为在
// 【RETURN】（原版本 tick 已完整执行完毕）处判定，咬钩当 tick 必然可见。
//
// 本 Mixin 在 tick 返回前做两件事（仅当持有者玩家手持带「渔获大师」的
// 鱼竿时生效；不再强制"开阔水域"，任何能正常钓鱼的水域均可）：
//
//   A. 自动收杆：检测到 biting=true，立即调用原版 retrieve() 收杆。
//     （retrieve 是原版收竿方法，会在内部触发 ItemFishedEvent，
//       渔获大师的稀有战利品追加逻辑自然随之执行）
//
//   B. 稳定咬钩节奏：把"抛竿→咬钩"的等待段（timeUntilLured）与咬钩窗口
//      （nibble）都重投为配置区间 [min, max] 秒内的随机值
//      （默认 30~60 秒），保证挂机/手动钓鱼每次上钩节奏稳定、窗口宽裕。
// ========================================================================
@Mixin(FishingHook.class)
public class FishingHookTickMixin {

    /** 咬钩窗口倒计时（biting=true 期间持续递减；归零即咬钩结束） */
    @Shadow
    private int nibble;

    /** 鱼是否咬住钩（原版在 nibble 被赋值的同时置 true） */
    @Shadow
    private boolean biting;

    /** "鱼已入钩"过渡倒计时（归零瞬间 nibble/biting 才被设置） */
    @Shadow
    private int timeUntilHooked;

    /** "等待咬钩"倒计时（抛竿入水后最主要的等待段，决定上钩快慢） */
    @Shadow
    private int timeUntilLured;

    // Mixin 附加实例字段：本轮"等待段"是否已重投为配置区间
    private boolean enchantmentEx$tunedBiteDelay;

    // Mixin 附加实例字段：本轮"咬钩窗口"是否已重投为配置区间
    private boolean enchantmentEx$tunedNibbleWindow;

    /** @Shadow 方法代理：直接调用原版收杆逻辑（public int retrieve(ItemStack)） */
    @Shadow
    public int retrieve(ItemStack stack) {
        throw new AssertionError("FishingHookTickMixin.shadow");
    }

    // 注入点：RETURN —— 原版 tick 完整执行完毕后再判定，时序零错失。
    // 自动收杆在咬钩瞬间的同 tick 内立即触发。
    @Inject(method = "tick", at = @At("RETURN"))
    private void enchantmentEx$manageFishing(CallbackInfo ci) {
        FishingHook self = (FishingHook) (Object) this;
        if (self.level().isClientSide()) return;
        if (!(self.getOwner() instanceof Player player)) return;

        // 只有「渔获大师」附魔才启用节奏/自动收杆
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.FISHING_MASTER.get(), findRod(player));
        if (level <= 0) return;

        // ================================================================
        // A. 自动收杆：鱼已咬钩（biting=true）→ 立即 retrieve
        // ================================================================
        if (AgricultureConfig.getFishingAutoReelEnabled() && this.biting) {
            ItemStack rod = findRod(player);
            // retrieve 生成渔获并触发 ItemFishedEvent（战利品追加随之生效），
            // 同时内部会 discard 钩子实体
            int durabilityUsed = this.retrieve(rod);

            // 模拟玩家右键收杆的耐久消耗与摆动动画
            // findRod 优先取主手：rod == 主手 ItemStack 即为主手竿，否则为副手竿
            boolean isOffhand = rod != player.getMainHandItem()
                    && player.getOffhandItem().getItem() instanceof FishingRodItem;
            InteractionHand hand = isOffhand
                    ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            EquipmentSlot slot = isOffhand
                    ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;

            if (rod.getItem() instanceof FishingRodItem
                    && durabilityUsed > 0 && !player.isCreative()) {
                rod.hurtAndBreak(durabilityUsed, player,
                        p -> p.broadcastBreakEvent(slot));
            }
            if (!rod.isEmpty()) {
                player.swing(hand);
            }
            return; // 钩子已被 retrieve 丢弃，无需再处理节奏
        }

        // ================================================================
        // B. 稳定节奏：等待段（timeUntilLured）与咬钩窗口（nibble）
        //    都保持在配置区间（默认 30~60 秒）
        // ================================================================
        if (this.nibble <= 0) {
            // 当前不在咬钩窗口内：
            //   1) 重置咬钩窗口标记 —— 下一轮咬钩到来时重新定长
            this.enchantmentEx$tunedNibbleWindow = false;

            //   2) 若原版刚在 catchingFish 中把 timeUntilLured 重置为新的
            //      随机等待（100~600 tick），改写为配置区间 [30,60] 秒
            if (this.timeUntilHooked <= 0 && this.timeUntilLured > 0
                    && !this.enchantmentEx$tunedBiteDelay) {
                this.timeUntilLured = rollDelayTicks(self);
                this.enchantmentEx$tunedBiteDelay = true;
            }
        } else {
            // 正在咬钩窗口内：
            //   1) 标记下一轮等待段需要重新定长（本轮咬钩结束后）
            this.enchantmentEx$tunedBiteDelay = false;

            //   2) 若原版刚设置 nibble=20~40（本轮咬钩窗口刚开始），
            //      延长为配置区间 [30,60] 秒 —— 即使关闭自动收杆，
            //      手动玩家也有充足时间右键收杆
            if (this.biting && !this.enchantmentEx$tunedNibbleWindow) {
                this.nibble = rollDelayTicks(self);
                this.enchantmentEx$tunedNibbleWindow = true;
            }
        }
    }

    // ========================================================================
    // 工具：从 [min,max]（tick）区间掷出一个随机延迟（默认 30~60 秒）
    // ========================================================================
    private static int rollDelayTicks(FishingHook self) {
        int min = AgricultureConfig.getFishingBiteDelayMinTicks();
        int max = AgricultureConfig.getFishingBiteDelayMaxTicks();
        if (min > max) min = max;
        if (max <= min) return min;
        return min + self.level().random.nextInt(max - min + 1);
    }

    // ========================================================================
    // 工具：取玩家手中的鱼竿（主手优先，其次副手；无竿返回空栈）
    // ========================================================================
    private static ItemStack findRod(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof FishingRodItem) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof FishingRodItem) return off;
        return main; // 主手可能为空或非竿（此时 retrieve 仍可产生渔获，只是不耗耐久）
    }
}