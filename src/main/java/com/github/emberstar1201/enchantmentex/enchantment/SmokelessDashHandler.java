package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.network.NetworkHandler;
import com.github.emberstar1201.enchantmentex.network.SmokelessDashSyncPacket;
import com.mojang.logging.LogUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// "无烟冲击"（Smokeless Dash）事件处理器
//
// 【核心逻辑】
//   1. 速度倍率存储在玩家 PersistentData + SPEED_CACHE 缓存
//   2. 客户端滚轮 → C2S 数据包 → 服务端更新倍率
//   3. PlayerTickEvent：客户端和服务端同时应用速度加速
//   4. 每次起飞重置为 1.0
//   5. 每 10 tick 发送 S2C 同步包确保客户端缓存准确
//   6. 速度上限保护 + 粒子特效
//
// ★ 关键设计：客户端和服务端同时加速
//   原版鞘翅物理在 LivingEntity.aiStep() 中每 tick 重新计算速度，
//   如果只在服务端加速，客户端 LocalPlayer 会被原版物理重置。
//   双向加速确保玩家立即感受到效果，服务端保持权威。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SmokelessDashHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    // NBT 键名
    private static final String TAG_ROOT = "SmokelessDashData";
    private static final String KEY_SPEED = "speed";

    // ★ 速度倍率缓存（服务端写入 + S2C 同步到客户端 + 客户端滚轮立即更新）
    // ConcurrentHashMap 保证跨线程安全
    public static final Map<UUID, Double> SPEED_CACHE = new ConcurrentHashMap<>();

    // 飞行状态跟踪表：记录玩家上一 tick 是否在飞行
    private static final Map<UUID, Boolean> PREV_FLYING = new ConcurrentHashMap<>();

    // S2C 同步计数器
    private static final Map<UUID, Integer> SYNC_TIMER = new ConcurrentHashMap<>();

    // ========================================================================
    // 读取玩家的当前速度倍率（更新缓存）
    // ========================================================================
    private static double getSpeedMultiplier(Player player) {
        CompoundTag data = player.getPersistentData().getCompound(TAG_ROOT);
        double speed = data.getDouble(KEY_SPEED);
        if (speed < 1.0) speed = 1.0;
        double max = Config.smokelessDashMaxMultiplier;
        if (speed > max) speed = max;

        // 更新缓存
        SPEED_CACHE.put(player.getUUID(), speed);
        return speed;
    }

    // ========================================================================
    // 设置玩家的速度倍率（含 clamp + 缓存更新）
    // ========================================================================
    private static void setSpeedMultiplier(Player player, double value) {
        double clamped = Math.max(1.0, Math.min(Config.smokelessDashMaxMultiplier, value));
        CompoundTag data = player.getPersistentData().getCompound(TAG_ROOT);
        data.putDouble(KEY_SPEED, clamped);
        player.getPersistentData().put(TAG_ROOT, data);

        // 更新缓存
        SPEED_CACHE.put(player.getUUID(), clamped);
    }

    // ========================================================================
    // 公有方法：获取缓存的速度倍率（供客户端 HUD 读取）
    // ========================================================================
    public static double getCachedMultiplier(UUID uuid) {
        Double val = SPEED_CACHE.get(uuid);
        return val != null ? val : 1.0;
    }

    // ========================================================================
    // 检查玩家是否穿着带附魔的鞘翅
    // ========================================================================
    private static boolean hasEnchantedElytra(Player player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() != Items.ELYTRA) return false;
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.SMOKELESS_DASH.get(), chest);
        return level > 0;
    }

    // ========================================================================
    // 服务端入口：处理客户端发来的滚轮调整数据包
    // ========================================================================
    public static void handleScrollDelta(ServerPlayer player, double delta) {
        if (!player.isFallFlying()) {
            LOGGER.debug("[SmokelessDash] handleScrollDelta 跳过：玩家未在飞行");
            return;
        }
        if (!hasEnchantedElytra(player)) {
            LOGGER.debug("[SmokelessDash] handleScrollDelta 跳过：无附魔鞘翅");
            return;
        }

        double current = getSpeedMultiplier(player);
        double newVal = current + delta;
        setSpeedMultiplier(player, newVal);
        LOGGER.debug("[SmokelessDash] handleScrollDelta: delta={}, 旧倍率={}, 新倍率={}",
                String.format("%.3f", delta),
                String.format("%.2f", current),
                String.format("%.2f", newVal));
    }

    // ========================================================================
    // PlayerTickEvent：每 tick 加速 + 状态检测 + 粒子 + S2C 同步
    //
    // ★ 客户端和服务端都执行加速逻辑（双向加速）
    // ★ 粒子和 S2C 同步仅在服务端执行
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        UUID uuid = player.getUUID();

        boolean isServer = !player.level().isClientSide();

        // ================================================================
        // 飞行状态切换检测
        // ================================================================
        boolean currentlyFlying = player.isFallFlying();
        Boolean wasFlying = PREV_FLYING.get(uuid);

        if (wasFlying != null && !wasFlying && currentlyFlying) {
            // 刚进入飞行状态 → 重置倍率为 1.0
            setSpeedMultiplier(player, 1.0);
            LOGGER.debug("[SmokelessDash] 进入飞行状态，重置倍率=1.0");
        }
        PREV_FLYING.put(uuid, currentlyFlying);

        // ================================================================
        // 只有正在飞行且有附魔鞘翅时才加速
        // ================================================================
        if (!currentlyFlying) return;
        if (!hasEnchantedElytra(player)) return;

        // 读取倍率（服务端从 NBT 读，客户端从 SPEED_CACHE 读）
        double multiplier;
        if (isServer) {
            multiplier = getSpeedMultiplier(player);
        } else {
            multiplier = getCachedMultiplier(uuid);
            // 如果缓存中没有且未设置，默认 1.0（无加速）
        }

        // ★ 倍率 > 1.0 才加速（1.0 是原版速度）
        if (multiplier <= 1.0) return;

        // ================================================================
        // ★ 修复：视线方向加法加速公式
        //
        // 原问题：boostFactor 缩放公式太弱，且只在服务端执行。
        // 客户端 LocalPlayer 的 aiStep() 每 tick 重新计算速度，
        // 服务端设的速度被覆盖，玩家感觉不到加速。
        //
        // 新公式：沿视线方向添加固定加速度
        //   acceleration = (倍率 - 1) × baseBoost
        //   baseBoost 默认 0.12，在 1.5x 时每 tick 加 0.06 blocks/tick
        //   1 秒（20 tick）后水平速度增量 ≈ 1.2 blocks/tick
        //
        // 这样客户端和服务端同时加速，玩家即刻感受到效果。
        // 服务端通过 velocity 同步保证客户端位置准确。
        // ================================================================
        Vec3 lookVec = player.getLookAngle();
        double acceleration = (multiplier - 1.0) * Config.smokelessDashBaseBoost;

        // 沿视线方向加速（垂直分量减弱防止抬头失速）
        player.setDeltaMovement(
                player.getDeltaMovement().add(
                        lookVec.x * acceleration,
                        lookVec.y * acceleration * 0.6,
                        lookVec.z * acceleration
                )
        );

        // ================================================================
        // 速度上限保护（仅服务端）
        // ================================================================
        if (isServer) {
            Vec3 currentVel = player.getDeltaMovement();
            double maxHorizSpeed = Config.smokelessDashMaxMultiplier * 1.5;
            double horizSpeed = Math.sqrt(
                    currentVel.x * currentVel.x + currentVel.z * currentVel.z);
            if (horizSpeed > maxHorizSpeed) {
                double scale = maxHorizSpeed / horizSpeed;
                player.setDeltaMovement(
                        currentVel.x * scale, currentVel.y, currentVel.z * scale);
            }

            double maxVerticalSpeed = maxHorizSpeed * 0.8;
            if (currentVel.y > maxVerticalSpeed) {
                player.setDeltaMovement(
                        currentVel.x, maxVerticalSpeed, currentVel.z);
            }

            // ================================================================
            // 粒子效果（仅服务端）
            // ================================================================
            if (multiplier > 1.5 && player instanceof ServerPlayer serverPlayer) {
                ServerLevel level = serverPlayer.serverLevel();
                if (level.getGameTime() % 3 == 0) {
                    Vec3 pos = player.position();

                    // 后方白色气流
                    level.sendParticles(ParticleTypes.END_ROD,
                            pos.x - lookVec.x * 1.5,
                            pos.y - lookVec.y * 1.5 + 0.5,
                            pos.z - lookVec.z * 1.5,
                            2, 0.3, 0.3, 0.3, 0.01);

                    // 高速 ( >2.0 ) 时额外金色闪光
                    if (multiplier > 2.0 && level.getGameTime() % 6 == 0) {
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                                pos.x - lookVec.x * 2.0,
                                pos.y - lookVec.y * 2.0 + 0.5,
                                pos.z - lookVec.z * 2.0,
                                1, 0.2, 0.2, 0.2, 0.02);
                    }
                }
            }

            // ================================================================
            // S2C 同步（每 10 tick）
            // ================================================================
            if (player instanceof ServerPlayer serverPlayer) {
                int timer = SYNC_TIMER.getOrDefault(uuid, 0) + 1;
                SYNC_TIMER.put(uuid, timer);

                if (timer >= 10) {
                    SYNC_TIMER.put(uuid, 0);
                    double currentMultiplier = getSpeedMultiplier(player);
                    NetworkHandler.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> serverPlayer),
                            new SmokelessDashSyncPacket(currentMultiplier)
                    );
                }
            }
        }
    }
}