package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// 古·云来弓法 附魔事件处理器
// 处理两个核心效果：1. 缩短弓蓄力时间  2. 生成箭矢飞行轨迹粒子
//
// 【蓄力加速与"云来弓法"共存规则】：
//   由于古·云来弓法与基础版"云来弓法"互不冲突（用户要求可共存），
//   两个附魔会同时作用于蓄力。为避免两者进度覆盖互相干扰，
//   两个 Handler 各自使用独立的 PersistentData key 管理小数累积，
//   且分别调用 AncientYunLai/YunLaiArchery 各自的附魔等级查询，
//   累加效果即"两者倍率分别算出的 extraProgress 之和"。
@Mod.EventBusSubscriber(modid = MODID)
public class AncientYunLaiHandler {

    private static final double PARTICLE_SPACING = 0.1D;
    private static final Map<UUID, Vec3> ARROW_PARTICLE_POSITIONS = new HashMap<>();

    // ========================================================================
    // 【效果一】缩短弓的蓄力时间（加速拉弓）
    // 原理：在玩家"使用物品"的每tick（LivingEntityUseItemEvent.Tick），
    //       根据附魔等级跳过额外tick进度，等效于蓄力速度变快。
    // 例如：倍率1.5倍意味着，每现实1tick，游戏内的蓄力进度前进1.5tick。
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

        // 获取弓上"古·云来弓法"附魔的等级
        int enchantLevel = net.minecraft.world.item.enchantment.EnchantmentHelper
                .getItemEnchantmentLevel(ModEnchantments.ANCIENT_YUNLAI.get(), usingItem);
        if (enchantLevel <= 0) {
            return;
        }

        // 获取蓄力加速倍率（查表法，每个等级对应独立数值）
        double chargeMultiplier = AncientYunLaiEnchantment.getChargeSpeedMultiplier(enchantLevel);

        // 倍率 <= 1.0 时无需加速
        if (chargeMultiplier <= 1.0) {
            return;
        }

        // 【核心逻辑】：倍率减1即为需要"额外跳过"的进度
        // 例如倍率=1.5，则每次额外推进0.5tick的蓄力进度
        // 对整数部分直接累加多次，小数部分通过累积计数处理（防止精度丢失）
        double extraProgress = chargeMultiplier - 1.0;

        // 整数部分：每tick直接多次推进（常见倍率1.5倍以上才会有整数跳）
        int wholeSkips = (int) extraProgress;
        // 通过循环多次触发"推进使用物品"效果
        for (int i = 0; i < wholeSkips; i++) {
            // 直接修改 duration（剩余使用时长）：每循环减 1 表示多前进 1 tick 的蓄力
            int newDuration = event.getDuration() - 1;
            if (newDuration <= 0) {
                newDuration = 1; // 防止直接使用完成，交给原版判定
            }
            event.setDuration(newDuration);
        }

