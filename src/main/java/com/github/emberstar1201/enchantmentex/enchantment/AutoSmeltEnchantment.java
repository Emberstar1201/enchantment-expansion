package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

// ========================================================================
// "自动冶炼"附魔 - 镐专属
//
// 【效果】
//   挖掘矿物时直接掉落成品（铁矿石→铁锭），无需烧制
//   I级：仅冶炼
//   II级：约33%几率额外+1
//   III级：约50%几率额外+1~2
//   每块矿石给予0.5~1 XP
//
// 【获取】附魔台可获得
// 【冲突】与时运（Fortune）互斥
// ========================================================================
public class AutoSmeltEnchantment extends Enchantment {

    private static final int MAX_LEVEL = 3;

    // 时运的注册表 ID，用于冲突检测
    private static final ResourceLocation FORTUNE_RL =
            ResourceLocation.parse("minecraft:fortune");

    public AutoSmeltEnchantment() {
        // Rarity.UNCOMMON：稀有度" uncommon"，附魔台可正常出现
        // EnchantmentCategory.DIGGER：镐、斧、锹类（实际通过 canEnchant 限制为仅镐）
        // EquipmentSlot.MAINHAND：仅主手生效
        super(Rarity.UNCOMMON, EnchantmentCategory.DIGGER,
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
        // I: 10, II: 18, III: 26
        return 10 + (level - 1) * 8;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
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

    // 仅镐可附魔
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof PickaxeItem;
    }

    // 与时运互斥
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        ResourceLocation otherRL = ForgeRegistries.ENCHANTMENTS.getKey(other);
        if (FORTUNE_RL.equals(otherRL)) {
            return false;
        }
        return super.checkCompatibility(other);
    }

    // ========================================================================
    // 对外 API
    // ========================================================================
    public static int getMaxLevelStatic() {
        return MAX_LEVEL;
    }
}