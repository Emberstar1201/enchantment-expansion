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
                    .fireResistant()   // 防 火 + 防 岩浆（防止掉落物被岩浆/火焰销毁）
            )
    );

    // ========================================================================
    // 【人权剑】（Sword of the Free Will）
    //   击败凋灵后掉落获得，无法合成
    //
    // Item.Properties 配置：
    //   .stacksTo(1)            ：不可堆叠
    //   .rarity(Rarity.EPIC)    ：史诗稀有度，物品名显示为紫色
    //   .fireResistant()        ：不会被火焰/岩浆销毁
    //
    // 内置附魔（锋利X、亡灵杀手X、击退II、拂晓I）由 SwordOfTheFreeWill 的
    // inventoryTick() 方法自动补充到 NBT。
    // 实际功能由 SwordOfTheFreeWillHandler 的事件监听实现。
    // 注册ID：sword_of_the_free_will
    // ========================================================================
    public static final RegistryObject<SwordOfTheFreeWill> SWORD_OF_THE_FREE_WILL =
            ITEMS.register("sword_of_the_free_will", SwordOfTheFreeWill::new);

    // ========================================================================
    // 【海洋之星】（Ocean star）
    //   在海洋神殿结构内的宝箱中获得（每神殿仅一个，100%生成）
    //
    // Item.Properties 配置：
    //   .stacksTo(1)         ：不可堆叠（唯一宝物）
    //   .rarity(Rarity.EPIC) ：史诗稀有度，物品名显示为紫色
    //   .fireResistant()     ：不会被火焰/岩浆销毁
    //
    // 物品属性定义见 OceanStarItem（浮光 + 描述文字），
    // 被动效果（水下挖掘/水流免疫/守卫者中立等）由 OceanStarHandler 事件驱动。
    // 注册ID：ocean_star
    // ========================================================================
    public static final RegistryObject<Item> OCEAN_STAR = ITEMS.register("ocean_star",
            () -> new OceanStarItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)
                    .fireResistant()
            )
    );

    // ========================================================================
    // 【强化卷轴】（Enhancement Scroll）
    //   一次性消耗品，仅能从遗迹宝箱中以 10% 概率获得。
    //
    //   在铁砧中与附魔装备 / 附魔书一同放入时：
    //     1. 随机选一个已有附魔提升一级
    //     2. 若该附魔已满级（如拂晓 maxLevel=1），则额外添加一个随机附魔
    //
    // Item.Properties 配置：
    //   .stacksTo(16)           ：可堆叠至 16 个（一次性消耗品，铁砧每次消耗 1 个）
    //   .rarity(Rarity.RARE)    ：稀有度（金色名）
    //   .fireResistant()        ：不会被火焰/岩浆销毁
    //
    // 自定义 EnhancementScrollItem 重写 isFoil() → 附魔光效
    // 铁砧逻辑与战利品注入见 EnhancementScrollHandler
    // 注册ID：enhancement_scroll
    // ========================================================================
    public static final RegistryObject<EnhancementScrollItem> ENHANCEMENT_SCROLL =
            ITEMS.register("enhancement_scroll", () -> new EnhancementScrollItem(new Item.Properties()
                    .stacksTo(16)
                    .rarity(Rarity.RARE)
                    .fireResistant()
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
                event.accept(OCEAN_STAR);
                event.accept(ENHANCEMENT_SCROLL);
            }
        }
    }
}
