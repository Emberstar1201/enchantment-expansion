package com.github.emberstar1201.enchantmentex.entity;

import com.github.emberstar1201.enchantmentex.Config;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

// ========================================================================
// 月牙形剑气实体
//
// 【功能】
//   1. 无重力直线飞行，恒定速度
//   2. 穿透敌人（每 tick 检测 AABB 范围内所有实体并造成伤害）
//   3. 到达最大距离后自动消失
//   4. 撞到方块后消失
//   5. 客户端生成月牙形粒子拖尾
//
// 【颜色模式】
//   - TYPE_YUNLAI = 1：淡蓝色月牙（云来剑法）
//   - TYPE_ANCIENT = 2：金色/青色月牙（古·云来剑法）
// ========================================================================
public class CrescentEntity extends Projectile {

    private static final EntityDataAccessor<Integer> DATA_TYPE =
            SynchedEntityData.defineId(CrescentEntity.class, EntityDataSerializers.INT);

    // 剑气伤害值（由创建时传入，由 Handler 根据攻击伤害计算）
    private float damage;

    // 最大飞行距离（格）
    private double maxDistance;

    // 记录发射位置，用于计算飞行距离
    private Vec3 startPos;

    // ========================================================================
    // 构造方法
    // ========================================================================

    // Forge 反序列化必需的无参构造
    public CrescentEntity(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
    }

    // 自定义构造：从发射者位置朝指定方向发射
    public CrescentEntity(Level level, LivingEntity owner,
                          Vec3 pos, Vec3 direction,
                          float speed, float damage, double maxDistance, int type) {
        super(ModEntities.CRESCENT.get(), level);
        this.setOwner(owner);
        this.setPos(pos.x, pos.y, pos.z);
        // 恒定速度直线飞行
        this.setDeltaMovement(direction.normalize().scale(speed));
        this.damage = damage;
        this.maxDistance = maxDistance;
        this.startPos = pos;
        this.setNoGravity(true);    // 无重力，保持直线
        this.setCrescentType(type);
    }

