package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;

import java.util.List;
import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【拂晓重制】附魔事件处理器
//
// 事件清单：
//   1. LivingDeathEvent    → 击杀记录 + Boss 倍率 + 击杀吸血 + 范围溅射
//   2. LivingHurtEvent     → 伤害加成 + 低血处决 + 暴击系统
//   3. ItemTooltipEvent    → 物品名显示等级，lore 显示各项数值
//   4. LivingEquipmentChangeEvent → 攻击距离 AttributeModifier
//
// 新机制：
//   A. 低血处决：目标剩余生命 ≤ 血线（默认 15%）时，本击必定暴击
//      （按暴击伤害计算）且额外 × 处决倍率（默认 2.0）
//   B. 击杀吸血：击杀时回复 = 目标最大生命 × 5%
//   C. 击杀溅射：击杀时对周围 3 格内非玩家敌人造成 =
//      目标最大生命 × 50% 的魔法伤害
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class DawnHandler {

    // ========================================================================
    // 固定 UUID 用于 AttributeModifier（避免重复叠加）
    // ========================================================================
    private static final UUID DAWN_REACH_UUID =
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID PIERCE_SPEED_UUID =
            UUID.fromString("d1d2d3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID PIERCE_REACH_UUID =
            UUID.fromString("d2e3f4a5-b6c7-8901-def2-345678901234");

    // ========================================================================
    // 事件1：LivingDeathEvent → 击杀记录
    // ========================================================================
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // 获取被击杀实体的攻击者
        DamageSource source = event.getSource();
        if (source == null) return;

        Entity attacker = source.getEntity();
        if (!(attacker instanceof Player player)) return;

        // 服务端才处理
        if (player.level().isClientSide()) return;

        // 检查主手武器是否有拂晓重制附魔
        ItemStack weapon = player.getMainHandItem();
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DAWN.get(), weapon);
        if (enchantLevel <= 0) return;

        // 被击杀的目标
        LivingEntity victim = event.getEntity();

        // 排除玩家（PVP 不计入成长）
        if (victim instanceof Player) return;

        // ========================================================================
        // 计算基础击杀成长值（默认 1.0）
        // ========================================================================
        double baseGrowth = 1.0;

        // ========================================================================
        // Boss 检测：末影龙、凋灵、监守者、或 ≥200 血
        // Boss 击杀额外 ×1.5~2.0 倍
        // ========================================================================
        if (isBoss(victim)) {
            double bossMultiplier = Config.dawnBossMultiplierMin
                    + player.level().random.nextDouble()
                    * (Config.dawnBossMultiplierMax - Config.dawnBossMultiplierMin);
            baseGrowth *= bossMultiplier;
        }

        // ========================================================================
        // 写入 PersistentData（服务器持久化）+ 同步到武器 NBT（客户端显示）
        // ========================================================================
        DawnData.addEffectiveKills(player, baseGrowth);

        // 将最新击杀数写入武器的 NBT（ItemStack NBT 会自动同步到客户端）
        double kills = DawnData.getEffectiveKills(player);
        DawnData.setItemKills(weapon, kills);

        // ========================================================================
        // 刺破长夜 · 连击爆发：击杀连击累计 + 阈值触发
        //   - 相邻击杀间隔 ≤ 连击窗口（默认 8 秒）→ 连击数 +1，否则重置为 1
        //   - 达到阈值（默认 3）且未激活、非冷却中 → 激活"刺破长夜"（类型 1）
        // ========================================================================
        if (Config.dawnPierceEnabled) {
            long now = player.level().getGameTime();
            long last = DawnData.getLastKillTime(player);
            boolean inWindow = (now - last) <= (long) (Config.dawnPierceComboWindowSeconds * 20);
            int combo = inWindow ? DawnData.getCombo(player) + 1 : 1;
            DawnData.setCombo(player, combo);
            DawnData.setLastKillTime(player, now);

            if (combo >= Config.dawnPierceComboThreshold
                    && DawnData.getCooldownTicks(player) == 0
                    && !DawnData.isPierceActive(player)) {
                activatePierce(player, 1);
            }
        }

        // ========================================================================
        // 击杀吸血：回复 = 目标最大生命 × 吸血比例（默认 5%）
        // heal() 会自动处理血量上限，不会溢出
        // ========================================================================
        if (Config.dawnLifestealEnabled && Config.dawnLifestealPercent > 0) {
            float healAmount = victim.getMaxHealth() * (float) Config.dawnLifestealPercent;
            player.heal(healAmount);
            // 播放治疗效果粒子（心形）
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HEART,
                        player.getX(), player.getY() + player.getBbHeight(), player.getZ(),
                        5, 0.3, 0.1, 0.3, 0.5);
            }
        }

        // ========================================================================
        // 击杀溅射：对周围 N 格内非玩家敌人造成 = 目标最大生命 × 溅射比例 的魔法伤害
        // 用 victim 的死亡位置作为溅射中心（与玩家位置无关，更直观）
        // ========================================================================
        if (Config.dawnSplashEnabled && Config.dawnSplashDamagePercent > 0) {
            float splashRadius = (float) Config.dawnSplashRadius;
            float splashDamage = victim.getMaxHealth() * (float) Config.dawnSplashDamagePercent;
            AABB splashBox = new AABB(
                    victim.getX() - splashRadius, victim.getY() - splashRadius, victim.getZ() - splashRadius,
                    victim.getX() + splashRadius, victim.getY() + splashRadius, victim.getZ() + splashRadius);
            List<LivingEntity> nearby = victim.level().getEntitiesOfClass(
                    LivingEntity.class, splashBox,
                    e -> e != victim
                            && e != player
                            && !(e instanceof Player)
                            && e.isAlive());
            for (LivingEntity entity : nearby) {
                entity.hurt(victim.damageSources().magic(), splashDamage);
            }
            // 播放溅射粒子（爆裂 + 辐射扩散）
            if (victim.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                        victim.getX(), victim.getY() + victim.getBbHeight() / 2, victim.getZ(),
                        1, 0.0, 0.0, 0.0, 0.0);
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        victim.getX(), victim.getY(), victim.getZ(),
                        20, splashRadius, 1.0, splashRadius, 0.1);
            }
        }
    }

    // ========================================================================
    // Boss 判定逻辑
    // ========================================================================
    private static boolean isBoss(LivingEntity entity) {
        // 末影龙 / 凋灵 / 监守者
        if (entity instanceof EnderDragon
                || entity instanceof WitherBoss
                || entity instanceof Warden) {
            return true;
        }
        // 最大生命值 ≥ 200
        return entity.getMaxHealth() >= Config.dawnBossHealthThreshold;
    }

    // ========================================================================
    // 处决粒子特效：目标被处决时播放（金色爆裂 + 附魔符文柱 + 火焰迸发）
    // 视觉上比普通暴击更醒目，用来反馈"处决成功"
    // ========================================================================
    private static void spawnExecuteParticles(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel serverLevel)) return;

        double x = target.getX();
        double y = target.getY() + target.getBbHeight() / 2;
        double z = target.getZ();

        // 金色爆裂：大量 CRIT 粒子从目标身体向外扩散
        serverLevel.sendParticles(ParticleTypes.CRIT, x, y, z,
                40, 0.8, 0.8, 0.8, 0.1);
        // 附魔符文柱：垂直喷涌上升
        serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z,
                30, 0.2, 0.8, 0.2, 0.6);
        // 火焰迸发：从目标脚下向上喷射
        serverLevel.sendParticles(ParticleTypes.FLAME, x, y, z,
                20, 0.5, 0.3, 0.5, 0.05);
    }

    // ========================================================================
    // 事件2：LivingHurtEvent → 伤害加成 + 暴击系统
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        // 获取被攻击目标（用于粒子效果位置）
        LivingEntity target = event.getEntity();

        // 检查武器附魔
        ItemStack weapon = player.getMainHandItem();
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DAWN.get(), weapon);
        if (enchantLevel <= 0) return;

        // 读取成长数据
        double kills = DawnData.getEffectiveKills(player);
        double critDamage = DawnData.getCritDamagePercent(kills);

        // ========================================================================
        // 伤害加成
        // ========================================================================
        double damageBonus = DawnData.getDamageBonusPercent(kills);
        if (damageBonus > 0) {
            float multiplier = 1.0f + (float) (damageBonus / 100.0);
            event.setAmount(event.getAmount() * multiplier);
        }

        // ========================================================================
        // 刺破长夜 · 状态伤害倍率 + 低血狂暴吸血
        //   type 1（连击爆发）：伤害 × 连击倍率（默认 1.5）
        //   type 2（低血狂暴）：伤害 × 低血倍率（默认 1.4）+ 按本次伤害吸血
        //   放在此处：与下方处决/暴击叠加，且不因处决提前 return 而丢失
        // ========================================================================
        if (Config.dawnPierceEnabled && DawnData.isPierceActive(player)) {
            int type = DawnData.getActiveType(player);
            if (type == 1) {
                event.setAmount(event.getAmount() * (float) Config.dawnPierceComboDamageMultiplier);
            } else if (type == 2) {
                event.setAmount(event.getAmount() * (float) Config.dawnPierceLowHpDamageMultiplier);
                // 低血狂暴吸血 = 本次造成伤害 × 吸血比例（默认 5%）
                float heal = event.getAmount() * (float) (Config.dawnPierceLowHpLifestealPercent / 100.0);
                player.heal(heal);
            }
        }

        // ========================================================================
        // 低血处决：目标剩余生命 ≤ 血线 → 本击必定暴击 + 额外倍率
        //
        // 机制说明：
        //   1. 血线来自配置（默认 15%）
        //   2. 处决伤害 = 已加成伤害 × (1 + 暴击伤害%) × 处决倍率
        //   3. 处决视为必暴击 → 重置伪概率累积，且不再进入下方普通暴击系统
        //   4. 玩家目标不被处决（PVP 保护），只对怪物生效
        // ========================================================================
        boolean isExecute = !(target instanceof Player)
                && Config.dawnExecuteThreshold > 0
                && target.getMaxHealth() > 0
                && target.getHealth() <= target.getMaxHealth() * Config.dawnExecuteThreshold;

        if (isExecute) {
            float executeMultiplier = 1.0f + (float) (critDamage / 100.0);
            executeMultiplier *= (float) Config.dawnExecuteDamageMultiplier;
            event.setAmount(event.getAmount() * executeMultiplier);
            // 处决即必定暴击，重置伪概率
            DawnData.setAccumulatedCrit(player, 0);
            // 播放处决特效
            spawnExecuteParticles(target);
            // return：处决已处理完毕，不进入普通暴击系统
            return;
        }

        // ========================================================================
        // 暴击系统（含伪概率机制）
        //
        // 机制说明：
        //   1. 跳劈（原版暴击）无视拂晓暴击系统，不累积也不消耗伪概率
        //   2. 普通攻击：基础暴击率 + 累积伪概率 → 判定暴击
        //      - 触发暴击 → 重置累积伪概率为 0
        //      - 未触发暴击 → 累积伪概率 +5%（上限到 100% 确保最终必暴击）
        //   3. 伪概率不显示在 lore 中
        // ========================================================================

        // 检测是否为跳劈（原版跳劈暴击）：玩家在空中且 fallDistance > 0
        boolean isJumpCrit = !player.onGround() && player.fallDistance > 0.0F;

        if (!isJumpCrit) {
            // 普通攻击 → 应用拂晓暴击系统（含伪概率）
            double baseCritRate = DawnData.getCritRatePercent(kills);
            double accumulatedCrit = DawnData.getAccumulatedCrit(player);
            double effectiveCritRate = baseCritRate + accumulatedCrit;

            if (effectiveCritRate > 0
                    && player.level().random.nextDouble() < (effectiveCritRate / 100.0)) {
                // 暴击触发 → 应用暴击伤害 + 重置累积概率
                float critMultiplier = 1.0f + (float) (critDamage / 100.0);
                event.setAmount(event.getAmount() * critMultiplier);
                DawnData.setAccumulatedCrit(player, 0);

                // 在目标位置播放自定义暴击粒子（CRIT + ENCHANTED_HIT）
                if (target.level() instanceof ServerLevel serverLevel) {
                    // 暴击星粒子：大量扩散
                    serverLevel.sendParticles(ParticleTypes.CRIT,
                            target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                            25, 0.5, 0.5, 0.5, 0.05);
                    // 附魔符文粒子：环绕上升
                    serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                            target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                            15, 0.3, 0.3, 0.3, 0.5);
                }
            } else {
                // 未触发暴击 → 累积 +5% 伪概率（上限 100%）
                double newAccumulated = accumulatedCrit + 5.0;
                DawnData.setAccumulatedCrit(player, Math.min(100.0, newAccumulated));
            }
        }
        // 跳劈：原版跳劈暴击已经自带 1.5x 加成，拂晓不做任何干涉
    }

    // ========================================================================
    // 刺破长夜 · 状态机：PlayerTickEvent
    //   - 递减激活/冷却 tick
    //   - 低血狂暴（类型 2）自动激活
    //   - 激活期间的移速+攻距 modifier（连击爆发型）管理
    //   - 激活期间持续粒子反馈
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide()) return;

        // 检查主手是否有拂晓附魔（附魔开关 + 全局开关）
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DAWN.get(), player.getMainHandItem());
        if (enchantLevel <= 0 || !Config.dawnPierceEnabled) {
            // 未装备拂晓或机制关闭 → 清除状态与 modifier，防止残留
            DawnData.setActiveTicks(player, 0);
            DawnData.setCombo(player, 0);
            removePierceModifiers(player);
            return;
        }

        // 递减激活剩余 tick
        int active = DawnData.getActiveTicks(player);
        if (active > 0) {
            DawnData.setActiveTicks(player, active - 1);
        }

        // 递减共享冷却剩余 tick
        int cooldown = DawnData.getCooldownTicks(player);
        if (cooldown > 0) {
            DawnData.setCooldownTicks(player, cooldown - 1);
        }

        int nowActive = DawnData.getActiveTicks(player);
        int nowCooldown = DawnData.getCooldownTicks(player);

        // 低血狂暴（类型 2）自动激活：生命 ≤ 阈值、未激活、非冷却、非创造
        if (nowActive == 0 && nowCooldown == 0 && !player.isCreative()
                && player.getHealth() <= player.getMaxHealth() * Config.dawnPierceLowHpThreshold) {
            activatePierce(player, 2);
            nowActive = DawnData.getActiveTicks(player);
        }

        // 连击爆发型（类型 1）效果 = 移速 + 攻距 modifier
        boolean activePiercing = nowActive > 0;
        if (activePiercing && DawnData.getActiveType(player) == 1) {
            applyPierceModifiers(player);
        } else {
            removePierceModifiers(player);
        }

        // 激活期间持续粒子反馈（每 4 tick 生成一次）
        if (activePiercing && player.tickCount % 4 == 0) {
            spawnActiveParticles(player, DawnData.getActiveType(player));
        }
    }

    // ========================================================================
    // 刺破长夜 · 激活入口：设置状态、消耗连击、启动共享冷却
    //   type: 1=连击爆发（伤害+移速+攻距） 2=低血狂暴（伤害+吸血）
    // ========================================================================
    private static void activatePierce(Player player, int type) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        // 设置状态
        DawnData.setActiveTicks(player, (int) (Config.dawnPierceDurationSeconds * 20));
        DawnData.setActiveType(player, type);
        DawnData.setCooldownTicks(player, (int) (Config.dawnPierceCooldownSeconds * 20));
        DawnData.setCombo(player, 0); // 消耗连击

        // 激活瞬间的强反馈：音效 + 粒子
        double x = player.getX();
        double y = player.getY() + player.getBbHeight() / 2;
        double z = player.getZ();
        if (type == 1) {
            // 连击爆发：金色符文爆开 + 响亮爆发音（LIGHTNING_BOLT_IMPACT 已验证为 SoundEvent）
            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z,
                    40, 0.6, 1.0, 0.6, 0.5);
            serverLevel.sendParticles(ParticleTypes.CRIT, x, y, z,
                    30, 0.8, 0.2, 0.8, 0.1);
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 2.0f, 1.2f);
        } else {
            // 低血狂暴：灵魂火蓝色爆发 + 低沉金属重击（IRON_GOLEM_ATTACK 已验证为 SoundEvent）
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z,
                    40, 0.6, 1.0, 0.6, 0.05);
            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z,
                    20, 0.5, 0.8, 0.5, 0.4);
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
    }

    /** 连击爆发型：应用移速 + 攻距 modifier */
    private static void applyPierceModifiers(Player player) {
        // 移动速度 +10%（固定比例加成）
        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(PIERCE_SPEED_UUID);
            AttributeModifier modifier = new AttributeModifier(
                    PIERCE_SPEED_UUID,
                    "Pierce night speed",
                    Config.dawnPierceComboMoveSpeedPercent / 100.0,
                    AttributeModifier.Operation.MULTIPLY_BASE
            );
            speedAttr.addTransientModifier(modifier);
        }
        // 攻击距离 +2 格
        AttributeInstance reachAttr = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (reachAttr != null) {
            reachAttr.removeModifier(PIERCE_REACH_UUID);
            AttributeModifier modifier = new AttributeModifier(
                    PIERCE_REACH_UUID,
                    "Pierce night reach",
                    Config.dawnPierceComboReachBonus,
                    AttributeModifier.Operation.ADDITION
            );
            reachAttr.addTransientModifier(modifier);
        }
    }

    /** 移除刺破长夜的移速/攻距 modifier */
    private static void removePierceModifiers(Player player) {
        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(PIERCE_SPEED_UUID);
        }
        AttributeInstance reachAttr = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (reachAttr != null) {
            reachAttr.removeModifier(PIERCE_REACH_UUID);
        }
    }

    /** 激活期间的持续粒子：脚下淡淡的光雾提示状态 */
    private static void spawnActiveParticles(Player player, int type) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        double x = player.getX();
        double y = player.getY() + 0.2;
        double z = player.getZ();
        if (type == 1) {
            // 连击爆发：稀疏散落的金色粒子
            serverLevel.sendParticles(ParticleTypes.CRIT, x, y, z,
                    2, 0.4, 0.1, 0.4, 0.02);
        } else {
            // 低血狂暴：脚下淡蓝色灵魂火粒子
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z,
                    2, 0.4, 0.1, 0.4, 0.01);
        }
    }

    // ========================================================================
    // 事件3：ItemTooltipEvent → 物品名显示等级，lore 显示各项数值
    // ========================================================================
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DAWN.get(), stack);
        if (enchantLevel <= 0) return;

        // ========================================================================
        // 从武器 NBT 读取成长数据（武器 NBT 自动同步到客户端）
        // 不读取 player.getPersistentData()，因为 PersistentData 不同步到客户端
        // ========================================================================
        double kills = DawnData.getItemKills(stack);
        int level = DawnData.getLevel(kills);

        // ========================================================================
        // 修改物品名 → 追加 [Lv.X]
        // ========================================================================
        List<Component> tooltip = event.getToolTip();
        if (!tooltip.isEmpty()) {
            Component originalName = tooltip.get(0);
            Component newName = originalName.copy()
                    .append(Component.literal(" ")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("[Lv." + level + "]")
                            .withStyle(ChatFormatting.GRAY));
            tooltip.set(0, newName);
        }

        // ========================================================================
        // 追加 lore 行（各项数值）
        // ========================================================================
        double dmgBonus = DawnData.getDamageBonusPercent(kills);
        double critRate = DawnData.getCritRatePercent(kills);
        double critDmg = DawnData.getCritDamagePercent(kills);

        tooltip.add(Component.literal("杀敌数: " + (int) kills)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("伤害加成: +" + String.format("%.1f", dmgBonus) + "%")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("暴击率: +" + String.format("%.1f", critRate) + "%")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("暴击伤害: +" + String.format("%.1f", critDmg) + "%")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("攻击距离: +" + String.format("%.1f", Config.dawnAttackRange) + "格")
                .withStyle(ChatFormatting.GRAY));

        // ========================================================================
        // 拂晓重制：新机制数值（处决 / 吸血 / 溅射，随配置显示）
        // ========================================================================
        tooltip.add(Component.literal("低血处决: 目标血量 ≤ "
                + String.format("%.0f", Config.dawnExecuteThreshold * 100) + "% 时必定暴击 ×"
                + String.format("%.1f", Config.dawnExecuteDamageMultiplier))
                .withStyle(ChatFormatting.GOLD));
        if (Config.dawnLifestealEnabled) {
            tooltip.add(Component.literal("击杀吸血: 回复目标最大生命 "
                    + String.format("%.0f", Config.dawnLifestealPercent * 100) + "%")
                    .withStyle(ChatFormatting.RED));
        }
        if (Config.dawnSplashEnabled) {
            tooltip.add(Component.literal("击杀溅射: 对周围 "
                    + String.format("%.1f", Config.dawnSplashRadius) + " 格敌人造成 "
                    + String.format("%.0f", Config.dawnSplashDamagePercent * 100) + "% 目标最大生命魔法伤害")
                    .withStyle(ChatFormatting.RED));
        }

        // ========================================================================
        // 刺破长夜：展示触发与效果数值（随配置动态显示）
        // ========================================================================
        if (Config.dawnPierceEnabled) {
            tooltip.add(Component.literal("刺破长夜: 连击 "
                    + Config.dawnPierceComboThreshold + " 或生命 ≤ "
                    + String.format("%.0f", Config.dawnPierceLowHpThreshold * 100) + "% 激活，持续 "
                    + String.format("%.0f", Config.dawnPierceDurationSeconds) + " 秒，冷却 "
                    + String.format("%.0f", Config.dawnPierceCooldownSeconds) + " 秒")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("  连击爆发: 伤害 ×"
                    + String.format("%.1f", Config.dawnPierceComboDamageMultiplier)
                    + " 移速+" + String.format("%.0f", Config.dawnPierceComboMoveSpeedPercent) + "%"
                    + " 攻距+" + String.format("%.1f", Config.dawnPierceComboReachBonus) + "格")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("  低血狂暴: 伤害 ×"
                    + String.format("%.1f", Config.dawnPierceLowHpDamageMultiplier)
                    + " 吸血" + String.format("%.0f", Config.dawnPierceLowHpLifestealPercent) + "%")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // ========================================================================
    // 事件4：LivingEquipmentChangeEvent → 动态管理 AttributeModifier
    // ========================================================================
    @SubscribeEvent
    public static void onEquipmentChange(
            net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent event) {
        // 仅监听主手
        if (event.getSlot() != EquipmentSlot.MAINHAND) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        ItemStack from = event.getFrom();
        ItemStack to = event.getTo();

        boolean hadEnchant = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DAWN.get(), from) > 0;
        boolean hasEnchant = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DAWN.get(), to) > 0;

        if (hasEnchant && !hadEnchant) {
            // 刚装备上拂晓武器 → 从 PersistentData 复制数据到武器 NBT + 应用 modifier
            double kills = DawnData.getEffectiveKills(player);
            DawnData.setItemKills(to, kills);
            applyAllModifiers(player);
        } else if (!hasEnchant && hadEnchant) {
            // 卸下拂晓武器 → 移除所有 modifier
            removeAllModifiers(player);
        } else if (hasEnchant && hadEnchant) {
            // 换另一把拂晓武器 → 从 PersistentData 复制到新武器 + 刷新 modifier
            double kills = DawnData.getEffectiveKills(player);
            DawnData.setItemKills(to, kills);
            removeAllModifiers(player);
            applyAllModifiers(player);
        }
    }

    // ========================================================================
    // AttributeModifier 管理方法
    // ========================================================================

    /** 应用所有拂晓 modifier（攻击距离） */
    private static void applyAllModifiers(Player player) {
        applyReachModifier(player);
    }

    /** 应用攻击距离 modifier（固定 +2.5 格） */
    private static void applyReachModifier(Player player) {
        AttributeInstance reachAttr = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (reachAttr == null) return;

        // 先移除旧的再添加新的
        reachAttr.removeModifier(DAWN_REACH_UUID);
        AttributeModifier modifier = new AttributeModifier(
                DAWN_REACH_UUID,
                "Dawn reach bonus",
                Config.dawnAttackRange,
                AttributeModifier.Operation.ADDITION
        );
        reachAttr.addTransientModifier(modifier);
    }

    /** 移除所有拂晓 modifier（攻击距离） */
    private static void removeAllModifiers(Player player) {
        AttributeInstance reachAttr = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (reachAttr != null) {
            reachAttr.removeModifier(DAWN_REACH_UUID);
        }
    }
}