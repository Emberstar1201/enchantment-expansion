package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 【疾跑节能】(Sprint Endurance) 附魔 - 护腿专属
//
// 效果：疾跑时抵消疾跑所额外增加的"疲劳值"（exhaustion），从而减少
//       （甚至消除）疾跑带来的额外饥饿消耗，适合长途探索与赶路。
//   - I 级：疾跑额外饥饿消耗减半。
//   - II 级：疾跑额外饥饿消耗完全抵消（疾跑的体力消耗≈走路水平）。
//
// 原理：原版游戏中，疾跑会在走路消耗的基础上额外累积 exhaustion（疲劳值），
//      每当 exhaustion 累积到 4.0 时扣减 1 点饥饿/饱和度。本附魔通过在
//       玩家 tick 中按等级抵消这份"额外疲劳值"，达到节能效果。
//       具体抵消量可在配置 (sprint_endurance) 中调整。
//
// 获取：附魔台可直接获得（非宝藏）+ 遗迹宝箱 + 村民交易。
// 冲突：无（护腿本身缺少专属附魔生态位，不与任何附魔冲突）。
// ========================================================================
public class SprintEnduranceEnchantment extends Enchantment {

    // 最高等级 II（2级）
    private static final int MAX_LEVEL = 2;

    public SprintEnduranceEnchantment() {
        // Rarity.UNCOMMON：附魔台可正常出现
        // EnchantmentCategory.ARMOR_LEGS：仅护腿类装备可附魔
        super(
                Rarity.UNCOMMON,
                EnchantmentCategory.ARMOR_LEGS,
                new EquipmentSlot[]{EquipmentSlot.LEGS}
        );
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    // 附魔台最低经验成本：II 级附魔，成本随等级递增
    @Override
    public int getMinCost(int level) {
        return 15 + level * 5;
    }

    @Override
    public int getMaxCost(int level) {
        return 20 + level * 7;
    }

    // 非宝藏附魔：附魔台 / 村民交易均可获得
    @Override
    public boolean isTreasureOnly() {
        return false;
    }

    // 可在遗迹宝箱中随机发现
    @Override
    public boolean isDiscoverable() {
        return true;
    }

    // 允许附魔书在铁砧上应用
    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // ========================================================================
    // 【冲突设置】无特殊互斥
    // 护腿（ARMOR_LEGS）在本模组中没有同类生态位的附魔，且与原版附魔
    // （护甲类如护甲值/保护为全身通用）互不冲突，因此保持父类默认逻辑。
    // ========================================================================

    // 仅护腿类护甲（实现 ArmorItem 且槽位为 LEGS）可附魔
    @Override
    public boolean canEnchant(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armorItem) {
            return armorItem.getEquipmentSlot() == EquipmentSlot.LEGS;
        }
        return false;
    }
}
