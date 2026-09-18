package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.enchantment.ModEnchantments;
import com.github.emberstar1201.enchantmentex.util.TLMSafe;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【晨曦之星 × 车万女仆】事件处理器 —— 女仆独立逻辑
//
// 【为什么是独立逻辑，而不是复用 DawnStarHandler】
//   玩家侧的晨曦之星逻辑挂在 TickEvent.PlayerTickEvent 上，而车万女仆是
//   Mob（EntityMaid），根本收不到 PlayerTickEvent。因此女仆必须换用
//   TickEvent.ServerTickEvent + level#getAllEntities 遍历（与 MaidStarHandler 同款），
//   并单独处理「受伤加成 / 击杀积累」两条事件分支。
//
// 【为什么状态数据可以直接沿用 DawnStarData】
//   DawnStarData 的参数已是 LivingEntity：女仆同样是 LivingEntity，Forge 也为其
//   提供了 getPersistentData()。于是晨光层数 / 晨曦剩余 tick / 冷却 tick 都存在
//   女仆自己的 PersistentData 里，与玩家完全隔离、互不干扰。
//
// 【效果（与玩家侧完全一致）】
//   1. 击杀敌对生物积累晨光 +0.25；主手武器带拂晓附魔则 +2，上限 10 层
//   2. 满 10 层自动进入「晨曦」180 秒：伤害 ×1.25、暴击率 +10%、
//      移速 +10%、每 2 秒回复 2.5 点生命
//   3. 「晨曦」结束后清空晨光，进入 90 秒冷却
//
// 【持有判定】
//   主手 / 副手 / 饰品栏（MaidBaubleInventory）/ 嵌入晨曦之星的盔甲，
//   全部交给 DawnStarData#isHoldingOrWearingDawnStar 统一判断（内部走 TLMSafe）。
//
// 【软前置】
//   全程只经 TLMSafe 访问车万女仆，不 import 任何 touhou_little_maid 包下的类；
//   未安装车万女仆时 isTouhouMaid 恒为 false，本类等同空转。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MaidDawnStarHandler {

    /** 女仆「晨曦」移速修饰符固定 UUID（与玩家侧的修饰符分开，避免互相干扰） */
    private static final UUID MAID_DAWN_SPEED_UUID =
            UUID.fromString("d4f6a8b0-2c4e-4a6c-8e0f-1b3d5f7a9c2e");

    // ========================================================================
    // 事件1：LivingDeathEvent → 女仆击杀敌对生物积累晨光
    // ========================================================================
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        DamageSource source = event.getSource();
        if (source == null) return;

        Entity attacker = source.getEntity();
        // 玩家侧由 DawnStarHandler 处理；这里只认「非玩家」的 LivingEntity，再筛女仆
        if (!(attacker instanceof LivingEntity maid) || maid instanceof Player) return;
        if (maid.level().isClientSide()) return;
        if (!TLMSafe.isTouhouMaid(maid)) return;

        LivingEntity victim = event.getEntity();
        if (victim instanceof Player) return;                       // PVP 不积累
        if (!DawnStarData.isHoldingOrWearingDawnStar(maid)) return;
        if (!isHostile(victim)) return;
        // 触发「晨曦」后（激活中 + 随后的冷却期）不再叠加晨光
        if (DawnStarData.isDawnActive(maid) || DawnStarData.getCooldownTicks(maid) > 0) return;

        // 拂晓联动：主手武器带拂晓附魔 → +2 层，否则 +0.25 层
        boolean dawnWeapon = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DAWN.get(), maid.getMainHandItem()) > 0;
        double gain = dawnWeapon
                ? DawnStarData.DAWN_KILL_CHARGE_GAIN
                : DawnStarData.KILL_CHARGE_GAIN;

        DawnStarData.addCharge(maid, gain);
        DawnStarData.refreshLore(maid);
    }

    // ========================================================================
    // 事件2：LivingHurtEvent → 女仆「晨曦」期间 伤害 ×1.25 + 暴击率 +10%
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity maid) || maid instanceof Player) return;
        if (maid.level().isClientSide()) return;
        if (!TLMSafe.isTouhouMaid(maid)) return;
        if (!DawnStarData.isDawnActive(maid)) return;
        // 收起晨曦之星后不再享受加成
        if (!DawnStarData.isHoldingOrWearingDawnStar(maid)) return;

        float amount = event.getAmount() * DawnStarData.DAWN_DAMAGE_MULTIPLIER;

        // 暴击率 +10%：命中时按原版暴击倍率结算
        if (maid.level().random.nextDouble() < DawnStarData.DAWN_CRIT_BONUS) {
            amount *= DawnStarData.DAWN_CRIT_MULTIPLIER;

            LivingEntity target = event.getEntity();
            if (target.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                        20, 0.5, 0.5, 0.5, 0.05);
            }
        }

        event.setAmount(amount);
    }

    // ========================================================================
    // 事件3：ServerTickEvent → 女仆状态机
    //   女仆没有 PlayerTickEvent，只能在这里遍历所有维度的女仆逐个推进状态。
    //   （未安装车万女仆时 getAllEntities 里没有任何女仆，循环体恒不命中）
    // ========================================================================
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (!(e instanceof LivingEntity maid)) continue;
                // 只处理女仆（按实体注册名命名空间识别，兼容女仆的子类/变体）
                if (!TLMSafe.isTouhouMaid(maid)) continue;
                tickMaid(maid);
            }
        }
    }

    private static void tickMaid(LivingEntity maid) {
        // ---------- 快速路径（性能关键） ----------
        // 绝大多数女仆身上并没有晨曦之星，先做廉价判定再决定是否继续
        int active = DawnStarData.getActiveTicks(maid);
        int cooldown = DawnStarData.getCooldownTicks(maid);
        boolean holding = DawnStarData.isHoldingOrWearingDawnStar(maid);
        if (!holding && active == 0 && cooldown == 0) return;

        // ---------- 递减激活 / 冷却 ----------
        boolean activeEnded = false;
        boolean cooldownEnded = false;

        if (active > 0) {
            active--;
            DawnStarData.setActiveTicks(maid, active);
            activeEnded = active == 0;
            // 「晨曦」结束 → 晨光层数归零，随后进入 90 秒冷却
            if (activeEnded) {
                DawnStarData.setCharge(maid, 0);
                cooldown = DawnStarData.DAWN_COOLDOWN_TICKS;
                DawnStarData.setCooldownTicks(maid, cooldown);
            }
        } else if (cooldown > 0) {
            cooldown--;
            DawnStarData.setCooldownTicks(maid, cooldown);
            cooldownEnded = cooldown == 0;
        }

        // ---------- 未持有晨曦之星 → 清掉加成 ----------
        if (!holding) {
            removeSpeedModifier(maid);
            if (activeEnded || cooldownEnded) {
                DawnStarData.refreshLore(maid);
            }
            return;
        }

        // ---------- 满层自动爆发 ----------
        if (DawnStarData.getCharge(maid) >= DawnStarData.MAX_CHARGE
                && active == 0 && cooldown == 0) {
            activateDawn(maid);
            active = DawnStarData.getActiveTicks(maid);
            DawnStarData.refreshLore(maid);
        }

        // ---------- 状态效果 ----------
        if (active > 0) {
            applySpeedModifier(maid);

            // 每 2 秒回复 2.5 点生命（女仆没有饥饿系统，这里用实体自身 tickCount 计时）
            if (maid.tickCount % DawnStarData.DAWN_REGEN_INTERVAL_TICKS == 0) {
                maid.heal(DawnStarData.DAWN_REGEN_AMOUNT);
            }
            // 持续粒子反馈
            if (maid.tickCount % 8 == 0) {
                spawnAuraParticles(maid);
            }
            // 状态倒计时：每秒刷新一次 lore
            if (maid.tickCount % 20 == 0) {
                DawnStarData.refreshLore(maid);
            }
        } else {
            removeSpeedModifier(maid);
            if (activeEnded || cooldownEnded || DawnStarData.needsLoreRefresh(maid)) {
                DawnStarData.refreshLore(maid);
            }
        }
    }

    // ========================================================================
    // 日出爆发：满层激活「晨曦」（与玩家侧表现一致：金色粒子柱 + 信标激活音）
    // ========================================================================
    private static void activateDawn(LivingEntity maid) {
        DawnStarData.setActiveTicks(maid, DawnStarData.DAWN_ACTIVE_TICKS);

        if (!(maid.level() instanceof ServerLevel serverLevel)) return;

        double x = maid.getX();
        double y = maid.getY();
        double z = maid.getZ();

        serverLevel.sendParticles(ParticleTypes.END_ROD,
                x, y, z, 120, 0.45, 1.20, 0.45, 0.30);
        serverLevel.sendParticles(ParticleTypes.FLAME,
                x, y, z, 60, 0.45, 0.40, 0.45, 0.05);
        serverLevel.playSound(null, x, y, z,
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0f, 1.0f);
    }

    /** 晨曦期间的持续粒子：身周淡淡金色光雾 */
    private static void spawnAuraParticles(LivingEntity maid) {
        if (!(maid.level() instanceof ServerLevel serverLevel)) return;
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                maid.getX(), maid.getY() + maid.getBbHeight() * 0.5, maid.getZ(),
                6, 0.5, 0.7, 0.5, 0.02);
    }

    // ========================================================================
    // 属性修饰符：晨曦移速 +10%
    // ========================================================================
    private static void applySpeedModifier(LivingEntity maid) {
        AttributeInstance speedAttr = maid.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;
        if (speedAttr.getModifier(MAID_DAWN_SPEED_UUID) == null) {
            speedAttr.addTransientModifier(new AttributeModifier(
                    MAID_DAWN_SPEED_UUID, "Maid Dawn Star Speed", DawnStarData.DAWN_SPEED_BONUS,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void removeSpeedModifier(LivingEntity maid) {
        AttributeInstance speedAttr = maid.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null && speedAttr.getModifier(MAID_DAWN_SPEED_UUID) != null) {
            speedAttr.removeModifier(MAID_DAWN_SPEED_UUID);
        }
    }

    /** 敌对生物判定：怪物类别（与玩家侧 DawnStarHandler#isHostile 一致） */
    private static boolean isHostile(LivingEntity entity) {
        return entity.getType().getCategory() == MobCategory.MONSTER;
    }
}
