package com.github.emberstar1201.enchantmentex.entity;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 友好娘系实体的随身背包能力接口。
 *
 * 丧尸娘与溺尸娘的实体类分别继承 Zombie / Drowned，无法共用基类，
 * 因此把背包菜单（ZombieGirlInventoryMenu）需要的最小能力抽成接口：
 * 两个实体各自实现后即可共用同一套「6 装备槽 + 64 格背包」GUI。
 *
 * isAlive / isRemoved / getItemBySlot / setItemSlot / setDropChance
 * 均由父类 LivingEntity / Mob 提供公开实现，实现类只需补齐其余三个方法。
 */
public interface FriendlyGirlInventory {

    /** 是否已被驯服（只有驯服后的个体才允许打开背包）。 */
    boolean isTamed();

    /** 该玩家是否是这只个体的主人。 */
    boolean isOwnedBy(Player player);

    /** 随身背包容器（64 格，随实体 NBT 持久化）。 */
    SimpleContainer getMeatInventory();

    /** 实体存活判定（Entity 公开方法，接口声明以便菜单持有接口引用时调用）。 */
    boolean isAlive();

    /** 实体是否已被移除（Entity 公开方法）。 */
    boolean isRemoved();

    /** 读取实体真实装备槽（LivingEntity 公开方法）。 */
    ItemStack getItemBySlot(EquipmentSlot slot);

    /** 写入实体真实装备槽，盔甲等属性由原版装备系统实时结算（LivingEntity 公开方法）。 */
    void setItemSlot(EquipmentSlot slot, ItemStack stack);

    /** 设置装备掉落概率，通过 GUI 放入的装备设为 100% 回收（Mob 公开方法）。 */
    void setDropChance(EquipmentSlot slot, float chance);
}
