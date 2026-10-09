package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 丧尸娘、溺尸娘和幸存者少女共用的工作 AI 配置。
 *
 * <p>范围采用车万女仆常见的「以实体为中心扫描方块」方式：水平半径和
 * 垂直半径分别限制一次找活的最大区域。数值越大，能发现的工作点越远，
 * 但空旷区域的扫描成本也会增加。</p>
 */
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GirlWorkConfig {
    private GirlWorkConfig() {
    }

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue MAX_WORK_RANGE = BUILDER
            .comment("友好娘工作判断的最大水平范围（格）",
                    "用于挖矿、耕地等工作目标扫描；默认 16，范围 4~64。")
            .defineInRange("girlWork.maxSearchRange", 16, 4, 64);

    private static final ForgeConfigSpec.IntValue MAX_WORK_VERTICAL_RANGE = BUILDER
            .comment("友好娘工作判断的最大垂直范围（格）",
                    "用于挖矿、耕地等工作目标扫描；默认 4，范围 1~16。")
            .defineInRange("girlWork.maxVerticalRange", 4, 1, 16);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /** 当前生效的水平工作范围。 */
    public static int maxSearchRange = 16;
    /** 当前生效的垂直工作范围。 */
    public static int maxVerticalRange = 4;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        maxSearchRange = MAX_WORK_RANGE.get();
        maxVerticalRange = MAX_WORK_VERTICAL_RANGE.get();
    }

    public static int horizontalRange() {
        return Math.max(4, maxSearchRange);
    }

    public static int verticalRange() {
        return Math.max(1, maxVerticalRange);
    }
}
