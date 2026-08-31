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
//                        对女仆自身进行"目标最大生命吸血"+ 范围溅射。
//                        溅射伤害同样走 AllyFilter.isFriendly 过滤，不会误伤其它女仆、
//                        主人、村民、驯服生物等。
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
        if (!AutoRepairConfig.isEnabled()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        CURRENT_AUTO_REPAIR_KEYS.clear();

        for (var level : server.getAllLevels()) {
            if (level.isClientSide) continue;
            // 只遍历 LivingEntity：命名空间判断能把非女仆实体过滤掉
            for (LivingEntity living : level.getEntitiesOfClass(
                    LivingEntity.class,
                    new AABB(
                            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
                    ),
                    TLMSafe::isTouhouMaid)) {
                handleMaidAutoRepair(living);
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
        // 嗜血：女仆攻击"友方"时不回血，避免误伤刷治疗
        if (AllyFilter.isFriendly(target)) return;

        ItemStack weapon = livingAttacker.getMainHandItem();
        if (weapon.isEmpty()) return;
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
}
