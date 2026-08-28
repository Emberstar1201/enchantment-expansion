package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.entity.CrescentEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 云来剑法 / 古·云来剑法 事件处理器
//
// 【职责】
//   1. PlayerTickEvent 中检测玩家主手武器是否携带云来剑法或古·云来剑法，
//      若持有则添加攻击速度（ATTACK_SPEED）和攻击距离（ATTACK_REACH）修饰符，
//      若不再持有则移除修饰符。
//   2. LivingHurtEvent 中按附魔伤害倍率放大造成伤害（提升输出价值）。
//
// 【技术方案】
//   使用 UUID 标识修饰符，每次 tick 检查主手物品，
//   需要时添加，不需要时移除。
//   伤害倍率与"终末将至/拂晓重制"同为 NORMAL 优先级，
//   倍率乘算（乘法交换律，顺序无关），可正常叠加。
//
// 【修饰符 UUID（固定，用于标识）】
//   云来剑法 - 攻速：f5a8d4c2-9b7e-4e3c-8d1f-6a2b5c4d3e2f
//   云来剑法 - 攻击距离：a2b1c3d4-e5f6-7890-abcd-ef1234567890
//   古·云来剑法 - 攻速：b3c2d1e4-f5a6-7890-bcde-f12345678901
//   古·云来剑法 - 攻击距离：c4d3e2f1-a5b6-7890-cdef-123456789012
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class YunLaiSwordmanshipHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ============================================================
    // 修饰符 UUID（固定值，不可变）
    // ============================================================
    // 云来剑法
    private static final UUID YUNLAI_SPEED_MODIFIER_UUID =
            UUID.fromString("f5a8d4c2-9b7e-4e3c-8d1f-6a2b5c4d3e2f");
    private static final UUID YUNLAI_REACH_MODIFIER_UUID =
            UUID.fromString("a2b1c3d4-e5f6-7890-abcd-ef1234567890");

    // 古·云来剑法
    private static final UUID ANCIENT_YUNLAI_SPEED_MODIFIER_UUID =
            UUID.fromString("b3c2d1e4-f5a6-7890-bcde-f12345678901");
    private static final UUID ANCIENT_YUNLAI_REACH_MODIFIER_UUID =
            UUID.fromString("c4d3e2f1-a5b6-7890-cdef-123456789012");

    // 修饰符名称（用在 AttributeModifier 中）
    private static final String YUNLAI_SPEED_NAME = "YunLaiSwordmanship Speed Bonus";
    private static final String YUNLAI_REACH_NAME = "YunLaiSwordmanship Reach Bonus";
    private static final String ANCIENT_YUNLAI_SPEED_NAME = "AncientYunLaiSwordmanship Speed Bonus";
    private static final String ANCIENT_YUNLAI_REACH_NAME = "AncientYunLaiSwordmanship Reach Bonus";

    // ============================================================
    // PlayerTickEvent：检查主手武器并管理修饰符
    //   ★ 仅服务端处理（修饰符在服务端管理）
    // ============================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 仅服务端
        if (event.player.level().isClientSide()) return;
        // 仅处理 START 阶段（每个 tick 一次）
        if (event.phase != TickEvent.Phase.START) return;

        Player player = event.player;
        ItemStack mainHand = player.getMainHandItem();

        // 检测云来剑法等级
        int yunlaiLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.YUNLAI_SWORDMANSHIP.get(), mainHand);
        boolean hasYunLai = yunlaiLevel > 0;

        // 检测古·云来剑法等级
        int ancientLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.ANCIENT_YUNLAI_SWORDMANSHIP.get(), mainHand);
        boolean hasAncientYunLai = ancientLevel > 0;

        // 云来剑法：管理修饰符
        manageModifier(player, Attributes.ATTACK_SPEED,
                YUNLAI_SPEED_MODIFIER_UUID, YUNLAI_SPEED_NAME,
                Config.yunlaiSwordmanshipAttackSpeed, hasYunLai);
        manageModifier(player, ForgeMod.ENTITY_REACH.get(),
                YUNLAI_REACH_MODIFIER_UUID, YUNLAI_REACH_NAME,
                Config.yunlaiSwordmanshipAttackReach, hasYunLai);

        // 古·云来剑法：管理修饰符
        manageModifier(player, Attributes.ATTACK_SPEED,
                ANCIENT_YUNLAI_SPEED_MODIFIER_UUID, ANCIENT_YUNLAI_SPEED_NAME,
                Config.ancientYunlaiSwordmanshipAttackSpeed, hasAncientYunLai);
        manageModifier(player, ForgeMod.ENTITY_REACH.get(),
                ANCIENT_YUNLAI_REACH_MODIFIER_UUID, ANCIENT_YUNLAI_REACH_NAME,
                Config.ancientYunlaiSwordmanshipAttackReach, hasAncientYunLai);

        // 调试日志（仅当附魔存在时，避免刷屏）
        if (hasYunLai || hasAncientYunLai) {
            LOGGER.debug("[YunLaiSword] yunlai={}, ancient={}, speed={}, reach={}",
                    hasYunLai, hasAncientYunLai,
                    Config.yunlaiSwordmanshipAttackSpeed,
                    Config.yunlaiSwordmanshipAttackReach);
        }
    }

    // ============================================================
    // 【伤害倍率 + 剑气波】LivingHurtEvent
    //   玩家主手武器带有云来剑法/古·云来剑法时：
    //   ① 按配置倍率放大近战伤害（原有效果保留）
    //   ② 按配置概率向玩家视线方向发射一道月牙形剑气（剑气波）
    //      云来剑法 → 淡蓝剑气；古·云来剑法 → 金色剑气（射程/伤害更高）
    //   两把剑法互斥，同一时间至多一种生效（先查云来，再查古·云来）。
    // ============================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // 伤害来源必须是玩家近战攻击（剑气自身造成的伤害不会再触发，防止递归）
        if (!(event.getSource().getEntity() instanceof Player attacker)) return;
        if (attacker.level().isClientSide()) return;
        // ★ 防止剑气波伤害再次触发剑气：剑气命中造成的伤害来源实体是剑气本身
        if (event.getSource().getDirectEntity() instanceof CrescentEntity) return;

        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty()) return;

        double multiplier = 1.0;
        int swordQiType = 0; // 0=不发射，1=云来剑气，2=古·云来剑气

        // 云来剑法：伤害 ×1.10 + 剑气波
        int yunlaiLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.YUNLAI_SWORDMANSHIP.get(), weapon);
        if (yunlaiLevel > 0) {
            multiplier = Config.yunlaiSwordmanshipDamageMultiplier;
            swordQiType = 1;
        } else {
            // 古·云来剑法：伤害 ×1.50（两把互斥，此处不叠加）
            int ancientLevel = EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.ANCIENT_YUNLAI_SWORDMANSHIP.get(), weapon);
            if (ancientLevel > 0) {
                multiplier = Config.ancientYunlaiSwordmanshipDamageMultiplier;
                swordQiType = 2;
            }
        }

        if (multiplier > 1.0) {
            event.setAmount(event.getAmount() * (float) multiplier);
        }

        // 剑气波：按概率发射（基于最终伤害计算剑气伤害）
        if (swordQiType != 0) {
            spawnSwordQi(attacker, weapon, event.getAmount(), swordQiType);
        }
    }

    // ============================================================
    // 【剑气波发射】向玩家视线方向发射一道月牙形剑气
    //
    // 技术方案：
    //   使用项目中已有的 CrescentEntity（月牙剑气实体）：
    //   - 无重力直线飞行、穿透敌人、到达射程自动消失
    //   - type=1 淡蓝色（云来），type=2 金色（古·云来）
    //
    // 发射参数（来自 Config）：
    //   云来：概率 60%，伤害=本次攻击×0.5，射程 6 格，速度 0.75 格/tick
    //   古·云来：概率 75%，伤害=本次攻击×0.75，射程 10 格，速度 1.0 格/tick
    // ============================================================
    private static void spawnSwordQi(Player attacker, ItemStack weapon,
                                     float attackDamage, int type) {
        if (!(attacker.level() instanceof ServerLevel)) return;

        double chance, damageMultiplier, range, speed;
        if (type == 1) {
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
        if (attacker.getRandom().nextDouble() > chance) return;

        // 剑气伤害：以本次攻击伤害为基准 × 配置倍率
        float swordQiDamage = attackDamage * (float) damageMultiplier;

        // 发射起点：玩家眼睛位置（跟随视线）
        Vec3 eyePos = attacker.getEyePosition();
        // 发射方向：玩家视线方向
        Vec3 lookDir = attacker.getLookAngle();

        CrescentEntity crescent = new CrescentEntity(
                attacker.level(), attacker,
                eyePos, lookDir,
                (float) speed, swordQiDamage, range, type);
        attacker.level().addFreshEntity(crescent);
    }

    // ============================================================
    // 统一修饰符管理方法
    //   如果 shouldHave 为 true 且修饰符不存在 → 添加
    //   如果 shouldHave 为 false 且修饰符存在 → 移除
    // ============================================================
    private static void manageModifier(Player player, Attribute attribute,
                                        UUID modifierUuid, String modifierName,
                                        double value, boolean shouldHave) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        AttributeModifier existing = instance.getModifier(modifierUuid);

        if (shouldHave) {
            // 需要修饰符 → 检查是否已存在（值变化时需替换）
            if (existing == null) {
                // 不存在 → 添加
                instance.addTransientModifier(
                        new AttributeModifier(modifierUuid, modifierName, value,
                                AttributeModifier.Operation.ADDITION));
            } else if (existing.getAmount() != value) {
                // 存在但值变化 → 移除后重新添加
                instance.removeModifier(modifierUuid);
                instance.addTransientModifier(
                        new AttributeModifier(modifierUuid, modifierName, value,
                                AttributeModifier.Operation.ADDITION));
            }
            // 存在且值相同 → 不做任何操作
        } else {
            // 不需要修饰符 → 如果存在则移除
            if (existing != null) {
                instance.removeModifier(modifierUuid);
            }
        }
    }
}