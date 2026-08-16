package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// "千破·青溟剑"附魔事件处理器
//
// 【核心效果：附加"无视一切防御"的真实伤害】
//
//   为什么不直接调用 hurt()？
//     因为任何通过 LivingEntity.hurt(DamageSource) 的伤害都会经过：
//       1. 伤害减免（盔甲值 Armor）
//       2. 附魔减伤（Protection 系列）
//       3. 盾牌格挡
//     无法达到"无视护盾/保护/防御"的要求。
//
//   正确做法：直接 setHealth(getHealth() - extraDamage)
//     1. 完全绕过所有减伤层 → 真实伤害
//     2. 但副作用：不会触发 LivingHurtEvent 的减伤、不会显示受伤数字
//        不会让 shield 进行格挡判定、不会正确触发死亡逻辑
//
//   完整流程（兼顾"真伤"和"正常游戏流程"）：
//     ① 玩家原始攻击 → LivingHurtEvent 触发（本次事件，HIGH 优先级）
//     ② 检查附魔等级，计算 extraDamage
//     ③ 记录 NBT 标记（防止递归）→ setHealth(getHealth() - extraDamage)
//     ④ 手动触发 hurt 标记（设置 hurtTime、hurtDuration）保证受伤动画
//     ⑤ 如果生命<=0，手动调用 die(damageSource) 触发正常死亡流程
//     ⑥ 清除 NBT 标记
//     ⑦ 视觉反馈（粒子+音效）
//
// 【视觉】玩家→目标的橙色尾焰轨迹 (ParticleTypes.FLAME)
// 【音效】铁傀儡挥击音 (SoundEvents.IRON_GOLEM_ATTACK)
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class QianpoQingMingSwordHandler {

    // NBT 标签：用于在直接 setHealth 调用前标记，
    // 防止 hurt() 触发的 LivingHurtEvent 再次叠加伤害（递归）
    private static final String TAG_APPLYING_QIANPO = "QianpoQingMingApplying";

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        // ★ 递归保护：如果已经在处理"千破"真实伤害，立即返回
        LivingEntity target = event.getEntity();
        if (target.getPersistentData().getBoolean(TAG_APPLYING_QIANPO)) return;

        // 仅服务端处理（真实伤害需要改血量，粒子+音效也是服务端广播）
        if (target.level().isClientSide()) return;

        // 伤害来源必须是玩家（手持附魔剑）
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) return;

        // 检查主手武器附魔
        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty()) return;

        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.QIANPO_QINGMING_SWORD.get(), weapon);
        if (enchantLevel <= 0) return;

        // 根据附魔等级读取配置的额外伤害值
        float extraDamage = switch (enchantLevel) {
            case 1 -> (float) Config.qianpoQingMingDamageLevel1;
            case 2 -> (float) Config.qianpoQingMingDamageLevel2;
            case 3 -> (float) Config.qianpoQingMingDamageLevel3;
            case 4 -> (float) Config.qianpoQingMingDamageLevel4;
            default -> (float) Config.qianpoQingMingDamageLevel5;
        };

        if (extraDamage <= 0) return;

        // =================================================================
        // ★ 视觉反馈（粒子+音效） ★
        // =================================================================
        if (Config.qianpoQingMingVisualEnabled && target.level() instanceof ServerLevel serverLevel) {
            spawnFlameTrail(serverLevel, attacker, target);
            playAttackSound(serverLevel, target.position());
        }

        // =================================================================
        // ★ 核心：无视一切防御的真实伤害 ★
        // =================================================================
        // 先在目标 NBT 上标记"正在应用千破真伤"，防止 die()/hurt 导致的
        // LivingHurtEvent 再次进入此方法形成递归
        CompoundTag targetTag = target.getPersistentData();
        targetTag.putBoolean(TAG_APPLYING_QIANPO, true);

        try {
            float currentHealth = target.getHealth();
            float newHealth = currentHealth - extraDamage;

            // 限制最低 0（不能直接 <=0 会死得不对）
            newHealth = Math.max(0, newHealth);
            target.setHealth(newHealth);

            // ★ 设置受伤时间（让目标有红色闪白效果和硬直）★
            // 原版 LivingEntity.hurt() 会做这件事，setHealth 不会，
            // 所以我们手动设置
            target.hurtDuration = 10;   // 硬直时间（tick）
            target.hurtTime = 10;       // 受伤动画计时
            // 标记伤害来源方向（这样受击时目标会被击退，朝向攻击者）
            target.setLastHurtByMob(attacker);

            // ★ 如果目标血量 <= 0，手动触发死亡流程 ★
            //   setHealth(0) 本身不会触发 onDeath()、掉落、死亡音效等，
            //   必须手动调用 die()
            if (newHealth <= 0) {
                // 使用原始 DamageSource 作为死亡原因（这样死亡消息会正常显示）
                target.die(event.getSource());
            }
        } finally {
            // 无论是否抛异常，必须清除标记防止卡死
            targetTag.remove(TAG_APPLYING_QIANPO);
        }

        // 注意：不修改 event.setAmount()！
        // 玩家原始攻击伤害仍然正常走盔甲/保护减伤流程，
        // 千破的 extraDamage 是"叠加"在上面的独立真伤，完全符合要求
    }

    // ========================================================================
    // 生成玩家→目标的橙色火焰轨迹粒子（模拟尾焰）
    // ========================================================================
    private static void spawnFlameTrail(ServerLevel level, Entity from, Entity to) {
        // 在玩家位置 → 目标位置 之间，用线性插值生成多个 FLAME 粒子
        Vec3 start = from.position().add(0, from.getBbHeight() * 0.6, 0);
        Vec3 end = to.position().add(0, to.getBbHeight() * 0.5, 0);

        // 粒子数量：根据距离，最少 10 个，最多 30 个
        double distance = start.distanceTo(end);
        int particleCount = Math.max(10, Math.min(30, (int) (distance * 8)));

        for (int i = 0; i < particleCount; i++) {
            double t = (double) i / (particleCount - 1); // 0.0 ~ 1.0
            double x = start.x + (end.x - start.x) * t;
            double y = start.y + (end.y - start.y) * t;
            double z = start.z + (end.z - start.z) * t;

            // ParticleTypes.FLAME：橙色火焰粒子（原版营火/火把那种颜色）
            // 加一点随机偏移让轨迹有厚度、更自然
            level.sendParticles(
                    ParticleTypes.FLAME,
                    x + (level.random.nextDouble() - 0.5) * 0.15,
                    y + (level.random.nextDouble() - 0.5) * 0.15,
                    z + (level.random.nextDouble() - 0.5) * 0.15,
                    1,      // 每个位置 1 个粒子
                    0,      // 速度 X:0（不飘动，直接连成线）
                    0,      // 速度 Y:0
                    0,      // 速度 Z:0
                    0       // 速度参数（FLAME 忽略此参数）
            );
        }
    }

    // ========================================================================
    // 播放攻击音效：铁傀儡挥击音（IRON_GOLEM_ATTACK）
    // ========================================================================
    private static void playAttackSound(ServerLevel level, Vec3 pos) {
        // SoundSource.HOSTILE：敌对生物音效通道（音量正常衰减）
        // 音量 1.0，音调 1.0（标准音高），随机 0.8~1.2 避免每次听起来完全一样
        float pitch = 0.8f + level.random.nextFloat() * 0.4f;
        level.playSound(
                null,
                pos.x, pos.y, pos.z,
                SoundEvents.IRON_GOLEM_ATTACK,
                SoundSource.HOSTILE,
                1.0f,
                pitch
        );
    }
}