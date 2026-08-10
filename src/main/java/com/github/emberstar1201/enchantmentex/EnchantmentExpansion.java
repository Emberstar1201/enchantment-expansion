package com.github.emberstar1201.enchantmentex;

import com.github.emberstar1201.enchantmentex.enchantment.ModEnchantments;
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
        ModEnchantments.register(modEventBus);
        MinecraftForge.EVENT_BUS.register(this);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
