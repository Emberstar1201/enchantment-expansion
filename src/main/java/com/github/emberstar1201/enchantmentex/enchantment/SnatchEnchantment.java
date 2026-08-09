package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

// ========================================================================
// 【攫取】附魔 - 近战武器通用（剑、斧、三叉戟）
//
// 效果（v2.0 缴械版，替代原"抢夺装备"机制）：
//   攻击生物时，按附魔等级触发"缴械"：
//     ① 概率判定 → 打落目标主手武器（目标进入空手状态）
//     ② 再单独判定（概率为武器的一半，向下取整）→ 随机打落一件护甲
//   被打落的物品保留原有 NBT（附魔、耐久、自定义名等），生成在目标位置地上，玩家可拾取
//   对没有武器/盔甲的目标对应槽位不生效
//
// 概率表（每个槽独立判定）：
//   Lv I = 10%（武器）/ 5%（盔甲）
//   Lv II = 20% / 10%
//   Lv III = 40% / 20%
//   Lv IV = 60% / 30%
//   Lv V = 80% / 40%
//
// 获取方式：
//   Lv I-III：附魔台可直接获得
//   Lv IV-V ：仅遗迹宝箱获取（通过 minCost > 30 机制过滤，附魔台抽不到）
//
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

    // 攫取"武器缴械"概率表
    //   [0] = 1 级（10%）... [4] = 5 级（80%）
    private static final double[] TRIGGER_CHANCES = { 0.10, 0.20, 0.40, 0.60, 0.80 };

    // 抢夺附魔的稳定注册表 ID：minecraft:looting（避免 mapping 字段名差异）
    private static final ResourceLocation LOOTING_RL = new ResourceLocation("minecraft", "looting");

    public SnatchEnchantment() {
        // Rarity.RARE：稀有度稀有（稀有附魔：I-III 在附魔台仍合理可见）
        // EnchantmentCategory.WEAPON：剑/斧/三叉戟等武器类（实际判定用 canEnchant 扩展）
        // 主手武器生效（基于攻击时手持判定）
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

    // 允许附魔书在铁砧上应用到剑/斧/三叉戟
    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // ========================================================================
    // 【冲突设置】与"抢夺"（Looting）互斥
    // 两者同属"近战强化掉落"类附魔：抢夺增加掉落物数量，攫取缴械敌方装备
    //   → 按用户要求互斥（checkCompatibility 返回 false）
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
    // 用户明确要求扩展：剑、斧、三叉戟等近战武器
    //   - SwordItem：剑（钻石剑、铁剑、下界合金剑等）
    //   - AxeItem：斧（可作武器用）
    //   - TridentItem：三叉戟（近战/远程通用）
    // 注意：弓/弩不在范围内（属于 RANGED 类，不在 WEAPON 分类下）
    // ========================================================================
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem;
    }

    // ========================================================================
    // 工具方法：给定附魔等级，返回"武器缴械"概率
    // 等级超出范围则钳制：<1 按 1 级（10%），>5 按 5 级（80%）
    // ========================================================================
    public static double getTriggerChance(int level) {
        int idx = Math.max(0, Math.min(level - 1, MAX_LEVEL - 1));
        return TRIGGER_CHANCES[idx];
    }

    // ========================================================================
    // 工具方法：给定附魔等级，返回"盔甲缴械"概率
    // 用户要求：盔甲缴械概率为武器缴械概率的一半（向下取整）
    //   Lv I = 10% / 2 = 5%   （0.10/2=0.05）
    //   Lv II = 20% / 2 = 10% （0.20/2=0.10）
    //   Lv III = 40% / 2 = 20% （0.40/2=0.20）
    //   Lv IV = 60% / 2 = 30% （0.60/2=0.30）
    //   Lv V = 80% / 2 = 40%  （0.80/2=0.40）
    // ========================================================================
    public static double getArmorTriggerChance(int level) {
        return getTriggerChance(level) / 2.0;
    }
}
