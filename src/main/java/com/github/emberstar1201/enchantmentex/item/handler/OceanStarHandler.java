package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.OceanStarConfig;
import com.github.emberstar1201.enchantmentex.data.OceanStarData;
import com.github.emberstar1201.enchantmentex.enchantment.ModEnchantments;
import com.github.emberstar1201.enchantmentex.item.ModItems;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 「海洋之星」物品效果事件处理器
//
// 【被动效果】（仅玩家手持主手/副手海洋之星时生效，全部支持独立配置开关）
//   1. 免疫水下挖掘惩罚 + 免疫挖掘疲劳（PlayerTickEvent 移除挖掘疲劳）
//   2. 免疫水流减速（PlayerTickEvent：泳速提升 + 抵消水流推力 + 移除减速效果/修饰符）
//   3. 水下挖掘速度加成（PlayerEvent.BreakSpeed：恢复被惩罚削弱的速度并乘 1.25）
//   4. 水下不扣氧气 + 免疫溺水窒息伤害（PlayerTickEvent 保持满氧 + LivingHurtEvent 取消 DROWN 伤害）
//   5. 守卫者/远古守卫者中立化（LivingChangeTargetEvent：不主动锁定持有者，但被攻击后正常反击）
//   6. 海洋神殿强制生成宝箱（ChunkEvent.Load：神殿所在区块生成/加载时，在其顶部中央放置宝箱）
//   7. 海洋神殿宝箱 100% 注入海洋之星（PlayerInteractEvent.RightClickBlock：首次打开神殿结构内的容器时放入）
//
// 【核心机制说明】
//   原版水下挖掘惩罚：玩家眼睛浸水且无『水下速掘』附魔时挖掘速度 ÷5；
//   不在地面时再 ÷5。BreakSpeed 事件拿到的原速度已包含这些惩罚，
//   因此需要『反向乘以 5』恢复，再按配置放大 1.25 倍。
//   守卫者反击判定：若守卫者最近一次打伤它的实体就是持有者本人，则放行，
//   保证『被玩家攻击后会正常反击』。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class OceanStarHandler {

    // 水中泳速修饰符固定 UUID（瞬态修饰符，随环境增删）
    private static final UUID SWIM_SPEED_UUID =
            UUID.fromString("a1c3e5f7-9b0d-4e2f-8b1a-5c7d9f0e2b4a");
    private static final String SWIM_SPEED_NAME = "OceanStar Swim Speed Boost";

    // ========================================================================
    // 【PlayerTickEvent】手持检测 + 环境免疫（挖掘疲劳 / 水流减速 / 泳速提升）
    //   仅服务端处理（属性与状态效果以服务端为准，移除后会同步到客户端）。
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide()) return;

        // 仅当玩家手持海洋之星时才处理（主手或副手）
        if (!isHoldingOceanStar(player)) return;

        // ---------- 效果 1b：水下不扣氧气（保持氧气值满，仅水中生效） ----------
        // 原版每 tick 会按潜水时间扣除 airSupply，耗尽后开始受 DROWN 伤害。
        // 这里每 tick 重置为满氧，从根源上杜绝氧气耗尽。同时配合
        // onLivingHurt 中的 DROWN 伤害免疫作为双保险。
        if (OceanStarConfig.enableOxygenImmunity && player.isInWater()) {
            player.setAirSupply(player.getMaxAirSupply());
        }

        // ---------- 效果 1a：免疫挖掘疲劳（远古守卫者的『挖掘疲劳』） ----------
        if (OceanStarConfig.enableMiningFatigueImmunity
                && player.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            player.removeEffect(MobEffects.DIG_SLOWDOWN);
        }

        // ---------- 效果 2：免疫水流减速 ----------
        boolean inWater = player.isInWater();
        if (inWater && OceanStarConfig.enableWaterMovementImmunity) {
            // 2a. 移除『缓慢』状态效果
            if (player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }

            // 2b. 移除 MOVEMENT_SPEED 属性的所有负值修饰符（水中拖拽/灵魂沙等减速源）
            AttributeInstance moveSpeedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (moveSpeedAttr != null) {
                List<UUID> negativeMods = new ArrayList<>();
                for (AttributeModifier mod : moveSpeedAttr.getModifiers()) {
                    if (mod.getAmount() < 0) {
                        negativeMods.add(mod.getId());
                    }
                }
                for (UUID modId : negativeMods) {
                    moveSpeedAttr.removeModifier(modId);
                }
            }

            // 2c. 提升游泳速度（补偿水中拖拽，使水中移动接近陆地速度）
            AttributeInstance swimAttr = player.getAttribute(ForgeMod.SWIM_SPEED.get());
            if (swimAttr != null) {
                double multiplier = OceanStarConfig.swimSpeedMultiplier;
                AttributeModifier existing = swimAttr.getModifier(SWIM_SPEED_UUID);
                if (existing == null) {
                    swimAttr.addTransientModifier(new AttributeModifier(
                            SWIM_SPEED_UUID, SWIM_SPEED_NAME, multiplier,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));
                } else if (existing.getAmount() != multiplier) {
                    swimAttr.removeModifier(SWIM_SPEED_UUID);
                    swimAttr.addTransientModifier(new AttributeModifier(
                            SWIM_SPEED_UUID, SWIM_SPEED_NAME, multiplier,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));
                }
            }

            // 2d. 抵消水流推力：原版会在服务端给水中实体叠加 flow*0.014 的速度推力，
            //     这里每 tick 反向扣除，实现『不受水流方向和强度影响』。
            //     近似取玩家脚下方块的水流向量，对玩家而言精度足够。
            BlockPos pos = player.blockPosition();
            FluidState fluid = player.level().getFluidState(pos);
            Vec3 flow = fluid.getFlow(player.level(), pos);
            if (flow.lengthSqr() > 0.0D) {
                player.setDeltaMovement(player.getDeltaMovement().subtract(flow.scale(0.014D)));
            }
        } else {
            // 未在水中（或配置关闭）：确保瞬态泳速修饰符被移除
            AttributeInstance swimAttr = player.getAttribute(ForgeMod.SWIM_SPEED.get());
            if (swimAttr != null && swimAttr.getModifier(SWIM_SPEED_UUID) != null) {
                swimAttr.removeModifier(SWIM_SPEED_UUID);
            }
        }
    }

    // ========================================================================
    // 【BlockEvent.BreakSpeed】免疫水下挖掘惩罚 + 水下挖掘速度 1.25 倍
    //
    //   原版流程（Player.getDigSpeed）：
    //     speed /= 5  （眼睛浸水且无水下速掘附魔时）
    //     speed /= 5  （不在地面时）
    //   → 反向 ×5 先恢复，再按配置放大水下倍率（默认 1.25）。
    // ========================================================================
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player == null || !isHoldingOceanStar(player)) return;

        boolean inWater = player.isEyeInFluid(FluidTags.WATER);
        boolean onGround = player.onGround();

        float speed = event.getOriginalSpeed();

        // 效果 1b：恢复水下挖掘惩罚（原版 ÷5 → ×5 抵消）
        if (OceanStarConfig.enableMiningPenaltyImmunity && inWater
                && !EnchantmentHelper.hasAquaAffinity(player)) {
            speed *= 5.0F;
        }
        // 恢复『不在地面』惩罚（原版 ÷5 → ×5 抵消）
        if (OceanStarConfig.enableMiningPenaltyImmunity && !onGround) {
            speed *= 5.0F;
        }

        // 效果 3：水下挖掘速度加成（正常陆地挖掘速度的 1.25 倍）
        if (inWater && OceanStarConfig.enableUnderwaterMiningBoost) {
            speed *= (float) OceanStarConfig.underwaterMiningBoostMultiplier;
        }

        event.setNewSpeed(speed);
    }

    // ========================================================================
    // 【LivingHurtEvent】免疫溺水窒息伤害
    //   持有海洋之星时，DROWN（溺水/窒息）伤害完全无效。
    //   与 onPlayerTick 中的"水下不扣氧气"协同，构成双保险。
    // ========================================================================
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!OceanStarConfig.enableOxygenImmunity) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!isHoldingOceanStar(player)) return;

        // DROWN = 溺水伤害（氧气耗尽后每 tick 受到的窒息伤害）
        if (event.getSource().is(DamageTypes.DROWN)) {
            event.setCanceled(true);
        }
    }

    // ========================================================================
    // 【LivingChangeTargetEvent】守卫者/远古守卫者中立化
    //   不主动攻击海洋之星持有者；但若玩家进攻了守卫者（lastHurtByMob == 玩家），
    //   守卫者可以正常回击（反击不受中立影响）。
    // ========================================================================
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!OceanStarConfig.enableGuardianNeutral) return;

        // 仅处理守卫者（远古守卫者是守卫者的子类，自动包含）
        if (!(event.getEntity() instanceof Guardian guardian)) return;

        // 新目标必须是海洋之星持有者
        if (!(event.getNewTarget() instanceof Player player)) return;
        if (!isHoldingOceanStar(player)) return;

        // 反击判定：守卫者最近被打它的实体如果正是持有者本人，允许反击
        if (guardian.getLastHurtByMob() == player) return;

        // 中立化：清除对该玩家的索敌
        event.setNewTarget(null);
        event.setCanceled(true);
    }

    // ========================================================================
    // 【ChunkEvent.Load】海洋神殿强制生成宝箱
    //
    //   原版海洋神殿内没有宝箱。此事件在海洋神殿所在区块被生成/加载时触发：
    //   1. 通过区块的结构引用找到「海洋神殿」结构实例
    //   2. 在其结构包围盒顶部中央放置一个宝箱
    //   3. 以「结构起始区块」为唯一标识写入持久化记录，保证每个神殿实例仅一个宝箱，
    //      不随时间、区块加载/刷新重复生成
    //
    //   延迟到服务器主线程执行，避免区块加载过程中的层级交互造成死锁。
    //   已存在的世界（海洋神殿早已生成）在玩家首次接近加载该区块时也会补上宝箱。
    // ========================================================================
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        // 仅服务端：客户端加载区块不处理
        if (event.getLevel().isClientSide()) return;
        if (!OceanStarConfig.enableMonumentChestGenerate) return;

        // 世界生成期间 event 的 level 是 WorldGenRegion；正常加载时是 ServerLevel
        LevelAccessor levelAccessor = event.getLevel();
        ServerLevel serverLevel = levelAccessor instanceof ServerLevel sl ? sl
                : (levelAccessor instanceof WorldGenRegion wgr ? wgr.getLevel() : null);
        if (serverLevel == null) return;

        ChunkPos chunkPos = event.getChunk().getPos();
        MinecraftServer server = serverLevel.getServer();

        // 延迟到服务器主线程执行（区块已完整加载后再放置宝箱）
        server.execute(() -> tryGenerateMonumentChest(serverLevel, chunkPos));
    }

    // ========================================================================
    // 核心：尝试在海洋神殿顶部中央放置宝箱（服务器主线程调用）
    // ========================================================================
    private static void tryGenerateMonumentChest(ServerLevel level, ChunkPos eventChunkPos) {
        // 取事件区块的结构引用数据（STRUCTURE_STARTS 阶段已包含引用与起始信息）
        ChunkAccess chunk = level.getChunk(eventChunkPos.x, eventChunkPos.z,
                ChunkStatus.STRUCTURE_STARTS, false);
        if (chunk == null) return;

        for (Map.Entry<Structure, LongSet> entry : chunk.getAllReferences().entrySet()) {
            Structure structure = entry.getKey();
            // 仅处理海洋神殿（远古守卫者所在建筑）
            if (structure.type() != StructureType.OCEAN_MONUMENT) continue;

            for (long ref : entry.getValue()) {
                ChunkPos refPos = new ChunkPos(ref);
                ChunkAccess refChunk = level.getChunk(refPos.x, refPos.z,
                        ChunkStatus.STRUCTURE_STARTS, false);
                if (refChunk == null) continue;

                StructureStart start = refChunk.getStartForStructure(structure);
                if (start == null || !start.isValid()) continue;

                placeMonumentChest(level, start);
                return; // 每神殿仅一只宝箱
            }
        }
    }

    // ========================================================================
    // 放置宝箱：优先海绵房附近，找不到则神殿顶部中央
    // 宝箱内容：海洋之星（slot 0） + 杂物 + 极小概率模组附魔书
    // ========================================================================
    private static void placeMonumentChest(ServerLevel level, StructureStart start) {
        // 神殿实例唯一标识：结构起始区块坐标
        ChunkPos startChunk = start.getChunkPos();
        long key = ChunkPos.asLong(startChunk.x, startChunk.z);

        // 持久化幂等：该神殿已放过宝箱则跳过
        OceanStarData data = OceanStarData.get(level);
        if (data.isHandled(key)) return;

        BoundingBox bb = start.getBoundingBox();

        // ================================================================
        // 【位置选择】优先神殿门口（四个方向底面外缘，向上找空气/水方块）
        // 找不到合适位置则 fallback 到神殿屋顶正上方一格
        // ================================================================
        BlockPos chestPos = findMonumentEntranceSpot(level, bb);
        if (chestPos == null) {
            // fallback：神殿屋顶正上方一格
            chestPos = new BlockPos(
                    bb.minX() + bb.getXSpan() / 2,
                    bb.maxY() + 1,
                    bb.minZ() + bb.getZSpan() / 2);
        }

        // 确保宝箱所在区块已完整生成/加载（未生成则跳过，等待后续区块事件重试）
        if (level.getChunk(chestPos.getX() >> 4, chestPos.getZ() >> 4,
                ChunkStatus.FULL, false) == null) return;

        // 强制放置宝箱
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);

        // ================================================================
        // 【宝箱填充】海洋之星 + 杂物 + 极小概率模组附魔书
        // ================================================================
        if (level.getBlockEntity(chestPos) instanceof Container container) {
            // 第 0 格：海洋之星（保底）
            container.setItem(0, new ItemStack(ModItems.OCEAN_STAR.get(), 1));

            // 其余格：杂物 + 极小概率附魔书
            fillMonumentChestLoot(container);
        }

        // 记录该神殿已处理（含宝箱与海洋之星均只生成一次）
        data.markHandled(key);
    }

    // ========================================================================
    // 定位海洋神殿门口位置
    //
    // 海洋神殿入口通常位于建筑某一面底面中央。本方法扫描神殿四个方向
    // （北/南/东/西）的底面外缘，向上寻找第一个空气或水方块作为宝箱放置位置。
    //
    // 选择门口而非海绵房的理由：
    //   - 门口位置更直观，玩家接近神殿时即可看到宝箱
    //   - 不依赖海绵房是否生成（某些神殿可能没有海绵房）
    //   - 避免深入神殿内部寻找，降低探索门槛
    // ========================================================================
    private static BlockPos findMonumentEntranceSpot(ServerLevel level, BoundingBox bb) {
        int centerX = bb.minX() + bb.getXSpan() / 2;
        int centerZ = bb.minZ() + bb.getZSpan() / 2;
        int baseY = bb.minY() + 1;

        // 四个方向的门口候选位置（底面中央向外偏移 1 格）
        BlockPos[] candidates = {
                new BlockPos(centerX, baseY, bb.minZ() - 1),  // 北面
                new BlockPos(centerX, baseY, bb.maxZ() + 1),  // 南面
                new BlockPos(bb.minX() - 1, baseY, centerZ),  // 西面
                new BlockPos(bb.maxX() + 1, baseY, centerZ)   // 东面
        };

        // 对每个候选位置，向上扫描找可放置位置（空气或水）
        for (BlockPos base : candidates) {
            BlockPos pos = base;
            while (pos.getY() <= bb.maxY() + 2) {
                if (level.getBlockState(pos).isAir()
                        || level.getFluidState(pos).is(FluidTags.WATER)) {
                    return pos;
                }
                pos = pos.above();
            }
        }
        return null;  // 四个方向均未找到合适位置
    }

    // ========================================================================
    // 填充海洋神殿宝箱的杂物与附魔书
    //
    // 杂物清单（与海洋主题契合）：
    //   - 铁锭 1-3  | 铁粒 2-5  | 海绵 1-3
    //   - 海晶石 1-2 | 海晶碎片 1-3 | 海晶灯 1-2
    //   - 金锭 1-2  | 名称标签 1 | 鹦鹉螺壳 1-2
    //
    // 附魔书：5% 概率刷一本等级 1 的模组附魔书
    //   （仅限非宝藏级、非合成获取、非特定途径的常规附魔）
    // ========================================================================
    private static void fillMonumentChestLoot(Container container) {
        RandomSource random = RandomSource.create();

        // 杂物候选清单（每项都是新堆栈，不会共享引用）
        ItemStack[] junks = {
                new ItemStack(Items.IRON_INGOT, 1 + random.nextInt(3)),         // 1-3
                new ItemStack(Items.IRON_NUGGET, 2 + random.nextInt(4)),       // 2-5
                new ItemStack(Items.SPONGE, 1 + random.nextInt(3)),            // 1-3
                new ItemStack(Items.PRISMARINE, 1 + random.nextInt(2)),        // 1-2
                new ItemStack(Items.PRISMARINE_CRYSTALS, 1 + random.nextInt(3)),// 1-3
                new ItemStack(Items.SEA_LANTERN, 1 + random.nextInt(2)),       // 1-2
                new ItemStack(Items.GOLD_INGOT, 1 + random.nextInt(2)),        // 1-2
                new ItemStack(Items.NAME_TAG, 1),                               // 1
                new ItemStack(Items.NAUTILUS_SHELL, 1 + random.nextInt(2))     // 1-2
        };

        // 随机选 3-5 件杂物放入剩余格子（避开 slot 0，那里是海洋之星）
        int junkCount = 3 + random.nextInt(3);  // 3-5
        int containerSize = container.getContainerSize();  // 普通宝箱 27 格
        int slot = 1;
        for (int i = 0; i < junkCount && slot < containerSize; i++) {
            ItemStack junk = junks[random.nextInt(junks.length)].copy();
            // 跳过非空格子
            while (slot < containerSize && !container.getItem(slot).isEmpty()) slot++;
            if (slot >= containerSize) break;
            container.setItem(slot, junk);
            slot++;
        }

        // 极小概率（5%）刷模组附魔书
        if (random.nextFloat() < 0.05f) {
            ItemStack book = createRandomModEnchantedBook();
            if (book != null) {
                while (slot < containerSize && !container.getItem(slot).isEmpty()) slot++;
                if (slot < containerSize) {
                    container.setItem(slot, book);
                }
            }
        }
    }

    // ========================================================================
    // 可放入海洋神殿宝箱的模组附魔清单
    //   仅包含：可从附魔台/常规宝箱/村民交易获取的普通附魔
    //   排除：宝藏级、合成获取、特定途径（钓鱼/要塞图书馆/沙漠神殿）的附魔
    //         end_approaches、ancient_yunlai、ancient_yunlai_swordmanship、
    //         plunder、eternal_spark、dawn、lightning_spear II、snatch
    //   注意：「雷电之戟 lightning_spear」I 级为普通附魔，保留在宝箱中（I 级）。
    // ========================================================================
    private static final List<RegistryObject<? extends Enchantment>> CHEST_ENCHANTMENTS =
            Arrays.asList(
                    ModEnchantments.ACCUMULATE,
                    ModEnchantments.AUTO_SMELT,
                    ModEnchantments.ARTISAN_LEGACY,
                    ModEnchantments.FLYWHEEL_EFFECT,
                    ModEnchantments.ENDER_ARROW,
                    ModEnchantments.ILLUSORY_FEAST,
                    ModEnchantments.SPRING_HARVEST,
                    ModEnchantments.ALL_NATURE_REVIVE,
                    ModEnchantments.FERTILE_BOUNTY,
                    ModEnchantments.FALL_CUSHION,
                    ModEnchantments.BLOODTHIRST,
                    ModEnchantments.LEVIS_ECHO,
                    ModEnchantments.ENTROPY,
                    ModEnchantments.TEMPERATURE_CONSTANT,
                    ModEnchantments.DIFFICULTY_GIFT,
                    ModEnchantments.EXPLOSIVE_ARROW,
                    ModEnchantments.CHAIN_ARROW,
                    ModEnchantments.SWIFT_CROSSBOW,
                    ModEnchantments.LIGHTNING_SPEAR,
                    ModEnchantments.EX_NIHILO,
                    ModEnchantments.SMOKELESS_DASH,
                    ModEnchantments.CHAIN_BREAKER,
                    ModEnchantments.QIANPO_QINGMING_SWORD,
                    ModEnchantments.ELEGANT_CATWALK,
                    ModEnchantments.DARK_WALKER
            );

    // ========================================================================
    // 随机生成一本等级 1 的模组附魔书
    // 使用 EnchantedBookItem.createForEnchantment 避免手动构造 NBT
    // ========================================================================
    private static ItemStack createRandomModEnchantedBook() {
        RandomSource rng = RandomSource.create();
        RegistryObject<? extends Enchantment> ro =
                CHEST_ENCHANTMENTS.get(rng.nextInt(CHEST_ENCHANTMENTS.size()));
        Enchantment enchantment = ro.get();
        if (enchantment == null) return null;
        // 等级设为 1（普适、安全，玩家可用铁砧往上合并）
        int level = 1;
        return EnchantedBookItem.createForEnchantment(
                new EnchantmentInstance(enchantment, level));
    }

    // ========================================================================
    // 【PlayerInteractEvent.RightClickBlock】海洋神殿容器内容注入（补充机制）
    //
    //   当玩家右击『海洋神殿』结构内的容器（宝箱/陷阱箱/木桶）时，
    //   若该神殿实例尚未发放过海洋之星，则 100% 放入一个，并持久化去重。
    //
    //   与强制宝箱生成协同：强制宝箱在生成时已直接含海洋之星（该神殿已被标记处理，
    //   此事件自然跳过，不会重复）；仅当强制宝箱生成被关闭、或神殿内存在
    //   数据包/其他模组放置的容器时，此机制负责补发。
    // ========================================================================
    @SubscribeEvent
    public static void onRightClickChest(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getEntity().level();
        // 仅服务端处理
        if (level.isClientSide()) return;
        if (!OceanStarConfig.enableMonumentChestLoot) return;

        // 仅主手触发一次，避免副手重复判定（数据层也有幂等保护）
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        BlockPos pos = event.getPos();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container container)) return;

        if (!(level instanceof ServerLevel serverLevel)) return;

        // 判断该容器是否位于『海洋神殿』结构内
        for (Structure structure : serverLevel.structureManager()
                .getAllStructuresAt(pos).keySet()) {
            if (structure.type() != StructureType.OCEAN_MONUMENT) continue;

            StructureStart start = serverLevel.structureManager().getStructureAt(pos, structure);
            if (start == null || !start.isValid()) continue;

            // 神殿实例唯一标识：结构起始区块坐标
            ChunkPos chunkPos = start.getChunkPos();
            long key = ChunkPos.asLong(chunkPos.x, chunkPos.z);

            // 持久化幂等：该神殿已发放过则跳过
            OceanStarData data = OceanStarData.get(serverLevel);
            if (data.isHandled(key)) return;

            // 100% 放入一个海洋之星（宝箱满则掉落在地上）
            boolean placed = placeOceanStar(container, serverLevel, pos);
            if (placed) {
                data.markHandled(key);
            }
            return;
        }
    }

    // ========================================================================
    // 工具方法：将海洋之星放入容器（优先空槽，满则掉落为物品实体）
    // ========================================================================
    private static boolean placeOceanStar(Container container, ServerLevel level, BlockPos pos) {
        ItemStack starStack = new ItemStack(ModItems.OCEAN_STAR.get(), 1);

        // 优先放入第一个空槽
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).isEmpty()) {
                container.setItem(i, starStack);
                return true;
            }
        }

        // 容器已满：将物品生成在容器上方
        ItemEntity itemEntity = new ItemEntity(level,
                pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                starStack);
        itemEntity.setDefaultPickUpDelay();
        level.addFreshEntity(itemEntity);
        return true;
    }

    // ========================================================================
    // 工具方法：检测玩家是否手持海洋之星（主手或副手）
    // ========================================================================
    private static boolean isHoldingOceanStar(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        return mainHand.is(ModItems.OCEAN_STAR.get())
                || offHand.is(ModItems.OCEAN_STAR.get());
    }
}