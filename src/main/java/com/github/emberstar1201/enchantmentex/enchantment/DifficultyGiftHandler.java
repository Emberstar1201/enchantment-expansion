package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.DifficultyGiftConfig;
import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

// ========================================================================
// 【难度馈赠】事件处理器
//
// 两个核心事件：
//   A. LivingHurtEvent：玩家手持带「难度馈赠」的武器攻击时，
//      根据当前游戏难度增加固定伤害。
//   B. PlayerTickEvent：动态管理属性修饰符，
//      手持武器 → 攻击速度加成；穿着盔甲 → 护甲 + 韧性加成。
//
// 原理：
//   - 难度值通过 player.level().getDifficulty() 获取（DedicatedServer 端）。
//   - 属性修饰符使用「固定 UUID」方案，难度变化或装备变化时
//     对比现有修饰符，只做必要的新增/移除/替换，避免重复叠加。
//   - 和平难度时所有修饰符会被移除（无加成）。
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DifficultyGiftHandler {

    // 固定 UUID：攻击速度 / 护甲 / 韧性（避免重复叠加）
    private static final UUID ATTACK_SPEED_UUID =
            UUID.fromString("6f2b6a10-6c3d-4a91-9f1e-6f00000d");
    private static final UUID ARMOR_UUID =
            UUID.fromString("6f2b6a10-6c3d-4a91-9f1e-6f00000a");
    private static final UUID TOUGHNESS_UUID =
            UUID.fromString("6f2b6a10-6c3d-4a91-9f1e-6f00000c");
    // 4 个盔甲槽独立 UUID：实现"每件难度馈赠盔甲单独提供加成"
    //   HEAD : 困难满套贡献 7.5 HP 等
    private static final UUID HEALTH_HEAD_UUID =
            UUID.fromString("d6e5d5a1-1000-4d00-8888-100000000001");
    private static final UUID HEALTH_CHEST_UUID =
            UUID.fromString("d6e5d5a1-1000-4d00-8888-100000000002");
    private static final UUID HEALTH_LEGS_UUID =
            UUID.fromString("d6e5d5a1-1000-4d00-8888-100000000003");
    private static final UUID HEALTH_FEET_UUID =
            UUID.fromString("d6e5d5a1-1000-4d00-8888-100000000004");

    // ========================================================================
    // 事件 A：武器伤害加成
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!DifficultyGiftConfig.isEnabled()) return;

        DamageSource source = event.getSource();
        // 仅当伤害来源是玩家（手持武器攻击）
        if (!(source.getEntity() instanceof Player attacker)) return;
        if (attacker.level().isClientSide()) return;

        // 主手/副手武器带「难度馈赠」才生效
        ItemStack weapon = attacker.getMainHandItem();
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DIFFICULTY_GIFT.get(), weapon);
        if (level <= 0) {
            weapon = attacker.getOffhandItem();
            level = EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.DIFFICULTY_GIFT.get(), weapon);
        }
        if (level <= 0) return;

        double bonus = DifficultyGiftConfig.getDamageBonus(attacker.level().getDifficulty());
        if (bonus > 0) {
            event.setAmount(event.getAmount() + (float) bonus);
        }
    }

    // ========================================================================
    // 事件 B：属性修饰符动态管理（攻速 / 护甲 / 韧性）
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide()) return;
        if (!DifficultyGiftConfig.isEnabled()) return;

        Difficulty difficulty = player.level().getDifficulty();
        boolean peaceful = difficulty == Difficulty.PEACEFUL;

        // ----------------------------------------------------------------
        // 1. 攻击速度修饰符：手持武器带附魔时添加
        // ----------------------------------------------------------------
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        boolean hasWeapon = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DIFFICULTY_GIFT.get(), mainHand) > 0
                || EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DIFFICULTY_GIFT.get(), offHand) > 0;

        double speedBonus = peaceful ? 0.0 : DifficultyGiftConfig.getAttackSpeedBonus(difficulty);
        manageModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_UUID,
                "difficulty_gift_attack_speed", speedBonus, hasWeapon && speedBonus > 0);

        // ----------------------------------------------------------------
        // 2. 护甲 / 韧性修饰符：任意盔甲部位带附魔时添加
        // ----------------------------------------------------------------
        boolean hasArmor = hasEnchantedArmor(player);
        double armorBonus = peaceful ? 0.0 : DifficultyGiftConfig.getArmorBonus(difficulty);
        double toughnessBonus = peaceful ? 0.0 : DifficultyGiftConfig.getToughnessBonus(difficulty);

        manageModifier(player, Attributes.ARMOR, ARMOR_UUID,
                "difficulty_gift_armor", armorBonus, hasArmor && armorBonus > 0);
        manageModifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_UUID,
                "difficulty_gift_toughness", toughnessBonus, hasArmor && toughnessBonus > 0);

        // ----------------------------------------------------------------
        // 3. 最大生命值加成：每件难度馈赠盔甲按"单件数值"独立加成。
        //    装备难度变化或附魔变化后，modifier 自动增删并裁剪当前血量避免溢出。
        // ----------------------------------------------------------------
        double healthPerArmor = peaceful ? 0.0
                : DifficultyGiftConfig.getMaxHealthBonusPerArmor(difficulty);
        boolean needClamp = applyOrRemoveHealthModifier(player,
                EquipmentSlot.HEAD, HEALTH_HEAD_UUID, "difficulty_gift_health_head",
                healthPerArmor);
        needClamp |= applyOrRemoveHealthModifier(player,
                EquipmentSlot.CHEST, HEALTH_CHEST_UUID, "difficulty_gift_health_chest",
                healthPerArmor);
        needClamp |= applyOrRemoveHealthModifier(player,
                EquipmentSlot.LEGS, HEALTH_LEGS_UUID, "difficulty_gift_health_legs",
                healthPerArmor);
        needClamp |= applyOrRemoveHealthModifier(player,
                EquipmentSlot.FEET, HEALTH_FEET_UUID, "difficulty_gift_health_feet",
                healthPerArmor);
        if (needClamp) {
            // 脱装备或难度下降时，避免角色"当前血量 > 新上限"不掉血。
            float max = player.getMaxHealth();
            if (player.getHealth() > max) {
                player.setHealth(max);
            }
        }
    }

    /** 检查玩家任意盔甲部位是否带「难度馈赠」 */
    private static boolean hasEnchantedArmor(Player player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;
            ItemStack stack = player.getItemBySlot(slot);
            if (EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.DIFFICULTY_GIFT.get(), stack) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 统一修饰符管理方法：
     * 需要修饰符且不存在/值变化 → 添加或替换；
     * 不需要修饰符且存在 → 移除。
     */
    private static void manageModifier(Player player, Attribute attribute,
                                       UUID modifierUuid, String modifierName,
                                       double value, boolean shouldHave) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        AttributeModifier existing = instance.getModifier(modifierUuid);

        if (shouldHave) {
            if (existing == null) {
                instance.addTransientModifier(
                        new AttributeModifier(modifierUuid, modifierName, value,
                                AttributeModifier.Operation.ADDITION));
            } else if (existing.getAmount() != value) {
                // 难度变化导致数值变化 → 替换
                instance.removeModifier(modifierUuid);
                instance.addTransientModifier(
                        new AttributeModifier(modifierUuid, modifierName, value,
                                AttributeModifier.Operation.ADDITION));
            }
        } else {
            if (existing != null) {
                instance.removeModifier(modifierUuid);
            }
        }
    }

    /**
     * 单件难度馈赠盔甲的生命值加成。
     * 返回值 true 表示 modifier 发生变化（需调用者进行血量上限裁剪）。
     */
    private static boolean applyOrRemoveHealthModifier(Player player,
                                                        EquipmentSlot slot,
                                                        UUID uuid, String name,
                                                        double healthPerArmor) {
        if (slot.getType() != EquipmentSlot.Type.ARMOR) return false;
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return false;
        ItemStack stack = player.getItemBySlot(slot);
        int ench = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DIFFICULTY_GIFT.get(), stack);
        double value = (ench > 0 && healthPerArmor > 0) ? healthPerArmor : 0.0;
        AttributeModifier existing = attr.getModifier(uuid);
        boolean changed = false;
        if (value > 0) {
            if (existing == null) {
                attr.addTransientModifier(new AttributeModifier(
                        uuid, name, value, AttributeModifier.Operation.ADDITION));
                changed = true;
            } else if (existing.getAmount() != value) {
                attr.removeModifier(uuid);
                attr.addTransientModifier(new AttributeModifier(
                        uuid, name, value, AttributeModifier.Operation.ADDITION));
                changed = true;
            }
        } else {
            if (existing != null) {
                attr.removeModifier(uuid);
                changed = true;
            }
        }
        return changed;
    }
}