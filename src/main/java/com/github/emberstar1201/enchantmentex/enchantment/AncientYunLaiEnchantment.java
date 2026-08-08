package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// "古·云来弓法"附魔 - 弓专属
// 传承自古老云来宗门的弓术，同时提升拉弓速度和箭矢飞行速度
// 采用查表法硬编码，两套独立倍率：箭矢飞行速度 + 蓄力加速倍率
// 【数值说明】所有数值完全照搬原"速射"附魔，仅重命名不做数值调整
public class AncientYunLaiEnchantment extends Enchantment {

    // 满级等级上限（配置为10级，与用户需求一致）
    private static final int MAX_LEVEL = 10;

    // ========================================================================
    // 【箭矢飞行速度倍率表】（完全照搬原速射附魔的精确平衡数值）
    // 索引 0 占位未使用，索引 1~10 对应附魔等级 1~10
    // 超过10级（指令获得）全部按10级值返回，不再增强
    // ========================================================================
    private static final double[] FLIGHT_SPEED_TABLE = {
            1.000,  // [0] 占位（无附魔）
            1.050,  // Lv1
            1.100,  // Lv2
            1.150,  // Lv3
            1.250,  // Lv4
            1.475,  // Lv5
            1.500,  // Lv6
            1.660,  // Lv7
            1.760,  // Lv8
            1.900,  // Lv9
            2.100   // Lv10（满级上限，超等级不再提升）
    };

    // ========================================================================
    // 【蓄力加速倍率表】（完全照搬原速射附魔，精确对应蓄力时间）
    // 倍率 = 原版蓄力时间(1.0s) / 实际蓄力时间
    // 倍率越高拉弓越快：
    //   1.053 → 0.95s / 1.111 → 0.90s / 1.176 → 0.85s / 1.250 → 0.80s
    //   1.333 → 0.75s / 1.429 → 0.70s / 1.538 → 0.65s / 2.000 → 0.50s
    //   2.222 → 0.45s / 5.000 → 0.20s（满级极速拉弓）
    //
    // 【与"云来弓法（基础版）"共享】基础版 YunLaiArcheryEnchantment 会通过
    //   AncientYunLaiEnchantment.getChargeSpeedMultiplier() 复用此表，保证
    //   两个附魔1-10级的蓄力速度数值完全一致
    // ========================================================================
    private static final double[] CHARGE_SPEED_TABLE = {
            1.000,  // [0] 占位（无附魔）
            1.053,  // Lv1  → 约0.95秒
            1.111,  // Lv2  → 约0.90秒
            1.176,  // Lv3  → 约0.85秒
            1.250,  // Lv4  → 约0.80秒
            1.333,  // Lv5  → 约0.75秒
            1.429,  // Lv6  → 约0.70秒
            1.538,  // Lv7  → 约0.65秒
            2.000,  // Lv8  → 约0.50秒
            2.222,  // Lv9  → 约0.45秒
            5.000   // Lv10 → 约0.20秒（满级极速）
    };

    public AncientYunLaiEnchantment() {
        // Rarity.RARE：稀有度设置为"稀有"（附魔台可获得）
        // EnchantmentCategory.BOW：仅弓可附魔
        // EquipmentSlot.MAINHAND：仅主手生效（弓必须在主手使用）
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
    // 线性递增：Lv1=1, Lv2=4, Lv5=13, Lv8=22, Lv10=28
    // 保证所有10个等级的最小成本都不超过30（附魔台最大经验等级）
    @Override
    public int getMinCost(int level) {
        return 1 + (level - 1) * 3;
    }

    // 获取附魔在附魔台中出现的最高成本
    // 最高成本 = 最低成本 + 12，保证区间宽度统一为12
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
    // 【对外API 1/2】获取箭矢飞行速度倍率
    // 查表法：精确匹配平衡数值
    // level < 1 → 返回 1.0（无加成）
    // level >= 10 → 返回 FLIGHT_SPEED_TABLE[10] = 2.10（封顶）
    // ========================================================================
    public static double getFlightSpeedMultiplier(int level) {
        if (level < 1) {
            return 1.0;
        }
        int effectiveLevel = Math.min(level, MAX_LEVEL);
        return FLIGHT_SPEED_TABLE[effectiveLevel];
    }

    // ========================================================================
    // 【对外API 2/2】获取蓄力加速倍率（用于拉弓速度）
    // 查表法：倍率=原版时间/目标时间，倍率越高拉弓越快
    // 超10级与箭矢飞行同样按10级封顶
    //
    // 【共享给云来弓法基础版】 YunLaiArcheryHandler 也会调用此方法
    //   获取蓄力加速倍率，保证两个附魔蓄力数值完全一致
    // ========================================================================
    public static double getChargeSpeedMultiplier(int level) {
        if (level < 1) {
            return 1.0;
        }
        int effectiveLevel = Math.min(level, MAX_LEVEL);
        return CHARGE_SPEED_TABLE[effectiveLevel];
    }

    // 兼容旧API：保留原方法名，默认返回箭矢飞行倍率
    @Deprecated
    public static double getSpeedMultiplier(int level) {
        return getFlightSpeedMultiplier(level);
    }

    // 检查此附魔是否可应用到给定物品栈（仅弓可以附魔）
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof BowItem;
    }
}
