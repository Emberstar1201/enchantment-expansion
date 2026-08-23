package com.github.emberstar1201.enchantmentex;

import com.github.emberstar1201.enchantmentex.enchantment.AncientYunLaiHandler;
import com.github.emberstar1201.enchantmentex.enchantment.ArtisanLegacyHandler;
import com.github.emberstar1201.enchantmentex.enchantment.AutoSmeltHandler;
import com.github.emberstar1201.enchantmentex.enchantment.BloodthirstHandler;
import com.github.emberstar1201.enchantmentex.enchantment.EternalSparkHandler;
import com.github.emberstar1201.enchantmentex.enchantment.ChannelingEventHandler;
import com.github.emberstar1201.enchantmentex.enchantment.ChannelingLootHandler;
import com.github.emberstar1201.enchantmentex.enchantment.CreationFromNothingHandler;
import com.github.emberstar1201.enchantmentex.enchantment.DawnHandler;
import com.github.emberstar1201.enchantmentex.enchantment.ElegantCatwalkHandler;
import com.github.emberstar1201.enchantmentex.enchantment.EndApproachesHandler;
import com.github.emberstar1201.enchantmentex.enchantment.ExNihiloHandler;
import com.github.emberstar1201.enchantmentex.enchantment.FlywheelEffectHandler;
import com.github.emberstar1201.enchantmentex.enchantment.GlacialArrowHandler;
import com.github.emberstar1201.enchantmentex.enchantment.LevisEchoHandler;
import com.github.emberstar1201.enchantmentex.enchantment.ModEnchantments;
import com.github.emberstar1201.enchantmentex.enchantment.PlunderHandler;
import com.github.emberstar1201.enchantmentex.enchantment.QianpoQingMingSwordHandler;
import com.github.emberstar1201.enchantmentex.enchantment.SnatchHandler;
import com.github.emberstar1201.enchantmentex.enchantment.SmokelessDashHandler;
import com.github.emberstar1201.enchantmentex.enchantment.SwiftCrossbowHandler;
import com.github.emberstar1201.enchantmentex.enchantment.WindRippleHandler;
import com.github.emberstar1201.enchantmentex.enchantment.YunLaiArcheryHandler;
import com.github.emberstar1201.enchantmentex.enchantment.YunLaiSwordmanshipHandler;
import com.github.emberstar1201.enchantmentex.entity.ModEntities;
import com.github.emberstar1201.enchantmentex.item.ModItems;
import com.github.emberstar1201.enchantmentex.item.handler.OceanStarHandler;
import com.github.emberstar1201.enchantmentex.item.handler.SwordOfTheFreeWillHandler;
import com.github.emberstar1201.enchantmentex.network.NetworkHandler;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(EnchantmentExpansion.MODID)
public class EnchantmentExpansion {
    public static final String MODID = "enchantment_expansion";
    private static final Logger LOGGER = LogUtils.getLogger();

