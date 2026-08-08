package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// "云来弓法"附魔 - 弓专属（入门基础版）
//
// 与"古·云来弓法"的区别：
//   相同点：
//     - 10级上限
//     - 蓄力加速倍率表完全一致（通过 AncientYunLaiEnchantment.getChargeSpeedMultiplier() 共享）
//     - Rarity.RARE 稀有度、适用装备、附魔台成本都相同
//
//   不同点：
//     - 云来弓法（本类）：仅提升拉弓速度，不改箭矢飞行速度
//     - 古·云来弓法：蓄力加速 + 箭矢飞行加速 双加成
//
// 冲突规则（checkCompatibility）：
//   ✅ 与力量（POWER）附魔 → 兼容（用户明确要求）
//   ✅ 与"古·云来弓法"（ANCIENT_YUNLAI）→ 不冲突，两者可共存（用户明确要求可叠加）
//   ❌ 与原版无限/火矢/冲击：按原版 super.checkCompatibility() 默认逻辑（同 EnchantmentCategory.BOW，互斥）
//     注：若用户希望与无限也共存，可在此方法中补充排除，当前按 BOW 类默认行为处理
// ========================================================================
public class YunLaiArcheryEnchantment extends Enchantment {

    // 满级等级上限：10级（与古·云来弓法保持一致）
    private static final int MAX_LEVEL = 10;

    public YunLaiArcheryEnchantment() {
        // Rarity.RARE：稀有度设置为"稀有"
        // EnchantmentCategory.BOW：仅弓可附魔
        // EquipmentSlot.MAINHAND：仅主手生效
        super(
                Rarity.RARE,
                EnchantmentCategory.BOW,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND}
        );
    }

    // 获取附魔最低等级
    @Override
    public int getMinLevel() {
        return 1;
    }

    // 获取附魔最高等级（设置为10级）
    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    // 获取附魔在附魔台中出现的最低成本
    // 【与古·云来弓法完全相同的成本曲线】：线性递增 1,4,7,10,13,16,19,22,25,28
    @Override
    public int getMinCost(int level) {
        return 1 + (level - 1) * 3;
    }

    // 获取附魔在附魔台中出现的最高成本
    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 12;
    }

    // 非宝藏附魔：附魔台可直接获得
    @Override
    public boolean isTreasureOnly() {
        return false;
    }

    // 可通过村民图书管理员交易获得附魔书
    @Override
    public boolean isTradeable() {
        return true;
    }

    // 可通过宝箱/钓鱼/附魔台等随机发现
    @Override
    public boolean isDiscoverable() {
        return true;
    }

    // 允许附魔书在铁砧上应用到弓
    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // ========================================================================
    // 【对外 API】获取蓄力加速倍率
    // 【数值对齐】直接复用 AncientYunLaiEnchantment.CHARGE_SPEED_TABLE
    //   保证本附魔 1-10 级的蓄力时间与古·云来弓法完全一致：
    //     Lv1 ≈ 0.95s（倍率 1.053）
    //     Lv4 ≈ 0.80s（倍率 1.250）
    //     Lv10 ≈ 0.20s（倍率 5.000）
    // 无箭矢飞行速度（Handler 不注册 EntityJoinLevelEvent）
    // ========================================================================
    public static double getChargeSpeedMultiplier(int level) {
        // 委托给古·云来弓法的查表，保证两套蓄力速度数值完全一致
        return AncientYunLaiEnchantment.getChargeSpeedMultiplier(level);
    }

    // 检查此附魔是否可应用到给定物品栈（仅弓可以附魔）
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof BowItem;
    }
}
