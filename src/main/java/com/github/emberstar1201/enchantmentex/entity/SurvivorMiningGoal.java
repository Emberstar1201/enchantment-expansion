package com.github.emberstar1201.enchantmentex.entity;

import com.github.emberstar1201.enchantmentex.ChainBreakerConfig;
import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.enchantment.ArtisanLegacyHandler;
import com.github.emberstar1201.enchantmentex.enchantment.AutoSmeltHandler;
import com.github.emberstar1201.enchantmentex.enchantment.ExNihiloHandler;
import com.github.emberstar1201.enchantmentex.enchantment.ModEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// ========================================================================
// 【拟人行为：挖矿】白天拿着镐子找活干的小矿工
//
//   玩法规则（用户的「严格等级制度」）：
//   ① 主手（武器栏）放一把镐，她就会去挖矿——背包里的镐也认，主手优先；
//   ② 镐阶决定能挖什么：木镐只能挖石头和煤炭，石镐解锁铁矿，
//      铁镐解锁金矿/钻石……实现方式是走原版判定
//      pickaxe.isCorrectToolForDrops(state)——工具阶级不够的方块根本
//      「不可收获」，她自然不会去碰，与玩家挖掘的等级门槛完全一致；
//   ③ 挖矿全程与玩家一致，能看到完整演出：
//        走到矿前 → 对着方块持续挥手 + 方块裂纹逐级扩散（原版
//        destroyBlockProgress 动画）→ 挖掘耗时按玩家公式结算
//        （木镐挖石头约 1.1 秒、挖煤矿约 2.3 秒，效率附魔同样加速）
//        → 方块破碎、掉落物弹出 → 她自己走过去把掉落物捡进随身背包；
//   ④ 附魔加成全部生效：
//        - 原版时运 / 精准采集 / 效率 / 耐久：走玩家同款公式，天然生效；
//        - 自动冶炼：粗矿直接变锭（II/III 级加成概率与玩家侧一致）；
//        - 无中生有：挖石质方块概率爆出额外矿物；
//        - 匠心传承 / 中国制造：记录记忆方块 + 概率额外掉落 + 定期修复；
//        - 连锁挖掘：主方块演出挖掘动画，连锁部分瞬间收进背包。
//
//   玩家侧的采集类附魔靠 BlockEvent.BreakEvent 触发，生物挖矿不走该事件，
//   因此在 Handler 里补了 mob 侧公共方法，由本 Goal 直接调用。
//
//   挖掘目标选择：矿石（DropExperienceBlock）优先且允许挖脚下的；
//   普通石头只挖「同层及以上」——防止她把自己脚下的石头地板啃穿。
//   战斗与睡觉的优先级都比挖矿高，夜晚和雷暴不再阻断工作。
//   摸黑挖矿没有任何惩罚（「照明」由 SurvivorLightPlaceGoal 负责）。
// ========================================================================
public class SurvivorMiningGoal extends Goal {

    /** 挖矿的两个阶段：对着方块挖掘 → 捡拾掉落物。 */
    private enum Phase { DIG, COLLECT }

    /** 捡完一轮掉落物的短暂歇息（tick），再找下一块。 */
    private static final int WORK_COOLDOWN_TICKS = 10;
    /** 走向目标方块的超时：超过 300 tick（15 秒）还没到就先放弃。 */
    private static final int GIVE_UP_TICKS = 300;
    /** 捡拾掉落物的超时：超过 150 tick（7.5 秒）就放弃捡剩下的（可能卡角落）。 */
    private static final int COLLECT_TIMEOUT_TICKS = 150;
    /** 找矿范围：水平 16 格；垂直 ±1（能看到的矿壁 / 脚下的矿）。 */
    private static final int SEARCH_H_RANGE = 16;
    private static final int SEARCH_V_RANGE = 1;
    /** 走到距目标中心 1.5 格内才开挖（与玩家交互距离相当）。 */
    private static final double REACH_SQR = 2.25D;
    /** 距掉落物 1.3 格内即可捡起（近身拾取半径）。 */
    private static final double PICKUP_REACH_SQR = 1.69D;

