package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.AutoRepairConfig;
import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.util.AllyFilter;
import com.github.emberstar1201.enchantmentex.util.TLMSafe;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 车万女仆（Touhou Little Maid）附魔兼容层
//
// 触发条件：仅当安装了 TLM，且实体类型命名空间为 "touhou_little_maid" 时。
// 未安装该模组：isTouhouMaid 永远为 false，所有方法几乎只做一次命名空间比较，
//                对玩家 & 原版生物无副作用。
//
// 当前兼容的附魔效果：
//   1. 自动修复（自动修复）：女仆主手/副手/护甲每级按配置间隔恢复 1 耐久。
//   2. 嗜血：女仆主手武器命中 LivingEntity 时，女仆按最大生命值百分比回血
//            （与玩家嗜血走同一 Config.bloodthirstHealPercent / CooldownTicks）。
//   3. 拂晓（击杀触发）：女仆持拂晓武器击杀 LivingEntity（非玩家）时，
//                        累计拂晓成长（记入主人，Boss 倍率与玩家版一致）、
//                        对女仆自身进行"目标最大生命吸血"+ 范围溅射。
//                        溅射伤害同样走 AllyFilter.isFriendly 过滤，不会误伤其它女仆、
//                        主人、村民、驯服生物等。
//   4. 拂晓（全部加成）：女仆持拂晓武器时，与玩家享受完全相同的加成：
//                        伤害加成、暴击系统（含伪概率）、低血处决、刺破长夜。
//                        成长数据取"主人"的拂晓等级（主人离线时退回武器 NBT 上的数值），
//                        因此女仆的强度与主人手上的同一把剑完全一致；
//                        而暴击伪概率 / 刺破长夜状态存在女仆自身的 PersistentData，
//                        与玩家互不干扰。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class TouhouMaidEnchantmentCompat {

    // 【自动修复】按"女仆 UUID + 装备索引（0~5）"累计 tick
    private static final Map<String, Integer> AUTO_REPAIR_ACCUM = new HashMap<>();
    private static final List<String> CURRENT_AUTO_REPAIR_KEYS = new ArrayList<>();

    // 【嗜血】按"女仆 UUID"记录冷却时间（与玩家嗜血同款）
    private static final String BT_TAG_ROOT = "EEMaidBT";
    private static final String BT_KEY_LAST = "lastTrigger";

    // ========================================================================
    // 1. 自动修复：服务端每个 ServerLevel 的所有女仆 tick
    // ========================================================================
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 两个功能都关闭时不遍历：避免无意义地扫描每个维度的全部实体
        boolean autoRepair = AutoRepairConfig.isEnabled();
        if (!autoRepair && !Config.dawnPierceEnabled) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        CURRENT_AUTO_REPAIR_KEYS.clear();

        for (var level : server.getAllLevels()) {
            if (level.isClientSide) continue;
            // 女仆枚举统一走 TLMSafe.collectMaids：命名空间过滤在内部完成。
            // 注意不要改回 getEntitiesOfClass + 无穷大 AABB，那种写法恒返回空列表。
            for (LivingEntity living : TLMSafe.collectMaids(level)) {
                if (autoRepair) handleMaidAutoRepair(living);
                // 拂晓 · 刺破长夜状态机（女仆版）
                if (Config.dawnPierceEnabled) handleMaidPierceTick(living);
            }
        }

        // 清理无效键：女仆卸载 / 维度切换后不会无限留存
        if (!CURRENT_AUTO_REPAIR_KEYS.isEmpty()) {
            AUTO_REPAIR_ACCUM.keySet().retainAll(CURRENT_AUTO_REPAIR_KEYS);
        } else {
            AUTO_REPAIR_ACCUM.clear();
        }
    }

    private static void handleMaidAutoRepair(LivingEntity maid) {
        List<ItemStack> stacks = TLMSafe.collectMaidEquipments(maid);
        if (stacks.isEmpty()) return;

        String uuidKey = maid.getUUID().toString();

        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack == null || stack.isEmpty()) continue;

            int level = EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.AUTO_REPAIR.get(), stack);
            if (level <= 0 || !stack.isDamaged()) continue;

            String key = uuidKey + "_" + i;
            CURRENT_AUTO_REPAIR_KEYS.add(key);

            int ticks = AUTO_REPAIR_ACCUM.getOrDefault(key, 0) + 1;
            int interval = AutoRepairConfig.getIntervalTicks(level);
            if (ticks >= interval) {
                stack.setDamageValue(stack.getDamageValue() - 1);
                ticks = 0;
            }
            AUTO_REPAIR_ACCUM.put(key, ticks);
        }
    }

    // ========================================================================
    // 2. 嗜血：女仆命中时按冷却、比例为女仆回血
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (source == null) return;
        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity livingAttacker)) return;
        if (!TLMSafe.isTouhouMaid(livingAttacker)) return;
        if (livingAttacker.level().isClientSide) return;

        LivingEntity target = event.getEntity();
        if (target == livingAttacker) return;

        ItemStack weapon = livingAttacker.getMainHandItem();
        if (weapon.isEmpty()) return;

        // 拂晓：全额加成（伤害加成 / 刺破长夜倍率 / 低血处决 / 暴击系统）
        applyDawnDamage(livingAttacker, target, weapon, event);

        // 嗜血：女仆攻击"友方"时不回血，避免误伤刷治疗
        if (AllyFilter.isFriendly(target)) return;

        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.BLOODTHIRST.get(), weapon);
        if (enchantLevel <= 0) return;

        // 冷却（与玩家嗜血共享配置）
        CompoundTag tag = livingAttacker.getPersistentData().getCompound(BT_TAG_ROOT);
        long now = livingAttacker.level().getGameTime();
        long last = tag.getLong(BT_KEY_LAST);
        if (now - last < Config.bloodthirstCooldownTicks) return;

        float healAmount = livingAttacker.getMaxHealth()
                * ((float) Config.bloodthirstHealPercent / 100.0f);
        livingAttacker.heal(healAmount);

        tag.putLong(BT_KEY_LAST, now);
        livingAttacker.getPersistentData().put(BT_TAG_ROOT, tag);

        if (livingAttacker.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HEART,
                    livingAttacker.getX(),
                    livingAttacker.getY() + livingAttacker.getBbHeight(),
                    livingAttacker.getZ(),
                    3, 0.3, 0.1, 0.3, 0.5);
        }
    }

    // ========================================================================
    // 3. 拂晓：女仆击杀时触发吸血 + 溅射（与玩家拂晓数值同源，AllyFilter 过滤）
    // ========================================================================
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        DamageSource source = event.getSource();
        if (source == null) return;
        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity livingAttacker)) return;
        if (!TLMSafe.isTouhouMaid(livingAttacker)) return;
        if (livingAttacker.level().isClientSide) return;

        LivingEntity victim = event.getEntity();
        if (victim == livingAttacker) return;
        // 拂晓：女仆"击杀友方"不触发成长/溅射，防止误伤刷效果
        if (AllyFilter.isFriendly(victim)) return;

        ItemStack weapon = livingAttacker.getMainHandItem();
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DAWN.get(), weapon);
        if (enchantLevel <= 0) return;

        // ========================================================================
        // 拂晓成长：女仆击杀的成长计入其主人
        //
        // 为什么记在主人头上：
        //   拂晓的成长数据（DawnData.effectiveKills）是"按玩家"存储的，
        //   武器 Tooltip 的 [Lv.X] 读的也是武器 NBT 里的同一份数据，
        //   女仆自身没有 PersistentData 体系，因此只有记在主人身上，
        //   主人下次拿起这把武器时等级才会正确显示。
        // 主人不在线则跳过（离线无法写入其 PersistentData）。
        // Boss 倍率复用玩家版 DawnHandler.isBoss，保证数值口径一致。
        // ========================================================================
        Player owner = resolveMaidOwner(livingAttacker);
        if (owner != null) {
            double growth = 1.0;
            if (DawnHandler.isBoss(victim)) {
                growth *= Config.dawnBossMultiplierMin
                        + livingAttacker.level().random.nextDouble()
                        * (Config.dawnBossMultiplierMax - Config.dawnBossMultiplierMin);
            }
            DawnData.addEffectiveKills(owner, growth);
            // 同步到女仆手中的武器 NBT，使 Tooltip 立即可见（NBT 会自动同步客户端）
            DawnData.setItemKills(weapon, DawnData.getEffectiveKills(owner));
        }

        // ========================================================================
        // 刺破长夜 · 连击累计 + 阈值触发
        //   状态存在女仆自身的 PersistentData，与玩家互不干扰。
        //   激活后的递减 / 低血狂暴自动激活由 handleMaidPierceTick 负责。
        // ========================================================================
        if (Config.dawnPierceEnabled) {
            long now = livingAttacker.level().getGameTime();
            long last = DawnData.getLastKillTime(livingAttacker);
            boolean inWindow = (now - last) <= (long) (Config.dawnPierceComboWindowSeconds * 20);
            int combo = inWindow ? DawnData.getCombo(livingAttacker) + 1 : 1;
            DawnData.setCombo(livingAttacker, combo);
            DawnData.setLastKillTime(livingAttacker, now);

            if (combo >= Config.dawnPierceComboThreshold
                    && DawnData.getCooldownTicks(livingAttacker) == 0
                    && !DawnData.isPierceActive(livingAttacker)) {
                DawnHandler.activatePierce(livingAttacker, 1);
            }
        }

        // 击杀吸血（与玩家拂晓同配置）
        if (Config.dawnLifestealEnabled && Config.dawnLifestealPercent > 0) {
            float healAmount = victim.getMaxHealth() * (float) Config.dawnLifestealPercent;
            livingAttacker.heal(healAmount);
            if (livingAttacker.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HEART,
                        livingAttacker.getX(),
                        livingAttacker.getY() + livingAttacker.getBbHeight(),
                        livingAttacker.getZ(),
                        4, 0.3, 0.1, 0.3, 0.5);
            }
        }

        // 击杀溅射（与玩家拂晓同范围、同伤害比例、同友伤过滤）
        if (Config.dawnSplashEnabled && Config.dawnSplashDamagePercent > 0) {
            float splashRadius = (float) Config.dawnSplashRadius;
            float splashDamage = victim.getMaxHealth() * (float) Config.dawnSplashDamagePercent;
            AABB box = new AABB(
                    victim.getX() - splashRadius,
                    victim.getY() - splashRadius,
                    victim.getZ() - splashRadius,
                    victim.getX() + splashRadius,
                    victim.getY() + splashRadius,
                    victim.getZ() + splashRadius);
            List<LivingEntity> nearby = victim.level().getEntitiesOfClass(
                    LivingEntity.class, box,
                    e -> e != victim
                            && e != livingAttacker
                            && e.isAlive()
                            && !AllyFilter.isFriendly(e));
            for (LivingEntity nearbyEntity : nearby) {
                nearbyEntity.hurt(victim.damageSources().magic(), splashDamage);
            }

            if (victim.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                        victim.getX(),
                        victim.getY() + victim.getBbHeight() / 2.0,
                        victim.getZ(),
                        1, 0, 0, 0, 0);
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        victim.getX(), victim.getY(), victim.getZ(),
                        20, splashRadius, 1.0, splashRadius, 0.1);
            }
        }
    }

    // ========================================================================
    // 拂晓 · 女仆完整加成（与玩家版数值同源）
    //
    // 与 DawnHandler.onLivingHurt 的玩家版一一对应：
    //   伤害加成 → 刺破长夜状态倍率 + 低血狂暴吸血 → 低血处决 → 暴击系统（含伪概率）
    // 唯一差别是"成长等级"的取值来源：玩家读自己的 PersistentData，
    // 女仆读其主人的（主人离线时退回武器 NBT 上已同步的数值），
    // 因此女仆的强度与主人手上这把剑完全一致。
    // ========================================================================
    private static void applyDawnDamage(LivingEntity maid, LivingEntity target,
                                        ItemStack weapon, LivingHurtEvent event) {
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DAWN.get(), weapon);
        if (enchantLevel <= 0) return;

        double kills = resolveDawnKills(maid, weapon);
        double critDamage = DawnData.getCritDamagePercent(kills);

        // 伤害加成
        double damageBonus = DawnData.getDamageBonusPercent(kills);
        if (damageBonus > 0) {
            event.setAmount(event.getAmount() * (1.0f + (float) (damageBonus / 100.0)));
        }

        // 刺破长夜 · 状态伤害倍率 + 低血狂暴吸血
        if (Config.dawnPierceEnabled && DawnData.isPierceActive(maid)) {
            int type = DawnData.getActiveType(maid);
            if (type == 1) {
                event.setAmount(event.getAmount() * (float) Config.dawnPierceComboDamageMultiplier);
            } else if (type == 2) {
                event.setAmount(event.getAmount() * (float) Config.dawnPierceLowHpDamageMultiplier);
                maid.heal(event.getAmount() * (float) (Config.dawnPierceLowHpLifestealPercent / 100.0));
            }
        }

        // 低血处决：目标剩余生命 ≤ 血线 → 必定暴击 + 额外倍率（玩家目标除外）
        boolean isExecute = !(target instanceof Player)
                && Config.dawnExecuteThreshold > 0
                && target.getMaxHealth() > 0
                && target.getHealth() <= target.getMaxHealth() * Config.dawnExecuteThreshold;
        if (isExecute) {
            float executeMultiplier = 1.0f + (float) (critDamage / 100.0);
            executeMultiplier *= (float) Config.dawnExecuteDamageMultiplier;
            event.setAmount(event.getAmount() * executeMultiplier);
            DawnData.setAccumulatedCrit(maid, 0);
            DawnHandler.spawnExecuteParticles(target);
            return;
        }

        // 暴击系统（含伪概率）：跳劈（原版暴击）不参与，与玩家版一致
        if (!maid.onGround() && maid.fallDistance > 0.0F) return;

        double baseCritRate = DawnData.getCritRatePercent(kills);
        double accumulatedCrit = DawnData.getAccumulatedCrit(maid);
        double effectiveCritRate = baseCritRate + accumulatedCrit;
        if (effectiveCritRate > 0
                && maid.level().random.nextDouble() < (effectiveCritRate / 100.0)) {
            event.setAmount(event.getAmount() * (1.0f + (float) (critDamage / 100.0)));
            DawnData.setAccumulatedCrit(maid, 0);
            if (target.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                        25, 0.5, 0.5, 0.5, 0.05);
                serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                        15, 0.3, 0.3, 0.3, 0.5);
            }
        } else {
            DawnData.setAccumulatedCrit(maid, Math.min(100.0, accumulatedCrit + 5.0));
        }
    }

    /** 女仆拂晓成长来源：主人在线时读主人，否则退回武器 NBT 上已同步的数值 */
    private static double resolveDawnKills(LivingEntity maid, ItemStack weapon) {
        Player owner = resolveMaidOwner(maid);
        if (owner != null) return DawnData.getEffectiveKills(owner);
        return DawnData.getItemKills(weapon);
    }

    // ========================================================================
    // 拂晓 · 刺破长夜状态机（女仆版）
    //   与 DawnHandler.onPlayerTick 的玩家版一一对应：递减激活/冷却 tick、
    //   低血狂暴自动激活、连击爆发型的移速/攻距 modifier、持续粒子。
    //   未持拂晓武器时清除状态与 modifier，防止残留。
    // ========================================================================
    private static void handleMaidPierceTick(LivingEntity maid) {
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DAWN.get(), maid.getMainHandItem());
        if (enchantLevel <= 0) {
            DawnData.setActiveTicks(maid, 0);
            DawnData.setCombo(maid, 0);
            DawnHandler.removePierceModifiers(maid);
            return;
        }

        int active = DawnData.getActiveTicks(maid);
        if (active > 0) DawnData.setActiveTicks(maid, active - 1);

        int cooldown = DawnData.getCooldownTicks(maid);
        if (cooldown > 0) DawnData.setCooldownTicks(maid, cooldown - 1);

        int nowActive = DawnData.getActiveTicks(maid);
        int nowCooldown = DawnData.getCooldownTicks(maid);

        // 低血狂暴（类型 2）自动激活：生命 ≤ 阈值、未激活、非冷却
        if (nowActive == 0 && nowCooldown == 0
                && maid.getHealth() <= maid.getMaxHealth() * Config.dawnPierceLowHpThreshold) {
            DawnHandler.activatePierce(maid, 2);
            nowActive = DawnData.getActiveTicks(maid);
        }

        // 连击爆发型（类型 1）效果 = 移速 + 攻距 modifier
        boolean piercing = nowActive > 0;
        if (piercing && DawnData.getActiveType(maid) == 1) {
            DawnHandler.applyPierceModifiers(maid);
        } else {
            DawnHandler.removePierceModifiers(maid);
        }

        // 激活期间持续粒子反馈（每 4 tick 一次）
        if (piercing && maid.tickCount % 4 == 0) {
            DawnHandler.spawnActiveParticles(maid, DawnData.getActiveType(maid));
        }
    }

    // ========================================================================
    // 工具：定位女仆的主人（用于把女仆击杀的成长记到主人头上）
    //   未安装车万女仆、主人为空、或主人不在线时返回 null。
    // ========================================================================
    private static Player resolveMaidOwner(LivingEntity maid) {
        UUID ownerId = TLMSafe.getMaidOwnerUUID(maid);
        if (ownerId == null) return null;
        if (!(maid.level() instanceof ServerLevel serverLevel)) return null;
        return serverLevel.getServer().getPlayerList().getPlayer(ownerId);
    }
}
