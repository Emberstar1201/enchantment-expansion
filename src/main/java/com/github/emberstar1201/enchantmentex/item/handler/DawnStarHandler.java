package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.enchantment.ModEnchantments;
import com.github.emberstar1201.enchantmentex.item.ModItems;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【晨曦之星】事件处理器 ——「破晓共鸣」
//
// 核心循环：积累（拂晓负责）→ 爆发（星星负责）→ 冷却
//   1. LivingDeathEvent  → 晨光积累（击杀敌对生物 +0.25；主手带拂晓 +2）
//                          + 获取仪式判定（日落后击杀凋零）
//   2. PlayerTickEvent   → 状态机：满层自动爆发 / 冷却递减 / 移速 / 回血 / 粒子
//   3. LivingHurtEvent   → 晨曦期间伤害 ×1.25 + 暴击率 +10%
//
// 【获取方式】
//   完成成就「我们逝去，我们永恒」后，于日落到日出之间（整段黑夜），
//   手持附魔了拂晓的武器再度击杀一只凋零 → 晨曦之星与凋零的战利品一同掉落在地上，
//   拾取后点亮成就「所谓破晓，终将自由」。
//
// 【仅服务端处理】
//   与星辉之星同理：属性修饰符若在客户端也执行 tick，会走进"移除修饰符"
//   分支把同步过来的加成删掉，因此必须加 isClientSide() 判断。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DawnStarHandler {

    /** 「晨曦」移速修饰符固定 UUID（瞬态修饰符，随状态增删） */
    private static final UUID DAWN_SPEED_UUID =
            UUID.fromString("c8e4b2a6-3d5f-4e7a-8b9c-1f2d3e4a5b6c");

    /** 前置成就：我们逝去，我们永恒 */
    private static final ResourceLocation ADVANCEMENT_WE_FALL_WE_REMAIN =
            new ResourceLocation(MODID, "we_fall_we_remain");

    /** 黑夜窗口：日落(12000) → 日出(24000)，即整段黑夜 */
    private static final long NIGHT_WINDOW_START = 12000L;
    private static final long NIGHT_WINDOW_END = 24000L;

    // ========================================================================
    // 事件1：LivingDeathEvent → 晨光积累 + 获取仪式
    // ========================================================================
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        DamageSource source = event.getSource();
        if (source == null) return;

        Entity attacker = source.getEntity();
        if (!(attacker instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        LivingEntity victim = event.getEntity();

        // ---------- 获取仪式（独立于是否已持有晨曦之星） ----------
        tryGrantDawnStar(player, victim);

        // ---------- 晨光积累 ----------
        if (victim instanceof Player) return;                       // PVP 不积累
        if (!DawnStarData.isHoldingOrWearingDawnStar(player)) return;
        if (!isHostile(victim)) return;
        // 触发「晨曦」后（激活中 + 随后的冷却期）不再叠加晨光
        if (DawnStarData.isDawnActive(player) || DawnStarData.getCooldownTicks(player) > 0) return;

        // 拂晓联动：主手武器带拂晓附魔 → +2 层，否则 +0.25 层
        boolean dawnWeapon = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DAWN.get(), player.getMainHandItem()) > 0;
        double gain = dawnWeapon
                ? DawnStarData.DAWN_KILL_CHARGE_GAIN
                : DawnStarData.KILL_CHARGE_GAIN;

        DawnStarData.addCharge(player, gain);
        DawnStarData.refreshLore(player);
    }

    // ========================================================================
    // 事件2：LivingHurtEvent → 晨曦期间 伤害 ×1.25 + 暴击率 +10%
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!DawnStarData.isDawnActive(player)) return;
        // 放下 / 卸下晨曦之星后不再享受加成
        if (!DawnStarData.isHoldingOrWearingDawnStar(player)) return;

        float amount = event.getAmount() * DawnStarData.DAWN_DAMAGE_MULTIPLIER;

        // 暴击率 +10%：命中时按原版暴击倍率结算
        if (player.level().random.nextDouble() < DawnStarData.DAWN_CRIT_BONUS) {
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
    // 事件3：PlayerTickEvent → 状态机
    //   - 递减激活 / 冷却 tick
    //   - 满层自动爆发「晨曦」
    //   - 激活期间：移速 +10%、每 2 秒回 2.5 点生命、持续粒子
    //   - lore 同步（倒计时 / 状态变化 / 新物品补写）
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide()) return;

        // ---------- 快速路径（性能关键） ----------
        // 本方法每 tick 对每个玩家都会执行；绝大多数玩家身上并没有晨曦之星。
        // 先做一次廉价判定：既未持有/穿戴、也没有进行中的状态 → 立刻返回，
        // 从而省掉后面的 NBT 写入、属性查询与多轮槽位扫描（lore 相关扫描尤其贵）。
        int active = DawnStarData.getActiveTicks(player);
        int cooldown = DawnStarData.getCooldownTicks(player);
        boolean holding = DawnStarData.isHoldingOrWearingDawnStar(player);
        if (!holding && active == 0 && cooldown == 0) return;

        // ---------- 递减激活 / 冷却 ----------
        boolean activeEnded = false;
        boolean cooldownEnded = false;

        if (active > 0) {
            active--;
            DawnStarData.setActiveTicks(player, active);
            activeEnded = active == 0;
            // 「晨曦」结束 → 晨光层数归零，随后进入 90 秒冷却
            // （激活期间层数一直保持在满层 10 不清空；冷却结束才允许重新积累）
            if (activeEnded) {
                DawnStarData.setCharge(player, 0);
                cooldown = DawnStarData.DAWN_COOLDOWN_TICKS;
                DawnStarData.setCooldownTicks(player, cooldown);
            }
        } else if (cooldown > 0) {
            cooldown--;
            DawnStarData.setCooldownTicks(player, cooldown);
            cooldownEnded = cooldown == 0;
        }

        // ---------- 未持有晨曦之星 → 清掉加成 ----------
        // 复用上面已经算好的 holding，避免同一 tick 内重复扫描全部槽位
        if (!holding) {
            removeSpeedModifier(player);
            if (activeEnded || cooldownEnded) {
                DawnStarData.refreshLore(player);
            }
            return;
        }

        // ---------- 满层自动爆发 ----------
        if (DawnStarData.getCharge(player) >= DawnStarData.MAX_CHARGE
                && active == 0 && cooldown == 0) {
            activateDawn(player);
            active = DawnStarData.getActiveTicks(player);
            DawnStarData.refreshLore(player);
        }

        // ---------- 状态效果 ----------
        if (active > 0) {
            applySpeedModifier(player);

            // 每 2 秒回复 2.5 点生命
            if (player.tickCount % DawnStarData.DAWN_REGEN_INTERVAL_TICKS == 0) {
                player.heal(DawnStarData.DAWN_REGEN_AMOUNT);
            }
            // 持续粒子反馈
            if (player.tickCount % 8 == 0) {
                spawnAuraParticles(player);
            }
            // 状态倒计时：每秒刷新一次 lore
            if (player.tickCount % 20 == 0) {
                DawnStarData.refreshLore(player);
            }
        } else {
            removeSpeedModifier(player);
            if (activeEnded || cooldownEnded || DawnStarData.needsLoreRefresh(player)) {
                DawnStarData.refreshLore(player);
            }
        }
    }

    // ========================================================================
    // 日出爆发：满层激活「晨曦」
    //   层数保持在满层 10 不清空（激活期间靠 onLivingDeath 的守卫阻止继续叠加，
    //   因此无论怎么攻击都维持 10）→ 180 秒持续结束后才清空层数并进入 90 秒冷却
    //   （清空与冷却均见 onPlayerTick 的 activeEnded 分支）
    //   触发瞬间：金色粒子柱 + BEACON_ACTIVATE
    // ========================================================================
    private static void activateDawn(Player player) {
        DawnStarData.setActiveTicks(player, DawnStarData.DAWN_ACTIVE_TICKS);

        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        // 金色粒子柱：END_ROD 向上喷涌 + FLAME 在脚下升腾
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                x, y, z, 120, 0.45, 1.20, 0.45, 0.30);
        serverLevel.sendParticles(ParticleTypes.FLAME,
                x, y, z, 60, 0.45, 0.40, 0.45, 0.05);

        // 音效风格与拂晓的「刺破长夜」保持一致
        serverLevel.playSound(null, x, y, z,
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0f, 1.0f);
    }

    /** 晨曦期间的持续粒子：身周淡淡金色光雾 */
    private static void spawnAuraParticles(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
                6, 0.5, 0.7, 0.5, 0.02);
    }

    // ========================================================================
    // 属性修饰符：晨曦移速 +10%
    // ========================================================================
    private static void applySpeedModifier(Player player) {
        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;
        if (speedAttr.getModifier(DAWN_SPEED_UUID) == null) {
            speedAttr.addTransientModifier(new AttributeModifier(
                    DAWN_SPEED_UUID, "Dawn Star Speed", DawnStarData.DAWN_SPEED_BONUS,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void removeSpeedModifier(Player player) {
        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null && speedAttr.getModifier(DAWN_SPEED_UUID) != null) {
            speedAttr.removeModifier(DAWN_SPEED_UUID);
        }
    }

    // ========================================================================
    // 获取仪式：完成前置成就后，于日落到日出之间用拂晓之刃斩杀凋零
    // ========================================================================
    private static void tryGrantDawnStar(Player player, LivingEntity victim) {
        if (DawnStarData.hasObtained(player)) return;
        if (!(victim instanceof WitherBoss)) return;

        // 主手武器必须附魔拂晓
        if (EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DAWN.get(), player.getMainHandItem()) <= 0) return;

        // 前置成就：我们逝去，我们永恒
        if (!hasCompletedAdvancement(player, ADVANCEMENT_WE_FALL_WE_REMAIN)) return;

        // 时间窗口：日落到日出（整段黑夜）
        if (!isNightWindow(player.level())) return;

        // ---------- 发放晨曦之星 ----------
        // 不再直接塞进玩家背包，而是与凋零的战利品一同掉落在地上（落点取凋零死亡处）。
        //   凋零的下界之星正是在这一刻由 dropCustomDeathLoot 掉落，因此两者几乎同时落地。
        //   setExtendedLifetime() 与下界之星保持一致：永不因存在时间过长而自然消失。
        if (!(victim.level() instanceof ServerLevel victimLevel)) return;

        ItemStack star = new ItemStack(ModItems.DAWN_STAR.get());
        DawnStarData.writeLoreTo(star, player);

        ItemEntity drop = new ItemEntity(victimLevel,
                victim.getX(), victim.getY() + victim.getBbHeight() / 2.0D, victim.getZ(), star);
        drop.setExtendedLifetime();
        victimLevel.addFreshEntity(drop);

        DawnStarData.setObtained(player);

        // 稍抒情的提示
        player.sendSystemMessage(Component.translatable(
                "message.enchantment_expansion.dawn_star_obtained"));

        // 仪式反馈：金色光柱 + 信标激活音
        if (player.level() instanceof ServerLevel serverLevel) {
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    x, y, z, 150, 0.5, 1.5, 0.5, 0.35);
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    x, y, z, 80, 0.5, 0.6, 0.5, 0.05);
            serverLevel.playSound(null, x, y, z,
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3.0f, 1.0f);
        }
    }

    /** 查询玩家是否已完成指定成就 */
    private static boolean hasCompletedAdvancement(Player player, ResourceLocation id) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        Advancement advancement = serverPlayer.server.getAdvancements().getAdvancement(id);
        if (advancement == null) return false;
        return serverPlayer.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /** 时间窗口判定：日落到日出（下界/末地无昼夜，不成立） */
    private static boolean isNightWindow(Level level) {
        if (level.dimensionType().hasFixedTime()) return false;
        long time = level.getDayTime() % 24000L;
        return time >= NIGHT_WINDOW_START && time < NIGHT_WINDOW_END;
    }

    /** 敌对生物判定：怪物类别（含僵尸、骷髅、凋零、末影龙等） */
    private static boolean isHostile(LivingEntity entity) {
        return entity.getType().getCategory() == MobCategory.MONSTER;
    }
}
