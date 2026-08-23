package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.ForgeRegistries;
import com.github.emberstar1201.enchantmentex.AgricultureConfig;

// ========================================================================
// 【渔获大师】附魔 - 鱼竿专用（FISHING_MASTER）
//
// 【效果】（数值见 AgricultureConfig）
//   一、咬钩加速：浮漂入水后咬钩等待大幅缩短
//        I : 等待 ×2     II: 等待 ×3     III: 等待 ×4
//        （实现：Mixin 缩短 FishingHook 的咬钩计时 nibble）
//   二、战利品提升：钓起时概率追加稀有战利品（II 起生效）
//        II : +附魔书、命名牌      III: +钻石、青金石
//
// 【获取】附魔台 / 宝箱 / 村民交易（RARE 稀有）
// 【最高等级】III
// 【冲突】与原版「饵钓」（Lure）、「海之眷顾」（Luck of the Sea）互斥
// ========================================================================
public class FishingMasterEnchantment extends Enchantment {

    private static final int MAX_LEVEL = 3;

    // 原版冲突附魔的稳定注册表 ID（避免 mapping 字段名差异）
    private static final ResourceLocation LURE_RL = ResourceLocation.parse("minecraft:lure");
    private static final ResourceLocation LUCK_RL = ResourceLocation.parse("minecraft:luck_of_the_sea");

    public FishingMasterEnchantment() {
        // EnchantmentCategory.FISHING_ROD：鱼竿专用
        super(
                Rarity.RARE,
                EnchantmentCategory.FISHING_ROD,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND}
        );
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
        return 15 + (level - 1) * 12;  // Lv1=15, Lv2=27, Lv3=39
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 20;
    }

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

    // 仅鱼竿可附魔
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof FishingRodItem;
    }

    // 与原版饵钓/海之眷顾互斥（同类加速与战利品提效不叠加）
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        ResourceLocation otherRL = ForgeRegistries.ENCHANTMENTS.getKey(other);
        if (otherRL != null && (LURE_RL.equals(otherRL) || LUCK_RL.equals(otherRL))) {
            return false;
        }
        return super.checkCompatibility(other);
    }
}