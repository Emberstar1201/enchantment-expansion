package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// "古·云来弓法"附魔 - 弓专属（古代传承版）
//
// 【平衡性调整 v1.1.2c】
//   - 最高等级：1级（原 10级 → 1级）
//   - 获取方式：仅限宝箱和村民交易（宝藏附魔，附魔台无法获得）
//   - 箭矢飞行速度：3.5 倍（原版满弦=1.0）
//   - 蓄力时间：0.5 秒（原版=1.0秒，倍率 2.0）
//   - 所有数值支持配置文件调整
//
// 【与"云来弓法（基础版）"的区别】
//   - 古·云来弓法：箭矢加速 + 蓄力加速（双效果，宝藏附魔）
//   - 云来弓法（基础版）：仅蓄力加速（单效果，附魔台可获得）
//   - 两者蓄力时间不同：古 0.5s vs 基础 0.45s（独立配置）
//
// 【冲突规则】
//   ✅ 与力量（POWER）→ 兼容
//   ✅ 与云来弓法基础版 → 兼容（可共存叠加）
//   ❌ 与原版无限/火矢/冲击：同 BOW 类别默认互斥
// ========================================================================
public class AncientYunLaiEnchantment extends Enchantment {

    // 最高等级：1级（不可通过指令获得更高等级）
    private static final int MAX_LEVEL = 1;

    // 箭矢飞行速度倍率表：索引 0 占位，索引 1 对应 1 级
    // 数值从配置文件读取，此处仅做结构保留
    private static final double[] FLIGHT_SPEED_TABLE = {
            1.000,  // [0] 占位（无附魔）
            3.500   // [1] 1级（实际读取 Config.ancientYunLaiArrowSpeed）
    };

    // 蓄力加速倍率表：索引 0 占位，索引 1 对应 1 级
    // 倍率 = 原版蓄力时间(1.0s) / 目标蓄力时间
    // 默认 0.5s → 1.0/0.5 = 2.0
    // 数值从配置文件读取，此处仅做结构保留
    private static final double[] CHARGE_SPEED_TABLE = {
            1.000,  // [0] 占位（无附魔）
            2.000   // [1] 1级 → 约 0.5 秒（实际读取 Config.ancientYunLaiChargeMultiplier）
    };

    public AncientYunLaiEnchantment() {
        // Rarity.VERY_RARE：更高稀有度（宝藏附魔，获得难度更大）
        // EnchantmentCategory.BOW：仅弓可附魔
        // EquipmentSlot.MAINHAND：仅主手生效
        super(
                Rarity.VERY_RARE,
                EnchantmentCategory.BOW,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND}
        );
    }

    // 最低等级
    @Override
    public int getMinLevel() {
        return 1;
    }

    // 最高等级（改为 1 级）
    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    // 附魔台最低成本：仅 1 级，固定 15（与宝藏附魔身份匹配）
    @Override
    public int getMinCost(int level) {
        return 15;
    }

    // 附魔台最高成本
    @Override
    public int getMaxCost(int level) {
        return 35;
    }

    // 【关键】宝藏附魔：附魔台无法获得
    // 仅限宝箱、村民交易、钓鱼等方式获取
    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    // 村民图书管理员可以出售此附魔书
    @Override
    public boolean isTradeable() {
        return true;
    }

    // 可通过宝箱/钓鱼等随机发现
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
    // 【对外 API 1/2】获取箭矢飞行速度倍率
    //
    // level >= 1 → 从配置文件读取（默认 3.5）
    // level < 1  → 返回 1.0（无加成）
    // 超过 1 级的指令获取 → 按 1 级封顶
    // ========================================================================
    public static double getFlightSpeedMultiplier(int level) {
        if (level < 1) {
            return 1.0;
        }
        // 从配置文件读取（支持运行时动态调整）
        return Config.ancientYunLaiArrowSpeed;
    }

    // ========================================================================
    // 【对外 API 2/2】获取蓄力加速倍率
    //
    // level >= 1 → 从配置文件读取（默认 2.0 = 0.5 秒蓄力）
    // level < 1  → 返回 1.0（无加速）
    // 超过 1 级的指令获取 → 按 1 级封顶
    //
    // 【注意】云来弓法基础版现在使用独立的蓄力倍率（Config.yunLaiArcheryChargeMultiplier），
    //   不再调用此方法，两者蓄力时间独立配置。
    // ========================================================================
    public static double getChargeSpeedMultiplier(int level) {
        if (level < 1) {
            return 1.0;
        }
        // 从配置文件读取（支持运行时动态调整）
        return Config.ancientYunLaiChargeMultiplier;
    }

    // 兼容旧 API：保留原方法名，默认返回箭矢飞行倍率
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