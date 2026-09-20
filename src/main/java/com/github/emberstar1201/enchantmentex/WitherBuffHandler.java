package com.github.emberstar1201.enchantmentex;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/** 凋零强化逻辑：属性、伤害、母弹分裂、死亡爆炸和经验。 */
public final class WitherBuffHandler {
    private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("a1b2c3d4-1111-4a01-9f01-000000000005");
    private static final String HEALTH_MODIFIER_NAME = "enchantment_expansion:wither_health";
    private static final String MOTHER_TAG = "EEMotherWitherSkull";
    private static final String CHILD_TAG = "EEWitherSkullChild";
    private static final String DEATH_HANDLED_TAG = "EEWitherDeathHandled";
    private static final String FLIGHT_TICKS_TAG = "EEWitherFlightTicks";

    private WitherBuffHandler() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!enabled() || event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof WitherBoss wither) {
            AttributeInstance health = wither.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) {
                health.removeModifier(HEALTH_MODIFIER_ID);
                double delta = MobBuffConfig.witherHealth - health.getBaseValue();
                if (Math.abs(delta) > 0.001D) {
                    health.addPermanentModifier(new AttributeModifier(
                            HEALTH_MODIFIER_ID, HEALTH_MODIFIER_NAME, delta, AttributeModifier.Operation.ADDITION));
                }
                if (!event.loadedFromDisk()) {
                    wither.setHealth(wither.getMaxHealth());
                } else if (wither.getHealth() > wither.getMaxHealth()) {
                    wither.setHealth(wither.getMaxHealth());
                }
            }
            AttributeInstance armor = wither.getAttribute(Attributes.ARMOR);
            if (armor != null) {
                armor.setBaseValue(MobBuffConfig.witherArmor);
            }
        } else if (event.getEntity() instanceof WitherSkull skull
                && skull.getOwner() instanceof WitherBoss
                && !skull.getPersistentData().getBoolean(CHILD_TAG)) {
            // 原版头颅没有自定义实体类型，使用 PersistentData 标记母弹，避免重复注册实体。
            skull.getPersistentData().putBoolean(MOTHER_TAG, true);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!enabled()) {
            return;
        }
        if (event.getEntity() instanceof WitherBoss wither
                && wither.getHealth() <= wither.getMaxHealth() * 0.5F
                && !event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) {
            event.setAmount(event.getAmount() * (float) (1.0D - MobBuffConfig.witherLowHealthDamageReduction));
        }
        if (event.getSource().getEntity() instanceof WitherSkull) {
            event.setAmount(event.getAmount() * (float) MobBuffConfig.witherSkullDamageMultiplier);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !enabled() || !MobBuffConfig.witherMotherSkullEnabled) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof WitherSkull skull)
                        || !skull.getPersistentData().getBoolean(MOTHER_TAG)
                        || skull.getPersistentData().getBoolean(CHILD_TAG)) {
                    continue;
                }
                int ticks = skull.getPersistentData().getInt(FLIGHT_TICKS_TAG) + 1;
                skull.getPersistentData().putInt(FLIGHT_TICKS_TAG, ticks);
                if (ticks < MobBuffConfig.witherMotherSkullFlightTicks) {
                    continue;
                }
                splitSkull(level, skull);
            }
        }
    }

    private static void splitSkull(ServerLevel level, WitherSkull mother) {
        mother.getPersistentData().putBoolean(CHILD_TAG, true);
        Entity owner = mother.getOwner();
        if (!(owner instanceof WitherBoss wither)) {
            return;
        }
        Vec3 direction = mother.getDeltaMovement();
        if (direction.lengthSqr() < 0.0001D) {
            direction = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            direction = direction.normalize();
        }
        int count = Math.max(2, Math.min(3, MobBuffConfig.witherMotherSkullSplitCount));
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0D * i) / count;
            Vec3 childDirection = direction.yRot((float) angle).normalize();
            WitherSkull child = new WitherSkull(level, wither,
                    childDirection.x, childDirection.y, childDirection.z);
            child.setPos(mother.position().add(childDirection.scale(0.6D)));
            child.setDeltaMovement(childDirection.scale(0.6D));
            child.getPersistentData().putBoolean(CHILD_TAG, true);
            level.addFreshEntity(child);
        }
        mother.discard();
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!enabled() || !(event.getEntity() instanceof WitherBoss wither)
                || !(wither.level() instanceof ServerLevel level)
                || wither.getPersistentData().getBoolean(DEATH_HANDLED_TAG)) {
            return;
        }
        wither.getPersistentData().putBoolean(DEATH_HANDLED_TAG, true);
        if (MobBuffConfig.witherDeathExplosionPower > 0.0D) {
            // NONE 保留伤害和视觉效果，但不破坏方块，也不会把掉落物炸散。
            level.explode(wither, wither.getX(), wither.getY(), wither.getZ(),
                    (float) MobBuffConfig.witherDeathExplosionPower, Level.ExplosionInteraction.NONE);
        }
        awardExperience(level, wither.position(), MobBuffConfig.witherExperience);
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (enabled() && event.getExplosion().getExploder() instanceof WitherBoss wither
                && wither.getPersistentData().getBoolean(DEATH_HANDLED_TAG)) {
            // 原版死亡爆炸紧随死亡事件触发，清空方块列表，防止与自定义 NONE 爆炸叠加破坏世界。
            event.getAffectedBlocks().clear();
            event.getAffectedEntities().removeIf(entity -> entity instanceof ItemEntity);
        }
    }

    @SubscribeEvent
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (enabled() && event.getEntity() instanceof WitherBoss) {
            // 死亡事件已经生成指定经验，清零原版流程，避免重复掉落。
            event.setDroppedExperience(0);
        }
    }

    private static void awardExperience(ServerLevel level, Vec3 position, int amount) {
        if (amount > 0) {
            ExperienceOrb.award(level, position, amount);
        }
    }

    private static boolean enabled() {
        return MobBuffConfig.enabled && MobBuffConfig.witherEnabled;
    }
}
