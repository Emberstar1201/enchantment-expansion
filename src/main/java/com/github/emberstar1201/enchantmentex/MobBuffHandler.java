package com.github.emberstar1201.enchantmentex;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import com.github.emberstar1201.enchantmentex.enchantment.ModEnchantments;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.github.emberstar1201.enchantmentex.mixin.CreeperSwellAccessor;

import java.util.UUID;

// ========================================================================
// 【原版怪物强化】事件处理器
//
// 覆盖用户需求：
//   1. 僵尸系：血量 25~30 随机；指定僵尸变种完整穿戴铁至钻石盔甲；
//      普通僵尸与尸壳有 75% 概率装备附魔铁剑，装备与武器掉落率为 75%；
//      额外掉落铜锭/金锭/金粒/铁粒/铁锭
//   2. 小僵尸：碰撞箱放大（EntityEvent.Size，无需 Mixin）
//   3. 骷髅系：血量 25~30；完整穿戴铁至钻石盔甲；装备与武器掉落率为 75%；拉弓更快（由 Mixin 完成）
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
    private static final UUID CAVE_SPIDER_HEALTH_UUID = UUID.fromString("a1b2c3d4-1111-4a01-9f06-000000000006");
    private static final UUID ILLAGER_HEALTH_UUID = UUID.fromString("a1b2c3d4-1111-4a01-9f07-000000000007");
    private static final UUID ILLAGER_ARMOR_UUID = UUID.fromString("a1b2c3d4-1111-4a01-9f08-000000000008");
    private static final UUID PHANTOM_SPEED_UUID = UUID.fromString("a1b2c3d4-1111-4a01-9f09-000000000009");
    private static final UUID RAVAGER_HEALTH_UUID = UUID.fromString("a1b2c3d4-1111-4a01-9f10-000000000010");
    private static final UUID RAVAGER_SPEED_UUID = UUID.fromString("a1b2c3d4-1111-4a01-9f11-000000000011");

    private static final ResourceLocation WOODLAND_MANSION = ResourceLocation.withDefaultNamespace("woodland_mansion");
    private static final String MANSION_ILLUSIONER_TAG = "enchantment_expansion_mansion_illusioner";
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
        if (event.getEntity() instanceof Illusioner illusioner && !isInsideWoodlandMansion(illusioner)) {
            event.setCanceled(true);
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
            applySpeedMultiplier(creeper, MobBuffConfig.creeperSpeedMultiplier, CREEPER_SPEED_UUID);
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
        if (!MobBuffConfig.enabled) {
            return;
        }
        if (mob instanceof Evoker && event.getLevel() instanceof ServerLevel serverLevel) {
            spawnMansionIllusioner(serverLevel, mob);
        }

        RandomSource random = mob.getRandom();

        if (mob instanceof Ghast) {
            // 恶魂只需要 Mixin 调整 AI 与弹射物速度，实体加入事件无需重复处理。
            return;
        } else if (mob instanceof ZombifiedPiglin zombifiedPiglin) {
            applyHealth(zombifiedPiglin, MobBuffConfig.rollZombieHealth(random), ZOMBIE_HEALTH_UUID);
            applyEquipmentDropChance(zombifiedPiglin, 15.0D);
            populateArmor(zombifiedPiglin, random);
        } else if (mob instanceof CaveSpider) {
            applyHealth(mob, MobBuffConfig.caveSpiderHealth, CAVE_SPIDER_HEALTH_UUID);
        } else if (mob instanceof Drowned drowned) {
            applyHealth(mob, MobBuffConfig.rollZombieHealth(random), ZOMBIE_HEALTH_UUID);
            if (drowned.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()
                    && random.nextDouble() * 100.0D < 75.0D) {
                drowned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
            }
            applyEquipmentDropChance(drowned, 15.0D);
            if (drowned.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.TRIDENT)) {
                drowned.setDropChance(EquipmentSlot.MAINHAND, 0.15F);
            }
        } else if (mob instanceof Zombie) {
            // ---- 僵尸系：指定变种必须获得完整铁质起步套装 ----
            applyHealth(mob, MobBuffConfig.rollZombieHealth(random), ZOMBIE_HEALTH_UUID);
            applyEquipmentDropChance(mob, 15.0D);
            if (mob instanceof Husk || mob.getType() == net.minecraft.world.entity.EntityType.ZOMBIE) {
                buffZombieEquipment(mob, random);
            }
        } else if (mob instanceof AbstractSkeleton) {
            // ---- 骷髅系（骷髅 / 流浪者 / 凋灵骷髅）----
            applyHealth(mob, MobBuffConfig.rollSkeletonHealth(random), SKELETON_HEALTH_UUID);
            applyEquipmentDropChance(mob, 15.0D);
            buffSkeletonEquipment(mob, random);
        } else if (mob instanceof Pillager pillager) {
            applyHealth(pillager, MobBuffConfig.pillagerHealth, ILLAGER_HEALTH_UUID);
            applyArmor(pillager, MobBuffConfig.pillagerArmor, ILLAGER_ARMOR_UUID);
            applyEquipmentDropChance(pillager, 15.0D);
        } else if (mob instanceof Vindicator vindicator) {
            applyHealth(vindicator, MobBuffConfig.vindicatorHealth, ILLAGER_HEALTH_UUID);
            applyArmor(vindicator, MobBuffConfig.vindicatorArmor, ILLAGER_ARMOR_UUID);
            applyEquipmentDropChance(vindicator, 15.0D);
        } else if (mob instanceof Evoker evoker) {
            applyHealth(evoker, MobBuffConfig.evokerHealth, ILLAGER_HEALTH_UUID);
            applyArmor(evoker, MobBuffConfig.evokerArmor, ILLAGER_ARMOR_UUID);
        } else if (mob instanceof Phantom phantom) {
            applySpeedMultiplier(phantom, MobBuffConfig.phantomSpeedMultiplier, PHANTOM_SPEED_UUID);
        } else if (mob instanceof Ravager ravager) {
            applyHealth(ravager, MobBuffConfig.ravagerHealth, RAVAGER_HEALTH_UUID);
            applySpeedMultiplier(ravager, MobBuffConfig.ravagerSpeedMultiplier, RAVAGER_SPEED_UUID);
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
        if (!MobBuffConfig.enabled) {
            return;
        }
        if (event.getEntity() instanceof Zombie zombie && zombie.isBaby()
                && MobBuffConfig.babyZombieEnlargeHitbox) {
            double multiplier = MobBuffConfig.babyZombieSizeMultiplier;
            if (multiplier > 1.0D) {
                EntityDimensions base = event.getNewSize();
                float width = (float) (base.width * multiplier);
                float height = (float) (base.height * multiplier);
                event.setNewSize(EntityDimensions.scalable(width, height), false);
                event.setNewEyeHeight(height * 0.9F);
            }
            return;
        }
        if (event.getEntity() instanceof CaveSpider) {
            double multiplier = MobBuffConfig.caveSpiderSizeMultiplier;
            if (multiplier > 1.0D) {
                EntityDimensions base = event.getNewSize();
                event.setNewSize(EntityDimensions.scalable(
                        (float) (base.width * multiplier),
                        (float) (base.height * multiplier)), false);
            }
        } else if (event.getEntity() instanceof Phantom) {
            double multiplier = MobBuffConfig.phantomSizeMultiplier;
            if (multiplier > 1.0D) {
                EntityDimensions base = event.getNewSize();
                event.setNewSize(EntityDimensions.scalable(
                        base.width,
                        (float) (base.height * multiplier)), false);
            }
        }
    }

    // ====================================================================
    // 三、额外掉落物
    //
    // 用 LivingDropsEvent 在死亡掉落的 Collection<ItemEntity> 里直接追加，
    // 比改 loot table 更直观，且与其它模组对僵尸战利品表的修改互不冲突。
    // ====================================================================
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!MobBuffConfig.enabled) {
            return;
        }
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        LivingEntity killed = event.getEntity();
        RandomSource random = killed.getRandom();
        if (killed instanceof Illusioner illusioner && isInsideWoodlandMansion(illusioner)) {
            event.getDrops().clear();
            addFixedDrop(event, illusioner, Items.BOW, 1);
            addFixedDrop(event, illusioner, Items.ARROW, 8 + random.nextInt(9));
            ItemStack invisibilityPotion = new ItemStack(Items.POTION);
            invisibilityPotion.getOrCreateTag().putString("Potion", "minecraft:invisibility");
            addFixedDrop(event, illusioner, invisibilityPotion);
            addFixedDrop(event, illusioner, Items.EMERALD, 1 + random.nextInt(3));
            return;
        }
        if (killed instanceof Zombie zombie) {
            addExtraDrop(event, zombie, Items.COPPER_INGOT, MobBuffConfig.zombieDropCopperIngotChance, random);
            addExtraDrop(event, zombie, Items.DIAMOND, MobBuffConfig.zombieDropDiamondChance, random);
            addExtraDrop(event, zombie, Items.GOLD_INGOT, MobBuffConfig.zombieDropGoldIngotChance, random);
            addExtraDrop(event, zombie, Items.GOLD_NUGGET, MobBuffConfig.zombieDropGoldNuggetChance, random);
            addExtraDrop(event, zombie, Items.IRON_INGOT, MobBuffConfig.zombieDropIronIngotChance, random);
            addExtraDrop(event, zombie, Items.IRON_NUGGET, MobBuffConfig.zombieDropIronNuggetChance, random);
        }
        if (killed instanceof Pillager || killed instanceof Vindicator || killed instanceof Evoker) {
            for (int i = 0; i < MobBuffConfig.illagerEmeraldCount; i++) {
                addExtraDrop(event, killed, Items.EMERALD, MobBuffConfig.illagerEmeraldChance, random);
            }
        }
        if (killed instanceof Phantom) {
            int min = Math.min(MobBuffConfig.phantomMembraneMin, MobBuffConfig.phantomMembraneMax);
            int max = Math.max(MobBuffConfig.phantomMembraneMin, MobBuffConfig.phantomMembraneMax);
            if (random.nextDouble() * 100.0D < MobBuffConfig.phantomMembraneDropChance) {
                int count = min + (max > min ? random.nextInt(max - min + 1) : 0);
                if (count > 0) {
                    event.getDrops().add(new ItemEntity(killed.level(), killed.getX(), killed.getY() + 0.3D,
                            killed.getZ(), new ItemStack(Items.PHANTOM_MEMBRANE, count)));
                }
            }
        }
    }

    // ====================================================================
    // 四、敌对生物经验倍率
    //
    // Enemy 覆盖普通敌对生物，NeutralMob 覆盖可被激怒的中立敌对生物。
    // 凋零和末影龙已有独立经验处理，必须排除以免重复修改。
    // ====================================================================
    @SubscribeEvent
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (!MobBuffConfig.enabled || event.getEntity().level().isClientSide()) {
            return;
        }

        LivingEntity killed = event.getEntity();
        if (killed instanceof Illusioner illusioner && isInsideWoodlandMansion(illusioner)) {
            int originalExperience = event.getDroppedExperience();
            if (originalExperience > 0) {
                event.setDroppedExperience((int) Math.min(Integer.MAX_VALUE, (long) originalExperience * 3L));
            }
            return;
        }
        if (killed instanceof WitherBoss || killed instanceof EnderDragon
                || !(killed instanceof Enemy || killed instanceof NeutralMob)) {
            return;
        }

        int originalExperience = event.getDroppedExperience();
        if (originalExperience <= 0 || MobBuffConfig.hostileExperienceBonusPercent <= 0.0D) {
            return;
        }

        // 450% 增加表示额外增加原版经验的 4.5 倍，总计为原版的 5.5 倍。
        double multiplier = 1.0D + MobBuffConfig.hostileExperienceBonusPercent / 100.0D;
        long increasedExperience = Math.round(originalExperience * multiplier);
        event.setDroppedExperience((int) Math.min(Integer.MAX_VALUE, increasedExperience));
    }

    // ====================================================================
    // 五、蜘蛛结网
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
    // 五、幻翼生成条件
    // ====================================================================
    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!MobBuffConfig.enabled) {
            return;
        }
        Mob mob = event.getEntity();
        if (mob instanceof Phantom
                && event.getLevel() instanceof ServerLevel serverLevel) {
            int requiredTicks = MobBuffConfig.phantomNoSleepDays * 24000;
            boolean hasUnrestedPlayer = serverLevel.players().stream()
                    .anyMatch(player -> player.getStats().getValue(Stats.CUSTOM, Stats.TIME_SINCE_REST) >= requiredTicks);
            if (!hasUnrestedPlayer) {
                event.setSpawnCancelled(true);
                return;
            }
        }
        if (!(mob instanceof CaveSpider)
                || event.getSpawnType() != net.minecraft.world.entity.MobSpawnType.SPAWNER
                || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        double extraChance = MobBuffConfig.caveSpiderSpawnMultiplier - 1.0D;
        int extraCount = Math.max(0, (int) extraChance);
        if (serverLevel.random.nextDouble() < extraChance - extraCount) {
            extraCount++;
        }
        for (int i = 0; i < extraCount; i++) {
            CaveSpider extra = net.minecraft.world.entity.EntityType.CAVE_SPIDER.create(serverLevel);
            if (extra == null) {
                continue;
            }
            extra.moveTo(event.getX() + (serverLevel.random.nextDouble() - 0.5D), event.getY(),
                    event.getZ() + (serverLevel.random.nextDouble() - 0.5D), serverLevel.random.nextFloat() * 360.0F, 0.0F);
            serverLevel.addFreshEntity(extra);
        }
    }

    // ====================================================================
    // 六、内部工具方法
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

    /** 普通僵尸与尸壳：保证完整盔甲，并按配置概率装备附魔铁剑。 */
    private static void buffZombieEquipment(Mob mob, RandomSource random) {
        populateArmor(mob, random);
        if (random.nextDouble() * 100.0D < MobBuffConfig.zombieWeaponChance) {
            // 用户要求的是“至少铁剑”，因此普通僵尸/尸壳已有木剑、石剑时也替换掉。
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.enchant(Enchantments.SHARPNESS, 1 + random.nextInt(3));
            if (random.nextBoolean()) {
                sword.enchant(Enchantments.FIRE_ASPECT, 1);
            }
            if (random.nextBoolean()) {
                sword.enchant(Enchantments.KNOCKBACK, 1);
            }
            if (random.nextBoolean()) {
                sword.enchant(ModEnchantments.PLUNDER.get(), 1);
            }
            mob.setItemSlot(EquipmentSlot.MAINHAND, sword);
            mob.setDropChance(EquipmentSlot.MAINHAND, 0.15F);
        }
    }

    /** 骷髅系：保证完整盔甲，主手保留原版弓或专属武器。 */
    private static void buffSkeletonEquipment(Mob mob, RandomSource random) {
        populateArmor(mob, random);
        ItemStack bow = mob.getItemBySlot(EquipmentSlot.MAINHAND);
        if (bow.getItem() instanceof BowItem && random.nextDouble() < 0.30D) {
            if (random.nextBoolean()) {
                bow.enchant(ModEnchantments.ANCIENT_YUNLAI.get(), 1);
            } else {
                bow.enchant(ModEnchantments.SNIPER.get(), 1);
            }
        }
    }


    private static void populateArmor(Mob mob, RandomSource random) {
        // Mob 的装备等级中 1 为金、3 为铁、4 为钻石；每个槽位独立抽取以支持所有混搭。
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) {
                continue;
            }
            int[] materialTiers = {1, 3, 4};
            int materialTier = materialTiers[random.nextInt(materialTiers.length)];
            Item item = Mob.getEquipmentForSlot(slot, materialTier);
            if (item != null) {
                mob.setItemSlot(slot, new ItemStack(item));
            }
        }
    }

    /** 增加护甲值，使用独立 UUID 防止重复加入世界时叠加。 */
    private static void applyArmor(LivingEntity mob, double armor, UUID modifierId) {
        AttributeInstance attribute = mob.getAttribute(Attributes.ARMOR);
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(modifierId);
        double delta = armor - attribute.getBaseValue();
        if (delta > 0.0001D) {
            attribute.addPermanentModifier(new AttributeModifier(
                    modifierId, "enchantment_expansion:mob_buff_armor", delta,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    /** 给移速加一个指定 UUID 的乘算修饰符。 */
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

    private static StructureStart getWoodlandMansionStart(ServerLevel level, BlockPos pos) {
        ResourceKey<Structure> mansionKey = ResourceKey.create(Registries.STRUCTURE, WOODLAND_MANSION);
        Structure mansion = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(mansionKey);
        if (mansion == null) {
            return null;
        }
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, mansion);
        return start.isValid() ? start : null;
    }

    private static boolean isInsideWoodlandMansion(LivingEntity entity) {
        return entity.level() instanceof ServerLevel level
                && getWoodlandMansionStart(level, entity.blockPosition()) != null;
    }

    private static void spawnMansionIllusioner(ServerLevel level, Mob evoker) {
        if (!isInsideWoodlandMansion(evoker)) {
            return;
        }
        StructureStart mansion = getWoodlandMansionStart(level, evoker.blockPosition());
        if (mansion == null) {
            return;
        }
        net.minecraft.world.level.levelgen.structure.BoundingBox bounds = mansion.getBoundingBox();
        net.minecraft.world.phys.AABB mansionBounds = new net.minecraft.world.phys.AABB(
                bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX() + 1.0D, bounds.maxY() + 1.0D,
                bounds.maxZ() + 1.0D);
        boolean alreadyPresent = level.getEntitiesOfClass(Illusioner.class, mansionBounds, Mob::isAlive).stream()
                .anyMatch(MobBuffHandler::isInsideWoodlandMansion);
        if (alreadyPresent) {
            return;
        }
        Illusioner illusioner = net.minecraft.world.entity.EntityType.ILLUSIONER.create(level);
        if (illusioner == null) {
            return;
        }
        illusioner.moveTo(evoker.getX() + 1.5D, evoker.getY(), evoker.getZ() + 1.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        illusioner.getPersistentData().putBoolean(MANSION_ILLUSIONER_TAG, true);
        illusioner.finalizeSpawn(level, level.getCurrentDifficultyAt(illusioner.blockPosition()),
                net.minecraft.world.entity.MobSpawnType.STRUCTURE, null, null);
        level.addFreshEntity(illusioner);
    }

    private static void addFixedDrop(LivingDropsEvent event, LivingEntity killed, Item item, int count) {
        addFixedDrop(event, killed, new ItemStack(item, count));
    }

    private static void addFixedDrop(LivingDropsEvent event, LivingEntity killed, ItemStack stack) {
        event.getDrops().add(new ItemEntity(killed.level(), killed.getX(), killed.getY() + 0.3D,
                killed.getZ(), stack));
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
