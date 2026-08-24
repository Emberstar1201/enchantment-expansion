package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 【爆破箭矢】(Explosive Arrow) 附魔 - 弓 / 弩专属
//
// 效果：
//   箭矢命中后产生小型爆炸（不破坏地形，仅对生物造成范围伤害）。
//   等级越高爆炸半径与伤害越大：
//     I  ：半径 2 格，中心伤害 6
//     II ：半径 3 格，中心伤害 9
//     III：半径 4 格，中心伤害 12
//   被直接命中的目标承受全额中心伤害，周围生物按距离衰减。
//
// 实现：实体命中处理见 ExplosiveArrowHandler（箭 NBT 打标记 +
//      ProjectileImpactEvent 命中时手动施加爆炸伤害），纯事件方案，无需 Mixin。
//
// 获取：附魔台 + 宝箱 / 村民（可发现）
// 互斥：与「贯穿链条」(chain_arrow) 互斥（同一支箭只能有一种弹药效果）
// ========================================================================
public class ExplosiveArrowEnchantment extends Enchantment {

    // 最高等级 III
    private static final int MAX_LEVEL = 3;

    public ExplosiveArrowEnchantment() {
        // Rarity.VERY_RARE：稀有
        // EnchantmentCategory.BOW：弓类基础分类（可额外支持弩）
        super(
                Rarity.VERY_RARE,
                EnchantmentCategory.BOW,
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

    // 附魔台经验成本（稀有附魔）
    @Override
    public int getMinCost(int level) {
        return 15 + (level - 1) * 8;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
    }

    // 非宝藏附魔：附魔台直接可获得
    @Override
    public boolean isTreasureOnly() {
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
    // 适用物品：弓 + 弩（两个都支持）
    // ========================================================================
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
    }

    // 附魔台上弩也能出现该附魔
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return canEnchant(stack);
    }

    // ========================================================================
    // 【互斥】与「贯穿链条」互斥：同一支箭不能同时装爆破与贯穿
    // ========================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        if (other == ModEnchantments.CHAIN_ARROW.get()) {
            return false;
        }
        // 其余附魔（迅捷之弩、力量、火矢等）保持默认兼容
        return super.checkCompatibility(other);
    }
}