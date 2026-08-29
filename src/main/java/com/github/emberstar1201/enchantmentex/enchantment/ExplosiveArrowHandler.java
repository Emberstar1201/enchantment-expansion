package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.ExplosiveArrowConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import com.github.emberstar1201.enchantmentex.util.AllyFilter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【爆破箭矢】附魔事件处理器
//
// 两段式实现（与迅捷之弩同款思路，保证判定与射击源解耦）：
//   ① EntityJoinLevelEvent：箭进入世界时，若射手（玩家）主/副手持有
//      带「爆破箭矢」的弓/弩，把附魔等级写入箭的 NBT。
//   ② ProjectileImpactEvent：箭命中时读取 NBT 等级，
//      - 命中生物：对其施加全额中心伤害
//      - 落点周围：按距离衰减施加范围伤害（不破坏地形、不点燃）
//      - 播放爆炸音效与粒子
//      - 取消后续原版箭矢伤害（避免双重伤害）
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ExplosiveArrowHandler {

    // 箭 NBT 标记：记录「爆破箭矢」附魔等级
    private static final String TAG_EXPLOSIVE_LEVEL = "ExplosiveArrowLevel";

    // ========================================================================
    // ① 箭生成进入世界时打标记
    // ========================================================================
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;

        // 防重复标记（维度传送/重新加入世界）
        CompoundTag tag = arrow.getPersistentData();
        if (tag.contains(TAG_EXPLOSIVE_LEVEL)) return;

        // 仅玩家射出的箭才标记
        if (!(arrow.getOwner() instanceof Player player)) return;

        // 检查主/副手弓弩上的附魔等级
        int level = getExplosiveLevel(player.getMainHandItem(), player.getOffhandItem());
        if (level <= 0) return;

        tag.putInt(TAG_EXPLOSIVE_LEVEL, level);
    }

    /** 计算两手弓/弩上的「爆破箭矢」等级（取较大值） */
    private static int getExplosiveLevel(ItemStack main, ItemStack off) {
        int level = 0;
        if (main.getItem() instanceof BowItem || main.getItem() instanceof CrossbowItem) {
            level = Math.max(level, EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.EXPLOSIVE_ARROW.get(), main));
        }
        if (off.getItem() instanceof BowItem || off.getItem() instanceof CrossbowItem) {
            level = Math.max(level, EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.EXPLOSIVE_ARROW.get(), off));
        }
        return level;
    }

    // ========================================================================
    // ② 箭命中时触发爆炸
    // ========================================================================
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        Level level = arrow.level();
        if (level.isClientSide()) return;

        // 读取 NBT 标记（无标记 → 非爆破箭）
        int levelEnchant = arrow.getPersistentData().getInt(TAG_EXPLOSIVE_LEVEL);
        if (levelEnchant <= 0) return;
        // 全局开关
        if (!ExplosiveArrowConfig.enabled) return;

        // 配置数值
        double radius = ExplosiveArrowConfig.getRadius(levelEnchant);
        double maxDamage = ExplosiveArrowConfig.getDamage(levelEnchant);

        // 命中位置（以射线命中点作为爆炸中心）
        HitResult hit = event.getRayTraceResult();
        Vec3 hitPos = hit.getLocation();
        Entity owner = arrow.getOwner();
        LivingEntity directTarget = null;

        // 直接命中的实体：施加全额中心伤害
        if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult entityHit) {
            if (entityHit.getEntity() instanceof LivingEntity target && target.isAlive()) {
                directTarget = target;
                target.hurt(level.damageSources().generic(), (float) maxDamage);
            }
        }

        LivingEntity finalDirectTarget = directTarget;

        // 范围伤害：以命中点为球心，随距离线性衰减
        AABB aabb = new AABB(hitPos.x - radius, hitPos.y - radius, hitPos.z - radius,
                hitPos.x + radius, hitPos.y + radius, hitPos.z + radius);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, aabb,
                e -> e.isAlive() && e != owner && e != finalDirectTarget
                        // 统一友伤过滤：不波及玩家、村民、驯服生物、女仆等友方
                        && !AllyFilter.isFriendly(e));
        for (LivingEntity entity : nearby) {
            double dist = Math.sqrt(entity.distanceToSqr(hitPos));
            if (dist > radius) continue;
            // 距离衰减伤害：中心满额，边缘趋近 0
            float falloff = (float) (maxDamage * (1.0 - dist / radius));
            if (falloff <= 0) continue;
            entity.hurt(level.damageSources().explosion(owner, owner), falloff);
        }

        // 爆炸音效与粒子（不产生方块破坏/火焰）
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0F, 1.0F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                    hitPos.x, hitPos.y, hitPos.z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        // 箭已爆炸，移除箭并取消原版命中伤害（避免双重伤害）
        arrow.discard();
        event.setCanceled(true);
    }
}