package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
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
// 【核心效果：附加魔法伤害（无视护甲，受保护附魔和抗性提升影响）】
//
//   使用 DamageSource.magic() 魔法伤害类型：
//     1. 无视盔甲值（Armor）→ 对高护甲目标依然有效
//     2. 受保护附魔（Protection）减免
//     3. 受抗性提升（Resistance）效果减免
//     4. 无法被盾牌格挡
//
//   实现方式（利用 hurt() 方法）：
//     ① 玩家原始攻击 → LivingHurtEvent 触发（本次事件，HIGH 优先级）
//     ② 检查附魔等级，计算 extraDamage
//     ③ 记录 NBT 标记（防止递归）→ target.hurt(magicDamage, extraDamage)
//     ④ hurt() 自动处理受伤动画、击退方向、死亡流程
//     ⑤ 清除 NBT 标记
//     ⑥ 视觉反馈（粒子+音效）
//
// 【视觉】玩家→目标的橙色尾焰轨迹 (ParticleTypes.FLAME)
// 【音效】铁傀儡挥击音 (SoundEvents.IRON_GOLEM_ATTACK)
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class QianpoQingMingSwordHandler {

    // NBT 标签：用于在调用 hurt() 前标记，
    // 防止递归触发 LivingHurtEvent 再次进入此方法
    private static final String TAG_APPLYING_QIANPO = "QianpoQingMingApplying";

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        // ★ 递归保护：如果已经在处理"千破"魔法伤害，立即返回
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
        // ★ 核心：魔法伤害（无视护甲，受保护附魔和抗性提升影响） ★
        // =================================================================
        // 使用 DamageSource.magic() 创建魔法伤害类型，
        // 魔法伤害无视盔甲值（Armor），但会被保护附魔（Protection）和
        // 抗性提升（Resistance）效果减免。
        //
        // 先在目标 NBT 上标记"正在应用千破魔法伤害"，防止 hurt() 触发的
        // LivingHurtEvent 再次进入此方法形成递归
        target.getPersistentData().putBoolean(TAG_APPLYING_QIANPO, true);

        try {
            // 创建魔法伤害来源（发射者为攻击者）
            DamageSource magicDamage = target.damageSources().magic();
            // 直接调用 hurt() 应用伤害，这会自动处理：
            //   - 受伤动画（hurtDuration/hurtTime）
            //   - 击退方向（setLastHurtByMob）
            //   - 死亡流程（die()）
            target.hurt(magicDamage, extraDamage);
        } finally {
            // 无论是否抛异常，必须清除标记防止卡死
            target.getPersistentData().remove(TAG_APPLYING_QIANPO);
        }

        // 注意：不修改 event.setAmount()！
        // 玩家原始攻击伤害仍然正常走盔甲/保护减伤流程，
        // 千破的 extraDamage 是"叠加"在上面的独立魔法伤害，完全符合要求
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