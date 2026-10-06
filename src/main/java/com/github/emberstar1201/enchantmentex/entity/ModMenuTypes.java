package com.github.emberstar1201.enchantmentex.entity;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.entity.menu.ZombieGirlInventoryMenu;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模组自定义容器菜单类型注册。
 * 在主类构造函数中调用 {@link #register(IEventBus)} 完成注册。
 */
public final class ModMenuTypes {

    /** 菜单类型延迟注册器。 */
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, EnchantmentExpansion.MODID);

    /**
     * 娘系实体背包菜单（丧尸娘 / 溺尸娘共用）。
     * 使用 {@link IForgeMenuType#create} 是为了在客户端重建菜单时能从附加数据中
     * 读取实体 id（NetworkHooks.openScreen 时写入），再从世界中取回对应实体。
     */
    public static final RegistryObject<MenuType<ZombieGirlInventoryMenu>> ZOMBIE_GIRL_INVENTORY =
            MENU_TYPES.register("zombie_girl_inventory",
                    () -> IForgeMenuType.create((windowId, inventory, data) -> {
                        Entity entity = inventory.player.level().getEntity(data.readVarInt());
                        if (entity instanceof FriendlyGirlInventory friendlyGirl) {
                            return new ZombieGirlInventoryMenu(windowId, inventory, friendlyGirl);
                        }
                        // 实体已消失（死亡 / 卸载）时不应打开菜单，直接抛错由网络层中断
                        throw new IllegalStateException(
                                "打开娘系实体背包失败：附加数据中的实体未实现 FriendlyGirlInventory，实际为 " + entity);
                    }));

    private ModMenuTypes() {
    }

    /** 在主类构造函数中调用的注册方法。 */
    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}
