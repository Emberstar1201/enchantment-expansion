package com.github.emberstar1201.enchantmentex.entity;

import com.github.emberstar1201.enchantmentex.item.handler.ResidualScytheHandler;
import com.github.emberstar1201.enchantmentex.util.AllyFilter;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/** 湮灭之镰的湮灭能量大球：服务端负责移动、碰撞和伤害，客户端只接收实体同步。 */
public class AnnihilationOrbEntity extends Projectile {
    private static final int MAX_LIFETIME = 40;
    private static final double EXPLOSION_RADIUS = 4.0D;
    private static final DustParticleOptions ORB_PARTICLE =
            new DustParticleOptions(new Vector3f(0.18F, 0.02F, 0.02F), 1.2F);

    private float damage;
    private int age;

    public AnnihilationOrbEntity(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
    }

    public AnnihilationOrbEntity(Level level, LivingEntity owner, Vec3 position,
                                 Vec3 direction, float damage) {
        super(ModEntities.ANNIHILATION_ORB.get(), level);
        this.setOwner(owner);
        this.setPos(position.x, position.y, position.z);
        this.setDeltaMovement(direction.normalize().scale(0.9D));
        this.setNoGravity(true);
        this.damage = damage;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
        tag.putInt("Age", age);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
        age = tag.getInt("Age");
    }

    @Override
    public void tick() {
        this.baseTick();
        if (level().isClientSide()) {
            spawnClientParticles();
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            return;
        }

        age++;
        if (age >= MAX_LIFETIME || horizontalCollision || verticalCollision) {
            explode();
            return;
        }

        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        if (getBoundingBox().inflate(0.35D).getSize() > 0) {
            List<LivingEntity> targets = level().getEntitiesOfClass(LivingEntity.class,
                    getBoundingBox().inflate(0.35D), entity -> entity != getOwner()
                            && entity.isAlive() && !entity.isSpectator()
                            && !AllyFilter.isFriendly(entity));
            if (!targets.isEmpty()) {
                explode();
                return;
            }
        }
        spawnServerParticles();
    }

    private void explode() {
        if (isRemoved()) return;
        if (level() instanceof ServerLevel serverLevel) {
            Entity owner = getOwner();
            AABB area = getBoundingBox().inflate(EXPLOSION_RADIUS);
            List<LivingEntity> targets = level().getEntitiesOfClass(LivingEntity.class, area,
                    entity -> entity != owner && entity.isAlive() && !entity.isSpectator()
                            && !AllyFilter.isFriendly(entity));
            for (LivingEntity target : targets) {
                // 独立标记会让镰刀处理器跳过连击和终界之星加成。
                if (owner instanceof LivingEntity livingOwner) {
                    ResidualScytheHandler.hurtByAnnihilation(livingOwner, target, damage);
                }
            }
            serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                    getX(), getY(), getZ(), 4, 0.25D, 0.25D, 0.25D, 0.0D);
            serverLevel.sendParticles(ORB_PARTICLE,
                    getX(), getY(), getZ(), 10, 0.6D, 0.6D, 0.6D, 0.02D);
        }
        discard();
    }

    private void spawnServerParticles() {
        if (level() instanceof ServerLevel serverLevel && age % 2 == 0) {
            serverLevel.sendParticles(ORB_PARTICLE, getX(), getY(), getZ(), 1,
                    0.05D, 0.05D, 0.05D, 0.0D);
        }
    }

    private void spawnClientParticles() {
        if (age % 2 == 0) {
            level().addParticle(ORB_PARTICLE, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
        }
    }
}
