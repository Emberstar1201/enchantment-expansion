package com.github.emberstar1201.enchantmentex.entity;

import com.github.emberstar1201.enchantmentex.GirlWorkConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

// ========================================================================
// 【拟人行为：农事】白天有活就干的小农妇
//
//   ① 收获：作物长熟（小麦 / 胡萝卜 / 马铃薯 / 甜菜 / 地狱疣 / 火把花）
//      就走过去收掉，不需要任何工具——熟了的庄稼最要紧，优先级最高；
//   ② 播种：背包里有种子时，走到空耕地上把种子种下去；
//   ③ 开荒：背包里有锄头时，把「靠近水的泥土 / 草方块」翻成耕地——
//      只翻水合范围内的泥地（耕地 4 格内有水才保持湿润，离水太远翻
//      了也白翻），翻地会消耗锄头 1 点耐久。
//
//   干活的「燃料」由玩家提供：锄头和种子可来自主手、副手、装备槽或随身背包。
//   夜晚和雷暴不再阻断工作；战斗与睡觉的优先级仍比农事高。
// ========================================================================
public class SurvivorFarmGoal extends Goal {

    /** 每完成一件农活后的短暂歇息（tick），再去找下一件活——间隔短，干起活来连贯。 */
    private static final int WORK_COOLDOWN_TICKS = 10;
    /** 走向目标地块的超时：超过 300 tick（15 秒）还没到就先放弃，歇一会再找别的活。 */
    private static final int GIVE_UP_TICKS = 300;
    /** 走到距目标中心 1.5 格内就动手。 */
    private static final double REACH_SQR = 2.25D;
    /** 开荒只翻「水合范围内」的泥土：与耕地水平 4 格内有水才能保持湿润。 */
    private static final int WATER_RANGE = 4;

    /** 种子 → 作物 的映射：背包里放什么种子，她就种什么。 */
    private static final Map<Item, Block> SEED_TO_CROP = new HashMap<>();

    static {
        SEED_TO_CROP.put(Items.WHEAT_SEEDS, Blocks.WHEAT);
        SEED_TO_CROP.put(Items.CARROT, Blocks.CARROTS);
        SEED_TO_CROP.put(Items.POTATO, Blocks.POTATOES);
        SEED_TO_CROP.put(Items.BEETROOT_SEEDS, Blocks.BEETROOTS);
        SEED_TO_CROP.put(Items.PUMPKIN_SEEDS, Blocks.PUMPKIN_STEM);
        SEED_TO_CROP.put(Items.MELON_SEEDS, Blocks.MELON_STEM);
        SEED_TO_CROP.put(Items.TORCHFLOWER_SEEDS, Blocks.TORCHFLOWER_CROP);
        SEED_TO_CROP.put(Items.PITCHER_POD, Blocks.PITCHER_CROP);
    }

    /** 农活类型，按此顺序取优先：收获 > 播种 > 开荒。 */
    private enum FarmTask { HARVEST, PLANT, TILL }

    private final Mob mob;
    private final FriendlyGirlInventory girl;
    /** 本轮锁定的目标地块与农活类型；null 表示当前没有活。 */
    private BlockPos targetPos;
    private FarmTask task;
    /** 下一次可以开始找活干的最早 tick（干完活歇口气）。 */
    private int nextStartTick;
    /** 本轮开工时刻，用于超时放弃。 */
    private int startTick;

    public SurvivorFarmGoal(SurvivorGirlEntity mob) {
        this(mob, mob);
    }

    /** 共用目标构造器：三种友好娘实体都使用同一套农事逻辑。 */
    public SurvivorFarmGoal(Mob mob, FriendlyGirlInventory girl) {
        this.mob = mob;
        this.girl = girl;
        // 只占用 MOVE flag，不用 LOOK——这样「开关门」Goal（LOOK）能与农事
        // 并行运行，她穿过田边木门去干活时不会被关着的门卡在门口
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // 休息间隔未到、死了 / 骑着东西 / 被命令坐下 / 睡着 / 泡水 / 有敌人，都不干活
        if (this.girl.getWorkMode() != GirlWorkMode.FARMING
                || this.mob.tickCount < this.nextStartTick
                || !this.mob.isAlive() || this.mob.isPassenger() || this.girl.isOrderedToSit()
                || this.mob.isSleeping() || this.mob.isInWater()
                || this.mob.getTarget() != null) {
            return false;
        }
        return this.findWork();
    }

