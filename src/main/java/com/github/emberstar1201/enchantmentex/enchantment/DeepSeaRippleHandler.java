package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.DeepSeaRippleConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【深海的涟漪】主处理器（服务端+客户端双向）
//
// 【加速机制说明】
//   原版水中游泳每 tick 受重力 + 水阻影响，速度会被反复重算。
//   因此采用“每 tick 沿视线水平方向加速 + 封顶”的思路：
//     1. 加速度 a = (multiplier - 1.0) × baseAcceleration
//     2. 水平速度上限 = multiplier × 0.5（原版约 0.5 blocks/tick 为游泳上限）
//   客户端和服务端同时执行 → 玩家零延迟感受加速，服务端保持位置权威。
//
// 【倍率存储】
//   · 服务端：player.getPersistentData() + MULTIPLIER_CACHE
//   · 客户端：MULTIPLIER_CACHE（滚轮事件中即时写入，无需等服务端）
//   · 默认倍率 1.0（等同于原版水中速度）
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DeepSeaRippleHandler {

    // PersistentData 根复合标签与 key
    private static final String TAG_ROOT = "DeepSeaRippleData";
    private static final String KEY_MULTIPLIER = "multiplier";

    // 倍率缓存：Client & Server 共享，ConcurrentHashMap 线程安全
    public static final Map<UUID, Double> MULTIPLIER_CACHE =
            new ConcurrentHashMap<>();

    // ActionBar 节流：每 15 tick（0.75s）更新一次，防止刷屏
    private static final Map<UUID, Integer> ACTIONBAR_COOLDOWN =
            new ConcurrentHashMap<>();

    // ========================================================================
    // 读取当前倍率（服务端从 NBT 读取，顺带写入缓存）
    // ========================================================================
    private static double readMultiplier(Player player) {
        CompoundTag tag = player.getPersistentData().getCompound(TAG_ROOT);
        double oldM = tag.getDouble(KEY_MULTIPLIER);
        double m = oldM;
        if (m < 1.0) m = 1.0;
        double max = DeepSeaRippleConfig.getMaxSwimMultiplier();
        if (m > max) m = max;
        // 只在值实际变化时才写入 NBT，避免每 tick 触发玩家数据同步包
        if (m != oldM) {
            tag.putDouble(KEY_MULTIPLIER, m);
            player.getPersistentData().put(TAG_ROOT, tag);
        }
        MULTIPLIER_CACHE.put(player.getUUID(), m);
        return m;
    }

    // ========================================================================
    // 写入倍率（服务端使用）：clamp → NBT → 缓存
    // ========================================================================
    private static void writeMultiplier(Player player, double value) {
        double clamped = Math.max(1.0,
                Math.min(DeepSeaRippleConfig.getMaxSwimMultiplier(), value));
        CompoundTag tag = player.getPersistentData().getCompound(TAG_ROOT);
        tag.putDouble(KEY_MULTIPLIER, clamped);
        player.getPersistentData().put(TAG_ROOT, tag);
        MULTIPLIER_CACHE.put(player.getUUID(), clamped);
    }

    // ========================================================================
    // 对外：读取 CACHE 中的倍率（客户端 HUD / ClientTick 使用）
    // ========================================================================
    public static double getCachedMultiplier(UUID uuid) {
        Double v = MULTIPLIER_CACHE.get(uuid);
        return v != null ? v : 1.0;
    }

    // ========================================================================
    // 服务端：C2S 滚轮包入口
    // ========================================================================
    public static void handleScrollDelta(ServerPlayer player, double delta) {
        if (!DeepSeaRippleConfig.isEnabled()) return;
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DEEP_SEA_RIPPLE.get(), boots);
        if (level <= 0) return;
        if (!player.isInWater() && !player.isSwimming()) return;

        double current = readMultiplier(player);
        writeMultiplier(player, current + delta);
    }

    // ================================================================
    // 辅助：判断玩家是否在水中（含游泳状态）
    // ================================================================
    private static boolean isUnderwater(Player p) {
        return p.isInWater() || p.isSwimming();
    }

    // ========================================================================
    // PlayerTickEvent：服务端+客户端 同时执行
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!DeepSeaRippleConfig.isEnabled()) return;

        Player player = event.player;
        UUID uuid = player.getUUID();
        boolean isServer = !player.level().isClientSide();

        // 1) 检查靴子附魔
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DEEP_SEA_RIPPLE.get(), boots);
        if (level <= 0) {
            // 脱靴：倍率重置回 1.0
            if (isServer) {
                CompoundTag tag = player.getPersistentData().getCompound(TAG_ROOT);
                if (tag.contains(KEY_MULTIPLIER)) {
                    tag.remove(KEY_MULTIPLIER);
                    player.getPersistentData().put(TAG_ROOT, tag);
                }
            }
            MULTIPLIER_CACHE.remove(uuid);
            return;
        }

        // 2) 确定当前倍率（服务端→NBT；客户端→CACHE）
        double multiplier = isServer ? readMultiplier(player)
                                     : getCachedMultiplier(uuid);

        // 3) 不在水中 → 不做速度修改（保留倍率）
        if (!isUnderwater(player)) return;

        // 4) 倍率 > 1.0 时执行加速
        if (multiplier > 1.0 + 0.001) {
            applySwimBoost(player, multiplier);
        }

        // 5) 粒子 + ActionBar（节流）
        int cd = ACTIONBAR_COOLDOWN.getOrDefault(uuid, 0) + 1;
        ACTIONBAR_COOLDOWN.put(uuid, cd);

        if (cd % 3 == 0) {
            spawnBubbleParticles(player);
        }
        if (cd >= 15 && isServer && player instanceof ServerPlayer sp) {
            ACTIONBAR_COOLDOWN.put(uuid, 0);
            sendActionBar(sp, multiplier);
        }
    }

    // ========================================================================
    // 游泳加速：沿视线水平方向施加推力，并做速度封顶
    //
    // 加速模式与 SmokelessDash 一致，目的是解决“服务端改的速度被
    // 客户端 LivingEntity.aiStep 水阻覆盖”的问题。
    // ========================================================================
    private static void applySwimBoost(Player player, double multiplier) {
        Vec3 look = player.getLookAngle();
        double accel = (multiplier - 1.0) * DeepSeaRippleConfig.getBaseAcceleration();

        // 垂直分量减半，避免单纯按 W+抬头无限往上冲
        player.setDeltaMovement(player.getDeltaMovement().add(
                look.x * accel,
                look.y * accel * 0.5,
                look.z * accel
        ));

        // ================================================================
        // 速度封顶（仅服务端做最终裁剪，防止越过上限）
        // 原版水平游泳速度约 0.5 blocks/tick，上限按倍率 × 0.5 计
        // 垂直速度上限单独限制，避免“顶天花板仍持续加速”
        // ================================================================
        if (!player.level().isClientSide()) {
            Vec3 v = player.getDeltaMovement();
            double horiz = Math.sqrt(v.x * v.x + v.z * v.z);
            double maxHoriz = multiplier * 0.5;
            if (horiz > maxHoriz && maxHoriz > 0.001) {
                double scale = maxHoriz / horiz;
                player.setDeltaMovement(v.x * scale, v.y, v.z * scale);
                v = player.getDeltaMovement();
            }
            double maxVert = Math.max(0.45, multiplier * 0.35);
            if (v.y > maxVert) {
                player.setDeltaMovement(v.x, maxVert, v.z);
            } else if (v.y < -maxVert) {
                player.setDeltaMovement(v.x, -maxVert, v.z);
            }
        }
    }

    // ========================================================================
    // 水中加速粒子：原版气泡粒子 + 少量水悬浮粒子
    // 只在客户端执行（减少服务端网络压力）
    // ========================================================================
    private static void spawnBubbleParticles(Player player) {
        if (!player.level().isClientSide()) return;

        double x = player.getX();
        double y = player.getY() + 0.3;
        double z = player.getZ();
        var level = player.level();

        for (int i = 0; i < 2; i++) {
            double ox = (level.random.nextDouble() - 0.5) * 0.5;
            double oy = level.random.nextDouble() * 0.4;
            double oz = (level.random.nextDouble() - 0.5) * 0.5;
            level.addParticle(
                    ParticleTypes.BUBBLE,
                    x + ox, y + oy, z + oz,
                    0, 0.06, 0
            );
        }
    }

    // ========================================================================
    // ActionBar 提示：§b 深海涟漪 x1.50
    // ========================================================================
    private static void sendActionBar(ServerPlayer player, double multiplier) {
        String text = "§b§l深海的涟漪 §r§3x"
                + String.format("%.2f", multiplier);
        player.displayClientMessage(Component.literal(text), true);
    }
}
