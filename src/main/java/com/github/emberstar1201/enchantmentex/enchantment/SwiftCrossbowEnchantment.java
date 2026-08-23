package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.ForgeRegistries;

// ========================================================================
// 【迅捷之弩】附魔 - 弩专用（SWIFT_CROSSBOW）
//
// 【效果】（数值见 SwiftCrossbowConfig）
//   一、弩箭附加魔法伤害（无视护甲，受保护附魔/抗性提升影响）：
//        I : +2    II: +4    III: +6    IV: +8
//   二、装填时间减少（CrossbowItem.getChargeDuration 由 Mixin 修改）：
//        I : 减 0.25s（1.0s）   II: 减 0.5s（0.75s）
//        III: 减 1.0s（0.25s）  IV: 减 1.25s（几乎立即装填）
//
// 【获取】仅宝箱可获得（isTreasureOnly=true，附魔台无法获得）
// 【稀有度】非常稀有（Rarity.VERY_RARE）
// 【最高等级】IV
// ========================================================================
public class SwiftCrossbowEnchantment extends Enchantment {

    // 最高等级 IV
    private static final int MAX_LEVEL = 4;

    public SwiftCrossbowEnchantment() {
        // Rarity.VERY_RARE：非常稀有（与 冰霜行者/经验修补 同级别）
        // EnchantmentCategory.CROSSBOW：仅弩专用
        super(
                Rarity.VERY_RARE,
                EnchantmentCategory.CROSSBOW,
                new net.minecraft.world.entity.EquipmentSlot[0]
        );
    }

    // 获取附魔最低等级（从1级开始）
    @Override
    public int getMinLevel() {
        return 1;
    }

    // 获取附魔最高等级（设置为4级）
    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    // 附魔台的最低经验成本（仅铁砧应用参考；宝藏附魔不会在附魔台刷出）
    @Override
    public int getMinCost(int level) {
        return 20 + (level - 1) * 12;   // Lv1=20, Lv2=32, Lv3=44, Lv4=56
    }

    // 附魔台的最高经验成本
    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 20;
    }

    // 宝藏附魔：仅宝箱获取，附魔台无法获得
    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    // 不可通过村民图书管理员交易获得（与原版宝藏附魔一致）
    @Override
    public boolean isTradeable() {
        return false;
    }

    // 可通过宝箱/钓鱼等随机发现（宝藏附魔法则）
    @Override
    public boolean isDiscoverable() {
        return true;
    }

    // 允许附魔书在铁砧上应用到弩
    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // 检查此附魔是否可应用到给定物品栈（仅弩）
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof CrossbowItem;
    }

    // ========================================================================
    // 【冲突设置】与原版「快速装填」（Quick Charge）互斥
    //
    // 原因：迅捷之弩本身就通过 Mixin 缩短装填时间，与原版快速装填
    //       的效果完全重叠；叠加后 IV 级 + 快速装填 III 会导致装填
    //       时间被压到下限以下（即使有 minChargeTicks 保底，两者叠加
    //       也会让数值意义归零）。
    // 实现：用注册表 ID 比较（minecraft:quick_charge），避免 mapping
    //       字段名（QUICK_CHARGE）在开发/生产环境下的差异。
    // ========================================================================
    private static final ResourceLocation QUICK_CHARGE_RL =
            ResourceLocation.parse("minecraft:quick_charge");

    @Override
    protected boolean checkCompatibility(Enchantment other) {
        ResourceLocation otherRL = ForgeRegistries.ENCHANTMENTS.getKey(other);
        if (QUICK_CHARGE_RL.equals(otherRL)) {
            return false;  // 与快速装填互斥
        }
        return super.checkCompatibility(other);
    }
}