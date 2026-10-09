package com.github.emberstar1201.enchantmentex.entity;

import com.github.emberstar1201.enchantmentex.ModAdvancements;
import com.github.emberstar1201.enchantmentex.sound.ModSounds;
import com.mojang.datafixers.util.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 幸存者少女：丧尸娘 / 溺尸娘被「生命之星」治愈后变回的人类少女。
 *
 * <p>直接继承 {@link ZombieGirlEntity}，因此完整保留：
 * <ul>
 *   1. 驯服 / 主人 / 坐下系统、64 格随身背包与 GUI；</li>
 *   2. 原版 1.1 倍（幼体 1.2 倍）移速加成、武器与护甲装备槽（属性照常生效）；</li>
 *   3. 协助主人、只攻击敌对亡灵的目标白名单，以及弓箭远程 AI；</li>
 *   4. 全套闲时对话与星尘收容兼容性（收容类型单独标记为 survivor_girl）。</li>
 * </ul>
 *
 * <p>与亡灵形态的差异：
 * <ul>
 *   1. {@link #getMobType()} 返回 {@link MobType#UNDEFINED}，是真正的人类：
 *      中毒等效果正常结算，不再受亡灵杀手额外伤害；</li>
 *   2. 材质使用 human_girl.png ~ human_girl_18.png（与丧尸娘 0~18 号变种一一对应，
 *      共 19 张用户自制皮肤）；</li>
 *   3. 背包自动进食从「只吃生肉」扩展为「任意食物」，
 *      按营养价值回血，并且只继承食物的正面药水效果
 *      （金苹果 / 附魔金苹果的再生、抗性、防火、吸收照常给予；
 *      腐肉的饥饿、蜘蛛眼 / 河豚的中毒等负面效果一律不施加）。</li>
 * </ul>
 */
public class SurvivorGirlEntity extends ZombieGirlEntity {
    /** 治愈前形态 NBT 值：普通丧尸娘。 */
    private static final String ORIGIN_ZOMBIE_GIRL = "zombie_girl";
    /** 治愈前形态 NBT 值：溺尸娘。 */
    private static final String ORIGIN_DROWNED_GIRL = "drowned_girl";

    /**
     * 治愈前的亡灵形态，被感染时变回它。
     * 默认按普通丧尸娘处理（/summon 等非治愈途径生成的幸存者也有合理的还原形态）。
     */
    private String originForm = ORIGIN_ZOMBIE_GIRL;

    /** 人类幼体长达成人所需时间：20 分钟 = 24000 tick（与原版小村民成长时间一致）。 */
    private static final int GROW_UP_TICKS = 24000;

    /**
     * 剩余成长 tick：幼体时每 tick 递减，归零即长大；-1 表示成年体无需计时。
     * 用「剩余时间倒计时」而非世界绝对时刻：普通存档读档、星尘收容冻结
     * （收容期间实体不 tick，剩余值天然不减少）都能正确续算。
     */
    private int growUpTicks = -1;

    /** 已完成人类化的皮肤数量：human_girl.png 与 human_girl_1 ~ human_girl_18，共 19 张。 */
    private static final int HUMAN_TEXTURE_COUNT = 19;

    /** 人类形态材质表（下标与丧尸娘变种编号一一对应）。 */
    private static final List<ResourceLocation> HUMAN_TEXTURES = new ArrayList<>();

    static {
        HUMAN_TEXTURES.add(new ResourceLocation("enchantment_expansion",
                "textures/entity/human_girl.png"));
        for (int i = 1; i < HUMAN_TEXTURE_COUNT; i++) {
            HUMAN_TEXTURES.add(new ResourceLocation("enchantment_expansion",
                    "textures/entity/human_girl_" + i + ".png"));
        }
    }

    public SurvivorGirlEntity(EntityType<? extends net.minecraft.world.entity.monster.Zombie> type,
                              Level level) {
        super(type, level);
    }

    // ====================================================================
    // 治愈转换：丧尸娘 / 溺尸娘 → 幸存者少女
    // ====================================================================

    /**
     * 把一只丧尸娘或溺尸娘治愈成人类少女。
     *
     * <p>直接复用实体 NBT：驯服状态、主人、皮肤变种、64 格背包、装备 / 盔甲、
     * 幼体状态、生命值、对话计时等全部随存档继承，仅重新生成实体 UUID。
     * 转换后永久持久化（不会自然消失）。变身瞬间的粒子 / 音效反馈由
     * {@link GirlCureHandler} 的三阶段收尾负责，本方法只做数据迁移。</p>
     *
     * @param curer 执行治愈的玩家；若被治愈者原本未驯服，幸存者少女将直接归属给该玩家
     * @return 转换生成的幸存者少女；非服务端或生成失败时返回 null
     */
    @Nullable
    public static SurvivorGirlEntity cureFrom(LivingEntity girl, Player curer) {
        if (!(girl.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        CompoundTag saved = girl.saveWithoutId(new CompoundTag());
        // 以新实体身份重生，避免世界中残留旧 UUID 引用
        saved.remove("UUID");
        // 记录治愈前的亡灵形态：被原版亡灵攻击感染时，要变回原来的她
        saved.putString("HumanOrigin",
                girl instanceof DrownedGirlEntity ? ORIGIN_DROWNED_GIRL : ORIGIN_ZOMBIE_GIRL);

        SurvivorGirlEntity human = ModEntities.SURVIVOR_GIRL.get().create(girl.level());
        if (human == null) {
            return null;
        }
        human.load(saved);
        // 治愈野生（未驯服）亡灵时，幸存者少女直接归属治愈者：
        // 否则人类形态仍处于野生状态，既打不开背包也无法被星尘收容。
        // 已驯服个体保留原主人，不抢归属。
        if (!human.isTamed() && curer != null) {
            human.bondTo(curer);
        }
        human.moveTo(girl.getX(), girl.getY(), girl.getZ(),
                girl.getYRot(), girl.getXRot());
        // 治愈产物是珍贵伙伴，不允许自然消失
        human.setPersistenceRequired();
        if (!serverLevel.addFreshEntity(human)) {
            return null;
        }

        // 治愈成就「驶向第2次生命」：发给执行治愈的玩家
        if (curer instanceof ServerPlayer serverCurer) {
            ModAdvancements.award(serverCurer, ModAdvancements.SECOND_LIFE);
        }

        girl.discard();
        return human;
    }

    // ====================================================================
    // 人类属性：不再是亡灵
    // ====================================================================

    /** 变回人类后使用普通生物类型：中毒正常生效，不再被亡灵杀手克制。 */
    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    // ====================================================================
    // 感染机制：被原版敌对亡灵打到残血（当前血量低于最大生命值 25%）时，
    // 立刻变回治愈前的亡灵形态（任意难度都适用，不做原版僵尸感染概率门）
    // ====================================================================

    /** 感染血量阈值：当前血量严格低于最大生命值的 25% 即被感染。 */
    private static final float INFECT_HEALTH_RATIO = 0.25F;

    /**
     * 伤害实际生效后，若来源是敌对亡灵且这一击把她打到残血
     * （当前血量 < 最大生命值 × 25%），立刻感染还原。
     * 非亡灵来源（坠落 / 火焰 / 玩家攻击等）即使打到残血也不会感染。
     *
     * <p>睡着时被打不惊醒：夜间受击后的重新躺床由共用的 GirlSleepGoal
     * 下一 tick 自动处理，此处只管感染判定。</p>
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && !this.level().isClientSide
                && source.getEntity() instanceof LivingEntity attacker
                && isInfectingUndead(attacker)
                && this.getHealth() > 0.0F
                && this.getHealth() < this.getMaxHealth() * INFECT_HEALTH_RATIO) {
            infectBack(attacker);
        }
        return result;
    }

    /**
     * 是否为会造成感染的「原版敌对亡灵」：
     * 僵尸 / 骷髅 / 溺尸 / 尸壳等亡灵怪物，以及亡灵属性的敌对生物；
     * 显式排除丧尸娘、溺尸娘与幸存者少女——她们自己的亡灵形态不会攻击并感染同伴。
     */
    private static boolean isInfectingUndead(LivingEntity entity) {
        return entity instanceof Enemy
                && entity.getMobType() == MobType.UNDEAD
                && !(entity instanceof ZombieGirlEntity)
                && !(entity instanceof DrownedGirlEntity);
    }

    /**
     * 变回治愈前的亡灵形态（丧尸娘或溺尸娘）。
     * 与治愈同理：整份 NBT 继承（驯服 / 主人 / 背包 / 装备 / 当前血量 / 对话计时），
     * 原位重生并重新持久化，播放原版感染音效与黑烟反馈。
     */
    private void infectBack(LivingEntity attacker) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        CompoundTag saved = this.saveWithoutId(new CompoundTag());
        saved.remove("UUID");

        // 注意：溺尸娘继承 Drowned 而非 ZombieGirlEntity，两者共同父类型取 Mob
        Mob reverted = ORIGIN_DROWNED_GIRL.equals(this.originForm)
                ? ModEntities.DROWNED_GIRL.get().create(this.level())
                : ModEntities.ZOMBIE_GIRL.get().create(this.level());
        if (reverted == null) {
            return;
        }
        reverted.load(saved);
        reverted.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
        // 感染后仍是主人的伙伴，不允许自然消失
        reverted.setPersistenceRequired();
        if (!serverLevel.addFreshEntity(reverted)) {
            return;
        }

        // 感染反馈：黑烟 + 原版僵尸感染村民音效
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                reverted.getX(), reverted.getY(0.75D), reverted.getZ(),
                24, 0.3D, 0.6D, 0.3D, 0.0D);
        serverLevel.playSound(null, reverted.blockPosition(),
                SoundEvents.ZOMBIE_INFECT, reverted.getSoundSource(), 1.0F, 1.0F);

        // 感染完成后，攻击者不应再追击已变回亡灵形态的少女：
        // 亡灵之间互不攻击，目标选择器也只搜 SurvivorGirlEntity，
        // 这里若把攻击者目标设为 reverted 会导致该攻击者持续追杀，
        // 而其它亡灵因目标选择器自然失效而停手——表现为只有凶手还在打。
        // 正确做法是清空攻击者目标，让它回归普通索敌。
        if (attacker instanceof Mob mob) {
            mob.setTarget(null);
        }
        this.discard();
    }

    /**
     * 每 tick 在丧尸娘逻辑之后追加人类幼体成长倒计时。
     * 注意必须在服务端推进；归零瞬间长达成人。
     */
    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            return;
        }
        if (this.isBaby() && this.growUpTicks > 0) {
            this.growUpTicks--;
            if (this.growUpTicks == 0) {
                growUpToAdult();
            }
        }
    }

    /**
     * 长达成人：清除幼体标记。
     * 移速修饰符由父类 syncMovementSpeed 在下一 tick 自动从 1.2 倍切回 1.1 倍，
     * 碰撞箱与渲染缩放随 setBaby 刷新；播放好感粒子作为成长反馈。
     */
    private void growUpToAdult() {
        this.growUpTicks = -1;
        this.setBaby(false);
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    this.getX(), this.getY(0.75D), this.getZ(),
                    24, 0.3D, 0.6D, 0.3D, 0.0D);
        }
    }

    /** 治愈前形态与成长剩余时间随存档持久化（含星尘收容 / 区块卸载后再被感染也能正确还原）。 */
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("HumanOrigin", this.originForm);
        tag.putInt("HumanGrowUpTicks", this.growUpTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HumanOrigin")) {
            this.originForm = ORIGIN_DROWNED_GIRL.equals(tag.getString("HumanOrigin"))
                    ? ORIGIN_DROWNED_GIRL : ORIGIN_ZOMBIE_GIRL;
        }
        if (tag.contains("HumanGrowUpTicks")) {
            // 存档 / 星尘放出：按保存的剩余时间继续成长
            this.growUpTicks = tag.getInt("HumanGrowUpTicks");
        } else {
            // 从亡灵形态 NBT 治愈而来（亡灵 NBT 没有此键）：幼体重新获得完整 20 分钟成长期
            this.growUpTicks = this.isBaby() ? GROW_UP_TICKS : -1;
        }
    }

    // ====================================================================
    // 人类材质：human_girl 系列（0~18 共 19 张；异常编号回落到 0 号默认皮肤）
    // ====================================================================

    @Override
    public ResourceLocation getVariantTexture() {
        int variant = getVariant();
        if (variant < 0 || variant >= HUMAN_TEXTURES.size()) {
            return HUMAN_TEXTURES.get(0);
        }
        return HUMAN_TEXTURES.get(variant);
    }

    /** /summon 或刷怪蛋生成时在全部 19 张人类皮肤中随机。 */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                       net.minecraft.world.entity.MobSpawnType reason,
                                       @Nullable SpawnGroupData spawnData,
                                       @Nullable CompoundTag dataTag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
        setVariant(this.random.nextInt(HUMAN_TEXTURE_COUNT));
        return result;
    }

    // ====================================================================
    // 通用进食：背包残血自动吃任意食物，只继承正面药水效果
    // ====================================================================

    /** 主人可以喂任意可食用物品（不再限于生肉）。 */
    @Override
    protected boolean canEatFromOwner(ItemStack stack) {
        return stack.getItem().getFoodProperties() != null;
    }

    /**
     * 主人喂食结算：按食物营养价值回血（每点营养 = 半颗心，
     * 与生肉 8 点营养回 2 颗心的旧数值一致），并给予食物附带的正面效果。
     */
    @Override
    protected void applyEatenFood(ItemStack stack, boolean creative) {
        FoodProperties food = stack.getItem().getFoodProperties();
        if (food == null) {
            return;
        }
        applyFood(stack, food, !creative);
    }

    /**
     * 残血自动进食：每 40 tick 由父类 aiStep 调用，扫描背包中第一件可食用物品。
     * 与丧尸娘只啃生肉不同，幸存者少女会吃面包、苹果、熟食等任意食物。
     */
    @Override
    protected void consumeMeatForHealing() {
        var backpack = getMeatInventory();
        for (int slot = 0; slot < backpack.getContainerSize(); slot++) {
            ItemStack foodStack = backpack.getItem(slot);
            FoodProperties food = foodStack.getItem().getFoodProperties();
            if (food != null) {
                applyFood(foodStack, food, true);
                this.playSound(getEatSound(), 1.0F, 1.25F);
                return;
            }
        }
    }

    /** 进食统一结算：扣物品、按营养回血、只施加正面药水效果、记录进食时刻。 */
    private void applyFood(ItemStack foodStack, FoodProperties food, boolean consume) {
        if (consume) {
            foodStack.shrink(1);
        }
        // 营养价值 → 回血量：8 点营养的生肉 / 牛排回 4 点生命（2 颗心），与亡灵形态一致
        this.heal(food.getNutrition() * 0.5F);
        applyBeneficialFoodEffects(food);
        this.lastMealTick = this.tickCount;
    }

    /**
     * 只继承食物的正面药水效果，并尊重原版配置的施加概率。
     * 金苹果（再生 / 伤害吸收）、附魔金苹果（再生 V / 抗性 / 防火 / 吸收 IV）
     * 都会正常生效；腐肉饥饿、生鸡肉饥饿、蜘蛛眼与河豚的中毒等负面效果不施加。
     */
    private void applyBeneficialFoodEffects(FoodProperties food) {
        for (Pair<MobEffectInstance, Float> entry : food.getEffects()) {
            MobEffectInstance effect = entry.getFirst();
            if (effect.getEffect().getCategory() == MobEffectCategory.BENEFICIAL
                    && this.random.nextFloat() < entry.getSecond()) {
                this.addEffect(new MobEffectInstance(effect));
            }
        }
    }

    /** 人类进食使用原版通用进食音，不再使用亡灵形态的啃肉音。 */
    @Override
    protected SoundEvent getEatSound() {
        return SoundEvents.GENERIC_EAT;
    }

    /**
     * 人类形态闲置时播放自己的呼吸声（human_girl.idle，idle_1 / idle_3 随机），
     * 不再沿用丧尸娘的低语；睡着时保持安静（沿用父类规则）。
     */
    @Override
    protected SoundEvent getAmbientSound() {
        return this.isSleeping() ? null : ModSounds.HUMAN_GIRL_IDLE.get();
    }

    // ====================================================================
    // 文案覆写：背包标题 / 驯服与拒绝提示 / 聊天前缀
    // ====================================================================

    @Override
    protected Component getInventoryTitle() {
        return Component.translatable("container.enchantment_expansion.survivor_girl_inventory");
    }

    @Override
    protected String getTamedMessageKey() {
        return "chat.enchantment_expansion.survivor_girl.tamed";
    }

    @Override
    protected String getInventoryDeniedMessageKey() {
        return "chat.enchantment_expansion.survivor_girl.inventory_denied";
    }

    @Override
    protected String getChatPrefix() {
        return "§b【幸存者少女】§f";
    }

    // ====================================================================
    // 独立对话文本：治愈后的人类少女有自己的一套台词，不再沿用丧尸娘池。
    // 每个池子目前只有一条（以后可直接往数组里追加翻译键，随机抽取逻辑不变）。
    // 闲时 / 时间感应 / 受伤冷却统一 10 分钟（12000 tick），死亡遗言死亡时触发一次。
    // 所有冷却字段都继承自丧尸娘，星尘收容时按既有 NBT 机制一并冻结。
    // ====================================================================

    /** 幸存者少女专属台词翻译键前缀。 */
    private static final String CHAT_PREFIX_KEY = "chat.enchantment_expansion.survivor_girl";

    /** 闲时台词（目前 1 条）。 */
    private static final String[] SURVIVOR_IDLE_KEYS = {
            CHAT_PREFIX_KEY + ".idle.0"
    };
    /** 日出台词。 */
    private static final String[] SURVIVOR_SUNRISE_KEYS = {
            CHAT_PREFIX_KEY + ".time.sunrise.0"
    };
    /** 正午台词。 */
    private static final String[] SURVIVOR_NOON_KEYS = {
            CHAT_PREFIX_KEY + ".time.noon.0"
    };
    /** 夜晚台词。 */
    private static final String[] SURVIVOR_NIGHT_KEYS = {
            CHAT_PREFIX_KEY + ".time.night.0"
    };
    /** 午夜台词。 */
    private static final String[] SURVIVOR_MIDNIGHT_KEYS = {
            CHAT_PREFIX_KEY + ".time.midnight.0"
    };
    /** 受伤台词。 */
    private static final String[] SURVIVOR_HURT_KEYS = {
            CHAT_PREFIX_KEY + ".hurt.0"
    };
    /** 死亡遗言。 */
    private static final String[] SURVIVOR_DEATH_KEYS = {
            CHAT_PREFIX_KEY + ".death.0"
    };

    /** 幸存者少女闲聊 / 时间感应 / 受伤台词的统一冷却：30 秒（600 tick）。 */
    private static final int SURVIVOR_CHAT_COOLDOWN = 600;

    @Override
    protected String[] getIdleChatKeys() {
        return SURVIVOR_IDLE_KEYS;
    }

    @Override
    protected String[] getHurtChatKeys() {
        return SURVIVOR_HURT_KEYS;
    }

    @Override
    protected String[] getDeathChatKeys() {
        return SURVIVOR_DEATH_KEYS;
    }

    @Override
    protected String[] getTimeChatKeys(TimeChatPhase phase) {
        return switch (phase) {
            case SUNRISE -> SURVIVOR_SUNRISE_KEYS;
            case NOON -> SURVIVOR_NOON_KEYS;
            case NIGHT -> SURVIVOR_NIGHT_KEYS;
            case MIDNIGHT -> SURVIVOR_MIDNIGHT_KEYS;
        };
    }

    /** 受伤吐槽同样使用 10 分钟冷却（丧尸娘是 30 秒，人类台词更少，统一节奏）。 */
    @Override
    protected int getHurtChatCooldown() {
        return SURVIVOR_CHAT_COOLDOWN;
    }

    /** 人类形态不再触发村庄侦查 / 战斗胜利 / 意识侵蚀等丧尸娘专属对话。 */
    @Override
    protected boolean enableZombieSpecificChats() {
        return false;
    }

    // 睡眠相关行为（夜晚找床、无视怪物、受击回躺、开门、睡姿、不闲聊、
    // 不阻止玩家休息、不可推动）全部由 ZombieGirlEntity 基类与共用的
    // GirlSleepGoal / GirlBedWakeHandler 提供，本类无需再覆写。

}
