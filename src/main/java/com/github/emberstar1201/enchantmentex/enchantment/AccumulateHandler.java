package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.AccumulateConfig;
import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// ========================================================================
// 【蓄积】服务端核心事件处理器
//
// 机制（三阶段）：
//   一、蓄力阶段（PlayerTickEvent + C2S 右键状态）：
//       玩家按住右键且主手武器带「蓄积」附魔 → 蓄力计时累加，
//       每满 chargeTimeSeconds（默认 5 秒）完成一阶蓄力，层数上限 = 附魔等级：
//         I 级：可蓄 1 阶
//         II 级：可蓄 2 阶
//         III 级：可蓄 3 阶
//       蓄力期间玩家身周持续浮现金色光晕粒子，每完成一阶播放爆发粒子 + 音效，
//       并通过 ActionBar 显示当前层数（如 "蓄力 I / III"）。
//
//   二、释放阶段（LivingHurtEvent）：
//       蓄力层数 ≥ 1 时，下一次近战攻击：
//         - 伤害 × 阶级倍率（I×1 / II×2 / III×3，配置可调）
//         - 对目标施加小范围击退（距离随阶级提升）
//       攻击后蓄力层数清零（一次性消耗）。
//
//   三、中断重置：切换主手物品、玩家受伤、或蓄力完成后超过
//       forgetSeconds（默认 10 秒）未发起攻击 → 蓄力层数与计时全部清零。
//
// 网络：客户端右键按住状态通过 AccumulateChargePacket（C2S）同步到
//       setHoldRight()，服务端无法直接读取客户端鼠标输入。
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AccumulateHandler {

    // ========================================================================
    // 单个玩家的蓄力状态
    // ========================================================================
    private static class AccumulateState {
        boolean holdingRight = false;   // 当前是否按住右键（由 C2S 包同步）
        int stages = 0;                 // 已蓄力阶数（0 = 未蓄力）
        int chargeTicks = 0;            // 当前阶的蓄力计时
        long stageReadyTick = 0;        // 最近一次完成蓄力阶段的游戏 tick
        ItemStack lastMainhand = ItemStack.EMPTY;  // 主手物品快照（检测切换）
    }

    // 玩家 UUID → 蓄力状态
    private static final Map<UUID, AccumulateState> ACCUMULATE_STATES = new HashMap<>();

    // ========================================================================
    // 网络入口：更新玩家的右键按住状态（客户端松开时也应通知）
    // ========================================================================
    public static void setHoldRight(UUID playerId, boolean holding) {
        AccumulateState state = ACCUMULATE_STATES.computeIfAbsent(playerId, k -> new AccumulateState());
        state.holdingRight = holding;
        if (!holding) {
            // 松开右键：蓄力计时暂停（不清空已蓄层数），
            // 层数保留，重新按住可继续蓄力
            state.chargeTicks = 0;
        }
    }

    // ========================================================================
    // 核心：蓄力计时（PlayerTickEvent END，仅服务端）
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide()) return;
        if (!AccumulateConfig.isEnabled()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        UUID uuid = player.getUUID();
        AccumulateState state = ACCUMULATE_STATES.computeIfAbsent(uuid, k -> new AccumulateState());

        // 获取主手武器的蓄积附魔等级（没有附魔则清除状态）
        ItemStack mainhand = player.getMainHandItem();
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.ACCUMULATE.get(), mainhand);
        if (enchantLevel <= 0) {
            ACCUMULATE_STATES.remove(uuid);
            return;
        }

        // ----------------------------------------------------------------
        // 切换物品检测：主手物品变化 → 重置全部蓄力
        // ----------------------------------------------------------------
        if (state.lastMainhand.isEmpty()
                || !ItemStack.isSameItemSameTags(state.lastMainhand, mainhand)) {
            resetState(state, serverPlayer, "§e蓄力因切换武器而消散");
            state.lastMainhand = mainhand.copy();
            state.holdingRight = false; // 切换物品视为松开
            return;
        }
        state.lastMainhand = mainhand.copy();

        // ----------------------------------------------------------------
        // 10 秒未攻击消散检测：完成蓄力后超过 forgetSeconds 未攻击
        // ----------------------------------------------------------------
        if (state.stages > 0) {
            long forgetTicks = AccumulateConfig.getForgetTicks();
            if (player.tickCount - state.stageReadyTick > forgetTicks) {
                resetState(state, serverPlayer, "§7蓄力已消散（长时间未攻击）");
                return;
            }
        }

        // ----------------------------------------------------------------
        // 蓄力进行中：按住右键 && 主手武器有效
        // ----------------------------------------------------------------
        if (state.holdingRight && enchantLevel > 0 && state.stages < enchantLevel) {
            state.chargeTicks++;

            // 蓄力过程粒子：每 10 tick 生成一圈金色光晕环绕玩家
            if (player.tickCount % 10 == 0) {
                spawnChargeAura(serverPlayer, state.stages, enchantLevel);
            }

            // 完成一阶蓄力
            int chargeTicksRequired = AccumulateConfig.getChargeTicks();
            if (state.chargeTicks >= chargeTicksRequired) {
                state.stages++;
                state.chargeTicks = 0;
                state.stageReadyTick = player.tickCount;

                // 完成音效 + 爆发粒子
                serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(),
                        serverPlayer.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS,
                        0.7F, 1.3F);
                spawnStageCompleteParticles(serverPlayer);

                // ActionBar 显示当前层数
                sendActionBar(serverPlayer, state.stages, enchantLevel);
            }
        }
    }

    // ========================================================================
    // 伤害倍率 + 击退 + 受伤中断（LivingHurtEvent）
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!AccumulateConfig.isEnabled()) return;
        Entity victim = event.getEntity();

        // ----------------------------------------------------------------
        // 分支 A：受害方是蓄力玩家 → 蓄力中断（除非伤害来自自己）
        // ----------------------------------------------------------------
        if (victim instanceof Player hurtPlayer) {
            UUID uuid = hurtPlayer.getUUID();
            AccumulateState state = ACCUMULATE_STATES.get(uuid);
            if (state != null && state.stages > 0
                    && event.getSource().getEntity() != hurtPlayer) {
                resetState(state, (ServerPlayer) hurtPlayer, "§c蓄力中断（受到伤害）");
            }
        }

        // ----------------------------------------------------------------
        // 分支 B：攻击方是蓄力玩家 → 释放蓄力（伤害倍率 + 击退）
        // ----------------------------------------------------------------
        if (!(event.getSource().getEntity() instanceof Player attacker)) return;
        if (!(victim instanceof LivingEntity target) || target.isSpectator()) return;
        if (!(attacker instanceof ServerPlayer serverAttacker)) return;

        AccumulateState state = ACCUMULATE_STATES.get(attacker.getUUID());
        if (state == null || state.stages <= 0) return;

        // 伤害倍率（阶级 → 倍率）
        double multiplier = AccumulateConfig.getStageDamageMultiplier(state.stages);
        if (multiplier > 1.0) {
            event.setAmount((float) (event.getAmount() * multiplier));
        }

        // 击退：从攻击者指向受害者的方向，强度随阶级提升
        double knockbackStr = AccumulateConfig.getStageKnockback(state.stages);
        if (knockbackStr > 0) {
            double dx = target.getX() - attacker.getX();
            double dz = target.getZ() - attacker.getZ();
            target.knockback(knockbackStr, dx, dz);
        }

        // ActionBar 提示释放
        sendActionBar(serverAttacker,
                "§6蓄力释放！伤害 ×" + String.format("%.1f", multiplier)
                        + " §7(击退 " + String.format("%.1f", knockbackStr) + ")");

        // 消耗：一次性释放后层数清零
        resetState(state, serverAttacker, null);
    }

    // ========================================================================
    // 工具：重置换阶段
    // ========================================================================
    private static void resetState(AccumulateState state, ServerPlayer player, String message) {
        boolean hadStages = state.stages > 0;
        state.stages = 0;
        state.chargeTicks = 0;
        state.stageReadyTick = 0;
        if (hadStages && message != null) {
            sendActionBar(player, message);
        }
    }

    // ========================================================================
    // 工具：发送 ActionBar 消息（屏幕下方显示）
    // ========================================================================
    private static void sendActionBar(ServerPlayer player, int stages, int maxStages) {
        sendActionBar(player, buildStageText(stages, maxStages));
    }

    private static void sendActionBar(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    /** 生成如 "蓄力 II / III" 的阶级文本（I/II/III 罗马数字） */
    private static String buildStageText(int stages, int maxStages) {
        String current = toRoman(stages);
        String max = toRoman(maxStages);
        if (stages >= maxStages && maxStages > 0) {
            return "§6§l✸ 蓄力 " + current + " / " + max + " §e已满";
        }
        if (stages > 0) {
            return "§e蓄力 " + current + " / " + max;
        }
        return "§7开始蓄力……";
    }

    /** 阿拉伯数字 → 罗马数字（仅支持 1~3） */
    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(n);
        };
    }

    // ========================================================================
    // 工具：蓄力中金色光晕粒子（环绕玩家一圈）
    // ========================================================================
    private static void spawnChargeAura(ServerPlayer player, int stages, int maxStages) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        // 环绕半径与数量（层数越多光晕越密集）
        int count = 6 + stages * 2;
        double radius = 0.6;
        float green = 0.75f + maxStages * 0.05f; // 阶级越高金色越亮

        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(360.0 / count * i + player.tickCount * 1.5 % 360);
            double px = player.getX() + Math.cos(angle) * radius;
            double pz = player.getZ() + Math.sin(angle) * radius;
            serverLevel.sendParticles(
                    new DustParticleOptions(new Vector3f(1.0f, green, 0.1f), 0.6f),
                    px, player.getY() + 0.5, pz, 1, 0, 0, 0, 0);
        }
    }

    /** 完成一阶蓄力时的爆发粒子（金屑爆开） */
    private static void spawnStageCompleteParticles(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0, player.getZ(),
                24, 0.6, 0.8, 0.6, 0.02);
        serverLevel.sendParticles(
                new DustParticleOptions(new Vector3f(1.0f, 0.9f, 0.3f), 1.0f),
                player.getX(), player.getY() + 0.8, player.getZ(),
                12, 0.4, 0.5, 0.4, 0);
    }
}