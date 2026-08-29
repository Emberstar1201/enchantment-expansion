package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.ChainArrowConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import com.github.emberstar1201.enchantmentex.util.AllyFilter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【贯穿链条】附魔事件处理器
//
// 两段式实现（与爆破箭矢同款思路，保证判定与射击源解耦）：
//   ① EntityJoinLevelEvent：箭生成进入世界时，若射手持有带「贯穿链条」
//      的弓/弩，把附魔等级写入箭的 NBT。
//   ② ProjectileImpactEvent：箭命中实体时，读取 NBT 等级：
//      - 对命中的第一个目标造成原版箭矢伤害
//      - 根据等级次数，在搜索半径内寻找下一个最近的敌人弹射过去
//      - 每次弹射对目标造成箭基础伤害 × damageRatio 的伤害
//      - 弹射时生成灵魂火焰粒子做出闪烁衔接效果
//      - 取消后续原版箭矢伤害（避免重复结算）
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChainArrowHandler {

    // 箭 NBT 标记：记录「贯穿链条」附魔等级
    private static final String TAG_CHAIN_LEVEL = "ChainArrowLevel";

    // ========================================================================
    // ① 箭生成进入世界时打标记
    // ========================================================================
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;

        // 防重复标记（维度传送/重新加入世界）
        CompoundTag tag = arrow.getPersistentData();
        if (tag.contains(TAG_CHAIN_LEVEL)) return;

        // 仅玩家射出的箭才标记
        if (!(arrow.getOwner() instanceof Player player)) return;

        // 检查主/副手弓弩上的附魔等级
        int level = getChainLevel(player.getMainHandItem(), player.getOffhandItem());
        if (level <= 0) return;

        tag.putInt(TAG_CHAIN_LEVEL, level);
    }

    /** 计算两手弓/弩上的「贯穿链条」等级（取较大值） */
    private static int getChainLevel(ItemStack main, ItemStack off) {
        int level = 0;
        if (main.getItem() instanceof BowItem || main.getItem() instanceof CrossbowItem) {
            level = Math.max(level, EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.CHAIN_ARROW.get(), main));
        }
        if (off.getItem() instanceof BowItem || off.getItem() instanceof CrossbowItem) {
            level = Math.max(level, EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.CHAIN_ARROW.get(), off));
        }
        return level;
    }

    // ========================================================================
    // ② 箭命中实体时触发贯穿链条
    // ========================================================================
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        Level level = arrow.level();
        if (level.isClientSide()) return;

        // 读取 NBT 标记（无标记 → 非贯穿箭）
        int levelEnchant = arrow.getPersistentData().getInt(TAG_CHAIN_LEVEL);
        if (levelEnchant <= 0) return;
        // 全局开关
        if (!ChainArrowConfig.enabled) return;

        // 仅对命中"实体"生效（命中方块不弹射）
        HitResult hit = event.getRayTraceResult();
        if (!(hit instanceof EntityHitResult entityHit)) return;
        if (!(entityHit.getEntity() instanceof LivingEntity firstTarget)) return;

        // 配置数值
        int bounceCount = ChainArrowConfig.getBounceCount(levelEnchant);
        double searchRange = ChainArrowConfig.searchRange;
        float bounceDamage = (float) (arrow.getBaseDamage() * ChainArrowConfig.damageRatio);

        // 对第一个目标造成原版箭矢伤害（使用箭伤害源）
        Entity owner = arrow.getOwner();
        firstTarget.hurt(level.damageSources().arrow(arrow, owner), bounceDamage);

        // 弹射链条：从第一个目标开始，逐跳寻找下一目标
        LivingEntity current = firstTarget;
        for (int i = 0; i < bounceCount; i++) {
            LivingEntity next = findNextTarget(level, current, owner);
            if (next == null) break;

            // 对下一目标造成弹射伤害
            next.hurt(level.damageSources().arrow(arrow, owner), bounceDamage);

            // 视觉衔接：箭瞬移到下一目标位置 + 灵魂火焰粒子
            arrow.moveTo(next.getX(), next.getY() + next.getBbHeight() * 0.5, next.getZ());
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        next.getX(), next.getY() + next.getBbHeight() * 0.5, next.getZ(),
                        3, 0.2, 0.2, 0.2, 0.02);
            }

            current = next;
        }

        // 链条完结，移除箭并取消原版命中伤害（避免重复结算）
        arrow.discard();
        event.setCanceled(true);
    }

    // ========================================================================
    // 工具：在搜索半径内寻找距离当前目标最近的下一个可弹射目标
    // 排除：当前目标自身、射手，以及玩家/村民/驯服生物/女仆等友方（统一友伤过滤）
    // ========================================================================
    private static LivingEntity findNextTarget(Level level, LivingEntity current, Entity owner) {
        AABB aabb = current.getBoundingBox().inflate(ChainArrowConfig.searchRange);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, aabb,
                e -> e.isAlive() && e != current && e != owner
                        && !AllyFilter.isFriendly(e));
        // 取距离最近的一个
        return candidates.stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(current)))
                .orElse(null);
    }
}