        // 小数部分：使用累积计数器（利用玩家PersistentData存储累积值）
        // 例如0.3倍小数，则每tick累积0.3，到>=1.0时额外多跳1tick
        float fractional = (float) (extraProgress - wholeSkips);
        if (fractional > 0) {
            // 【关键】：独立key，与"云来弓法基础版" YunLaiArcheryHandler 的累积器互不干扰
            String accumKey = "AncientYunLai_FractionalAccum_" + usingItem.getDescriptionId();
            float accumulated = player.getPersistentData().getFloat(accumKey);
            accumulated += fractional;
            if (accumulated >= 1.0f) {
                // 累积满1 tick，额外跳1格进度
                int newDuration = event.getDuration() - 1;
                if (newDuration <= 0) {
                    newDuration = 1;
                }
                event.setDuration(newDuration);
                accumulated -= 1.0f; // 减去消耗的1.0，保留余数继续累积
            }
            player.getPersistentData().putFloat(accumKey, accumulated);
        }
    }

    // ========================================================================
    // 【效果二】生成箭矢飞行轨迹粒子
    // 原理：在带有古·云来弓法的箭矢加入服务端世界时记录初始位置，
    //       后续每 tick 在箭矢上一位置与当前位置之间插值生成粒子。
    // ========================================================================
    @SubscribeEvent
    public static void onArrowJoinWorld(EntityJoinLevelEvent event) {
        // 仅处理箭矢类实体（包含普通箭、光谱箭、药箭等所有AbstractArrow子类）
        if (!(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }

        // 获取箭矢的发射者（owner），必须是玩家
        if (!(arrow.getOwner() instanceof Player player)) {
            return;
        }

        // 获取玩家主手使用的弓（即发射这支箭时用的弓）
        // 注意：玩家在箭发射后的短暂时间内主手仍持有弓，此事件在服务端和客户端都会触发
        ItemStack bow = player.getMainHandItem();
        if (!(bow.getItem() instanceof BowItem)) {
            // 如果主手不是弓，尝试检查玩家"使用中"的物品（某些极端情况）
            bow = player.getUseItem();
            if (!(bow.getItem() instanceof BowItem)) {
                return;
            }
        }

        // 获取"古·云来弓法"附魔等级
        int enchantLevel = net.minecraft.world.item.enchantment.EnchantmentHelper
                .getItemEnchantmentLevel(ModEnchantments.ANCIENT_YUNLAI.get(), bow);
        if (enchantLevel <= 0) {
            return;
        }

        if (arrow.level().isClientSide()) {
            return;
        }

        // 保留既有的高速箭效果；粒子插值只修复视觉拖尾，不改变速度数值。
        double flightMultiplier = AncientYunLaiEnchantment
                .getFlightSpeedMultiplier(enchantLevel);
        if (flightMultiplier > 1.0) {
            arrow.setDeltaMovement(arrow.getDeltaMovement().scale(flightMultiplier));
            arrow.setBaseDamage(arrow.getBaseDamage() * flightMultiplier);
        }

        ARROW_PARTICLE_POSITIONS.put(arrow.getUUID(), arrow.position());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Iterator<Map.Entry<UUID, Vec3>> iterator = ARROW_PARTICLE_POSITIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Vec3> entry = iterator.next();
            AbstractArrow arrow = findArrow(event.getServer().getAllLevels(), entry.getKey());
            if (arrow == null || !arrow.isAlive()) {
                iterator.remove();
                continue;
            }

            Vec3 previousPosition = entry.getValue();
            Vec3 currentPosition = arrow.position();
            Vec3 movement = currentPosition.subtract(previousPosition);
            int particleCount = Math.max(1, (int) Math.ceil(movement.length() / PARTICLE_SPACING));
            ServerLevel level = (ServerLevel) arrow.level();
            for (int i = 0; i <= particleCount; i++) {
                Vec3 particlePosition = previousPosition.lerp(currentPosition, (double) i / particleCount);
                level.sendParticles(ParticleTypes.END_ROD,
                        particlePosition.x, particlePosition.y, particlePosition.z,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
            entry.setValue(currentPosition);
        }
    }

    private static AbstractArrow findArrow(Iterable<ServerLevel> levels, UUID arrowId) {
        for (ServerLevel level : levels) {
            if (level.getEntity(arrowId) instanceof AbstractArrow arrow) {
                return arrow;
            }
        }
        return null;
    }

    // ========================================================================
    // 辅助方法：获取玩家身上"古·云来弓法"附魔等级（支持主手和副手，虽然副手弓几乎不会用）
    // 目前只在主手生效，因为弓的使用必须在主手
    // ========================================================================
    private static int getAncientYunLaiLevel(LivingEntity entity) {
        ItemStack mainHand = entity.getMainHandItem();
        return net.minecraft.world.item.enchantment.EnchantmentHelper
                .getItemEnchantmentLevel(ModEnchantments.ANCIENT_YUNLAI.get(), mainHand);
    }
}
