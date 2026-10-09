package com.github.emberstar1201.enchantmentex.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumSet;

/**
 * 友好娘共用的自动睡觉目标（幸存者少女、丧尸娘、溺尸娘通用）。
 *
 * <p>规则（三类实体完全一致）：</p>
 * <ul>
 *   <li>只有<b>已驯服</b>的个体会睡觉（野生亡灵保持游荡）；</li>
 *   <li>入夜或雷暴时，只要附近存在可睡的床就<b>直接去睡，无视周围怪物</b>——
 *       玩家建的庇护所本来就可能把怪物刷在墙外，睡眠 Goal 的注册优先级高于
 *       攻击 Goal，赶路与睡眠期间不会因发现敌人而放弃上床；</li>
 *   <li>躺下后只认「天亮 / 雷暴停 / 床被拆」。受伤时原版 {@link LivingEntity#hurt}
 *       会强制唤醒，本 Goal 下一 tick 立即把她重新放回床上（伤害照常结算，
 *       残血感染等原有逻辑不受影响）；</li>
 *   <li>睡姿直接复用 {@link Mob#startSleeping} 的 Pose.SLEEPING，
 *       渲染层会自动套用玩家同款平躺姿势，三种实体无需各自实现。</li>
 * </ul>
 *
 * <p>出生点保护：入睡走的是生物睡眠接口，不触碰玩家重生点数据；选床时还会
 * 跳过在线玩家已绑定的重生床；玩家右键她正睡的床时由
 * {@link GirlBedWakeHandler} 先把她唤起，同一次右键玩家即可上床。</p>
 */
public final class GirlSleepGoal extends Goal {
    private static final int BED_SEARCH_RADIUS = 64;
    private static final int SEARCH_INTERVAL = 100;
    private static final int REPATH_INTERVAL = 20;
    private static final int GIVE_UP_TICKS = 600;
    private static final int GIVE_UP_COOLDOWN = 12000;
    private static final double BED_REACH_SQR = 4.0D;

    private final Mob mob;
    private final FriendlyGirlInventory girl;
    @Nullable
    private BlockPos bedPos;
    private int searchCooldown;
    private int repathCooldown;
    private long startTick;
    private long giveUpUntilTick;
    private boolean hadNoGravity;

    public GirlSleepGoal(Mob mob, FriendlyGirlInventory girl) {
        this.mob = mob;
        this.girl = girl;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
    }

    private boolean isSleepTime() {
        return this.mob.level().isNight() || this.mob.level().isThundering();
    }

    @Override
    public boolean canUse() {
        if (!this.girl.isTamed() || this.mob.isDeadOrDying()) {
            return false;
        }
        // 已经在睡：典型场景是区块卸载重载后，实体带着 sleepingPos 回到世界，
        // 但 Goal 的运行状态不持久化——必须在这里「接管现场」，恢复床位继续睡，
        // 否则她永远等不到天亮唤醒（只能玩家右键），这正是「白天不会醒」的根因。
        if (this.mob.isSleeping()) {
            this.bedPos = this.mob.getSleepingPos().orElse(null);
            return true;
        }
        // 已驯服、未被命令坐下、没骑乘、不在水里，且处于夜晚 / 雷暴。
        // 注意：刻意不检查 getTarget()——庇护所墙外有怪物也要正常上床。
        if (this.mob.tickCount < this.giveUpUntilTick || !this.isSleepTime()
                || this.girl.isOrderedToSit() || this.mob.isPassenger()
                || this.mob.isInWater()) {
            return false;
        }
        if (this.bedPos != null && isBedAvailable(this.mob.level(), this.bedPos)) {
            return true;
        }
        if (--this.searchCooldown <= 0) {
            this.searchCooldown = SEARCH_INTERVAL;
            this.bedPos = findNearestBed();
        }
        return this.bedPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        // 天亮 / 雷暴停歇 → 起床（玩家睡觉跳过夜晚同样会让 isSleepTime 变 false）
        if (!this.isSleepTime()) {
            return false;
        }
        if (this.mob.isSleeping()) {
            // 睡着后雷打不动：不检查敌人、不检查目标，只剩「床没了」会起床。
            // 配合 isInterruptable()=false，跟随主人等高优先级 Goal 也无法把她抢起来。
            return this.bedPos != null
                    && this.mob.level().getBlockState(this.bedPos).getBlock() instanceof BedBlock;
        }
        // 赶路阶段同样不检查战斗目标：即使中途刷出怪物也要继续去床上。
        if (this.girl.isOrderedToSit() || this.mob.isPassenger()
                || this.mob.isInWater() || this.mob.isDeadOrDying()) {
            return false;
        }
        if (this.mob.tickCount - this.startTick > GIVE_UP_TICKS) {
            this.giveUpUntilTick = this.mob.tickCount + GIVE_UP_COOLDOWN;
            this.bedPos = null;
            return false;
        }
        return this.bedPos != null && isBedAvailable(this.mob.level(), this.bedPos);
    }

    /**
     * 已经躺上床后本 Goal 不可被打断：原版 GoalSelector 只在「运行中的 Goal 可打断
     * 且新 Goal 优先级数字更小」时才允许抢占，返回 false 后，跟随主人（溺尸娘为
     * 优先级 1）、坐下指令等任何 Goal 都无法把她从床上抢起来——
     * 醒来只剩「天亮 / 床被拆 / 死亡」三条路。
     * 赶路去床上的阶段仍可被正常打断（比如主人走远时放弃睡觉去跟随）。
     */
    @Override
    public boolean isInterruptable() {
        return !this.mob.isSleeping();
    }

