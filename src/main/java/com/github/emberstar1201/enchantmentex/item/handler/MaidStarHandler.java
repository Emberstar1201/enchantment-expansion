package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.OceanStarConfig;
import com.github.emberstar1201.enchantmentex.item.ModItems;
import com.github.emberstar1201.enchantmentex.util.TLMSafe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【星星×车万女仆联动】事件处理器
//
// 当玩家安装车万女仆（TouhouLittleMaid）后，女仆可以装备所有星星：
//   · 放入女仆饰品栏（BaubleItemInventory）—— 需要 MaidStarBaubleExtension
//     先通过官方扩展点把星星注册进 BaubleManager，否则物品根本插不进去
//   · 拿在主手 / 副手
// 以上三种位置都会在本类中被识别并生效：
//   - 终界之星：先限制单次伤害至 10 点，再按配置减伤 + 末影系生物中立
//   - 海洋之星：水下氧气免疫 + 免疫挖掘疲劳 + 水流免疫（泳速提升）
//                + 免疫溺水伤害 + 守卫者中立
//   - 生命之星：生命上限 +30 + 回血加速（摘下后自动移除加成）
//   - 虚空之星：免疫摔落伤害 + 免疫虚空伤害
//   - 星辉之星：夜间移速 +20% + 夜视
//   - 晨曦之星：晨光积累 → 满层爆发「晨曦」（独立逻辑，见 MaidDawnStarHandler；
//               它需要 LivingDeathEvent / LivingHurtEvent 与自定义状态机，
//               故不并入本类的 ServerTick 分发）
//
// 【位置识别】
//   统一走 TLMSafe：
//     · 主手/副手 → LivingEntity#getMainHandItem / getOffhandItem
//     · 饰品栏   → 反射 EntityMaid#getMaidBauble() 后遍历 ItemStackHandler 槽位
//   未安装车万女仆时 TLMSafe 全部返回安全默认值，本类完全不生效。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MaidStarHandler {

    // 属性修饰符 UUID（与玩家侧 Handler 保持一致，便于统一识别）
    private static final UUID LIFE_STAR_MAX_HEALTH_UUID =
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID STARLIGHT_NIGHT_SPEED_UUID =
            UUID.fromString("b2d4f6a8-1c3e-4d5f-9a7b-2e4c6f8a0b1d");
    private static final UUID OCEAN_STAR_SWIM_SPEED_UUID =
            UUID.fromString("a1c3e5f7-9b0d-4e2f-8b1a-5c7d9f0e2b4a");
    private static final String OCEAN_STAR_SWIM_SPEED_NAME = "OceanStar Swim Speed Boost";

    // 女仆回血计时器（每女仆一个）
    private static final Map<UUID, Integer> MAID_REGEN_TIMER = new HashMap<>();

    // ========================================================================
    // Tick 事件：为女仆应用持续性效果（生命之星、星辉之星、海洋之星）
    // ========================================================================
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // 遍历所有加载的女仆，应用星星效果
        for (net.minecraft.server.level.ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (!(e instanceof LivingEntity entity)) {
                    continue;
                }

                // 只处理女仆（按实体注册名命名空间识别，兼容女仆的子类/变体）
                if (!TLMSafe.isTouhouMaid(entity)) {
                    continue;
                }

                // 生命之星：+30 生命值上限 + 回血加速
                if (isMaidUsingStar(entity, ModItems.LIFE_STAR.get())) {
                    applyLifeStarEffects(entity);
                } else {
                    clearLifeStarEffects(entity);
                }

                // 星辉之星：夜间移速 +20% + 夜视
                if (isMaidUsingStar(entity, ModItems.STARLIGHT_STAR.get())) {
                    applyStarlightStarEffects(entity);
                } else {
                    clearStarlightStarEffects(entity);
                }

                // 海洋之星：水下环境免疫
                if (isMaidUsingStar(entity, ModItems.OCEAN_STAR.get())) {
                    applyOceanStarEffects(entity);
                } else {
                    clearOceanStarSwimSpeed(entity);
                }
            }
        }
    }

    private static void applyLifeStarEffects(LivingEntity entity) {
        AttributeInstance maxHealthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null && maxHealthAttr.getModifier(LIFE_STAR_MAX_HEALTH_UUID) == null) {
            maxHealthAttr.addPermanentModifier(new AttributeModifier(
                    LIFE_STAR_MAX_HEALTH_UUID, "LifeStar Max Health", 30.0,
                    AttributeModifier.Operation.ADDITION));
        }

        // 回血加速：每 5 tick 回复 1.0 点
        // （女仆没有原版饥饿值系统，无法复刻玩家的 FoodData.tick 节奏，
        //   因此用一个独立计时器给出稳定的"加速回血"表现）
        UUID maidUUID = entity.getUUID();
        int timer = MAID_REGEN_TIMER.getOrDefault(maidUUID, 0);
        if (timer <= 0 && entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(1.0f);
            MAID_REGEN_TIMER.put(maidUUID, 5);
        } else {
            MAID_REGEN_TIMER.put(maidUUID, timer - 1);
        }
    }

    // 摘下生命之星：移除上限加成（否则 +30 生命会永久残留）
    private static void clearLifeStarEffects(LivingEntity entity) {
        AttributeInstance maxHealthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null && maxHealthAttr.getModifier(LIFE_STAR_MAX_HEALTH_UUID) != null) {
            maxHealthAttr.removeModifier(LIFE_STAR_MAX_HEALTH_UUID);
            // 当前生命值超过新上限时，压回上限（与原版属性移除逻辑保持一致）
            if (entity.getHealth() > maxHealthAttr.getValue()) {
                entity.setHealth((float) maxHealthAttr.getValue());
            }
        }
        MAID_REGEN_TIMER.remove(entity.getUUID());
    }

    private static void applyStarlightStarEffects(LivingEntity entity) {
        boolean isNight = !entity.level().isDay();

        AttributeInstance speedAttr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            if (isNight && speedAttr.getModifier(STARLIGHT_NIGHT_SPEED_UUID) == null) {
                speedAttr.addTransientModifier(new AttributeModifier(
                        STARLIGHT_NIGHT_SPEED_UUID, "Starlight Night Speed", 0.20D,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            } else if (!isNight && speedAttr.getModifier(STARLIGHT_NIGHT_SPEED_UUID) != null) {
                speedAttr.removeModifier(STARLIGHT_NIGHT_SPEED_UUID);
            }
        }

        // 夜视效果
        if (isNight) {
            MobEffectInstance current = entity.getEffect(MobEffects.NIGHT_VISION);
            if (current == null || current.getDuration() <= 220) {
                entity.addEffect(new MobEffectInstance(
                        MobEffects.NIGHT_VISION, 300, 0, false, false, true));
            }
        }
    }

    private static void clearStarlightStarEffects(LivingEntity entity) {
        AttributeInstance speedAttr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null && speedAttr.getModifier(STARLIGHT_NIGHT_SPEED_UUID) != null) {
            speedAttr.removeModifier(STARLIGHT_NIGHT_SPEED_UUID);
        }
        // 夜视不主动移除：让它按剩余时长自然结束，避免每次摘下星就闪一下
    }

    // ========================================================================
    // 海洋之星：水下环境免疫（对应玩家侧 OceanStarHandler 的 PlayerTickEvent）
    // ========================================================================
    private static void applyOceanStarEffects(LivingEntity entity) {
        // 氧气免疫：水中每 tick 回满氧气，从根源杜绝溺水
        if (OceanStarConfig.enableOxygenImmunity && entity.isInWater()) {
            entity.setAirSupply(entity.getMaxAirSupply());
        }

        // 免疫挖掘疲劳（远古守卫者的负面效果）
        if (OceanStarConfig.enableMiningFatigueImmunity
                && entity.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            entity.removeEffect(MobEffects.DIG_SLOWDOWN);
        }

        if (!entity.isInWater() || !OceanStarConfig.enableWaterMovementImmunity) {
            clearOceanStarSwimSpeed(entity);
            return;
        }

        // 水流免疫 a：移除「缓慢」效果
        if (entity.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
            entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }

        // 水流免疫 b：移除移速属性上的负向修饰符（水中拖拽等减速源）
        AttributeInstance moveSpeedAttr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
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

        // 水流免疫 c：提升泳速（与玩家侧共用同一 UUID 与倍率配置）
        AttributeInstance swimAttr = entity.getAttribute(ForgeMod.SWIM_SPEED.get());
        if (swimAttr != null) {
            double multiplier = OceanStarConfig.swimSpeedMultiplier;
            AttributeModifier existing = swimAttr.getModifier(OCEAN_STAR_SWIM_SPEED_UUID);
            if (existing == null) {
                swimAttr.addTransientModifier(new AttributeModifier(
                        OCEAN_STAR_SWIM_SPEED_UUID, OCEAN_STAR_SWIM_SPEED_NAME, multiplier,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            } else if (existing.getAmount() != multiplier) {
                // 玩家在配置界面改过倍率：重建修饰符以套用新值
                swimAttr.removeModifier(OCEAN_STAR_SWIM_SPEED_UUID);
                swimAttr.addTransientModifier(new AttributeModifier(
                        OCEAN_STAR_SWIM_SPEED_UUID, OCEAN_STAR_SWIM_SPEED_NAME, multiplier,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }

        // 水流免疫 d：抵消水流推力
        //   原版对水中的实体叠加 flow*0.014 的速度推力，这里每 tick 反向扣除；
        //   注意是"扣除外力"而非"追加移动"，因此不会干扰女仆的寻路。
        BlockPos pos = entity.blockPosition();
        FluidState fluid = entity.level().getFluidState(pos);
        Vec3 flow = fluid.getFlow(entity.level(), pos);
        if (flow.lengthSqr() > 0.0D) {
            entity.setDeltaMovement(entity.getDeltaMovement().subtract(flow.scale(0.014D)));
        }
    }

    // 离开水域 / 摘下海洋之星：移除瞬态泳速修饰符
    private static void clearOceanStarSwimSpeed(LivingEntity entity) {
        AttributeInstance swimAttr = entity.getAttribute(ForgeMod.SWIM_SPEED.get());
        if (swimAttr != null && swimAttr.getModifier(OCEAN_STAR_SWIM_SPEED_UUID) != null) {
            swimAttr.removeModifier(OCEAN_STAR_SWIM_SPEED_UUID);
        }
    }

    // ========================================================================
    // 伤害减免事件
    //
    // 终界之星：先限制单次伤害至 10 点，再按配置减伤（默认 80%）
    // 虚空之星：免疫虚空伤害
    // 海洋之星：免疫溺水窒息伤害
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }

        // 终界之星：先限伤至 10 点，再减伤
        if (isMaidUsingStar(entity, ModItems.END_STAR.get())) {
            float originalAmount = event.getAmount();
            float cappedAmount = Math.min(originalAmount, 10.0f);
            double reduction = Math.min(Config.endStarDamageReductionPercent, 0.99);
            float newAmount = Math.max((float) (cappedAmount * (1 - reduction)), 0.5f);
            event.setAmount(newAmount);
            return;
        }

        // 虚空之星：免疫虚空伤害
        if (isMaidUsingStar(entity, ModItems.VOID_STAR.get())
                && event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            event.setCanceled(true);
            return;
        }

        // 海洋之星：免疫溺水伤害
        if (isMaidUsingStar(entity, ModItems.OCEAN_STAR.get())
                && event.getSource().is(DamageTypes.DROWN)) {
            event.setCanceled(true);
        }
    }

    // ========================================================================
    // 摔落伤害事件：虚空之星免疫摔落伤害
    // ========================================================================
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }

        if (isMaidUsingStar(entity, ModItems.VOID_STAR.get())) {
            event.setDamageMultiplier(0.0F);
            event.setCanceled(true);
        }
    }

    // ========================================================================
    // 伤害攻击事件：末影系生物对佩戴终界之星的女仆造成的直接伤害被拦截
    // （末影龙不走普通目标选择，必须在这里拦截，实现"绝对中立"）
    // ========================================================================
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }

        if (isMaidUsingStar(entity, ModItems.END_STAR.get())
                && isEndNeutralMob(event.getSource().getEntity())) {
            event.setCanceled(true);
        }
    }

    // ========================================================================
    // 目标变更事件：末影系生物 / 守卫者无法主动锁定佩戴对应星星的女仆
    // ========================================================================
    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewTarget();
        if (newTarget == null || newTarget.level() == null || newTarget.level().isClientSide()) {
            return;
        }
        if (!TLMSafe.isTouhouMaid(newTarget)) {
            return;
        }

        Entity attacker = event.getEntity();

        // 终界之星：末影系生物中立
        if (isEndNeutralMob(attacker)
                && isMaidUsingStar(newTarget, ModItems.END_STAR.get())) {
            event.setNewTarget(null);
            event.setCanceled(true);
            return;
        }

        // 海洋之星：守卫者中立（但被女仆攻击过则放行，保证会正常反击）
        if (OceanStarConfig.enableGuardianNeutral
                && attacker instanceof Guardian guardian
                && guardian.getLastHurtByMob() != newTarget
                && isMaidUsingStar(newTarget, ModItems.OCEAN_STAR.get())) {
            event.setNewTarget(null);
            event.setCanceled(true);
        }
    }

    // ========================================================================
    // 工具方法：判断女仆是否正在使用指定星星
    //
    // 覆盖三种位置（任一命中即视为生效）：
    //   1. 主手
    //   2. 副手
    //   3. 饰品栏（车万女仆自定义的 MaidBaubleInventory）
    // ========================================================================
    private static boolean isMaidUsingStar(LivingEntity entity, Item star) {
        if (entity == null || star == null) {
            return false;
        }
        // 未安装车万女仆 / 非女仆实体：直接返回，避免无谓的反射开销
        if (!TLMSafe.isTouhouMaid(entity)) {
            return false;
        }

        if (entity.getMainHandItem().is(star) || entity.getOffhandItem().is(star)) {
            return true;
        }

        for (ItemStack stack : TLMSafe.collectMaidBaubles(entity)) {
            if (!stack.isEmpty() && stack.is(star)) {
                return true;
            }
        }
        return false;
    }

    // ========================================================================
    // 工具方法：判断实体是否为末影系敌对生物
    // ========================================================================
    private static boolean isEndNeutralMob(Entity entity) {
        return entity instanceof EnderMan
                || entity instanceof Endermite
                || entity instanceof Shulker
                || entity instanceof EnderDragon;
    }
}
