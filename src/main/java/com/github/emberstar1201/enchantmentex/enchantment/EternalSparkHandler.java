package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// "星火不灭"（Eternal Spark）附魔事件处理器
//
// 【改版说明】
//   原版：命中后标记→每秒持续扣血（LevelTickEvent遍历全实体，过于复杂且测试未生效）
//   新版：每次命中直接附加额外真实伤害（5% × 层数 最大生命值），简单可靠
//
// 【效果】
//   ① 攻击命中时给目标叠加星火标记（上限3层），并立即造成：
//        层数1 → 5%  最大生命值真实伤害
//        层数2 → 10% 最大生命值真实伤害
//        层数3 → 15% 最大生命值真实伤害
//      （可配置基础百分比和每层加成）
//
//   ② 标记目标死亡时，星火扩散至周围5格敌对生物（人权剑Buff期间翻倍至10格）
//
//   ③ 人权剑「人的意志」Buff 激活期间：基础伤害倍率从5%提升至配置值（默认8%）
//
// 【NBT 结构】（实体 PersistentData）
//   EternalSparkData: {
//       stacks: 1..3                      // 当前层数
//       spreadByBuff: 0 or 1              // 是否由人权剑Buff产生
//   }
//
// 【粒子】暗红灵魂火 + 金色末地烛光点
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EternalSparkHandler {

    // NBT 根键名
    private static final String TAG_ROOT = "EternalSparkData";
    private static final String KEY_STACKS = "stacks";
    private static final String KEY_BUFFED = "spreadByBuff";

    // 最大叠加层数
    private static final int MAX_STACKS = 3;

    // 递归保护标记（防止 setHealth / hurt 再次触发 LivingHurtEvent）
    private static final String DAMAGING_FLAG = "ETERNAL_SPARK_DAMAGING";

    // ================================================================
    // 辅助：判断攻击者是否处于人权剑主动Buff状态
    // ================================================================
    private static boolean hasFreeWillBuff(LivingEntity entity) {
        if (entity == null) return false;
        return entity.getPersistentData().getBoolean("SotFW_buffActive");
    }

    // ================================================================
    // 辅助：施加真实伤害
    //   使用 setHealth 直接扣血（绕过盔甲减伤），
    //   若扣至 ≤0 则调用 hurt() 触发正确的死亡逻辑，
    //   附带递归保护标记防止无限循环。
    // ================================================================
    private static void applyTrueDamage(LivingEntity target, float damage) {
        if (damage <= 0 || target.isDeadOrDying()) return;

        CompoundTag rec = target.getPersistentData();
        if (rec.getBoolean(DAMAGING_FLAG)) return;
        rec.putBoolean(DAMAGING_FLAG, true);

        try {
            float newHealth = target.getHealth() - damage;
            if (newHealth <= 0) {
                // 需要击杀 → 用 hurt 触发掉落/经验/成就
                target.hurt(target.damageSources().magic(), damage);
            } else {
                target.setHealth(newHealth);
            }
        } finally {
            rec.remove(DAMAGING_FLAG);
        }
    }

    // ================================================================
    // 辅助：播放心得火焰暗红/金色粒子
    // ================================================================
    private static void spawnParticles(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel sl)) return;
        Vec3 p = target.position();
        // 暗红灵魂火
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                p.x, p.y + target.getBbHeight() * 0.5, p.z,
                6,
                target.getBbWidth() * 0.4,
                target.getBbHeight() * 0.3,
                target.getBbWidth() * 0.4,
                0.05);
        // 金色末地烛光点
        sl.sendParticles(ParticleTypes.END_ROD,
                p.x, p.y + target.getBbHeight() * 0.5, p.z,
                2,
                target.getBbWidth() * 0.5,
                target.getBbHeight() * 0.5,
                target.getBbWidth() * 0.5,
                0.02);
    }

    // ================================================================
    // 辅助：扩散时生成金色冲击波环形粒子
    // ================================================================
    private static void spawnSpreadEffect(ServerLevel sl, Vec3 center, double radius) {
        int perRing = 24;
        for (double ringScale : new double[]{0.6, 1.0}) {
            double r = radius * ringScale;
            for (int i = 0; i < perRing; i++) {
                double angle = 2 * Math.PI * i / perRing;
                double x = center.x + r * Math.cos(angle);
                double z = center.z + r * Math.sin(angle);
                sl.sendParticles(ParticleTypes.END_ROD,
                        x, center.y + 0.2, z, 1, 0, 0, 0, 0);
                if (ringScale >= 1.0) {
                    sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            x, center.y + 0.2, z, 1, 0, 0.05, 0, 0.05);
                }
            }
        }
        // 中心爆燃
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                center.x, center.y + 0.5, center.z,
                16, 0.3, 0.3, 0.3, 0.1);
    }

    // ================================================================
    // 辅助：判断实体是否为敌对生物（MobCategory.MONSTER）
    // ================================================================
    private static boolean isHostile(LivingEntity entity) {
        if (entity == null) return false;
        return entity.getType().getCategory() == MobCategory.MONSTER;
    }

    // ================================================================
    // LivingHurtEvent：攻击命中 → 叠加星火 + 立即附加真实伤害
    // ================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (target.getPersistentData().getBoolean(DAMAGING_FLAG)) return;

        Entity src = event.getSource().getEntity();
        if (!(src instanceof LivingEntity attacker)) return;

        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty()) return;

        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.ETERNAL_SPARK.get(), weapon);
        if (level <= 0) return;

        // ================================================================
        // 读取/创建星火标记
        // ================================================================
        CompoundTag data = target.getPersistentData().getCompound(TAG_ROOT);
        int stacks = data.getInt(KEY_STACKS);

        // 叠加一层（上限3层）
        if (stacks < MAX_STACKS) stacks++;

        data.putInt(KEY_STACKS, stacks);

        // 若攻击者处于人权剑Buff，标记之
        if (hasFreeWillBuff(attacker)) {
            data.putBoolean(KEY_BUFFED, true);
        }

        target.getPersistentData().put(TAG_ROOT, data);

        // ================================================================
        // ★ 立即造成额外真实伤害
        //    公式：基础%(默认5%) × 层数 × (1 + 每层额外加成)
        //    简化：直接 = 基础% × 层数 × (1 + (层数-1)×额外加成%)
        //    默认：层数1=5%, 层数2=10%, 层数3=15%
        //    人权剑Buff：基础%改为配置值(默认8%)
        //              层数1=8%, 层数2=16%, 层数3=24%
        // ================================================================
        boolean buffed = data.getBoolean(KEY_BUFFED);
        double basePercent = buffed
                ? Config.eternalSparkActiveDamagePercent
                : Config.eternalSparkBaseDamagePercent;
        // 每层独立加成：实际值 = basePercent × stacks × (1 + (stacks-1) × stackBonus / 100)
        // 简化公式为可直接配置理解的乘积形式
        double stackMultiplier = 1.0 + (stacks - 1) * (Config.eternalSparkStackBonusPercent / 100.0);
        double totalPercent = basePercent * stacks * stackMultiplier;

        float extraDamage = (float) (target.getMaxHealth() * (totalPercent / 100.0));
        applyTrueDamage(target, extraDamage);

        // 粒子反馈
        spawnParticles(target);
    }

    // ================================================================
    // LivingDeathEvent：带星火标记的目标死亡 → 扩散到周围敌对生物
    // ================================================================
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) return;

        CompoundTag deadData = dead.getPersistentData();
        if (!deadData.contains(TAG_ROOT)) return;

        CompoundTag spark = deadData.getCompound(TAG_ROOT);
        int stacks = spark.getInt(KEY_STACKS);
        if (stacks <= 0) return;

        boolean buffed = spark.getBoolean(KEY_BUFFED);
        double range = buffed
                ? Config.eternalSparkSpreadRange * 2.0
                : Config.eternalSparkSpreadRange;

        Vec3 pos = dead.position();
        ServerLevel sl = (ServerLevel) dead.level();

        // 粒子冲击波
        spawnSpreadEffect(sl, pos, range);

        // 查找范围敌对生物
        AABB box = new AABB(
                pos.x - range, pos.y - range, pos.z - range,
                pos.x + range, pos.y + range, pos.z + range);
        List<LivingEntity> nearby = sl.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != dead && !e.isDeadOrDying() && e.isPickable()
                        && !(e instanceof Player) && isHostile(e));

        if (nearby.isEmpty()) return;

        for (LivingEntity next : nearby) {
            CompoundTag tag = next.getPersistentData().getCompound(TAG_ROOT);
            int cur = tag.getInt(KEY_STACKS);
            // 已有标记则叠加（上限3），无标记则从1层开始
            tag.putInt(KEY_STACKS, Math.min(MAX_STACKS, cur + 1));
            // 继承Buff标记
            if (buffed) tag.putBoolean(KEY_BUFFED, true);
            next.getPersistentData().put(TAG_ROOT, tag);

            spawnParticles(next);
        }
    }
}