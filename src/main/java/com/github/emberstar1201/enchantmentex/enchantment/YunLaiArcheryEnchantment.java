package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// "云来弓法"附魔 - 弓专属（基础版）
//
// 【平衡性调整 v1.1.2c】
//   - 最高等级：1级（原 10级 → 1级）
//   - 获取方式：附魔台可获得（保持不变，非宝藏附魔）
//   - 蓄力时间：0.45 秒（原版=1.0秒，倍率 ≈2.222）
//   - 无箭矢飞行速度加成（与古·云来弓法的区别）
//   - 数值支持配置文件调整
//
// 【与"古·云来弓法"的区别】
//   - 古·云来弓法：箭矢加速 + 蓄力加速（双效果，宝藏附魔，附魔台无法获得）
//   - 云来弓法（基础版）：仅蓄力加速（单效果，附魔台可获得）
//   - 两者可共存叠加（用户明确要求）
//
// ========================================================================
public class YunLaiArcheryEnchantment extends Enchantment {

    // 最高等级：1级
    private static final int MAX_LEVEL = 1;

    // 蓄力加速倍率表：索引 0 占位，索引 1 对应 1 级
    // 默认 0.45s → 1.0/0.45 ≈ 2.222
    // 数值从配置文件读取，此处仅做结构保留
    private static final double[] CHARGE_SPEED_TABLE = {
            1.000,  // [0] 占位（无附魔）
            2.222   // [1] 1级 → 约 0.45 秒（实际读取 Config.yunLaiArcheryChargeMultiplier）
    };

    public YunLaiArcheryEnchantment() {
        // Rarity.RARE：稀有度"稀有"（附魔台可获得）
        // EnchantmentCategory.BOW：仅弓可附魔
        // EquipmentSlot.MAINHAND：仅主手生效
        super(
                Rarity.RARE,
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

    // 附魔台最低成本：仅 1 级，固定 5（与 RARE 身份匹配，比古·云来低）
    @Override
    public int getMinCost(int level) {
        return 5;
    }

    // 附魔台最高成本
    @Override
    public int getMaxCost(int level) {
        return 25;
    }

    // 非宝藏附魔：附魔台可直接获得
    @Override
    public boolean isTreasureOnly() {
        return false;
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
    // 【对外 API】获取蓄力加速倍率
    //
    // level >= 1 → 从配置文件读取（默认 ≈2.222 = 0.45 秒蓄力）
    // level < 1  → 返回 1.0（无加速）
    // 超过 1 级的指令获取 → 按 1 级封顶
    //
    // 【与古·云来弓法独立】
    //   不再委托 AncientYunLaiEnchantment.getChargeSpeedMultiplier()，
    //   使用独立的蓄力倍率 Config.yunLaiArcheryChargeMultiplier。
    //   两者蓄力时间独立配置（古 0.5s vs 基础 0.45s）。
    // ========================================================================
    public static double getChargeSpeedMultiplier(int level) {
        if (level < 1) {
            return 1.0;
        }
        // 从配置文件读取（支持运行时动态调整）
        return Config.yunLaiArcheryChargeMultiplier;
    }

    // 检查此附魔是否可应用到给定物品栈（仅弓可以附魔）
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof BowItem;
    }
}