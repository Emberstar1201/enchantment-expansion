package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// 附魔注册类：统一管理模组所有自定义附魔的注册
public class ModEnchantments {
    // 创建附魔的延迟注册器，所有附魔都将通过它注册到 "enchantment_expansion" 命名空间下
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, EnchantmentExpansion.MODID);

    // ========================================================================
    // 注册"古·云来弓法"附魔（原"速射"重命名）
    // 弓专属：蓄力加速 + 箭矢飞行加速，10级上限
    // 注册ID：ancient_yunlai
    // ========================================================================
    public static final RegistryObject<AncientYunLaiEnchantment> ANCIENT_YUNLAI =
            ENCHANTMENTS.register("ancient_yunlai", AncientYunLaiEnchantment::new);

    // ========================================================================
    // 注册"云来弓法（基础版）"附魔
    // 弓专属：仅蓄力加速，不改变箭矢速度，10级上限
    // 与古·云来弓法不冲突（两者可叠加）
    // 注册ID：yunlai_archery
    // ========================================================================
    public static final RegistryObject<YunLaiArcheryEnchantment> YUNLAI_ARCHERY =
            ENCHANTMENTS.register("yunlai_archery", YunLaiArcheryEnchantment::new);

    // ========================================================================
    // 注册"风踏涟漪"附魔 - 靴子专属：陆地加速+水面行走
    // 注册ID：wind_ripple
    // ========================================================================
    public static final RegistryObject<WindRippleEnchantment> WIND_RIPPLE =
            ENCHANTMENTS.register("wind_ripple", WindRippleEnchantment::new);

    // 注册ID：creation_from_nothing
    public static final RegistryObject<CreationFromNothingEnchantment> CREATION_FROM_NOTHING =
            ENCHANTMENTS.register("creation_from_nothing", CreationFromNothingEnchantment::new);

    // ========================================================================
    // 注册"云来剑法"附魔 - 剑专属：攻击距离+冷却缩减，5级上限
    // 注册ID：yunlai_swordmanship
    // ========================================================================
    public static final RegistryObject<YunLaiSwordmanshipEnchantment> YUNLAI_SWORDMANSHIP =
            ENCHANTMENTS.register("yunlai_swordmanship", YunLaiSwordmanshipEnchantment::new);

    // ========================================================================
    // 注册"古·云来剑法"附魔 - 剑专属：继承云来剑法+AOE之力，1级，仅宝箱获取
    // 注册ID：ancient_yunlai_swordmanship
    // ========================================================================
    public static final RegistryObject<AncientYunLaiSwordmanshipEnchantment> ANCIENT_YUNLAI_SWORDMANSHIP =
            ENCHANTMENTS.register("ancient_yunlai_swordmanship", AncientYunLaiSwordmanshipEnchantment::new);

    // ========================================================================
    // 注册"优雅猫步"附魔 - 靴子专属：移速1.45倍+免疫掉落伤害
    // 注册ID：elegant_catwalk
    // ========================================================================
    public static final RegistryObject<ElegantCatwalkEnchantment> ELEGANT_CATWALK =
            ENCHANTMENTS.register("elegant_catwalk", ElegantCatwalkEnchantment::new);

    // ========================================================================
    // 注册"攫取"附魔 - 剑专属：击杀时按概率抢夺敌方装备
    // 5级：I-III附魔台可获得，IV-V仅宝箱获取（minCost>30过滤）
    // 与抢夺（Looting）互斥
    // 注册ID：snatch
    // ========================================================================
    public static final RegistryObject<SnatchEnchantment> SNATCH =
            ENCHANTMENTS.register("snatch", SnatchEnchantment::new);

    // ========================================================================
    // 注册"强夺"附魔 - 剑专属：稀有战利品必掉+爬行者必掉唱片
    // 3级、VERY_RARE、仅宝箱获取（treasureOnly=true）
    // 与抢夺（Looting）互斥
    // 注册ID：plunder
    // ========================================================================
    public static final RegistryObject<PlunderEnchantment> PLUNDER =
            ENCHANTMENTS.register("plunder", PlunderEnchantment::new);

    // ========================================================================
    // 注册"拂晓"附魔 - 近战武器通用：随昼夜变化调整伤害与冷却
    // 1级、VERY_RARE、仅宝箱获取（treasureOnly=true）
    // 时段：白天无加成 / 傍晚+50% / 夜晚+100% / 午夜+300%伤害+无冷却
    // 注册ID：dawn
    // ========================================================================
    public static final RegistryObject<DawnEnchantment> DAWN =
            ENCHANTMENTS.register("dawn", DawnEnchantment::new);

    // 在主类构造函数中调用此方法，将注册器绑定到模组事件总线
    public static void register(IEventBus eventBus) {
        ENCHANTMENTS.register(eventBus);
    }
}
