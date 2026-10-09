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

    /** 是否已被驯服（只有驯服后的个体才允许打开背包、夜晚找床睡觉）。 */
    boolean isTamed();

    /** 是否被主人命令原地坐下（睡觉时若被命令坐下应立即让位）。 */
    boolean isOrderedToSit();

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

    // ------------------------------------------------------------------
    // 工作模式（挖矿 / 农耕 / 点灯等）。当前该功能仍在开发中：对应 Goal
    // 尚未注册到任何实体，这里给出默认实现（永远待机、不支持切换），
    // 让网络包与未启用的 Goal 代码能够编译且行为完全为空。
    // 等实体正式接入工作模式时再覆写这三个方法。
    // ------------------------------------------------------------------

    /** 当前工作模式；默认永远待机。 */
    default GirlWorkMode getWorkMode() {
        return GirlWorkMode.IDLE;
    }

    /** 切换工作模式；默认空实现（未接入该功能的实体忽略请求）。 */
    default void setWorkMode(GirlWorkMode mode) {
    }

    /** 是否支持指定工作模式；默认全部不支持。 */
    default boolean supportsWorkMode(GirlWorkMode mode) {
        return false;
    }
}
