package com.example.examplemod.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;

// ========================================================================
// 【风踏涟漪】附魔 - 靴子专属
// 效果：
//   1. 陆地移速提升 1.5 倍
//   2. 可在水面行走（如同踩在实体方块上），水面移速 1.25 倍
//   3. 潜行时所有加成失效
//   4. 飞行/骑乘/完全沉入水中时不生效
// 冲突：深海探索者、冰霜行者、灵魂疾行、迅捷潜行
// ========================================================================
public class WindRippleEnchantment extends Enchantment {

    // 最高等级 I（1级）
    private static final int MAX_LEVEL = 1;

    public WindRippleEnchantment() {
        // Rarity.RARE：稀有度稀有
        // EnchantmentCategory.ARMOR_FEET：仅靴子类装备可附魔
        // EquipmentSlot.FEET：仅脚部装备栏生效
        super(
                Rarity.RARE,
                EnchantmentCategory.ARMOR_FEET,
                new EquipmentSlot[]{EquipmentSlot.FEET}
        );
    }

    // 获取附魔最低等级（从1级开始）
    @Override
    public int getMinLevel() {
        return 1;
    }

    // 获取附魔最高等级（设置为1级，不可继续升级）
    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    // 附魔在附魔台出现的最低经验成本
    // Lv1 约 15~30 经验区间，属于较稀有的附魔
    @Override
    public int getMinCost(int level) {
        return 15 + (level - 1) * 10;
    }

    // 附魔在附魔台出现的最高经验成本
    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
    }

    // 非宝藏附魔：附魔台可直接获得
    @Override
    public boolean isTreasureOnly() {
        return false;
    }

    // 可通过村民图书管理员交易获得附魔书
    @Override
    public boolean isTradeable() {
        return true;
    }

    // 可通过宝箱/钓鱼/附魔台等随机发现
    @Override
    public boolean isDiscoverable() {
        return true;
    }

    // 允许附魔书在铁砧上应用到靴子
    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // ========================================================================
    // 【冲突设置】与其他靴子移动类附魔互斥
    // 用户明确要求冲突的四种：
    //   1. 深海探索者 (Depth Strider) - 同样影响水中/水面移动
    //   2. 冰霜行者 (Frost Walker)   - 同样在水面生效（结冰 vs 踩水）
    //   3. 灵魂疾行 (Soul Speed)      - 灵魂沙上加速，同为靴子移速类
    //   4. 迅捷潜行 (Swift Sneak)     - 潜行时加速，与本附魔"潜行失效"机制冲突
    // ========================================================================
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        if (other == Enchantments.DEPTH_STRIDER) return false;
        if (other == Enchantments.FROST_WALKER) return false;
        if (other == Enchantments.SOUL_SPEED) return false;
        if (other == Enchantments.SWIFT_SNEAK) return false;
        // 其他附魔：沿用原版默认逻辑（同类附魔互斥，其余兼容）
        return super.checkCompatibility(other);
    }

    // 检查此附魔是否可应用到给定物品栈
    // 仅靴子类护甲（实现 ArmorItem 且槽位为 FEET）可附魔
    // 【Forge 1.20.1 注意】：ArmorItem 实现了 Equipment 接口，正确获取槽位的方法是
    // getEquipmentSlot()，而非不存在的 getSlot()；也可直接委托给父类 canEnchant()
    // 因为构造时已传入 EnchantmentCategory.ARMOR_FEET，父类会自动判断
    @Override
    public boolean canEnchant(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armorItem) {
            return armorItem.getEquipmentSlot() == EquipmentSlot.FEET;
        }
        return false;
    }
}
