package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【飞轮效应】服务端事件处理器
//
// 核心功能：
//   1. handleDashRequest() — 收到 R 键数据包后触发冲刺
//   2. onPlayerTick() — 每 tick 更新冲刺状态 + 冷却完毕通知
//
// 冲刺状态机（DashState）：
//   - 记录起始位置、方向、最大距离、速度、已进行 tick 数
//   - 每 tick 设置玩家速度 + 无敌 + 粒子/音效
//   - 撞墙或超距后停止
//
// 冷却管理：
//   - 通过玩家 PersistentData 存储上次使用时间
//   - 冷却中按 R → 显示红色动作栏提示
//   - 冷却结束 → 显示绿色提示 + "叮"音效
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class FlywheelEffectHandler {

    // PersistentData 键名
    private static final String TAG_ROOT = "FlywheelData";
    private static final String KEY_LAST_USE = "lastUseTime";

    // ========================================================================
    // 冲刺状态：记录每个正在冲刺的玩家
    // ========================================================================
    private static final Map<UUID, DashState> DASHING_PLAYERS = new HashMap<>();

    private static class DashState {
        final Vec3 startPos;
        final Vec3 direction;
        final double maxDistance;
        final double speed;           // 每 tick 移动距离（blocks/tick）
        final int totalTicks;         // 总持续 tick 数
        int ticksElapsed;             // 已执行 tick 数
        boolean hasRefunded;          // 是否已退还耐久（防止重复退款）

        DashState(Vec3 startPos, Vec3 direction, double maxDistance) {
            this.startPos = startPos;
            this.direction = direction;
            this.maxDistance = maxDistance;
            // 冲刺速度：可在 Config 中调整，默认 1.2 blocks/tick
            this.speed = Config.flywheelDashSpeed;
            // 计算需要的 tick 数，至少 4 tick 确保有冲刺感
            this.totalTicks = Math.max(4, (int) Math.ceil(maxDistance / speed));
            this.ticksElapsed = 0;
            this.hasRefunded = false;
        }

        /** 当前已移动的距离 */
        double traveledDistance() {
            return ticksElapsed * speed;
        }
    }

    // ========================================================================
    // 收到客户端 R 键数据包 → 尝试启动冲刺
    // 由 FlywheelDashPacket.handle() 回调调用
    // ========================================================================
    public static void handleDashRequest(ServerPlayer player) {
        // ---------- 前置检查 ----------

        // 骑乘时禁用
        if (player.isPassenger()) return;

        // 检查靴子是否有飞轮附魔
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.FLYWHEEL_EFFECT.get(), boots);
        if (enchantLevel <= 0) return;

        // 检查冷却
        if (isOnCooldown(player)) {
            // 冷却中 → 红色动作栏提示
            player.displayClientMessage(
                    Component.literal("飞轮效应冷却中")
                            .withStyle(ChatFormatting.RED),
                    true // action bar
            );
            return;
        }

        // 检查耐久度是否足够（至少需要 3 点耐久）
        if (boots.getDamageValue() + Config.flywheelDurabilityCost > boots.getMaxDamage()) {
            return;
        }

        // ---------- 开始冲刺 ----------

        // 扣除耐久
        boots.hurtAndBreak(Config.flywheelDurabilityCost, player,
                p -> p.broadcastBreakEvent(EquipmentSlot.FEET));

        // 记录使用时间（冷却计时）
        setLastUseTime(player, player.level().getGameTime());

        // 计算冲刺参数
        Vec3 lookVec = player.getLookAngle();         // 准心方向（归一化向量）
        double distance = getDashDistance(enchantLevel); // 根据等级获取冲刺距离

        // 创建冲刺状态并入队
        DashState state = new DashState(
                player.position(),
                new Vec3(lookVec.x, Math.max(-0.5, Math.min(0.5, lookVec.y)), lookVec.z), // 限制垂直角度，避免飞太高
                distance
        );
        DASHING_PLAYERS.put(player.getUUID(), state);

        // 第一帧立即应用速度 + 无敌
        applyDashMotion(player, state);

        // 初始粒子爆发（冲刺起步特效）
        if (player.level() instanceof ServerLevel serverLevel) {
            spawnBurstParticles(serverLevel, player);
        }
    }

    // ========================================================================
    // 每 tick 更新：冲刺移动 + 冷却完毕通知
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return; // 只在 END 阶段处理
        Player player = event.player;
        if (player.level().isClientSide()) return;     // 仅服务端

        ServerPlayer serverPlayer = (ServerPlayer) player;

        // ---------- 1. 冲刺更新 ----------
        DashState state = DASHING_PLAYERS.get(player.getUUID());
        if (state != null) {
            updateDash(serverPlayer, state);
        }

        // ---------- 2. 冷却完毕通知 ----------
        checkCooldownCompletion(serverPlayer);
    }

    // ========================================================================
    // 每 tick 更新冲刺状态
    // ========================================================================
    private static void updateDash(ServerPlayer player, DashState state) {
        state.ticksElapsed++;

        // ----- 撞墙检测：检查玩家中心是否在固体方块内 -----
        if (isInsideWall(player)) {
            stopDash(player, state, true); // 撞墙 → 退还耐久
            return;
        }

        // ----- 超距检测 -----
        if (state.ticksElapsed > state.totalTicks) {
            stopDash(player, state, false); // 正常结束 → 不退耐久
            return;
        }

        // ----- 应用速度 -----
        applyDashMotion(player, state);

        // ----- 粒子效果（白色+蓝色螺旋轨迹） -----
        spawnDashParticles(player, state);

        // ----- 音效（幻翼飞行声，每 4 tick 播放一次） -----
        if (state.ticksElapsed % 4 == 0 && player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PHANTOM_FLAP,
                    SoundSource.PLAYERS,
                    0.6f, 1.0f + player.level().random.nextFloat() * 0.3f);
        }
    }

    // ========================================================================
    // 停止冲刺
    // ========================================================================
    private static void stopDash(ServerPlayer player, DashState state, boolean refundDurability) {
        // 从冲刺列表中移除
        DASHING_PLAYERS.remove(player.getUUID());

        // 取消无敌
        player.setInvulnerable(false);

        // 撞墙退还耐久
        if (refundDurability && !state.hasRefunded) {
            state.hasRefunded = true;
            ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
            // 退还 3 点耐久（不会低于 0）
            int newDamage = Math.max(0, boots.getDamageValue() - Config.flywheelDurabilityCost);
            boots.setDamageValue(newDamage);
        }

        // 结束粒子效果（小范围消散）
        if (player.level() instanceof ServerLevel serverLevel) {
            spawnStopParticles(serverLevel, player);
        }
    }

    // ========================================================================
    // 应用冲刺速度 + 无敌
    // ========================================================================
    private static void applyDashMotion(ServerPlayer player, DashState state) {
        // 设置速度 = 方向 × 速度值
        Vec3 velocity = new Vec3(
                state.direction.x * state.speed,
                state.direction.y * state.speed,
                state.direction.z * state.speed
        );
        player.setDeltaMovement(velocity);

        // 全程无敌
        player.setInvulnerable(true);

        // 标记玩家在移动，避免被服务器踢出（飞行检测）
        player.hurtMarked = true;
    }

    // ========================================================================
    // 撞墙检测：检查玩家身体中心是否在固体方块内
    // ========================================================================
    private static boolean isInsideWall(Player player) {
        // 检测三个关键位置：脚、身体中心、头
        BlockPos[] checkPositions = {
                player.blockPosition(),                                        // 脚
                BlockPos.containing(player.getX(), player.getY() + 0.9, player.getZ()), // 身体中心
                BlockPos.containing(player.getX(), player.getY() + 1.6, player.getZ())  // 头
        };
        for (BlockPos pos : checkPositions) {
            BlockState state = player.level().getBlockState(pos);
            if (state.isSolid()) {
                return true;
            }
        }
        return false;
    }

    // ========================================================================
    // 粒子效果：白色+蓝色螺旋轨迹
    // ========================================================================
    private static void spawnDashParticles(ServerPlayer player, DashState state) {
        ServerLevel level = (ServerLevel) player.level();
        double x = player.getX();
        double y = player.getY() + 0.8;
        double z = player.getZ();

        // 螺旋角度随 tick 变化
        double angle = state.ticksElapsed * 0.8;
        double radius = 0.6;

        // 白色粒子（END_ROD）：螺旋外侧
        level.sendParticles(ParticleTypes.END_ROD,
                x + Math.cos(angle) * radius,
                y,
                z + Math.sin(angle) * radius,
                1, 0, 0, 0, 0);

        // 蓝色粒子（SOUL_FIRE_FLAME）：螺旋另一侧
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                x + Math.cos(angle + Math.PI) * radius,
                y,
                z + Math.sin(angle + Math.PI) * radius,
                1, 0, 0, 0, 0);
    }

    /** 冲刺起步粒子爆发 */
    private static void spawnBurstParticles(ServerLevel level, ServerPlayer player) {
        level.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 0.8, player.getZ(),
                20, 0.5, 0.5, 0.5, 0.1);
    }

    /** 冲刺结束粒子消散 */
    private static void spawnStopParticles(ServerLevel level, ServerPlayer player) {
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                player.getX(), player.getY() + 0.8, player.getZ(),
                10, 0.3, 0.3, 0.3, 0.05);
    }

    // ========================================================================
    // 冷却管理
    // ========================================================================

    /** 检查玩家是否在冷却中 */
    private static boolean isOnCooldown(Player player) {
        CompoundTag data = player.getPersistentData().getCompound(TAG_ROOT);
        long lastUse = data.getLong(KEY_LAST_USE);
        long gameTime = player.level().getGameTime();
        int cooldownTicks = getCooldownTicks(player);
        return gameTime - lastUse < cooldownTicks;
    }

    /** 获取当前等级的冷却 tick 数 */
    private static int getCooldownTicks(Player player) {
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.FLYWHEEL_EFFECT.get(), boots);
        // 配置项：I/II/III 级冷却秒数 × 20（转 tick）
        // 默认：60/30/15 秒 → 1200/600/300 tick
        return switch (level) {
            case 1 -> Config.flywheelCooldownSeconds1 * 20;
            case 2 -> Config.flywheelCooldownSeconds2 * 20;
            case 3 -> Config.flywheelCooldownSeconds3 * 20;
            default -> 0;
        };
    }

    /** 根据等级获取冲刺距离 */
    private static double getDashDistance(int level) {
        return switch (level) {
            case 1 -> Config.flywheelDistance1;
            case 2 -> Config.flywheelDistance2;
            case 3 -> Config.flywheelDistance3;
            default -> 0;
        };
    }

    /** 记录上次使用时间到 PersistentData */
    private static void setLastUseTime(Player player, long time) {
        CompoundTag data = player.getPersistentData().getCompound(TAG_ROOT);
        data.putLong(KEY_LAST_USE, time);
        player.getPersistentData().put(TAG_ROOT, data);
    }

    /** 获取上次使用时间 */
    private static long getLastUseTime(Player player) {
        return player.getPersistentData()
                .getCompound(TAG_ROOT)
                .getLong(KEY_LAST_USE);
    }

    // ========================================================================
    // 冷却完毕通知
    //
    // 每 tick 检查：如果正好从"冷却中"变为"冷却结束"，则通知玩家
    // 方案：记录上次检查时的冷却状态，发现状态切换时触发通知
    // ========================================================================
    private static final Map<UUID, Boolean> PREVIOUS_COOLDOWN_STATE = new HashMap<>();

    private static void checkCooldownCompletion(ServerPlayer player) {
        UUID uuid = player.getUUID();

        // 检查是否有飞轮附魔（没有附魔就不需要检查冷却）
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.FLYWHEEL_EFFECT.get(), boots);
        if (level <= 0) {
            PREVIOUS_COOLDOWN_STATE.remove(uuid);
            return;
        }

        boolean currentlyOnCooldown = isOnCooldown(player);
        Boolean wasOnCooldown = PREVIOUS_COOLDOWN_STATE.get(uuid);

        if (wasOnCooldown != null && wasOnCooldown && !currentlyOnCooldown) {
            // 状态刚切换：从冷却中 → 冷却完毕
            // 弹出提示文本
            player.displayClientMessage(
                    Component.literal("飞轮效应已冷却完毕")
                            .withStyle(ChatFormatting.GREEN),
                    true // action bar
            );
            // 播放"叮"音效
            player.level().playSound(null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS,
                    1.0f, 2.0f);
        }

        // 更新上次状态
        PREVIOUS_COOLDOWN_STATE.put(uuid, currentlyOnCooldown);
    }
}