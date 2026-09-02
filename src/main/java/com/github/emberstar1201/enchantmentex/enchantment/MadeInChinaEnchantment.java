package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// "中国制造"附魔（Made in China）
//
// 恶搞附魔：耐久强化 + 匠心传承 + 原版耐久 三者混合体
// 一句话概括：将耐用、耐操、经济实惠于一身。
//  - 适用：所有可损耗耐久的物品（BREAKABLE）
//  - 最高等级：I（单等级）
//  - 效果（详见 MadeInChinaHandler）：
//      1) 每 3 秒自动恢复 1 点耐久（融合原版耐久 / 耐久强化 / 自动修复概念）
//      2) 镐/斧/锹额外触发"匠心传承"效果：记忆方块、越挖越快、几率额外掉落
//         （由 ArtisanLegacyHandler 共用触发，避免重复实现）
//  - 获取：附魔台 + 村民交易 + 遗迹宝箱（非宝藏附魔，三种途径均可获得）
//  - 互斥：与原版耐久（Unbreaking）、耐久强化（Durability Boost）、匠心传承（Artisan Legacy）三者互斥
// ========================================================================
public class MadeInChinaEnchantment extends Enchantment {

    private static final int MAX_LEVEL = 1;

    public MadeInChinaEnchantment() {
        // Rarity.RARE：稀有度"稀有"
        // EnchantmentCategory.BREAKABLE：所有可损耗耐久的物品（武器/盔甲/工具）
        // 全部装备槽：MAINHAND/OFFHAND/HEAD/CHEST/LEGS/FEET 都可生效
        super(Rarity.RARE, EnchantmentCategory.BREAKABLE,
                new EquipmentSlot[]{
                        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
                        EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS, EquipmentSlot.FEET
                });
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
        return 25;
    }

    @Override
    public int getMaxCost(int level) {
        return 50;
    }

    // 非宝藏附魔：附魔台、村民交易、遗迹宝箱均可获得
    @Override
    public boolean isTreasureOnly() {
        return false;
    }

    // 可在村民交易中出现
    @Override
    public boolean isTradeable() {
        return true;
    }

    // 宝箱中可发现
    @Override
    public boolean isDiscoverable() {
        return true;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // 所有可损耗耐久物品均可附魔
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.isDamageableItem();
    }

    // 附魔台可获得（与耐久强化一致：canEnchant 即可上台）
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return canEnchant(stack);
    }

    // ========================================================================
    // 互斥：与原版耐久 / 耐久强化 / 匠心传承 三者互斥
    // 避免与三者叠加导致耐久效果过强或匠心传承效果重复触发
    // ========================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        if (other == net.minecraft.world.item.enchantment.Enchantments.UNBREAKING) {
            return false;
        }
        if (other == ModEnchantments.DURABILITY_BOOST.get()) {
            return false;
        }
        if (other == ModEnchantments.ARTISAN_LEGACY.get()) {
            return false;
        }
        return super.checkCompatibility(other);
    }

    // 对外静态 API
    public static int getMaxLevelStatic() {
        return MAX_LEVEL;
    }
}
