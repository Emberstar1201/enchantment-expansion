package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.item.ModItems;
import com.github.emberstar1201.enchantmentex.util.CuriosCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

/** 荆棘之冠：将玩家造成的最终伤害再次以不可减免的自定义伤害施加。 */
@Mod.EventBusSubscriber(modid = MODID)
public final class ThornsCrownHandler {
    private static final UUID ARMOR_ID = UUID.fromString("d5cfbd6f-0d25-4c68-a08c-3f5e39f4c7a1");
    private static final UUID TOUGHNESS_ID = UUID.fromString("3a1f646d-2c5e-4c76-9eb6-b87ea8e5b3b1");
    private static final String ARMOR_NAME = "thorns_crown_armor";
    private static final String TOUGHNESS_NAME = "thorns_crown_toughness";
    private static final ThreadLocal<Boolean> DAMAGE_GUARD = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final Map<LivingEntity, PendingDamage> PENDING_DAMAGE = new WeakHashMap<>();

    private ThornsCrownHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide()) {
            updateArmor(event.player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (DAMAGE_GUARD.get() || event.getEntity().level().isClientSide()) {
            return;
        }

        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Player attacker)
                || attacker == target
                || !isEquipped(attacker)) {
            return;
        }

        float finalAmount = event.getAmount();
        if (finalAmount <= 0.0F) {
            return;
        }

        event.setCanceled(true);
        PendingDamage pending = PENDING_DAMAGE.computeIfAbsent(target, ignored -> new PendingDamage());
        pending.amount += finalAmount;
        pending.ticksSinceLastSettlement = Math.min(pending.ticksSinceLastSettlement,
                Math.max(0, Config.thornsCrownSettlementIntervalTicks - 1));

        // 原始伤害本会杀死目标时，当前累计伤害必须立即结算。
        if (target.getHealth() - finalAmount <= 0.0F) {
            settle(target);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        LivingEntity target = event.getEntity();
        PendingDamage pending = PENDING_DAMAGE.get(target);
        if (pending == null) return;
        pending.ticksSinceLastSettlement++;
        if (pending.ticksSinceLastSettlement >= Math.max(1, Config.thornsCrownSettlementIntervalTicks)) {
            settle(target);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        PENDING_DAMAGE.remove(event.getEntity());
    }

    private static void settle(LivingEntity target) {
        PendingDamage pending = PENDING_DAMAGE.remove(target);
        if (pending == null || pending.amount <= 0.0F || !target.isAlive()) return;
        ResourceKey<DamageType> atonement = ResourceKey.create(Registries.DAMAGE_TYPE,
                new ResourceLocation(MODID, "atonement"));
        DamageSource damageSource = new DamageSource(
                target.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(atonement));
        DAMAGE_GUARD.set(true);
        try {
            target.hurt(damageSource, pending.amount);
        } finally {
            DAMAGE_GUARD.set(false);
        }
    }

    private static final class PendingDamage {
        private float amount;
        private int ticksSinceLastSettlement;
    }

    public static boolean isEquipped(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        ItemStack offhand = player.getOffhandItem();
        return offhand.is(ModItems.THORNS_CROWN.get())
                || CuriosCompat.isWearing(player, ModItems.THORNS_CROWN.get());
    }

    public static void updateArmor(Player player) {
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        AttributeInstance toughness = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (armor == null || toughness == null) {
            return;
        }

        if (isEquipped(player)) {
            if (armor.getModifier(ARMOR_ID) == null) {
                armor.addTransientModifier(new AttributeModifier(
                        ARMOR_ID, ARMOR_NAME, 4.0D, AttributeModifier.Operation.ADDITION));
            }
            if (toughness.getModifier(TOUGHNESS_ID) == null) {
                toughness.addTransientModifier(new AttributeModifier(
                        TOUGHNESS_ID, TOUGHNESS_NAME, 4.0D, AttributeModifier.Operation.ADDITION));
            }
        } else {
            if (armor.getModifier(ARMOR_ID) != null) armor.removeModifier(ARMOR_ID);
            if (toughness.getModifier(TOUGHNESS_ID) != null) toughness.removeModifier(TOUGHNESS_ID);
        }
    }
}
