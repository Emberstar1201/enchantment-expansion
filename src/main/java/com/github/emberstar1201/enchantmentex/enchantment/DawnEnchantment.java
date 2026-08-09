package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 【拂晓】附魔 - 近战武器通用（剑、斧、三叉戟）
//
// 效果：随昼夜变化动态调整力量
//   白天   (6000~12000)              ：无加成
//   傍晚/凌晨 (12000~13000 / 23000~24000)：伤害 +50% ，冷却 -25%
//   夜晚   (13000~17000)              ：伤害 +100%（2倍），冷却 -50%
//   午夜   (17000~18000)              ：伤害 +300%（4倍），冷却 -100%（无冷却）
//
// 获取方式：仅以附魔书形式在遗迹宝箱中出现（treasureOnly + discoverable + 不可交易）
// 稀有度：非常稀有（Rarity.VERY_RARE）
// 最高等级：I（1级）
// ========================================================================
public class DawnEnchantment extends Enchantment {

    // PersistentData 中存储"当前时段"的键名
    // 时段编码：0=白天 1=傍晚/凌晨 2=夜晚 3=午夜
    // 在 DawnTimeTracker 中写入，在 DawnHandler 中读取
    public static final String PERIOD_KEY = "dawn_period";

    // PersistentData 中存储"上一 tick 是否已应用修饰符"的键名
    // 用于状态切换判断，避免每 tick 重复增删 AttributeModifier
    public static final String LAST_PERIOD_KEY = "dawn_last_period";

    public DawnEnchantment() {
        // Rarity.VERY_RARE：非常稀有（与原版 冰霜行者/经验修补 同级别）
        // EnchantmentCategory.WEAPON：剑/斧/三叉戟等近战武器
        // 主手武器生效
        super(
                Rarity.VERY_RARE,
                EnchantmentCategory.WEAPON,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND}
        );
    }

    // 获取附魔最低等级
    @Override
    public int getMinLevel() {
        return 1;
    }

    // 获取附魔最高等级（1级，不可升级）
    @Override
    public int getMaxLevel() {
        return 1;
    }

    // 附魔在附魔台出现的最低经验成本
    // 由于 isTreasureOnly=true，附魔台不会刷出此附魔，此值仅供铁砧参考
    @Override
    public int getMinCost(int level) {
        return 40;
    }

    @Override
    public int getMaxCost(int level) {
        return 60;
    }

    // 宝藏附魔：仅以附魔书形式在遗迹宝箱中出现，附魔台无法获得
    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    // 不可通过村民图书管理员交易获得（更符合"非常稀有"定位）
    @Override
    public boolean isTradeable() {
        return false;
    }

    // 可通过宝箱/钓鱼等随机发现
    @Override
    public boolean isDiscoverable() {
        return true;
    }

    // 允许附魔书在铁砧上应用到剑/斧/三叉戟
    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // ========================================================================
    // 检查此附魔是否可应用到给定物品栈
    // 用户要求：剑、斧、三叉戟等近战武器
    // ========================================================================
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem;
    }

    // 默认兼容性：与其他附魔按原版逻辑（不额外互斥）
    // 用户未要求特定冲突设置，保持父类默认行为
}
