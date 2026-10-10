package com.github.emberstar1201.enchantmentex.entity;

import com.github.emberstar1201.enchantmentex.entity.menu.ZombieGirlInventoryMenu;
import com.github.emberstar1201.enchantmentex.item.UnownedStardustItem;
import com.github.emberstar1201.enchantmentex.sound.ModSounds;
import com.github.emberstar1201.enchantmentex.util.TLMSafe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * 溺尸娘：保留 Drowned 原生水陆移动、游泳、上浮和靠岸行为的独立变种。
 * 目标过滤只允许敌对亡灵，避免攻击玩家、少女和其它友好生物。
 */
public class DrownedGirlEntity extends Drowned implements FriendlyGirlInventory {
    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("8b14dfcb-75b1-4c42-8ea2-4f75d7a0c1e9");
    private static final String SPEED_MODIFIER_NAME = "drowned_girl_base_speed";
    // MULTIPLY_TOTAL 运算：成人 0.1 → 最终为原版溺尸的 1.1 倍（陆地与游泳同样生效）
    private static final double SPEED_MULTIPLIER_ADULT = 0.1D;
    // MULTIPLY_TOTAL 运算：幼年 0.2 → 最终为小溺尸的 1.2 倍
    private static final double SPEED_MULTIPLIER_BABY = 0.2D;
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(DrownedGirlEntity.class, EntityDataSerializers.INT);

    /**
     * 溺尸娘皮肤：与丧尸娘共用同一批贴图文件，变种 0~18 共 19 个
     * （0 号为默认 zombie_girl.png，其余按编号 zombie_girl_N.png）。
     * 治愈成幸存者少女后编号原样继承，对应 human_girl_N.png。
     */
    private static final ResourceLocation[] TEXTURES = {
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_1.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_2.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_3.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_4.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_5.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_6.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_7.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_8.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_9.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_10.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_11.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_12.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_13.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_14.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_15.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_16.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_17.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_18.png")
    };

    public DrownedGirlEntity(EntityType<? extends Drowned> type, Level level) {
        super(type, level);
        // 陆地寻路允许穿过木门（配合 OpenDoorGoal 实际开关门），夜晚能开门进屋睡觉
        this.groundNavigation.setCanOpenDoors(true);
        this.groundNavigation.setCanFloat(true);
    }

    // ====================================================================
    // 驯服与背包：与丧尸娘同规则（生肉 1/3 驯服；非满血自动啃生肉回血；
    // 主人空手/非武器右键打开「6 装备槽 + 64 格背包」GUI）。
    // 实现 FriendlyGirlInventory 接口后与丧尸娘共用 ZombieGirlInventoryMenu。
    // ====================================================================
    private boolean tamed;
    @Nullable
    private UUID ownerUuid;
    /** 是否坐下（与丧尸娘同字段名，跟随 Goal 会检查）。 */
    private boolean sitting;
    /**
     * 64 格随身背包（8 列 × 8 行），随实体 NBT 持久化。
     * 字段名沿用丧尸娘的 MeatInventory 历史命名，实际可放任意物品。
     */
    private final SimpleContainer meatInventory = new SimpleContainer(64);

    /**
     * 皮肤变种是否已被真正分配过（自然生成 finalizeSpawn / 读档 Variant / 治愈感染继承）。
     * {@code /summon}、{@code /ee girl summon} 新建的实体既不走 finalizeSpawn 也不带
     * Variant NBT，编号恒为默认 0，没有刷怪蛋的幼年溺尸娘因此永远显示默认 zombie_girl.png。
     * 该字段不落盘，首个服务端 tick 发现未分配时补一次随机并自动同步客户端。
     */
    private boolean variantAssigned;