    /**
     * 石质方块白名单（矿石之外的「挖石头」对象）。
     * 用显式白名单而不是「所有可挖方块」：熔炉/铁砧/钟之类也是镐子挖的，
     * 白名单可以避免她把玩家的设备拆了。
     */
    private static final Set<Block> MINEABLE_STONES = Set.of(
            Blocks.STONE, Blocks.COBBLESTONE, Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE,
            Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.TUFF,
            Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.CALCITE, Blocks.DRIPSTONE_BLOCK,
            Blocks.NETHERRACK, Blocks.BASALT, Blocks.BLACKSTONE, Blocks.END_STONE);

    private final SurvivorGirlEntity mob;
    /** 当前阶段。 */
    private Phase phase = Phase.DIG;
    /** DIG：正在挖的方块；COLLECT：刚挖掉的方块位置（捡拾锚点）。 */
    private BlockPos targetPos;
    /** DIG 阶段的挖掘累计进度（每 tick 增长，≥1 破坏），与玩家同款算法。 */
    private float digProgress;
    /** 下一次可以开始找活的最早 tick。 */
    private int nextStartTick;
    /** 本轮开工时刻，用于超时放弃。 */
    private int startTick;
    /** COLLECT 阶段开始时刻，用于捡拾超时。 */
    private int collectStartTick;

    public SurvivorMiningGoal(SurvivorGirlEntity mob) {
        this.mob = mob;
        // 只占用 MOVE flag——照明 Goal（无 flag）能与挖矿并行，她边挖边插火把
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // 休息间隔未到、死了 / 骑着东西 / 被命令坐下 / 睡着 / 泡水 / 有敌人，都不挖
        if (this.mob.getWorkMode() != GirlWorkMode.MINING
                || this.mob.tickCount < this.nextStartTick
                || !this.mob.isAlive() || this.mob.isPassenger() || this.mob.isOrderedToSit()
                || this.mob.isSleeping() || this.mob.isInWater()
                || this.mob.getTarget() != null) {
            return false;
        }
        return this.findWork();
    }