    // ========================================================================
    // 数据同步
    // ========================================================================
    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_TYPE, 1);
    }

    public void setCrescentType(int type) {
        this.entityData.set(DATA_TYPE, type);
    }

    public int getCrescentType() {
        return this.entityData.get(DATA_TYPE);
    }

    // ========================================================================
    // NBT 持久化
    // ========================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("CrescentDamage", this.damage);
        tag.putDouble("CrescentMaxDistance", this.maxDistance);
        if (this.startPos != null) {
            tag.putDouble("StartPosX", this.startPos.x);
            tag.putDouble("StartPosY", this.startPos.y);
            tag.putDouble("StartPosZ", this.startPos.z);
        }
        tag.putInt("CrescentType", this.getCrescentType());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.damage = tag.getFloat("CrescentDamage");
        this.maxDistance = tag.getDouble("CrescentMaxDistance");
        if (tag.contains("StartPosX")) {
            this.startPos = new Vec3(
                    tag.getDouble("StartPosX"),
                    tag.getDouble("StartPosY"),
                    tag.getDouble("StartPosZ")
            );
        }
        this.setCrescentType(tag.getInt("CrescentType"));
    }

    // ========================================================================
    // 每 tick 更新
    // ========================================================================
    @Override
    public void tick() {
        // 调用 Entity.baseTick() 处理火焰、传送门等基础逻辑
        // 不调用 super.tick()（Projectile.tick() 会减速并自带碰撞检测）
        this.baseTick();

        // 初始化发射位置
        if (this.startPos == null) {
            this.startPos = this.position();
        }

        // ---------- 服务端逻辑 ----------
        if (!this.level().isClientSide()) {
            // 发射者已消失则移除剑气
            if (this.getOwner() != null && this.getOwner().isRemoved()) {
                this.discard();
                return;
            }

            // 检查最大飞行距离
            if (this.startPos.distanceToSqr(this.position()) >= this.maxDistance * this.maxDistance) {
                this.discard();
                return;
            }

            // 检查是否撞到方块（使用 Entity.move() 后的碰撞标志）
            if (this.horizontalCollision || this.verticalCollision) {
                this.discard();
                return;
            }
        }

        // ---------- 移动（恒定速度直线飞行） ----------
        Vec3 movement = this.getDeltaMovement();
        this.move(net.minecraft.world.entity.MoverType.SELF, movement);

        // ---------- 实体碰撞检测（穿透式 AABB 检测） ----------
        if (!this.level().isClientSide()) {
            // 当前帧的碰撞箱膨胀检测
            AABB aabb = this.getBoundingBox().inflate(0.3);
            List<Entity> targets = this.level().getEntities(this, aabb,
                    e -> e instanceof LivingEntity
                            && e.isAlive()
                            && e != this.getOwner()
                            && !e.isSpectator());

            for (Entity target : targets) {
                if (target instanceof LivingEntity living) {
                    // 避免重复伤害同一目标（用 hurt 标记，但穿透攻击只触发一次 per tick）
                    living.hurt(
                            this.damageSources().mobProjectile(this, this.getOwner() instanceof LivingEntity lo ? lo : null),
                            this.damage
                    );
                }
            }

            // 服务端通过 sendParticles 广播月牙形粒子到所有附近客户端
            spawnCrescentParticles();
        }
    }

    // ========================================================================
    // 月牙形粒子特效
    //
    // 在剑气当前位置生成一组排列成月牙/弧形的粒子
    // 云来剑法（类型1）：淡蓝色（RGB: 0.5, 0.8, 1.0）
    // 古·云来剑法（类型2）：金色（RGB: 1.0, 0.8, 0.0）+ 青色（RGB: 0.0, 1.0, 1.0）
    // ========================================================================
    private void spawnCrescentParticles() {
        Level world = this.level();
        if (!(world instanceof ServerLevel serverLevel)) return;

        Vec3 pos = this.position();
        Vec3 dir = this.getDeltaMovement().normalize();
        if (dir.lengthSqr() < 0.001) return;

        int type = this.getCrescentType();

        // 计算垂直平面上的两个轴（用于生成月牙形状）
        Vec3 up = new Vec3(0, 1, 0);
        // 避免与 Y 轴平行时出问题
        if (Math.abs(dir.y) > 0.99) {
            up = new Vec3(1, 0, 0);
        }
        Vec3 right = dir.cross(up).normalize();
        Vec3 localUp = right.cross(dir).normalize();

        // 月牙形状参数：半圆弧上的点
        // 生成一个 C 形（月牙）：在 -120° ~ +120° 范围内取点，但去掉中间部分（形成月牙缺口）
        int particleCount = 6 + world.random.nextInt(3); // 6~8 个粒子

        for (int i = 0; i < particleCount; i++) {
            // 角度范围：-150° ~ +150°，中间 -30°~+30° 跳过（形成月牙缺口）
            float angleDeg;
            if (i % 2 == 0) {
                angleDeg = -150.0f + world.random.nextFloat() * 90.0f;   // 左翼 -150° ~ -60°
            } else {
                angleDeg = 60.0f + world.random.nextFloat() * 90.0f;     // 右翼 60° ~ 150°
            }
            float angleRad = (float) Math.toRadians(angleDeg);

            // 月牙半径（约 0.3~0.5 格）
            float radius = 0.3f + world.random.nextFloat() * 0.2f;

            // 计算在垂直平面上的位置（垂直于飞行方向）
            double dx = right.x * Math.cos(angleRad) * radius + localUp.x * Math.sin(angleRad) * radius;
            double dy = right.y * Math.cos(angleRad) * radius + localUp.y * Math.sin(angleRad) * radius;
            double dz = right.z * Math.cos(angleRad) * radius + localUp.z * Math.sin(angleRad) * radius;

            double px = pos.x + dx;
            double py = pos.y + dy;
            double pz = pos.z + dz;

            // 选择粒子颜色
            if (type == 1) {
                // 云来剑法：淡蓝色 DustParticleOptions
                // Vec3(红, 绿, 蓝) 值域 0~1
                serverLevel.sendParticles(
                        new DustParticleOptions(new Vector3f(0.5f, 0.8f, 1.0f), 0.6f),
                        px, py, pz, 1, 0, 0, 0, 0
                );
            } else {
                // 古·云来剑法：金色 + 青色混合
                if (world.random.nextBoolean()) {
                    // 金色 DustParticleOptions
                    serverLevel.sendParticles(
                            new DustParticleOptions(new Vector3f(1.0f, 0.85f, 0.0f), 0.7f),
                            px, py, pz, 1, 0, 0, 0, 0
                    );
                } else {
                    // 青色 DustParticleOptions
                    serverLevel.sendParticles(
                            new DustParticleOptions(new Vector3f(0.0f, 1.0f, 1.0f), 0.6f),
                            px, py, pz, 1, 0, 0, 0, 0
                    );
                }
            }
        }

        // 额外：在剑气中心生成一个光点（尾迹效果）
        if (world.random.nextFloat() < 0.4f) {
            if (type == 1) {
                serverLevel.sendParticles(
                        new DustParticleOptions(new Vector3f(0.7f, 0.9f, 1.0f), 0.3f),
                        pos.x, pos.y, pos.z, 1, 0, 0, 0, 0
                );
            } else {
                // 古·云来：金色光点
                serverLevel.sendParticles(
                        new DustParticleOptions(new Vector3f(1.0f, 0.9f, 0.4f), 0.4f),
                        pos.x, pos.y, pos.z, 1, 0, 0, 0, 0
                );
            }
        }
    }
}