package com.github.emberstar1201.enchantmentex.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import com.github.emberstar1201.enchantmentex.entity.menu.ZombieGirlInventoryMenu;
import com.github.emberstar1201.enchantmentex.item.UnownedStardustItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import com.github.emberstar1201.enchantmentex.sound.ModSounds;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import java.util.UUID;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * 丧尸娘：友好亡灵生物。
 *
 * 设计要点（为什么继承 Zombie 而不是 TamableAnimal）：
 *   1. 继承 Zombie 后保留亡灵身份（MobType.UNDEAD：免疫中毒、受亡灵杀手额外伤害）；
 *      但重写 isInvertedHealAndHarm() 返回 false，治疗/伤害瞬伤药水按普通生物结算——
 *      治疗药水正常回血，伤害药水正常造成伤害，不再反转。
 *   2. 环境音、受伤音、死亡音使用与溺尸娘共用的 husk_girl 自定义语音。
 *   3. 驯服 / 跟随 / 坐下状态由本类自行实现（Zombie 不是 TamableAnimal）。
 *   4. 通过自定义属性把「僵尸召唤增援」概率清零，玩家攻击她时不会刷出僵尸。
 */
public class ZombieGirlEntity extends Zombie implements FriendlyGirlInventory, RangedAttackMob {
    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("6b8c9bb8-8fcb-4d0d-9f68-9c4f7b4e4f31");
    // MULTIPLY_TOTAL 运算：成人 0.1 → 最终为原版僵尸的 1.1 倍
    private static final double SPEED_MULTIPLIER_ADULT = 0.1D;
    // MULTIPLY_TOTAL 运算：幼年 0.2 → 最终为小僵尸的 1.2 倍
    private static final double SPEED_MULTIPLIER_BABY = 0.2D;
    private static final String SPEED_MODIFIER_NAME = "zombie_girl_base_speed";

    /** 是否已被驯服。 */
    private boolean tamed;
    /** 主人 UUID；未驯服时为 null。 */
    private UUID ownerUuid;
    /** 是否被主人命令坐下。 */
    private boolean sitting;
    /**
     * 驯服丧尸娘的随身背包（64 格，8 × 8 布局）。
     * 菜单直接读写这个容器，关闭界面时不需要二次同步；实体存档时写入 MeatInventory。
     * 注意：NBT 键名 {@code MeatInventory} 为历史命名，保留不改以兼容旧存档；
     * 现在背包可放入任意物品（生肉仍会被自动取食，星星放入即可获得被动效果）。
     */
    private static final int BACKPACK_SIZE = 64;
    private final SimpleContainer meatInventory = new SimpleContainer(BACKPACK_SIZE);

    /**
     * 皮肤变种是否已被真正分配过（自然生成 finalizeSpawn / 读档 Variant / 治愈转换）。
     * 为什么需要这个标记：{@code /summon}、{@code /ee girl summon} 等内存新建的实体
     * 既不会走 finalizeSpawn，NBT 里也没有 Variant 键，变体编号会一直停在默认 0，
     * 幼年体没有刷怪蛋、几乎都靠这些途径产生，所以永远显示默认 zombie_girl.png。
     * 该字段不落盘：首个服务端 tick 发现仍未分配时补一次随机，
     * 写入 SynchedEntityData 后会自动同步给追踪客户端。合法的 0 号皮肤因为已被标记，
     * 不会在下次 tick 被重新随机。
     */
    private boolean variantAssigned;
    /**
     * 皮肤变种的网络同步数据：0~9 对应 zombie_girl.png 到 zombie_girl_9.png。
     * 为什么不能用普通字段：finalizeSpawn 只在服务端执行，普通字段不会同步，
     * 客户端的 variant 永远是默认值 0，渲染器就只能拿到 zombie_girl.png。
     * SynchedEntityData 会在生成 / 数据变化时自动把值同步给所有追踪的客户端。
     */
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(ZombieGirlEntity.class, EntityDataSerializers.INT);

    /**
     * 材质贴图路径必须与 assets/enchantment_expansion/textures/entity 下的文件名完全一致。
     * 变种 0 使用默认贴图 zombie_girl.png，其余按编号使用 zombie_girl_N.png。
     * 新增皮肤时只需把最大编号写进 MAX_VARIANT（贴图文件需提前放入正确路径）。
     */
    private static final int MAX_VARIANT = 18;

    /** 预生成的变种贴图表（下标即 variant 值），避免渲染时反复构造 ResourceLocation。 */
    private static final List<ResourceLocation> VARIANT_TEXTURES = new ArrayList<>();

    static {
        VARIANT_TEXTURES.add(new ResourceLocation("enchantment_expansion",
                "textures/entity/zombie_girl.png"));
        for (int i = 1; i <= MAX_VARIANT; i++) {
            VARIANT_TEXTURES.add(new ResourceLocation("enchantment_expansion",
                    "textures/entity/zombie_girl_" + i + ".png"));
        }
    }

    // ====================================================================
    // 闲时对话系统：台词翻译键与冷却常量（文本见 lang 文件，支持四语言）
    // ====================================================================
    /** 常态闲聊台词翻译键池（随机抽取，不固定顺序）。 */
    private static final String[] IDLE_CHAT_KEYS = {
            "chat.enchantment_expansion.zombie_girl.idle.0",
            "chat.enchantment_expansion.zombie_girl.idle.1",
            "chat.enchantment_expansion.zombie_girl.idle.2",
            "chat.enchantment_expansion.zombie_girl.idle.3",
            "chat.enchantment_expansion.zombie_girl.idle.4",
            "chat.enchantment_expansion.zombie_girl.idle.5",
            "chat.enchantment_expansion.zombie_girl.idle.6",
            "chat.enchantment_expansion.zombie_girl.idle.7",
            "chat.enchantment_expansion.zombie_girl.idle.8",
            "chat.enchantment_expansion.zombie_girl.idle.9"
    };
    /** 靠近正常村庄（有存活村民）时的台词翻译键池（随机抽取）。 */
    private static final String[] VILLAGE_CHAT_KEYS = {
            "chat.enchantment_expansion.zombie_girl.village.0",
            "chat.enchantment_expansion.zombie_girl.village.1",
            "chat.enchantment_expansion.zombie_girl.village.2"
    };
    /** 靠近僵尸村庄（全是僵尸村民）时的台词翻译键池（随机抽取）。 */
    private static final String[] ZOMBIE_VILLAGE_CHAT_KEYS = {
            "chat.enchantment_expansion.zombie_girl.zombie_village.0",
            "chat.enchantment_expansion.zombie_girl.zombie_village.1",
            "chat.enchantment_expansion.zombie_girl.zombie_village.2"
    };
    /** 受伤时的台词翻译键池（随机抽取）。 */
    private static final String[] HURT_CHAT_KEYS = {
            "chat.enchantment_expansion.zombie_girl.hurt.0",
            "chat.enchantment_expansion.zombie_girl.hurt.1",
            "chat.enchantment_expansion.zombie_girl.hurt.2",
            "chat.enchantment_expansion.zombie_girl.hurt.3",
            "chat.enchantment_expansion.zombie_girl.hurt.4"
    };
    /** 死亡遗言翻译键池（随机抽取）。 */
    private static final String[] DEATH_CHAT_KEYS = {
            "chat.enchantment_expansion.zombie_girl.death.0",
            "chat.enchantment_expansion.zombie_girl.death.1",
            "chat.enchantment_expansion.zombie_girl.death.2"
    };
    /** 与主人一同击败亡灵生物后的台词池（60 秒冷却）。 */
    private static final String[] COMBAT_CHAT_KEYS = {
            "chat.enchantment_expansion.zombie_girl.combat.0",
            "chat.enchantment_expansion.zombie_girl.combat.1",
            "chat.enchantment_expansion.zombie_girl.combat.2"
    };
    /** 日出台词池。 */
    private static final String[] TIME_SUNRISE_CHAT_KEYS = {
            "chat.enchantment_expansion.zombie_girl.time.sunrise.0",
            "chat.enchantment_expansion.zombie_girl.time.sunrise.1"
    };
    /** 中午台词池。 */
    private static final String[] TIME_NOON_CHAT_KEYS = {
            "chat.enchantment_expansion.zombie_girl.time.noon.0",
            "chat.enchantment_expansion.zombie_girl.time.noon.1"
    };
    /** 夜晚台词池。 */
    private static final String[] TIME_NIGHT_CHAT_KEYS = {
            "chat.enchantment_expansion.zombie_girl.time.night.0",
            "chat.enchantment_expansion.zombie_girl.time.night.1"
    };
    /** 午夜台词池。 */
    private static final String[] TIME_MIDNIGHT_CHAT_KEYS = {
            "chat.enchantment_expansion.zombie_girl.time.midnight.0",
            "chat.enchantment_expansion.zombie_girl.time.midnight.1"
    };
    /** 意识侵蚀轻度台词池。 */
    private static final String[] CORRUPTION_MILD_CHAT_KEYS = {
            "chat.enchantment_expansion.zombie_girl.corruption.mild.0",
            "chat.enchantment_expansion.zombie_girl.corruption.mild.1"
    };
    /** 意识侵蚀中度台词池。 */
    private static final String[] CORRUPTION_MODERATE_CHAT_KEYS = {
            "chat.enchantment_expansion.zombie_girl.corruption.moderate.0",
            "chat.enchantment_expansion.zombie_girl.corruption.moderate.1"
    };
    /** 意识侵蚀重度台词池。 */
    private static final String[] CORRUPTION_SEVERE_CHAT_KEYS = {
            "chat.enchantment_expansion.zombie_girl.corruption.severe.0",
            "chat.enchantment_expansion.zombie_girl.corruption.severe.1"
    };

