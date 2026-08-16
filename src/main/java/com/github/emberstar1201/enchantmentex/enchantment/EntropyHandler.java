package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【熵增】附魔事件处理器
//
// 使用实体 PersistentData 存储熵标记层数和减速状态。
// LivingHurtEvent：攻击时叠加标记，按层数触发不同效果。
//
// NBT 结构（直接写入实体根 PersistentData）：
//   EntropyStacks: 1..3
//   EntropySpeedDebuff: true/false
//
// 【粒子反馈】
//   满层引爆时在目标位置生成灰黑色粒子（WITCH + LARGE_SMOKE）
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class EntropyHandler {

    // NBT 键名
    private static final String TAG_STACKS = "EntropyStacks";
    private static final String TAG_SPEED_DEBUFF = "EntropySpeedDebuff";

    // 固定 UUID 用于移速 AttributeModifier
    private static final UUID ENTROPY_SPEED_UUID =
            UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");

    // 效果参数
    private static final int MAX_STACKS = 3;
    private static final double LOST_HP_RATIO = 0.3;    // 30% 已损失生命值
    private static final double SPREAD_RATIO = 0.5;     // 50% 扩散伤害
    private static final double SPREAD_RADIUS = 8.0;    // 8 格扩散半径
    private static final double SPEED_REDUCTION = -0.15; // -15% 移速

    // ========================================================================
    // LivingHurtEvent：叠加标记 → 引爆 / 减速
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // 获取攻击者（必须为玩家）
        Entity attackerEntity = event.getSource().getEntity();
        if (!(attackerEntity instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        // 获取目标实体
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;

        // 检查主手武器是否有熵增附魔
        ItemStack weapon = player.getMainHandItem();
        int enchantLevel = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.ENTROPY.get(), weapon);
        if (enchantLevel <= 0) return;

        // ========================================================================
        // 读取 / 递增熵标记
        // ========================================================================
        var data = target.getPersistentData();
        int stacks = data.getInt(TAG_STACKS);
        stacks++;

        if (stacks >= MAX_STACKS) {
            // ================================================================
            // 满层（3层）：引爆熵增
            // ================================================================

            // 计算引爆伤害：已损失生命值 × 30%
            float lostHealth = target.getMaxHealth() - target.getHealth();
            float explosionDamage = lostHealth * (float) LOST_HP_RATIO;

            if (explosionDamage > 0) {
                // 对主目标造成魔法伤害（视为真实伤害，无视护甲）
                DamageSource magicSource = target.damageSources().magic();
                target.hurt(magicSource, explosionDamage);
            }

            // 扩散到周围敌对生物
            if (target.level() instanceof ServerLevel serverLevel && explosionDamage > 0) {
                AABB box = AABB.ofSize(target.position(),
                        SPREAD_RADIUS * 2, SPREAD_RADIUS * 2, SPREAD_RADIUS * 2);
                List<LivingEntity> nearbyHostiles = target.level().getEntitiesOfClass(
                        LivingEntity.class, box,
                        e -> e != target && e != player && e instanceof Enemy);

                float spreadDamage = explosionDamage * (float) SPREAD_RATIO;
                for (LivingEntity mob : nearbyHostiles) {
                    mob.hurt(mob.damageSources().magic(), spreadDamage);
                }

                // 粒子效果：灰黑色魔法粒子 + 烟雾
                serverLevel.sendParticles(ParticleTypes.WITCH,
                        target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                        40, 1.5, 0.5, 1.5, 0.1);
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                        15, 1.0, 0.3, 1.0, 0.02);
            }

            // 清除标记 + 减速
            removeSpeedDebuff(target);
            data.remove(TAG_STACKS);
            data.remove(TAG_SPEED_DEBUFF);

        } else {
            // ================================================================
            // 未满层：保存标记
            // ================================================================
            data.putInt(TAG_STACKS, stacks);

            if (stacks == 2) {
                // 2层：目标移动速度 -15%
                applySpeedDebuff(target);
            }
        }
    }

    // ========================================================================
    // 工具方法：移速减速 AttributeModifier 管理
    // ========================================================================

    /** 应用移速减速（-15% MULTIPLY_TOTAL） */
    private static void applySpeedDebuff(LivingEntity entity) {
        var data = entity.getPersistentData();
        if (data.getBoolean(TAG_SPEED_DEBUFF)) return; // 已应用，跳过

        AttributeInstance attr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;

        // 移除旧值后添加新值（确保幂等）
        attr.removeModifier(ENTROPY_SPEED_UUID);
        AttributeModifier modifier = new AttributeModifier(
                ENTROPY_SPEED_UUID,
                "Entropy speed debuff",
                SPEED_REDUCTION,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        attr.addTransientModifier(modifier);
        data.putBoolean(TAG_SPEED_DEBUFF, true);
    }

    /** 移除移速减速 */
    private static void removeSpeedDebuff(LivingEntity entity) {
        var data = entity.getPersistentData();
        if (!data.getBoolean(TAG_SPEED_DEBUFF)) return; // 未应用，跳过

        AttributeInstance attr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null) {
            attr.removeModifier(ENTROPY_SPEED_UUID);
        }
        data.remove(TAG_SPEED_DEBUFF);
    }
}