package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

// ========================================================================
// 「幽匿行者」附魔独立配置文件
//
// 配置路径：config/enchantment_expansion-dark_walker.toml
//
// 配置项：
//   1. darkWalker.enabled        ：附魔总开关（默认 true）
//   2. darkWalker.biomeWhitelist ：生效生物群系列表（默认仅深暗之域）
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DarkWalkerConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ========================================================================
    // 一、附魔总开关
    // ========================================================================
    private static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("幽匿行者：附魔总开关（默认 true）。设为 false 可整附魔失效。")
            .define("darkWalker.enabled", true);

    // ========================================================================
    // 二、是否限定生效生物群系（默认 false = 不限，全局生效）
    //    - false：只要靴子有幽匿行者附魔，任何维度/生物群系都生效
    //    - true ：仅在下述生物群系列表内生效
    // ========================================================================
    private static final ForgeConfigSpec.BooleanValue RESTRICT_TO_BIOME = BUILDER
            .comment("幽匿行者：是否限定生效生物群系（默认 false = 不限生物群系，全局生效）。",
                    "设为 true 时，仅在下述 biomeWhitelist 列表中的生物群系生效。")
            .define("darkWalker.restrictToBiome", false);

    // ========================================================================
    // 三、生效生物群系列表（仅当 restrictToBiome=true 时生效）
    //    默认：深暗之域 (minecraft:deep_dark)
    //    如需扩展到其他生物群系，追加原版/模组生物群系 ID 即可，
    //    例如："minecraft:ancient_city"（古城本身不是生物群系而是结构，
    //    这里仅示意语法），实际写法应为生物群系注册 ID。
    // ========================================================================
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BIOME_WHITELIST = BUILDER
            .comment(
                    "幽匿行者：生效的生物群系列表（仅当 restrictToBiome=true 时使用）。",
                    "填写生物群系注册 ID（如 minecraft:deep_dark）。",
                    "可配置多个，用逗号分隔：[\"minecraft:deep_dark\", \"minecraft:dripstone_caves\"]")
            .defineListAllowEmpty(
                    List.of("darkWalker.biomeWhitelist"),
                    () -> List.of("minecraft:deep_dark"),
                    obj -> obj instanceof String);

    // 配置 SPEC 实例（供 registerConfig 注册）
    static final ForgeConfigSpec SPEC = BUILDER.build();

    // ========================================================================
    // 运行时缓存字段（配置加载后才有效）
    // ========================================================================
    public static boolean enabled;
    public static boolean restrictToBiome;
    public static List<? extends String> biomeWhitelist;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // ★ 关键过滤：必须确认是本配置，否则其他配置加载时 get() 会抛异常
        if (event.getConfig() == null || event.getConfig().getSpec() != DarkWalkerConfig.SPEC) {
            return;
        }
        enabled = ENABLED.get();
        restrictToBiome = RESTRICT_TO_BIOME.get();
        biomeWhitelist = BIOME_WHITELIST.get();
    }

    // ========================================================================
    // 工具查询方法
    // ========================================================================

    /** 附魔是否整体启用 */
    public static boolean isEnabled() {
        return enabled;
    }

    /** 指定生物群系 ID 是否在生效列表内 */
    public static boolean isBiomeAllowed(String biomeId) {
        if (biomeId == null || biomeWhitelist == null) return false;
        return biomeWhitelist.contains(biomeId);
    }
}