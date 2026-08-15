package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// "古·云来剑法·重制"附魔 - 剑专属（单级·宝箱限定）
//
// 获取方式：仅以附魔书形式在遗迹宝箱中出现（比云来剑法更稀有）
//
// 【效果】
//   1. 攻击命中时，100%概率挥出3道月牙形剑气（扇形分布）
//      每道剑气伤害 = 本次攻击 × 0.75
//   2. 攻击速度 +5.0（手持时自动生效，切换武器自动移除）
//   3. 粒子效果：金色/青色月牙
//
// 与"云来剑法·重制"互斥（同一把剑只能二选一）
//
// 兼容性：
//   ✅ 与所有原版剑类附魔兼容（锋利、亡灵杀手、节肢杀手、击退、火焰附加、抢夺、横扫之刃）
//   ❌ 与"云来剑法·重制"互斥
// ========================================================================
public class AncientYunLaiSwordmanshipEnchantment extends Enchantment {

    // 最高等级：1级
    private static final int MAX_LEVEL = 1;

    public AncientYunLaiSwordmanshipEnchantment() {
        // Rarity.VERY_RARE：非常稀有，比云来剑法更稀有
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

    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    // 宝藏附魔成本（比云来剑法更高）
    @Override
    public int getMinCost(int level) {
        return 30;
    }

    @Override
    public int getMaxCost(int level) {
        return 60;
    }

    // 宝藏附魔：附魔台无法获得，仅宝箱/钓鱼
    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    @Override
    public boolean isTradeable() {
        return false;
    }

    @Override
    public boolean isDiscoverable() {
        return true;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // ========================================================================
    // 冲突规则
    // ✅ 与所有原版剑类附魔兼容
    // ❌ 与"云来剑法·重制"互斥
    // ========================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        if (other == this) {
            return false;
        }
        if (other == ModEnchantments.YUNLAI_SWORDMANSHIP.get()) {
            return false;
        }
        return true;
    }

    // 仅剑类物品可附魔
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof SwordItem;
    }
}