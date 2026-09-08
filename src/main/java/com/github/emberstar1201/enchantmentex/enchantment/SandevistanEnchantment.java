package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 【斯安维斯坦】（Sandevistan）胸甲附魔
//
// 灵感来源：赛博朋克系列的 Sandevistan 植入体。
//
// 【适用物品】胸甲（ARMOR_CHEST 分类；鞘翅不可附上）
// 【最高等级】III（3级）
//
// 【效果】
//   按下激活键（默认 X，可在按键设置中自定义）后短时间内：
//     - 范围内世界时间减缓：怪物以 1/N 速度行动、弹射物慢动作飞行；
//     - 范围内其他玩家被减速（可在配置中关闭）；
//     - 激活者自身获得移速与攻速加成，配合 FOV 拉伸与青蓝色特效，
//       营造"子弹时间"手感。
//   持续结束后进入冷却，激活一次性消耗少量饥饿值。
//
// 【等级数值（默认值，均可配置）】
//   Lv I  民用型：持续 3 秒 / 冷却 60 秒 / 世界 0.50 倍速 / 自身 +30% 移速 +20% 攻速
//   Lv II 军用型：持续 4 秒 / 冷却 45 秒 / 世界 0.34 倍速 / 自身 +40% 移速 +35% 攻速
//   Lv III 传说型：持续 5 秒 / 冷却 30 秒 / 世界 0.25 倍速 / 自身 +50% 移速 +50% 攻速
//
// 【获取方式】
//   Lv I    ：附魔台 / 村民交易 / 宝箱
//   Lv II+  ：仅遗迹宝箱（通过 minCost > 30 机制，附魔台抽不到 II/III 级）
// ========================================================================
public class SandevistanEnchantment extends Enchantment {

    private static final int MAX_LEVEL = 3;

    // ========================================================================
    // 附魔等级 → 经验成本（控制哪些等级能在附魔台出现）
    // 原版附魔台最高等级为 30，minCost 超过 30 的等级不会被附魔台抽到。
    //   Lv I  : 18~33  → 附魔台可见
    //   Lv II : 50~65  → 高于 30，附魔台不可见，仅宝箱
    //   Lv III: 80~95  → 高于 30，附魔台不可见，仅宝箱
    // ========================================================================
    private static final int[] MIN_COSTS = { 18, 50, 80 };

    public SandevistanEnchantment() {
        // Rarity.VERY_RARE：非常稀有
        // EnchantmentCategory.ARMOR_CHEST：胸甲/鞘翅分类（鞘翅在 canEnchant 中排除）
        // EquipmentSlot.CHEST：胸甲位生效
        super(Rarity.VERY_RARE, EnchantmentCategory.ARMOR_CHEST,
                new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    @Override
    public int getMinCost(int level) {
        int idx = Math.max(0, Math.min(level - 1, MAX_LEVEL - 1));
        return MIN_COSTS[idx];
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
    }

    // 非宝藏附魔：I 级需要在附魔台出现；
    // II/III 级通过 minCost > 30 机制保证附魔台不可获得，仅由宝箱掉落。
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
    // 仅胸甲可附上：排除鞘翅（斯安维斯坦是"植入体"，设定上随胸甲装备）
    // ========================================================================
    @Override
    public boolean canEnchant(ItemStack stack) {
        if (stack.getItem() instanceof ElytraItem) {
            return false;
        }
        return super.canEnchant(stack);
    }
}
