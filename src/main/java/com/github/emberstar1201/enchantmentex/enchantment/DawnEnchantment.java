package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 【拂晓重制】附魔 - 武器通用
//
// 【效果】
//   击杀敌对/中立生物永久成长，绑定玩家 PersistentData
//   每击杀：
//     伤害 +0.5%（上限 200%，400 杀）
//     暴击率 +0.2%（上限 55%，275 杀）
//     暴击伤害 +0.5%（上限 100%，200 杀）
//   攻击距离 +2.5 格（附魔即得，无需成长）
//   Boss 击杀额外 ×1.5~2 倍成长
//
// 【获取】附魔台可获得（仅 I 级）
// ========================================================================
public class DawnEnchantment extends Enchantment {

    private static final int MAX_LEVEL = 1;

    public DawnEnchantment() {
        // Rarity.UNCOMMON：附魔台可正常出现
        // EnchantmentCategory.WEAPON：剑、斧、三叉戟等近战武器
        // EquipmentSlot.MAINHAND：仅主手生效
        super(Rarity.UNCOMMON, EnchantmentCategory.WEAPON,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND});
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
        return 15;
    }

    @Override
    public int getMaxCost(int level) {
        return 30;
    }

    // 非宝藏附魔：附魔台可获得
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
}