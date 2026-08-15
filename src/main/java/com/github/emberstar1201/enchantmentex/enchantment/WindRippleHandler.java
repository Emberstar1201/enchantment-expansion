package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【风踏涟漪】附魔事件处理器
//
// 核心机制：
//   1. 移速加成使用"固定UUID + AttributeModifier"，每tick检测状态变化时
//      才移除旧modifier再添加新的，避免每tick叠加导致数值爆炸。
//   2. 水上行走：检测玩家脚部正下方一格是否为水（水源或流动水），
//      若是则将玩家"钉"在水面位置（Y坐标取整+1），并设置onGround=true，
//      重置fallDistance防止摔落伤害。
//   3. 岩浆行走（新增）：检测玩家脚部下方是否为岩浆，若是则将玩家保持
//      在岩浆表面，并每tick清除火焰（setRemainingFireTicks=0），实现
//      完全火焰免疫。
//   4. 潜行修复：移除"潜行时所有加成失效"的逻辑，潜行时水上行走、
//      岩浆行走、移速加成全部正常生效（玩家可通过跳跃离开水面/岩浆）。
//   5. 跳跃加速修复：空中跳跃时不再切换为NONE状态，陆地加速效果在
//      跳跃全程持续保持。
//   6. 飞行/骑乘/完全沉入水中（头部也在水里）时不触发任何加成。
//   7. 粒子效果：水面行走→滴水粒子，岩浆行走→岩浆粒子，陆地行走→扬尘粒子。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class WindRippleHandler {

    // ========================================================================
    // 固定UUID：用于标识本附魔添加的移速Modifier
    // 用固定UUID才能每次精准找到并移除旧的modifier，避免叠加
    // ========================================================================
    private static final UUID WIND_RIPPLE_SPEED_UUID =
            UUID.fromString("a1b2c3d4-5678-4a9e-8b0f-1c2d3e4f5a6b");

    // 移速加成倍率
    private static final double LAND_SPEED_MULTIPLIER = 1.5;   // 陆地 1.5 倍
    private static final double WATER_SPEED_MULTIPLIER = 1.25; // 水面 1.25 倍
    // 岩浆行走移速从 Config 读取（默认 1.15）

    // 定义移动状态，便于状态切换时管理modifier
    private enum MovementState {
        NONE,           // 无加成（飞行/骑乘/沉没等）
        LAND_BOOST,     // 陆地加速（含跳跃中）
        WATER_WALK,     // 水面行走 + 加速
        LAVA_WALK       // 岩浆行走 + 加速 + 火焰免疫
    }

    // 记录上一tick的状态，用于检测切换（避免每tick重复修改属性）
    private static final String PREV_STATE_KEY = "WindRipple_PrevState";

    // ========================================================================
    // 主逻辑：PlayerTickEvent.PlayerTickEvent
    // 每玩家每tick触发一次，服务端+客户端都会触发
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 仅在 Phase.END 处理（原版实体tick完成后），避免与原版逻辑冲突
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;
        Level level = player.level();

        // ---------- 前置条件检查 ----------

        // 1. 检查靴子上是否有风踏涟漪附魔
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        int enchantLevel = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.WIND_RIPPLE.get(), boots);
        if (enchantLevel <= 0) {
            // 没有附魔：如果之前加过modifier，要移除掉并重置状态
            removeSpeedModifier(player);
            player.getPersistentData().putInt(PREV_STATE_KEY, MovementState.NONE.ordinal());
            return;
        }

        // 2. 特殊状态下完全不生效：鞘翅飞行 / 骑乘 / 创造飞行
        if (player.isFallFlying()
                || player.isPassenger()
                || player.getAbilities().flying) {
            applyState(player, MovementState.NONE, level);
            return;
        }

        // ================================================================
        // 【Bug修复】移除了"潜行时所有加成失效"的早期返回逻辑
        // 现在潜行状态下，水上行走、岩浆行走、移速加成全部正常生效
        // 玩家如果需要下沉，只需跳出水面/岩浆区域即可
        // ================================================================

        // ---------- 第一阶段：岩浆行走检测（优先级最高） ----------

        if (isStandingOnLava(player, level)) {
            // 应用岩浆行走状态（含移速加成）
            applyState(player, MovementState.LAVA_WALK, level);

            // 将玩家固定在岩浆表面（跳跃时不拉回）
            Double surfaceY = getFluidSurfaceY(player, level, Fluids.LAVA, Blocks.LAVA);
            if (surfaceY != null) {
                alignToSurface(player, surfaceY);
            }

            // ================================================================
            // 【火焰免疫】每 tick 清除火焰计时器
            // 玩家站在岩浆表面时完全不受灼烧伤害
            // ================================================================
            player.setRemainingFireTicks(0);

            // 强制标记为在地面上（支持疾跑、跳跃等原版逻辑）
            player.setOnGround(true);
            // 重置摔落距离
            player.fallDistance = 0.0f;

            // 生成岩浆行走粒子
            spawnLavaParticles(player, level);

            // 岩浆行走处理完毕后直接返回，不再处理水和陆地逻辑
            return;
        }

        // ---------- 第二阶段：水面行走检测 ----------

        boolean isWaterWalking = isStandingOnWater(player, level);
        // 完全沉入水中（头部以下在水里，且不是水面行走状态）
        boolean isSubmerged = player.isInWater() && !isWaterWalking;

        if (isSubmerged) {
            // 完全沉入水中：不触发加成
            applyState(player, MovementState.NONE, level);
            return;
        }

        if (isWaterWalking) {
            // ---------- 水面行走模式 ----------
            applyState(player, MovementState.WATER_WALK, level);

            // 将玩家固定在水面位置
            Double surfaceY = getFluidSurfaceY(player, level, Fluids.WATER, Blocks.WATER);
            if (surfaceY != null) {
                alignToSurface(player, surfaceY);
            }

            // 标记在地面上 + 重置摔落距离
            player.setOnGround(true);
            player.fallDistance = 0.0f;

            // 水面行走粒子
            spawnWaterParticles(player, level);
            return;
        }

        // ---------- 第三阶段：陆地加速模式 ----------

        // ================================================================
        // 【Bug修复】跳跃时加速效果保持
        // 原逻辑：player.onGround() 为 false 时切换到 NONE → 移速加成消失
        // 新逻辑：无论是否在地面，只要未触发其他特殊状态就保持 LAND_BOOST
        // 这样玩家跳跃全过程都能享受加速效果
        // ================================================================
        applyState(player, MovementState.LAND_BOOST, level);

        // 仅在地面时生成扬尘粒子（空中不生成）
        if (player.onGround()) {
            spawnLandParticles(player, level);
        }
    }

    // ========================================================================
    // 状态切换核心方法：对比上一tick状态，有变化才修改AttributeModifier
    // 避免每tick addPermanentModifier() 导致Modifier叠加数值爆炸
    // ========================================================================
    private static void applyState(Player player, MovementState newState, Level level) {
        int prevStateOrdinal = player.getPersistentData().getInt(PREV_STATE_KEY);
        MovementState prevState = MovementState.values()[prevStateOrdinal];

        // 状态未变：无需重复操作
        if (prevState == newState) {
            return;
        }

        // 状态有变：先移除旧modifier，再按新状态决定是否添加新的
        removeSpeedModifier(player);

        switch (newState) {
            case LAND_BOOST -> addSpeedModifier(player, LAND_SPEED_MULTIPLIER);
            case WATER_WALK -> addSpeedModifier(player, WATER_SPEED_MULTIPLIER);
            case LAVA_WALK -> addSpeedModifier(player, getLavaSpeedMultiplier());
            case NONE -> { /* 不添加任何modifier */ }
        }

        // 保存当前状态供下一tick对比
        player.getPersistentData().putInt(PREV_STATE_KEY, newState.ordinal());
    }

    // ========================================================================
    // 获取岩浆行走移速倍率（优先从Config读取，未加载时使用默认值）
    // ========================================================================
    private static double getLavaSpeedMultiplier() {
        return Config.windRippleLavaSpeed > 0.0
                ? Config.windRippleLavaSpeed
                : 1.15; // 默认 1.15 倍（岩浆黏稠，比水面慢一点）
    }

    // ========================================================================
    // 添加移速Modifier
    // 使用 MULTIPLY_BASE 模式：最终速度 = 基础速度 * 倍率
    // ========================================================================
    private static void addSpeedModifier(Player player, double multiplier) {
        double amount = multiplier - 1.0;

        AttributeModifier modifier = new AttributeModifier(
                WIND_RIPPLE_SPEED_UUID,
                "WindRipple speed boost",
                amount,
                AttributeModifier.Operation.MULTIPLY_BASE
        );

        // 确认没有同名UUID的modifier（理论上applyState已移除，但保险起见）
        if (player.getAttribute(Attributes.MOVEMENT_SPEED)
                .getModifier(WIND_RIPPLE_SPEED_UUID) == null) {
            player.getAttribute(Attributes.MOVEMENT_SPEED)
                    .addPermanentModifier(modifier);
        }
    }

    // ========================================================================
    // 安全移除风踏涟漪添加的移速Modifier（通过固定UUID查找）
    // ========================================================================
    private static void removeSpeedModifier(Player player) {
        var attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null && attr.getModifier(WIND_RIPPLE_SPEED_UUID) != null) {
            attr.removeModifier(WIND_RIPPLE_SPEED_UUID);
        }
    }

    // ========================================================================
    // 【通用】将玩家对齐到流体表面
    //
    // 行为：
    //   - 如果玩家正在跳起（motion.y > 0），不强制对齐位置，允许自由跳跃
    //   - 否则将玩家Y坐标对齐到流体表面，并归零Y轴速度抵消重力
    // ========================================================================
    private static void alignToSurface(Player player, double surfaceY) {
        double playerFeetY = player.getY();
        double dy = surfaceY - playerFeetY;
        Vec3 motion = player.getDeltaMovement();

        // 玩家主动上升（跳跃/弹起）时不硬拉回表面
        boolean isJumpingUp = motion.y > 0;

        if (!isJumpingUp) {
            // 位置偏差超过 0.001 时才强制对齐
            if (Math.abs(dy) > 0.001) {
                player.setPos(player.getX(), surfaceY, player.getZ());
            }
            // Y轴速度归零：抵消重力，消除"下沉→拉起→再下沉"的拉锯
            player.setDeltaMovement(motion.x, 0.0, motion.z);
        }
    }

    // ========================================================================
    // 【通用】获取玩家应该站立的流体表面 Y 坐标
    //
    // 两种识别模式：
    //   模式 A（标准）：脚下方块含指定流体 → 表面 = belowFeet.y + 1.0
    //   模式 B（穿过表面）：脚部在流体方块内，上方是空气
    //     → 高处下落一帧穿过流体表面时由本模式接住
    // ========================================================================
    private static Double getFluidSurfaceY(Player player, Level level,
                                           Fluid fluidType, Block fluidBlock) {
        BlockPos feetPos = player.blockPosition();
        BlockPos belowPos = feetPos.below();
        BlockState belowState = level.getBlockState(belowPos);
        BlockState feetState = level.getBlockState(feetPos);

        // 模式 A：脚下方块含指定流体（纯流体方块或含流体的方块）
        boolean belowHasFluid = belowState.getFluidState().is(fluidType)
                || belowState.is(fluidBlock);
        if (belowHasFluid) {
            return belowPos.getY() + 1.0;
        }

        // 模式 B：玩家穿过流体表面（脚部在流体方块内，且上方是空气）
        // 【关键限制】上方必须是空气 → 多格深的流体中不会误判
        boolean feetInFluid = feetState.getFluidState().is(fluidType)
                || feetState.is(fluidBlock);
        if (feetInFluid) {
            BlockState aboveState = level.getBlockState(feetPos.above());
            if (aboveState.isAir()) {
                return feetPos.getY() + 1.0;
            }
        }

        return null;
    }

    // ========================================================================
    // 判断玩家是否站在水上（委托给通用流体检测）
    // ========================================================================
    private static boolean isStandingOnWater(Player player, Level level) {
        return isStandingOnFluid(player, level, Fluids.WATER, Blocks.WATER);
    }

    // ========================================================================
    // 判断玩家是否站在岩浆上
    // ========================================================================
    private static boolean isStandingOnLava(Player player, Level level) {
        return isStandingOnFluid(player, level, Fluids.LAVA, Blocks.LAVA);
    }

    // ========================================================================
    // 【通用】判断玩家是否站在指定流体表面
    //
    // 容差设计：
    //   上限 +0.5：玩家正在下落接近表面（提前 0.5 格触发托举）
    //   下限 -1.5：玩家已穿过表面但在流体方块上半部分（仍可托起）
    // ========================================================================
    private static boolean isStandingOnFluid(Player player, Level level,
                                             Fluid fluidType, Block fluidBlock) {
        Double surfaceYOpt = getFluidSurfaceY(player, level, fluidType, fluidBlock);
        if (surfaceYOpt == null) {
            return false;
        }
        double surfaceY = surfaceYOpt;
        double playerFeetY = player.getY();

        // 容差：玩家脚底距流体表面 -1.5 到 +0.5 格
        return playerFeetY >= surfaceY - 1.5 && playerFeetY <= surfaceY + 0.5;
    }

    // ========================================================================
    // 岩浆行走粒子：脚底生成岩浆飞溅粒子（Lava Pop）
    // 仅在玩家有水平移动时生成，且每3tick生成一次
    // ========================================================================
    private static void spawnLavaParticles(Player player, Level level) {
        if (level.isClientSide()) {
            if (level.getGameTime() % 3 != 0) return;

            Vec3 motion = player.getDeltaMovement();
            double hSpeedSqr = motion.x * motion.x + motion.z * motion.z;
            if (hSpeedSqr < 0.01) return;

            double px = player.getX();
            double py = player.getY();
            double pz = player.getZ();

            // 生成 2~3 个岩浆粒子
            for (int i = 0; i < 2; i++) {
                double ox = (level.random.nextDouble() - 0.5) * 0.4;
                double oz = (level.random.nextDouble() - 0.5) * 0.4;
                level.addParticle(
                        ParticleTypes.LAVA,     // 橙色岩浆泡泡粒子
                        px + ox, py + 0.05, pz + oz,
                        0, 0.05, 0
                );
            }
        }
    }

    // ========================================================================
    // 水面行走粒子：脚底生成水花粒子（原版 Falling Water）
    // ========================================================================
    private static void spawnWaterParticles(Player player, Level level) {
        if (level.isClientSide()) {
            if (level.getGameTime() % 3 != 0) return;

            Vec3 motion = player.getDeltaMovement();
            double hSpeedSqr = motion.x * motion.x + motion.z * motion.z;
            if (hSpeedSqr < 0.01) return;

            double px = player.getX();
            double py = player.getY();
            double pz = player.getZ();

            for (int i = 0; i < 2; i++) {
                double ox = (level.random.nextDouble() - 0.5) * 0.4;
                double oz = (level.random.nextDouble() - 0.5) * 0.4;
                level.addParticle(
                        ParticleTypes.FALLING_WATER,
                        px + ox, py + 0.05, pz + oz,
                        0, 0.05, 0
                );
            }
        }
    }

    // ========================================================================
    // 陆地行走粒子：脚底生成"风/尘埃"效果
    // ========================================================================
    private static void spawnLandParticles(Player player, Level level) {
        if (level.isClientSide()) {
            if (level.getGameTime() % 2 != 0) return;

            Vec3 motion = player.getDeltaMovement();
            double hSpeedSqr = motion.x * motion.x + motion.z * motion.z;
            if (hSpeedSqr < 0.015) return;

            double px = player.getX();
            double py = player.getY();
            double pz = player.getZ();

            BlockState groundState = level.getBlockState(player.blockPosition().below());

            ParticleOptions particle;
            try {
                particle = new BlockParticleOption(ParticleTypes.BLOCK, groundState);
            } catch (Exception e) {
                particle = ParticleTypes.POOF;
            }

            for (int i = 0; i < 2; i++) {
                double speedMag = Math.sqrt(hSpeedSqr);
                if (speedMag < 0.001) speedMag = 0.001;
                double backX = -(motion.x / speedMag) * 0.2;
                double backZ = -(motion.z / speedMag) * 0.2;

                double randX = (level.random.nextDouble() - 0.5) * 0.3;
                double randZ = (level.random.nextDouble() - 0.5) * 0.3;

                level.addParticle(
                        particle,
                        px + backX + randX, py + 0.01, pz + backZ + randZ,
                        (level.random.nextDouble() - 0.5) * 0.1,
                        level.random.nextDouble() * 0.05 + 0.02,
                        (level.random.nextDouble() - 0.5) * 0.1
                );
            }
        }
    }
}