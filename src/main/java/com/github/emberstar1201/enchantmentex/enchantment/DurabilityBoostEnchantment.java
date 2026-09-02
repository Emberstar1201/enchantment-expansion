package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 耐久强化（Durability Boost）
//
// 适用：全部可损耗耐久的物品（工具/武器/盔甲）
// 最高等级：III
//
// 【双机制叠加】
// 机制一：最大耐久提升（Mixin ItemStack#getMaxDamage）
//   I 级：最大耐久 ×1.5（+50%）
//   II 级：最大耐久 ×2.0（+100%）
//   III 级：最大耐久 ×2.5（+150%）
//   F3+H 悬浮窗显示的 Max Damage、耐久条比例均按新上限更新。
//
// 机制二：概率不消耗耐久（Mixin ItemStack#hurt）
//   I 级：50% 概率跳过耐久损耗
//   II 级：66% 概率跳过耐久损耗
//   III 级：75% 概率跳过耐久损耗
//
// 获取：附魔台 + 宝箱 + 村民交易（非宝藏附魔）
// 互斥：与原版耐久 Unbreaking、中国制造、匠心传承 三者互斥
// ========================================================================
public class DurabilityBoostEnchantment extends Enchantment {

    public DurabilityBoostEnchantment() {
        super(Rarity.UNCOMMON, EnchantmentCategory.BREAKABLE,
                new EquipmentSlot[]{
                        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
                        EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS, EquipmentSlot.FEET
                });
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinCost(int level) {
        return 10 + (level - 1) * 8;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
    }

    @Override
    public boolean isTreasureOnly() {
        return false;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.isDamageableItem();
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return canEnchant(stack);
    }

    // ========================================================================
    // 互斥：与原版耐久 Unbreaking / 中国制造 / 匠心传承 三者互斥
    //   - 原版耐久 I/II/III：20%/33%/40% 不耗耐久，本附魔更强且功能重叠
    //   - 中国制造：已包含更大的耐久强化 + Unbreaking 机制，互斥防叠加
    //   - 匠心传承：独立的镐/斧/锹效果，但用户需求"中国制造=三者合一"，
    //     故匠心传承附魔本身也不能与耐久强化共存（避免双份匠心传承）
    // ========================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        if (other == net.minecraft.world.item.enchantment.Enchantments.UNBREAKING) {
            return false;
        }
        if (other == ModEnchantments.MADE_IN_CHINA.get()) {
            return false;
        }
        if (other == ModEnchantments.ARTISAN_LEGACY.get()) {
            return false;
        }
        return super.checkCompatibility(other);
    }
}