    /**
     * 时间感应对话的四个时段（供子类按时段提供各自的台词池）。
     * 判定窗口与 tickTimeChat 中的世界时间区间一一对应。
     */
    protected enum TimeChatPhase {
        /** 日出（23000~1000）。 */
        SUNRISE,
        /** 正午（5000~7000）。 */
        NOON,
        /** 夜晚（13000~15000）。 */
        NIGHT,
        /** 午夜（17000~19000）。 */
        MIDNIGHT
    }

    // ====================================================================
    // 对话台词池钩子：丧尸娘使用上方默认池；幸存者少女等子类覆写为各自的
    // 独立台词池。每个池子至少包含一条翻译键，由随机下标抽取。
    // ====================================================================

    /** 常态闲聊台词池（子类可覆写）。 */
    protected String[] getIdleChatKeys() {
        return IDLE_CHAT_KEYS;
    }

    /** 受伤吐槽台词池（子类可覆写）。 */
    protected String[] getHurtChatKeys() {
        return HURT_CHAT_KEYS;
    }

    /** 死亡遗言台词池（子类可覆写）。 */
    protected String[] getDeathChatKeys() {
        return DEATH_CHAT_KEYS;
    }

    /**
     * 指定时段的时间感应台词池（子类可覆写）。
     * @param phase 已命中的时段，不为 null
     */
    protected String[] getTimeChatKeys(TimeChatPhase phase) {
        return switch (phase) {
            case SUNRISE -> TIME_SUNRISE_CHAT_KEYS;
            case NOON -> TIME_NOON_CHAT_KEYS;
            case NIGHT -> TIME_NIGHT_CHAT_KEYS;
            case MIDNIGHT -> TIME_MIDNIGHT_CHAT_KEYS;
        };
    }

    /**
     * 受伤吐槽冷却（tick）。丧尸娘固定 30 秒；
     * 幸存者少女覆写为 10 分钟，与其独立台词的统一冷却保持一致。
     */
    protected int getHurtChatCooldown() {
        return HURT_CHAT_COOLDOWN;
    }

    /**
     * 是否启用丧尸娘专属对话事件：村庄侦查、战斗胜利、意识侵蚀彩蛋。
     * 幸存者少女已变回人类，没有亡灵低语与意识侵蚀体验，覆写为 false，
     * 只保留通用的闲时 / 时间感应 / 受伤 / 死亡四类对话。
     */
    protected boolean enableZombieSpecificChats() {
        return true;
    }

    /**
     * 当前是否允许触发常态闲聊。睡着时不闲聊，
     * 避免她躺在床上时还弹出闲聊文本（幸存者少女经继承同规则）。
     */
    protected boolean canIdleChatNow() {
        return !this.isSleeping();
    }

    /** 受伤吐槽冷却：30 秒（600 tick），防止火焰等高频伤害刷屏。 */
    private static final int HURT_CHAT_COOLDOWN = 600;
    /** 受伤吐槽冷却截止时刻（tick）。 */
    private long hurtChatCooldownUntil;

    /** 常态闲聊间隔：30 秒（600 tick）。 */
    private static final int IDLE_CHAT_INTERVAL = 600;
    /** 村庄对话冷却：30 秒（600 tick）。 */
    private static final int VILLAGE_CHAT_COOLDOWN = 600;
    /** 主人需在实体 32 格内才能收到闲聊。 */
    private static final double OWNER_CHAT_RANGE_SQR = 32.0D * 32.0D;
    /** 村庄 POI 检测半径：128 格。 */
    private static final int VILLAGE_POI_RANGE = 128;
    /** 视为"同一村庄"的最大中心偏移，超过则当作进入了另一个村庄。 */
    private static final double SAME_VILLAGE_DIST_SQR = 64.0D * 64.0D;

    /** 下一次允许常态闲聊的游戏时刻（tick）。 */
    private long nextIdleChatTick;
    /** 村庄对话冷却截止时刻（tick）。 */
    private long villageChatCooldownUntil;
    /** 上次吃生肉回血的时刻（tick），用于判断"不在啃生肉"。子类需要访问以扩展进食逻辑。 */
    protected long lastMealTick = -10000L;
    /** 战斗胜利对话冷却截止时刻（tick）。 */
    private long combatChatCooldownUntil;
    /** 上次触发时间感应对话的时刻（tick），防止同一时段重复触发。 */
    private long lastTimeChatTick = -600L;
    /** 意识侵蚀彩蛋：上次触发时刻（tick）。 */
    private long lastCorruptionChatTick = -600L;
    /** 意识侵蚀彩蛋：无敌人持续时间（tick）。 */
    private int noEnemyTicks;
    /** 意识侵蚀彩蛋：定住状态截止时刻（tick）。 */
    private long freezeUntilTick;
    /** 意识侵蚀彩蛋：是否已发送过本次台词（防止定住期间重复刷屏）。 */
    private boolean corruptionChatSent;
    /**
     * 当前所处村庄的中心 POI；离开村庄范围后重置为 null。
     * 同一村庄只触发一次对话，彻底离开后重新进入才会再次触发。
     */
    @Nullable
    private BlockPos lastVillagePos;

