package com.github.emberstar1201.enchantmentex.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 少女附近的苦力怕与幻翼行为。
 *
 * <p>使用事件而不是修改原版实体 Goal，避免影响少女范围之外的正常怪物行为。</p>
 */
@Mod.EventBusSubscriber(modid = "enchantment_expansion", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GirlPredatorAvoidanceHandler {
    private static final double CREEPER_RANGE = 8.0D;
    private static final double PHANTOM_RANGE = 12.0D;

    private GirlPredatorAvoidanceHandler() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        if (entity instanceof Creeper creeper) {
            makeCreeperFlee(creeper);
        } else if (entity instanceof Phantom phantom) {
            makeDivingPhantomFlee(phantom);
        }
    }

    private static void makeCreeperFlee(Creeper creeper) {
        LivingEntity girl = findNearestGirl(creeper, CREEPER_RANGE);
        if (girl == null) {
            return;
        }

        creeper.setTarget(null);
        creeper.setSwellDir(-1);
        Vec3 away = creeper.position().subtract(girl.position());
        if (away.lengthSqr() < 1.0E-4D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        away = away.normalize();
        creeper.getNavigation().moveTo(
                creeper.getX() + away.x * 10.0D,
                creeper.getY(),
                creeper.getZ() + away.z * 10.0D,
                1.25D);
    }

    private static void makeDivingPhantomFlee(Phantom phantom) {
        if (phantom.getDeltaMovement().y >= -0.05D
                || findNearestGirl(phantom, PHANTOM_RANGE) == null) {
            return;
        }

        // 仅拦截正在下降的俯冲阶段；盘旋和远处飞行仍保持原版行为。
        phantom.setTarget(null);
        phantom.setDeltaMovement(phantom.getDeltaMovement().x * 0.5D, 0.65D,
                phantom.getDeltaMovement().z * 0.5D);
        phantom.getNavigation().stop();
    }

    private static LivingEntity findNearestGirl(Entity entity, double range) {
        AABB box = entity.getBoundingBox().inflate(range);
        return entity.level().getEntitiesOfClass(
                LivingEntity.class,
                box,
                girl -> girl.isAlive()
                        && (girl instanceof ZombieGirlEntity
                        || girl instanceof DrownedGirlEntity))
                .stream()
                .min((first, second) -> Double.compare(
                        entity.distanceToSqr(first), entity.distanceToSqr(second)))
                .orElse(null);
    }
}
