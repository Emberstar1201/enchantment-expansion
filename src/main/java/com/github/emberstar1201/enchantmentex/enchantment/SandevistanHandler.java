package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.SandevistanConfig;
import com.github.emberstar1201.enchantmentex.network.NetworkHandler;
import com.github.emberstar1201.enchantmentex.network.SandevistanStatePacket;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// ========================================================================
// 【斯安维斯坦】（Sandevistan）核心事件处理器（服务端权威）
//
// 效果：按键激活后短时间内，范围内"世界时间减缓"，激活者自身变快。
//
// 分对象实现（手感与安全性的折中）：
//   1. 普通生物（怪物/动物）：LivingTickEvent 按"慢放分母"取消 tick。
//      分母 N = round(1/时缓倍率)，世界每 N tick 放行 1 tick，
//      生物以 1/N 速度【均匀慢放】行动（固定相位放行，不随机卡顿）。
//   2. Boss（末影龙/凋灵）：不冻结 tick（AI 阶段复杂易出问题），
//      改为每 tick 刷新高阶缓慢药水（无图标无粒子）。
//   3. 其他玩家：挂移速/攻速属性修饰符（MULTIPLY_TOTAL 负值）。
//      客户端原生支持属性同步，被减速玩家看到的是平滑慢动作，
//      不会出现服务端跳 tick 导致的橡胶带回滚。
//   4. 弹射物（箭/三叉戟等）：速度向量每 tick 乘时缓倍率，慢动作飞行；
//      激活者自己发射的弹射物不减速。
//   5. 掉落物/经验球：可选开关，同样衰减速度，纯视觉效果。
//
// 激活者自身：移速 + 攻速属性加成（MULTIPLY_BASE，固定 UUID transient），
// 结束/死亡/下线/换下胸甲时全部清理，绝不残留。
//
// 多人规则：多个玩家同时激活不叠加，范围内取最强（倍率最小）的时缓；
// 冷却各自独立。
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SandevistanHandler {

    // 玩家 PersistentData 中的冷却截止时间戳（gameTime tick）
    private static final String COOLDOWN_KEY = "SandevistanCooldownUntil";

    // ========================================================================
    // 固定 UUID：属性修饰符（避免重复叠加；结束时按 UUID 精确移除）
    // ========================================================================
    private static final UUID SELF_SPEED_UUID =
            UUID.fromString("f3a7c1e2-4b6d-4c8f-9e0a-1b2c3d4e5f60");
    private static final UUID SELF_ATTACK_SPEED_UUID =
            UUID.fromString("f3a7c1e2-4b6d-4c8f-9e0a-1b2c3d4e5f61");
    private static final String SELF_SPEED_NAME = "Sandevistan self speed";
    private static final String SELF_ATTACK_SPEED_NAME = "Sandevistan self attack speed";

    private static final UUID SLOW_SPEED_UUID =
            UUID.fromString("f3a7c1e2-4b6d-4c8f-9e0a-1b2c3d4e5f62");
    private static final UUID SLOW_ATTACK_SPEED_UUID =
            UUID.fromString("f3a7c1e2-4b6d-4c8f-9e0a-1b2c3d4e5f63");
    private static final String SLOW_SPEED_NAME = "Sandevistan time slow speed";
    private static final String SLOW_ATTACK_SPEED_NAME = "Sandevistan time slow attack speed";

    // 斯安维斯坦主题色：青蓝色（用于粒子）
    private static final DustParticleOptions CYAN_DUST =
            new DustParticleOptions(new Vector3f(0.0f, 0.75f, 1.0f), 1.4f);

    // ========================================================================
    // 运行时状态表
    // ========================================================================

    /** 当前所有激活中的时缓实例：激活者 UUID → 实例数据 */
    public static final Map<UUID, ActiveInstance> ACTIVE = new ConcurrentHashMap<>();

    /** 当前被时缓减速的其他玩家：玩家 UUID → 时缓倍率（每 tick 全量重算） */
    private static final Map<UUID, Double> SLOWED_PLAYERS = new ConcurrentHashMap<>();

    /** 一个激活中的时缓实例 */
    public static class ActiveInstance {
        public final UUID activatorId;
        public final int level;
        public final long endTime;      // 结束时刻（维度 gameTime tick）
        public final double scale;      // 时缓倍率（0.25 = 世界 1/4 速）
        public final int denominator;   // 慢放分母（每 N tick 放行 1 tick）

        ActiveInstance(UUID activatorId, int level, long endTime, double scale, int denominator) {
            this.activatorId = activatorId;
            this.level = level;
            this.endTime = endTime;
            this.scale = scale;
            this.denominator = denominator;
        }
    }

    // ========================================================================
    // 激活入口（C2S 包调用）：全部服务端校验
    // ========================================================================
    public static void tryActivate(ServerPlayer player) {
        // 已在激活中：忽略
        if (ACTIVE.containsKey(player.getUUID())) return;

        // 校验 1：胸甲带有斯安维斯坦附魔
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.SANDEVISTAN.get(), chest);
        if (level <= 0) {
            player.sendSystemMessage(Component.translatable(
                    "message.enchantment_expansion.sandevistan.no_armor"));
            return;
        }

        // 校验 2：冷却
        long now = player.level().getGameTime();
        long cdUntil = player.getPersistentData().getLong(COOLDOWN_KEY);
        if (now < cdUntil) {
            long secondsLeft = Math.max(0, (cdUntil - now + 19) / 20);
            player.sendSystemMessage(Component.translatable(
                    "message.enchantment_expansion.sandevistan.cooldown", secondsLeft));
            return;
        }

        // 校验 3：饥饿值（模拟植入体能耗）
        int hungerCost = SandevistanConfig.getHungerCost(level);
        if (player.getFoodData().getFoodLevel() < hungerCost) {
            player.sendSystemMessage(Component.translatable(
                    "message.enchantment_expansion.sandevistan.no_hunger"));
            return;
        }
        if (hungerCost > 0) {
            player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() - hungerCost);
        }

        // ===== 激活 =====
        int duration = SandevistanConfig.getDurationTicks(level);
        double scale = SandevistanConfig.getTimeScale(level);
        int denominator = SandevistanConfig.getSlowDenominator(level);
        long endTime = now + duration;

        ACTIVE.put(player.getUUID(),
                new ActiveInstance(player.getUUID(), level, endTime, scale, denominator));
        // 冷却从效果结束之后开始计算
        player.getPersistentData().putLong(COOLDOWN_KEY,
                endTime + SandevistanConfig.getCooldownTicks(level));

        // 激活者自身增益
        applySelfModifiers(player, level);

        // 音效 + 粒子 + 提示消息
        ServerLevel serverLevel = player.serverLevel();
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.7F);
        serverLevel.sendParticles(CYAN_DUST,
                player.getX(), player.getY() + 1.0, player.getZ(),
                40, 0.6, 0.9, 0.6, 0.03);
        player.sendSystemMessage(Component.translatable(
                "message.enchantment_expansion.sandevistan.activated"));

        // 广播激活状态给附近客户端（驱动 FOV / 色调 / 粒子）
        broadcastState(serverLevel, player, true, scale, endTime);
    }

    // ========================================================================
    // 结束激活：移除自身增益 + 广播结束（受影响玩家的减速由 tick 重算自动清理）
    // ========================================================================
    private static void deactivate(MinecraftServer server, UUID uuid) {
        ActiveInstance inst = ACTIVE.remove(uuid);
        if (inst == null) return;

        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            removeSelfModifiers(player);
            ServerLevel level = player.serverLevel();
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.CONDUIT_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 0.8F);
            broadcastState(level, player, false, 0.0, 0L);
        }
    }

    // ========================================================================
    // 服务端 tick：到期清理 + 玩家减速重算 + 弹射物/掉落物减速
    // ========================================================================
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;

        long now = server.overworld().getGameTime();

        // 1. 到期实例清理
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, ActiveInstance> entry : ACTIVE.entrySet()) {
            if (entry.getValue().endTime <= now) {
                expired.add(entry.getKey());
            }
        }
        for (UUID uuid : expired) {
            deactivate(server, uuid);
        }

        // 2. 玩家减速修饰符全量重算（覆盖上线/下线/离范围/多实例重叠）
        if (SandevistanConfig.affectPlayers) {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                ActiveInstance inst = null;
                // 激活者自己不吃减速
                if (!ACTIVE.containsKey(sp.getUUID())) {
                    inst = getStrongest(sp);
                }
                Double old = SLOWED_PLAYERS.get(sp.getUUID());
                if (inst != null) {
                    if (old == null || Math.abs(old - inst.scale) > 0.001) {
                        applySlowModifiers(sp, inst.scale);
                        SLOWED_PLAYERS.put(sp.getUUID(), inst.scale);
                    }
                } else if (old != null) {
                    removeSlowModifiers(sp);
                    SLOWED_PLAYERS.remove(sp.getUUID());
                }
            }
        } else if (!SLOWED_PLAYERS.isEmpty()) {
            // 配置关闭时兜底移除全部
            for (UUID uuid : SLOWED_PLAYERS.keySet()) {
                ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                if (sp != null) removeSlowModifiers(sp);
            }
            SLOWED_PLAYERS.clear();
        }

        // 3. 各维度弹射物 / 掉落物减速
        boolean slowProjectiles = SandevistanConfig.affectProjectiles;
        boolean slowItems = SandevistanConfig.affectItems;
        if (ACTIVE.isEmpty() || (!slowProjectiles && !slowItems)) return;

        for (ServerLevel level : server.getAllLevels()) {
            // 合并本维度所有激活者的影响范围
            AABB box = null;
            // 区块范围 AABB：以激活者中心为基准，X/Z 半宽 = 区块数×16 格，
            // Y 覆盖整个可建造高度（时缓对竖直高度不设限，符合区块概念）。
            double halfX = SandevistanConfig.radiusChunksX * 16.0;
            double halfZ = SandevistanConfig.radiusChunksZ * 16.0;
            for (ActiveInstance inst : ACTIVE.values()) {
                Entity act = level.getEntity(inst.activatorId);
                if (act == null) continue;
                AABB a = new AABB(
                        act.getX() - halfX, level.getMinBuildHeight(), act.getZ() - halfZ,
                        act.getX() + halfX, level.getMaxBuildHeight(), act.getZ() + halfZ);
                box = (box == null) ? a : box.minmax(a);
            }
            if (box == null) continue;

            if (slowProjectiles) {
                for (Projectile proj : level.getEntitiesOfClass(Projectile.class, box)) {
                    ActiveInstance inst = getStrongest(proj);
                    if (inst == null) continue;
                    // 激活者自己发射的弹射物：默认保持原速（强化「只有我快」手感）；
                    // 配置 affectSelfProjectiles=true 时同样慢动作（电影感）。
                    Entity owner = proj.getOwner();
                    if (!SandevistanConfig.affectSelfProjectiles
                            && owner != null && ACTIVE.containsKey(owner.getUUID())) continue;
                    proj.setDeltaMovement(proj.getDeltaMovement().scale(inst.scale));
                }
            }

            if (slowItems) {
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
                    ActiveInstance inst = getStrongest(item);
                    if (inst != null) {
                        item.setDeltaMovement(item.getDeltaMovement().scale(inst.scale));
                    }
                }
                for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box)) {
                    ActiveInstance inst = getStrongest(orb);
                    if (inst != null) {
                        orb.setDeltaMovement(orb.getDeltaMovement().scale(inst.scale));
                    }
                }
            }
        }
    }

    // ========================================================================
    // 生物 tick：普通生物冻结慢放 / Boss 药水减速
    // （玩家不在此处理——玩家走属性修饰符路线）
    // ========================================================================
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (entity instanceof Player) return;

        ActiveInstance inst = getStrongest(entity);
        if (inst == null) return;

        // Boss：不取消 tick（末影龙/凋灵 AI 阶段复杂，冻结易出异常），改用缓慢药水
        boolean isBoss = entity instanceof EnderDragon || entity instanceof WitherBoss;
        if (SandevistanConfig.affectBosses && isBoss) {
            // 缓慢每级 -15% 移速：放大器 = round((1-倍率)/0.15) - 1
            int amplifier = Math.max(0,
                    Math.round((1.0f - (float) inst.scale) / 0.15f) - 1);
            entity.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, 40, amplifier,
                    false, false, false));
            return;
        }

        // 普通生物：每 denominator tick 放行 1 tick（移动 + AI 一起慢放）。
        // ★ 关键：单靠取消 LivingTickEvent 只能冻结 baseTick 的移动逻辑，
        //   Mob.serverAiStep()（拉弓/弩装填/寻路目标等 AI）在 Mob.tick 中
        //   于 super.tick() 之后单独调用，不受这个 cancel 影响。
        //   所以 AI 的部分交由 Mixin SandevistanAIStallMixin 在同一分母下 gate。
        // 用 (gameTime + 实体ID) 做相位错开，避免所有怪物同步"一顿"。
        if (shouldStallAI(entity)) {
            event.setCanceled(true);
        }
    }

    // ========================================================================
    // 工具：该生物在此 tick 是否应被"冻结"（移动 + AI 都慢放）。
    // 被 Mixin(SandevistanAIStallMixin) 和 onLivingTick 共同调用，
    // 保证移动与 AI(serverAiStep) 用完全相同的分母与相位判定，步调一致。
    // 返回 true = 此 tick 跳过该生物的移动与 AI。
    // ========================================================================
    public static boolean shouldStallAI(LivingEntity entity) {
        if (entity.level().isClientSide()) return false;
        if (entity instanceof Player) return false;          // 玩家走属性修饰符路线
        ActiveInstance inst = getStrongest(entity);
        if (inst == null) return false;                      // 不在时缓场内
        boolean isBoss = entity instanceof EnderDragon || entity instanceof WitherBoss;
        if (SandevistanConfig.affectBosses && isBoss) return false; // Boss 走缓慢药水
        // 同一相位错开判定：分母内放行 1 tick
        long gameTime = entity.level().getGameTime();
        return (gameTime + entity.getId()) % inst.denominator != 0;
    }

    // ========================================================================
    // 异常清理：激活者死亡 / 下线 / 换下胸甲 → 立即结束时缓
    // ========================================================================
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp
                && ACTIVE.containsKey(sp.getUUID())) {
            deactivate(sp.server, sp.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp
                && ACTIVE.containsKey(sp.getUUID())) {
            deactivate(sp.server, sp.getUUID());
        }
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getSlot() != EquipmentSlot.CHEST) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!ACTIVE.containsKey(sp.getUUID())) return;

        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.SANDEVISTAN.get(), event.getTo());
        if (level <= 0) {
            deactivate(sp.server, sp.getUUID());
        }
    }

    // ========================================================================
    // 工具：返回影响该实体的最强时缓实例（同维度、半径内、倍率最小者）
    // ========================================================================
    private static ActiveInstance getStrongest(Entity entity) {
        ActiveInstance best = null;
        // ServerLevel.getEntity(UUID) 只返回本维度实体，跨维度自然为 null；
        // Level 基类只有 getEntity(int)，故此处必须强转 ServerLevel（调用点均在服务端）
        if (!(entity.level() instanceof ServerLevel serverLevel)) return null;
        // 区块范围判定：X / Z 各自半宽 = 区块数×16 格（轴对齐矩形区域，不再用球形半径）。
        double halfX = SandevistanConfig.radiusChunksX * 16.0;
        double halfZ = SandevistanConfig.radiusChunksZ * 16.0;
        for (ActiveInstance inst : ACTIVE.values()) {
            Entity activator = serverLevel.getEntity(inst.activatorId);
            if (activator == null) continue;
            double dx = Math.abs(activator.getX() - entity.getX());
            double dz = Math.abs(activator.getZ() - entity.getZ());
            if (dx > halfX || dz > halfZ) continue;
            if (best == null || inst.scale < best.scale) {
                best = inst;
            }
        }
        return best;
    }

    // ========================================================================
    // 广播时缓状态给附近客户端
    // ========================================================================
    private static void broadcastState(ServerLevel level, Player activator,
                                       boolean active, double scale, long endTime) {
        // 广播半径取 X/Z 区块中较大者折算成格，再加冗余 48 格，确保场内客户端都收到状态
        double maxChunks = Math.max(SandevistanConfig.radiusChunksX, SandevistanConfig.radiusChunksZ);
        double broadcastRadius = maxChunks * 16.0 + 48.0;
        NetworkHandler.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        activator.getX(), activator.getY(), activator.getZ(),
                        broadcastRadius, level.dimension())),
                new SandevistanStatePacket(activator.getUUID(), active, scale, endTime));
    }

    // ========================================================================
    // 属性修饰符：激活者自身增益
    // ========================================================================
    private static void applySelfModifiers(Player player, int level) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SELF_SPEED_UUID);
            speed.addTransientModifier(new AttributeModifier(
                    SELF_SPEED_UUID, SELF_SPEED_NAME,
                    SandevistanConfig.getSelfSpeedBonus(level),
                    AttributeModifier.Operation.MULTIPLY_BASE));
        }
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            attackSpeed.removeModifier(SELF_ATTACK_SPEED_UUID);
            attackSpeed.addTransientModifier(new AttributeModifier(
                    SELF_ATTACK_SPEED_UUID, SELF_ATTACK_SPEED_NAME,
                    SandevistanConfig.getSelfAttackSpeedBonus(level),
                    AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    private static void removeSelfModifiers(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SELF_SPEED_UUID);
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) attackSpeed.removeModifier(SELF_ATTACK_SPEED_UUID);
    }

    // ========================================================================
    // 属性修饰符：时缓场内其他玩家减速（移速 + 攻速按倍率压缩）
    // ========================================================================
    private static void applySlowModifiers(Player player, double scale) {
        // MULTIPLY_TOTAL 负值：scale=0.25 → amount=-0.75 → 最终速度 ×0.25
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOW_SPEED_UUID);
            speed.addTransientModifier(new AttributeModifier(
                    SLOW_SPEED_UUID, SLOW_SPEED_NAME,
                    scale - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            attackSpeed.removeModifier(SLOW_ATTACK_SPEED_UUID);
            attackSpeed.addTransientModifier(new AttributeModifier(
                    SLOW_ATTACK_SPEED_UUID, SLOW_ATTACK_SPEED_NAME,
                    scale - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void removeSlowModifiers(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SLOW_SPEED_UUID);
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) attackSpeed.removeModifier(SLOW_ATTACK_SPEED_UUID);
    }
}
