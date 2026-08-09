package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

// ========================================================================
// 【攫取】附魔 - 剑类武器专属
// 效果：击杀生物时，按概率抢夺其身上的装备（武器、盔甲）
//   抢夺的装备保留原有附魔/耐久/NBT，直接进入击杀者背包，满则掉地上
// 概率（每个装备槽独立判定）：
//   Lv I = 10%    Lv II = 20%    Lv III = 40%    Lv IV = 60%    Lv V = 80%
// 获取方式：
//   Lv I-III：附魔台可直接获得
//   Lv IV-V ：仅遗迹宝箱获取（附魔台无法获得，通过 minCost > 30 机制过滤）
// 冲突：抢夺（Looting）——同属"击杀掉落强化"类附魔，互斥
// ========================================================================
public class SnatchEnchantment extends Enchantment {

    // 最高等级 V（5级）
    private static final int MAX_LEVEL = 5;

    // ========================================================================
    // 附魔等级 → 经验成本（用于控制"哪些等级能在附魔台出现"）
    // 原版附魔台最高等级为 30，minCost 超过 30 的等级不会在附魔台被抽到。
    //   Lv I:   3-13    → 正常附魔台出现
    //   Lv II: 12-27    → 正常附魔台出现
    //   Lv III:22-37    → 附魔台有概率（满书架 30 级能覆盖 22+ 但不超 30 到 37）
    //   Lv IV:  50-65   → 绝对高于 30，附魔台不会出现
    //   Lv V:   80-95   → 绝对高于 30，附魔台不会出现
    // ========================================================================
    private static final int[] MIN_COSTS = { 3, 12, 22, 50, 80 };

    // 攫取触发概率表（每个装备槽独立）
    //   [0] = 1 级（10%）... [4] = 5 级（80%）
    private static final double[] TRIGGER_CHANCES = { 0.10, 0.20, 0.40, 0.60, 0.80 };

    // 抢夺附魔的稳定注册表 ID：minecraft:looting（避免 mapping 字段名差异）
    private static final ResourceLocation LOOTING_RL = new ResourceLocation("minecraft", "looting");

    public SnatchEnchantment() {
        // Rarity.RARE：稀有度稀有（稀有附魔：I-III 在附魔台仍合理可见）
        // EnchantmentCategory.WEAPON：剑/斧等武器类（实际判定用 canEnchant 限制为剑）
        // 主手武器生效（但实际效果触发基于击杀时手持判定，非槽位限制）
        super(
                Rarity.RARE,
                EnchantmentCategory.WEAPON,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND}
        );
    }

    // 获取附魔最低等级（从1级开始）
    @Override
    public int getMinLevel() {
        return 1;
    }

    // 获取附魔最高等级（设置为5级，不可继续升级）
    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    // 附魔在附魔台出现的最低经验成本
    // 关键：IV 级 minCost=50、V 级 80 → 均超过附魔台最大等级 30
    //   → 附魔台永远抽不到 IV/V 级，仅能通过宝箱/探索获得
    @Override
    public int getMinCost(int level) {
        int idx = Math.max(0, Math.min(level - 1, MAX_LEVEL - 1));
        return MIN_COSTS[idx];
    }

    // 附魔在附魔台出现的最高经验成本（最低成本+15，常规模式）
    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
    }

    // 非宝藏附魔：因为 I-III 级需要在附魔台出现
    //（IV/V 级通过 minCost > 30 机制保证附魔台不可获得，而非 isTreasureOnly）
    @Override
    public boolean isTreasureOnly() {
        return false;
    }

    // 可通过村民图书管理员交易获得附魔书（会给 I-III 级，因为 IV/V 是"稀有版"仅宝箱）
    @Override
    public boolean isTradeable() {
        return true;
    }

    // 可通过宝箱/钓鱼/附魔台等随机发现 → IV/V 级会出现在宝箱中
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
    // 两者同属"击杀时强化掉落"类附魔：抢夺增加掉落物数量，攫取抢夺装备
    //   → 机制相近，按用户要求互斥（checkCompatibility 返回 false）
    // 兼容其他 WEAPON 附魔（锋利、亡灵杀手、节肢杀手、击退、火焰附加、抢夺外的全部）
    // ========================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        ResourceLocation otherRL = ForgeRegistries.ENCHANTMENTS.getKey(other);
        if (LOOTING_RL.equals(otherRL)) {
            return false;  // 与抢夺附魔互斥
        }
        // 其他附魔：沿用原版默认逻辑（同类 WEAPON 附魔与本类互斥判断由父类处理）
        return super.checkCompatibility(other);
    }

    // ========================================================================
    // 检查此附魔是否可应用到给定物品栈
    // 用户明确要求：适用装备：剑（EnchantmentCategory.WEAPON）
    // 严格限制为 SwordItem（仅剑），斧等武器不可附魔（符合"攫取剑法"主题）
    // ========================================================================
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof SwordItem;
    }

    // ========================================================================
    // 工具方法：给定附魔等级，返回"每个装备槽"的抢夺概率
    // 等级超出范围则钳制：<1 按 1 级（10%），>5 按 5 级（80%）
    //   例如：铁砧与指令拿到的 6 级，按 80% 上限处理
    // ========================================================================
    public static double getTriggerChance(int level) {
        int idx = Math.max(0, Math.min(level - 1, MAX_LEVEL - 1));
        return TRIGGER_CHANCES[idx];
    }
}
