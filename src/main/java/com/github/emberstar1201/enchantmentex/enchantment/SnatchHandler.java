package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【攫取】附魔事件处理器（v2.0 缴械版）
//
// 触发时机：LivingDamageEvent —— 伤害实际造成之前（护甲计算之后）。
//   选择 LivingDamageEvent 而非 LivingHurtEvent：
//     - LivingHurtEvent 在护甲/抗性计算前触发，可能被护甲减伤到 0
//     - LivingDamageEvent 在最终伤害计算后触发，确保只有实际造成伤害才缴械
//   （即便伤害被全部吸收，缴械逻辑也合理：玩家击中目标 → 触发缴械）
//
// 核心流程：
//   1. 从 DamageSource 取攻击者，若不是玩家（或攻击者不是 LivingEntity）直接返回
//   2. 检查攻击者主手武器上的"攫取"附魔等级，无附魔则返回
//   3. 【武器缴械判定】
//        - 概率 P_w = SnatchEnchantment.getTriggerChance(level)
//        - 若 ROLL 成功 且 目标主手有物品 → spawnAtLocation + 清空主手槽
//   4. 【盔甲缴械判定】（在武器缴械之后独立判定）
//        - 概率 P_a = P_w / 2（向下取整，由 SnatchEnchantment.getArmorTriggerChance 计算）
//        - 若 ROLL 成功，从目标的 4 个护甲槽中"随机选一件非空"打落
//        - 没有非空护甲槽则不触发
//
// spawnAtLocation：LivingEntity 原生方法，在实体位置生成 ItemEntity 并返回，
//   保留物品 NBT（直接 copy 原 ItemStack），玩家可拾取。
//
// 客户端保护：本处理器涉及生成实体、修改装备槽，仅服务端执行。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class SnatchHandler {

    // 4 个护甲槽，用于"随机选择一件打落"
    private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        Level level = target.level();

        // 仅服务端执行（生成 ItemEntity、修改装备槽等服务端逻辑）
        if (level.isClientSide()) {
            return;
        }

        // 1. 判断攻击者是否是玩家（用户要求"攻击者武器是否附有攫取"）
        //    source.getEntity() 在近战攻击时返回攻击者本身
        if (event.getSource() == null || !(event.getSource().getEntity() instanceof Player attacker)) {
            return;
        }

        // 2. 检查攻击者主手武器是否有"攫取"附魔
        ItemStack weaponStack = attacker.getMainHandItem();
        int snatchLevel = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.SNATCH.get(), weaponStack);
        if (snatchLevel <= 0) {
            return;
        }

        // 3. 取武器缴械与盔甲缴械概率
        double weaponChance = SnatchEnchantment.getTriggerChance(snatchLevel);
        double armorChance = SnatchEnchantment.getArmorTriggerChance(snatchLevel);

        // ========================================================================
        // 【武器缴械】先判定
        // ========================================================================
        // 目标主手持有的物品（保留 NBT/附魔/耐久）
        ItemStack mainHandStack = target.getMainHandItem();
        if (!mainHandStack.isEmpty()) {
            if (level.random.nextDouble() < weaponChance) {
                // 4a. 在目标位置生成掉落物（玩家可拾取）
                //     spawnAtLocation 内部会 copy ItemStack，保留所有 NBT
                //     返回生成的 ItemEntity（生成失败时返回 null，但通常不会失败）
                target.spawnAtLocation(mainHandStack.copy());
                // 4b. 清空目标主手槽位 → 目标进入空手状态
                target.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
        }

        // ========================================================================
        // 【盔甲缴械】后判定，与武器缴械独立
        // ========================================================================
        // 先收集所有非空护甲槽，再从中随机选一个打落（避免连续选到空槽）
        List<EquipmentSlot> nonEmptyArmorSlots = new ArrayList<>();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!target.getItemBySlot(slot).isEmpty()) {
                nonEmptyArmorSlots.add(slot);
            }
        }

        // 仅当目标有非空护甲时才进行 ROLL（避免对没护甲的目标触发概率浪费）
        if (!nonEmptyArmorSlots.isEmpty()) {
            if (level.random.nextDouble() < armorChance) {
                // 随机选择一个非空护甲槽打落
                EquipmentSlot chosenSlot = nonEmptyArmorSlots.get(
                        level.random.nextInt(nonEmptyArmorSlots.size()));
                ItemStack armorStack = target.getItemBySlot(chosenSlot);

                // 5a. 在目标位置生成掉落物（玩家可拾取）
                target.spawnAtLocation(armorStack.copy());
                // 5b. 清空对应护甲槽位
                target.setItemSlot(chosenSlot, ItemStack.EMPTY);
            }
        }
    }
}
