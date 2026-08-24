package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 【贯穿链条】(Chain Arrow) 附魔 - 弓 / 弩专属
//
// 效果：
//   箭矢命中目标后，会寻找附近最近的其他敌人并弹射过去继续造成伤害，
//   形成"链条"。等级越高弹射次数越多：
//     I  ：命中后弹射 1 次（共命中 2 个目标）
//     II ：命中后弹射 2 次（共命中 3 个目标）
//     III：命中后弹射 3 次（共命中 4 个目标）
//   每次弹射伤害约为箭基础伤害的 80%（可在配置中调整）。
//
// 实现：实体命中处理见 ChainArrowHandler（箭 NBT 打标记 +
//      ProjectileImpactEvent 命中时循环寻找下一目标），纯事件方案，无需 Mixin。
//
// 获取：附魔台 + 宝箱 / 村民（可发现）
// 互斥：与「爆破箭矢」(explosive_arrow) 互斥（同一支箭只能有一种弹药效果）
// ========================================================================
public class ChainArrowEnchantment extends Enchantment {

    // 最高等级 III
    private static final int MAX_LEVEL = 3;

    public ChainArrowEnchantment() {
        // Rarity.VERY_RARE：稀有
        // EnchantmentCategory.BOW：弓类基础分类（可额外支持弩）
        super(
                Rarity.VERY_RARE,
                EnchantmentCategory.BOW,
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
        return 15 + (level - 1) * 8;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
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

    // ========================================================================
    // 适用物品：弓 + 弩（两个都支持）
    // ========================================================================
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
    }

    // 附魔台上弩也能出现该附魔
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return canEnchant(stack);
    }

    // ========================================================================
    // 【互斥】与「爆破箭矢」互斥：同一支箭不能同时装贯穿与爆破
    // ========================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        if (other == ModEnchantments.EXPLOSIVE_ARROW.get()) {
            return false;
        }
        // 其余附魔保持默认兼容
        return super.checkCompatibility(other);
    }
}