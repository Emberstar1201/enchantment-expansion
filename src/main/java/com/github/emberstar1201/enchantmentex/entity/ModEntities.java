package com.github.emberstar1201.enchantmentex.entity;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// ========================================================================
// 实体注册类
// 注册琉璃冰魄箭实体 + 月牙形剑气实体
// 在主类构造函数中调用 register(modEventBus) 完成注册
// ========================================================================
public class ModEntities {

    // 实体延迟注册器
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, EnchantmentExpansion.MODID);

    // ================================================================
    // 1. 琉璃冰魄箭实体
    //    MobCategory.MISC：杂项分类，不受怪物容量限制
    //    sized(0.5F, 0.5F)：与原版箭矢碰撞箱一致
    // ================================================================
    public static final RegistryObject<EntityType<GlacialArrowEntity>> GLACIAL_ARROW =
            ENTITY_TYPES.register("glacial_arrow",
                    () -> EntityType.Builder.<GlacialArrowEntity>of(
                                    GlacialArrowEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(4)
                            .updateInterval(20)
                            .build("glacial_arrow"));

    // ================================================================
    // 2. 月牙形剑气实体（云来剑法 / 古·云来剑法）
    //    碰撞箱稍小（0.3F），以匹配月牙视觉大小
    //    跟踪范围稍大（10），确保远距离可见
    //    更新间隔较短（2），保证粒子同步流畅
    // ================================================================
    public static final RegistryObject<EntityType<CrescentEntity>> CRESCENT =
            ENTITY_TYPES.register("crescent",
                    () -> EntityType.Builder.<CrescentEntity>of(
                                    CrescentEntity::new, MobCategory.MISC)
                            .sized(0.3F, 0.3F)
                            .clientTrackingRange(10)
                            .updateInterval(2)
                            .build("crescent"));

    // ================================================================
    // 3. 自定义闪电实体（人权剑「人的意志」使用）
    //    与原版 LightningBolt 尺寸/跟踪参数一致：
    //    sized(0,0)         → 无碰撞箱（闪电不阻挡实体）
    //    clientTrackingRange(16) → 与原版闪电一致
    //    updateInterval(MAX)  → 服务器几乎不推送状态更新（视觉由客户端模拟）
    // ================================================================
    public static final RegistryObject<EntityType<CustomLightningEntity>> CUSTOM_LIGHTNING =
            ENTITY_TYPES.register("custom_lightning",
                    () -> EntityType.Builder.<CustomLightningEntity>of(
                                    CustomLightningEntity::new, MobCategory.MISC)
                            .sized(0.0F, 0.0F)
                            .clientTrackingRange(16)
                            .updateInterval(Integer.MAX_VALUE)
                            .build("custom_lightning"));

    // 在主类构造函数中调用的注册方法
    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}