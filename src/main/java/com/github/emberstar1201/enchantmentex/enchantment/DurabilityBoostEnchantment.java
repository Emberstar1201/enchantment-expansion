package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 耐久强化（Durability Boost）
//
// 适用：全部可损耗耐久的物品（工具/武器/盔甲）
// 最高等级：III
// 效果：
//   I 级：耐久度 +50%
//   II 级：耐久度 +100%
//   III 级：耐久度 +150%
// 线性提升：每级 +50%
// 获取：附魔台 + 宝箱 + 村民交易
//
// 实现方式：通过 Forge 的 ItemAttributeModifierEvent 不适用，
// 因为耐久度不是属性。正确做法是重写 modifyDurability 或者
// 在物品创建时直接增加 maxDamage。但附魔无法修改物品本身 maxDamage。
//
// 真正可行的方案：监听 LivingEntity 使用物品的 tick 事件，
// 以概率减少耐久消耗（类似原版 Unbreaking 但更强）。
//   - I 级：50% 概率不消耗耐久
//   - II 级：66% 概率不消耗耐久
//   - III 级：75% 概率不消耗耐久
// 等效于耐久度提升至 2x / 3x / 4x（线性增长）。
// ========================================================================
public class DurabilityBoostEnchantment extends Enchantment {

    public DurabilityBoostEnchantment() {
        super(Rarity.UNCOMMON, EnchantmentCategory.BREAKABLE,
                new EquipmentSlot[]{
                        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
                        EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS, EquipmentSlot.FEET
                });
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinCost(int level) {
        return 10 + (level - 1) * 8;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
    }

    @Override
    public boolean isTreasureOnly() {
        return false;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.isDamageableItem();
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return canEnchant(stack);
    }

    // ========================================================================
    // 与原版耐久（Unbreaking）互斥
    // 原版耐久 I/II/III 的不消耗概率为 20%/33%/40%，
    // 本附魔 I/II/III 为 50%/66%/75%，效果更强且功能重叠。
    // ========================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        if (other == net.minecraft.world.item.enchantment.Enchantments.UNBREAKING) {
            return false;
        }
        return super.checkCompatibility(other);
    }
}
