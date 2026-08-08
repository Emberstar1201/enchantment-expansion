package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// "云来剑法"附魔 - 剑专属（基础版）
//
// 效果：
//   1. 攻击距离提升：每级 +0.5 格，5级共 +2.5 格
//      公式：距离加成 = 等级 × 0.5
//   2. 攻击冷却缩减：递减收益
//      公式：缩减比例 = 50% × (1 - 0.8^等级)
//        Lv1 ≈ 10%   Lv2 ≈ 18%   Lv3 ≈ 24.4%   Lv4 ≈ 29.5%   Lv5 ≈ 33.6%
//      （超过5级不再提升，按5级封顶）
//
// 与"古·云来剑法"的关系：
//   - 古·云来剑法"完整继承"本附魔的所有效果（距离+冷却），并额外拥有 AOE 之力
//   - 两者 checkCompatibility 互斥（同一把剑只能二选一）
//
// 兼容性：
//   ✅ 与所有原版剑类附魔兼容（锋利、亡灵杀手、节肢杀手、击退、火焰附加、抢夺、横扫之刃）
//   ❌ 与"古·云来剑法"互斥
// ========================================================================
public class YunLaiSwordmanshipEnchantment extends Enchantment {

    // 满级等级上限：5级
    private static final int MAX_LEVEL = 5;

    // 每级攻击距离加成（格）
    private static final double REACH_PER_LEVEL = 0.5;

    // 冷却缩减封顶比例（满级50%上限系数）
    private static final double COOLDOWN_CAP = 0.5;

    // 递减基数（0.8^level 越大则缩减越小，越接近满级越平缓）
    private static final double DECAY_BASE = 0.8;

    public YunLaiSwordmanshipEnchantment() {
        // Rarity.RARE：稀有度"稀有"
        // EnchantmentCategory.WEAPON：仅剑类武器可附魔
        // EquipmentSlot.MAINHAND：仅主手生效（剑必须在主手攻击）
        super(
                Rarity.RARE,
                EnchantmentCategory.WEAPON,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND}
        );
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    // 最高等级：5级
    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    // 附魔台最低成本：线性递增 1,5,9,13,17（保证5级仍能在附魔台出现）
    @Override
    public int getMinCost(int level) {
        return 1 + (level - 1) * 4;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 10;
    }

    // 非宝藏附魔：附魔台可直接获得
    @Override
    public boolean isTreasureOnly() {
        return false;
    }

    @Override
    public boolean isTradeable() {
        return true;
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
    // 【对外 API 1/2】获取攻击距离加成（格数）
    // 公式：距离加成 = 等级 × 0.5
    //   Lv1 = 0.5格  Lv3 = 1.5格  Lv5 = 2.5格
    // 超过5级按5级封顶（不再增加）
    // ========================================================================
    public static double getAttackReachBonus(int level) {
        if (level < 1) {
            return 0.0;
        }
        int effectiveLevel = Math.min(level, MAX_LEVEL);
        return effectiveLevel * REACH_PER_LEVEL;
    }

    // ========================================================================
    // 【对外 API 2/2】获取攻击冷却缩减比例（0.0 ~ 0.5）
    // 公式：缩减 = 50% × (1 - 0.8^等级)
    //   Lv1 ≈ 0.10  Lv3 ≈ 0.244  Lv5 ≈ 0.336
    // 超过5级按5级封顶
    //
    // 【实现说明】此比例为"冷却中攻击的伤害惩罚减免比例"，
    //   Handler 在 LivingHurtEvent 中据此补正伤害，等效于攻击冷却缩减。
    // ========================================================================
    public static double getCooldownReduction(int level) {
        if (level < 1) {
            return 0.0;
        }
        int effectiveLevel = Math.min(level, MAX_LEVEL);
        return COOLDOWN_CAP * (1.0 - Math.pow(DECAY_BASE, effectiveLevel));
    }

    // ========================================================================
    // 冲突规则
    // ✅ 与所有原版剑类附魔兼容（锋利/亡灵/节肢/击退/火焰附加/抢夺/横扫之刃）
    // ❌ 与"古·云来剑法"互斥（同一把剑只能二选一）
    // 自身与自身不兼容
    //
    // 【实现】：由于 super.checkCompatibility 默认对"同 EnchantmentCategory"返回 false
    //   （WEAPON 类的锋利等会被默认判为互斥），但用户要求与原版剑类附魔兼容，
    //   因此除了"古·云来剑法"和"自身"外，其余一律返回 true（兼容）。
    // ========================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        // 自身不兼容（原版防御）
        if (other == this) {
            return false;
        }
        // 与"古·云来剑法"互斥
        if (other == ModEnchantments.ANCIENT_YUNLAI_SWORDMANSHIP.get()) {
            return false;
        }
        // 其余全部兼容（含原版所有剑类附魔）
        return true;
    }

    // 仅剑类物品可附魔（SwordItem）。WEAPON category 本身已限制剑，这里再做防御性校验
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof SwordItem;
    }
}
