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
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.SaddleItem;
import net.minecraft.world.item.ShearsItem;
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
    // MULTIPLY_TOTAL 运算：成人 0.1 → 最终为原版僵尸的 1.24 倍
    private static final double SPEED_MULTIPLIER_ADULT = 0.24D;
    // MULTIPLY_TOTAL 运算：幼年 0.2 → 最终为小僵尸的 1.24 倍
    private static final double SPEED_MULTIPLIER_BABY = 0.24D;
    private static final String SPEED_MODIFIER_NAME = "zombie_girl_base_speed";

    private static final EntityDataAccessor<Boolean> DATA_TAMED =
            SynchedEntityData.defineId(ZombieGirlEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_AFFECTION =
            SynchedEntityData.defineId(ZombieGirlEntity.class, EntityDataSerializers.INT);
    /** 主人 UUID；未驯服时为 null。 */
    private UUID ownerUuid;
    /** 是否被主人命令坐下。 */
    private boolean sitting;
    private static final int MAX_AFFECTION = 100;
    private static final long GIFT_BOREDOM_TICKS = 600L;
    private String lastGiftItem = "";
    private long lastGiftTick = Long.MIN_VALUE;
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

    /** 上次吃生肉回血的时刻（tick），供进食逻辑判断。 */
    protected long lastMealTick = -10000L;

    public ZombieGirlEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        // 允许跟随 / 睡觉寻路穿过木门（配合 OpenDoorGoal 实际开关门），
        // 夜晚能自己开门走进玩家庇护所里的床；可涉水浮起避免卡水沟。
        if (this.getNavigation() instanceof GroundPathNavigation groundNavigation) {
            groundNavigation.setCanOpenDoors(true);
            groundNavigation.setCanFloat(true);
        }
        // 危险方块设置负寻路权重，避免正常移动与撤退时主动踩入火、岩浆和仙人掌。
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.DAMAGE_FIRE, -1.0F);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.LAVA, -1.0F);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.DAMAGE_OTHER, -1.0F);
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
        if (!this.level().isClientSide && this.isTamed()
                && this.tickCount % 40L == 0L
                && this.getHealth() < this.getMaxHealth()) {
            consumeMeatForHealing();
        }
        // 无攻击目标时在水中自动上浮到水面（有目标时允许下潜追敌）
        if (this.isInWater() && this.getTarget() == null && this.getDeltaMovement().y < 0.05D) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D)
                    .add(0.0D, 0.08D, 0.0D));
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

    /** 死亡时把随身背包物品掉落在脚下，避免贵重物品随实体消失。 */
    @Override
    public void die(DamageSource source) {
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
        double flightTicks = Math.sqrt(this.distanceToSqr(target)) / 1.6D;
        double dx = target.getX() + target.getDeltaMovement().x * flightTicks - this.getX();
        double dy = target.getY(0.3333333333333333D) + target.getDeltaMovement().y * flightTicks - arrow.getY();
        double dz = target.getZ() + target.getDeltaMovement().z * flightTicks - this.getZ();
        if (isFriendlyInShotLine(target)) {
            return;
        }
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float inaccuracy = Math.max(0.5F,
                14.0F - this.level().getDifficulty().getId() * 4.0F - 2.0F);
        arrow.shoot(dx, dy + horizontalDist * 0.2D, dz, 1.6F, inaccuracy);
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F,
                1.0F / (this.random.nextFloat() * 0.4F + 0.8F));
        this.level().addFreshEntity(arrow);
    }

    /** 检查射线段上是否有主人或其他少女，避免远程攻击误伤友军。 */
    protected boolean isFriendlyInShotLine(LivingEntity target) {
        net.minecraft.world.phys.Vec3 start = this.getEyePosition();
        net.minecraft.world.phys.Vec3 end = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        net.minecraft.world.phys.AABB bounds = new net.minecraft.world.phys.AABB(start, end).inflate(0.45D);
        return this.level().getEntitiesOfClass(LivingEntity.class, bounds,
                entity -> entity != this && entity != target && entity.isAlive()
                        && (entity instanceof Player player && this.isOwnedBy(player)
                        || entity instanceof ZombieGirlEntity || entity instanceof DrownedGirlEntity))
                .stream().anyMatch(entity -> entity.getBoundingBox().inflate(0.15D)
                        .clip(start, end).isPresent());
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
        this.entityData.define(DATA_TAMED, false);
        this.entityData.define(DATA_AFFECTION, 0);
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

    public void setCampVariant(int variant) {
        setVariant(variant);
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
        // 低血时优先脱离战斗；比坐下低一级，坐下命令仍保持最高移动优先权。
        this.goalSelector.addGoal(1, new RetreatWhenHurtGoal());
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
        // 主人受袭时优先保护主人，目标白名单仍排除玩家和少女同伴。
        this.targetSelector.addGoal(0, new OwnerHurtByTargetGoal());
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
     * 注：拴绳、命名牌、刷怪蛋以及其它原版实体交互物品会先放行，避免被背包逻辑截获。
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        // 手持无主的星尘 / 生命之星：返回 PASS 放行给物品的 interactLivingEntity 处理。
        // 注意：mobInteract 一旦返回消费结果（SUCCESS/CONSUME），物品交互会被完全拦截。
        if (held.getItem() instanceof UnownedStardustItem
                || held.getItem() instanceof com.github.emberstar1201.enchantmentex.item.LifeStarItem
                || held.getItem() instanceof com.github.emberstar1201.enchantmentex.item.AffectionStageUpItem
                || held.getItem() instanceof com.github.emberstar1201.enchantmentex.item.AffectionStageDownItem
                || isVanillaEntityInteractionItem(held)) {
            return InteractionResult.PASS;
        }

        // ---- 未驯服：喂驯服食物有 1/3 概率驯服（与狼一致） ----
        if (!this.isTamed()) {
            if (isTamingFood(held)) {
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

    /** 原版会直接作用于实体的物品，必须先放行给原版实体交互逻辑。 */
    public static boolean isVanillaEntityInteractionItem(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof SpawnEggItem
                || item instanceof BucketItem
                || item instanceof DyeItem
                || item instanceof SaddleItem
                || item instanceof ShearsItem
                || item == Items.LEAD
                || item == Items.NAME_TAG;
    }

    /** 是否为可驯服食物：丧尸娘只接受生肉，幸存者少女覆写为任意食物。 */
    protected boolean isTamingFood(ItemStack stack) {
        return isRawMeat(stack);
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
        this.entityData.set(DATA_TAMED, true);
        this.ownerUuid = player.getUUID();
        if (this.getAffection() <= 0) {
            this.setAffection(10);
        }
        this.setPersistenceRequired();
    }

    /** 驯服：记录主人、禁止自然消失、向主人发送对话框提示。 */
    protected void tame(Player player) {
        this.entityData.set(DATA_TAMED, true);
        this.ownerUuid = player.getUUID();
        this.setAffection(10);
        this.setPersistenceRequired();
        if (player.level() instanceof ServerLevel) {
            player.displayClientMessage(
                    Component.translatable(getTamedMessageKey()), true);
            // 驯服赠礼：日记放进她的随身背包（子类覆写为各自的日记类型）
            giveTamingDiary();
        }
    }

    /** 驯服赠礼：丧尸娘给「破损的日记」，幸存者少女覆写给幸存者日记。 */
    protected void giveTamingDiary() {
        com.github.emberstar1201.enchantmentex.item.GirlDiaryBooks
                .giveZombieGirlDiary(this);
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
        return this.entityData.get(DATA_TAMED);
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

    @Override
    public int getAffection() {
        return this.isTamed()
                ? Math.max(0, Math.min(MAX_AFFECTION, this.entityData.get(DATA_AFFECTION))) : 0;
    }

    @Override
    public void setAffection(int value) {
        this.entityData.set(DATA_AFFECTION, Math.max(0, Math.min(MAX_AFFECTION, value)));
    }

    @Override
    public void receiveGift(Player player) {
        ItemStack gift = player.getMainHandItem();
        if (!this.isTamed() || !this.isOwnedBy(player) || gift.isEmpty()) {
            return;
        }
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(gift.getItem()).toString();
        boolean bored = itemId.equals(this.lastGiftItem)
                && this.tickCount - this.lastGiftTick < GIFT_BOREDOM_TICKS;
        int quality = switch (gift.getRarity()) {
            case UNCOMMON -> 2;
            case RARE -> 4;
            case EPIC -> 8;
            default -> 1;
        };
        float acceptChance = bored ? 0.15F : Math.min(0.9F, 0.45F + quality * 0.05F);
        this.lastGiftItem = itemId;
        this.lastGiftTick = this.tickCount;
        if (this.random.nextFloat() > acceptChance) {
            player.displayClientMessage(Component.translatable(
                    "chat.enchantment_expansion.girl.gift_rejected"), true);
            return;
        }
        if (!player.getAbilities().instabuild) {
            gift.shrink(1);
        }
        this.setAffection(this.getAffection() + quality);
        player.displayClientMessage(Component.translatable(
                "chat.enchantment_expansion.girl.gift_accepted", quality), true);
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
        tag.putBoolean("Tamed", this.isTamed());
        tag.putBoolean("Sitting", sitting);
        tag.putInt("Affection", this.entityData.get(DATA_AFFECTION));
        tag.putString("LastGiftItem", lastGiftItem);
        tag.putLong("LastGiftTick", lastGiftTick);
        tag.putInt("Variant", this.entityData.get(DATA_VARIANT));
        tag.put("MeatInventory", this.meatInventory.createTag());
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_TAMED, tag.getBoolean("Tamed"));
        sitting = tag.getBoolean("Sitting");
        this.setAffection(tag.contains("Affection") ? tag.getInt("Affection") : 0);
        lastGiftItem = tag.getString("LastGiftItem");
        lastGiftTick = tag.contains("LastGiftTick")
                ? tag.getLong("LastGiftTick") : Long.MIN_VALUE;
        // 夹紧到合法范围 0~MAX_VARIANT 后写回同步数据，防止旧存档或外部命令写入异常索引
        if (tag.contains("Variant")) {
            this.entityData.set(DATA_VARIANT, Math.max(0, Math.min(MAX_VARIANT, tag.getInt("Variant"))));
            this.variantAssigned = true;
        }
        ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.meatInventory.fromTag(tag.getList("MeatInventory", Tag.TAG_COMPOUND));
        if (tag.contains("CapturedTickCount")) {
            this.tickCount = tag.getInt("CapturedTickCount");
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
            if (!self.isTamed() || self.ownerUuid == null || self.level().isClientSide) {
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
            return self.isTamed()
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
    // 八、内部目标：主人受袭时反击凶手
    // ====================================================================
    private class OwnerHurtByTargetGoal extends TargetGoal {
        private Player owner;
        private int timestamp;

        OwnerHurtByTargetGoal() {
            super(ZombieGirlEntity.this, true);
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            ZombieGirlEntity self = ZombieGirlEntity.this;
            if (!self.isTamed() || self.ownerUuid == null || self.level().isClientSide) return false;
            this.owner = self.level() instanceof ServerLevel server
                    ? server.getPlayerByUUID(self.ownerUuid) : null;
            LivingEntity attacker = this.owner == null ? null : this.owner.getLastHurtByMob();
            return attacker != null && attacker.isAlive() && isValidAttackTarget(attacker)
                    && this.owner.getLastHurtByMobTimestamp() != this.timestamp
                    && this.canAttack(attacker, TargetingConditions.forCombat());
        }

        @Override
        public void start() {
            this.timestamp = this.owner.getLastHurtByMobTimestamp();
            ZombieGirlEntity.this.setTarget(this.owner.getLastHurtByMob());
            super.start();
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
            return isTamed() && sitting;
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
    // 低血撤退：生命低于 30% 时向主人附近退开；无主人则远离当前目标。
    // ====================================================================
    private class RetreatWhenHurtGoal extends Goal {
        private int cooldown;
        private int retreatTicks;
        private LivingEntity target;
        private Player owner;

        RetreatWhenHurtGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            ZombieGirlEntity self = ZombieGirlEntity.this;
            if (self.sitting || self.getHealth() > self.getMaxHealth() * 0.30F
                    || self.getTarget() == null) return false;
            this.target = self.getTarget();
            return this.target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            ZombieGirlEntity self = ZombieGirlEntity.this;
            boolean reachedOwner = this.owner != null && self.distanceToSqr(this.owner) <= 36.0D;
            return self.getHealth() <= self.getMaxHealth() * 0.30F
                    && this.target != null && this.target.isAlive()
                    && this.retreatTicks > 0 && !reachedOwner && !self.getNavigation().isDone();
        }

        @Override
        public void start() {
            ZombieGirlEntity self = ZombieGirlEntity.this;
            this.owner = self.isTamed() && self.ownerUuid != null && self.level() instanceof ServerLevel server
                    ? server.getPlayerByUUID(self.ownerUuid) : null;
            double dx;
            double dz;
            if (this.owner != null && this.owner.isAlive()) {
                // 有主人时退向主人一侧，但只移动一段距离，避免直接冲回战场中心。
                dx = this.owner.getX() - self.getX();
                dz = this.owner.getZ() - self.getZ();
            } else {
                // 主人不在线或不可用时，明确沿当前目标的反方向撤退。
                this.owner = null;
                dx = self.getX() - this.target.getX();
                dz = self.getZ() - this.target.getZ();
            }
            double length = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
            BlockPos retreat = BlockPos.containing(self.getX() + dx / length * 8.0D,
                    self.getY(), self.getZ() + dz / length * 8.0D);
            self.getNavigation().moveTo(retreat.getX() + 0.5D, retreat.getY(), retreat.getZ() + 0.5D, 1.25D);
            this.retreatTicks = 100;
        }

        @Override
        public void tick() {
            if (this.retreatTicks > 0) this.retreatTicks--;
            if (this.owner == null && this.target != null) {
                ZombieGirlEntity.this.getLookControl().setLookAt(this.target, 10.0F,
                        ZombieGirlEntity.this.getMaxHeadXRot());
            }
        }

        @Override
        public void stop() {
            ZombieGirlEntity.this.getNavigation().stop();
            this.cooldown = 40;
            this.retreatTicks = 0;
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
            if (!self.isTamed() || self.sitting || self.ownerUuid == null || self.getTarget() != null) {
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
                self.navigation.moveTo(this.owner, 1.24D);
            }
        }
    }
}
