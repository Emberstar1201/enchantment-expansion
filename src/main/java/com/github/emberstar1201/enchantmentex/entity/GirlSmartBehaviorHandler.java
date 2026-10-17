package com.github.emberstar1201.enchantmentex.entity;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 少女共用的同伴协同与装备拾取行为。 */
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GirlSmartBehaviorHandler {
    private static final double ALERT_RANGE = 12.0D;
    private static final double ITEM_RANGE = 3.0D;
    private static final int ITEM_SCAN_INTERVAL = 10;

    private GirlSmartBehaviorHandler() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide
                || !(event.getEntity() instanceof Mob mob)
                || !isGirl(mob)
                || mob.tickCount % ITEM_SCAN_INTERVAL != 0) {
            return;
        }

        shareThreat(mob);
        pickUpUsefulEquipment(mob);
    }

    /** 附近少女共享自己最近受到的敌人，形成简单的呼救和集火。 */
    private static void shareThreat(Mob girl) {
        LivingEntity threat = girl.getLastHurtByMob();
        if (!isValidThreat(threat)) {
            return;
        }

        for (LivingEntity ally : girl.level().getEntitiesOfClass(
                LivingEntity.class, girl.getBoundingBox().inflate(ALERT_RANGE),
                GirlSmartBehaviorHandler::isGirl)) {
            if (ally == girl || !(ally instanceof Mob allyMob)
                    || allyMob.getTarget() != null) {
                continue;
            }
            allyMob.setTarget(threat);
        }
    }

    /** 只拾取空装备槽，避免自动替换玩家精心装备的武器和护甲。 */
    private static void pickUpUsefulEquipment(Mob girl) {
        for (ItemEntity itemEntity : girl.level().getEntitiesOfClass(
                ItemEntity.class, girl.getBoundingBox().inflate(ITEM_RANGE),
                item -> !item.getItem().isEmpty())) {
            ItemStack dropped = itemEntity.getItem();
            EquipmentSlot slot = LivingEntity.getEquipmentSlotForItem(dropped);
            if (slot.getType() == EquipmentSlot.Type.HAND
                    && !isWeapon(dropped)) {
                continue;
            }
            if (!girl.getItemBySlot(slot).isEmpty()) {
                continue;
            }

            ItemStack equipped = dropped.split(1);
            girl.setItemSlot(slot, equipped);
            girl.setDropChance(slot, 1.0F);
            itemEntity.setItem(dropped);
            if (dropped.isEmpty()) {
                itemEntity.discard();
            }
            return;
        }
    }

    private static boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.SwordItem
                || stack.getItem() instanceof net.minecraft.world.item.AxeItem
                || stack.getItem() instanceof net.minecraft.world.item.BowItem
                || stack.getItem() instanceof net.minecraft.world.item.TridentItem
                || stack.getItem() instanceof net.minecraft.world.item.ShieldItem;
    }

    private static boolean isValidThreat(LivingEntity entity) {
        return entity != null && entity.isAlive() && entity instanceof Enemy
                && !isGirl(entity) && !(entity instanceof net.minecraft.world.entity.player.Player);
    }

    private static boolean isGirl(LivingEntity entity) {
        return entity instanceof ZombieGirlEntity
                || entity instanceof DrownedGirlEntity;
    }
}
