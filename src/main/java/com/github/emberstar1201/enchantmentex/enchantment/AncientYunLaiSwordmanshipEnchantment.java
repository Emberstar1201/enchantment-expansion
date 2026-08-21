package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 古·云来剑法 附魔定义
//
// 【效果】
//   最高等级：I 级
//   攻击速度：+5.0（ADDITION 操作）
//   攻击距离：+5.0 格（基础 3 格 → 8 格）
//   伤害倍率：×1.50（+50%，配置可调）
//   手持时直接生效
//
// 【获取】
//   仅宝箱/村民交易（treasure = true）
//
// 【冲突】
//   与 云来剑法 互斥（checkCompatibility）
// ========================================================================
public class AncientYunLaiSwordmanshipEnchantment extends Enchantment {

    public AncientYunLaiSwordmanshipEnchantment() {
        super(
                Rarity.VERY_RARE,
                EnchantmentCategory.WEAPON,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND}
        );
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public boolean isTreasureOnly() {
        return true; // 仅宝箱/村民交易
    }

    @Override
    public boolean isTradeable() {
        return true; // 村民交易可获得
    }

    @Override
    public boolean isDiscoverable() {
        return false; // 附魔台不可出，但战利品表可获得
    }

    // 与云来剑法互斥
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        if (other instanceof YunLaiSwordmanshipEnchantment) {
            return false;
        }
        return super.checkCompatibility(other);
    }
}