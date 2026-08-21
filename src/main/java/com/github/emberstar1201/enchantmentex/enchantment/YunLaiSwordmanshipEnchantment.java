package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 云来剑法 附魔定义
//
// 【效果】
//   最高等级：I 级
//   攻击速度：+2.5（ADDITION 操作）
//   攻击距离：+2.5 格（基础 3 格 → 5.5 格）
//   伤害倍率：×1.10（+10%，配置可调）
//   手持时直接生效
//
// 【获取】
//   附魔台可出（treasure = false）
//
// 【冲突】
//   与 古·云来剑法 互斥（checkCompatibility）
// ========================================================================
public class YunLaiSwordmanshipEnchantment extends Enchantment {

    public YunLaiSwordmanshipEnchantment() {
        super(
                Rarity.RARE,
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
        return false; // 附魔台可出
    }

    @Override
    public boolean isTradeable() {
        return true; // 村民交易可获得
    }

    @Override
    public boolean isDiscoverable() {
        return true; // 战利品表可获得
    }

    // 与古·云来剑法互斥
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        if (other instanceof AncientYunLaiSwordmanshipEnchantment) {
            return false;
        }
        return super.checkCompatibility(other);
    }
}