    @Override
    public void aiStep() {
        super.aiStep();
        // 兜底分配皮肤变种：未经 finalizeSpawn、也不带 Variant NBT 的新建实体
        // （没有刷怪蛋的幼年溺尸娘主要靠 /summon 产生）在首个服务端 tick 补随机。
        if (!this.level().isClientSide && !this.variantAssigned) {
            this.entityData.set(DATA_VARIANT, this.random.nextInt(TEXTURES.length));
            this.variantAssigned = true;
        }
        syncMovementSpeed();
        // 已驯服且非满血：每 2 秒尝试从随身背包啃一块生肉回血（与丧尸娘同节奏）
        if (!this.level().isClientSide && this.tamed
                && this.tickCount % 40L == 0L
                && this.getHealth() < this.getMaxHealth()) {
            consumeMeatForHealing();
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

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        // 手持无主的星尘 / 生命之星：返回 PASS 放行给物品的 interactLivingEntity 处理。
        // 注意：mobInteract 一旦返回消费结果（SUCCESS/CONSUME），物品交互会被完全拦截。
        if (held.getItem() instanceof UnownedStardustItem
                || held.getItem() instanceof com.github.emberstar1201.enchantmentex.item.LifeStarItem) {
            return InteractionResult.PASS;
        }

        // ---- 未驯服：喂生肉有 1/3 概率驯服（与丧尸娘一致），其它物品一律拒绝 ----
        if (!this.tamed) {
            if (ZombieGirlEntity.isRawMeat(held)) {
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
            return InteractionResult.FAIL;
        }

        // ---- 已驯服且是主人：喂食回血优先；空手或非武器打开专属背包 GUI ----
        if (this.isOwnedBy(player)) {
            // 非满血时手持生肉：直接喂食回血（回血优先于打开背包，与丧尸娘一致）
            if (ZombieGirlEntity.isRawMeat(held) && this.getHealth() < this.getMaxHealth()) {
                if (!this.level().isClientSide) {
                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                    this.heal(4.0F);
                }
                this.playSound(ModSounds.HUSK_GIRL_EAT.get(), 1.0F, 1.4F);
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            if (ZombieGirlEntity.isMeleeWeapon(held)) {
                if (!this.level().isClientSide) {
                    receiveWeapon(player, held);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            // 空手或手持其它物品：打开背包 GUI（仅主手触发，避免双手各开一次）
            if (hand == InteractionHand.MAIN_HAND) {
                if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                    NetworkHooks.openScreen(serverPlayer,
                            new SimpleMenuProvider(
                                    (containerId, inventory, menuPlayer) ->
                                            new ZombieGirlInventoryMenu(containerId, inventory, this),
                                    Component.translatable(
                                            "container.enchantment_expansion.drowned_girl_inventory")),
                            buffer -> buffer.writeVarInt(this.getId()));
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            return InteractionResult.FAIL;
        }

        // ---- 非主人面对已驯服个体：不允许打开背包，并发送一次提示 ----
        if (!this.level().isClientSide) {
            player.displayClientMessage(
                    Component.translatable(
                            "chat.enchantment_expansion.drowned_girl.inventory_denied"), true);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    private void receiveWeapon(Player player, ItemStack held) {
        ItemStack newWeapon = held.copy();
        ItemStack oldWeapon = this.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, newWeapon);
        this.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 1.0F);
        held.shrink(1);
        if (!oldWeapon.isEmpty() && !player.getInventory().add(oldWeapon)) {
            player.drop(oldWeapon, false);
        }
    }

    /** 驯服：记录主人、禁止自然消失、向主人发送对话框提示。 */
    private void tame(Player player) {
        this.tamed = true;
        this.ownerUuid = player.getUUID();
        this.setPersistenceRequired();
        if (player.level() instanceof ServerLevel) {
            player.displayClientMessage(
                    Component.translatable("chat.enchantment_expansion.drowned_girl.tamed"), true);
            // 驯服赠礼：日记放进她的随身背包
            com.github.emberstar1201.enchantmentex.item.GirlDiaryBooks
                    .giveDrownedGirlDiary(this);
        }
    }

    @Override
    public boolean isTamed() {
        return this.tamed;
    }

    /** 是否被命令坐下（FriendlyGirlInventory 接口要求；供共用睡觉 Goal 判定）。 */
    @Override
    public boolean isOrderedToSit() {
        return this.sitting;
    }

    @Nullable
    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public void bondTo(Player player) {
        this.tamed = true;
        this.ownerUuid = player.getUUID();
        this.setPersistenceRequired();
    }

    public boolean isOwnedBy(Player player) {
        return this.ownerUuid != null && this.ownerUuid.equals(player.getUUID());
    }

    @Override
    public SimpleContainer getMeatInventory() {
        return this.meatInventory;
    }

    /** 随身背包中是否含有指定物品（星星判定用），与丧尸娘同款实现。 */
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

    /** 从随身背包啃一块生肉回血（每块 2 颗心），播放专用啃肉音效。 */
    private void consumeMeatForHealing() {
        for (int slot = 0; slot < this.meatInventory.getContainerSize(); slot++) {
            ItemStack meat = this.meatInventory.getItem(slot);
            if (ZombieGirlEntity.isRawMeat(meat)) {
                meat.shrink(1);
                this.heal(4.0F);
                this.playSound(ModSounds.HUSK_GIRL_EAT.get(), 1.0F, 1.25F);
                return;
            }
        }
    }

    /** 药水效果不再按亡灵反转：治疗药水正常回血，伤害药水正常受伤。 */
    @Override
    public boolean isInvertedHealAndHarm() {
        return false;
    }

    // ====================================================================
    // 语音：溺尸娘与丧尸娘共用一套 husk_girl 音频（取代原版溺尸音效，
    // 不再区分陆地 / 水下版本；在水中播放时声音引擎会自动加上闷音效果）。
    // ====================================================================
    @Override
    protected SoundEvent getAmbientSound() {
        // 睡着时不播放闲置音（与原版村民一致）
        return this.isSleeping() ? null : ModSounds.HUSK_GIRL_IDLE.get();
    }

    /** 友好溺尸娘不能阻止玩家上床睡觉（Monster 原版默认会阻止休息）。 */
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
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_VARIANT, 0);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                         MobSpawnType reason, @Nullable SpawnGroupData spawnData,
                                         @Nullable CompoundTag dataTag) {
        this.entityData.set(DATA_VARIANT, this.random.nextInt(TEXTURES.length));
        this.variantAssigned = true;
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    public ResourceLocation getVariantTexture() {
        int variant = this.entityData.get(DATA_VARIANT);
        return TEXTURES[Math.max(0, Math.min(TEXTURES.length - 1, variant))];
    }

    public void setCampVariant(int variant) {
        this.entityData.set(DATA_VARIANT, Math.max(0, Math.min(TEXTURES.length - 1, variant)));
        this.variantAssigned = true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", this.entityData.get(DATA_VARIANT));
        tag.putBoolean("Tamed", this.tamed);
        tag.put("MeatInventory", this.meatInventory.createTag());
        if (this.ownerUuid != null) {
            tag.putUUID("Owner", this.ownerUuid);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // 存档（含星尘收容放出、治愈/感染的 NBT 迁移）里带 Variant 才视为已分配；
        // /summon 等无 Variant 键的新建实体保持未分配，由首个服务端 tick 兜底随机。
        if (tag.contains("Variant")) {
            this.entityData.set(DATA_VARIANT, Math.max(0, Math.min(TEXTURES.length - 1,
                    tag.getInt("Variant"))));
            this.variantAssigned = true;
        }
        this.tamed = tag.getBoolean("Tamed");
        this.ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.meatInventory.fromTag(tag.getList("MeatInventory", Tag.TAG_COMPOUND));
        // 从星尘放出时恢复收容时刻的 tickCount：
        // 实体所有基于 tickCount 的计时（回血节奏、攻击目标记忆等）在收容期间完全冻结。
        if (tag.contains("CapturedTickCount")) {
            this.tickCount = tag.getInt("CapturedTickCount");
        }
    }

    /** 溺尸娘保留亡灵逻辑，但不会因为白天而燃烧。 */
    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    /** 刷怪蛋生成的溺尸娘在和平难度下也必须保留。 */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    protected void registerGoals() {
        // 意识暂时丧失彩蛋：附近有村民时低概率追着村民跑（只追不打）。
        // 优先级 0，与水中上浮 Goal（只占 JUMP 标记）不冲突，
        // 触发期间会压制 super 中注册的原版水中攻击等全部移动行为。
        this.goalSelector.addGoal(0, new GirlConfusionGoal(this));
        // 开门与睡觉必须在 super 之前注册：睡眠 Goal 与原版攻击同为优先级 2，
        // 同优先级按注册顺序抢占 MOVE/JUMP/LOOK 标记，确保夜里附近有床时
        // 直接去睡，不会先被水中攻击 / 闲逛类 Goal 抢走。
        // （OpenDoorGoal 不占移动标记，可与任意行为并行。）
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(2, new GirlSleepGoal(this, this));
        // 再注册原版溺尸的游泳上浮、水中攻击、寻水、闲逛等移动 Goal。
        super.registerGoals();
        // 在原版基础上追加：跟随主人（优先级 1，与攻击同级但互斥，高于寻水）、靠岸（优先级 3）。
        // 原版 DrownedGoToWaterGoal 优先级为 2，因此跟随（1）会压过寻水，避免被拉回水里。
        this.goalSelector.addGoal(1, new FollowOwnerGoal());
        this.goalSelector.addGoal(3, new MoveToLandGoal());
    }

    /** 传送到主人附近（主人落地时才触发，避免主人跳跃/飞行时瞬移）。 */
    private void teleportToOwner() {
        Player owner = this.level() instanceof ServerLevel server
                ? server.getPlayerByUUID(this.ownerUuid) : null;
        if (owner == null || !owner.onGround()) {
            return;
        }
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        double x = owner.getX();
        double y = owner.getY();
        double z = owner.getZ();
        for (int attempt = 0; attempt < 16; attempt++) {
            int dx = this.random.nextInt(7) - 3;
            int dz = this.random.nextInt(7) - 3;
            mutable.set(owner.getBlockX() + dx, owner.getBlockY(), owner.getBlockZ() + dz);
            while (this.level().getBlockState(mutable).blocksMotion()
                    && mutable.getY() < this.level().getMaxBuildHeight()) {
                mutable.move(0, 1, 0);
            }
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

    @Override
    protected void addBehaviourGoals() {
        // 先保留 Drowned 的水陆移动、游泳上浮、靠岸和攻击 goal。
        super.addBehaviourGoals();
        // 移除原版针对玩家、村民、铁傀儡等目标，避免友好亡灵误伤无关生物。
        this.targetSelector.getAvailableGoals().removeIf(goal -> goal.getGoal() instanceof HurtByTargetGoal
                || goal.getGoal() instanceof NearestAttackableTargetGoal);
        // 优先级 1：主人协同攻击（主人攻击的目标，600 tick 记忆窗口）
        this.targetSelector.addGoal(1, new OwnerHurtTargetGoal());
        // 优先级 2：被攻击时反击敌对亡灵
        this.targetSelector.addGoal(2, new FriendlyUndeadHurtByTargetGoal(this));
        // 优先级 3：天生仇恨敌对亡灵
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                this, Mob.class, 10, true, false, DrownedGirlEntity::isWaterHostileUndead));
    }

    @Override
    public boolean okTarget(@Nullable LivingEntity target) {
        // 目标白名单：排除玩家、同类、车万女仆；其余目标（主人攻击的目标、
        // 水中亡灵等）均可攻击。原版的「只能攻击水中亡灵」限制会挡住主人的陆上目标。
        return isValidAttackTarget(target);
    }

    /**
     * 目标白名单：主人协同攻击和天生仇恨共用。
     * 排除玩家、丧尸娘、溺尸娘、车万女仆；其余生物均可作为主人协同目标。
     */
    private static boolean isValidAttackTarget(LivingEntity target) {
        return target != null
                && target.isAlive()
                && !(target instanceof Player)
                && !(target instanceof ZombieGirlEntity)
                && !(target instanceof DrownedGirlEntity)
                && !TLMSafe.isTouhouMaid(target);
    }

    private static boolean isWaterHostileUndead(@Nullable LivingEntity target) {
        return target instanceof Enemy
                && target.getMobType() == MobType.UNDEAD
                && !(target instanceof DrownedGirlEntity)
                && !(target instanceof ZombieGirlEntity);
    }

    // ====================================================================
    // 主人协同攻击：主人攻击某生物后 600 tick（30 秒）内，溺尸娘协助追击。
    // 与丧尸娘同款逻辑，目标走 isValidAttackTarget 白名单。
    // ====================================================================
    private class OwnerHurtTargetGoal extends TargetGoal {
        private static final int OWNER_TARGET_MEMORY_TICKS = 600;

        private Player owner;
        private LivingEntity ownerTarget;
        private int ownerHurtTimestamp;

        OwnerHurtTargetGoal() {
            super(DrownedGirlEntity.this, false);
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        private boolean isValidOwnerTarget(LivingEntity target) {
            return isValidAttackTarget(target)
                    && this.canAttack(target, TargetingConditions.forCombat());
        }

        @Override
        public boolean canUse() {
            DrownedGirlEntity self = DrownedGirlEntity.this;
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
            return this.owner.tickCount - this.ownerHurtTimestamp <= OWNER_TARGET_MEMORY_TICKS
                    && isValidOwnerTarget(this.ownerTarget);
        }

        @Override
        public boolean canContinueToUse() {
            DrownedGirlEntity self = DrownedGirlEntity.this;
            return self.tamed
                    && this.owner != null
                    && this.owner.tickCount - this.ownerHurtTimestamp <= OWNER_TARGET_MEMORY_TICKS
                    && isValidOwnerTarget(this.ownerTarget)
                    && self.getTarget() == this.ownerTarget;
        }

        @Override
        public void start() {
            DrownedGirlEntity.this.setTarget(this.ownerTarget);
            super.start();
        }

        @Override
        public void stop() {
            if (DrownedGirlEntity.this.getTarget() == this.ownerTarget) {
                DrownedGirlEntity.this.setTarget(null);
            }
            this.ownerTarget = null;
            super.stop();
        }
    }

    private static final class FriendlyUndeadHurtByTargetGoal extends HurtByTargetGoal {
        private final DrownedGirlEntity drownedGirl;

        private FriendlyUndeadHurtByTargetGoal(DrownedGirlEntity drownedGirl) {
            super(drownedGirl);
            this.drownedGirl = drownedGirl;
        }

        @Override
        public boolean canUse() {
            LivingEntity attacker = this.drownedGirl.getLastHurtByMob();
            return super.canUse() && isWaterHostileUndead(attacker);
        }
    }

    // ====================================================================
    // 跟随主人：已驯服且未坐下、无攻击目标、主人距离超过 3 格时跟随；
    // 超过 16 格直接传送到主人附近。与丧尸娘同款逻辑。
    // ====================================================================
    private class FollowOwnerGoal extends Goal {
        private Player owner;
        private int timeToRecalcPath;

        FollowOwnerGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            DrownedGirlEntity self = DrownedGirlEntity.this;
            if (!self.tamed || self.sitting || self.ownerUuid == null || self.getTarget() != null) {
                return false;
            }
            this.owner = self.level() instanceof ServerLevel server
                    ? server.getPlayerByUUID(self.ownerUuid) : null;
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
            DrownedGirlEntity self = DrownedGirlEntity.this;
            self.getLookControl().setLookAt(this.owner, 10.0F, self.getMaxHeadXRot());
            // 主人距离超过 16 格时传送
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

    // ====================================================================
    // 靠岸：在水中且无攻击目标、未跟随主人时，向最近的陆地移动。
    // 优先级 3，低于跟随（1）和寻水（2），只在两者都不激活时生效。
    // ====================================================================
    private class MoveToLandGoal extends MoveToBlockGoal {
        MoveToLandGoal() {
            super(DrownedGirlEntity.this, 1.15D, 16, 8);
        }

        @Override
        public boolean canUse() {
            DrownedGirlEntity self = DrownedGirlEntity.this;
            return super.canUse() && !self.sitting && self.getTarget() == null && self.isInWater();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && DrownedGirlEntity.this.isInWater()
                    && DrownedGirlEntity.this.getTarget() == null && !DrownedGirlEntity.this.sitting;
        }

        @Override
        protected boolean isValidTarget(LevelReader level, BlockPos pos) {
            BlockPos above = pos.above();
            return level.isEmptyBlock(above) && level.isEmptyBlock(above.above())
                    && level.getBlockState(pos).entityCanStandOn(level, pos, DrownedGirlEntity.this);
        }
    }
}
