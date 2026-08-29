package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.ForgeRegistries;

// ========================================================================
// 【熵增】附魔
//
// 适用装备：武器（WEAPON）
// 最高等级：III
// 获取方式：附魔台 / 宝箱 / 村民交易 等常规途径
//
// 效果：
//   攻击命中时，目标身上叠加一层"熵"标记
//     1层：无额外效果
//     2层：目标移动速度 -15%
//     3层（满层）：引爆熵增，造成目标已损失生命值 × 30% 的额外真实伤害
//                  并扩散到周围所有敌对生物（半径 8 格），每个扩散目标继承原伤害的 50%
//   满层引爆后标记清零，重新叠加
// ========================================================================
public class EntropyEnchantment extends Enchantment {

    public EntropyEnchantment() {
        super(
                Rarity.RARE,
                EnchantmentCategory.WEAPON,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND}
        );
    }

    @Override
    public int getMinCost(int level) {
        return 10 + level * 15;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 20;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    // ========================================================================
    // 【平衡性冲突】与"星火不灭"、"兵长的回声"互斥
    // 三者同属"基于目标生命百分比造成伤害"的附魔，叠加会导致
    // 伤害随 Boss 血量无限成长（百分比真伤三件套），故两两互斥。
    // 使用 ResourceLocation 比较，避免 mappings 版本差异问题。
    // ========================================================================
    private static final ResourceLocation ETERNAL_SPARK_RL =
            new ResourceLocation("enchantment_expansion", "eternal_spark");
    private static final ResourceLocation LEVIS_ECHO_RL =
            new ResourceLocation("enchantment_expansion", "levis_echo");

    @Override
    protected boolean checkCompatibility(Enchantment other) {
        ResourceLocation otherRL = ForgeRegistries.ENCHANTMENTS.getKey(other);
        if (ETERNAL_SPARK_RL.equals(otherRL) || LEVIS_ECHO_RL.equals(otherRL)) {
            return false;  // 与星火不灭、兵长的回声互斥
        }
        return super.checkCompatibility(other);
    }
}