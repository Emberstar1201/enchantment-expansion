package com.github.emberstar1201.enchantmentex;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/** 末影龙强化逻辑，全部使用 Forge 事件实现。 */
public final class EnderDragonBuffHandler {

    private static final String ROAR_COOLDOWN = "enchantment_expansion_dragon_roar_cooldown";
    private static final String DRAGON_FIREBALL_COOLDOWN = "enchantment_expansion_dragon_fireball_cooldown";
    private static final String DRAGON_CHARGE_COOLDOWN = "enchantment_expansion_dragon_charge_cooldown";
    private static final String PENDING_EXPERIENCE = "enchantment_expansion_pending_experience";

    private EnderDragonBuffHandler() {
    }

    /** 世界级持久化标记：区分第一次击杀和重生后的击杀。 */
    private static final class DragonDefeatData extends SavedData {
        private boolean defeated;

        private static DragonDefeatData load(CompoundTag tag) {
            DragonDefeatData data = new DragonDefeatData();
            data.defeated = tag.getBoolean("defeated");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("defeated", defeated);
            return tag;
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!MobBuffConfig.enabled || !MobBuffConfig.enderDragonEnabled
                || event.getLevel().isClientSide()
                || !(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }

        AttributeInstance maxHealth = dragon.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(MobBuffConfig.enderDragonHealth);
        }
        AttributeInstance armor = dragon.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(MobBuffConfig.enderDragonArmor);
        }

        // 从存档加载时保留当前血量；新生成的龙才设置为满血。
        if (!event.loadedFromDisk()) {
            dragon.setHealth(dragon.getMaxHealth());
        } else if (dragon.getHealth() > dragon.getMaxHealth()) {
            dragon.setHealth(dragon.getMaxHealth());
        }
    }

    @SubscribeEvent
    public static void onDragonDamage(LivingDamageEvent event) {
        if (!MobBuffConfig.enabled || !MobBuffConfig.enderDragonEnabled
                || event.getEntity().level().isClientSide()
                || !(event.getSource().getEntity() instanceof EnderDragon dragon)) {
            return;
        }
        if (dragon.getHealth() <= dragon.getMaxHealth() * 0.5F) {
            event.setAmount(event.getAmount()
                    * (float) MobBuffConfig.enderDragonLowHealthDamageMultiplier);
        }
    }

    @SubscribeEvent
    public static void onDragonDeath(LivingDeathEvent event) {
        if (!MobBuffConfig.enabled || !MobBuffConfig.enderDragonEnabled
                || !(event.getEntity() instanceof EnderDragon dragon)
                || !(dragon.level() instanceof ServerLevel level)) {
            return;
        }
        DragonDefeatData defeatData = level.getDataStorage().computeIfAbsent(
                DragonDefeatData::load, DragonDefeatData::new, "enchantment_expansion_dragon_defeat");
        boolean respawnedDragon = defeatData.defeated;
        defeatData.defeated = true;
        defeatData.setDirty();
        int experience = respawnedDragon
                ? MobBuffConfig.enderDragonRespawnExperience
                : MobBuffConfig.enderDragonFirstExperience;
        if (experience > 0) {
            dragon.getPersistentData().putInt(PENDING_EXPERIENCE, experience);
        }
    }

    @SubscribeEvent
    public static void onDragonExperienceDrop(LivingExperienceDropEvent event) {
        if (MobBuffConfig.enabled && MobBuffConfig.enderDragonEnabled
                && event.getEntity() instanceof EnderDragon) {
            // 自定义经验在死亡动画结束阶段生成，清零原版掉落流程，避免重复。
            event.setDroppedExperience(0);
        }
    }

    /** 服务端每 tick 维护末影龙的战斗强化和声音冷却。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !MobBuffConfig.enabled || !MobBuffConfig.enderDragonEnabled) {
            return;
        }

        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension() != Level.END) {
                continue;
            }
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof EnderDragon dragon) {
                    tickDragonExperience(level, dragon);
                    tickDragonRoar(level, dragon);
                    tickDragonAttacks(level, dragon);
                }
            }
        }
    }

    private static void tickDragonExperience(ServerLevel level, EnderDragon dragon) {
        int experience = dragon.getPersistentData().getInt(PENDING_EXPERIENCE);
        if (experience <= 0 || dragon.dragonDeathTime < 150) {
            return;
        }

        ExperienceOrb.award(level, dragon.position(), experience);
        dragon.getPersistentData().remove(PENDING_EXPERIENCE);
    }

    private static void tickDragonRoar(ServerLevel level, EnderDragon dragon) {
        decrementCooldown(dragon, ROAR_COOLDOWN);
        if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.SITTING_FLAMING
                || dragon.getPersistentData().getInt(ROAR_COOLDOWN) > 0) {
            return;
        }

        dragon.getPersistentData().putInt(ROAR_COOLDOWN, 40);
        AABB area = dragon.getBoundingBox().inflate(16.0D, 8.0D, 16.0D);
        List<Player> players = level.getEntitiesOfClass(Player.class, area);
        for (Player player : players) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.WITHER,
                    MobBuffConfig.enderDragonRoarDamageSeconds * 20,
                    0,
                    false,
                    true,
                    true));
        }
    }

    private static void tickDragonAttacks(ServerLevel level, EnderDragon dragon) {
        decrementCooldown(dragon, DRAGON_FIREBALL_COOLDOWN);
        decrementCooldown(dragon, DRAGON_CHARGE_COOLDOWN);

        if (dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING) {
            return;
        }

        Player target = level.getNearestPlayer(dragon, 64.0D);
        if (target == null) {
            return;
        }

        if (dragon.getPersistentData().getInt(DRAGON_FIREBALL_COOLDOWN) <= 0) {
            Vec3 direction = target.getEyePosition()
                    .subtract(dragon.getEyePosition())
                    .normalize();
            DragonFireball fireball = new DragonFireball(
                    level,
                    dragon,
                    direction.x,
                    direction.y,
                    direction.z);
            fireball.setPos(dragon.getX(), dragon.getY() + 1.0D, dragon.getZ());
            level.addFreshEntity(fireball);
            dragon.getPersistentData().putInt(
                    DRAGON_FIREBALL_COOLDOWN,
                    getAttackCooldown(dragon, true));
        }

        if (dragon.getPersistentData().getInt(DRAGON_CHARGE_COOLDOWN) <= 0
                && dragon.getPhaseManager().getCurrentPhase().getPhase()
                != EnderDragonPhase.CHARGING_PLAYER) {
            dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
            dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER)
                    .setTarget(target.position());
            dragon.getPersistentData().putInt(
                    DRAGON_CHARGE_COOLDOWN,
                    getAttackCooldown(dragon, true) * 2);
        }
    }

    private static int getAttackCooldown(EnderDragon dragon, boolean aggressivePhase) {
        int base = MobBuffConfig.enderDragonAttackCooldown;
        boolean lowHealth = dragon.getHealth() <= dragon.getMaxHealth() * 0.5F;
        return Math.max(1, lowHealth && aggressivePhase ? base / 2 : base);
    }


    private static void decrementCooldown(Entity entity, String key) {
        int cooldown = entity.getPersistentData().getInt(key);
        if (cooldown > 0) {
            entity.getPersistentData().putInt(key, cooldown - 1);
        }
    }

}