    public EnchantmentExpansion(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        // ================================================================
        // 注册延迟注册器
        // ================================================================
        // 1. 附魔注册器（风踏涟漪、云来弓法、云来剑法、攫取、强夺、拂晓、琉璃冰魄箭等）
        ModEnchantments.register(modEventBus);
        // 2. 物品注册器（终界之星等）
        ModItems.register(modEventBus);
        // 3. 实体注册器（琉璃冰魄箭实体等）
        ModEntities.register(modEventBus);

        // ================================================================
        // 注册配置文件（琉璃冰魄箭 17 项配置、终界之星 5 项配置等）
        // 配置文件路径：config/enchantment_expansion-common.toml
        // ================================================================

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        // 海洋之星独立配置（显式指定文件名，避免与主配置默认命名冲突）
        context.registerConfig(ModConfig.Type.COMMON, OceanStarConfig.SPEC,
                "enchantment_expansion-ocean_star.toml");
        // 强夺附魔独立配置（显式指定文件名，避免与主配置默认命名冲突）
        context.registerConfig(ModConfig.Type.COMMON, PlunderConfig.SPEC,
                "enchantment_expansion-plunder.toml");
        // 迅捷之弩附魔独立配置（显式指定文件名，避免与主配置默认命名冲突）
        context.registerConfig(ModConfig.Type.COMMON, SwiftCrossbowConfig.SPEC,
                "enchantment_expansion-swift_crossbow.toml");

        // ================================================================
        // ★★★★★ 显式注册所有事件处理器到 Forge 事件总线 ★★★★★
        //
        // 原因：部分 Handler 使用了 @Mod.EventBusSubscriber 但未指定
        // bus = Bus.FORGE（默认走 Bus.MOD），导致 Forge 事件（如
        // PlayerTickEvent、LivingHurtEvent、BlockEvent.BreakEvent 等）
        // 无法被正确接收。
        //
        // 显式注册作为双重保障，确保所有 Handler 必定生效，不受注解
        // 影响。如需添加新的 Handler，请在这里一并注册。
        // ================================================================
        MinecraftForge.EVENT_BUS.register(AncientYunLaiHandler.class);
        MinecraftForge.EVENT_BUS.register(ArtisanLegacyHandler.class);
        MinecraftForge.EVENT_BUS.register(AutoSmeltHandler.class);
        MinecraftForge.EVENT_BUS.register(BloodthirstHandler.class);
        MinecraftForge.EVENT_BUS.register(ChannelingEventHandler.class);
        MinecraftForge.EVENT_BUS.register(ChannelingLootHandler.class);
        MinecraftForge.EVENT_BUS.register(CreationFromNothingHandler.class);
        MinecraftForge.EVENT_BUS.register(DawnHandler.class);
        MinecraftForge.EVENT_BUS.register(ElegantCatwalkHandler.class);
        MinecraftForge.EVENT_BUS.register(EndApproachesHandler.class);
        MinecraftForge.EVENT_BUS.register(ExNihiloHandler.class);
        MinecraftForge.EVENT_BUS.register(FlywheelEffectHandler.class);
        MinecraftForge.EVENT_BUS.register(GlacialArrowHandler.class);
        MinecraftForge.EVENT_BUS.register(LevisEchoHandler.class);
        MinecraftForge.EVENT_BUS.register(EternalSparkHandler.class);
        MinecraftForge.EVENT_BUS.register(PlunderHandler.class);
        MinecraftForge.EVENT_BUS.register(QianpoQingMingSwordHandler.class);
        MinecraftForge.EVENT_BUS.register(SnatchHandler.class);
        MinecraftForge.EVENT_BUS.register(WindRippleHandler.class);
        MinecraftForge.EVENT_BUS.register(YunLaiArcheryHandler.class);
        MinecraftForge.EVENT_BUS.register(YunLaiSwordmanshipHandler.class);
        MinecraftForge.EVENT_BUS.register(OceanStarHandler.class);
        MinecraftForge.EVENT_BUS.register(SwordOfTheFreeWillHandler.class);
        MinecraftForge.EVENT_BUS.register(SwiftCrossbowHandler.class);
        MinecraftForge.EVENT_BUS.register(SmokelessDashHandler.class);

        // ================================================================
        // 注册网络通道（飞轮效应等 C2S 数据包）
        // ================================================================
        NetworkHandler.register();

        // ================================================================
        // 客户端注册：实体渲染器
        // ================================================================
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::onRegisterEntityRenderers);
        }
    }

    // ========================================================================
    // 注册实体渲染器（客户端专用）
    // ========================================================================
    private void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GLACIAL_ARROW.get(),
                com.github.emberstar1201.enchantmentex.entity.client.GlacialArrowRenderer::new);
        event.registerEntityRenderer(ModEntities.CRESCENT.get(),
                com.github.emberstar1201.enchantmentex.entity.client.CrescentRenderer::new);
    }
}
