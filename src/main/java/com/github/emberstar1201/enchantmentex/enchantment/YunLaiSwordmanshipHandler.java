package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 云来剑法 / 古·云来剑法 事件处理器
//
// 【职责】
//   在 PlayerTickEvent 中检测玩家主手武器是否携带云来剑法或古·云来剑法，
//   若持有则添加攻击速度（ATTACK_SPEED）和攻击距离（ATTACK_REACH）修饰符，
//   若不再持有则移除修饰符。
//
// 【技术方案】
//   使用 UUID 标识修饰符，每次 tick 检查主手物品，
//   需要时添加，不需要时移除。
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