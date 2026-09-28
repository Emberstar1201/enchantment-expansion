package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.OceanStarConfig;
import com.github.emberstar1201.enchantmentex.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 「海洋之星」物品效果事件处理器
//
// 【被动效果】（仅玩家手持主手/副手海洋之星时生效，全部支持独立配置开关）
//   1. 免疫水下挖掘惩罚 + 免疫挖掘疲劳（PlayerTickEvent 移除挖掘疲劳）
//   2. 免疫水流减速（PlayerTickEvent：泳速提升 + 抵消水流推力 + 移除减速效果/修饰符）
//   3. 水下挖掘速度加成（PlayerEvent.BreakSpeed：恢复被惩罚削弱的速度并乘 1.25）
//   4. 水下不扣氧气 + 免疫溺水窒息伤害（PlayerTickEvent 保持满氧 + LivingHurtEvent 取消 DROWN 伤害）
//   5. 守卫者/远古守卫者中立化（LivingChangeTargetEvent：不主动锁定持有者，但被攻击后正常反击）
//
// 【核心机制说明】
//   原版水下挖掘惩罚：玩家眼睛浸水且无『水下速掘』附魔时挖掘速度 ÷5；
//   不在地面时再 ÷5。BreakSpeed 事件拿到的原速度已包含这些惩罚，
//   因此需要『反向乘以 5』恢复，再按配置放大 1.25 倍。
//   守卫者反击判定：若守卫者最近一次打伤它的实体就是持有者本人，则放行，
//   保证『被玩家攻击后会正常反击』。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class OceanStarHandler {

    // 水中泳速修饰符固定 UUID（瞬态修饰符，随环境增删）
    private static final UUID SWIM_SPEED_UUID =
            UUID.fromString("a1c3e5f7-9b0d-4e2f-8b1a-5c7d9f0e2b4a");
    private static final String SWIM_SPEED_NAME = "OceanStar Swim Speed Boost";

    // ========================================================================
    // 【PlayerTickEvent】手持检测 + 环境免疫（挖掘疲劳 / 水流减速 / 泳速提升）
    //   仅服务端处理（属性与状态效果以服务端为准，移除后会同步到客户端）。
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide()) return;

        // 仅当玩家手持海洋之星时才处理（主手或副手）
        if (!isHoldingOceanStar(player)) return;

        // ---------- 效果 1b：水下不扣氧气（保持氧气值满，仅水中生效） ----------
        // 原版每 tick 会按潜水时间扣除 airSupply，耗尽后开始受 DROWN 伤害。
        // 这里每 tick 重置为满氧，从根源上杜绝氧气耗尽。同时配合
        // onLivingHurt 中的 DROWN 伤害免疫作为双保险。
        if (OceanStarConfig.enableOxygenImmunity && player.isInWater()) {
            player.setAirSupply(player.getMaxAirSupply());
        }

        // ---------- 效果 1a：免疫挖掘疲劳（远古守卫者的『挖掘疲劳』） ----------
        if (OceanStarConfig.enableMiningFatigueImmunity
                && player.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            player.removeEffect(MobEffects.DIG_SLOWDOWN);
        }

        // ---------- 效果 2：免疫水流减速 ----------
        boolean inWater = player.isInWater();
        if (inWater && OceanStarConfig.enableWaterMovementImmunity) {
            // 2a. 移除『缓慢』状态效果
            if (player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }

            // 2b. 移除 MOVEMENT_SPEED 属性的所有负值修饰符（水中拖拽/灵魂沙等减速源）
            AttributeInstance moveSpeedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (moveSpeedAttr != null) {
                List<UUID> negativeMods = new ArrayList<>();
                for (AttributeModifier mod : moveSpeedAttr.getModifiers()) {
                    if (mod.getAmount() < 0) {
                        negativeMods.add(mod.getId());
                    }
                }
                for (UUID modId : negativeMods) {
                    moveSpeedAttr.removeModifier(modId);
                }
            }

            // 2c. 提升游泳速度（补偿水中拖拽，使水中移动接近陆地速度）
            AttributeInstance swimAttr = player.getAttribute(ForgeMod.SWIM_SPEED.get());
            if (swimAttr != null) {
                double multiplier = OceanStarConfig.swimSpeedMultiplier;
                AttributeModifier existing = swimAttr.getModifier(SWIM_SPEED_UUID);
                if (existing == null) {
                    swimAttr.addTransientModifier(new AttributeModifier(
                            SWIM_SPEED_UUID, SWIM_SPEED_NAME, multiplier,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));
                } else if (existing.getAmount() != multiplier) {
                    swimAttr.removeModifier(SWIM_SPEED_UUID);
                    swimAttr.addTransientModifier(new AttributeModifier(
                            SWIM_SPEED_UUID, SWIM_SPEED_NAME, multiplier,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));
                }
            }

            // 2d. 抵消水流推力：原版会在服务端给水中实体叠加 flow*0.014 的速度推力，
            //     这里每 tick 反向扣除，实现『不受水流方向和强度影响』。
            //     近似取玩家脚下方块的水流向量，对玩家而言精度足够。
            BlockPos pos = player.blockPosition();
            FluidState fluid = player.level().getFluidState(pos);
            Vec3 flow = fluid.getFlow(player.level(), pos);
            if (flow.lengthSqr() > 0.0D) {
                player.setDeltaMovement(player.getDeltaMovement().subtract(flow.scale(0.014D)));
            }
        } else {
            // 未在水中（或配置关闭）：确保瞬态泳速修饰符被移除
            AttributeInstance swimAttr = player.getAttribute(ForgeMod.SWIM_SPEED.get());
            if (swimAttr != null && swimAttr.getModifier(SWIM_SPEED_UUID) != null) {
                swimAttr.removeModifier(SWIM_SPEED_UUID);
            }
        }
    }

    // ========================================================================
    // 【BlockEvent.BreakSpeed】免疫水下挖掘惩罚 + 水下挖掘速度 1.25 倍
    //
    //   原版流程（Player.getDigSpeed）：
    //     speed /= 5  （眼睛浸水且无水下速掘附魔时）
    //     speed /= 5  （不在地面时）
    //   → 反向 ×5 先恢复，再按配置放大水下倍率（默认 1.25）。
    // ========================================================================
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player == null || !isHoldingOceanStar(player)) return;

        boolean inWater = player.isEyeInFluid(FluidTags.WATER);
        boolean onGround = player.onGround();

        float speed = event.getOriginalSpeed();

        // 效果 1b：恢复水下挖掘惩罚（原版 ÷5 → ×5 抵消）
        if (OceanStarConfig.enableMiningPenaltyImmunity && inWater
                && !EnchantmentHelper.hasAquaAffinity(player)) {
            speed *= 5.0F;
        }
        // 恢复『不在地面』惩罚（原版 ÷5 → ×5 抵消）
        if (OceanStarConfig.enableMiningPenaltyImmunity && !onGround) {
            speed *= 5.0F;
        }

        // 效果 3：水下挖掘速度加成（正常陆地挖掘速度的 1.25 倍）
        if (inWater && OceanStarConfig.enableUnderwaterMiningBoost) {
            speed *= (float) OceanStarConfig.underwaterMiningBoostMultiplier;
        }

        event.setNewSpeed(speed);
    }

    // ========================================================================
    // 【LivingHurtEvent】免疫溺水窒息伤害
    //   持有海洋之星时，DROWN（溺水/窒息）伤害完全无效。
    //   与 onPlayerTick 中的"水下不扣氧气"协同，构成双保险。
    // ========================================================================
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!OceanStarConfig.enableOxygenImmunity) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!isHoldingOceanStar(player)) return;

        // DROWN = 溺水伤害（氧气耗尽后每 tick 受到的窒息伤害）
        if (event.getSource().is(DamageTypes.DROWN)) {
            event.setCanceled(true);
        }
    }

    // ========================================================================
    // 【LivingChangeTargetEvent】守卫者/远古守卫者中立化
    //   不主动攻击海洋之星持有者；但若玩家进攻了守卫者（lastHurtByMob == 玩家），
    //   守卫者可以正常回击（反击不受中立影响）。
    // ========================================================================
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!OceanStarConfig.enableGuardianNeutral) return;

        // 仅处理守卫者（远古守卫者是守卫者的子类，自动包含）
        if (!(event.getEntity() instanceof Guardian guardian)) return;

        // 新目标必须是海洋之星持有者
        if (!(event.getNewTarget() instanceof Player player)) return;
        if (!isHoldingOceanStar(player)) return;

        // 反击判定：守卫者最近被打它的实体如果正是持有者本人，允许反击
        if (guardian.getLastHurtByMob() == player) return;

        // 中立化：清除对该玩家的索敌
        event.setNewTarget(null);
        event.setCanceled(true);
    }

    // ========================================================================
    // 工具方法：检测玩家是否手持海洋之星，或穿戴嵌入海洋之星的盔甲
    // ========================================================================
    private static boolean isHoldingOceanStar(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        
        // 检查手持海洋之星
        if (mainHand.is(ModItems.OCEAN_STAR.get()) || offHand.is(ModItems.OCEAN_STAR.get())) {
            return true;
        }
        
        // 检查穿戴的盔甲中是否有嵌入的海洋之星
        for (ItemStack armorPiece : player.getArmorSlots()) {
            if (!armorPiece.isEmpty() 
                    && armorPiece.hasTag() 
                    && armorPiece.getTag().contains("EmbeddedStar")) {
                String embeddedStar = armorPiece.getTag().getString("EmbeddedStar");
                if ("ocean_star".equals(embeddedStar)) {
                    return true;
                }
            }
        }
        
        return false;
    }
}