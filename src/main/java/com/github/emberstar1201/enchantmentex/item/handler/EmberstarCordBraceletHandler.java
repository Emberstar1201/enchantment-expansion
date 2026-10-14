package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.item.ModItems;
import com.github.emberstar1201.enchantmentex.util.CuriosCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

/**
 * Emberstar Cord Bracelet 的服务端效果处理器。
 *
 * 这里仅参考 IMBlocker 处理输入法冲突时的“声明位置清晰、逻辑隔离”思路，
 * 没有复制 IMBlocker 的代码，也没有引入任何输入法实现。
 */
@Mod.EventBusSubscriber(modid = MODID)
public final class EmberstarCordBraceletHandler {
    private static final UUID FOLLOW_RANGE_MODIFIER_ID = UUID.fromString("6dbd4d0b-6951-4a73-9c39-b65f63e88e01");
    private static final UUID ATTACK_SPEED_MODIFIER_ID = UUID.fromString("a9b21d4e-cfd2-4a9b-a7b0-d6c31de8df24");
    private static final String FOLLOW_RANGE_MODIFIER_NAME = "emberstar_cord_bracelet_detection_range";
    private static final String ATTACK_SPEED_MODIFIER_NAME = "emberstar_cord_bracelet_night_attack_speed";
    private static final double BASE_REDUCTION = 0.275D;
    private static final double MAX_REDUCTION = 0.75D;
    private static final double GROWTH_TICKS = 240000.0D;
    private static final double VOID_CONVERSION_CHANCE = 0.05D;
    private static final ThreadLocal<Boolean> VOID_DAMAGE_GUARD = ThreadLocal.withInitial(() -> false);

    private EmberstarCordBraceletHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }

        Player player = event.player;
        boolean equipped = isEquipped(player);
        // “火堆外的夜晚”是显示名称，装备后始终启用对应战斗效果。
        boolean nightPower = equipped;

        updateAttackSpeed(player, nightPower);
        updateDetectionRange(player, equipped);
    }

    /** 怪物的 FOLLOW_RANGE 直接决定 AI 搜索距离，比只清理目标更稳定。 */
    private static void updateDetectionRange(Player player, boolean equipped) {
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64.0D))) {
            AttributeInstance attribute = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (attribute == null) {
                continue;
            }

            AttributeModifier old = attribute.getModifier(FOLLOW_RANGE_MODIFIER_ID);
            if (equipped) {
                if (old == null) {
                    attribute.addTransientModifier(new AttributeModifier(
                            FOLLOW_RANGE_MODIFIER_ID, FOLLOW_RANGE_MODIFIER_NAME,
                            -0.75D, AttributeModifier.Operation.MULTIPLY_TOTAL));
                }
                if (mob.getTarget() == player && mob.distanceToSqr(player) > attribute.getValue() * attribute.getValue()) {
                    mob.setTarget(null);
                }
            } else if (old != null) {
                attribute.removeModifier(FOLLOW_RANGE_MODIFIER_ID);
            }
        }
    }

    /** 夜间攻速翻倍等价于攻击冷却时间缩短 50%。 */
    private static void updateAttackSpeed(Player player, boolean active) {
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed == null) {
            return;
        }

        AttributeModifier old = attackSpeed.getModifier(ATTACK_SPEED_MODIFIER_ID);
        if (active && old == null) {
            attackSpeed.addTransientModifier(new AttributeModifier(
                    ATTACK_SPEED_MODIFIER_ID, ATTACK_SPEED_MODIFIER_NAME,
                    1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!active && old != null) {
            attackSpeed.removeModifier(ATTACK_SPEED_MODIFIER_ID);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof Player player && isEquipped(player)
                && event.getEntity() instanceof Mob mob) {
            AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (followRange != null && mob.distanceToSqr(player) > followRange.getValue() * followRange.getValue()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player player && isEquipped(player)) {
            double progress = Math.min(1.0D, player.level().getGameTime() / GROWTH_TICKS);
            double reduction = BASE_REDUCTION + (MAX_REDUCTION - BASE_REDUCTION) * progress;
            event.setAmount((float) (event.getAmount() * (1.0D - reduction)));
        }

        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)
                || !isEquipped(attacker) || attacker == victim) {
            return;
        }

        // 装备后的伤害倍率无条件生效；虚空转化只改变伤害类型，不跳过倍率。
        if (isNightPowerActive(attacker)) {
            event.setAmount(event.getAmount() * 3.5F);
        }

        // 虚空转化是独立的 5% 命中效果，不附带夜晚或营火条件。
        if (attacker.getRandom().nextDouble() < VOID_CONVERSION_CHANCE
                && !VOID_DAMAGE_GUARD.get()) {
            float amount = event.getAmount();
            event.setCanceled(true);
            VOID_DAMAGE_GUARD.set(true);
            try {
                // 取消原伤害后立刻用虚空伤害重放，虚空伤害不经过护甲减免。
                victim.hurt(victim.level().damageSources().fellOutOfWorld(), amount);
            } finally {
                VOID_DAMAGE_GUARD.set(false);
            }
            return;
        }

    }

    private static boolean isNightPowerActive(LivingEntity entity) {
        return isEquipped(entity);
    }

    private static boolean hasNearbyLitCampfire(LivingEntity entity) {
        BlockPos center = entity.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -4, -8), center.offset(8, 4, 8))) {
            var state = entity.level().getBlockState(pos);
            if (state.is(BlockTags.CAMPFIRES) && state.hasProperty(CampfireBlock.LIT)
                    && state.getValue(CampfireBlock.LIT)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEquipped(LivingEntity entity) {
        if (entity instanceof Player player) {
            // 需求约定为副手或 Curios 饰品栏；主手持有不视为装备。
            ItemStack offhand = player.getOffhandItem();
            return offhand.is(ModItems.EMBERSTAR_CORD_BRACELET.get())
                    || CuriosCompat.isWearing(player, ModItems.EMBERSTAR_CORD_BRACELET.get());
        }
        return false;
    }
}
