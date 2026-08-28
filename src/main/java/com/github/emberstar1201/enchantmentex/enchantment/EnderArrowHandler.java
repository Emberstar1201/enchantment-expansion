package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.EnderArrowConfig;
import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// 末影箭事件处理：射出时给箭记录标记，命中时依据标记传送原射手。
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EnderArrowHandler {

    private static final String TAG_ENDER_ARROW = "EnderArrow";
    private static final Map<UUID, Long> COOLDOWN_END_TICKS = new HashMap<>();

    @SubscribeEvent
    public static void onArrowJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }

        CompoundTag tag = arrow.getPersistentData();
        if (tag.contains(TAG_ENDER_ARROW)
                || !(arrow.getOwner() instanceof Player player)) {
            return;
        }

        if (getEnderArrowLevel(player.getMainHandItem(), player.getOffhandItem()) > 0) {
            // 标记写入箭本身，避免玩家在箭飞行期间切换武器而丢失效果。
            tag.putBoolean(TAG_ENDER_ARROW, true);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }

        Level level = arrow.level();
        if (level.isClientSide()
                || !EnderArrowConfig.isEnabled()
                || !arrow.getPersistentData().getBoolean(TAG_ENDER_ARROW)
                || !(arrow.getOwner() instanceof ServerPlayer player)
                || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        long now = serverLevel.getGameTime();
        long cooldownEnd = COOLDOWN_END_TICKS.getOrDefault(player.getUUID(), 0L);
        if (now < cooldownEnd) {
            long remainingSeconds = (long) Math.ceil((cooldownEnd - now) / 20.0);
            player.displayClientMessage(Component.literal(
                    "§5末影箭冷却中：" + remainingSeconds + " 秒"), true);
            return;
        }

        HitResult hit = event.getRayTraceResult();
        Vec3 destination = hit.getLocation();
        if (EnderArrowConfig.shouldTeleportToEntity()
                && hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity target) {
            destination = getEntityFrontPosition(target);
        }

        serverLevel.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1.0, player.getZ(),
                32, 0.35, 0.7, 0.35, 0.15);
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        // 直接调用玩家传送不会附带末影珍珠伤害。
        player.teleportTo(destination.x, destination.y, destination.z);

        serverLevel.sendParticles(ParticleTypes.PORTAL,
                destination.x, destination.y + 1.0, destination.z,
                32, 0.35, 0.7, 0.35, 0.15);
        serverLevel.playSound(null, destination.x, destination.y, destination.z,
                SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        COOLDOWN_END_TICKS.put(player.getUUID(), now + EnderArrowConfig.getCooldownTicks());
    }

    private static int getEnderArrowLevel(ItemStack mainHand, ItemStack offHand) {
        int level = 0;
        if (isBowOrCrossbow(mainHand)) {
            level = Math.max(level, EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.ENDER_ARROW.get(), mainHand));
        }
        if (isBowOrCrossbow(offHand)) {
            level = Math.max(level, EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.ENDER_ARROW.get(), offHand));
        }
        return level;
    }

    private static boolean isBowOrCrossbow(ItemStack stack) {
        return stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem;
    }

    private static Vec3 getEntityFrontPosition(LivingEntity target) {
        Vec3 direction = target.getLookAngle().multiply(1.0, 0.0, 1.0);
        if (direction.lengthSqr() < 1.0E-4) {
            direction = new Vec3(0.0, 0.0, 1.0);
        } else {
            direction = direction.normalize();
        }
        return target.position().add(direction);
    }
}
