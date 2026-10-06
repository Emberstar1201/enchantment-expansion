package com.github.emberstar1201.enchantmentex.entity;

import com.github.emberstar1201.enchantmentex.entity.menu.ZombieGirlInventoryMenu;
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
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
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
 * 目标过滤只允许水中的敌对亡灵，避免攻击玩家和其它溺尸娘。
 */
public class DrownedGirlEntity extends Drowned implements FriendlyGirlInventory {
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(DrownedGirlEntity.class, EntityDataSerializers.INT);

    private static final ResourceLocation[] TEXTURES = {
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_1.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_2.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_3.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_4.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_5.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_6.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_7.png"),
            new ResourceLocation("enchantment_expansion", "textures/entity/zombie_girl_8.png")
    };

    public DrownedGirlEntity(EntityType<? extends Drowned> type, Level level) {
        super(type, level);
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

    @Override
    public void aiStep() {
        super.aiStep();
        // 已驯服且非满血：每 2 秒尝试从随身背包啃一块生肉回血（与丧尸娘同节奏）
        if (!this.level().isClientSide && this.tamed
                && this.tickCount % 40L == 0L
                && this.getHealth() < this.getMaxHealth()) {
            consumeMeatForHealing();
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

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

    /** 驯服：记录主人、禁止自然消失、向主人发送对话框提示。 */
    private void tame(Player player) {
        this.tamed = true;
        this.ownerUuid = player.getUUID();
        this.setPersistenceRequired();
        if (player.level() instanceof ServerLevel) {
            player.displayClientMessage(
                    Component.translatable("chat.enchantment_expansion.drowned_girl.tamed"), true);
        }
    }

    public boolean isTamed() {
        return this.tamed;
    }

    @Nullable
    public UUID getOwnerUuid() {
        return this.ownerUuid;
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
        return ModSounds.HUSK_GIRL_IDLE.get();
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
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    public ResourceLocation getVariantTexture() {
        int variant = this.entityData.get(DATA_VARIANT);
        return TEXTURES[Math.max(0, Math.min(TEXTURES.length - 1, variant))];
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
        this.entityData.set(DATA_VARIANT, Math.max(0, Math.min(TEXTURES.length - 1,
                tag.getInt("Variant"))));
        this.tamed = tag.getBoolean("Tamed");
        this.ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.meatInventory.fromTag(tag.getList("MeatInventory", Tag.TAG_COMPOUND));
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
        // 先注册原版溺尸的游泳上浮、水中攻击、寻水、闲逛等移动 Goal。
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
        // 优先级 2：被攻击时反击水中亡灵
        this.targetSelector.addGoal(2, new FriendlyUndeadHurtByTargetGoal(this));
        // 优先级 3：天生仇恨水中亡灵
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
     * 排除玩家、丧尸娘、溺尸娘、车万女仆；其余生物均可作为目标。
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
                && target.isInWater()
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
