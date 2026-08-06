package com.example.examplemod.enchantment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

import static com.example.examplemod.ExampleMod.MODID;

// ========================================================================
// 【风踏涟漪】附魔事件处理器
//
// 核心机制：
//   1. 移速加成使用"固定UUID + AttributeModifier"，每tick检测状态变化时
//      才移除旧modifier再添加新的，避免每tick叠加导致数值爆炸。
//   2. 水上行走：检测玩家脚部正下方一格是否为水（水源或流动水），
//      若是则将玩家"钉"在水面位置（Y坐标取整+1），并设置onGround=true，
//      重置fallDistance防止摔落伤害。
//   3. 潜行时水上行走失效，玩家正常下沉；所有移速加成同时失效。
//   4. 飞行/骑乘/完全沉入水中（头部也在水里）时不触发任何加成。
//   5. 粒子效果：水面行走生成滴水粒子，陆地行走生成尘/风粒子。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class WindRippleHandler {

    // ========================================================================
    // 固定UUID：用于标识本附魔添加的移速Modifier
    // 用固定UUID才能每次精准找到并移除旧的modifier，避免叠加
    // 【注意】UUID 必须是合法十六进制字符：仅允许 0-9 和 a-f（不区分大小写）
    //         格式为 8-4-4-4-12 共 32 位十六进制数，由 UUID.randomUUID() 生成
    // ========================================================================
    private static final UUID WIND_RIPPLE_SPEED_UUID =
            UUID.fromString("a1b2c3d4-5678-4a9e-8b0f-1c2d3e4f5a6b");

    // 移速加成倍率
    private static final double LAND_SPEED_MULTIPLIER = 1.5;   // 陆地 1.5 倍
    private static final double WATER_SPEED_MULTIPLIER = 1.25; // 水面 1.25 倍

    // 定义三种移动状态，便于状态切换时管理modifier
    private enum MovementState {
        NONE,       // 无加成（潜行/飞行/骑乘/沉没等）
        LAND_BOOST, // 陆地加速
        WATER_WALK  // 水面行走 + 加速
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
        int enchantLevel = net.minecraft.world.item.enchantment.EnchantmentHelper
                .getItemEnchantmentLevel(ModEnchantments.WIND_RIPPLE.get(), boots);
        if (enchantLevel <= 0) {
            // 没有附魔：如果之前加过modifier，要移除掉并重置状态
            removeSpeedModifier(player);
            player.getPersistentData().putInt(PREV_STATE_KEY, MovementState.NONE.ordinal());
            return;
        }

        // 2. 特殊状态下完全不生效：飞行 / 骑乘 / 创造飞行
        if (player.isFallFlying()     // 鞘翅飞行
                || player.isPassenger() // 骑乘（马、船、矿车等）
                || player.getAbilities().flying) { // 创造/旁观者飞行
            applyState(player, MovementState.NONE, level);
            return;
        }

        // 3. 潜行状态：所有加成失效（水上行走也失效，玩家正常下沉）
        if (player.isShiftKeyDown()) {
            applyState(player, MovementState.NONE, level);
            return;
        }

        // ---------- 核心：判断当前是陆地还是水面 ----------

        boolean isWaterWalking = isStandingOnWater(player, level);
        boolean isSubmerged = player.isInWater() && !isWaterWalking; // 完全沉入水中（非水面行走状态）

        // 完全沉入水中（头部以下在水里）：不触发加成（用户明确要求）
        if (isSubmerged) {
            applyState(player, MovementState.NONE, level);
            return;
        }

        if (isWaterWalking) {
            // ---------- 水面行走模式 ----------
            applyState(player, MovementState.WATER_WALK, level);

            // 核心：将玩家"钉"在水面上（视觉上踩在水面方块表面）
            // Y坐标 = 脚下水方块Y + 1（水方块表面），减去玩家脚部与实体中心的偏移
            BlockPos feetPos = player.blockPosition();
            // 修正玩家位置：如果脚部正下方是水，则把Y轴对齐到水面
            double waterSurfaceY = feetPos.getY() + 1.0;
            double playerFeetY = player.getY(); // player.getY() 即实体脚底Y
            // 如果玩家低于水面，就把他抬起来；如果刚好在水面上，则维持
            if (playerFeetY < waterSurfaceY - 0.001) {
                // 设置运动向量Y为微正数（防止下沉）
                Vec3 motion = player.getDeltaMovement();
                player.setDeltaMovement(motion.x, Math.max(motion.y, 0.01), motion.z);
            }
            // 强制在地面上：防止跳跃/下落判定
            player.setOnGround(true);
            // 重置摔落距离：水面行走不会摔落
            player.fallDistance = 0.0f;

            // 水面行走粒子效果：脚底生成滴水粒子（水花）
            spawnWaterParticles(player, level);
        } else {
            // ---------- 陆地加速模式 ----------
            // 仅在玩家实际站在地面上时生效（不是跳跃/悬空状态）
            if (player.onGround()) {
                applyState(player, MovementState.LAND_BOOST, level);
                // 陆地行走粒子：脚底生成扬尘/风粒子
                spawnLandParticles(player, level);
            } else {
                // 空中：不改变Y轴，但也不加移速加成
                applyState(player, MovementState.NONE, level);
            }
        }
    }

    // ========================================================================
    // 状态切换核心方法：对比上一tick状态，有变化才修改AttributeModifier
    // 避免每tick addPermanentModifier() 导致Modifier叠加数值爆炸
    // ========================================================================
    private static void applyState(Player player, MovementState newState, Level level) {
        int prevStateOrdinal = player.getPersistentData().getInt(PREV_STATE_KEY);
        MovementState prevState = MovementState.values()[prevStateOrdinal];

        // 状态未变：无需重复操作（但水面模式仍需在onPlayerTick中维持位置，那里单独处理）
        if (prevState == newState) {
            return;
        }

        // 状态有变：先移除旧modifier，再按新状态决定是否添加新的
        removeSpeedModifier(player);

        switch (newState) {
            case LAND_BOOST -> addSpeedModifier(player, LAND_SPEED_MULTIPLIER);
            case WATER_WALK -> addSpeedModifier(player, WATER_SPEED_MULTIPLIER);
            case NONE -> { /* 不添加任何modifier */ }
        }

        // 保存当前状态供下一tick对比
        player.getPersistentData().putInt(PREV_STATE_KEY, newState.ordinal());
    }

    // ========================================================================
    // 添加移速Modifier
    // 使用 MULTIPLY_BASE 模式：最终速度 = 基础速度 * 倍率
    // 与原版其他移速效果（如速度药水MULTIPLY_TOTAL）叠加方式更合理
    // ========================================================================
    private static void addSpeedModifier(Player player, double multiplier) {
        // 倍率从 1.5 转换为 +0.5（因为 MULTIPLY_BASE 是 1 + amount）
        // 即 modifier amount = multiplier - 1
        double amount = multiplier - 1.0;

        AttributeModifier modifier = new AttributeModifier(
                WIND_RIPPLE_SPEED_UUID,
                "WindRipple speed boost",
                amount,
                AttributeModifier.Operation.MULTIPLY_BASE
        );

        // 先确认没有同名UUID的modifier（理论上applyState已移除，但保险起见）
        if (player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(WIND_RIPPLE_SPEED_UUID) == null) {
            player.getAttribute(Attributes.MOVEMENT_SPEED).addPermanentModifier(modifier);
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
    // 判断玩家是否"站在水面上"
    // 判定条件：
    //   1. 脚部所在方块（player.blockPosition()）不是水（玩家实体不在水方块内）
    //   2. 脚部正下方一格（blockPosition().below()）是水方块（水源或流动水）
    // 这样玩家视觉上正好踩在水方块的表面
    // ========================================================================
    private static boolean isStandingOnWater(Player player, Level level) {
        BlockPos feetBlockPos = player.blockPosition();
        BlockPos belowFeet = feetBlockPos.below();
        BlockState belowState = level.getBlockState(belowFeet);

        // 脚下是水方块（水源或流动水）
        boolean belowIsWater = belowState.is(Blocks.WATER);

        // 脚部所在位置不是水（否则玩家已经"在水中"而不是"站在水面"）
        BlockState feetState = level.getBlockState(feetBlockPos);
        boolean feetNotInWater = !feetState.is(Blocks.WATER);

        // 同时要求玩家Y坐标不高于水面太多（避免跳在空中时也算水面行走）
        // player.getY() 是脚底，水方块表面是 belowFeet.getY() + 1
        double surfaceY = belowFeet.getY() + 1.0;
        double playerFeetY = player.getY();
        boolean closeToSurface = (playerFeetY >= surfaceY - 0.3) && (playerFeetY <= surfaceY + 0.1);

        return belowIsWater && feetNotInWater && closeToSurface;
    }

    // ========================================================================
    // 水面行走粒子：脚底生成水花粒子（原版 Dripstone Drip Water）
    // 仅在玩家有水平移动时生成，且每3tick生成一次避免粒子过多
    // ========================================================================
    private static void spawnWaterParticles(Player player, Level level) {
        // 非客户端不发送粒子（粒子只需要在客户端显示）
        if (level.isClientSide()) {
            // 每 3 tick 生成一次，降低粒子密度
            if (level.getGameTime() % 3 != 0) return;

            // 必须有水平移动才生成水花（站着不动不生成）
            Vec3 motion = player.getDeltaMovement();
            double horizontalSpeedSqr = motion.x * motion.x + motion.z * motion.z;
            if (horizontalSpeedSqr < 0.01) return;

            // 在玩家脚底位置随机撒水滴粒子
            double px = player.getX();
            double py = player.getY(); // 脚底Y
            double pz = player.getZ();

            // 一次生成 2~3 个粒子
            for (int i = 0; i < 2; i++) {
                double offsetX = (level.random.nextDouble() - 0.5) * 0.4;
                double offsetZ = (level.random.nextDouble() - 0.5) * 0.4;
                // 原版滴水粒子：DRIPPING_WATER -> FALLING_WATER -> SPLASH
                // 这里直接用 FALLING_WATER 视觉更像水花溅起
                level.addParticle(
                        ParticleTypes.FALLING_WATER,
                        px + offsetX, py + 0.05, pz + offsetZ,
                        0, 0.05, 0
                );
            }
        }
    }

    // ========================================================================
    // 陆地行走粒子：脚底生成"风/尘埃"效果
    // 使用底部方块的 BlockParticleOption（即踩在什么方块上就扬什么方块的尘）
    // 如果下方方块无法生成尘粒子，则退化为 HAPPY_VILLAGER 简化版（小风点）
    // ========================================================================
    private static void spawnLandParticles(Player player, Level level) {
        if (level.isClientSide()) {
            // 每 2 tick 生成一次（扬灰稍微频繁更有疾风感）
            if (level.getGameTime() % 2 != 0) return;

            // 必须有水平移动才生成
            Vec3 motion = player.getDeltaMovement();
            double horizontalSpeedSqr = motion.x * motion.x + motion.z * motion.z;
            if (horizontalSpeedSqr < 0.015) return;

            double px = player.getX();
            double py = player.getY();
            double pz = player.getZ();

            // 脚下方块类型（用于生成同色扬尘）
            BlockState groundState = level.getBlockState(player.blockPosition().below());

            // 尝试用 BlockParticle（看起来就是踩起的方块碎屑）
            ParticleOptions particle;
            try {
                particle = new BlockParticleOption(ParticleTypes.BLOCK, groundState);
            } catch (Exception e) {
                // 某些方块（空气/液体）无法转为BlockParticle，用备用粒子
                particle = ParticleTypes.POOF; // 小风团
            }

            // 生成 1~2 个粒子，位置在脚底靠后（模拟拖尾）
            for (int i = 0; i < 2; i++) {
                // 位置稍微在玩家"后方"，即 motion 的反方向，形成风拖尾
                double speedMag = Math.sqrt(horizontalSpeedSqr);
                if (speedMag < 0.001) speedMag = 0.001;
                double backX = -(motion.x / speedMag) * 0.2;
                double backZ = -(motion.z / speedMag) * 0.2;

                double randX = (level.random.nextDouble() - 0.5) * 0.3;
                double randZ = (level.random.nextDouble() - 0.5) * 0.3;

                level.addParticle(
                        particle,
                        px + backX + randX, py + 0.01, pz + backZ + randZ,
                        // 速度：略微向上+向外扩散
                        (level.random.nextDouble() - 0.5) * 0.1,
                        level.random.nextDouble() * 0.05 + 0.02,
                        (level.random.nextDouble() - 0.5) * 0.1
                );
            }
        }
    }
}
