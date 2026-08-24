package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

// ========================================================================
// 【幽匿行者】(Dark Walker) 附魔 - 靴子专属
//
// 效果（默认全局生效，任意维度/生物群系；可在配置中改为仅限生物群系）：
//   1. 完全隐身于幽匿生态：
//      - 不会触发幽匿感测体、幽匿尖啸体等任何幽匿方块
//      - 监守者无法检测到玩家（即使玩家在它面前走动、奔跑、挖掘，甚至贴脸）
//      - 玩家不产生任何振动（vibration）
//   2. 本质说明：
//      原版监守者/幽匿方块的"检测链"分为两条：
//      a) 振动感知：玩家行走/奔跑/挖掘会发出游戏事件(GameEvent)，
//         经 ServerLevel.gameEvent 分派给幽匿感测体/尖啸体/监守者。
//         → 由 ServerLevelGameEventMixin 在本附魔激活时拦截振动。
//      b) 实体感知：监守者通过 WardenEntitySensor 在 24 格半径内
//         检索可攻击实体，并用 Warden.canTargetEntity() 过滤；
//         贴脸碰撞、被攻击也会提升怒气（同样走 canTargetEntity）。
//         → 由 WardenCanTargetMixin 在 canTargetEntity() 中对
//           本附魔玩家返回 false，监守者会彻底无视玩家。
//
// 获取：附魔台可直接获得（非宝藏）+ 遗迹宝箱中也会出现
// 冲突：与原版「迅捷潜行」(Swift Sneak) 互斥（同为深暗之域靴子附魔，
//      主题性质相反：迅捷潜行是"被监守者发现时快速逃离"，幽匿行者是
//      "完全不被发现"，两者同装会互相矛盾）
// ========================================================================
public class DarkWalkerEnchantment extends Enchantment {

    // 最高等级 I（1级）
    private static final int MAX_LEVEL = 1;

    public DarkWalkerEnchantment() {
        // Rarity.VERY_RARE：稀有度非常稀有（深暗之域主题，与监守者同级别存在）
        // EnchantmentCategory.ARMOR_FEET：仅靴子类装备可附魔
        super(
                Rarity.VERY_RARE,
                EnchantmentCategory.ARMOR_FEET,
                new EquipmentSlot[]{EquipmentSlot.FEET}
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

    // 附魔台最低经验成本：非常稀有附魔对应较高成本（30 起步）
    @Override
    public int getMinCost(int level) {
        return 30;
    }

    @Override
    public int getMaxCost(int level) {
        return 50;
    }

    // 非宝藏附魔：附魔台可直接获得（用户明确要求附魔台获取）
    @Override
    public boolean isTreasureOnly() {
        return false;
    }

    // 可在遗迹宝箱中随机发现（用户明确要求遗迹宝箱获取）
    @Override
    public boolean isDiscoverable() {
        return true;
    }

    // 允许附魔书在铁砧上应用
    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // ========================================================================
    // 【冲突设置】仅与「迅捷潜行」(Swift Sneak) 互斥
    // 理由：Swift Sneak 是 1.19 深暗之域配套附魔（潜行时缓步移动，
    //      避免被监守者探测），与幽匿行者"完全不被探测"主题相悖。
    // 其余靴子附魔（冰霜行者/灵魂疾行/深海探索者/优雅猫步/风踏涟漪）：
    //   - 深海探索者/冰霜行者：与水系/熔岩相关，不冲突
    //   - 优雅猫步/风踏涟漪：同为移速类，但 EnchantmentCategory 相同
   //     的附魔原版默认不互斥（互斥需在配置表中声明），保持默认兼容
    // ========================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        if (other == net.minecraft.world.item.enchantment.Enchantments.SWIFT_SNEAK) {
            return false;
        }
        return super.checkCompatibility(other);
    }

    // 仅靴子类护甲（实现 ArmorItem 且槽位为 FEET）可附魔
    @Override
    public boolean canEnchant(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armorItem) {
            return armorItem.getEquipmentSlot() == EquipmentSlot.FEET;
        }
        return false;
    }
}