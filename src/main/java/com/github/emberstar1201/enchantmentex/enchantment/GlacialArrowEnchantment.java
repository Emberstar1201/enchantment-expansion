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
// 【效果】（简化版：取消二段蓄力，改为直接散射）
//   蓄力加速：蓄力时间降至原版的50%（默认 0.5秒）
//   母箭速度：3.2倍原版箭速度，命中后半径2.5格 AOE 范围伤害
//   散射机制：射出时立即散射12支子箭（30°扇形）
//   牵引效果：母箭命中后牵引周围敌人
//   穿透效果：母箭可穿透3个敌人
//
// 【等级】1级（附魔台可获得，非宝藏）
// 【冲突】与云来弓法、古·云来弓法互斥（不可同时附魔）；与力量(POWER)兼容
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

    // ========================================================================
    // 【冲突规则】与云来弓法、古·云来弓法互斥
    // ========================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        // 与云来弓法互斥
        if (other instanceof YunLaiArcheryEnchantment) return false;
        // 与古·云来弓法互斥
        if (other instanceof AncientYunLaiEnchantment) return false;
        // 与爆破箭矢/贯穿链条/末影箭互斥（命中时多个 Handler 争夺箭矢事件）
        if (other == ModEnchantments.EXPLOSIVE_ARROW.get()) return false;
        if (other == ModEnchantments.CHAIN_ARROW.get()) return false;
        if (other == ModEnchantments.ENDER_ARROW.get()) return false;
        // 其余沿用父类逻辑（力量等 BOW 附魔可共存）
        return super.checkCompatibility(other);
    }

    // 检测是否可附魔到该物品（仅弓）
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof BowItem;
    }

    // ========================================================================
    // 对外 API
    // ========================================================================

    /** 获取蓄力加速倍率（蓄力时间=0.5s → 倍率=2.0，即原版2倍速蓄力） */
    public static double getChargeSpeedMultiplier() {
        return Config.glacialArrowChargeMultiplier;
    }

    /** 获取母箭速度倍率 */
    public static double getSpeed() {
        return Config.glacialArrowSpeed;
    }

    /** 获取母箭伤害倍率 */
    public static double getDamage() {
        return Config.glacialArrowDamage;
    }

    /** 获取母箭AOE范围半径 */
    public static double getAoeRange() {
        return Config.glacialArrowAoeRange;
    }

    /** 获取子箭数量 */
    public static int getSubArrowCount() {
        return Config.glacialArrowSubCount;
    }

    /** 获取扇形角度 */
    public static double getFanAngle() {
        return Config.glacialArrowFanAngle;
    }

    /** 获取子箭速度倍率 */
    public static double getSubSpeedMultiplier() {
        return Config.glacialArrowSubSpeed;
    }

    /** 获取子箭伤害倍率 */
    public static double getSubDamageMultiplier() {
        return Config.glacialArrowSubDamage;
    }

    /** 获取子箭AOE范围半径 */
    public static double getSubAoeRange() {
        return Config.glacialArrowSubAoeRange;
    }

    /** 获取牵引强度 */
    public static double getPullStrength() {
        return Config.glacialArrowPullStrength;
    }

    /** 获取牵引范围 */
    public static double getPullRange() {
        return Config.glacialArrowPullRange;
    }

    /** 获取粒子间隔 */
    public static int getParticleInterval() {
        return Config.glacialArrowParticleInterval;
    }
}