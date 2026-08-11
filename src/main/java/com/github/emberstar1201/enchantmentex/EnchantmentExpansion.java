package com.github.emberstar1201.enchantmentex;

import com.github.emberstar1201.enchantmentex.enchantment.ModEnchantments;
import com.github.emberstar1201.enchantmentex.item.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
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
        // 1. 附魔注册器（风踏涟漪、云来弓法、云来剑法、攫取、强夺、拂晓等）
        ModEnchantments.register(modEventBus);
        // 2. 物品注册器（终界之星等）
        ModItems.register(modEventBus);

        // ================================================================
        // 注册配置文件（终界之星 5 项配置：飞行、减伤、拾取延迟、消失时间）
        // 配置文件路径：config/enchantment_expansion-common.toml
        // ================================================================

        MinecraftForge.EVENT_BUS.register(this);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
