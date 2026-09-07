package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.SprintEnduranceConfig;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【疾跑节能】附魔事件处理器
//
// 设计原理：
//   原版疾跑会在走路消耗的基础上额外累积 exhaustion（疲劳值）。
//   每当玩家疲劳值达到 4.0 时，原版会扣减 1 点饥饿/饱和度，这正是
//   "疾跑比走路更容易饿"的根源。
//
//   本 Handler 通过 PlayerTickEvent，在【玩家疾跑 + 护腿有疾跑节能附魔】
//   时，按附魔等级每 tick 抵消一份"疾跑的额外疲劳值"：
//     - I  级：每 tick 抵消 baseOffsetPerTick × 0.5
//     - II  级：每 tick 抵消 baseOffsetPerTick × 1.0（完全抵消）
//   baseOffsetPerTick 由配置提供，默认对应当前版本原版疾跑每秒额外
//   能耗，可自行微调。
//
//   抵消量做下限保护（不使 fatigue 为负），避免数值异常。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SprintEnduranceHandler {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        // 仅服务端逻辑线程处理（避免客户端重复/失真）
        if (player.level().isClientSide) return;

        // 总开关
        if (!SprintEnduranceConfig.isEnabled()) return;

        // 只有疾跑时才需要抵消疾跑带来的额外疲劳值
        if (!player.isSprinting()) return;

        // 护腿上是否有疾跑节能附魔
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        int level = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.SPRINT_ENDURANCE.get(), legs);
        if (level <= 0) return;

        // 按等级抵消对应的\"疾跑额外疲劳值\"。
        // 说明：1.20.1 中 FoodData.exhaustion 为私有字段，无法直接读写，
        // 因此改用官方公开的 addExhaustion() 方法，传入负数即可减少疲劳值。
        float reduction = SprintEnduranceConfig.getReductionPerTick(level);
        if (reduction <= 0f) return;

        player.getFoodData().addExhaustion(-reduction);
    }
}
