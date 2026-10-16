package com.github.emberstar1201.enchantmentex;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;

import com.github.emberstar1201.enchantmentex.item.ModItems;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
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
    private static final String VULNERABLE_TICKS = "enchantment_expansion_dragon_vulnerable_ticks";
    private static final String BREATH_CLOUD_COOLDOWN = "enchantment_expansion_breath_cloud_cooldown";
    private static final String THRESHOLD_TRIGGERED = "enchantment_expansion_dragon_threshold_triggered";
    private static final String PEARL_FIREBALL_COOLDOWN = "enchantment_expansion_dragon_pearl_fireball_cooldown";
    private static final int DRAGON_FIREBALL_INTERVAL = 200; // 土球固定约 10 秒发射一次
    private static final int PEARL_FIREBALL_INTERVAL = 40; // 末影珍珠触发后，龙最多每 2 秒追踪一次
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
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }

        boolean enhanced = MobBuffRuntime.isEnabled() && MobBuffConfig.enderDragonEnabled;
        AttributeInstance maxHealth = dragon.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(enhanced ? MobBuffConfig.enderDragonHealth : 200.0D);
        }
        if (!enhanced) {
            if (!event.loadedFromDisk()) {
                dragon.setHealth(dragon.getMaxHealth());
            } else if (dragon.getHealth() > dragon.getMaxHealth()) {
                dragon.setHealth(dragon.getMaxHealth());
            }
            return;
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
        if (!MobBuffRuntime.isEnabled() || !MobBuffConfig.enderDragonEnabled
                || event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
                || event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            return;
        }
        // 只统计末影龙附近实际存在的水晶；世界边界包围盒会导致扫描范围异常，无法可靠反映场上水晶数量。
        // 水晶只要在末地当前世界边界内就生效，不能限制在龙身边 64 格。
        var border = dragon.level().getWorldBorder();
        AABB endWorldArea = new AABB(
                border.getMinX(), -3.0E7D, border.getMinZ(),
                border.getMaxX(), 3.0E7D, border.getMaxZ());
        int crystals = dragon.level().getEntitiesOfClass(
                EndCrystal.class,
                endWorldArea,
                Entity::isAlive).size();
        crystals = Math.min(crystals, 8);
        if (crystals > 0) {
            // 护罩相位：水晶存在时完全弹开弹射物，迫使玩家先摧毁水晶或用近战输出。
            if (MobBuffConfig.enderDragonCrystalShieldEnabled
                    && event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
                event.setAmount(0.0F);
                return;
            }
            event.setAmount(event.getAmount() * (1.0F - crystals * 0.1F));
        }
        // 虚弱窗口：水晶被摧毁后的一段时间内，龙受到的伤害放大。
        if (dragon.getPersistentData().getInt(VULNERABLE_TICKS) > 0) {
            event.setAmount(event.getAmount()
                    * (float) MobBuffConfig.enderDragonVulnerabilityDamageMultiplier);
        }
        if (dragon.getHealth() <= dragon.getMaxHealth() * 0.5F) {
            event.setAmount(event.getAmount()
                    * (float) MobBuffConfig.enderDragonLowHealthDamageMultiplier);
        }
    }

    /** 末影珍珠落点事件：玩家完成末影珍珠传送后，末影龙向该玩家发射追踪火球。 */
    @SubscribeEvent
    public static void onEnderPearlImpact(ProjectileImpactEvent event) {
        if (!MobBuffRuntime.isEnabled() || !MobBuffConfig.enderDragonEnabled
                || !(event.getProjectile() instanceof ThrownEnderpearl pearl)
                || !(pearl.level() instanceof ServerLevel level)
                || level.dimension() != Level.END
                || !(pearl.getOwner() instanceof Player player)
                || player.isCreative()) {
            return;
        }
        for (EnderDragon dragon : level.getEntitiesOfClass(
                EnderDragon.class,
                player.getBoundingBox().inflate(64.0D),
                dragon -> dragon.isAlive()
                        && dragon.getPersistentData().getInt(PEARL_FIREBALL_COOLDOWN) <= 0)) {
            // 命中事件可能早于原版玩家传送，因此使用珍珠落点作为追踪目标，避免火球射向旧位置。
            Vec3 direction = pearl.position()
                    .add(0.0D, player.getBbHeight() * 0.5D, 0.0D)
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
            dragon.getPersistentData().putInt(PEARL_FIREBALL_COOLDOWN, PEARL_FIREBALL_INTERVAL);
        }
    }

    /** 末影水晶离开世界（通常是被摧毁）时，让末地的龙进入虚弱窗口。 */
    @SubscribeEvent
    public static void onCrystalRemoved(EntityLeaveLevelEvent event) {
        if (!MobBuffRuntime.isEnabled() || !MobBuffConfig.enderDragonEnabled
                || !MobBuffConfig.enderDragonCrystalShieldEnabled
                || !(event.getEntity() instanceof EndCrystal)
                || !(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != Level.END) {
            return;
        }
        var border = level.getWorldBorder();
        AABB endWorldArea = new AABB(
                border.getMinX(), -3.0E7D, border.getMinZ(),
                border.getMaxX(), 3.0E7D, border.getMaxZ());
        for (EnderDragon dragon : level.getEntitiesOfClass(EnderDragon.class, endWorldArea)) {
            dragon.getPersistentData().putInt(VULNERABLE_TICKS,
                    MobBuffConfig.enderDragonVulnerabilitySeconds * 20);
        }
    }

    @SubscribeEvent
    public static void onDragonDeath(LivingDeathEvent event) {
        if (!MobBuffRuntime.isEnabled() || !MobBuffConfig.enderDragonEnabled
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
        if (MobBuffRuntime.isEnabled() && MobBuffConfig.enderDragonEnabled
                && event.getEntity() instanceof EnderDragon) {
            // 自定义经验在死亡动画结束阶段生成，清零原版掉落流程，避免重复。
            event.setDroppedExperience(0);
        }
    }

    /** 服务端每 tick 维护末影龙的战斗强化和声音冷却。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        boolean enhanced = MobBuffRuntime.isEnabled() && MobBuffConfig.enderDragonEnabled;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension() != Level.END) {
                continue;
            }
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof EnderDragon dragon)) {
                    continue;
                }
                syncDragonHealth(dragon, enhanced);
                if (enhanced) {
                    tickDragonExperience(level, dragon);
                    tickDragonVulnerability(level, dragon);
                    tickDragonRoar(level, dragon);
                    tickBreathCloud(level, dragon);
                    tickDragonRecovery(level, dragon);
                    tickDragonThreshold(level, dragon);
                    tickDragonAttacks(level, dragon);
                    decrementCooldown(dragon, PEARL_FIREBALL_COOLDOWN);
                }
            }
        }
    }

    private static void syncDragonHealth(EnderDragon dragon, boolean enhanced) {
        AttributeInstance maxHealth = dragon.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        double targetHealth = enhanced ? MobBuffConfig.enderDragonHealth : 200.0D;
        if (Math.abs(maxHealth.getBaseValue() - targetHealth) > 0.001D) {
            maxHealth.setBaseValue(targetHealth);
        }
        if (dragon.getHealth() > dragon.getMaxHealth()) {
            dragon.setHealth(dragon.getMaxHealth());
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

    /** 虚弱窗口计时：持续期间环绕暴击粒子提示玩家抓紧输出。 */
    private static void tickDragonVulnerability(ServerLevel level, EnderDragon dragon) {
        int vulnerable = dragon.getPersistentData().getInt(VULNERABLE_TICKS);
        if (vulnerable <= 0) {
            return;
        }
        dragon.getPersistentData().putInt(VULNERABLE_TICKS, vulnerable - 1);
        if (vulnerable % 5 == 0) {
            level.sendParticles(ParticleTypes.CRIT,
                    dragon.getX(), dragon.getY() + dragon.getBbHeight() * 0.5D, dragon.getZ(),
                    8, dragon.getBbWidth() * 0.5D, 1.0D, dragon.getBbWidth() * 0.5D, 0.05D);
        }
    }

    /** 龙息领域：停靠祭坛喷吐期间，定期在祭坛地面留下持续伤害的龙息云雾。 */
    private static void tickBreathCloud(ServerLevel level, EnderDragon dragon) {
        decrementCooldown(dragon, BREATH_CLOUD_COOLDOWN);
        if (!MobBuffConfig.enderDragonBreathCloudEnabled
                || dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.SITTING_FLAMING
                || dragon.getPersistentData().getInt(BREATH_CLOUD_COOLDOWN) > 0) {
            return;
        }
        dragon.getPersistentData().putInt(BREATH_CLOUD_COOLDOWN,
                MobBuffConfig.enderDragonBreathCloudInterval);
        AreaEffectCloud cloud = new AreaEffectCloud(level,
                dragon.getX(), dragon.getY() - 2.0D, dragon.getZ());
        cloud.setOwner(dragon);
        cloud.setParticle(ParticleTypes.DRAGON_BREATH);
        cloud.setRadius((float) MobBuffConfig.enderDragonBreathCloudRadius);
        cloud.setDuration(MobBuffConfig.enderDragonBreathCloudDurationSeconds * 20);
        cloud.setWaitTime(10);
        cloud.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 0));
        level.addFreshEntity(cloud);
    }

    private static void tickDragonRecovery(ServerLevel level, EnderDragon dragon) {
        if (dragon.tickCount % 40 == 0 && dragon.getHealth() < dragon.getMaxHealth()) {
            dragon.heal((float) MobBuffConfig.enderDragonHealAmount);
        }
    }

    private static void tickDragonThreshold(ServerLevel level, EnderDragon dragon) {
        if (dragon.getPersistentData().getBoolean(THRESHOLD_TRIGGERED)
                || dragon.getHealth() > dragon.getMaxHealth() * MobBuffConfig.enderDragonSummonThreshold) {
            return;
        }
        dragon.getPersistentData().putBoolean(THRESHOLD_TRIGGERED, true);
        if (MobBuffConfig.enderDragonSummonEndermen) {
            for (int i = 0; i < 2; i++) {
                EnderMan enderman = EntityType.ENDERMAN.create(level);
                if (enderman != null) {
                    enderman.moveTo(dragon.getX() + level.random.nextInt(9) - 4,
                            dragon.getY(), dragon.getZ() + level.random.nextInt(9) - 4,
                            level.random.nextFloat() * 360.0F, 0.0F);
                    enderman.getPersistentData().putBoolean("enchantment_expansion_dragon_minion", true);
                    level.addFreshEntity(enderman);
                }
            }
        }
        if (MobBuffConfig.enderDragonRespawnCrystal) {
            EndCrystal crystal = EntityType.END_CRYSTAL.create(level);
            if (crystal != null) {
                crystal.moveTo(dragon.getX(), 80.0D, dragon.getZ(), 0.0F, 0.0F);
                level.addFreshEntity(crystal);
            }
        }
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

        Player target = level.getEntitiesOfClass(
                        Player.class,
                        dragon.getBoundingBox().inflate(64.0D),
                        player -> !player.isCreative() && !isEndStarNeutral(player))
                .stream()
                .min((first, second) -> Double.compare(
                        dragon.distanceToSqr(first), dragon.distanceToSqr(second)))
                .orElse(null);
        if (target == null) {
            return;
        }

        if (dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.SITTING_FLAMING
                || dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.SITTING_SCANNING) {
            // 祭坛停靠期间交给原版阶段 AI，避免自定义攻击打断停靠。
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
                    DRAGON_FIREBALL_INTERVAL);
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

    private static boolean isEndStarNeutral(Player player) {
        if (player.getMainHandItem().is(ModItems.END_STAR.get())
                || player.getOffhandItem().is(ModItems.END_STAR.get())) {
            return true;
        }
        for (ItemStack armorPiece : player.getArmorSlots()) {
            if (armorPiece.hasTag()
                    && "end_star".equals(armorPiece.getTag().getString("EmbeddedStar"))) {
                return true;
            }
        }
        return false;
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
