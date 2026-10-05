package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.entity.AnnihilationOrbEntity;
import com.github.emberstar1201.enchantmentex.item.ModItems;
import com.github.emberstar1201.enchantmentex.item.handler.EndStarHandler;
import com.github.emberstar1201.enchantmentex.util.AllyFilter;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ResidualScytheHandler {
    private static final String COMBO_KEY = "ResidualScytheCombo";
    private static final String LAST_HIT_KEY = "ResidualScytheLastHit";
    private static final String BLADE_DAMAGE_KEY = "ResidualScytheBladeDamage";
    private static final int MAX_COMBO_DAMAGE = 30;
    private static final long COMBO_WINDOW = 40L;
    private static final int BLADE_COOLDOWN_TICKS = 50;
    private static final String ENERGY_KEY = "ResidualScytheEnergy";
    private static final String ANNIHILATION_DAMAGE_KEY = "ResidualScytheAnnihilationDamage";
    private static final int MAX_ENERGY = 80;
    private static final int ORB_COST = 80;
    private static final int ORB_COOLDOWN_TICKS = 20;

    private ResidualScytheHandler() {
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (player.level().isClientSide() || stack.getItem() != ModItems.RESIDUAL_SCYTHE.get()) {
            return;
        }

        event.setCanceled(true);
        if (player.getCooldowns().isOnCooldown(ModItems.RESIDUAL_SCYTHE.get())) {
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
            return;
        }

        fireBlade(player);
        player.getCooldowns().addCooldown(ModItems.RESIDUAL_SCYTHE.get(), BLADE_COOLDOWN_TICKS);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) {
            return;
        }

        if (event.getSource().getDirectEntity() instanceof Player player
                && (player.getPersistentData().getBoolean(BLADE_DAMAGE_KEY)
                || player.getPersistentData().getBoolean(ANNIHILATION_DAMAGE_KEY))) {
            if (player.getPersistentData().getBoolean(BLADE_DAMAGE_KEY)
                    && event.getEntity().getType().getCategory() == MobCategory.MONSTER
                    && event.getEntity() != player
                    && !AllyFilter.isFriendly(event.getEntity())) {
                addEnergy(player.getMainHandItem(), 1 + player.getRandom().nextInt(5));
            }
            return;
        }

        ItemStack stack = attacker.getMainHandItem();
        if (stack.getItem() != ModItems.RESIDUAL_SCYTHE.get()) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (!target.isAlive() || target == attacker || AllyFilter.isFriendly(target)) {
            return;
        }

        if (target.getType().getCategory() == MobCategory.MONSTER && attacker instanceof Player player) {
            addEnergy(stack, 1 + player.getRandom().nextInt(5));
        }

        long now = attacker.level().getGameTime();
        long lastHit = stack.getOrCreateTag().getLong(LAST_HIT_KEY);
        int combo = now - lastHit <= COMBO_WINDOW
                ? stack.getOrCreateTag().getInt(COMBO_KEY)
                : 0;
        int damage = Math.min(MAX_COMBO_DAMAGE, 15 + combo);
        event.setAmount(EndStarHandler.applyDamageBonus(attacker, target, damage));
        stack.getOrCreateTag().putInt(COMBO_KEY, Math.min(MAX_COMBO_DAMAGE - 15, combo + 1));
        stack.getOrCreateTag().putLong(LAST_HIT_KEY, now);
    }

    public static void tryFireAnnihilationOrb(Player player) {
        if (player.level().isClientSide()) return;
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() != ModItems.RESIDUAL_SCYTHE.get()
                || getEnergy(stack) < ORB_COST
                || player.getCooldowns().isOnCooldown(ModItems.RESIDUAL_SCYTHE.get())) {
            return;
        }
        stack.getOrCreateTag().putInt(ENERGY_KEY, 0);
        player.getCooldowns().addCooldown(ModItems.RESIDUAL_SCYTHE.get(), ORB_COOLDOWN_TICKS);
        Vec3 direction = player.getLookAngle().normalize();
        AnnihilationOrbEntity orb = new AnnihilationOrbEntity(player.level(), player,
                player.getEyePosition().add(direction.scale(0.8D)), direction,
                getBaseWeaponDamage(player));
        player.level().addFreshEntity(orb);
    }

    /** 大招使用基础武器伤害的 8 倍，不读取连击计数，也不经过终界之星加成。 */
    private static float getBaseWeaponDamage(Player player) {
        double damage = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        return (float) (Math.max(1.0D, damage) * 8.0D);
    }

    public static void hurtByAnnihilation(LivingEntity owner, LivingEntity target, float baseDamage) {
        owner.getPersistentData().putBoolean(ANNIHILATION_DAMAGE_KEY, true);
        try {
            target.hurt(owner.level().damageSources().mobAttack(owner), baseDamage);
        } finally {
            owner.getPersistentData().remove(ANNIHILATION_DAMAGE_KEY);
        }
    }

    private static int getEnergy(ItemStack stack) {
        return Math.min(MAX_ENERGY, stack.getOrCreateTag().getInt(ENERGY_KEY));
    }

    private static void addEnergy(ItemStack stack, int amount) {
        stack.getOrCreateTag().putInt(ENERGY_KEY, Math.min(MAX_ENERGY, getEnergy(stack) + amount));
    }

    private static void fireBlade(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        Set<UUID> hitTargets = new HashSet<>();

        for (int i = 1; i <= 32; i++) {
            Vec3 point = start.add(direction.scale(i * 0.5D));
            level.sendParticles(
                    new DustParticleOptions(new Vector3f(0.18F, 0.02F, 0.02F), 1.0F),
                    point.x, point.y, point.z, 3, 0.05D, 0.05D, 0.05D, 0.01D);

            AABB area = new AABB(point, point).inflate(0.45D);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                    entity -> entity != player && entity.isAlive()
                            && !entity.isSpectator() && !AllyFilter.isFriendly(entity))) {
                if (!hitTargets.add(target.getUUID())) {
                    continue;
                }
                player.getPersistentData().putBoolean(BLADE_DAMAGE_KEY, true);
                try {
                    target.hurt(level.damageSources().playerAttack(player), 18.75F);
                } finally {
                    player.getPersistentData().remove(BLADE_DAMAGE_KEY);
                }
            }
        }
    }
}
