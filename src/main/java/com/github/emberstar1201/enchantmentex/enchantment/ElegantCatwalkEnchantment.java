package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;

// ========================================================================
// 【优雅猫步】附魔 - 靴子专属
// 效果：
//   1. 移速提升：获得 1.45 倍永久移速加成（猫一样的奔跑速度）
//   2. 免疫掉落伤害：永远不会受到摔落伤害，如同猫的轻盈下落
// 冲突：仅深海探索者 (Depth Strider)
// 获取方式：附魔台可直接获得（非宝藏），也会出现在遗迹宝箱中
// ========================================================================
public class ElegantCatwalkEnchantment extends Enchantment {

    // 最高等级 I（1级，固定稀有度稀有）
    private static final int MAX_LEVEL = 1;

    public ElegantCatwalkEnchantment() {
        // Rarity.RARE：稀有度稀有
        // EnchantmentCategory.ARMOR_FEET：仅靴子类装备可附魔
        // EquipmentSlot.FEET：仅脚部装备栏生效
        super(
                Rarity.RARE,
                EnchantmentCategory.ARMOR_FEET,
                new EquipmentSlot[]{EquipmentSlot.FEET}
        );
    }

    // 获取附魔最低等级（从1级开始）
    @Override
    public int getMinLevel() {
        return 1;
    }

    // 获取附魔最高等级（设置为1级，不可继续升级）
    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    // 附魔在附魔台出现的最低经验成本
    // Lv1 约 10~25 经验区间，作为稀有附魔在附魔台有合理出现率
    @Override
    public int getMinCost(int level) {
        return 10 + (level - 1) * 8;
    }

    // 附魔在附魔台出现的最高经验成本
    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
    }

    // 非宝藏附魔：附魔台可直接获得（用户明确要求）
    @Override
    public boolean isTreasureOnly() {
        return false;
    }

    // 可通过村民图书管理员交易获得附魔书
    @Override
    public boolean isTradeable() {
        return true;
    }

    // 可通过宝箱/钓鱼/附魔台等随机发现（用户明确要求遗迹宝箱中出现）
    @Override
    public boolean isDiscoverable() {
        return true;
    }

    // 允许附魔书在铁砧上应用到靴子
    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // ========================================================================
    // 【冲突设置】仅与深海探索者互斥
    // 深海探索者（Depth Strider）：影响水中移速，与"猫步"类加速主题互斥
    // 其他靴子附魔（冰霜行者/灵魂疾行/迅捷潜行）：保持兼容，用户只指定了这一种互斥
    // 与【风踏涟漪】：同为靴子移速类附魔，但按父类默认逻辑 EnchantmentCategory.ARMOR_FEET
    //   同类附魔默认互斥，因此优雅猫步与风踏涟漪也不能共存（合理，两个都是靴子加速类）
    // ========================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        if (other == Enchantments.DEPTH_STRIDER) return false;
        // 其他附魔：沿用原版默认逻辑（同类附魔互斥，其余兼容）
        return super.checkCompatibility(other);
    }

    // 检查此附魔是否可应用到给定物品栈
    // 仅靴子类护甲（实现 ArmorItem 且槽位为 FEET）可附魔
    @Override
    public boolean canEnchant(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armorItem) {
            return armorItem.getEquipmentSlot() == EquipmentSlot.FEET;
        }
        return false;
    }
}
