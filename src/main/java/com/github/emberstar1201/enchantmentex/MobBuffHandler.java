package com.github.emberstar1201.enchantmentex;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.github.emberstar1201.enchantmentex.mixin.CreeperSwellAccessor;

import java.util.UUID;

// ========================================================================
// 【原版怪物强化】事件处理器
//
// 覆盖用户需求：
//   1. 僵尸系：血量 25~30 随机；25% 概率穿甲（最多到钻石，无下界合金）+ 25% 补武器；
//      装备掉落概率提升到 15%；额外掉落铜锭/金锭/金粒/铁粒/铁锭
//   2. 小僵尸：碰撞箱放大（EntityEvent.Size，无需 Mixin）
//   3. 骷髅系：血量 25~30；25% 概率穿甲；装备掉落率 15%；拉弓更快（由 Mixin 完成）
//   4. 蜘蛛：命中玩家时在 4 格范围内结网
//   5. 苦力怕：蓄力时间更长（Mixin 改 maxSwell）+ 移速加快（属性修饰符）
//   6. 末影人：血量 40 → 50；弹射物可命中（由 Mixin 完成）
//
// 说明：
//   - 该类不带 @Mod.EventBusSubscriber 注解，由主类 EnchantmentExpansion
//     显式 MinecraftForge.EVENT_BUS.register(...) 注册（与 IllusoryFeastLootHandler 一致）。
//   - 血量/装备/掉落全部在服务端处理，客户端不做任何修改，避免双端不同步。
// ========================================================================
public final class MobBuffHandler {

    private MobBuffHandler() {
    }

    // 固定 UUID：同一个实体被重复处理（换维度、区块重载）时不会叠加修饰符
    private static final UUID ZOMBIE_HEALTH_UUID = UUID.fromString("a1b2c3d4-1111-4a01-9f01-000000000001");
    private static final UUID SKELETON_HEALTH_UUID = UUID.fromString("a1b2c3d4-1111-4a01-9f01-000000000002");
    private static final UUID ENDERMAN_HEALTH_UUID = UUID.fromString("a1b2c3d4-1111-4a01-9f01-000000000003");
    private static final UUID CREEPER_SPEED_UUID = UUID.fromString("a1b2c3d4-1111-4a01-9f01-000000000004");
    private static final UUID WITHER_HEALTH_UUID = UUID.fromString("a1b2c3d4-1111-4a01-9f01-000000000005");
    private static final UUID CAVE_SPIDER_HEALTH_UUID = UUID.fromString("a1b2c3d4-1111-4a01-9f01-000000000006");
    private static final UUID ILLAGER_HEALTH_UUID = UUID.fromString("a1b2c3d4-1111-4a01-9f01-000000000007");
    private static final UUID ILLAGER_ARMOR_UUID = UUID.fromString("a1b2c3d4-1111-4a01-9f01-000000000008");
    private static final UUID PHANTOM_SPEED_UUID = UUID.fromString("a1b2c3d4-1111-4a01-9f01-000000000009");
    private static final UUID RAVAGER_HEALTH_UUID = UUID.fromString("a1b2c3d4-1111-4a01-9f01-000000000010");

    private static final String HEALTH_MODIFIER_NAME = "enchantment_expansion:mob_buff_health";
    private static final String SPEED_MODIFIER_NAME = "enchantment_expansion:mob_buff_speed";

