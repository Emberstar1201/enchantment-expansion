package com.github.emberstar1201.enchantmentex;

import com.github.emberstar1201.enchantmentex.enchantment.ModEnchantments;
import com.github.emberstar1201.enchantmentex.entity.ModEntities;
import com.github.emberstar1201.enchantmentex.item.ModItems;
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

        MinecraftForge.EVENT_BUS.register(this);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

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
    }
}
