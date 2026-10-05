package com.github.emberstar1201.enchantmentex.entity;

import com.github.emberstar1201.enchantmentex.sound.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;

/**
 * 溺尸娘：保留 Drowned 原生水陆移动、游泳、上浮和靠岸行为的独立变种。
 * 目标过滤只允许水中的敌对亡灵，避免攻击玩家和其它溺尸娘。
 */
public class DrownedGirlEntity extends Drowned {
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(DrownedGirlEntity.class, EntityDataSerializers.INT);

    private static final ResourceLocation[] TEXTURES = {
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_1.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_2.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_3.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_4.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_5.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_6.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_7.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_8.png")
    };

    public DrownedGirlEntity(EntityType<? extends Drowned> type, Level level) {
        super(type, level);
    }

    /** 药水效果不再按亡灵反转：治疗药水正常回血，伤害药水正常受伤。 */
    @Override
    public boolean isInvertedHealAndHarm() {
        return false;
    }

    // ====================================================================
    // 语音：溺尸娘与丧尸娘共用一套 husk_girl 音频（取代原版溺尸音效，
    // 不再区分陆地 / 水下版本；在水中播放时声音引擎会自动加上闷音效果）。
    // ====================================================================
    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.HUSK_GIRL_IDLE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.HUSK_GIRL_HUST.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.HUSK_GIRL_FAREWELL.get();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_VARIANT, 0);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                         MobSpawnType reason, @Nullable SpawnGroupData spawnData,
                                         @Nullable CompoundTag dataTag) {
        this.entityData.set(DATA_VARIANT, this.random.nextInt(TEXTURES.length));
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    public ResourceLocation getVariantTexture() {
        int variant = this.entityData.get(DATA_VARIANT);
        return TEXTURES[Math.max(0, Math.min(TEXTURES.length - 1, variant))];
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", this.entityData.get(DATA_VARIANT));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_VARIANT, Math.max(0, Math.min(TEXTURES.length - 1,
                tag.getInt("Variant"))));
    }

    /** 溺尸娘保留亡灵逻辑，但不会因为白天而燃烧。 */
    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    /** 刷怪蛋生成的溺尸娘在和平难度下也必须保留。 */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    protected void addBehaviourGoals() {
        // 先保留 Drowned 的水陆移动、游泳上浮、靠岸和攻击 goal。
        super.addBehaviourGoals();
        // 移除原版针对玩家、村民、铁傀儡等目标，避免友好亡灵误伤无关生物。
        this.targetSelector.getAvailableGoals().removeIf(goal -> goal.getGoal() instanceof HurtByTargetGoal
                || goal.getGoal() instanceof NearestAttackableTargetGoal);
        this.targetSelector.addGoal(1, new FriendlyUndeadHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, Mob.class, 10, true, false, DrownedGirlEntity::isWaterHostileUndead));
    }

    @Override
    public boolean okTarget(@Nullable LivingEntity target) {
        return isWaterHostileUndead(target);
    }

    private static boolean isWaterHostileUndead(@Nullable LivingEntity target) {
        return target instanceof Enemy
                && target.getMobType() == MobType.UNDEAD
                && target.isInWater()
                && !(target instanceof DrownedGirlEntity)
                && !(target instanceof ZombieGirlEntity);
    }

    private static final class FriendlyUndeadHurtByTargetGoal extends HurtByTargetGoal {
        private final DrownedGirlEntity drownedGirl;

        private FriendlyUndeadHurtByTargetGoal(DrownedGirlEntity drownedGirl) {
            super(drownedGirl);
            this.drownedGirl = drownedGirl;
        }

        @Override
        public boolean canUse() {
            LivingEntity attacker = this.drownedGirl.getLastHurtByMob();
            return super.canUse() && isWaterHostileUndead(attacker);
        }
    }
}
