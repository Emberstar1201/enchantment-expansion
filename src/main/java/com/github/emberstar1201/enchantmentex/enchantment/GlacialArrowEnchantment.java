package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// "琉璃冰魄箭"附魔 - 弓专属
//
// 【效果】
//   一段蓄力（< 阈值 tick）：箭速 ×3，命中后半径1格 AOE（伤害×1.5），淡蓝粒子拖尾
//   二段蓄力（≥ 阈值 tick）：母箭速 ×5，半径2.5格 AOE（伤害×2.5），
//     射出2支子箭（120°扇面），子箭速度=母箭×0.8，伤害=母箭×0.5，AOE半径1.5格，
//     母箭和子箭命中后拉人（2格内→命中点，0.4格），冰晶粒子拖尾
//
// 【等级】1级（附魔台可获得，非宝藏）
// 【冲突】与力量(POWER)兼容
// ========================================================================
public class GlacialArrowEnchantment extends Enchantment {

    private static final int MAX_LEVEL = 1;

    public GlacialArrowEnchantment() {
        // Rarity.RARE：稀有度"稀有"
        // EnchantmentCategory.BOW：仅弓可附魔
        // EquipmentSlot.MAINHAND：仅主手生效
        super(Rarity.RARE, EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
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
        return 35;
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

    // 检测是否可附魔到该物品（仅弓）
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof BowItem;
    }

    // ========================================================================
    // 对外 API：获取蓄力阈值（二段蓄力所需 tick 数）
    // ========================================================================
    public static int getChargeThreshold() {
        return Config.glacialArrowChargeThreshold;
    }

    // 对外 API：获取一段蓄力速度倍率
    public static double getStage1Speed() {
        return Config.glacialArrowStage1Speed;
    }

    // 对外 API：获取一段蓄力伤害倍率
    public static double getStage1Damage() {
        return Config.glacialArrowStage1Damage;
    }

    // 对外 API：获取一段蓄力 AOE 半径
    public static double getStage1AoeRange() {
        return Config.glacialArrowStage1AoeRange;
    }

    // 对外 API：获取二段蓄力母箭速度倍率
    public static double getStage2Speed() {
        return Config.glacialArrowStage2Speed;
    }

    // 对外 API：获取二段蓄力母箭伤害倍率
    public static double getStage2Damage() {
        return Config.glacialArrowStage2Damage;
    }

    // 对外 API：获取二段蓄力母箭 AOE 半径
    public static double getStage2AoeRange() {
        return Config.glacialArrowStage2AoeRange;
    }

    // 对外 API：获取子箭数量
    public static int getSubArrowCount() {
        return Config.glacialArrowSubCount;
    }

    // 对外 API：获取子箭扇形角度
    public static double getFanAngle() {
        return Config.glacialArrowFanAngle;
    }

    // 对外 API：获取子箭速度倍率（相对于母箭）
    public static double getSubSpeedMultiplier() {
        return Config.glacialArrowSubSpeed;
    }

    // 对外 API：获取子箭伤害倍率（相对于母箭）
    public static double getSubDamageMultiplier() {
        return Config.glacialArrowSubDamage;
    }

    // 对外 API：获取子箭 AOE 半径
    public static double getSubAoeRange() {
        return Config.glacialArrowSubAoeRange;
    }

    // 对外 API：获取拉人强度
    public static double getPullStrength() {
        return Config.glacialArrowPullStrength;
    }

    // 对外 API：获取拉人范围
    public static double getPullRange() {
        return Config.glacialArrowPullRange;
    }

    // 对外 API：获取粒子间隔
    public static int getParticleInterval() {
        return Config.glacialArrowParticleInterval;
    }
}