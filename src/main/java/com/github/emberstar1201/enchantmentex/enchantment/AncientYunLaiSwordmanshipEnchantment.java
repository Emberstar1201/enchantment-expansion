package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// "古·云来剑法"附魔 - 剑专属（传承版，仅1级）
//
// 与"云来剑法"的关系：
//   完整继承云来剑法的所有效果（攻击距离提升 + 攻击冷却缩减），
//   并额外拥有 AOE 之力（攻击有概率对周围3格内敌人造成等额伤害）。
//
// 数值（继承自云来剑法 Lv1）：
//   - 攻击距离：+0.5 格（getAttackReachBonus(1)）
//   - 冷却缩减：约 10%（getCooldownReduction(1)）
//
// 获取方式：
//   仅以附魔书形式在遗迹宝箱（地牢、废弃矿井、堡垒、末地城）中出现
//   - isTreasureOnly() = true  → 附魔台无法获得
//   - isDiscoverable() = true  → 宝箱/钓鱼可发现
//   - isTradeable() = false    → 村民不卖
//   - isAllowedOnBooks() = true → 附魔书可在铁砧应用
//
// 兼容性：
//   ✅ 与所有原版剑类附魔兼容
//   ❌ 与"云来剑法"互斥（同一把剑只能二选一）
// ========================================================================
public class AncientYunLaiSwordmanshipEnchantment extends Enchantment {

    // 满级等级上限：1级
    private static final int MAX_LEVEL = 1;

    public AncientYunLaiSwordmanshipEnchantment() {
        // Rarity.VERY_RARE：稀有度"非常稀有"（与原版经验修补同级，匹配宝箱获取定位）
        // EnchantmentCategory.WEAPON：仅剑类武器可附魔
        // EquipmentSlot.MAINHAND：仅主手生效
        super(
                Rarity.VERY_RARE,
                EnchantmentCategory.WEAPON,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND}
        );
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    // 最高等级：1级
    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    // 宝藏附魔成本较高（参考经验修补：minCost 25）
    @Override
    public int getMinCost(int level) {
        return 25;
    }

    @Override
    public int getMaxCost(int level) {
        return 50;
    }

    // 【关键】宝藏附魔：附魔台无法获得，仅宝箱/钓鱼/铁砧附魔书
    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    // 不可通过村民图书管理员交易获得（保持稀有性）
    @Override
    public boolean isTradeable() {
        return false;
    }

    // 可通过宝箱/钓鱼等随机发现（这是宝箱附魔的核心开关）
    @Override
    public boolean isDiscoverable() {
        return true;
    }

    // 允许附魔书在铁砧上应用到剑
    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // ========================================================================
    // 【继承效果】获取攻击距离加成
    // 委托给 YunLaiSwordmanshipEnchantment，保证数值与"云来剑法 Lv1"完全一致
    // ========================================================================
    public static double getAttackReachBonus(int level) {
        // 古·云来剑法只有1级，数值完全继承云来剑法
        return YunLaiSwordmanshipEnchantment.getAttackReachBonus(level);
    }

    // ========================================================================
    // 【继承效果】获取攻击冷却缩减比例
    // 委托给 YunLaiSwordmanshipEnchantment
    // ========================================================================
    public static double getCooldownReduction(int level) {
        return YunLaiSwordmanshipEnchantment.getCooldownReduction(level);
    }

    // ========================================================================
    // 冲突规则
    // ✅ 与所有原版剑类附魔兼容
    // ❌ 与"云来剑法"互斥
    // ========================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        // 自身不兼容
        if (other == this) {
            return false;
        }
        // 与"云来剑法"互斥
        if (other == ModEnchantments.YUNLAI_SWORDMANSHIP.get()) {
            return false;
        }
        // 其余全部兼容（含原版所有剑类附魔）
        return true;
    }

    // 仅剑类物品可附魔
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof SwordItem;
    }
}
