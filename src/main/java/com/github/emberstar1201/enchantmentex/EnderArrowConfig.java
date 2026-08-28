package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// 末影箭独立配置：config/enchantment_expansion-ender_arrow.toml
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class EnderArrowConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("末影箭：附魔总开关")
            .define("enderArrow.enabled", true);

    private static final ForgeConfigSpec.DoubleValue COOLDOWN_SECONDS = BUILDER
            .comment("末影箭：成功传送后的冷却时间（秒，默认 3）")
            .defineInRange("enderArrow.cooldownSeconds", 3.0, 0.0, 300.0);

    private static final ForgeConfigSpec.BooleanValue TELEPORT_TO_ENTITY = BUILDER
            .comment("末影箭：命中实体时是否传送到实体正前方一格；false 时传送到箭矢命中点")
            .define("enderArrow.teleportToEntity", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    private static boolean enabled = true;
    private static double cooldownSeconds = 3.0;
    private static boolean teleportToEntity = true;

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        // 仅处理本配置，避免其他配置事件导致读取时机错误。
        if (event.getConfig() == null || event.getConfig().getSpec() != SPEC) {
            return;
        }

        enabled = ENABLED.get();
        cooldownSeconds = COOLDOWN_SECONDS.get();
        teleportToEntity = TELEPORT_TO_ENTITY.get();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static long getCooldownTicks() {
        return Math.round(cooldownSeconds * 20.0);
    }

    public static boolean shouldTeleportToEntity() {
        return teleportToEntity;
    }
}
