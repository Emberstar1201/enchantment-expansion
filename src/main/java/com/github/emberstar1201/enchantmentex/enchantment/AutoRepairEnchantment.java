package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 自动修复（Auto Repair）附魔定义
//
// 【适用】所有可损耗耐久的物品：武器、工具、盔甲
// 【最高等级】III
// 【效果】每隔固定 tick 自动恢复 1 点耐久：
//   I 级：100 ticks（5秒）
//   II 级：80  ticks（4秒）
//   III 级：60  ticks（3秒）
// 【获取】仅遗迹宝箱（宝藏附魔）
// ========================================================================
public class AutoRepairEnchantment extends Enchantment {

    public AutoRepairEnchantment() {
        super(
                Rarity.VERY_RARE,
                // BREAKABLE 涵盖所有可损耗耐久的物品（武器/工具/盔甲）
                EnchantmentCategory.BREAKABLE,
                new EquipmentSlot[]{
                        EquipmentSlot.MAINHAND,
                        EquipmentSlot.OFFHAND,
                        EquipmentSlot.HEAD,
                        EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS,
                        EquipmentSlot.FEET
                }
        );
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinCost(int level) {
        // 宝藏附魔仍需要定义经验成本（用于铁砧和战利品附魔等级计算）
        return 30 + (level - 1) * 12;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 25;
    }

    @Override
    public boolean isTreasureOnly() {
        // 宝藏附魔：仅通过战利品/宝箱/钓鱼/猪灵获得
        return true;
    }

    @Override
    public boolean isTradeable() {
        // 需求：不与村民交易
        return false;
    }

    @Override
    public boolean isDiscoverable() {
        // 附魔台不可发现
        return false;
    }

    @Override
    public boolean isAllowedOnBooks() {
        // 允许存在于附魔书上，方便战利品生成
        return true;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        // 仅对有耐久上限的物品可附魔
        return stack.isDamageableItem();
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        // 附魔台不可直接附魔；仅通过宝箱获得
        return false;
    }
}
