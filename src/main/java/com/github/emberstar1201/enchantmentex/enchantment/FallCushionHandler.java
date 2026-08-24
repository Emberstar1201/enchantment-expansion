package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.FallCushionConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【坠落缓冲】附魔事件处理器
//
// 实现（纯事件，无需 Mixin）：
//   LivingFallEvent（摔落结算前触发）：
//     ① 检测靴子上「坠落缓冲」等级
//     ② 按等级设置摔落伤害倍率（setDamageMultiplier 直接降低伤害）
//     ③ 若摔落距离超过配置下限，产生冲击波：
//        - 以玩家落点为中心，把半径内所有非玩家实体击退出去
//        - 击退强度随距离衰减
//        - 播放落地闷响音效 + 尘土粒子
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FallCushionHandler {

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        // 全局开关
        if (!FallCushionConfig.enabled) return;

        // 检测靴子附魔等级
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        int level = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.FALL_CUSHION.get(), boots);
        if (level <= 0) return;

        // ① 按等级减免摔落伤害
        event.setDamageMultiplier((float) FallCushionConfig.getDamageMultiplier(level));

        // ② 冲击波判定：摔落距离达到门槛才触发
        float fallDistance = event.getDistance();
        if (fallDistance < FallCushionConfig.minFallDistance) {
            return; // 低处落地不触发冲击波（防止平地跳跃滥用）
        }

        double radius = FallCushionConfig.getRadius(level);
        double knockback = FallCushionConfig.getKnockback(level);
        Level levelWorld = player.level();

        // 半径内所有实体（排除玩家自身，保留生物与掉落物可被推开）
        AABB aabb = player.getBoundingBox().inflate(radius);
        List<LivingEntity> nearby = levelWorld.getEntitiesOfClass(LivingEntity.class, aabb,
                e -> e != player && e.isAlive());

        for (LivingEntity entity : nearby) {
            double dx = entity.getX() - player.getX();
            double dz = entity.getZ() - player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > radius) continue;

            // 击退强度随距离衰减（近处强、远处弱）
            double strength = knockback * (1.0 - dist / radius);
            if (strength <= 0) continue;
            entity.knockback(strength, dx, dz);
        }

        // 落地闷响音效 + 尘土粒子（视觉反馈）
        levelWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 0.8F, 0.6F);
        if (levelWorld instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 0.1, player.getZ(),
                    20, radius * 0.3, 0.1, radius * 0.3, 0.1);
        }
    }
}