    // ====================================================================
    // 一、实体加入世界：改血量 / 装备 / 掉落概率（僵尸系、骷髅系、苦力怕、末影人）
    //
    // 为什么用 EntityJoinLevelEvent 而不是 MobSpawnEvent.FinalizeSpawn？
    //   ForgeEventFactory.onFinalizeSpawn 是「先触发事件，再调用 mob.finalizeSpawn」，
    //   也就是说 FinalizeSpawn 事件触发时，原版还没执行 populateDefaultEquipmentSlots，
    //   装备是空的；而 EntityJoinLevelEvent 在实体完整生成、装备填充完毕后触发。
    // ====================================================================
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!MobBuffConfig.enabled) {
            return;
        }
        // ---- 苦力怕：蓄力更久 ----
        // ★ 必须放在 isClientSide 判断之前：maxSwell 没有任何网络同步，
        //   客户端 tick() 与 getSwelling()（膨胀渲染 / 引爆音调）都会读它。
        //   只在服务端改的话，客户端会按默认 30 tick 提前闪白、音调提前拉满，
        //   然后「白着等」20 tick 才真正爆炸，观感是坏的。
        if (event.getEntity() instanceof Creeper creeper) {
            if (MobBuffConfig.creeperSwellTicks > 0) {
                ((CreeperSwellAccessor) (Object) creeper).setMaxSwell(MobBuffConfig.creeperSwellTicks);
            }
            if (event.getLevel().isClientSide()) {
                // 客户端只需要 maxSwell，移速由服务端的属性修饰符同步过去
                return;
            }
            if (event.loadedFromDisk()) {
                return;
            }
            applySpeedMultiplier(creeper, MobBuffConfig.creeperSpeedMultiplier);
            return;
        }

        // 只在服务端处理：EntityJoinLevelEvent 双端都会触发，客户端重复处理会浪费性能
        if (event.getLevel().isClientSide()) {
            return;
        }
        // 区块重载/换维度时实体是「从磁盘读出来的」，跳过可避免反复回满血
        if (event.loadedFromDisk()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        RandomSource random = mob.getRandom();
        if (mob instanceof CaveSpider caveSpider) {
            applyHealth(caveSpider, MobBuffConfig.caveSpiderHealth, CAVE_SPIDER_HEALTH_UUID);
        }
        if (mob instanceof Drowned drowned) {
            if (drowned.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()
                    && random.nextDouble() * 100.0D < MobBuffConfig.drownedTridentChance) {
                drowned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
            }
            if (!drowned.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()
                    && drowned.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.TRIDENT)) {
                drowned.setDropChance(EquipmentSlot.MAINHAND,
                        (float) (MobBuffConfig.drownedTridentDropChance / 100.0D));
            }
        }
        if (mob instanceof AbstractIllager illager) {
            applyHealth(illager, MobBuffConfig.illagerHealth, ILLAGER_HEALTH_UUID);
            AttributeInstance armor = illager.getAttribute(Attributes.ARMOR);
            if (armor != null) {
                armor.removeModifier(ILLAGER_ARMOR_UUID);
                armor.addPermanentModifier(new AttributeModifier(ILLAGER_ARMOR_UUID, HEALTH_MODIFIER_NAME,
                        MobBuffConfig.illagerArmor, AttributeModifier.Operation.ADDITION));
            }
            if (illager instanceof Pillager || illager instanceof Vindicator) {
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND}) {
                    illager.setDropChance(slot, (float) (MobBuffConfig.illagerWeaponDropChance / 100.0D));
                }
            }
        }
        if (mob instanceof Phantom phantom) {
            applySpeedMultiplier(phantom, MobBuffConfig.phantomSpeedMultiplier, PHANTOM_SPEED_UUID);
        }
        if (mob instanceof Ravager ravager) {
            applyHealth(ravager, MobBuffConfig.ravagerHealth, RAVAGER_HEALTH_UUID);
            applySpeedMultiplier(ravager, MobBuffConfig.ravagerSpeedMultiplier, PHANTOM_SPEED_UUID);
        }

        if (mob instanceof Zombie) {
            // ---- 僵尸系（僵尸 / 尸壳 / 溺尸 / 僵尸村民 / 僵尸猪灵）----
            applyHealth(mob, MobBuffConfig.rollZombieHealth(random), ZOMBIE_HEALTH_UUID);
            applyEquipmentDropChance(mob, MobBuffConfig.zombieEquipmentDropChance);
            buffZombieEquipment(mob, random);
        } else if (mob instanceof AbstractSkeleton) {
            // ---- 骷髅系（骷髅 / 流浪者 / 凋灵骷髅）----
            // 主手已被 AbstractSkeleton 强制填充为弓，因此这里的补装备逻辑不会动到弓
            applyHealth(mob, MobBuffConfig.rollSkeletonHealth(random), SKELETON_HEALTH_UUID);
            applyEquipmentDropChance(mob, MobBuffConfig.skeletonEquipmentDropChance);
            buffSkeletonEquipment(mob, random);
        } else if (mob instanceof EnderMan) {
            // ---- 末影人：血量 40 → 50 ----
            applyHealth(mob, MobBuffConfig.enderManHealth, ENDERMAN_HEALTH_UUID);
        }
    }

    // ====================================================================
    // 二、小僵尸碰撞箱放大
    //
    // 原理：LivingEntity.getDimensions(pose) 会用 getScale() 缩放体型，
    //       而 getScale() 对幼年实体返回 0.5，所以小僵尸碰撞箱只有 0.3×0.975。
    //       Entity 在 refreshDimensions() 里会触发 EntityEvent.Size，
    //       改这个事件的 newSize 即可放大碰撞箱（1.20.1 中该事件已标记废弃但仍生效）。
    //
    // ★ 注意：EntityEvent.Size 在「实体构造器」里也会触发一次，
    //   此处用 isBaby() 做守卫：构造阶段的僵尸一定不是幼年（幼年状态是
    //   finalizeSpawn 里才 setBaby(true) 的），因此不会误改。
    // ====================================================================
    @SubscribeEvent
    public static void onEntitySize(EntityEvent.Size event) {
        if (!MobBuffConfig.enabled || !MobBuffConfig.babyZombieEnlargeHitbox) {
            return;
        }
        double multiplier;
        if (event.getEntity() instanceof Zombie zombie && zombie.isBaby()) {
            multiplier = MobBuffConfig.babyZombieSizeMultiplier;
        } else if (event.getEntity() instanceof CaveSpider) {
            multiplier = MobBuffConfig.caveSpiderSizeMultiplier;
        } else if (event.getEntity() instanceof Phantom) {
            multiplier = MobBuffConfig.phantomSizeMultiplier;
        } else {
            return;
        }
        if (multiplier <= 1.0D) {
            return;
        }
        EntityDimensions base = event.getNewSize();
        float width = (float) (base.width * multiplier);
        float height = (float) (base.height * multiplier);
        event.setNewSize(EntityDimensions.scalable(width, height), false);
        // 视高同步放大，保证远程瞄准头部时判定正确
        event.setNewEyeHeight(height * 0.9F);
    }

    // ====================================================================
    // 三、僵尸系额外掉落物
    //
    // 用 LivingDropsEvent 在死亡掉落的 Collection<ItemEntity> 里直接追加，
    // 比改 loot table 更直观，且与其它模组对僵尸战利品表的修改互不冲突。
    // ====================================================================
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!MobBuffConfig.enabled) {
            return;
        }
        if (!(event.getEntity() instanceof Zombie zombie)) {
            return;
        }
        if (zombie.level().isClientSide()) {
            return;
        }
        RandomSource random = zombie.getRandom();
        addExtraDrop(event, zombie, Items.COPPER_INGOT, MobBuffConfig.zombieDropCopperIngotChance, random);
        addExtraDrop(event, zombie, Items.GOLD_INGOT, MobBuffConfig.zombieDropGoldIngotChance, random);
        addExtraDrop(event, zombie, Items.GOLD_NUGGET, MobBuffConfig.zombieDropGoldNuggetChance, random);
        addExtraDrop(event, zombie, Items.IRON_NUGGET, MobBuffConfig.zombieDropIronNuggetChance, random);
        addExtraDrop(event, zombie, Items.IRON_INGOT, MobBuffConfig.zombieDropIronIngotChance, random);
    }

    // ====================================================================
    // 四、蜘蛛结网
    //
    // 需求：「蜘蛛攻击你时在附近（4 个方块）生成蜘蛛网」。
    // 因此以「被攻击的玩家」为中心，在半径内随机挑选空气方块放置蜘蛛网。
    // ====================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!MobBuffConfig.enabled) {
            return;
        }
        if (event.getSource().getEntity() instanceof WitherSkeleton witherSkeleton
                && witherSkeleton.getHealth() <= witherSkeleton.getMaxHealth() * 0.5F) {
            event.setAmount(event.getAmount()
                    * (float) MobBuffConfig.witherSkeletonLowHealthDamageMultiplier);
        }
        if (!MobBuffConfig.spiderWebEnabled) {
            return;
        }
        // 攻击者必须是蜘蛛（含洞穴蜘蛛）
        if (!(event.getSource().getEntity() instanceof Spider)) {
            return;
        }
        // 只在玩家被咬时触发（避免怪物互殴时满地蜘蛛网）
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.random.nextDouble() * 100.0D >= MobBuffConfig.spiderWebChance) {
            return;
        }
        placeCobwebs(serverLevel, target);
    }

    // ====================================================================
    // 内部工具方法
    // ====================================================================

    /**
     * 把实体最大血量调整到目标值（用固定 UUID 的 ADDITION 修饰符，
     * 这样不会破坏原版血量基础值，也不会与其它模组的修饰符冲突）。
     */
    private static void applyHealth(LivingEntity mob, double targetHealth, UUID modifierId) {
        AttributeInstance maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        maxHealth.removeModifier(modifierId);
        double delta = targetHealth - maxHealth.getBaseValue();
        if (Math.abs(delta) > 0.001D) {
            maxHealth.addPermanentModifier(new AttributeModifier(
                    modifierId, HEALTH_MODIFIER_NAME, delta, AttributeModifier.Operation.ADDITION));
        }
        // 刚生成的怪物直接回满血，避免出现「血量上限涨了但血条只有一半」
        mob.setHealth(mob.getMaxHealth());
    }

    /** 提升实体身上装备的掉落概率（原版默认 8.5%） */
    private static void applyEquipmentDropChance(Mob mob, double percent) {
        float chance = (float) (percent / 100.0D);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ARMOR || slot.getType() == EquipmentSlot.Type.HAND) {
                mob.setDropChance(slot, chance);
            }
        }
    }

    /** 僵尸系：25% 概率穿甲 + 25% 概率补武器 */
    private static void buffZombieEquipment(Mob mob, RandomSource random) {
        if (random.nextDouble() * 100.0D < MobBuffConfig.zombieEquipChance) {
            populateArmor(mob, random, MobBuffConfig.zombieEquipPieceChance);
        }
        // 主手已有武器（僵尸猪灵的金剑、溺尸的三叉戟等）时不覆盖
        if (mob.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()
                && random.nextDouble() * 100.0D < MobBuffConfig.zombieWeaponChance) {
            mob.setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(random.nextBoolean() ? Items.IRON_SWORD : Items.IRON_SHOVEL));
        }
    }

    /** 骷髅系：25% 概率穿甲（主手永远保留原版的弓） */
    private static void buffSkeletonEquipment(Mob mob, RandomSource random) {
        if (random.nextDouble() * 100.0D < MobBuffConfig.skeletonEquipChance) {
            populateArmor(mob, random, MobBuffConfig.skeletonEquipPieceChance);
        }
    }

    /**
     * 按原版逻辑填充盔甲：
     *   - 材质等级 i：先取 0~1，再三次 9.5% 概率升级，最高到钻石
     *     （Mob.getEquipmentForSlot 只覆盖到钻石级，天然「不含下界合金」）
     *   - 穿戴顺序 FEET → LEGS → CHEST → HEAD，与原版一致，
     *     出现「只穿了靴子和护腿」这种半套装备的观感
     *   - 只在槽位为空时填充，不会把原版已给的装备降级
     */
    private static void populateArmor(Mob mob, RandomSource random, double pieceChancePercent) {
        int materialTier = random.nextInt(2);
        for (int i = 0; i < 3; i++) {
            if (random.nextFloat() < 0.095F) {
                materialTier++;
            }
        }
        boolean firstSlot = true;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) {
                continue;
            }
            // 与原版一致：从第二件开始，每件都要过一次概率，失败就停止后续穿戴
            if (!firstSlot && random.nextDouble() * 100.0D >= pieceChancePercent) {
                break;
            }
            firstSlot = false;
            if (!mob.getItemBySlot(slot).isEmpty()) {
                continue;
            }
            Item item = Mob.getEquipmentForSlot(slot, materialTier);
            if (item != null) {
                mob.setItemSlot(slot, new ItemStack(item));
            }
        }
    }

    /** 苦力怕等：给移速加一个固定 UUID 的乘算修饰符 */
    private static void applySpeedMultiplier(Mob mob, double multiplier) {
        applySpeedMultiplier(mob, multiplier, CREEPER_SPEED_UUID);
    }

    private static void applySpeedMultiplier(Mob mob, double multiplier, UUID modifierId) {
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(modifierId);
        double amount = multiplier - 1.0D;
        if (amount > 0.0001D) {
            speed.addPermanentModifier(new AttributeModifier(
                    modifierId, SPEED_MODIFIER_NAME, amount, AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    /** 按百分比概率追加一件掉落物 */
    private static void addExtraDrop(LivingDropsEvent event, LivingEntity killed, Item item,
                                     double chancePercent, RandomSource random) {
        if (chancePercent <= 0.0D) {
            return;
        }
        if (random.nextDouble() * 100.0D >= chancePercent) {
            return;
        }
        event.getDrops().add(new ItemEntity(
                killed.level(),
                killed.getX(),
                killed.getY() + 0.3D,
                killed.getZ(),
                new ItemStack(item)));
    }

    /** 在被攻击玩家周围半径内随机放置蜘蛛网（只替换空气，不封死玩家脚下） */
    private static void placeCobwebs(ServerLevel level, Player target) {
        RandomSource random = level.random;
        BlockPos center = target.blockPosition();
        int radius = MobBuffConfig.spiderWebRadius;
        int remaining = MobBuffConfig.spiderWebCount;
        // 尝试次数给足，避免随机落点全被非空气方块占满时一个网都放不出来
        int maxAttempts = remaining * 16;

        for (int attempt = 0; attempt < maxAttempts && remaining > 0; attempt++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            int dy = random.nextInt(3) - 1;
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            BlockPos pos = center.offset(dx, dy, dz);
            // 不把网直接扣在玩家身上，否则玩家会被瞬间定住、体验过差
            if (pos.equals(center)) {
                continue;
            }
            if (!level.getBlockState(pos).isAir()) {
                continue;
            }
            level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 3);
            remaining--;
        }
    }
}
