package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

// ========================================================================
// 【强夺】附魔 - 剑类武器专属
// 效果：
//   1. 稀有战利品（头颅、唱片）100% 掉落，不再受原版概率影响
//   2. 按附魔等级额外增加稀有战利品数量：
//        Lv I = +1, Lv II = +2, Lv III = +3
//   3. 击杀爬行者时，必定额外掉落一张音乐唱片（无需骷髅射杀）
//   4. 非稀有战利品不受影响（仍按原版概率和数量掉落）
//   5. BOSS 固定掉落物（下界之星、龙蛋）不触发额外加成
// 获取方式：仅以附魔书形式在遗迹宝箱中出现（附魔台无法获得）
// 稀有度：非常稀有（Rarity.VERY_RARE）
// 冲突：抢夺（Looting）——同属"击杀掉落强化"类附魔，互斥
// ========================================================================
public class PlunderEnchantment extends Enchantment {

    // 最高等级 III（3级）
    private static final int MAX_LEVEL = 3;

    // 抢夺附魔的稳定注册表 ID：minecraft:looting（避免 mapping 字段名差异）
    private static final ResourceLocation LOOTING_RL = new ResourceLocation("minecraft", "looting");

    public PlunderEnchantment() {
        // Rarity.VERY_RARE：非常稀有（与原版 冰霜行者/经验修补 同级别）
        // EnchantmentCategory.WEAPON：剑/斧等武器类（实际判定用 canEnchant 限制为剑）
        // 主手武器生效（与 SnatchEnchantment 一致，基于击杀时手持判定）
        super(
                Rarity.VERY_RARE,
                EnchantmentCategory.WEAPON,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND}
        );
    }

    // 获取附魔最低等级（从1级开始）
    @Override
    public int getMinLevel() {
        return 1;
    }

    // 获取附魔最高等级（设置为3级，不可继续升级）
    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    // 附魔在附魔台出现的最低经验成本
    // 由于 isTreasureOnly=true，附魔台不会刷出此附魔，minCost 仍按常规公式定义
    //（仅供铁砧应用时的经验消耗参考）
    @Override
    public int getMinCost(int level) {
        return 25 + (level - 1) * 15;  // Lv1=25, Lv2=40, Lv3=55
    }

    // 附魔在附魔台出现的最高经验成本
    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 20;
    }

    // 宝藏附魔：用户明确要求"仅以附魔书形式在遗迹宝箱中出现，附魔台无法获得"
    //   → isTreasureOnly=true：附魔台永远刷不出（宝箱仍可发现）
    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    // 不可通过村民图书管理员交易获得（与原版宝藏附魔一致：冰霜行者可交易，经验修补不可）
    //   → 设为 false 让玩家只能通过宝箱探索获得附魔书（更符合"非常稀有"定位）
    @Override
    public boolean isTradeable() {
        return false;
    }

    // 可通过宝箱/钓鱼等随机发现（用户明确要求遗迹宝箱中出现）
    @Override
    public boolean isDiscoverable() {
        return true;
    }

    // 允许附魔书在铁砧上应用到剑
    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // ========================================================================
    // 【冲突设置】与"抢夺"（Looting）互斥
    // 同 SnatchEnchantment：用注册表 ID 比较，避免 mapping 字段名差异
    // ========================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        ResourceLocation otherRL = ForgeRegistries.ENCHANTMENTS.getKey(other);
        if (LOOTING_RL.equals(otherRL)) {
            return false;  // 与抢夺附魔互斥
        }
        // 与"攫取"（Snatch）也建议互斥：两者同属"击杀掉落强化"机制
        // 但用户未明确要求，默认按父类判断（WEAPON 类不互斥，可叠加）
        // 如需互斥，可参考下方注释开启：
        // ResourceLocation snatchRL = new ResourceLocation("enchantment_expansion", "snatch");
        // if (snatchRL.equals(otherRL)) return false;
        return super.checkCompatibility(other);
    }

    // 检查此附魔是否可应用到给定物品栈
    // 严格限制为 SwordItem（仅剑），与 SnatchEnchantment 保持一致
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof SwordItem;
    }

    // ========================================================================
    // 工具方法：给定附魔等级，返回"稀有战利品额外掉落数量"
    //   Lv I = +1, Lv II = +2, Lv III = +3
    // 等级超出范围则钳制：<1 按 1 级，>3 按 3 级
    // ========================================================================
    public static int getExtraRareLootCount(int level) {
        return Math.max(1, Math.min(level, MAX_LEVEL));
    }
}
