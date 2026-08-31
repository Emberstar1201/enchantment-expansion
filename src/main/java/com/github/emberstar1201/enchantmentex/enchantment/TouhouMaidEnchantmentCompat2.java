package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.FallCushionConfig;
import com.github.emberstar1201.enchantmentex.IllusoryFeastConfig;
import com.github.emberstar1201.enchantmentex.TemperatureConstantConfig;
import com.github.emberstar1201.enchantmentex.entity.CrescentEntity;
import com.github.emberstar1201.enchantmentex.util.AllyFilter;
import com.github.emberstar1201.enchantmentex.util.TLMSafe;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 车万女仆 · 附魔兼容性扩展（第 2 批）
//
// 与第 1 批（自动修复/嗜血/拂晓）保持同一模式：
//   - 软兼容：仅通过 TLMSafe.isTouhouMaid 判断女仆实体；
//   - 未安装车万女仆时：所有方法在入口处 return，开销可忽略；
//   - 效果数值复用玩家同名配置，避免两份维护成本。
//
// 本批接入：
//   (A) 云来剑法 / 古·云来剑法 —— 攻速/攻距修饰符 + 近战伤害倍率 + 剑气
//   (B) 坠落缓冲             —— 摔落伤害减免 + 落地冲击波（不分敌我、女仆本人不伤）
//   (C) 温度恒定             —— 冰冻/火焰伤害取消 + 冻结计时清零
//   (D) 画饼充饥             —— 女仆无 FoodData，转为对女仆回血（按每级的 food 数值 × 每饱食度等价 2 点血换算）
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class TouhouMaidEnchantmentCompat2 {

    // ------- 云来剑法 修饰符 UUID（女仆专用，避免与玩家共享 UUID 冲突） -------
    private static final UUID MAID_YUNLAI_SPEED_UUID =
            UUID.fromString("1f101b2e-0d87-4a9f-9c3b-3cf12ab4f201");
    private static final UUID MAID_YUNLAI_REACH_UUID =
            UUID.fromString("2f202b3e-1d87-4b9f-8c3b-3cf12ab4f202");
    private static final UUID MAID_ANCIENT_YUNLAI_SPEED_UUID =
            UUID.fromString("3f303b4e-2d87-4c9f-9d3b-3cf12ab4f203");
    private static final UUID MAID_ANCIENT_YUNLAI_REACH_UUID =
            UUID.fromString("4f404b5e-3d87-4d9f-ae3b-3cf12ab4f204");

    // ====================================================================
    // 【A-1】云来剑法：女仆 ServerTick 阶段维护 modifier
    //        （女仆没有 PlayerTickEvent，因此在 ServerTick 中遍历所有 TLM 实体）
    // ====================================================================
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (var level : server.getAllLevels()) {
            if (level.isClientSide) continue;
            for (LivingEntity maid : level.getEntitiesOfClass(
                    LivingEntity.class,
                    new AABB(
                            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
                    ),
                    TLMSafe::isTouhouMaid)) {
                ItemStack weapon = maid.getMainHandItem();
                int yunlai = EnchantmentHelper.getItemEnchantmentLevel(
                        ModEnchantments.YUNLAI_SWORDMANSHIP.get(), weapon);
                int ancient = EnchantmentHelper.getItemEnchantmentLevel(
                        ModEnchantments.ANCIENT_YUNLAI_SWORDMANSHIP.get(), weapon);

                boolean hasYunlai = yunlai > 0;
                boolean hasAncient = ancient > 0;

                // 云来剑法
                applyOrRemoveModifier(maid, Attributes.ATTACK_SPEED,
                        MAID_YUNLAI_SPEED_UUID,
                        "MaidYunlaiSpeed",
                        Config.yunlaiSwordmanshipAttackSpeed,
                        hasYunlai);
                applyOrRemoveModifier(maid, ForgeMod.ENTITY_REACH.get(),
                        MAID_YUNLAI_REACH_UUID,
                        "MaidYunlaiReach",
                        Config.yunlaiSwordmanshipAttackReach,
                        hasYunlai);

                // 古·云来剑法（两把互斥，与玩家版一致）
                applyOrRemoveModifier(maid, Attributes.ATTACK_SPEED,
                        MAID_ANCIENT_YUNLAI_SPEED_UUID,
                        "MaidAncientYunlaiSpeed",
                        Config.ancientYunlaiSwordmanshipAttackSpeed,
                        hasAncient);
                applyOrRemoveModifier(maid, ForgeMod.ENTITY_REACH.get(),
                        MAID_ANCIENT_YUNLAI_REACH_UUID,
                        "MaidAncientYunlaiReach",
                        Config.ancientYunlaiSwordmanshipAttackReach,
                        hasAncient);
            }
        }
    }

    // ====================================================================
    // 【A-2】云来剑法伤害倍率 + 剑气发射
    // ====================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (source == null) return;
        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity livingAttacker)) return;
        if (!TLMSafe.isTouhouMaid(livingAttacker)) return;
        if (livingAttacker.level().isClientSide()) return;

        LivingEntity target = event.getEntity();
        if (target == livingAttacker) return;

        // 防止剑气递归触发剑气
        if (event.getSource().getDirectEntity() instanceof CrescentEntity) return;

        ItemStack weapon = livingAttacker.getMainHandItem();
        if (weapon.isEmpty()) return;

        double multiplier = 1.0;
        int swordQiType = 0; // 0=无 1=云来 2=古
        int yunlaiLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.YUNLAI_SWORDMANSHIP.get(), weapon);
        int ancientLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.ANCIENT_YUNLAI_SWORDMANSHIP.get(), weapon);

        if (yunlaiLevel > 0) {
            multiplier = Config.yunlaiSwordmanshipDamageMultiplier;
            swordQiType = 1;
        } else if (ancientLevel > 0) {
            multiplier = Config.ancientYunlaiSwordmanshipDamageMultiplier;
            swordQiType = 2;
        }

        float originalAmount = event.getAmount();
        if (multiplier > 1.0) {
            event.setAmount(originalAmount * (float) multiplier);
        }
        if (swordQiType != 0
                && target.level() instanceof ServerLevel serverLevel
                && !AllyFilter.isFriendly(target)) {
            trySpawnSwordQi(serverLevel, livingAttacker, target, swordQiType,
                    swordQiType == 1 ? yunlaiLevel : ancientLevel,
                    originalAmount);
        }
    }

    private static void trySpawnSwordQi(
            ServerLevel level,
            LivingEntity maid,
            LivingEntity target,
            int swordQiType,
            int levelEnchant,
            float eventAmountRef
    ) {
        double chance, damageMultiplier, range, speed;
        if (swordQiType == 1) {
            chance = Config.yunlaiSwordmanshipSwordQiChance;
            damageMultiplier = Config.yunlaiSwordmanshipSwordQiDamageMultiplier;
            range = Config.yunlaiSwordmanshipSwordQiRange;
            speed = Config.yunlaiSwordmanshipSwordQiSpeed;
        } else {
            chance = Config.ancientYunlaiSwordmanshipSwordQiChance;
            damageMultiplier = Config.ancientYunlaiSwordmanshipSwordQiDamageMultiplier;
            range = Config.ancientYunlaiSwordmanshipSwordQiRange;
            speed = Config.ancientYunlaiSwordmanshipSwordQiSpeed;
        }
        if (chance <= 0) return;
        if (level.random.nextDouble() >= chance) return;

        // 女仆剑气伤害与玩家同款：以本次攻击伤害为基准 × 配置倍率
        float swordQiDamage = eventAmountRef * (float) damageMultiplier;

        Vec3 start = maid.position().add(0, maid.getBbHeight() * 0.75, 0);
        Vec3 dir = target.position().add(0, target.getBbHeight() * 0.5, 0)
                .subtract(start).normalize();
        if (dir.lengthSqr() < 1.0E-6) dir = maid.getLookAngle();

        CrescentEntity crescent = new CrescentEntity(
                level, maid, start, dir,
                (float) speed, swordQiDamage, range, swordQiType);
        level.addFreshEntity(crescent);
    }

    // ====================================================================
    // 【B】坠落缓冲：LivingFallEvent
    // ====================================================================
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!TLMSafe.isTouhouMaid(entity)) return;
        if (!FallCushionConfig.enabled) return;

        ItemStack boots = entity.getItemBySlot(EquipmentSlot.FEET);
        int level = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.FALL_CUSHION.get(), boots);
        if (level <= 0) return;

        event.setDamageMultiplier((float) FallCushionConfig.getDamageMultiplier(level));

        float fallDistance = event.getDistance();
        if (fallDistance < FallCushionConfig.minFallDistance) return;

        double radius = FallCushionConfig.getRadius(level);
        double knockback = FallCushionConfig.getKnockback(level);
        Level world = entity.level();

        AABB aabb = entity.getBoundingBox().inflate(radius);
        List<LivingEntity> nearby = world.getEntitiesOfClass(LivingEntity.class, aabb,
                e -> e != entity && e.isAlive());

        for (LivingEntity nearbyEntity : nearby) {
            double dx = nearbyEntity.getX() - entity.getX();
            double dz = nearbyEntity.getZ() - entity.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > radius) continue;
            double strength = knockback * (1.0 - dist / radius);
            if (strength <= 0) continue;
            nearbyEntity.knockback(strength, dx, dz);
        }

        world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 0.8F, 0.6F);
        if (world instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    entity.getX(), entity.getY() + 0.1, entity.getZ(),
                    20, radius * 0.3, 0.1, radius * 0.3, 0.1);
        }
    }

    // ====================================================================
    // 【C-1】温度恒定 · LivingHurtEvent：冰冻/火焰伤害取消
    // ====================================================================
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH)
    public static void onLivingHurtTempConstant(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!TLMSafe.isTouhouMaid(entity)) return;
        if (!TemperatureConstantConfig.enabled) return;
        if (!hasTemperatureConstant(entity)) return;

        if (TemperatureConstantConfig.preventFreeze
                && event.getSource().is(DamageTypeTags.IS_FREEZING)) {
            event.setCanceled(true);
            entity.setTicksFrozen(0);
            return;
        }
        if (TemperatureConstantConfig.preventHeat
                && event.getSource().is(DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
        }
    }

    // ====================================================================
    // 【C-2】温度恒定 · LivingTick：冻结计时清零
    // ====================================================================
    @SubscribeEvent
    public static void onLivingTickTempConstant(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Level level = event.level;
        if (level.isClientSide) return;
        if (!TemperatureConstantConfig.enabled
                || !TemperatureConstantConfig.preventFreeze) return;
        for (LivingEntity maid : level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(
                        Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                        Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
                ),
                e -> TLMSafe.isTouhouMaid(e) && e.getTicksFrozen() > 0 && hasTemperatureConstant(e))) {
            maid.setTicksFrozen(0);
        }
    }

    private static boolean hasTemperatureConstant(LivingEntity maid) {
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = maid.getItemBySlot(slot);
            if (EnchantmentHelper.getTagEnchantmentLevel(
                    ModEnchantments.TEMPERATURE_CONSTANT.get(), stack) > 0) {
                return true;
            }
        }
        return false;
    }

    // ====================================================================
    // 【D】画饼充饥：女仆受击命中 -> 回血（女仆无饥饿系统）
    //   食物等级转生命规则：
    //     原版 1 饱食度（鸡腿）= 4 点食物值，能回复 2 点生命（半心/食物）。
    //     这里采用更直观的：回血 = getFood(level) * 1.0（即每个饥饿点直接回 1 点血）。
    //     同时少量饱和值也按同系数加一些。总的目标是"让女仆战斗中持续续航"，
    //     与女仆自身原版回血不冲突。
    // ====================================================================
    @SubscribeEvent
    public static void onLivingHurtIllusoryFeast(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!IllusoryFeastConfig.isEnabled()) return;
        DamageSource source = event.getSource();
        if (source == null) return;
        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity livingAttacker)) return;
        if (!TLMSafe.isTouhouMaid(livingAttacker)) return;
        LivingEntity target = event.getEntity();
        if (target == livingAttacker) return;
        // 女仆"攻击友方"不回血
        if (AllyFilter.isFriendly(target)) return;

        ItemStack weapon = livingAttacker.getMainHandItem();
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.ILLUSORY_FEAST.get(), weapon);
        if (level <= 0) return;

        int food = IllusoryFeastConfig.getFood(level);
        float saturation = IllusoryFeastConfig.getSaturation(level);
        // 简化换算：1 食物值 = 1 点生命；1 饱食度因子 = 额外 0.5 点生命（微量溢出）
        float heal = food + saturation * 0.5F;
        if (heal > 0 && livingAttacker.isAlive()) {
            livingAttacker.heal(heal);
            if (livingAttacker.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HEART,
                        livingAttacker.getX(),
                        livingAttacker.getY() + livingAttacker.getBbHeight(),
                        livingAttacker.getZ(),
                        2, 0.3, 0.1, 0.3, 0.5);
            }
        }
    }

    // ====================================================================
    // 辅助：对任意 LivingEntity（含女仆）应用或移除 AttributeModifier
    // ====================================================================
    private static void applyOrRemoveModifier(
            LivingEntity entity,
            Attribute attribute,
            UUID uuid,
            String name,
            double value,
            boolean apply
    ) {
        AttributeInstance attr = entity.getAttribute(attribute);
        if (attr == null) return;
        attr.removeModifier(uuid);
        if (apply && value != 0) {
            attr.addTransientModifier(new AttributeModifier(
                    uuid, name, value, AttributeModifier.Operation.ADDITION));
        }
    }
}
