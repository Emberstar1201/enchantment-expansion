package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 【温度恒定】(Temperature Constant) 附魔 - 护甲（任意部位）
//
// 效果：
//   穿戴者免疫极端温度伤害：
//     - 防冰冻：细雪（Powder Snow）冻伤（DamageTypeTags.IS_FREEZING）
//     - 防高温：火焰 / 岩浆 / 高温地面灼烧（DamageTypeTags.IS_FIRE）
//   只要任意一件护甲带有本附魔即全局生效（不限于特定部位）。
//
// 实现：伤害拦截 + 冻结值清零见 TemperatureConstantHandler
//      （LivingHurtEvent 拦截伤害源 + PlayerTickEvent 清除冻结计时），
//      纯事件方案，无需 Mixin。
//
// 获取：附魔台 + 宝箱 / 村民（可发现）
// 互斥：无强制互斥（与火焰保护、水下呼吸等原版附魔都可共存）
// ========================================================================
public class TemperatureConstantEnchantment extends Enchantment {

    // 最高等级 I
    private static final int MAX_LEVEL = 1;

    public TemperatureConstantEnchantment() {
        // Rarity.RARE：稀有
        // EnchantmentCategory.ARMOR：所有护甲部位适用
        super(
                Rarity.RARE,
                EnchantmentCategory.ARMOR,
                new EquipmentSlot[]{
                        EquipmentSlot.HEAD,
                        EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS,
                        EquipmentSlot.FEET
                }
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
        return 20;
    }

    @Override
    public int getMaxCost(int level) {
        return 40;
    }

    // 非宝藏附魔：附魔台直接可获得
    @Override
    public boolean isTreasureOnly() {
        return false;
    }

    @Override
    public boolean isDiscoverable() {
        return true;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // 任意护甲部位（实现 ArmorItem 即可）
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem;
    }
}