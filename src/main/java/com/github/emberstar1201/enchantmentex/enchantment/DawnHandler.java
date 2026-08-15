package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
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

import java.util.List;
import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【拂晓重制】附魔事件处理器
//
// 事件清单：
//   1. LivingDeathEvent    → 击杀记录 + Boss 倍率
//   2. LivingHurtEvent     → 伤害加成 + 暴击系统
//   3. ItemTooltipEvent    → 物品名显示等级，lore 显示各项数值
//   4. LivingEquipmentChangeEvent → 攻击距离 + 攻速惩罚 AttributeModifier
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class DawnHandler {

    // ========================================================================
    // 固定 UUID 用于 AttributeModifier（避免重复叠加）
    // ========================================================================
    private static final UUID DAWN_REACH_UUID =
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID DAWN_SPEED_UUID =
            UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

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
        // 击杀后立即刷新攻速惩罚 AttributeModifier（因为有效击杀数变了）
        // ========================================================================
        updateAttackSpeedModifier(player, kills);
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

        // ========================================================================
        // 伤害加成
        // ========================================================================
        double damageBonus = DawnData.getDamageBonusPercent(kills);
        if (damageBonus > 0) {
            float multiplier = 1.0f + (float) (damageBonus / 100.0);
            event.setAmount(event.getAmount() * multiplier);
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

        double critDamage = DawnData.getCritDamagePercent(kills);

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
        double spdPenalty = DawnData.getAttackSpeedPenaltyPercent(kills);

        tooltip.add(Component.literal("杀敌数: " + (int) kills)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("伤害加成: +" + String.format("%.1f", dmgBonus) + "%")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("暴击率: +" + String.format("%.1f", critRate) + "%")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("暴击伤害: +" + String.format("%.1f", critDmg) + "%")
                .withStyle(ChatFormatting.GRAY));
        if (spdPenalty > 0) {
            tooltip.add(Component.literal("攻速: -" + String.format("%.1f", spdPenalty) + "%")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.literal("攻击距离: +" + String.format("%.1f", Config.dawnAttackRange) + "格")
                .withStyle(ChatFormatting.GRAY));
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
            // 刚装备上拂晓武器 → 从 PersistentData 复制数据到武器 NBT + 应用所有 modifier
            double kills = DawnData.getEffectiveKills(player);
            DawnData.setItemKills(to, kills);
            applyAllModifiers(player, kills);
        } else if (!hasEnchant && hadEnchant) {
            // 卸下拂晓武器 → 移除所有 modifier
            removeAllModifiers(player);
        } else if (hasEnchant && hadEnchant) {
            // 换另一把拂晓武器 → 从 PersistentData 复制到新武器 + 刷新 modifier
            double kills = DawnData.getEffectiveKills(player);
            DawnData.setItemKills(to, kills);
            removeAllModifiers(player);
            applyAllModifiers(player, kills);
        }
    }

    // ========================================================================
    // AttributeModifier 管理方法
    // ========================================================================

    /** 应用所有拂晓 modifier（攻击距离 + 攻速惩罚） */
    private static void applyAllModifiers(Player player, double kills) {
        applyReachModifier(player);
        updateAttackSpeedModifier(player, kills);
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

    /** 更新攻速惩罚 modifier（随击杀数动态变化） */
    private static void updateAttackSpeedModifier(Player player, double kills) {
        AttributeInstance speedAttr = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speedAttr == null) return;

        // 移除旧的 modifier
        speedAttr.removeModifier(DAWN_SPEED_UUID);

        // 计算当前攻速惩罚
        double penaltyPercent = DawnData.getAttackSpeedPenaltyPercent(kills);
        if (penaltyPercent > 0) {
            // 使用 MULTIPLY_BASE 操作：攻速 × (1 - penaltyPercent/100)
            AttributeModifier modifier = new AttributeModifier(
                    DAWN_SPEED_UUID,
                    "Dawn speed penalty",
                    -penaltyPercent / 100.0,
                    AttributeModifier.Operation.MULTIPLY_BASE
            );
            speedAttr.addTransientModifier(modifier);
        }
    }

    /** 移除所有拂晓 modifier */
    private static void removeAllModifiers(Player player) {
        AttributeInstance reachAttr = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (reachAttr != null) {
            reachAttr.removeModifier(DAWN_REACH_UUID);
        }
        AttributeInstance speedAttr = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(DAWN_SPEED_UUID);
        }
    }
}