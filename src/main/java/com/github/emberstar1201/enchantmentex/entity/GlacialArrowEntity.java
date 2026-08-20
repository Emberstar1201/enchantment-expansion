package com.github.emberstar1201.enchantmentex.entity;

import com.github.emberstar1201.enchantmentex.Config;
import com.mojang.logging.LogUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;

// ========================================================================
// 琉璃冰魄箭实体（简化版：取消二段蓄力，统一行为）
//
// 【功能】
//   1. 飞行时冰晶粒子拖尾
//   2. 命中后 AOE 范围伤害
//   3. 母箭命中后牵引周围敌人
//   4. 母箭射出时立即生成子箭（扇形散射）
//   5. 母箭穿透多个敌人
//
// 【数据同步】
//   子箭标识 isSubArrow 仅服务端使用，不须同步
// ========================================================================
public class GlacialArrowEntity extends AbstractArrow {

    private static final Logger LOGGER = LogUtils.getLogger();

    // 子箭标识（仅服务端使用，子箭不再生成子箭，不须同步到客户端）
    private boolean isSubArrow = false;

    // ========================================================================
    // 母箭穿透相关字段（仅服务端）
    // ========================================================================
    private int maxPierceCount = 3;              // 最大穿透次数（配置值，0=不穿透）
    private int currentPierceCount = 0;          // 已穿透次数
    private final Set<UUID> piercedEntityIds = new HashSet<>();  // 已穿透实体UUID

    // ========================================================================
    // 构造方法
    // ========================================================================

    // Forge 实体反序列化必须的无参构造
    public GlacialArrowEntity(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
    }

    // 自定义构造：从发射者位置创建
    public GlacialArrowEntity(Level level, LivingEntity owner, double posX, double posY, double posZ) {
        super(ModEntities.GLACIAL_ARROW.get(), owner, level);
        this.setPos(posX, posY, posZ);
    }