    /**
     * 在身边扫描一圈锁定一个目标方块：矿石优先，其次石质方块。
     * BlockPos.betweenClosed 复用可变坐标对象，入选后必须 immutable() 落盘。
     */
    private boolean findWork() {
        ItemStack pickaxe = this.findPickaxe();
        if (pickaxe.isEmpty()) {
            return false;
        }
        Level level = this.mob.level();
        BlockPos origin = this.mob.blockPosition();
        BlockPos ore = null;
        BlockPos stone = null;
        double oreDist = Double.MAX_VALUE;
        double stoneDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SEARCH_H_RANGE, -SEARCH_V_RANGE, -SEARCH_H_RANGE),
                origin.offset(SEARCH_H_RANGE, SEARCH_V_RANGE, SEARCH_H_RANGE))) {
            BlockState state = level.getBlockState(pos);
            if (!this.canMine(pos, state, pickaxe)) {
                continue;
            }
            boolean isOre = state.getBlock() instanceof DropExperienceBlock;
            // 普通石头只挖「同层及以上」：脚下的（地板）不啃，防止挖穿自家地板
            if (!isOre && pos.getY() < origin.getY()) {
                continue;
            }
            double dist = pos.distSqr(origin);
            if (isOre) {
                if (dist < oreDist) {
                    oreDist = dist;
                    ore = pos.immutable();
                }
            } else if (dist < stoneDist) {
                stoneDist = dist;
                stone = pos.immutable();
            }
        }

        // 矿优先：有煤挖煤、有铁挖铁；周围连块石头都没有才闲着
        this.targetPos = ore != null ? ore : stone;
        this.phase = Phase.DIG;
        this.digProgress = 0.0F;
        return this.targetPos != null;
    }

    /**
     * 单个方块是否归她挖。
     * 等级制度的核心就在 pickaxe.isCorrectToolForDrops(state)：
     * 工具阶级低于方块需求时返回 false（木镐对铁矿石就是 false），她直接无视。
     */
    private boolean canMine(BlockPos pos, BlockState state, ItemStack pickaxe) {
        if (state.isAir() || !state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return false;
        }
        if (!pickaxe.isCorrectToolForDrops(state)) {
            return false;
        }
        // destroySpeed < 0 = 不可破坏（基岩等），必须排除
        if (state.getDestroySpeed(this.mob.level(), pos) < 0.0F) {
            return false;
        }
        // 带方块实体的（熔炉/箱子等）不拆
        if (state.hasBlockEntity()) {
            return false;
        }
        // 不挖水柱下方的方块（防止挖出口子灌水）
        return this.mob.level().getFluidState(pos.above()).isEmpty();
    }

    @Override
    public void start() {
        this.startTick = this.mob.tickCount;
        this.phase = Phase.DIG;
        this.digProgress = 0.0F;
        this.mob.getNavigation().moveTo(this.targetPos.getX() + 0.5D, this.targetPos.getY(),
                this.targetPos.getZ() + 0.5D, 0.7D);
    }

    @Override
    public boolean canContinueToUse() {
        // 共通打断条件：睡觉、被命令坐下、泡水、有敌人
        if (this.mob.getWorkMode() != GirlWorkMode.MINING
                || this.mob.isSleeping() || this.mob.isPassenger() || this.mob.isOrderedToSit()
                || this.mob.isInWater() || this.mob.getTarget() != null) {
            return false;
        }
        if (this.phase == Phase.DIG) {
            if (this.targetPos == null) {
                return false;
            }
            // 目标被别人动了（挖了 / 消失了）或镐子没了，就重新找活
            BlockState state = this.mob.level().getBlockState(this.targetPos);
            if (!this.canMine(this.targetPos, state, this.findPickaxe())) {
                return false;
            }
            // 走不到就放弃，防止在原地空转
            if (this.mob.tickCount - this.startTick > GIVE_UP_TICKS) {
                this.nextStartTick = this.mob.tickCount + WORK_COOLDOWN_TICKS;
                this.targetPos = null;
                return false;
            }
            return true;
        }
        // COLLECT 阶段：锚点附近还有掉落物且没超时才继续捡
        if (this.mob.tickCount - this.collectStartTick > COLLECT_TIMEOUT_TICKS) {
            this.nextStartTick = this.mob.tickCount + WORK_COOLDOWN_TICKS;
            this.targetPos = null;
            return false;
        }
        return this.findNearestLoot() != null;
    }

    @Override
    public void tick() {
        if (this.phase == Phase.DIG) {
            this.digTick();
        } else {
            this.collectTick();
        }
    }

    // ========================================================================
    // DIG：对着目标方块「玩家式挖掘」
    // ========================================================================
    private void digTick() {
        if (this.targetPos == null) {
            return;
        }
        double dx = this.mob.getX() - (this.targetPos.getX() + 0.5D);
        double dy = this.mob.getY() - this.targetPos.getY();
        double dz = this.mob.getZ() - (this.targetPos.getZ() + 0.5D);
        if (dx * dx + dy * dy + dz * dz > REACH_SQR) {
            this.tickToolSelfRepair(); // 赶路时顺带处理工具的自动修复判定
            return; // 还在赶路，路上不累计进度
        }
        ItemStack pickaxe = this.findPickaxe();
        if (pickaxe.isEmpty()) {
            return;
        }
        this.mob.getNavigation().stop();

        // 持续挥手（原版 LivingEntity.swing 每 tick 调用即是「挖掘中」的挥手节奏）
        this.mob.swing(InteractionHand.MAIN_HAND);

        BlockState state = this.mob.level().getBlockState(this.targetPos);
        this.digProgress += this.getDestroyProgress(state, pickaxe);
        if (this.digProgress >= 1.0F) {
            this.breakBlock(this.targetPos, state, pickaxe);
        } else {
            // 服务端广播裂纹动画：进度换算成 0~9 的破坏等级（与玩家同款换算）
            int stage = Math.min(9, Math.max(0, (int) (this.digProgress * 10.0F) - 1));
            ((ServerLevel) this.mob.level()).destroyBlockProgress(
                    this.mob.getId(), this.targetPos, stage);
        }
    }

    /**
     * 破坏方块并弹出掉落物（看得见的演出），随后进入捡拾阶段。
     */
    private void breakBlock(BlockPos pos, BlockState state, ItemStack pickaxe) {
        ServerLevel serverLevel = (ServerLevel) this.mob.level();
        List<ItemStack> drops = this.collectDrops(pos, state, pickaxe);
        // 清除裂纹动画
        serverLevel.destroyBlockProgress(this.mob.getId(), pos, -1);
        // 移除方块（不自然掉落——掉落由下面手动弹出，这样附魔才完整生效）
        serverLevel.destroyBlock(pos, false, this.mob);
        // 经验照常掉：矿石的经验走 spawnAfterBreak（吃时运加成，与玩家一致）
        state.getBlock().spawnAfterBreak(state, serverLevel, pos, pickaxe, true);
        // 掉落物以原版弹跳姿态出现在方块位置——「变成掉落物」的演出
        for (ItemStack drop : drops) {
            Block.popResource(serverLevel, pos, drop);
        }
        // 主方块本体消耗 1 点耐久（连锁部分由 tryChainBreak 另算）
        this.damageTool(pickaxe, 1);
        // 连锁挖掘：同类型方块直接收进背包（不逐个演出，保持连锁的「爽快」）
        this.tryChainBreak(pos, state, pickaxe);
        // 进入捡拾阶段：targetPos 保留作捡拾锚点
        this.phase = Phase.COLLECT;
        this.collectStartTick = this.mob.tickCount;
    }

    // ========================================================================
    // COLLECT：把刚挖出来的掉落物自己捡进随身背包
    // ========================================================================
    private void collectTick() {
        ItemEntity loot = this.findNearestLoot();
        if (loot == null) {
            // 捡完了（或掉落物被冲走）：歇口气再找下一块矿
            this.nextStartTick = this.mob.tickCount + WORK_COOLDOWN_TICKS;
            this.targetPos = null;
            return;
        }
        if (loot.distanceToSqr(this.mob) <= PICKUP_REACH_SQR) {
            // 近身拾取：整堆收进背包；背包满了剩余的留在地上
            ItemStack taken = loot.getItem().copy();
            ItemStack leftover = this.mob.getMeatInventory().addItem(taken);
            if (leftover.isEmpty()) {
                loot.discard();
            } else {
                loot.setItem(leftover);
            }
            this.mob.swing(InteractionHand.MAIN_HAND);
        } else {
            this.mob.getNavigation().moveTo(loot.getX(), loot.getY(), loot.getZ(), 0.7D);
        }
    }

    /** 找捡拾锚点（刚挖掉的方块）附近 2 格内最近的掉落物；没有返回 null。 */
    private ItemEntity findNearestLoot() {
        if (this.targetPos == null) {
            return null;
        }
        AABB box = new AABB(this.targetPos).inflate(2.0D);
        ItemEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (ItemEntity item : this.mob.level().getEntitiesOfClass(ItemEntity.class, box)) {
            double dist = item.distanceToSqr(this.mob);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = item;
            }
        }
        return nearest;
    }

    /**
     * 单 tick 的挖掘进度增量，公式与玩家 getDestroySpeed 一致：
     *   进度 = 工具速度 / 硬度 / 30（可收获分支）。
     * 木镐挖石头：2 / 1.5 / 30 ≈ 0.044/tick → 约 1.1 秒，与玩家完全相同；
     * 效率附魔同样按玩家规则加成（eff² + 1）；她站地上且不在水中，无额外惩罚。
     */
    private float getDestroyProgress(BlockState state, ItemStack pickaxe) {
        Level level = this.mob.level();
        float hardness = state.getDestroySpeed(level, this.targetPos);
        if (hardness <= -1.0F) {
            return 0.0F; // 不可破坏，理论上进不来，保险起见
        }
        boolean canHarvest = pickaxe.isCorrectToolForDrops(state);
        float speed = canHarvest ? pickaxe.getDestroySpeed(state) : 1.0F;
        int efficiency = EnchantmentHelper.getItemEnchantmentLevel(
                Enchantments.BLOCK_EFFICIENCY, pickaxe);
        if (efficiency > 0 && canHarvest) {
            speed += efficiency * efficiency + 1;
        }
        // canMine 已保证「可收获」，走 ÷30 分支
        return speed / hardness / 30.0F;
    }

    /**
     * 收集一个方块的完整掉落（原版战利品 + 模组采集类附魔的产出）。
     */
    private List<ItemStack> collectDrops(BlockPos pos, BlockState state, ItemStack pickaxe) {
        ServerLevel serverLevel = (ServerLevel) this.mob.level();
        // 走原版战利品表（工具作为上下文）：时运 / 精准采集 / 全局掉落修改器照常生效
        List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, serverLevel, pos,
                this.mob.level().getBlockEntity(pos), this.mob, pickaxe));

        // 自动冶炼：粗矿直接变锭，II/III 级加成概率与玩家侧完全一致
        int smeltLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.AUTO_SMELT.get(), pickaxe);
        if (smeltLevel > 0 && Config.autoSmeltOreBlocks.contains(blockId(state))) {
            drops = AutoSmeltHandler.applySmelt(drops, smeltLevel, serverLevel.random);
        }

        // 无中生有：挖石质方块概率爆出额外矿物（稀有池 / 常规池与玩家侧一致）
        int exNihiloLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.EX_NIHILO.get(), pickaxe);
        if (exNihiloLevel > 0 && Config.exNihiloStoneBlocks.contains(blockId(state))
                && pickaxe.isCorrectToolForDrops(state)) {
            ItemStack extra = ExNihiloHandler.rollExtraDropForMob(serverLevel, exNihiloLevel);
            if (!extra.isEmpty()) {
                drops.add(extra);
            }
        }

        // 匠心传承 / 中国制造：记录记忆方块 + 概率额外掉落
        ItemStack legacy = ArtisanLegacyHandler.mobBreak(pickaxe, state, pos,
                serverLevel, this.mob, serverLevel.random);
        if (!legacy.isEmpty()) {
            drops.add(legacy);
        }
        return drops;
    }

    /**
     * 连锁挖掘：围绕主方块的立方体范围内连锁破坏同类型方块。
     * 连锁部分不做挖掘动画（与玩家连锁挖掘的瞬时手感一致），掉落直接收进背包。
     */
    private void tryChainBreak(BlockPos origin, BlockState targetState, ItemStack pickaxe) {
        int chainLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.CHAIN_BREAKER.get(), pickaxe);
        if (chainLevel <= 0 || !ChainBreakerConfig.isEnabled()) {
            return;
        }
        int radius = ChainBreakerConfig.getRadius(chainLevel);
        if (radius <= 0) {
            return;
        }
        Level level = this.mob.level();
        ServerLevel serverLevel = (ServerLevel) level;
        int broken = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius))) {
            if (pos.equals(origin)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            // 仅连锁与目标方块同类型的方块；不可破坏（基岩）跳过
            if (state.isAir() || state.getBlock() != targetState.getBlock()
                    || state.getDestroySpeed(level, pos) < 0.0F) {
                continue;
            }
            List<ItemStack> drops = this.collectDrops(pos.immutable(), state, pickaxe);
            level.destroyBlock(pos.immutable(), false, this.mob);
            state.getBlock().spawnAfterBreak(state, serverLevel, pos.immutable(), pickaxe, true);
            for (ItemStack drop : drops) {
                ItemStack leftover = this.mob.getMeatInventory().addItem(drop);
                if (!leftover.isEmpty()) {
                    this.mob.spawnAtLocation(leftover);
                }
            }
            broken++;
        }
        if (broken > 0) {
            double rawCost = broken * ChainBreakerConfig.chainBreakerDurabilityPerBlock;
            int cost = Math.max(1, (int) Math.ceil(rawCost));
            cost = Math.min(cost, ChainBreakerConfig.chainBreakerMaxDurabilityCost);
            this.damageTool(pickaxe, cost);
        }
    }

    /**
     * 工具耐久结算：逐点掷原版耐久与耐久强化的「不消耗」概率，
     * 与玩家挖掘的耐久数学一致（I 级耐久 ≈ 1/2 概率免除，等效耐久翻倍）。
     */
    private void damageTool(ItemStack tool, int baseCost) {
        if (tool.isEmpty() || baseCost <= 0 || !tool.isDamageableItem()) {
            return;
        }
        int unbreaking = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, tool);
        int durabilityBoost = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DURABILITY_BOOST.get(), tool);
        int actualCost = 0;
        for (int i = 0; i < baseCost; i++) {
            // 原版耐久：level/(level+1) 概率免除本次损耗
            if (unbreaking > 0 && this.mob.getRandom().nextInt(unbreaking + 1) > 0) {
                continue;
            }
            // 耐久强化：I 50% / II 66% / III 75% 概率不消耗（等效耐久 ×2/×3/×4）
            if (durabilityBoost > 0 && this.mob.getRandom().nextInt(durabilityBoost + 1) > 0) {
                continue;
            }
            actualCost++;
        }
        if (actualCost > 0) {
            tool.hurtAndBreak(actualCost, this.mob,
                    (entity) -> entity.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
    }

    /**
     * 工具自动修复：自动修复（I 100t / II 80t / III 60t 回 1 点）、
     * 中国制造（每 3 秒回 1 点）。赶路的每个 tick 都会检查。
     */
    private void tickToolSelfRepair() {
        ItemStack pickaxe = this.findPickaxe();
        if (pickaxe.isEmpty() || !pickaxe.isDamageableItem() || pickaxe.getDamageValue() <= 0) {
            return;
        }
        int autoRepair = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.AUTO_REPAIR.get(), pickaxe);
        int interval = switch (autoRepair) {
            case 1 -> 100;
            case 2 -> 80;
            case 3 -> 60;
            default -> -1;
        };
        int madeInChina = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.MADE_IN_CHINA.get(), pickaxe);
        if (madeInChina > 0) {
            interval = 60;
        }
        if (interval > 0 && this.mob.tickCount % interval == 0) {
            pickaxe.setDamageValue(pickaxe.getDamageValue() - 1);
        }
    }

    /**
     * 找她的镐：主手、 副手、装备槽、背包按优先级查找，返回实体内的原始引用。
     */
    private ItemStack findPickaxe() {
        ItemStack mainHand = this.mob.getMainHandItem();
        if (mainHand.getItem() instanceof PickaxeItem) {
            return mainHand;
        }
        ItemStack offHand = this.mob.getOffhandItem();
        if (offHand.getItem() instanceof PickaxeItem) {
            return offHand;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
                continue;
            }
            ItemStack stack = this.mob.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof PickaxeItem) {
                return stack;
            }
        }
        SimpleContainer backpack = this.mob.getMeatInventory();
        for (int i = 0; i < backpack.getContainerSize(); i++) {
            ItemStack stack = backpack.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof PickaxeItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    @Override
    public void stop() {
        // 中断时清除裂纹动画（挖掘到一半被打断 / 睡觉去 等）
        if (this.targetPos != null && this.digProgress > 0.0F) {
            ((ServerLevel) this.mob.level()).destroyBlockProgress(
                    this.mob.getId(), this.targetPos, -1);
        }
        this.targetPos = null;
        this.phase = Phase.DIG;
        this.digProgress = 0.0F;
    }
}
