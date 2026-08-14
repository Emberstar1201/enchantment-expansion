package com.github.emberstar1201.enchantmentex.entity;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// ========================================================================
// 实体注册类
// 注册琉璃冰魄箭的自定义箭矢实体
// 在主类构造函数中调用 register(modEventBus) 完成注册
// ========================================================================
public class ModEntities {

    // 实体延迟注册器
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, EnchantmentExpansion.MODID);

    // 琉璃冰魄箭实体类型
    // MobCategory.MISC：杂项分类，不受怪物容量限制
    // sized(0.5F, 0.5F)：与原版箭矢碰撞箱一致
    // clientTrackingRange(4)：客户端追踪范围4格
    // updateInterval(20)：每20 tick 同步一次数据（与原版箭一致）
    public static final RegistryObject<EntityType<GlacialArrowEntity>> GLACIAL_ARROW =
            ENTITY_TYPES.register("glacial_arrow",
                    () -> EntityType.Builder.<GlacialArrowEntity>of(
                                    GlacialArrowEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(4)
                            .updateInterval(20)
                            .build("glacial_arrow"));

    // 在主类构造函数中调用的注册方法
    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}