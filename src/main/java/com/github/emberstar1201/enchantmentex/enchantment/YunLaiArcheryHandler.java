package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 云来弓法（基础版）附魔事件处理器
//
// 【职责单一】：仅处理"拉弓蓄力加速"效果
// 【不处理】：箭矢飞行速度（用户需求：云来弓法基础版不改变箭矢速度）
//
// 【与古·云来弓法共存的设计】：
//   1. 独立的 Enchantment 查询：本 Handler 只查 ModEnchantments.YUNLAI_ARCHERY
//      古·云来弓法的 Handler 只查 ANCIENT_YUNLAI，两个附魔的蓄力进度分开计算
//   2. 独立的 PersistentData 累加 key：
//        AncientYunLai : "AncientYunLai_FractionalAccum_xxx"
//        YunLaiArchery  : "YunLaiArchery_FractionalAccum_xxx"
//      两者互不干扰，小数累积不会互相覆盖
//   3. 同一 tick 两个 Handler 都会执行 event.setDuration() 减进度：
//      结果就是"总额外进度 = 古云来 extraProgress + 基础版 extraProgress"
//      两者同时存在时蓄力速度叠加（可观察到蓄力时间进一步缩短）
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class YunLaiArcheryHandler {

    // ========================================================================
    // 【仅效果一】缩短弓的蓄力时间（加速拉弓）
    // 原理：在玩家"使用物品"的每tick，根据附魔等级跳过额外 tick 进度
    // 【不注册】EntityJoinLevelEvent → 不改变箭矢飞行速度（符合用户要求）
    // ========================================================================
    @SubscribeEvent
    public static void onBowUseTick(LivingEntityUseItemEvent.Tick event) {
        // 仅处理玩家实体，避免影响骷髅等其他使用弓的生物
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        ItemStack usingItem = event.getItem();
        // 仅处理弓类物品的使用
        if (!(usingItem.getItem() instanceof BowItem)) {
            return;
        }

        // 仅查询"云来弓法（基础版）"的附魔等级
        // 注：不查古·云来弓法，两者在各自的 Handler 中处理
        int enchantLevel = net.minecraft.world.item.enchantment.EnchantmentHelper
                .getItemEnchantmentLevel(ModEnchantments.YUNLAI_ARCHERY.get(), usingItem);
        if (enchantLevel <= 0) {
            return;
        }

        // 获取蓄力加速倍率
        // 复用 AncientYunLaiEnchantment.getChargeSpeedMultiplier() 查表
        // 数值完全相同：Lv1=1.053 ... Lv10=5.000
        double chargeMultiplier = YunLaiArcheryEnchantment.getChargeSpeedMultiplier(enchantLevel);

        // 倍率 <= 1.0 时无需加速
        if (chargeMultiplier <= 1.0) {
            return;
        }

        // 【核心逻辑】：倍率减 1 即为需要"额外跳过"的蓄力进度
        double extraProgress = chargeMultiplier - 1.0;

        // ---------- 整数部分：每 tick 直接跳 n 格进度 ----------
        int wholeSkips = (int) extraProgress;
        for (int i = 0; i < wholeSkips; i++) {
            int newDuration = event.getDuration() - 1;
            if (newDuration <= 0) {
                newDuration = 1; // 防止直接使用完成，交给原版判定
            }
            event.setDuration(newDuration);
        }

        // ---------- 小数部分：累积计数处理（保证无精度丢失） ----------
        float fractional = (float) (extraProgress - wholeSkips);
        if (fractional > 0) {
            // 【关键】：独立 key，与古·云来弓法的小数累积器互不干扰
            String accumKey = "YunLaiArchery_FractionalAccum_" + usingItem.getDescriptionId();
            float accumulated = player.getPersistentData().getFloat(accumKey);
            accumulated += fractional;
            if (accumulated >= 1.0f) {
                // 累积满 1 tick，额外跳 1 格进度
                int newDuration = event.getDuration() - 1;
                if (newDuration <= 0) {
                    newDuration = 1;
                }
                event.setDuration(newDuration);
                accumulated -= 1.0f; // 保留余数继续累积
            }
            player.getPersistentData().putFloat(accumKey, accumulated);
        }
    }
}