    /**
     * 在身边扫描一圈，按「收获 > 播种 > 开荒」的优先级锁定一件活。
     * BlockPos.betweenClosed 复用可变坐标对象，入选后必须 immutable() 落盘。
     */
    private boolean findWork() {
        ItemStack seeds = findFirstStack(item -> SEED_TO_CROP.containsKey(item));
        ItemStack hoe = findFirstStack(item -> item instanceof HoeItem);
        BlockPos origin = this.mob.blockPosition();
        BlockPos harvest = null;
        BlockPos plant = null;
        BlockPos till = null;
        double harvestDist = Double.MAX_VALUE;
        double plantDist = Double.MAX_VALUE;
        double tillDist = Double.MAX_VALUE;
        int tillWaterDist = Integer.MAX_VALUE;

        int horizontalRange = GirlWorkConfig.horizontalRange();
        int verticalRange = GirlWorkConfig.verticalRange();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-horizontalRange, -verticalRange, -horizontalRange),
                origin.offset(horizontalRange, verticalRange, horizontalRange))) {
            BlockState state = this.mob.level().getBlockState(pos);
            if (isMatureCrop(state)) {
                double dist = pos.distSqr(origin);
                if (dist < harvestDist) {
                    harvestDist = dist;
                    harvest = pos.immutable();
                }
            } else if (!seeds.isEmpty() && state.is(Blocks.FARMLAND)
                    && this.mob.level().getBlockState(pos.above()).isAir()) {
                double dist = pos.distSqr(origin);
                if (dist < plantDist) {
                    plantDist = dist;
                    plant = pos.immutable();
                }
            } else if (!hoe.isEmpty() && isTillable(state)
                    && this.mob.level().getBlockState(pos.above()).isAir()) {
                // 开荒先按「离水最近」排序，再按离实体的距离排序；这样不会
                // 因为脚边有一块干泥就忽略水边成片的可耕地。
                int waterDistance = waterDistanceSqr(pos);
                if (waterDistance >= 0) {
                    double dist = pos.distSqr(origin);
                    if (waterDistance < tillWaterDist
                            || (waterDistance == tillWaterDist && dist < tillDist)) {
                        tillWaterDist = waterDistance;
                        tillDist = dist;
                        till = pos.immutable();
                    }
                }
            }
        }

        if (harvest != null) {
            this.targetPos = harvest;
            this.task = FarmTask.HARVEST;
        } else if (plant != null) {
            this.targetPos = plant;
            this.task = FarmTask.PLANT;
        } else if (till != null) {
            this.targetPos = till;
            this.task = FarmTask.TILL;
        }
        return this.targetPos != null;
    }

    @Override
    public void start() {
        this.startTick = this.mob.tickCount;
        this.mob.getNavigation().moveTo(this.targetPos.getX() + 0.5D, this.targetPos.getY(),
                this.targetPos.getZ() + 0.5D, 0.7D);
    }

    @Override
    public boolean canContinueToUse() {
        if (this.girl.getWorkMode() != GirlWorkMode.FARMING || this.targetPos == null) {
            return false;
        }
        // 目标地块被别人动了（收走了 / 被踩了 / 种上了）就重新找活
        BlockState state = this.mob.level().getBlockState(this.targetPos);
        switch (this.task) {
            case HARVEST -> {
                if (!isMatureCrop(state)) {
                    return false;
                }
            }
            case PLANT -> {
                if (!state.is(Blocks.FARMLAND)
                        || !this.mob.level().getBlockState(this.targetPos.above()).isAir()) {
                    return false;
                }
            }
            case TILL -> {
                if (!isTillable(state)
                        || !this.mob.level().getBlockState(this.targetPos.above()).isAir()) {
                    return false;
                }
            }
        }
        // 睡觉、被命令坐下、泡水、有敌人：活可以先放放
        if (this.mob.isSleeping() || this.mob.isPassenger() || this.girl.isOrderedToSit()
                || this.mob.isInWater() || this.mob.getTarget() != null) {
            return false;
        }
        // 走不到（隔栏 / 卡门口）就放弃，防止在原地空转
        if (this.mob.tickCount - this.startTick > GIVE_UP_TICKS) {
            this.nextStartTick = this.mob.tickCount + WORK_COOLDOWN_TICKS;
            this.targetPos = null;
            return false;
        }
        return true;
    }

    @Override
    public void tick() {
        if (this.targetPos == null) {
            return;
        }
        double dx = this.mob.getX() - (this.targetPos.getX() + 0.5D);
        double dy = this.mob.getY() - this.targetPos.getY();
        double dz = this.mob.getZ() - (this.targetPos.getZ() + 0.5D);
        if (dx * dx + dy * dy + dz * dz > REACH_SQR) {
            return; // 还在赶路
        }
        this.mob.getNavigation().stop();
        Level level = this.mob.level();
        switch (this.task) {
            case HARVEST -> {
                this.mob.swing(InteractionHand.MAIN_HAND);
                // destroyBlock(true) 走原版掉落：小麦掉麦粒与种子、胡萝卜掉胡萝卜……
                level.destroyBlock(this.targetPos, true, this.mob);
            }
            case PLANT -> {
                ItemStack seeds = findFirstStack(item -> SEED_TO_CROP.containsKey(item));
                Block crop = seeds.isEmpty() ? null : SEED_TO_CROP.get(seeds.getItem());
                if (crop != null) {
                    seeds.shrink(1);
                    this.mob.swing(InteractionHand.MAIN_HAND);
                    level.setBlock(this.targetPos.above(), crop.defaultBlockState(), 3);
                    level.playSound(null, this.targetPos, SoundEvents.CROP_PLANTED,
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                }
            }
            case TILL -> {
                ItemStack hoe = findFirstStack(item -> item instanceof HoeItem);
                if (!hoe.isEmpty()) {
                    this.mob.swing(InteractionHand.MAIN_HAND);
                    level.setBlock(this.targetPos, Blocks.FARMLAND.defaultBlockState(), 3);
                    level.playSound(null, this.targetPos, SoundEvents.HOE_TILL,
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                    // 翻地消耗锄头耐久，用坏了就得再给她一把
                    hoe.hurtAndBreak(1, this.mob,
                            (entity) -> entity.broadcastBreakEvent(EquipmentSlot.MAINHAND));
                }
            }
        }
        // 干完一件活歇口气，再找下一件
        this.nextStartTick = this.mob.tickCount + WORK_COOLDOWN_TICKS;
        this.targetPos = null;
    }

    @Override
    public void stop() {
        this.targetPos = null;
    }

    /** 按主手、 副手、装备槽、背包的优先级查找实体内原始物品。 */
    private ItemStack findFirstStack(Predicate<Item> filter) {
        ItemStack mainHand = this.mob.getMainHandItem();
        if (filter.test(mainHand.getItem())) {
            return mainHand;
        }
        ItemStack offHand = this.mob.getOffhandItem();
        if (filter.test(offHand.getItem())) {
            return offHand;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
                continue;
            }
            ItemStack stack = this.mob.getItemBySlot(slot);
            if (!stack.isEmpty() && filter.test(stack.getItem())) {
                return stack;
            }
        }
        SimpleContainer backpack = this.girl.getMeatInventory();
        for (int i = 0; i < backpack.getContainerSize(); i++) {
            ItemStack stack = backpack.getItem(i);
            if (!stack.isEmpty() && filter.test(stack.getItem())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 是否为可收获的成熟作物（双格作物如瓶状草收获逻辑复杂，先不碰）。 */
    private static boolean isMatureCrop(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return false;
        }
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        return block == Blocks.NETHER_WART
                && state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
    }

    /** 是否可用锄头翻地的「泥土方块」：泥土与草方块。 */
    private static boolean isTillable(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM);
    }

    /** 复刻耕地水合判定：目标方块同层或上一层、水平 4 格内存在水。 */
    private boolean isNearWater(BlockPos pos) {
        return waterDistanceSqr(pos) >= 0;
    }

    /** 返回最近水面的平方距离；不在水合范围内时返回 -1。 */
    private int waterDistanceSqr(BlockPos pos) {
        int best = Integer.MAX_VALUE;
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -WATER_RANGE; dx <= WATER_RANGE; dx++) {
                for (int dz = -WATER_RANGE; dz <= WATER_RANGE; dz++) {
                    if (this.mob.level().getFluidState(pos.offset(dx, dy, dz)).is(FluidTags.WATER)) {
                        int distance = dx * dx + dz * dz + dy * dy;
                        if (distance < best) {
                            best = distance;
                        }
                    }
                }
            }
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }
}