    @Override
    public void start() {
        this.startTick = this.mob.tickCount;
        this.repathCooldown = 0;
        this.hadNoGravity = this.mob.isNoGravity();
        // 接管已睡状态时不需要再导航（人已经在床上）
        if (!this.mob.isSleeping() && this.bedPos != null) {
            this.mob.getNavigation().moveTo(
                    this.bedPos.getX() + 0.5D, this.bedPos.getY(), this.bedPos.getZ() + 0.5D, 1.0D);
        }
    }

    @Override
    public void tick() {
        if (this.bedPos == null || !this.mob.isAlive()) {
            return;
        }
        if (this.mob.isSleeping()) {
            // 睡中锁定床面：原版睡姿仍受重力影响，这里关重力并每 tick 贴回床中心，
            // 防止抖动、被击退速度残留或其它实体挤压导致漂移。
            this.mob.setDeltaMovement(Vec3.ZERO);
            this.mob.setNoGravity(true);
            double bedX = this.bedPos.getX() + 0.5D;
            double bedY = this.bedPos.getY() + 0.6875D;
            double bedZ = this.bedPos.getZ() + 0.5D;
            if (this.mob.distanceToSqr(bedX, bedY, bedZ) > 1.0E-7D) {
                this.mob.setPos(bedX, bedY, bedZ);
            }
            return;
        }
        if (!isBedAvailable(this.mob.level(), this.bedPos)) {
            this.bedPos = null;
            return;
        }
        double distSqr = Vec3.atCenterOf(this.bedPos).distanceToSqr(this.mob.position());
        boolean sameHeight = Math.abs(
                this.mob.getY() - (this.bedPos.getY() + 0.6875D)) < 1.5D;
        if (distSqr <= BED_REACH_SQR && sameHeight) {
            this.mob.getNavigation().stop();
            // 到达床边 → 入睡。此处同时承担「受击回躺」：
            // 原版 hurt 会把睡眠者唤醒并清掉 OCCUPIED，但她人没离开床，
            // 下一 tick 走到这里就会立刻重新躺回去（夜里被打也不醒）。
            this.mob.startSleeping(this.bedPos);
        } else if (--this.repathCooldown <= 0) {
            this.repathCooldown = REPATH_INTERVAL;
            this.mob.getNavigation().moveTo(
                    this.bedPos.getX() + 0.5D, this.bedPos.getY(),
                    this.bedPos.getZ() + 0.5D, 1.0D);
        }
    }

    @Override
    public void stop() {
        if (this.mob.isSleeping()) {
            // 清除 OCCUPIED 并把她移动到床边安全站位
            this.mob.stopSleeping();
        }
        this.mob.setNoGravity(this.hadNoGravity);
        this.bedPos = null;
        this.repathCooldown = 0;
    }

    /** 用村庄 POI 索引找最近的可用床（统一换算为床头位置）。 */
    @Nullable
    private BlockPos findNearestBed() {
        if (!(this.mob.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getPoiManager()
                .getInRange(holder -> holder.is(PoiTypes.HOME),
                        this.mob.blockPosition(), BED_SEARCH_RADIUS,
                        PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos)
                .map(pos -> normalizeHeadPos(this.mob.level(), pos))
                .filter(pos -> pos != null && isBedAvailable(serverLevel, pos))
                .min(Comparator.comparingDouble(
                        pos -> pos.distSqr(this.mob.blockPosition())))
                .orElse(null);
    }

    /**
     * POI 可能登记在床的任意半块（床脚 / 床头都是 BedBlock），
     * 统一换算为床头坐标：床脚沿 FACING 方向再走一格。
     */
    @Nullable
    private static BlockPos normalizeHeadPos(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) {
            return null;
        }
        return state.getValue(BedBlock.PART) == BedPart.HEAD
                ? pos : pos.relative(state.getValue(BedBlock.FACING));
    }

    /**
     * 床是否可以睡：
     * <ol>
     *   <li>床头存在且未被占用（另一半床缺失 / 已被睡均拒绝）；</li>
     *   <li>没有其他生物正躺在床头格；</li>
     *   <li>不是在线玩家已绑定的出生点床（床头、床脚都比对）；</li>
     *   <li>床边有可站立空间，不会睡进被堵死的床里。</li>
     * </ol>
     */
    private boolean isBedAvailable(Level level, @Nullable BlockPos head) {
        if (head == null) {
            return false;
        }
        BlockState state = level.getBlockState(head);
        if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.OCCUPIED)
                || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(head),
                entity -> entity != this.mob && entity.isSleeping()).isEmpty()) {
            return false;
        }
        // 跳过在线玩家绑定的重生床：床的任意一半匹配即拒绝
        BlockPos foot = head.relative(state.getValue(BedBlock.FACING).getOpposite());
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            BlockPos respawn = player.getRespawnPosition();
            if (respawn != null && player.getRespawnDimension() == serverLevel.dimension()
                    && (respawn.equals(head) || respawn.equals(foot))) {
                return false;
            }
        }
        Direction facing = state.getValue(BedBlock.FACING);
        return BedBlock.findStandUpPosition(this.mob.getType(), serverLevel, head,
                facing, this.mob.getYRot()).isPresent();
    }
}