    // ========================================================================
    // 读写 NBT（实体存档时保留子箭标识和穿透数据）
    // ========================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IsSubArrow", this.isSubArrow);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("IsSubArrow")) {
            this.isSubArrow = tag.getBoolean("IsSubArrow");
        }
    }

    // ========================================================================
    // Getter/Setter
    // ========================================================================
    public void setSubArrow(boolean subArrow) {
        this.isSubArrow = subArrow;
    }

    public boolean isSubArrow() {
        return this.isSubArrow;
    }

    public void setMaxPierceCount(int count) {
        this.maxPierceCount = count;
    }

    // ========================================================================
    // 每 tick 更新：冰晶粒子拖尾
    // ========================================================================
    @Override
    public void tick() {
        super.tick();

        // 仅在客户端生成粒子
        if (!this.level().isClientSide()) return;

        int interval = Config.glacialArrowParticleInterval;

        // 按间隔生成冰晶粒子拖尾
        if (this.tickCount % interval == 0) {
            Vec3 pos = this.position();
            // 在箭矢后方生成粒子（拖尾效果）
            Vec3 backward = this.getDeltaMovement().normalize().scale(-0.3);
            double px = pos.x + backward.x;
            double py = pos.y + backward.y;
            double pz = pos.z + backward.z;

            // 冰晶粒子拖尾（SNOWFLAKE + 少量 END_ROD 发光粒子）
            this.level().addParticle(ParticleTypes.SNOWFLAKE, px, py, pz, 0, 0, 0);
            if (this.random.nextFloat() < 0.3f) {
                this.level().addParticle(ParticleTypes.END_ROD, px, py, pz, 0, 0, 0);
            }
        }
    }

    // ========================================================================
    // 命中处理
    // ========================================================================

    // 命中方块
    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!this.level().isClientSide()) {
            applyHitEffects(result.getLocation());
        }
        super.onHitBlock(result);
    }

    // 命中实体
    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (this.level().isClientSide()) return;

        Entity hitEntity = result.getEntity();
        if (!(hitEntity instanceof LivingEntity)) {
            this.discard();
            return;
        }

        // 防止重复穿透同一实体（多段穿透时避免反复伤害）
        if (piercedEntityIds.contains(hitEntity.getUUID())) return;
        piercedEntityIds.add(hitEntity.getUUID());

        // 播放命中音效
        this.playSound(SoundEvents.ARROW_HIT, 1.0F,
                1.2F / (this.random.nextFloat() * 0.2F + 0.9F));

        if (isSubArrow) {
            // 子箭：先应用AOE伤害 + 牵引 + 粒子，再调用父类（基础伤害 + 销毁）
            applyHitEffects(result.getLocation());
            super.onHitEntity(result);
            return;
        }

        // ====== 母箭：AOE + 牵引 + 穿透逻辑 ======
        applyHitEffects(result.getLocation());

        currentPierceCount++;
        if (currentPierceCount >= maxPierceCount) {
            this.discard();
        }
        // 否则继续飞行，穿透下一个敌人
    }

    // ========================================================================
    // 核心命中效果：AOE 范围伤害 + 牵引敌人 + 粒子特效
    // ========================================================================
    private void applyHitEffects(Vec3 hitLocation) {
        if (isSubArrow) {
            // 子箭：使用子箭专用配置（较小范围、较低伤害）
            applyAoeDamage(hitLocation,
                    Config.glacialArrowSubAoeRange,
                    Config.glacialArrowSubDamage);
            // 子箭小范围牵引：范围取子箭AOE范围的一半，强度为母箭的一半
            applyPullEffect(hitLocation,
                    Config.glacialArrowSubAoeRange * 0.5,
                    Config.glacialArrowPullStrength * 0.5);
        } else {
            // 母箭：使用母箭配置
            applyAoeDamage(hitLocation,
                    Config.glacialArrowAoeRange,
                    Config.glacialArrowDamage);
            applyPullEffect(hitLocation,
                    Config.glacialArrowPullRange,
                    Config.glacialArrowPullStrength);
        }

        // 冰晶爆炸粒子
        spawnHitParticles(hitLocation);
    }

    // ========================================================================
    // AOE 范围伤害
    // ========================================================================
    private void applyAoeDamage(Vec3 center, double radius, double damageMultiplier) {
        double baseDamage = this.getBaseDamage();
        double finalDamage = baseDamage * damageMultiplier;

        // 获取范围内的所有 LivingEntity
        AABB aabb = new AABB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        List<Entity> entities = this.level().getEntities(this, aabb,
                entity -> entity instanceof LivingEntity && entity.isAlive()
                        && entity != this.getOwner()); // 不伤害发射者自己

        DamageSource damageSource = this.damageSources().arrow(this, this.getOwner());

        for (Entity entity : entities) {
            if (entity instanceof LivingEntity target) {
                // 距离越远伤害略微递减（线性衰减）
                double distance = entity.distanceToSqr(center);
                double maxDistSqr = radius * radius;
                double factor = 1.0 - (distance / maxDistSqr) * 0.3; // 边缘保留70%伤害
                target.hurt(damageSource, (float) (finalDamage * factor));
            }
        }
    }

    // ========================================================================
    // 生成子箭（直接散射，取消二段蓄力限制）
    // ========================================================================
    public void spawnSubArrowsNow() {
        int subCount = Config.glacialArrowSubCount;

        if (subCount <= 0) return;
        if (this.isSubArrow) return;  // 子箭不再生成子箭
        if (this.level().isClientSide()) return;

        // 母箭的飞行方向作为基准方向
        Vec3 baseDirection = this.getDeltaMovement().normalize();
        if (baseDirection.lengthSqr() < 0.001) {
            baseDirection = Vec3.directionFromRotation(this.getXRot(), this.getYRot());
        }

        // 扇形角度（度 → 弧度），均匀分布（霰弹式）
        double fanAngleRad = Math.toRadians(Config.glacialArrowFanAngle);
        double angleStep = subCount > 1 ? fanAngleRad / (subCount - 1) : 0;
        double startAngle = -fanAngleRad / 2.0;

        // 获取水平基准方向（忽略 Y 轴，用于水平散射）
        Vec3 horizontalDir = new Vec3(baseDirection.x, 0, baseDirection.z).normalize();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = horizontalDir.cross(up).normalize();
        if (right.lengthSqr() < 0.001) {
            right = new Vec3(0, 0, 1);
        }

        Vec3 spawnCenter = this.position();
        double motherBaseDamage = this.getBaseDamage();
        double subDamageMultiplier = Config.glacialArrowSubDamage;
        double subSpeedMultiplier = Config.glacialArrowSubSpeed;

        for (int i = 0; i < subCount; i++) {
            double angleOffset = startAngle + angleStep * i;

            // 旋转基准方向得到子箭方向（水平旋转）
            Vec3 subDirection = horizontalDir
                    .scale(Math.cos(angleOffset))
                    .add(right.scale(Math.sin(angleOffset)))
                    .normalize();

            // 略微向上抬一点，使子箭不下坠太快
            subDirection = subDirection.add(0, 0.1, 0).normalize();

            // 速度 = 母箭速度 × 配置倍率
            double subSpeed = this.getDeltaMovement().length() * subSpeedMultiplier;

            // 在母箭位置前方偏移生成，避免子箭立即互相碰撞
            Vec3 spawnPos = spawnCenter.add(subDirection.scale(1.0));

            // 创建子箭实体
            GlacialArrowEntity subArrow = new GlacialArrowEntity(
                    this.level(), (LivingEntity) this.getOwner(),
                    spawnPos.x, spawnPos.y, spawnPos.z);
            subArrow.setSubArrow(true);
            subArrow.setBaseDamage(motherBaseDamage * subDamageMultiplier);
            subArrow.setDeltaMovement(subDirection.scale(subSpeed));
            subArrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            subArrow.setMaxPierceCount(0); // 子箭不穿透，命中即销毁
            subArrow.setNoGravity(false);

            this.level().addFreshEntity(subArrow);
        }
    }

    // ========================================================================
    // 牵引效果（可指定范围和强度，子箭使用较小值）
    // ========================================================================
    private void applyPullEffect(Vec3 hitLocation, double pullRange, double pullStrength) {
        if (pullStrength <= 0 || pullRange <= 0) return;

        // 获取范围内的 LivingEntity
        AABB aabb = new AABB(hitLocation.x - pullRange, hitLocation.y - pullRange, hitLocation.z - pullRange,
                hitLocation.x + pullRange, hitLocation.y + pullRange, hitLocation.z + pullRange);
        List<Entity> entities = this.level().getEntities(this, aabb,
                entity -> entity instanceof LivingEntity && entity.isAlive()
                        && entity != this.getOwner()); // 不拉发射者

        for (Entity entity : entities) {
            Vec3 entityPos = entity.position();
            Vec3 pullVector = hitLocation.subtract(entityPos).normalize().scale(pullStrength);
            entity.setDeltaMovement(
                    entity.getDeltaMovement().x + pullVector.x,
                    entity.getDeltaMovement().y + pullVector.y + 0.1, // 稍微向上拉，防止卡地
                    entity.getDeltaMovement().z + pullVector.z
            );
            entity.hurtMarked = true;
        }
    }

    // ========================================================================
    // 命中粒子效果（冰晶爆炸）
    // ========================================================================
    private void spawnHitParticles(Vec3 hitLocation) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        // 冰晶爆炸效果
        serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                hitLocation.x, hitLocation.y, hitLocation.z,
                20, 0.8, 0.8, 0.8, 0.1);
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                hitLocation.x, hitLocation.y, hitLocation.z,
                15, 0.5, 0.5, 0.5, 0.05);
        // 爆炸冲击波效果
        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                hitLocation.x, hitLocation.y, hitLocation.z,
                1, 0, 0, 0, 0);
    }

    // ========================================================================
    // 辅助：获取箭矢的拾取物品栈（用于渲染）
    // ========================================================================
    @Override
    protected ItemStack getPickupItem() {
        return new ItemStack(Items.ARROW);
    }
}