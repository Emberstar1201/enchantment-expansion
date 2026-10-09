package com.github.emberstar1201.enchantmentex.entity;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 生命之星治愈过渡（仿原版僵尸村民治疗的等待期）。
 *
 * <p>右键后不再立即变身，而是进入「净化中」状态，全程三阶段：</p>
 * <ol>
 *   <li>一阶段（前 1~2 分钟）：身上偶尔冒出绿色十字粒子（happy_villager）；</li>
 *   <li>二阶段（最后 10 秒）：绿色十字明显变频繁，伴随治疗系药水旋涡；</li>
 *   <li>三阶段（结束瞬间）：爆炸烟雾 + 浓烟挡住整个模型，烟雾中完成变身，
 *       消散后显现人类形态。</li>
 * </ol>
 *
 * <p>状态存在实体 ForgeData（随存档持久化，星尘收容也会携带）：
 * 截止时刻用 level.getGameTime() 绝对时间，区块卸载 / 重进存档后继续有效；
 * 若收容放出时已过了截止时刻，下一 tick 直接完成变身。</p>
 */
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GirlCureHandler {

    /** 净化开始的时刻（level.getGameTime()，绝对时间）。 */
    private static final String TAG_CURE_START = "LifeCureStart";
    /** 净化完成的时刻（level.getGameTime()，绝对时间）。 */
    private static final String TAG_CURE_UNTIL = "LifeCureUntil";
    /** 发起治愈的玩家 UUID（离线也允许完成，只是变身时不再给ta发成就）。 */
    private static final String TAG_CURE_PLAYER = "LifeCurePlayer";

    /** 一阶段时长下限：60 秒（1200 tick），即「1~2 分钟」的 1 分钟端。 */
    private static final int STAGE1_MIN_TICKS = 1200;
    /** 一阶段时长随机增量：0~1200 tick，总时长 60~120 秒。 */
    private static final int STAGE1_RANDOM_TICKS = 1200;
    /** 二阶段（粒子变频繁）时长：10 秒（200 tick）。 */
    private static final int STAGE2_TICKS = 200;

    private GirlCureHandler() {
    }

    /** 目标是否已在净化中（供生命之星避免重复开始）。 */
    public static boolean isCuring(LivingEntity target) {
        return target.getPersistentData().contains(TAG_CURE_UNTIL);
    }

    /**
     * 开始净化：记录开始 / 截止时刻与治愈者，并播放原版僵尸村民开始治疗的音效。
     * 返回 false 表示目标已在净化中（本次右键不生效）。
     */
    public static boolean startCuring(LivingEntity target, ServerPlayer player) {
        CompoundTag data = target.getPersistentData();
        if (data.contains(TAG_CURE_UNTIL)) {
            return false;
        }
        long now = target.level().getGameTime();
        int stage1 = STAGE1_MIN_TICKS + target.getRandom().nextInt(STAGE1_RANDOM_TICKS + 1);
        data.putLong(TAG_CURE_START, now);
        data.putLong(TAG_CURE_UNTIL, now + stage1 + STAGE2_TICKS);
        data.putUUID(TAG_CURE_PLAYER, player.getUUID());
        target.level().playSound(null, target.blockPosition(),
                SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.NEUTRAL, 1.0F, 1.0F);
        return true;
    }

    @SubscribeEvent
    public static void onGirlTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        // 只处理可被治愈的形态；幸存者少女（已是人类）直接跳过
        if (entity instanceof SurvivorGirlEntity) {
            return;
        }
        if (!(entity instanceof ZombieGirlEntity) && !(entity instanceof DrownedGirlEntity)) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(TAG_CURE_UNTIL) || !entity.isAlive()) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        long now = level.getGameTime();
        long until = data.getLong(TAG_CURE_UNTIL);
        if (now >= until) {
            finishCure(entity, level, data);
            return;
        }

        long remaining = until - now;
        double px = entity.getRandomX(1.0D);
        double py = entity.getRandomY();
        double pz = entity.getRandomZ(1.0D);
        if (remaining <= STAGE2_TICKS) {
            // 二阶段：粒子变得频繁——每 3 tick 一波绿色十字，每 10 tick 一圈治疗旋涡
            if (entity.tickCount % 3 == 0) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, px, py, pz,
                        4, 0.3D, 0.5D, 0.3D, 0.0D);
            }
            if (entity.tickCount % 10 == 0) {
                level.sendParticles(ParticleTypes.INSTANT_EFFECT,
                        entity.getX(), entity.getY(0.5D), entity.getZ(),
                        2, 0.4D, 0.6D, 0.4D, 0.0D);
            }
        } else if (entity.tickCount % 20 == 0) {
            // 一阶段：每秒冒出几颗绿色十字
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, px, py, pz,
                    3, 0.3D, 0.5D, 0.3D, 0.0D);
        }
    }

    /**
     * 三阶段收尾：爆炸烟雾 + 浓烟遮住模型，烟雾里完成变身；
     * 然后清掉净化标记并调用治愈转换（成就发给治愈者）。
     */
    private static void finishCure(LivingEntity entity, ServerLevel level, CompoundTag data) {
        data.remove(TAG_CURE_START);
        data.remove(TAG_CURE_UNTIL);
        UUID curerUuid = data.hasUUID(TAG_CURE_PLAYER) ? data.getUUID(TAG_CURE_PLAYER) : null;
        data.remove(TAG_CURE_PLAYER);

        double cx = entity.getX();
        double cy = entity.getY(0.5D);
        double cz = entity.getZ();
        // 大团烟雾挡住整个模型，变身在烟雾掩护下完成
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, cx, cy, cz,
                60, 0.6D, 0.9D, 0.6D, 0.01D);
        // 净化完成的绿色十字与音效
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, cx, cy, cz,
                24, 0.5D, 0.8D, 0.5D, 0.0D);
        level.playSound(null, entity.blockPosition(),
                SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.NEUTRAL, 1.0F, 1.0F);

        ServerPlayer curer = curerUuid != null
                ? level.getServer().getPlayerList().getPlayer(curerUuid) : null;
        SurvivorGirlEntity.cureFrom(entity, curer);
    }
}
