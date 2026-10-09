package com.github.emberstar1201.enchantmentex.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 「意识暂时丧失」彩蛋行为。
 *
 * <p>触发规则：附近存在村民时，每隔一秒进行一次判定，每次有 5% 概率进入
 * 意识丧失状态；持续 10~20 秒后自行恢复，结束后进入五分钟冷却。</p>
 *
 * <p>行为表现：少女会把最近的村民当作目标并追着跑，村民基于原版对
 * {@code Monster} 的恐慌机制会四散逃跑；但本 Goal 只负责寻路接近和注视，
 * 绝不调用任何攻击逻辑。追上之后只会停在村民身边盯着看。</p>
 *
 * <p>行为隔离：本 Goal 占用 MOVE / LOOK / TARGET 三个标记且不可被打断，
 * 期间近战、弓箭、跟随主人等 Goal 全部无法运行，从调度层保证
 * 「只追不打」。目标选择器（TARGET 标记）也会整体暂停，其他目标
 * Goal 无法把目标从村民身上改走。</p>
 *
 * <p>仅对丧尸娘与溺尸娘生效；幸存者少女是正常人，显式排除。</p>
 */
public class GirlConfusionGoal extends Goal {
    /** 每次掷骰进入意识丧失状态的概率（5%）。 */
    private static final float TRIGGER_CHANCE = 0.05F;
    /** 掷骰间隔：20 tick = 1 秒。 */
    private static final int TRIGGER_CHECK_INTERVAL = 20;
    /** 触发所要求的村民搜索半径（格）。 */
    private static final int VILLAGER_SCAN_RADIUS = 16;
    /** 实体生成后的初始保护期：10~100 秒内不会触发，避免一刷怪就犯迷糊。 */
    private static final int INITIAL_DELAY_MIN = 200;
    private static final int INITIAL_DELAY_RANGE = 1800;
    /** 一次彩蛋结束后的冷却：5 分钟内不会再次触发。 */
    private static final int COOLDOWN_AFTER = 6000;
    /** 意识丧失持续时间：10~20 秒。 */
    private static final int DURATION_MIN = 200;
    private static final int DURATION_RANGE = 201;
    /** 追击移速倍率，与僵尸追村民的节奏接近。 */
    private static final double CHASE_SPEED = 1.1D;
    /** 追到 1.5 格以内就停下，只围观不动手。 */
    private static final double CATCH_DISTANCE_SQR = 2.25D;
    /** 重新规划路径的间隔（tick）。 */
    private static final int REPATH_INTERVAL = 10;
    /** 安抚铁傀儡的搜索半径。 */
    private static final int GOLEM_SCAN_RADIUS = 16;

    /**
     * 当前正处于意识丧失状态（或已掷骰成功、正在让路）的少女集合。
     * 使用弱引用集合：实体死亡或卸载后不阻止 GC，也无需手动清理。
     * Entity 未重写 equals/hashCode，按身份区分，复活 / 重载产生的新实体
     * 不会误命中旧实例。
     */
    private static final Set<Mob> CONFUSED = Collections.newSetFromMap(new WeakHashMap<>());

    private final Mob mob;
    /** 正在追的村民；丢失后会自动改追范围内最近的另一个村民。 */
    private Villager targetVillager;
    /** 本次意识丧失状态的结束时刻（实体 tickCount 基准）。 */
    private int confusionEnd;
    /** 冷却结束时刻，在此之前不掷骰。 */
    private int cooldownUntil;
    /** 下一次掷骰的最早时刻，用于把判定限制为每秒一次。 */
    private int nextCheckAt;
    /** 下一次重新规划路径的时刻。 */
    private int repathAt;
    /**
     * 上一 tick 掷骰成功、但同 tick 因标记被不可打断 Goal
     * 占用而无法启动。处于该状态时其他不可打断 Goal 应主动让路，
     * 下一 tick 本 Goal 正式启动。
     */
    private boolean yielding;

