package com.github.emberstar1201.enchantmentex;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
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
    private static final String SHIELD_TICKS_TAG = "EEWitherShieldTicks";
    private static final String SHIELD_CYCLE_TICKS_TAG = "EEWitherShieldCycleTicks";
    private static final String CURSE_TICKS_TAG = "EEWitherCurseTicks";
    private static final String MINION_COOLDOWN_TAG = "EEWitherMinionCooldown";
    private static final String BARRAGE_COOLDOWN_TAG = "EEWitherBarrageCooldown";
    private static final String SMITE_SUPPRESSION_TICKS_TAG = "EEWitherSmiteSuppressionTicks";
    private static final String MINION_SACRIFICE_TAG = "EEWitherMinionSacrifice";
    private static final int MINION_SACRIFICE_DELAY = 60;

    private WitherBuffHandler() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof WitherBoss wither) {
            boolean enhanced = enabled();
            AttributeInstance health = wither.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) {
                health.removeModifier(HEALTH_MODIFIER_ID);
                double targetHealth = enhanced ? MobBuffConfig.witherHealth : 300.0D;
                double delta = targetHealth - health.getBaseValue();
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
            if (!enhanced) {
                return;
            }
            if (MobBuffConfig.witherShieldCycleEnabled
                    && (!event.loadedFromDisk() || !wither.getPersistentData().contains(SHIELD_TICKS_TAG))) {
                // 护盾轮换：开局直接处于护盾期，之后按周期间隔循环。
                wither.getPersistentData().putInt(SHIELD_TICKS_TAG, MobBuffConfig.witherShieldCycleDuration);
                wither.getPersistentData().putInt(SHIELD_CYCLE_TICKS_TAG, MobBuffConfig.witherShieldCycleInterval);
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
        if (event.getEntity() instanceof WitherBoss wither) {
            if (event.getSource().getEntity() instanceof Player player
                    && player.getMainHandItem().getEnchantmentLevel(Enchantments.SMITE) > 0) {
                wither.getPersistentData().putInt(SMITE_SUPPRESSION_TICKS_TAG, 40);
            }
            boolean bypassesReduction = event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
                    || event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD);
            boolean shielded = MobBuffConfig.witherShieldCycleEnabled
                    && wither.getPersistentData().getInt(SHIELD_TICKS_TAG) > 0;
            if (!bypassesReduction && shielded) {
                // 护盾期：弹射物被完全弹开，其他伤害减半；非护盾期才是完整输出窗口。
                if (event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
                    event.setCanceled(true);
                    return;
                }
                event.setAmount(event.getAmount() * 0.5F);
            }
            if (!bypassesReduction && wither.getHealth() <= wither.getMaxHealth() * 0.5F) {
                event.setAmount(event.getAmount() * (float) (1.0D - MobBuffConfig.witherLowHealthDamageReduction));
            }
        }
        // 弹射物伤害源：getEntity() 返回射击者，getDirectEntity() 才是头颅本体。
        if (event.getSource().getDirectEntity() instanceof WitherSkull) {
            event.setAmount(event.getAmount() * (float) MobBuffConfig.witherSkullDamageMultiplier);
            // 凋零诅咒：被凋零头颅击中的玩家治疗受阻，带诅咒死亡会生成凋零骷髅。
            if (MobBuffConfig.witherCurseEnabled
                    && event.getEntity() instanceof Player player
                    && !player.level().isClientSide()) {
                player.getPersistentData().putInt(CURSE_TICKS_TAG, MobBuffConfig.witherCurseSeconds * 20);
                player.displayClientMessage(Component.translatable(
                        "message.enchantment_expansion.wither_curse"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        boolean enhanced = enabled();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof WitherBoss wither) {
                    syncWitherHealth(wither, enhanced);
                    if (enhanced) {
                        tickWitherShield(level, wither);
                        tickWitherRecovery(wither);
                        tickWitherMinions(level, wither);
                        tickWitherBarrage(level, wither);
                    }
                } else if (enhanced && entity instanceof Player player) {
                    tickWitherCurse(level, player);
                } else if (enhanced && entity instanceof WitherSkeleton skeleton
                        && skeleton.getPersistentData().getBoolean(MINION_SACRIFICE_TAG)) {
                    tickWitherMinionSacrifice(level, skeleton);
                }
            }
        }
        if (!enhanced || !MobBuffConfig.witherMotherSkullEnabled) {
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

    private static void tickWitherRecovery(WitherBoss wither) {
        if (wither.tickCount % 40 == 0 && wither.getHealth() < wither.getMaxHealth()
                && wither.getPersistentData().getInt(SMITE_SUPPRESSION_TICKS_TAG) <= 0) {
            wither.heal((float) MobBuffConfig.witherHealAmount);
        }
        int suppression = wither.getPersistentData().getInt(SMITE_SUPPRESSION_TICKS_TAG);
        if (suppression > 0) {
            wither.getPersistentData().putInt(SMITE_SUPPRESSION_TICKS_TAG, suppression - 1);
        }
    }

    private static void tickWitherMinions(ServerLevel level, WitherBoss wither) {
        int cooldown = Math.max(0, wither.getPersistentData().getInt(MINION_COOLDOWN_TAG) - 1);
        wither.getPersistentData().putInt(MINION_COOLDOWN_TAG, cooldown);
        if (wither.getHealth() > wither.getMaxHealth() * 0.5F || cooldown > 0) {
            return;
        }
        WitherSkeleton minion = EntityType.WITHER_SKELETON.create(level);
        if (minion != null) {
            minion.moveTo(wither.getX() + 2.0D, wither.getY(), wither.getZ(), 0.0F, 0.0F);
            minion.getPersistentData().putBoolean(MINION_SACRIFICE_TAG, true);
            minion.getPersistentData().putInt(MINION_COOLDOWN_TAG, MINION_SACRIFICE_DELAY);
            minion.setTarget(null);
            level.addFreshEntity(minion);
            wither.getPersistentData().putInt(
                    MINION_COOLDOWN_TAG,
                    MobBuffConfig.witherMinionInterval
            );
        }
    }

    /** 仆从延迟献祭：避免生成瞬间回血，并让凋零真正消耗这只仆从。 */
    private static void tickWitherMinionSacrifice(ServerLevel level, WitherSkeleton minion) {
        int delay = Math.max(0,
                minion.getPersistentData().getInt(MINION_COOLDOWN_TAG) - 1);
        minion.getPersistentData().putInt(MINION_COOLDOWN_TAG, delay);
        if (delay > 0) {
            return;
        }
        WitherBoss wither = level.getEntitiesOfClass(
                        WitherBoss.class,
                        minion.getBoundingBox().inflate(16.0D))
                .stream()
                .filter(Entity::isAlive)
                .min((first, second) -> Double.compare(
                        minion.distanceToSqr(first), minion.distanceToSqr(second)))
                .orElse(null);
        if (wither != null && wither.getHealth() < wither.getMaxHealth()) {
            wither.heal(8.0F);
        }
        level.sendParticles(ParticleTypes.SOUL,
                minion.getX(), minion.getY() + 1.0D, minion.getZ(),
                8, 0.4D, 0.7D, 0.4D, 0.02D);
        minion.discard();
    }

    private static void tickWitherBarrage(ServerLevel level, WitherBoss wither) {
        int cooldown = Math.max(0, wither.getPersistentData().getInt(BARRAGE_COOLDOWN_TAG) - 1);
        wither.getPersistentData().putInt(BARRAGE_COOLDOWN_TAG, cooldown);
        if (wither.getHealth() > wither.getMaxHealth() * 0.5F || cooldown > 0) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            double angle = i * Math.PI * 2.0D / 3.0D;
            WitherSkull skull = new WitherSkull(level, wither, Math.cos(angle), 0.1D, Math.sin(angle));
            skull.setPos(wither.getX(), wither.getY() + 1.0D, wither.getZ());
            level.addFreshEntity(skull);
        }
        wither.getPersistentData().putInt(BARRAGE_COOLDOWN_TAG, MobBuffConfig.witherBarrageInterval);
    }

    /** 护盾轮换：护盾期倒计时，结束后等待周期间隔，再次进入护盾期。 */
    private static void tickWitherShield(ServerLevel level, WitherBoss wither) {
        if (!MobBuffConfig.witherShieldCycleEnabled) {
            return;
        }
        int shield = wither.getPersistentData().getInt(SHIELD_TICKS_TAG);
        if (shield > 0) {
            wither.getPersistentData().putInt(SHIELD_TICKS_TAG, shield - 1);
            // 护盾期粒子提示：末地烛光点环绕，提示远程攻击无效。
            if (shield % 5 == 0) {
                level.sendParticles(ParticleTypes.END_ROD,
                        wither.getX(), wither.getY() + 1.2D, wither.getZ(),
                        6, 1.0D, 0.8D, 1.0D, 0.02D);
            }
            return;
        }
        int cycle = wither.getPersistentData().getInt(SHIELD_CYCLE_TICKS_TAG) - 1;
        if (cycle <= 0) {
            wither.getPersistentData().putInt(SHIELD_TICKS_TAG, MobBuffConfig.witherShieldCycleDuration);
            cycle = MobBuffConfig.witherShieldCycleInterval;
        }
        wither.getPersistentData().putInt(SHIELD_CYCLE_TICKS_TAG, cycle);
    }

    /** 凋零诅咒计时：持续期间散发灵魂粒子提示。 */
    private static void tickWitherCurse(ServerLevel level, Player player) {
        int curse = player.getPersistentData().getInt(CURSE_TICKS_TAG);
        if (curse <= 0) {
            return;
        }
        player.getPersistentData().putInt(CURSE_TICKS_TAG, curse - 1);
        if (curse % 10 == 0) {
            level.sendParticles(ParticleTypes.SOUL,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    2, 0.3D, 0.5D, 0.3D, 0.01D);
        }
    }

    /** 诅咒期间治疗效果按比例降低。 */
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (enabled() && MobBuffConfig.witherCurseEnabled
                && event.getEntity().getPersistentData().getInt(CURSE_TICKS_TAG) > 0) {
            event.setAmount(event.getAmount() * (float) (1.0D - MobBuffConfig.witherCurseHealingReduction));
        }
    }

    /** 带着凋零诅咒死亡的玩家，在原地留下一只凋零骷髅。 */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!enabled() || !MobBuffConfig.witherCurseEnabled
                || !(event.getEntity() instanceof Player player)
                || !(player.level() instanceof ServerLevel level)
                || player.getPersistentData().getInt(CURSE_TICKS_TAG) <= 0) {
            return;
        }
        player.getPersistentData().remove(CURSE_TICKS_TAG);
        WitherSkeleton skeleton = EntityType.WITHER_SKELETON.create(level);
        if (skeleton != null) {
            skeleton.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
            level.addFreshEntity(skeleton);
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

    private static void syncWitherHealth(WitherBoss wither, boolean enhanced) {
        AttributeInstance health = wither.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) {
            return;
        }
        double targetHealth = enhanced ? MobBuffConfig.witherHealth : 300.0D;
        double currentHealth = health.getValue();
        if (Math.abs(currentHealth - targetHealth) > 0.001D) {
            health.removeModifier(HEALTH_MODIFIER_ID);
            double delta = targetHealth - health.getBaseValue();
            if (Math.abs(delta) > 0.001D) {
                health.addPermanentModifier(new AttributeModifier(
                        HEALTH_MODIFIER_ID, HEALTH_MODIFIER_NAME, delta, AttributeModifier.Operation.ADDITION));
            }
        }
        if (wither.getHealth() > wither.getMaxHealth()) {
            wither.setHealth(wither.getMaxHealth());
        }
    }

    private static boolean enabled() {
        return MobBuffRuntime.isEnabled() && MobBuffConfig.witherEnabled;
    }
}
