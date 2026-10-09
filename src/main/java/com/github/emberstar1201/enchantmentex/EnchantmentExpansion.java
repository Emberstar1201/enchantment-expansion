package com.github.emberstar1201.enchantmentex;

import com.github.emberstar1201.enchantmentex.enchantment.AccumulateHandler;
import com.github.emberstar1201.enchantmentex.enchantment.AgricultureHandler;
import com.github.emberstar1201.enchantmentex.enchantment.ChainBreakerHandler;
import com.github.emberstar1201.enchantmentex.enchantment.DifficultyGiftHandler;
import com.github.emberstar1201.enchantmentex.enchantment.AncientYunLaiHandler;
import com.github.emberstar1201.enchantmentex.enchantment.ArtisanLegacyHandler;
import com.github.emberstar1201.enchantmentex.enchantment.AutoRepairHandler;
import com.github.emberstar1201.enchantmentex.enchantment.AutoRepairLootHandler;
import com.github.emberstar1201.enchantmentex.enchantment.AutoSmeltHandler;
import com.github.emberstar1201.enchantmentex.enchantment.BloodthirstHandler;
import com.github.emberstar1201.enchantmentex.enchantment.ChainArrowHandler;
import com.github.emberstar1201.enchantmentex.enchantment.MadeInChinaHandler;
import com.github.emberstar1201.enchantmentex.enchantment.DarkWalkerHandler;
import com.github.emberstar1201.enchantmentex.enchantment.DeepSeaRippleHandler;
import com.github.emberstar1201.enchantmentex.enchantment.ExperienceGiftHandler;
import com.github.emberstar1201.enchantmentex.enchantment.FeatherWingHandler;
import com.github.emberstar1201.enchantmentex.enchantment.FeatherWingLootHandler;
import com.github.emberstar1201.enchantmentex.enchantment.SniperHandler;
import com.github.emberstar1201.enchantmentex.enchantment.SandevistanHandler;
import com.github.emberstar1201.enchantmentex.enchantment.SandevistanLootHandler;
import com.github.emberstar1201.enchantmentex.enchantment.EternalSparkHandler;
import com.github.emberstar1201.enchantmentex.enchantment.ExplosiveArrowHandler;
import com.github.emberstar1201.enchantmentex.enchantment.EnderArrowHandler;
import com.github.emberstar1201.enchantmentex.enchantment.FallCushionHandler;
import com.github.emberstar1201.enchantmentex.enchantment.IllusoryFeastHandler;
import com.github.emberstar1201.enchantmentex.enchantment.IllusoryFeastLootHandler;
import com.github.emberstar1201.enchantmentex.enchantment.TemperatureConstantHandler;
import com.github.emberstar1201.enchantmentex.enchantment.TouhouMaidEnchantmentCompat;
import com.github.emberstar1201.enchantmentex.enchantment.TouhouMaidEnchantmentCompat2;
import com.github.emberstar1201.enchantmentex.enchantment.TouhouMaidEnchantmentCompat3;
import com.github.emberstar1201.enchantmentex.enchantment.TouhouMaidEnchantmentCompat4;
import com.github.emberstar1201.enchantmentex.enchantment.ChannelingEventHandler;
import com.github.emberstar1201.enchantmentex.enchantment.ChannelingLootHandler;
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
import com.github.emberstar1201.enchantmentex.enchantment.SprintEnduranceHandler;
import com.github.emberstar1201.enchantmentex.enchantment.SwiftCrossbowHandler;
import com.github.emberstar1201.enchantmentex.enchantment.WindRippleHandler;
import com.github.emberstar1201.enchantmentex.enchantment.YunLaiArcheryHandler;
import com.github.emberstar1201.enchantmentex.enchantment.YunLaiSwordmanshipHandler;
import com.github.emberstar1201.enchantmentex.entity.FriendlyGirlPeacefulSpawnHandler;
import com.github.emberstar1201.enchantmentex.entity.ModEntities;
import com.github.emberstar1201.enchantmentex.entity.ModMenuTypes;
import com.github.emberstar1201.enchantmentex.item.ModItems;
import com.github.emberstar1201.enchantmentex.item.handler.EnhancementScrollHandler;
import com.github.emberstar1201.enchantmentex.item.handler.EternalTotemHandler;
import com.github.emberstar1201.enchantmentex.item.handler.LifeStarHandler;
import com.github.emberstar1201.enchantmentex.item.handler.MaidStarHandler;
import com.github.emberstar1201.enchantmentex.item.handler.OceanStarHandler;
import com.github.emberstar1201.enchantmentex.item.handler.ProtectedItemHandler;
import com.github.emberstar1201.enchantmentex.item.handler.StarEmbedHandler;
import com.github.emberstar1201.enchantmentex.item.handler.StarlightStarHandler;
import com.github.emberstar1201.enchantmentex.item.handler.SwordOfTheFreeWillHandler;
import com.github.emberstar1201.enchantmentex.item.handler.ResidualScytheHandler;
import com.github.emberstar1201.enchantmentex.item.handler.LuohongyuHandler;
import com.github.emberstar1201.enchantmentex.item.handler.TerminalBookHandler;
import com.github.emberstar1201.enchantmentex.item.handler.VoidStarHandler;
import com.github.emberstar1201.enchantmentex.client.handler.EnchantmentBookLookupHandler;
import com.github.emberstar1201.enchantmentex.network.NetworkHandler;
import com.github.emberstar1201.enchantmentex.recipe.ModRecipes;
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
        // 3.2 音效注册器（丧尸娘 / 溺尸娘共用的闲置、受伤、死亡语音）
        com.github.emberstar1201.enchantmentex.sound.ModSounds.register(modEventBus);
        // 3.1 容器菜单类型注册器（丧尸娘背包界面）
        ModMenuTypes.register(modEventBus);
        // 4. 配方序列化器注册器（生命之星：9 种不同的花）
        ModRecipes.register(modEventBus);

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
        // 农业生产系附魔独立配置（显式指定文件名，避免与主配置默认命名冲突）
        context.registerConfig(ModConfig.Type.COMMON, AgricultureConfig.SPEC,
                "enchantment_expansion-agriculture.toml");
        // 幽匿行者附魔独立配置（显式指定文件名，避免与主配置默认命名冲突）
        context.registerConfig(ModConfig.Type.COMMON, DarkWalkerConfig.SPEC,
                "enchantment_expansion-dark_walker.toml");
        // 爆破箭矢附魔独立配置（显式指定文件名，避免与主配置默认命名冲突）
        context.registerConfig(ModConfig.Type.COMMON, ExplosiveArrowConfig.SPEC,
                "enchantment_expansion-explosive_arrow.toml");
        // 贯穿链条附魔独立配置（显式指定文件名，避免与主配置默认命名冲突）
        context.registerConfig(ModConfig.Type.COMMON, ChainArrowConfig.SPEC,
                "enchantment_expansion-chain_arrow.toml");
        // 温度恒定附魔独立配置（显式指定文件名，避免与主配置默认命名冲突）
        context.registerConfig(ModConfig.Type.COMMON, TemperatureConstantConfig.SPEC,
                "enchantment_expansion-temperature_constant.toml");
        // 坠落缓冲附魔独立配置（显式指定文件名，避免与主配置默认命名冲突）
        context.registerConfig(ModConfig.Type.COMMON, FallCushionConfig.SPEC,
                "enchantment_expansion-fall_cushion.toml");
        // 连锁挖掘附魔独立配置（显式指定文件名，避免与主配置默认命名冲突）
        context.registerConfig(ModConfig.Type.COMMON, ChainBreakerConfig.SPEC,
                "enchantment_expansion-chain_breaker.toml");
        // 难度馈赠附魔独立配置（显式指定文件名，避免与主配置默认命名冲突）
        context.registerConfig(ModConfig.Type.COMMON, DifficultyGiftConfig.SPEC,
                "enchantment_expansion-difficulty_gift.toml");
        // 蓄积附魔独立配置（显式指定文件名，避免与主配置默认命名冲突）
        context.registerConfig(ModConfig.Type.COMMON, AccumulateConfig.SPEC,
                "enchantment_expansion-accumulate.toml");
        // 画饼充饥附魔独立配置（显式指定文件名，避免与主配置默认命名冲突）
        context.registerConfig(ModConfig.Type.COMMON, IllusoryFeastConfig.SPEC,
                "enchantment_expansion-illusory_feast.toml");
        // 自动修复附魔独立配置（显式指定文件名，避免与主配置默认命名冲突）
        context.registerConfig(ModConfig.Type.COMMON, AutoRepairConfig.SPEC,
                "enchantment_expansion-auto_repair.toml");
        // 深海的涟漪附魔独立配置（显式指定文件名，避免与主配置默认命名冲突）
        context.registerConfig(ModConfig.Type.COMMON, DeepSeaRippleConfig.SPEC,
                "enchantment_expansion-deep_sea_ripple.toml");
        // 疾跑节能附魔独立配置（显式指定文件名，避免与主配置默认命名冲突）
        context.registerConfig(ModConfig.Type.COMMON, SprintEnduranceConfig.SPEC,
                "enchantment_expansion-sprint_endurance.toml");
        // 斯安维斯坦附魔独立配置（时缓半径/作用对象开关/三级数值）
        context.registerConfig(ModConfig.Type.COMMON, SandevistanConfig.SPEC,
                "enchantment_expansion-sandevistan.toml");
        // 原版怪物强化独立配置（僵尸/骷髅/蜘蛛/苦力怕/末影人的数值调整，可整体关闭）
        context.registerConfig(ModConfig.Type.COMMON, MobBuffConfig.SPEC,
                "enchantment_expansion-mob_buff.toml");

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
        MinecraftForge.EVENT_BUS.register(ResidualScytheHandler.class);
        MinecraftForge.EVENT_BUS.register(LuohongyuHandler.class);
        MinecraftForge.EVENT_BUS.register(TerminalBookHandler.class);
        MinecraftForge.EVENT_BUS.register(ProtectedItemHandler.class);
        MinecraftForge.EVENT_BUS.register(StarEmbedHandler.class);
        MinecraftForge.EVENT_BUS.register(LifeStarHandler.class);
        // 星星×车万女仆：女仆手持/佩戴星星时的全部效果（未装车万女仆时全部空转）
        MinecraftForge.EVENT_BUS.register(MaidStarHandler.class);
        // 虚空之星：免疫摔落/虚空伤害 + 坠入虚空传送回出生点
        MinecraftForge.EVENT_BUS.register(VoidStarHandler.class);
        // 终界之星：飞行、减伤、虚空拦截与 3 秒虚空救援
        MinecraftForge.EVENT_BUS.register(com.github.emberstar1201.enchantmentex.item.handler.EndStarHandler.class);
        // 星辉之星：夜间移速加成 + 夜视 + 经验掉落翻倍
        MinecraftForge.EVENT_BUS.register(StarlightStarHandler.class);
        // 永恒图腾：致命伤抵挡 + 回满血 + 消耗耐久
        MinecraftForge.EVENT_BUS.register(EternalTotemHandler.class);
        MinecraftForge.EVENT_BUS.register(EnhancementScrollHandler.class);
        MinecraftForge.EVENT_BUS.register(SwiftCrossbowHandler.class);
        MinecraftForge.EVENT_BUS.register(AgricultureHandler.class);
        MinecraftForge.EVENT_BUS.register(SmokelessDashHandler.class);
        MinecraftForge.EVENT_BUS.register(DarkWalkerHandler.class);
        MinecraftForge.EVENT_BUS.register(ExplosiveArrowHandler.class);
        MinecraftForge.EVENT_BUS.register(EnderArrowHandler.class);
        MinecraftForge.EVENT_BUS.register(ChainArrowHandler.class);
        MinecraftForge.EVENT_BUS.register(TemperatureConstantHandler.class);
        MinecraftForge.EVENT_BUS.register(FallCushionHandler.class);
        MinecraftForge.EVENT_BUS.register(ChainBreakerHandler.class);
        MinecraftForge.EVENT_BUS.register(DifficultyGiftHandler.class);
        MinecraftForge.EVENT_BUS.register(AccumulateHandler.class);
        MinecraftForge.EVENT_BUS.register(IllusoryFeastHandler.class);
        MinecraftForge.EVENT_BUS.register(IllusoryFeastLootHandler.class);
        MinecraftForge.EVENT_BUS.register(AutoRepairHandler.class);
        MinecraftForge.EVENT_BUS.register(AutoRepairLootHandler.class);
        MinecraftForge.EVENT_BUS.register(DeepSeaRippleHandler.class);
        MinecraftForge.EVENT_BUS.register(SprintEnduranceHandler.class);
        MinecraftForge.EVENT_BUS.register(ExperienceGiftHandler.class);
        MinecraftForge.EVENT_BUS.register(FeatherWingHandler.class);
        MinecraftForge.EVENT_BUS.register(FeatherWingLootHandler.class);
        // 斯安维斯坦：时缓核心逻辑 + 遗迹宝箱附魔书注入
        MinecraftForge.EVENT_BUS.register(SandevistanHandler.class);
        MinecraftForge.EVENT_BUS.register(SandevistanLootHandler.class);
        // 帕秋莉手册软前置提醒：未装 Patchouli 的玩家进世界时提示搭配手册体验更佳
        MinecraftForge.EVENT_BUS.register(PatchouliReminderHandler.class);
        MinecraftForge.EVENT_BUS.register(SniperHandler.class);
        MinecraftForge.EVENT_BUS.register(MadeInChinaHandler.class);
        MinecraftForge.EVENT_BUS.register(TouhouMaidEnchantmentCompat.class);
        MinecraftForge.EVENT_BUS.register(TouhouMaidEnchantmentCompat2.class);
        MinecraftForge.EVENT_BUS.register(TouhouMaidEnchantmentCompat3.class);
        MinecraftForge.EVENT_BUS.register(TouhouMaidEnchantmentCompat4.class);
        // 原版怪物强化：血量 / 装备 / 额外掉落 / 小僵尸碰撞箱 / 蜘蛛结网
        MinecraftForge.EVENT_BUS.register(MobBuffHandler.class);
        // 友好丧尸娘：和平难度下保留 MONSTER 分类，同时执行受限的专属自然补刷
        MinecraftForge.EVENT_BUS.register(FriendlyGirlPeacefulSpawnHandler.class);
        // 凋零强化：属性 / 半血减伤 / 母弹分裂 / 死亡爆炸与经验
        MinecraftForge.EVENT_BUS.register(WitherBuffHandler.class);
        // 末影龙强化：血量 / 护甲 / 主动攻击 / 吼叫 / 半血机制
        MinecraftForge.EVENT_BUS.register(EnderDragonBuffHandler.class);
        // 原版怪物强化：游戏内配置命令 /ee mobbuff ...（仅 OP / 服主可用）
        MinecraftForge.EVENT_BUS.register(MobBuffCommandHandler.class);
        MinecraftForge.EVENT_BUS.register(MobBuffPromptHandler.class);
        // 附魔书快捷查找（EnchantmentBookLookupHandler）：
        //   不再在这里显式注册。它是纯客户端处理器（引用 RenderTooltipEvent / GuiGraphics），
        //   已加 @Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Bus.FORGE)，
        //   由 FML 只在客户端分发时自动注册到 FORGE 总线；若在此无条件注册，
        //   DEDICATED_SERVER 启动会加载该类并触发 "invalid dist" 崩溃。

        // ================================================================
        // 注册网络通道（飞轮效应等 C2S 数据包）
        // ================================================================
        NetworkHandler.register();

        // ================================================================
        // 客户端注册：实体渲染器
        // ================================================================
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::onRegisterEntityRenderers);
            // 丧尸娘玩家皮肤 UV 模型层（主体 + 盔甲内外层）
            modEventBus.addListener(this::onRegisterLayerDefinitions);
        }
        // 实体默认属性注册（含丧尸娘：将僵尸增援概率清零，被攻击时不会召唤僵尸）
        modEventBus.addListener(this::onEntityAttributeCreation);
        // 实体自然生成位置规则注册（丧尸娘：按怪物规则在地表黑暗处生成）
        modEventBus.addListener(this::onSpawnPlacementRegister);
    }

    // ========================================================================
    // 注册实体渲染器（客户端专用）
    // ========================================================================
    private void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GLACIAL_ARROW.get(),
                com.github.emberstar1201.enchantmentex.entity.client.GlacialArrowRenderer::new);
        event.registerEntityRenderer(ModEntities.CRESCENT.get(),
                com.github.emberstar1201.enchantmentex.entity.client.CrescentRenderer::new);
        event.registerEntityRenderer(ModEntities.ANNIHILATION_ORB.get(),
                com.github.emberstar1201.enchantmentex.entity.client.AnnihilationOrbRenderer::new);
        // 自定义闪电实体：沿用原版闪电渲染器（保证视觉效果保持一致）
        event.registerEntityRenderer(ModEntities.CUSTOM_LIGHTNING.get(),
                net.minecraft.client.renderer.entity.LightningBoltRenderer::new);
        // 丧尸娘：玩家皮肤 UV 模型（HumanoidMobRenderer），不再用僵尸模型
        event.registerEntityRenderer(ModEntities.ZOMBIE_GIRL.get(),
                com.github.emberstar1201.enchantmentex.entity.client.ZombieGirlRenderer::new);
        event.registerEntityRenderer(ModEntities.DROWNED_GIRL.get(),
                com.github.emberstar1201.enchantmentex.entity.client.DrownedGirlRenderer::new);
        // 幸存者少女：复用丧尸娘 Alex 细手模型，贴图换成 human_girl 系列人类皮肤
        event.registerEntityRenderer(ModEntities.SURVIVOR_GIRL.get(),
                com.github.emberstar1201.enchantmentex.entity.client.HumanGirlRenderer::new);
    }

    // ========================================================================
    // 注册实体模型层定义（客户端专用）
    // ========================================================================
    private void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        // 主体：Alex 细手玩家皮肤 UV（64x64）。
        // 不注册任何盔甲模型层——丧尸娘穿戴盔甲不显示外观，只保留装备属性。
        event.registerLayerDefinition(
                com.github.emberstar1201.enchantmentex.entity.client.ZombieGirlLayers.MAIN,
                com.github.emberstar1201.enchantmentex.entity.client.ZombieGirlModel::createBodyLayer);
    }

    // ========================================================================
    // 注册实体默认属性
    // ========================================================================
    private void onEntityAttributeCreation(
            net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        // 基础属性沿用僵尸；额外把「召唤增援」属性压到 0
        event.put(ModEntities.ZOMBIE_GIRL.get(),
                net.minecraft.world.entity.monster.Zombie.createAttributes()
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0D)
                        .build());
        event.put(ModEntities.DROWNED_GIRL.get(),
                net.minecraft.world.entity.monster.Drowned.createAttributes().build());
        // 幸存者少女：属性沿用僵尸（与丧尸娘一致，增援概率清零），移速加成由实体每 tick 自行结算
        event.put(ModEntities.SURVIVOR_GIRL.get(),
                net.minecraft.world.entity.monster.Zombie.createAttributes()
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0D)
                        .build());
    }

    // ========================================================================
    // 注册实体自然生成位置规则
    // ========================================================================
    private void onSpawnPlacementRegister(
            net.minecraftforge.event.entity.SpawnPlacementRegisterEvent event) {
        // 普通丧尸娘：刷怪权重与原版僵尸一致（95），非和平难度走原版夜晚/暗处生成；
        // 白天露天按 80% 概率门放行（有效权重略低于夜晚）；和平难度由专属补刷器处理。
        event.register(ModEntities.ZOMBIE_GIRL.get(),
                net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                FriendlyGirlPeacefulSpawnHandler::checkZombieGirlSpawnRules,
                net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.Operation.REPLACE);

        // 溺尸娘：水深、亮度、河流 1/15、普通水域 1/40 均复刻原版溺尸，
        // 仅去掉和平难度阻断，并由和平补刷器在 MONSTER 刷怪循环关闭时补刷。
        event.register(ModEntities.DROWNED_GIRL.get(),
                net.minecraft.world.entity.SpawnPlacements.Type.IN_WATER,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                FriendlyGirlPeacefulSpawnHandler::checkDrownedGirlSpawnRules,
                net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}