    public GirlConfusionGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
        // 构造发生在实体生成期间，此时 tickCount 为 0，直接用相对时刻即可。
        this.cooldownUntil = INITIAL_DELAY_MIN + mob.getRandom().nextInt(INITIAL_DELAY_RANGE);
    }

    /**
     * 指定实体是否处于意识丧失彩蛋状态（含让路中的瞬间）。
     * 供铁傀儡目标拦截事件查询。
     */
    public static boolean isConfused(@Nullable LivingEntity entity) {
        return entity instanceof Mob confusedMob && CONFUSED.contains(confusedMob);
    }

    @Override
    public boolean canUse() {
        if (this.mob.level().isClientSide || this.mob.isNoAi()) {
            cancelYielding();
            return false;
        }
        // 幸存者少女不参与亡灵彩蛋
        if (this.mob instanceof SurvivorGirlEntity) {
            cancelYielding();
            return false;
        }
        // 睡着或被命令坐下时不能突然冲出去；若正处于让路瞬间则取消本次触发
        if (this.mob.isSleeping()
                || (this.mob instanceof ZombieGirlEntity zombieGirl && zombieGirl.isOrderedToSit())) {
            cancelYielding();
            return false;
        }

        // 已激活：继续保持运行，由 canContinueToUse 决定何时结束
        if (CONFUSED.contains(this.mob)) {
            return true;
        }
        // 上一 tick 已掷骰成功，占用标记的不可打断 Goal 本 tick 已让路，正式启动
        if (this.yielding) {
            return true;
        }

        int now = this.mob.tickCount;
        if (now < this.cooldownUntil || now < this.nextCheckAt) {
            return false;
        }
        this.nextCheckAt = now + TRIGGER_CHECK_INTERVAL;

        // 前提：附近必须有村民才掷骰
        if (findNearestVillager() == null) {
            return false;
        }
        if (this.mob.getRandom().nextFloat() < TRIGGER_CHANCE) {
            // 本 tick 先不启动：已有不可打断 Goal 占用标记，需要一个 tick 的让路窗口，
            // 把实体登记到状态集合中，相关 Goal 的 canUse 检测到后会自行停止，
            // 下一 tick 本 Goal 即可拿到全部行为标记。
            CONFUSED.add(this.mob);
            this.yielding = true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob.level().isClientSide || this.mob.isNoAi() || this.mob.isSleeping()) {
            return false;
        }
        if (this.mob instanceof ZombieGirlEntity zombieGirl && zombieGirl.isOrderedToSit()) {
            return false;
        }
        if (this.mob.tickCount >= this.confusionEnd) {
            return false;
        }
        // 目标村民消失（死亡 / 传送 / 维度切换）时尝试换人，没人可追才结束
        if (!isValidVillager(this.targetVillager)) {
            this.targetVillager = findNearestVillager();
        }
        return this.targetVillager != null;
    }

    @Override
    public void start() {
        this.yielding = false;
        int duration = DURATION_MIN + this.mob.getRandom().nextInt(DURATION_RANGE);
        this.confusionEnd = this.mob.tickCount + duration;
        if (!isValidVillager(this.targetVillager)) {
            this.targetVillager = findNearestVillager();
        }
        if (this.targetVillager != null) {
            // 占用 TARGET 标记后其余目标 Goal 已停止，这里把目标设为村民。
            // 注意：本 Goal 只移动、不攻击；近战 / 弓箭 Goal 又被
            // MOVE / LOOK 标记锁住，村民绝对不会受到伤害。
            this.mob.setTarget(this.targetVillager);
        }
        this.repathAt = 0;
        calmNearbyGolems();
    }

    @Override
    public void tick() {
        // 中途丢失目标村民就立刻换人；附近完全没有村民时等待 canContinueToUse 结束
        if (!isValidVillager(this.targetVillager)) {
            this.targetVillager = findNearestVillager();
            if (this.targetVillager == null) {
                return;
            }
        }
        if (this.mob.getTarget() != this.targetVillager) {
            this.mob.setTarget(this.targetVillager);
        }

        double distanceSqr = this.mob.distanceToSqr(this.targetVillager);
        if (distanceSqr > CATCH_DISTANCE_SQR) {
            // 追击中：周期性重新规划路径，村民受恐慌影响会持续逃跑
            if (this.mob.tickCount >= this.repathAt) {
                this.mob.getNavigation().moveTo(this.targetVillager, CHASE_SPEED);
                this.repathAt = this.mob.tickCount + REPATH_INTERVAL;
            }
        } else {
            // 追上了：停住脚步，只是看着，绝不出手
            this.mob.getNavigation().stop();
        }
        this.mob.getLookControl().setLookAt(this.targetVillager, 30.0F, 30.0F);

        // 定期让附近已经举拳的铁傀儡放下仇恨（目标变更事件只能阻止新锁定，
        // 彩蛋开始前已锁定她的铁傀儡需要在这里主动清除）
        if (this.mob.tickCount % 20 == 0) {
            calmNearbyGolems();
        }
    }

    @Override
    public void stop() {
        CONFUSED.remove(this.mob);
        this.yielding = false;
        this.cooldownUntil = this.mob.tickCount + COOLDOWN_AFTER;
        this.targetVillager = null;
        this.mob.setTarget(null);
        this.mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        // 追击必须每 tick 更新，不能被 GoalSelector 的隔 tick 优化拖慢
        return true;
    }

    @Override
    public boolean isInterruptable() {
        // 意识丧失期间攻击、跟随、睡眠赶路等任何 Goal 都不能打断她
        return false;
    }

    /** 寻找范围内距离最近的存活村民（包含小村民），没有时返回 null。 */
    @Nullable
    private Villager findNearestVillager() {
        AABB searchBox = this.mob.getBoundingBox().inflate(VILLAGER_SCAN_RADIUS, 4.0D, VILLAGER_SCAN_RADIUS);
        List<Villager> villagers = this.mob.level().getEntitiesOfClass(
                Villager.class, searchBox, villager -> villager.isAlive() && !villager.isRemoved());
        return villagers.stream()
                .min(Comparator.comparingDouble(this.mob::distanceToSqr))
                .orElse(null);
    }

    private boolean isValidVillager(@Nullable Villager villager) {
        return villager != null && villager.isAlive() && !villager.isRemoved();
    }

    /**
     * 取消尚未正式启动的触发（让路窗口内实体突然睡着 / 坐下 / 失去 AI）。
     * 这种情况下本 Goal 从未 start，不会收到 stop() 回调，必须手动清理状态集合，
     * 否则铁傀儡保护与让路状态会永久误判。
     */
    private void cancelYielding() {
        if (this.yielding) {
            this.yielding = false;
            CONFUSED.remove(this.mob);
        }
    }

    /**
     * 把附近铁傀儡当前针对自己的攻击目标清空。
     * 铁傀儡有「主动搜索怪物」「保卫受袭村民」「被打报复」三条锁定路径，
     * 新目标的产生由 {@code GirlConfusionHandler} 拦截；这里负责清除
     * 彩蛋开始前已经存在的旧目标。
     */
    private void calmNearbyGolems() {
        AABB searchBox = this.mob.getBoundingBox().inflate(GOLEM_SCAN_RADIUS, 4.0D, GOLEM_SCAN_RADIUS);
        for (IronGolem golem : this.mob.level().getEntitiesOfClass(IronGolem.class, searchBox)) {
            if (golem.getTarget() == this.mob) {
                golem.setTarget(null);
            }
        }
    }
}