    public ZombieGirlEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        // 允许跟随 / 睡觉寻路穿过木门（配合 OpenDoorGoal 实际开关门），
        // 夜晚能自己开门走进玩家庇护所里的床；可涉水浮起避免卡水沟。
        if (this.getNavigation() instanceof GroundPathNavigation groundNavigation) {
            groundNavigation.setCanOpenDoors(true);
            groundNavigation.setCanFloat(true);
        }
    }

    @Override
    protected boolean supportsBreakDoorGoal() {
        return false;
    }

    /**
     * 药水效果不再按亡灵反转：治疗药水正常回血，伤害药水正常受伤。
     * 原版亡灵（僵尸/骷髅）通过重写此方法返回 true 实现反转，这里改回正常生物逻辑。
     */
    @Override
    public boolean isInvertedHealAndHarm() {
        return false;
    }

    // ====================================================================
    // 语音：丧尸娘与溺尸娘共用一套 husk_girl 音频
    //   闲置音播放时由 sounds.json 在 idle_1/2/3 中随机三选一，
    //   受伤音在 hust_1/2 中随机二选一，死亡音固定为 farewell_1。
    // ====================================================================
    @Override
    protected SoundEvent getAmbientSound() {
        // 睡着时不播放闲置音（与原版村民一致；幸存者少女同样继承此规则）
        return this.isSleeping() ? null : ModSounds.HUSK_GIRL_IDLE.get();
    }

    /**
     * 她继承自 Monster，原版怪物会阻止玩家休息（入睡时提示
     * 「周围有怪物在游荡」）。已驯服的友好娘睡在身边时玩家必须能正常上床，
     * 因此显式关闭该判定（幸存者少女经继承同样生效）。
     */
    @Override
    public boolean isPreventingPlayerRest(Player player) {
        return false;
    }

    /** 睡着后不可被推动：防止路过的生物把她一点点挤下床。 */
    @Override
    public boolean isPushable() {
        return !this.isSleeping() && super.isPushable();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.HUSK_GIRL_HUST.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.HUSK_GIRL_FAREWELL.get();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // 兜底分配皮肤变种：/summon、/ee girl summon 等未经 finalizeSpawn、
        // 也不带 Variant NBT 的实体（幼年体主要靠这些途径产生）在首个服务端 tick 补随机。
        // 幸存者少女的 aiStep 会先走这里，随机范围 0~18 与 human_girl 19 张贴图一致。
        if (!this.level().isClientSide && !this.variantAssigned) {
            setVariant(this.random.nextInt(MAX_VARIANT + 1));
        }
        syncMovementSpeed();
        if (!this.level().isClientSide && this.tamed
                && this.tickCount % 40L == 0L
                && this.getHealth() < this.getMaxHealth()) {
            consumeMeatForHealing();
        }
        // 无攻击目标时在水中自动上浮到水面（有目标时允许下潜追敌）
        if (this.isInWater() && this.getTarget() == null && this.getDeltaMovement().y < 0.05D) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D)
                    .add(0.0D, 0.08D, 0.0D));
        }
        // 闲时对话系统（仅服务端）
        if (!this.level().isClientSide) {
            tickIdleChat();
            // POI 查询与范围扫描有开销，每秒（20 tick）检测一次即可
            if (this.tickCount % 20 == 0) {
                // 时间感应是通用对话（丧尸娘 / 幸存者少女各自有独立台词）
                tickTimeChat();
                // 村庄侦查 / 战斗胜利 / 意识侵蚀是丧尸娘专属，
                // 幸存者少女（人类形态）覆写 enableZombieSpecificChats 关闭
                if (enableZombieSpecificChats()) {
                    tickVillageChat();
                    tickCombatChat();
                    tickCorruptionChat();
                }
            }
            // 意识侵蚀定住效果：冻结期间禁止移动和攻击（仅意识侵蚀启用时可能产生冻结）
            if (this.tickCount < this.freezeUntilTick) {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
                this.getNavigation().stop();
                this.setTarget(null);
            }
        }
    }

    private void syncMovementSpeed() {
        var attribute = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }
        // 幼年体型用更高的倍率，体型变化（长大）时更新修饰符
        double amount = this.isBaby() ? SPEED_MULTIPLIER_BABY : SPEED_MULTIPLIER_ADULT;
        AttributeModifier existing = attribute.getModifier(SPEED_MODIFIER_UUID);
        if (existing != null) {
            if (existing.getAmount() == amount) {
                return;
            }
            attribute.removeModifier(SPEED_MODIFIER_UUID);
        }
        attribute.addPermanentModifier(new AttributeModifier(
                SPEED_MODIFIER_UUID, SPEED_MODIFIER_NAME, amount,
                Operation.MULTIPLY_TOTAL));
    }

    // ====================================================================
    // 闲时对话：常态闲聊（每 30 秒随机一条）
    // ====================================================================
    private void tickIdleChat() {
        if (this.tickCount < this.nextIdleChatTick) return;
        // 冷却到期即重置下一轮，即使本轮条件不满足也不会堆积刷屏
        this.nextIdleChatTick = this.tickCount + IDLE_CHAT_INTERVAL;

        if (!this.tamed || this.ownerUuid == null) return;
        // 和平状态：不在战斗、未坐下、最近 10 秒内没有啃生肉
        if (this.getTarget() != null || this.sitting) return;
        if (this.tickCount - this.lastMealTick < 200L) return;
        // 子类钩子（如幸存者少女睡着时不闲聊）
        if (!canIdleChatNow()) return;

        ServerPlayer owner = getOnlineOwner();
        if (owner == null || owner.distanceToSqr(this) > OWNER_CHAT_RANGE_SQR) return;

        String[] idlePool = getIdleChatKeys();
        sendChatToOwner(idlePool[this.random.nextInt(idlePool.length)]);
    }

    // ====================================================================
    // 闲时对话：靠近村庄时触发（同一村庄一次，冷却 30 秒）
    // ====================================================================
    private void tickVillageChat() {
        if (!this.tamed || this.ownerUuid == null) return;
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        // 以"床"类 POI 作为村庄标志，取 128 格内最近的一个
        BlockPos villageCenter = serverLevel.getPoiManager()
                .getInRange(holder -> holder.is(PoiTypes.HOME), this.blockPosition(),
                        VILLAGE_POI_RANGE, PoiManager.Occupancy.ANY)
                .map(net.minecraft.world.entity.ai.village.poi.PoiRecord::getPos)
                .min(Comparator.comparingDouble(pos -> pos.distSqr(this.blockPosition())))
                .orElse(null);

        if (villageCenter == null) {
            // 已离开村庄范围，重置状态以便下次重新进入时再次触发
            this.lastVillagePos = null;
            return;
        }

        // 同一村庄（中心偏移 64 格内）不重复触发
        if (this.lastVillagePos != null
                && villageCenter.distSqr(this.lastVillagePos) < SAME_VILLAGE_DIST_SQR) {
            return;
        }
        this.lastVillagePos = villageCenter;

        if (this.tickCount < this.villageChatCooldownUntil) return;

        ServerPlayer owner = getOnlineOwner();
        if (owner == null || owner.distanceToSqr(this) > OWNER_CHAT_RANGE_SQR) return;

        // 村庄类型判定：POI 中心 32 格内有存活村民 → 正常村庄；
        // 没有村民但有僵尸村民 → 僵尸村庄；两者皆无则无法判断，不触发
        net.minecraft.world.phys.AABB villageBox = new net.minecraft.world.phys.AABB(villageCenter)
                .inflate(32.0D);
        boolean hasVillager = !serverLevel.getEntitiesOfClass(
                net.minecraft.world.entity.npc.Villager.class, villageBox).isEmpty();
        boolean hasZombieVillager = !serverLevel.getEntitiesOfClass(
                net.minecraft.world.entity.monster.ZombieVillager.class, villageBox).isEmpty();

        String[] pool = hasVillager ? VILLAGE_CHAT_KEYS
                : hasZombieVillager ? ZOMBIE_VILLAGE_CHAT_KEYS : null;
        if (pool == null) return;

        sendChatToOwner(pool[this.random.nextInt(pool.length)]);
        this.villageChatCooldownUntil = this.tickCount + VILLAGE_CHAT_COOLDOWN;
    }

    // ====================================================================
    // 战斗胜利对话：与主人一同击败亡灵生物后触发（60 秒冷却）
    // ====================================================================
    private void tickCombatChat() {
        if (!this.tamed || this.ownerUuid == null) return;
        if (this.tickCount < this.combatChatCooldownUntil) return;

        // 检查 16 格内是否有刚死亡的亡灵生物（最后攻击者是主人或丧尸娘自己）
        net.minecraft.world.phys.AABB area = this.getBoundingBox().inflate(16.0D);
        java.util.List<Monster> nearbyUndead = this.level().getEntitiesOfClass(Monster.class, area,
                entity -> entity.getMobType() == MobType.UNDEAD
                        && entity.isDeadOrDying()
                        && entity.getLastHurtByMob() != null
                        && (entity.getLastHurtByMob().getUUID().equals(this.ownerUuid)
                            || entity.getLastHurtByMob() == this));
        if (nearbyUndead.isEmpty()) return;

        ServerPlayer owner = getOnlineOwner();
        if (owner == null || owner.distanceToSqr(this) > OWNER_CHAT_RANGE_SQR) return;

        sendChatToOwner(COMBAT_CHAT_KEYS[this.random.nextInt(COMBAT_CHAT_KEYS.length)]);
        this.combatChatCooldownUntil = this.tickCount + IDLE_CHAT_INTERVAL; // 与闲聊同冷却
    }

    // ====================================================================
    // 时间感应对话：日出 / 正午 / 夜晚 / 午夜（各时段共享 30 秒冷却）
    // ====================================================================
    private void tickTimeChat() {
        if (!this.tamed || this.ownerUuid == null) return;
        // 同一时段 30 秒内不重复触发（冷却也会被星尘收容冻结）
        if (this.tickCount - this.lastTimeChatTick < 600L) return;

        ServerPlayer owner = getOnlineOwner();
        if (owner == null || owner.distanceToSqr(this) > OWNER_CHAT_RANGE_SQR) return;

        long dayTime = this.level().getDayTime() % 24000L;
        TimeChatPhase phase = null;

        // 日出 23000~1000，正午 5000~7000，夜晚 13000~15000，午夜 17000~19000
        if (dayTime >= 23000L || dayTime <= 1000L) {
            phase = TimeChatPhase.SUNRISE;
        } else if (dayTime >= 5000L && dayTime <= 7000L) {
            phase = TimeChatPhase.NOON;
        } else if (dayTime >= 13000L && dayTime <= 15000L) {
            phase = TimeChatPhase.NIGHT;
        } else if (dayTime >= 17000L && dayTime <= 19000L) {
            phase = TimeChatPhase.MIDNIGHT;
        }
        if (phase == null) return;

        // 台词池由子类按实体类型提供（丧尸娘 / 幸存者少女各自独立）
        String[] pool = getTimeChatKeys(phase);
        sendChatToOwner(pool[this.random.nextInt(pool.length)]);
        this.lastTimeChatTick = this.tickCount;
    }

    // ====================================================================
    // 意识侵蚀彩蛋：无敌人 10 秒后原地定住 3 秒 + 台词
    // ====================================================================
    private void tickCorruptionChat() {
        if (!this.tamed || this.ownerUuid == null) return;

        // 已有目标或定住中则重置计数
        if (this.getTarget() != null || this.tickCount < this.freezeUntilTick) {
            this.noEnemyTicks = 0;
            return;
        }

        // 注意：本方法每 20 tick 才被调用一次，因此每次按 20 tick 累加
        this.noEnemyTicks += 20;
        if (this.noEnemyTicks < 200) return; // 10 秒 = 200 tick

        // 30 秒冷却
        if (this.tickCount - this.lastCorruptionChatTick < 600L) return;

        ServerPlayer owner = getOnlineOwner();
        if (owner == null || owner.distanceToSqr(this) > OWNER_CHAT_RANGE_SQR) return;

        // 随机选择侵蚀程度：轻度 50%、中度 30%、重度 20%
        float roll = this.random.nextFloat();
        String[] pool;
        if (roll < 0.5F) {
            pool = CORRUPTION_MILD_CHAT_KEYS;
        } else if (roll < 0.8F) {
            pool = CORRUPTION_MODERATE_CHAT_KEYS;
        } else {
            pool = CORRUPTION_SEVERE_CHAT_KEYS;
        }

        sendChatToOwner(pool[this.random.nextInt(pool.length)]);
        this.lastCorruptionChatTick = this.tickCount;
        this.noEnemyTicks = 0;
        this.freezeUntilTick = this.tickCount + 60; // 定住 3 秒（60 tick）
        this.corruptionChatSent = true;
    }

    /** 获取在线的主人（ServerPlayer），不在线返回 null。 */
    @Nullable
    private ServerPlayer getOnlineOwner() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return null;
        return serverLevel.getServer().getPlayerList().getPlayer(this.ownerUuid);
    }

    /**
     * 向主人的聊天栏发送带统一前缀的翻译键台词。
     * 前缀本身是硬编码的“丧尸娘”标识（固定，不翻译），
     * 后面的台词内容走 translatable，客户端自动切换语言。
     */
    private void sendChatToOwner(String translationKey) {
        ServerPlayer owner = getOnlineOwner();
        if (owner != null) {
            // 前缀用 literal（固定文本），台词用 translatable（四语言）
            owner.sendSystemMessage(Component.literal(getChatPrefix())
                    .append(Component.translatable(translationKey)));
        }
    }

    /** 受伤时触发：冷却时长由子类决定（丧尸娘 30 秒，幸存者少女 10 分钟），火焰等高频伤害不会刷屏。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && this.tamed && this.ownerUuid != null
                && !this.level().isClientSide
                && this.tickCount >= this.hurtChatCooldownUntil) {
            this.hurtChatCooldownUntil = this.tickCount + getHurtChatCooldown();
            String[] hurtPool = getHurtChatKeys();
            sendChatToOwner(hurtPool[this.random.nextInt(hurtPool.length)]);
        }
        return result;
    }

    /** 死亡时触发：遗言不受 32 格距离限制，主人只要在线就能收到。 */
    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide && this.tamed && this.ownerUuid != null) {
            String[] deathPool = getDeathChatKeys();
            sendChatToOwner(deathPool[this.random.nextInt(deathPool.length)]);
        }
        // 随身背包里的全部物品掉落在脚下：背包现在可放任意物品（含星星等贵重物），
        // 绝不能随尸体消失；装备槽由原版掉落系统按 100% 掉落率处理，不在这里重复。
        if (!this.level().isClientSide) {
            for (int slot = 0; slot < this.meatInventory.getContainerSize(); slot++) {
                ItemStack backpackStack = this.meatInventory.getItem(slot);
                if (!backpackStack.isEmpty()) {
                    this.spawnAtLocation(backpackStack, 0.0F);
                    this.meatInventory.setItem(slot, ItemStack.EMPTY);
                }
            }
        }
        super.die(source);
    }



    // ====================================================================
    // 远程攻击：RangedAttackMob 接口实现（骷髅射箭逻辑适配）
    // ====================================================================
    /**
     * 弓的远程攻击实现：
     * 1. 从主手弓的 NBT 读取无限附魔，决定箭是否可回收；
     * 2. 用 ProjectileUtil.getMobArrow 生成箭实体，自动应用力量附魔；
     * 3. 手动应用火矢附魔（骷髅原版逻辑同款）；
     * 4. 射出的箭 owner 是丧尸娘自己，模组弓附魔通过
     *    EntityJoinLevelEvent 检查 owner 主手弓的附魔，自动生效。
     */
    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        ItemStack bow = this.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack arrowStack = new ItemStack(Items.ARROW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, arrowStack, distanceFactor);
        // 无限附魔：箭命中后不可回收
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, bow) > 0) {
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        }
        // 力量附魔：增加箭矢基础伤害
        int powerLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, bow);
        if (powerLevel > 0) {
            arrow.setBaseDamage(arrow.getBaseDamage() + powerLevel * 0.5D + 0.5D);
        }
        // 火矢附魔：点燃目标
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, bow) > 0) {
            arrow.setSecondsOnFire(100);
        }
        // 计算射击方向（带一点随机散布）
        double dx = target.getX() - this.getX();
        double dy = target.getY(0.3333333333333333D) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontalDist * 0.2D, dz, 1.6F, (float)(14 - 4));
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F,
                1.0F / (this.random.nextFloat() * 0.4F + 0.8F));
        this.level().addFreshEntity(arrow);
    }

    /** 从专属背包中取一块生肉回血；每块恢复 2 颗心，受最大生命值上限自动截断。 */
    protected void consumeMeatForHealing() {
        for (int slot = 0; slot < this.meatInventory.getContainerSize(); slot++) {
            ItemStack meat = this.meatInventory.getItem(slot);
            if (isRawMeat(meat)) {
                meat.shrink(1);
                this.heal(4.0F);
                // 专用啃肉音效（husk_girl_eat_1.ogg），取代原版进食声
                this.playSound(ModSounds.HUSK_GIRL_EAT.get(), 1.0F, 1.25F);
                this.lastMealTick = this.tickCount;
                return;
            }
        }
    }

    /** 供容器菜单直接访问随身背包（64 格，方法名为历史命名）。 */
    public SimpleContainer getMeatInventory() {
        return this.meatInventory;
    }

    /**
     * 随身背包中是否持有指定物品（星星被动效果判定用）。
     * 放在背包任意格子里的终界之星等物品都算「携带」，与手持等效；
     * 丧尸娘数量很少，每 tick 扫描 64 格的开销可以忽略。
     */
    public boolean hasItemInBackpack(net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < this.meatInventory.getContainerSize(); slot++) {
            ItemStack stack = this.meatInventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    /** 遍历背包物品（供星星 lore 刷新等需要拿到真实 ItemStack 的场景使用）。 */
    public java.util.List<ItemStack> getBackpackItems() {
        java.util.List<ItemStack> items = new java.util.ArrayList<>(this.meatInventory.getContainerSize());
        for (int slot = 0; slot < this.meatInventory.getContainerSize(); slot++) {
            items.add(this.meatInventory.getItem(slot));
        }
        return items;
    }

    /** 注册需要自动网络同步的数据；父类构造期间会调用，此时静态 DATA_VARIANT 已初始化。 */
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_VARIANT, 0);
    }

    /** 自然生成或刷怪蛋出生时，随机分配皮肤变种。 */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                         MobSpawnType reason, @Nullable SpawnGroupData spawnData,
                                         @Nullable CompoundTag dataTag) {
        // 共有 MAX_VARIANT + 1 张贴图（0~MAX_VARIANT），写入同步数据后客户端渲染器才能拿到对应变种
        this.entityData.set(DATA_VARIANT, this.random.nextInt(MAX_VARIANT + 1));
        this.variantAssigned = true;
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    /** 获取材质变种贴图路径（查预生成表，越界时回落到默认贴图）。 */
    public ResourceLocation getVariantTexture() {
        int variant = clampVariant(this.entityData.get(DATA_VARIANT));
        return VARIANT_TEXTURES.get(variant);
    }

    /** 读取皮肤变种索引（子类渲染 / 转换时使用）。 */
    protected int getVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    /** 写入皮肤变种索引（自动夹紧到合法范围），治愈转换等场景使用。 */
    protected void setVariant(int variant) {
        this.entityData.set(DATA_VARIANT, clampVariant(variant));
        this.variantAssigned = true;
    }

    /** 把外部 NBT / 随机值夹紧到当前实体支持的皮肤索引范围。 */
    protected int clampVariant(int variant) {
        if (variant < 0 || variant >= VARIANT_TEXTURES.size()) {
            return 0;
        }
        return variant;
    }

    // ====================================================================
    // 一、行为 AI：完全替换 Zombie.registerGoals，不继承任何攻击玩家的目标
    // ====================================================================
    @Override
    protected void registerGoals() {
        // 意识暂时丧失彩蛋：附近有村民时低概率追着村民跑（只追不打）。
        // 优先级 0 高于一切战斗 / 睡眠赶路行为，且自身不可打断，
        // 保证触发期间近战、弓箭全部让路。幸存者少女继承本方法，
        // 但 Goal 内部会显式排除幸存者少女。
        this.goalSelector.addGoal(0, new GirlConfusionGoal(this));
        // 坐下时独占移动 / 跳跃 / 视线标记，阻止其它行为目标执行
        this.goalSelector.addGoal(1, new SitWhenOrderedGoal());
        // 寻路撞门时自动开关木门（该 Goal 不占移动标记，可与其它行为并行）
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        // 夜晚找床睡觉：与攻击同为优先级 2，但更早注册，会先拿到
        // MOVE/JUMP/LOOK 标记——夜里只要附近有床，她就直接去睡，无视周围怪物。
        this.goalSelector.addGoal(2, new GirlSleepGoal(this, this));
        // 采用溺尸的近战路径攻击节奏，但保留本实体自己的目标过滤
        this.goalSelector.addGoal(2, new ZombieGirlAttackGoal(1.0D, true));
        // 主手持有弓时启用远程攻击（骷髅同款 RangedBowAttackGoal）
        this.goalSelector.addGoal(2, new RangedBowAttackGoal<>(this, 1.0D, 20, 15.0F));
        // 水中没有目标时寻找真正可站立的岸边方块
        this.goalSelector.addGoal(3, new MoveToLandGoal());
        // 驯服后跟随主人
        this.goalSelector.addGoal(4, new FollowOwnerGoal());
        // 闲逛、观察玩家、随机张望
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // 受伤后保留攻击者，避免装备武器或被其它目标短暂打断后失去反击
        this.targetSelector.addGoal(0, new HostileUndeadHurtByTargetGoal());
        // 目标选择：驯服后优先协助主人攻击其击中的中立 / 友好生物
        this.targetSelector.addGoal(1, new OwnerHurtTargetGoal());
        // 天生仇恨「敌对类亡灵」（僵尸、骷髅等），无论是否驯服
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, Mob.class, 10, true, false, ZombieGirlEntity::isHostileUndead));
    }

    /** Zombie 构造器末尾会调用；刻意留空，阻止注册攻击玩家 / 铁傀儡的目标 AI。 */
    @Override
    protected void addBehaviourGoals() {
    }

    /**
     * 和平模式下不被清除。
     * Mob#checkDespawn 在和平难度每 tick 检查时调用本方法，返回 true 才会 discard；
     * Monster 默认返回 true，这里必须显式返回 false（之前回退时该覆写丢失过）。
     */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    // ====================================================================
    // 二、亡灵特性控制
    // ====================================================================

    /** 水下状态下是否允许追击当前攻击目标。 */
    private boolean shouldChaseUnderwater(LivingEntity target) {
        return target instanceof Enemy && target.getMobType() == MobType.UNDEAD;
    }

    /** 主人距离过远时，把驯服的丧尸娘传送到主人附近的安全位置。
     * 传送前确认主人已经落地，避免主人在半空（跳跃/坠落/末影珍珠飞行）时
     * 把丧尸娘传送到高空导致坠落伤害或卡入方块。 */
    private void teleportToOwner() {
        Player owner = this.level() instanceof ServerLevel server
                ? server.getPlayerByUUID(this.ownerUuid) : null;
        if (owner == null || !owner.onGround()) {
            return;
        }
        // 在主人周围 3 格内寻找一个可站立的位置
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        double x = owner.getX();
        double y = owner.getY();
        double z = owner.getZ();
        for (int attempt = 0; attempt < 16; attempt++) {
            int dx = this.random.nextInt(7) - 3; // -3 ~ +3
            int dz = this.random.nextInt(7) - 3;
            mutable.set(owner.getBlockX() + dx, owner.getBlockY(), owner.getBlockZ() + dz);
            // 找到头顶没有固体、脚下可站立的 Y
            while (this.level().getBlockState(mutable).blocksMotion() && mutable.getY() < this.level().getMaxBuildHeight()) {
                mutable.move(0, 1, 0);
            }
            // 检查该位置是否安全（非水、非岩浆、脚下有块）
            if (!this.level().getBlockState(mutable).isSolid()
                    && !this.level().getBlockState(mutable.below()).isAir()) {
                x = mutable.getX() + 0.5;
                y = mutable.getY();
                z = mutable.getZ() + 0.5;
                break;
            }
        }
        this.teleportTo(x, y, z);
        this.navigation.stop();
    }

    /** 不在白天燃烧：直接关闭日光敏感判定（僵尸原本白天会着火）。 */
    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    /** 不在水下转化成溺尸（她应始终保持丧尸娘形态）。 */
    @Override
    public boolean isUnderWaterConverting() {
        return false;
    }

    /**
     * 判断目标是否为「敌对类亡灵」，同时排除同类。
     * 1.20.1 原版不存在 #minecraft:undead 实体类型标签，必须用 Forge 的
     * LivingEntity#getMobType()（僵尸 / 骷髅等覆盖为 MobType.UNDEAD）。
     */
    private static boolean isHostileUndead(LivingEntity target) {
        return target instanceof Enemy
                && target.getMobType() == MobType.UNDEAD
                && !(target instanceof ZombieGirlEntity)
                && !(target instanceof DrownedGirlEntity);
    }

    /**
     * 主人协同攻击和近战 Goal 共用的目标过滤，避免同类内斗和误伤玩家。
     *
     * <p>车万女仆（无论是否已驯服，都是同一个 EntityMaid 实体）在此一并排除：
     * 玩家攻击自家或野生女仆时，已驯服的丧尸娘不会协助攻击，近战 Goal 也不会出手。
     * 未安装车万女仆时 {@code isTouhouMaid} 恒为 false，无额外影响。</p>
     */
    private static boolean isValidAttackTarget(LivingEntity target) {
        return target != null
                && target.isAlive()
                && !(target instanceof Player)
                && !(target instanceof ZombieGirlEntity)
                && !(target instanceof DrownedGirlEntity)
                && !com.github.emberstar1201.enchantmentex.util.TLMSafe.isTouhouMaid(target);
    }

    // ====================================================================
    // 三、交互：手持生肉右键驯服，类似狼；驯服后可喂食或命令坐下
    // ====================================================================
    /**
     * 完全接管右键交互：任何分支都不调用 super.mobInteract（Mob 默认返回 PASS，
     * 会继续走 Entity.interact / 物品使用流程，可能被其它模组或原版逻辑当成
     * “给她装备武器 / 盔甲”）。
     *
     * 交互规则：
     *   未驯服：只接受生肉（1/3 驯服概率），其它一律 FAIL。
     *   主人：生肉可直接喂食；主手空手时打开背包；剑/斧/三叉戟仍可直接交给她装备；
     *         其它物品 FAIL（盔甲等物品请通过背包界面放入）。
     *   非主人面对已驯服个体：发送拒绝提示并保持关闭。
     * 注：拴绳、命名牌、刷怪蛋在 Mob#checkAndHandleImportantInteractions 中
     *     更早处理，不受这里影响，仍然可用。
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        // 手持无主的星尘 / 生命之星：返回 PASS 放行给物品的 interactLivingEntity 处理。
        // 注意：mobInteract 一旦返回消费结果（SUCCESS/CONSUME），物品交互会被完全拦截。
        if (held.getItem() instanceof UnownedStardustItem
                || held.getItem() instanceof com.github.emberstar1201.enchantmentex.item.LifeStarItem) {
            return InteractionResult.PASS;
        }

        // ---- 未驯服：喂生肉有 1/3 概率驯服（与狼一致） ----
        if (!this.tamed) {
            if (isRawMeat(held)) {
                if (!this.level().isClientSide) {
                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                    if (this.random.nextInt(3) == 0) {
                        tame(player);
                        this.navigation.stop();
                        this.setTarget(null);
                        // byte 7：客户端播放爱心粒子
                        this.level().broadcastEntityEvent(this, (byte) 7);
                    } else {
                        // byte 6：驯服失败的烟雾粒子
                        this.level().broadcastEntityEvent(this, (byte) 6);
                    }
                }
                this.playSound(ModSounds.HUSK_GIRL_EAT.get(), 1.0F, 1.4F);
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            // 未驯服时不接受任何其它物品，阻止武器 / 盔甲被装上
            return InteractionResult.FAIL;
        }

        // ---- 已驯服且是主人：喂食、交武器优先；手上不拿武器即可打开专属背包 ----
        if (this.isOwnedBy(player)) {
            if (canEatFromOwner(held) && this.getHealth() < this.getMaxHealth()) {
                if (!this.level().isClientSide) {
                    // 扣物品与具体回血 / 药水结算交给可覆写钩子（幸存者少女吃任意食物）
                    this.applyEatenFood(held, player.getAbilities().instabuild);
                }
                this.playSound(getEatSound(), 1.0F, 1.4F);
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            // 手持剑 / 斧 / 三叉戟：优先快捷交付到主手
            if (isMeleeWeapon(held)) {
                if (!this.level().isClientSide) {
                    receiveWeapon(player, held);
                }
                this.playSound(SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 1.0F);
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            // 空手或手持其它物品：直接打开专属背包（仅主手触发，避免双手各开一次）
            if (hand == InteractionHand.MAIN_HAND) {
                if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                    NetworkHooks.openScreen(serverPlayer,
                            new SimpleMenuProvider(
                                    (containerId, inventory, menuPlayer) ->
                                            new ZombieGirlInventoryMenu(containerId, inventory, this),
                                    getInventoryTitle()),
                            buffer -> buffer.writeVarInt(this.getId()));
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            return InteractionResult.FAIL;
        }

        // ---- 非主人面对已驯服个体：不允许打开背包，并发送一次提示 ----
        if (!this.level().isClientSide) {
            player.displayClientMessage(
                    Component.translatable(getInventoryDeniedMessageKey()), true);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    /** 是否为生肉（可驯服食物，也允许放入专属背包）。 */
    public static boolean isRawMeat(ItemStack stack) {
        return stack.is(Items.BEEF) || stack.is(Items.PORKCHOP) || stack.is(Items.CHICKEN)
                || stack.is(Items.MUTTON) || stack.is(Items.RABBIT) || stack.is(Items.ROTTEN_FLESH);
    }

    /**
     * 是否为可交付给她的武器：剑 / 斧 / 三叉戟 / 弓。
     * 近战武器由 MeleeAttackGoal 自动使用，弓由 RangedBowAttackGoal 使用。
     */
    public static boolean isMeleeWeapon(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem
                || item instanceof BowItem;
    }

    /**
     * 接收主人交付的武器（服务端调用）：
     *   1. 新武器（完整 NBT / 附魔 / 耐久）装备到主手；
     *   2. 主手掉落率设为 100%，她死亡时武器必定掉出、可回收；
     *   3. 从玩家手中移除原物品；
     *   4. 她原先持有的武器还给主人，背包满则掉落在主人脚下。
     */
    private void receiveWeapon(Player player, ItemStack held) {
        ItemStack newWeapon = held.copy();
        ItemStack oldWeapon = this.getItemBySlot(EquipmentSlot.MAINHAND);
        LivingEntity currentTarget = this.getTarget();
        this.setItemSlot(EquipmentSlot.MAINHAND, newWeapon);
        this.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
        held.shrink(1);
        // 装备同步不会改变目标；显式写回，避免目标选择器刷新期间出现停战
        if (currentTarget != null && currentTarget.isAlive() && this.canAttack(currentTarget, TargetingConditions.forCombat())) {
            this.setTarget(currentTarget);
        }
        if (!oldWeapon.isEmpty() && !player.getInventory().add(oldWeapon)) {
            player.drop(oldWeapon, false);
        }
    }

    /**
     * 直接与玩家建立驯服归属（不走生肉概率驯服，也不发提示）。
     * 生命之星把野生亡灵治愈成幸存者少女时使用，保证治愈后立刻能开背包、
     * 能被星尘收容；已驯服个体的原主人不会被改写。
     */
    public void bondTo(Player player) {
        this.tamed = true;
        this.ownerUuid = player.getUUID();
        this.setPersistenceRequired();
    }

    /** 驯服：记录主人、禁止自然消失、向主人发送对话框提示。 */
    private void tame(Player player) {
        this.tamed = true;
        this.ownerUuid = player.getUUID();
        this.setPersistenceRequired();
        if (player.level() instanceof ServerLevel) {
            player.displayClientMessage(
                    Component.translatable(getTamedMessageKey()), true);
            // 驯服赠礼：日记放进她的随身背包（幸存者少女不会被生肉驯服，不受影响）
            com.github.emberstar1201.enchantmentex.item.GirlDiaryBooks
                    .giveZombieGirlDiary(this);
        }
    }

    /** 驯服成功提示的翻译键（幸存者少女等子类覆写为各自的台词）。 */
    protected String getTamedMessageKey() {
        return "chat.enchantment_expansion.zombie_girl.tamed";
    }

    /** 非主人尝试打开背包时的拒绝提示翻译键（子类可覆写）。 */
    protected String getInventoryDeniedMessageKey() {
        return "chat.enchantment_expansion.zombie_girl.inventory_denied";
    }

    /** 背包界面标题（子类可覆写为各自的名称）。 */
    protected Component getInventoryTitle() {
        return Component.translatable("container.enchantment_expansion.zombie_girl_inventory");
    }

    /** 聊天消息前缀（子类可覆写，如「幸存者少女」）。 */
    protected String getChatPrefix() {
        return "§b【丧尸娘】§f";
    }

    /** 主人手持何种物品可以喂食；丧尸娘只接受生肉，幸存者少女覆写为任意食物。 */
    protected boolean canEatFromOwner(ItemStack stack) {
        return isRawMeat(stack);
    }

    /**
     * 主人喂食结算（仅服务端调用，物品是否消耗由 creative 参数决定）。
     * 丧尸娘固定回 2 颗心；幸存者少女覆写为按食物营养回血并继承正面药水效果。
     */
    protected void applyEatenFood(ItemStack stack, boolean creative) {
        if (!creative) {
            stack.shrink(1);
        }
        this.heal(4.0F);
    }

    /** 进食音效（丧尸娘是专用啃肉音，幸存者少女覆写为原版进食音）。 */
    protected SoundEvent getEatSound() {
        return ModSounds.HUSK_GIRL_EAT.get();
    }

    public boolean isTamed() {
        return tamed;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public boolean isOwnedBy(Player player) {
        return ownerUuid != null && ownerUuid.equals(player.getUUID());
    }

    public boolean isOrderedToSit() {
        return sitting;
    }

    /** 设置坐下状态；坐下时立即停下移动。 */
    public void setOrderedToSit(boolean sitting) {
        this.sitting = sitting;
        if (sitting) {
            this.navigation.stop();
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
        }
    }

    // ====================================================================
    // 四、默认装备：不穿盔甲，必定生成一顶铁 / 金 / 钻石头盔
    // ====================================================================
    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random,
                                                net.minecraft.world.DifficultyInstance difficulty) {
        // 100% 生成一顶头盔：铁、金、钻石等概率（自然生成与刷怪蛋都会调用本方法）
        int tier = random.nextInt(3);
        Item helmet = tier == 0 ? Items.IRON_HELMET
                : tier == 1 ? Items.GOLDEN_HELMET : Items.DIAMOND_HELMET;
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(helmet));
    }

    /** 不随机附魔默认装备，保持头盔为普通品质。 */
    @Override
    protected void populateDefaultEquipmentEnchantments(net.minecraft.util.RandomSource random,
                                                        net.minecraft.world.DifficultyInstance difficulty) {
    }

    // ====================================================================
    // 五、存档：驯服状态、主人、坐下、皮肤变种
    // ====================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Tamed", tamed);
        tag.putBoolean("Sitting", sitting);
        tag.putInt("Variant", this.entityData.get(DATA_VARIANT));
        tag.put("MeatInventory", this.meatInventory.createTag());
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        // 对话计时字段必须一并保存；仅恢复 tickCount 会让这些字段在放出后回到默认值，
        // 从而使多个聊天事件同时满足条件并连续刷屏。
        tag.putLong("ChatHurtCooldownUntil", hurtChatCooldownUntil);
        tag.putLong("ChatNextIdleTick", nextIdleChatTick);
        tag.putLong("ChatVillageCooldownUntil", villageChatCooldownUntil);
        tag.putLong("ChatLastMealTick", lastMealTick);
        tag.putLong("ChatCombatCooldownUntil", combatChatCooldownUntil);
        tag.putLong("ChatLastTimeTick", lastTimeChatTick);
        tag.putLong("ChatLastCorruptionTick", lastCorruptionChatTick);
        tag.putInt("ChatNoEnemyTicks", noEnemyTicks);
        tag.putLong("ChatFreezeUntilTick", freezeUntilTick);
        tag.putBoolean("ChatCorruptionSent", corruptionChatSent);
        if (lastVillagePos != null) {
            tag.putLong("ChatLastVillagePos", lastVillagePos.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        tamed = tag.getBoolean("Tamed");
        sitting = tag.getBoolean("Sitting");
        // 夹紧到合法范围 0~MAX_VARIANT 后写回同步数据，防止旧存档或外部命令写入异常索引
        if (tag.contains("Variant")) {
            this.entityData.set(DATA_VARIANT, Math.max(0, Math.min(MAX_VARIANT, tag.getInt("Variant"))));
            this.variantAssigned = true;
        }
        ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.meatInventory.fromTag(tag.getList("MeatInventory", Tag.TAG_COMPOUND));
        // 从星尘放出时恢复收容时刻的 tickCount：实体所有对话事件
        // （常态闲聊 / 村庄 / 战斗胜利 / 时间感应 / 意识侵蚀 / 受伤吐槽）的
        // 冷却与计时都基于 tickCount，恢复后收容期间的时间视为完全冻结，
        // 不会一放出就因冷却字段陈旧而瞬间刷屏或长期沉默。
        if (tag.contains("CapturedTickCount")) {
            this.tickCount = tag.getInt("CapturedTickCount");
            if (!tag.contains("ChatNextIdleTick")) {
                return;
            }
            if (tag.getBoolean("CapturedChatState")) {
                // 星尘中保存的是相对时间；以放出后的新 tickCount 重建绝对时间。
                hurtChatCooldownUntil = this.tickCount + tag.getLong("ChatHurtCooldownUntil");
                nextIdleChatTick = this.tickCount + tag.getLong("ChatNextIdleTick");
                villageChatCooldownUntil = this.tickCount + tag.getLong("ChatVillageCooldownUntil");
                lastMealTick = this.tickCount - tag.getLong("ChatLastMealTick");
                combatChatCooldownUntil = this.tickCount + tag.getLong("ChatCombatCooldownUntil");
                lastTimeChatTick = this.tickCount - tag.getLong("ChatLastTimeTick");
                lastCorruptionChatTick = this.tickCount - tag.getLong("ChatLastCorruptionTick");
                freezeUntilTick = this.tickCount + tag.getLong("ChatFreezeUntilTick");
            } else {
                hurtChatCooldownUntil = tag.getLong("ChatHurtCooldownUntil");
                nextIdleChatTick = tag.getLong("ChatNextIdleTick");
                villageChatCooldownUntil = tag.getLong("ChatVillageCooldownUntil");
                lastMealTick = tag.getLong("ChatLastMealTick");
                combatChatCooldownUntil = tag.getLong("ChatCombatCooldownUntil");
                lastTimeChatTick = tag.getLong("ChatLastTimeTick");
                lastCorruptionChatTick = tag.getLong("ChatLastCorruptionTick");
                freezeUntilTick = tag.getLong("ChatFreezeUntilTick");
            }
            noEnemyTicks = tag.getInt("ChatNoEnemyTicks");
            corruptionChatSent = tag.getBoolean("ChatCorruptionSent");
            lastVillagePos = tag.contains("ChatLastVillagePos")
                    ? BlockPos.of(tag.getLong("ChatLastVillagePos")) : null;
        }
    }

    // ====================================================================
    // 六、内部目标：协助主人攻击其击中的中立 / 友好生物
    // ====================================================================
    private class OwnerHurtTargetGoal extends TargetGoal {
        /**
         * 协助攻击的记忆时长（tick，20t=1秒）：主人攻击后 600 tick（30 秒）内
         * 丧尸娘都会响应并保持追击，超过后停止，避免无期限追着旧目标跑。
         */
        private static final int OWNER_TARGET_MEMORY_TICKS = 600;

        private Player owner;
        private LivingEntity ownerTarget;
        private int ownerHurtTimestamp;

        OwnerHurtTargetGoal() {
            super(ZombieGirlEntity.this, false);
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        /** 主人攻击目标的协助条件：只排除玩家、丧尸娘类和无效目标。 */
        private boolean isValidOwnerTarget(LivingEntity target) {
            return isValidAttackTarget(target)
                    && this.canAttack(target, TargetingConditions.forCombat());
        }

        @Override
        public boolean canUse() {
            ZombieGirlEntity self = ZombieGirlEntity.this;
            if (!self.tamed || self.ownerUuid == null || self.level().isClientSide) {
                return false;
            }
            this.owner = self.level() instanceof ServerLevel server
                    ? server.getPlayerByUUID(self.ownerUuid) : null;
            if (this.owner == null) {
                return false;
            }
            this.ownerTarget = this.owner.getLastHurtMob();
            this.ownerHurtTimestamp = this.owner.getLastHurtMobTimestamp();
            // 只响应主人记忆窗口（600 tick）内的攻击，避免无期限追击旧目标。
            return this.owner.tickCount - this.ownerHurtTimestamp <= OWNER_TARGET_MEMORY_TICKS
                    && isValidOwnerTarget(this.ownerTarget);
        }

        @Override
        public boolean canContinueToUse() {
            ZombieGirlEntity self = ZombieGirlEntity.this;
            return self.tamed
                    && this.owner != null
                    && this.owner.tickCount - this.ownerHurtTimestamp <= OWNER_TARGET_MEMORY_TICKS
                    && isValidOwnerTarget(this.ownerTarget)
                    && self.getTarget() == this.ownerTarget;
        }

        @Override
        public void start() {
            ZombieGirlEntity.this.setTarget(this.ownerTarget);
            super.start();
        }

        @Override
        public void stop() {
            // 目标死亡或记忆过期后清空，防止攻击 Goal 继续追击旧目标。
            if (ZombieGirlEntity.this.getTarget() == this.ownerTarget) {
                ZombieGirlEntity.this.setTarget(null);
            }
            this.ownerTarget = null;
            super.stop();
        }
    }

    // ====================================================================
    // 八、内部目标：只对敌对亡灵反击
    // ====================================================================
    private class HostileUndeadHurtByTargetGoal extends TargetGoal {
        private int timestamp;

        HostileUndeadHurtByTargetGoal() {
            super(ZombieGirlEntity.this, true);
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity attacker = ZombieGirlEntity.this.getLastHurtByMob();
            return ZombieGirlEntity.this.getLastHurtByMobTimestamp() != this.timestamp
                    && attacker != null
                    && isHostileUndead(attacker)
                    && this.canAttack(attacker, TargetingConditions.forCombat());
        }

        @Override
        public void start() {
            ZombieGirlEntity.this.setTarget(ZombieGirlEntity.this.getLastHurtByMob());
            this.timestamp = ZombieGirlEntity.this.getLastHurtByMobTimestamp();
            super.start();
        }
    }

    // ====================================================================
    // 九、内部行为：被命令坐下（独占行为标记，阻止其它目标执行）
    // ====================================================================
    private class SitWhenOrderedGoal extends Goal {
        SitWhenOrderedGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return tamed && sitting;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            ZombieGirlEntity.this.navigation.stop();
        }

        @Override
        public void stop() {
            ZombieGirlEntity.this.navigation.stop();
        }
    }

    private class ZombieGirlAttackGoal extends ZombieAttackGoal {
        ZombieGirlAttackGoal(double speed, boolean longMemory) {
            super(ZombieGirlEntity.this, speed, longMemory);
        }

        // 主手持有弓时禁用近战冲锋，把攻击行为完全交给 RangedBowAttackGoal，
        // 避免两个同优先级（2）的 Goal 互相抢占导致近战时弓无法瞄准。
        @Override
        public boolean canUse() {
            return super.canUse() && isValidAttackTarget(ZombieGirlEntity.this.getTarget())
                    && !(ZombieGirlEntity.this.getMainHandItem().getItem() instanceof BowItem);
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && isValidAttackTarget(ZombieGirlEntity.this.getTarget())
                    && !(ZombieGirlEntity.this.getMainHandItem().getItem() instanceof BowItem);
        }
    }

    // ====================================================================
    // 十、内部行为：落水后寻找陆地
    // ====================================================================
    private class MoveToLandGoal extends MoveToBlockGoal {
        MoveToLandGoal() {
            super(ZombieGirlEntity.this, 1.15D, 16, 8);
        }

        @Override
        public boolean canUse() {
            ZombieGirlEntity self = ZombieGirlEntity.this;
            return super.canUse() && !self.sitting && self.getTarget() == null && self.isInWater();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && ZombieGirlEntity.this.isInWater()
                    && ZombieGirlEntity.this.getTarget() == null && !ZombieGirlEntity.this.sitting;
        }

        @Override
        protected boolean isValidTarget(LevelReader level, BlockPos pos) {
            BlockPos above = pos.above();
            return level.isEmptyBlock(above) && level.isEmptyBlock(above.above())
                    && level.getBlockState(pos).entityCanStandOn(level, pos, ZombieGirlEntity.this);
        }


    }

    // ====================================================================
    // 十、内部行为：跟随主人
    // ====================================================================
    private class FollowOwnerGoal extends Goal {
        private Player owner;
        private int timeToRecalcPath;

        FollowOwnerGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            ZombieGirlEntity self = ZombieGirlEntity.this;
            if (!self.tamed || self.sitting || self.ownerUuid == null || self.getTarget() != null) {
                return false;
            }
            this.owner = self.level() instanceof ServerLevel server
                    ? server.getPlayerByUUID(self.ownerUuid) : null;
            // 与主人距离超过 3 格才跟随
            return this.owner != null && self.distanceToSqr(this.owner) > 9.0D;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            if (this.owner == null) {
                return;
            }
            ZombieGirlEntity self = ZombieGirlEntity.this;
            self.getLookControl().setLookAt(
                    this.owner, 10.0F, self.getMaxHeadXRot());
            // 主人距离超过 16 格时传送到主人附近
            if (self.distanceToSqr(this.owner) > 256.0D) {
                self.teleportToOwner();
                return;
            }
            if (--this.timeToRecalcPath <= 0) {
                this.timeToRecalcPath = 10;
                self.navigation.moveTo(this.owner, 1.1D);
            }
        }
    }
}
