package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.ForgeRegistries;

// ========================================================================
// "星火不灭"（Eternal Spark）附魔
//
// 【适用物品】剑、斧、三叉戟（EnchantmentCategory.WEAPON）
// 【最高等级】I（1级）
// 【获取方式】合成获得：下界之星 × 1 + 烈焰棒 × 4 + 书 × 4
//
// 【效果】
//   1. 攻击命中时给目标添加"星火"标记
//   2. 标记期间每秒造成"最大生命值 × 2%"的真实伤害，持续 5 秒
//   3. 标记可叠加：每层额外 +5% 伤害倍率，上限 3 层
//      - 1 层：2% 最大生命值 / 秒
//      - 2 层：2% × (1+5%) = 2.1% / 秒
//      - 3 层：2% × (1+10%) = 2.2% / 秒
//   4. 带标记目标死亡时，星火扩散至周围 5 格内的敌对生物
//   5. ★ 人权剑「人的意志」buff 激活期间：
//      - 扩散范围翻倍（5 格 → 10 格）
//      - 基础伤害从 2% → 3% 最大生命值
//
// 【视觉效果】
//   - 标记状态：目标身上生成暗红/金色火焰粒子（SOUL_FIRE_FLAME）
//   - 扩散时：死亡位置生成金色冲击波环形粒子（END_ROD）
// ========================================================================
public class EternalSparkEnchantment extends Enchantment {

    public EternalSparkEnchantment() {
        // Rarity.VERY_RARE：附魔台稀有出现（虽然主要通过合成获取）
        // EnchantmentCategory.WEAPON：剑、斧、三叉戟都被归为此类
        // EquipmentSlot.MAINHAND：仅主手生效
        super(Rarity.VERY_RARE, EnchantmentCategory.WEAPON,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 1; // 最高 I 级
    }

    @Override
    public int getMinCost(int level) {
        // 仅 1 级，附魔等级 50（非常稀有）
        return 50;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 30;
    }

    // 主要通过合成获得，附魔台极稀有也能出
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
        return true; // 附魔台极稀有可出（主要合成获取）
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // ========================================================================
    // 【平衡性冲突】与"熵增"、"兵长的回声"互斥
    // 三者同属"基于目标生命百分比造成伤害"的附魔，叠加会导致
    // 伤害随 Boss 血量无限成长（百分比真伤三件套），故两两互斥。
    // 使用 ResourceLocation 比较，避免 mappings 版本差异问题。
    // ========================================================================
    private static final ResourceLocation ENTROPY_RL =
            new ResourceLocation("enchantment_expansion", "entropy");
    private static final ResourceLocation LEVIS_ECHO_RL =
            new ResourceLocation("enchantment_expansion", "levis_echo");

    @Override
    protected boolean checkCompatibility(Enchantment other) {
        ResourceLocation otherRL = ForgeRegistries.ENCHANTMENTS.getKey(other);
        if (ENTROPY_RL.equals(otherRL) || LEVIS_ECHO_RL.equals(otherRL)) {
            return false;  // 与熵增、兵长的回声互斥
        }
        return super.checkCompatibility(other);
    }
}
