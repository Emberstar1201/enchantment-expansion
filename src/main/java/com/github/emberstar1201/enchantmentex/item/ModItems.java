package com.github.emberstar1201.enchantmentex.item;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// ========================================================================
// 物品注册类
//
// 负责注册本模组中所有自定义 Item（非附魔）。
// 使用 DeferredRegister 机制注册到 ForgeRegistries.ITEMS。
//
// 【关键】Forge 1.20.1 中 Item.Properties.tab() 已被移除！
//   物品不会自动出现在创造模式物品栏。正确做法是监听
//   BuildCreativeModeTabContentsEvent 事件，将物品加入原版的 CreativeModeTab。
//   事件监听必须注册到 MOD 总线（不是 FORGE 总线）。
// ========================================================================
public class ModItems {

    // 延迟注册器（DeferredRegister）：注册类型为 Item
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, EnchantmentExpansion.MODID);

    // ========================================================================
    // 【终界之星】 - 击败末影龙后在返回传送门上方获得的史诗级物品
    //
    // Item.Properties 配置：
    //   .stacksTo(64)            ：可堆叠 64 个
    //   .rarity(Rarity.EPIC)     ：史诗稀有度，物品名显示为紫色
    //
    // 【关键】使用自定义 EndStarItem 类而非普通 Item：
    //   - EndStarItem 重写 isFoil() 返回 true
    //   - 物品始终带有动态附魔光效（与下界之星相同）
    //   - 在手持、背包、掉落、展示框中均可见
    //
    // 其他效果（飞行、减伤、伤害加成、粒子等）通过 EndStarHandler 事件驱动
    // 注册ID：end_star
    // ========================================================================
    public static final RegistryObject<Item> END_STAR = ITEMS.register("end_star",
            () -> new EndStarItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.EPIC)
            )
    );

    // ========================================================================
    // 注册方法：在主类构造函数中调用此方法，将注册器绑定到模组事件总线
    // ========================================================================
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    // ========================================================================
    // 【内部事件监听器】将物品添加到创造模式物品栏
    // ========================================================================
    // Forge 1.20.1 标准做法：
    //   - 必须用 @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
    //     因为 BuildCreativeModeTabContentsEvent 是 MOD 总线事件
    //   - 通过 event.getTabKey() 判断当前正在构建哪个创造标签页
    //   - 通过 event.accept() 添加物品到该标签页
    // ========================================================================
    @Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class CreativeTabHandler {

        @SubscribeEvent
        public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
            // 将终界之星添加到原版"工具与实用物品"标签页
            //   旧 TAB_MISC 在 1.19.3+ 重命名为 TOOLS_AND_UTILITIES
            //   ResourceKey<CreativeModeTab> = minecraft:tools_and_utilities
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                event.accept(END_STAR);
            }
        }
    }
}
