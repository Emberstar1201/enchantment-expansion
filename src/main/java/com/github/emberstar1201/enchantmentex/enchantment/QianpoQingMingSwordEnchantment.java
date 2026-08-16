package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// "千破·青溟剑"附魔 - 剑类武器通用
//
// 【效果】
//   攻击生物时附加独立额外伤害（与武器伤害叠加触发）
//   该额外伤害无视护盾、保护附魔、盔甲防御（直接作用于生命值）
//   I: +10 | II: +20 | III: +30 | IV: +40 | V: +50
//
// 【视觉】
//   攻击时在玩家→目标连线上生成橙色尾焰轨迹粒子 (ParticleTypes.FLAME)
//
// 【音效】
//   每次触发时播放铁傀儡挥击音效 (SoundEvents.IRON_GOLEM_ATTACK)
//
// 【稀有度】RARE：附魔台概率较低出现
// 【获取】附魔台可出（I~V级）
// ========================================================================
public class QianpoQingMingSwordEnchantment extends Enchantment {

    private static final int MAX_LEVEL = 5;

    public QianpoQingMingSwordEnchantment() {
        // Rarity.RARE：附魔台出现概率较低
        // EnchantmentCategory.WEAPON：剑、斧、三叉戟等所有攻击性武器
        // EquipmentSlot.MAINHAND：仅主手生效
        super(Rarity.RARE, EnchantmentCategory.WEAPON,
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

    // 附魔台等级消耗：I: 15，每级递增 5
    @Override
    public int getMinCost(int level) {
        return 15 + (level - 1) * 5;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 20;
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

    // ========================================================================
    // 对外 API
    // ========================================================================
    public static int getMaxLevelStatic() {
        return MAX_LEVEL;
    }
}