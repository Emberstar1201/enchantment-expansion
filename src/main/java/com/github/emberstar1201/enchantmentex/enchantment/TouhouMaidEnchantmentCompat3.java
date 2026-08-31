package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.DifficultyGiftConfig;
import com.github.emberstar1201.enchantmentex.PlunderConfig;
import com.github.emberstar1201.enchantmentex.util.AllyFilter;
import com.github.emberstar1201.enchantmentex.util.TLMSafe;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 车万女仆 · 附魔兼容性扩展（第 3 批）
//
// 本批接入：
//   (A) 狙击 Sniper      —— 女仆持弓/弩射击时，箭矢标记+飞行距离增伤；
//   (B) 难度馈赠 DifficultyGift —— 女仆武器按难度加固定伤害；
//                                 手持附魔武器 → 攻速 modifier；
//                                 穿着附魔盔甲 → 护甲/韧性 modifier；
//   (C) 强夺 Plunder    —— 女仆击杀触发：稀有战利品保底（头/唱片）
//                                 + 怪物装备槽强制掉落（按等级概率/耐久）。
//
// 所有入口都先判断 TLMSafe.isTouhouMaid。玩家版本的 Handler 不受影响。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class TouhouMaidEnchantmentCompat3 {

    // --------------------------- 狙击 NBT 标记 ---------------------------
    private static final String TAG_SNIPER = "EEMaidSniper";
    private static final String TAG_SNIPER_X = "EEMaidSniperX";
    private static final String TAG_SNIPER_Y = "EEMaidSniperY";
    private static final String TAG_SNIPER_Z = "EEMaidSniperZ";
    private static final double SNIPER_BLOCKS_PER_TIER = 10.0;
    private static final double SNIPER_BONUS_PER_TIER = 0.20;
    private static final double SNIPER_MAX_BONUS = 1.0;

    // --------------------------- 难度馈赠 UUID（女仆独立） ---------------------------
    private static final UUID MAID_DG_SPEED_UUID =
            UUID.fromString("e0a8311c-2e41-45c3-9cfd-1234567890a1");
    private static final UUID MAID_DG_ARMOR_UUID =
            UUID.fromString("e1a8311c-2e41-45c3-9cfd-1234567890a2");
    private static final UUID MAID_DG_TOUGHNESS_UUID =
            UUID.fromString("e2a8311c-2e41-45c3-9cfd-1234567890a3");
    private static final UUID MAID_DG_HEALTH_HEAD =
            UUID.fromString("e3a8311c-3e41-46c3-0001-1234567890aa");
    private static final UUID MAID_DG_HEALTH_CHEST =
            UUID.fromString("e3a8311c-3e41-46c3-0001-1234567890ab");
    private static final UUID MAID_DG_HEALTH_LEGS =
            UUID.fromString("e3a8311c-3e41-46c3-0001-1234567890ac");
    private static final UUID MAID_DG_HEALTH_FEET =
            UUID.fromString("e3a8311c-3e41-46c3-0001-1234567890ad");

    // ========================================================================
    // 【A-1】狙击：女仆发射 AbstractArrow → 打标记 + 记录"眼睛位置"（bb 中上方）
    // ========================================================================
    @SubscribeEvent
    public static void onArrowJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;

        CompoundTag tag = arrow.getPersistentData();
        if (tag.contains(TAG_SNIPER)) return;

        if (!(arrow.getOwner() instanceof LivingEntity shooter)) return;
        if (!TLMSafe.isTouhouMaid(shooter)) return;

        int level = getSniperLevelFromLiving(shooter);
        if (level <= 0) return;

        tag.putBoolean(TAG_SNIPER, true);
        Vec3 origin = shooter.position().add(0, shooter.getBbHeight() * 0.85, 0);
        tag.putDouble(TAG_SNIPER_X, origin.x);
        tag.putDouble(TAG_SNIPER_Y, origin.y);
        tag.putDouble(TAG_SNIPER_Z, origin.z);
    }

    /** 女仆"主/副手"的弓/弩上的狙击等级 */
    private static int getSniperLevelFromLiving(LivingEntity living) {
        int level = 0;
        ItemStack main = living.getMainHandItem();
        ItemStack off = living.getOffhandItem();
        if (main.getItem() instanceof BowItem || main.getItem() instanceof CrossbowItem) {
            level = Math.max(level, EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.SNIPER.get(), main));
        }
        if (off.getItem() instanceof BowItem || off.getItem() instanceof CrossbowItem) {
            level = Math.max(level, EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.SNIPER.get(), off));
        }
        return level;
    }

    // ========================================================================
    // 【A-2】狙击：箭矢命中 LivingEntity → 距离增伤
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHurtSniper(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        DamageSource source = event.getSource();
        if (source == null) return;
        if (!(source.getDirectEntity() instanceof AbstractArrow arrow)) return;

        CompoundTag tag = arrow.getPersistentData();
        if (!tag.contains(TAG_SNIPER)) return;

        // 验证射手仍存在（非必须，防御用）
        if (!(arrow.getOwner() instanceof LivingEntity shooter)
                || !TLMSafe.isTouhouMaid(shooter)) {
            // 玩家狙击的箭头走玩家 Handler；这里女仆标记的只处理女仆射手
        }

        Vec3 origin = new Vec3(
                tag.getDouble(TAG_SNIPER_X),
                tag.getDouble(TAG_SNIPER_Y),
                tag.getDouble(TAG_SNIPER_Z)
        );
        Vec3 hit = arrow.position();
        double distance = origin.distanceTo(hit);
        double bonus = Math.min(distance / SNIPER_BLOCKS_PER_TIER * SNIPER_BONUS_PER_TIER, SNIPER_MAX_BONUS);
        if (bonus <= 0.001) return;

        event.setAmount((float) (event.getAmount() * (1.0 + bonus)));

        // 女仆不显示 ActionBar（女仆没有玩家型的显示入口）。
        // 仅可选：对主人发送提示（此处保留空白，不依赖主人定位，避免误刷屏）
    }

    // ========================================================================
    // 【B-1】难度馈赠：女仆武器命中 → 加固定伤害（按难度）
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHurtDifficultyGift(LivingHurtEvent event) {
        if (!DifficultyGiftConfig.isEnabled()) return;
        if (event.getEntity().level().isClientSide()) return;
        DamageSource source = event.getSource();
        if (source == null) return;
        if (!(source.getEntity() instanceof LivingEntity attacker)) return;
        if (!TLMSafe.isTouhouMaid(attacker)) return;

        ItemStack weapon = attacker.getMainHandItem();
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DIFFICULTY_GIFT.get(), weapon);
        if (level <= 0) {
            weapon = attacker.getOffhandItem();
            level = EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.DIFFICULTY_GIFT.get(), weapon);
        }
        if (level <= 0) return;

        double bonus = DifficultyGiftConfig.getDamageBonus(attacker.level().getDifficulty());
        if (bonus > 0) {
            event.setAmount(event.getAmount() + (float) bonus);
        }
    }

    // ========================================================================
    // 【B-2】难度馈赠：ServerTick 中管理 modifier
    //        （ATTACK_SPEED / ARMOR / ARMOR_TOUGHNESS）
    // ========================================================================
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!DifficultyGiftConfig.isEnabled()) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (var level : server.getAllLevels()) {
            if (level.isClientSide) continue;
            for (LivingEntity maid : level.getEntitiesOfClass(
                    LivingEntity.class,
                    new AABB(
                            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
                    ),
                    TLMSafe::isTouhouMaid)) {
                manageDifficultyGiftModifiers(maid);
            }
        }
    }

    private static void manageDifficultyGiftModifiers(LivingEntity maid) {
        Difficulty difficulty = maid.level().getDifficulty();
        boolean peaceful = difficulty == Difficulty.PEACEFUL;

        ItemStack main = maid.getMainHandItem();
        ItemStack off = maid.getOffhandItem();
        boolean hasWeapon = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DIFFICULTY_GIFT.get(), main) > 0
                || EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DIFFICULTY_GIFT.get(), off) > 0;
        double speedBonus = peaceful ? 0.0 : DifficultyGiftConfig.getAttackSpeedBonus(difficulty);
        applyOrRemove(maid, Attributes.ATTACK_SPEED,
                MAID_DG_SPEED_UUID, "MaidDGSpeed",
                speedBonus, hasWeapon && speedBonus > 0);

        boolean hasArmor = hasArmorEnchanted(maid);
        double armorBonus = peaceful ? 0.0 : DifficultyGiftConfig.getArmorBonus(difficulty);
        double toughnessBonus = peaceful ? 0.0 : DifficultyGiftConfig.getToughnessBonus(difficulty);
        applyOrRemove(maid, Attributes.ARMOR,
                MAID_DG_ARMOR_UUID, "MaidDGArmor",
                armorBonus, hasArmor && armorBonus > 0);
        applyOrRemove(maid, Attributes.ARMOR_TOUGHNESS,
                MAID_DG_TOUGHNESS_UUID, "MaidDGTough",
                toughnessBonus, hasArmor && toughnessBonus > 0);

        // 单件盔甲的 MAX_HEALTH 加成：基础 20 HP + 困难满套=4×7.5=+30 → 合计 50
        double healthPer = peaceful ? 0.0
                : DifficultyGiftConfig.getMaxHealthBonusPerArmor(difficulty);
        boolean changed = false;
        changed |= applyHealthModifier(maid, EquipmentSlot.HEAD, MAID_DG_HEALTH_HEAD,
                "MaidDGHealthHead", healthPer);
        changed |= applyHealthModifier(maid, EquipmentSlot.CHEST, MAID_DG_HEALTH_CHEST,
                "MaidDGHealthChest", healthPer);
        changed |= applyHealthModifier(maid, EquipmentSlot.LEGS, MAID_DG_HEALTH_LEGS,
                "MaidDGHealthLegs", healthPer);
        changed |= applyHealthModifier(maid, EquipmentSlot.FEET, MAID_DG_HEALTH_FEET,
                "MaidDGHealthFeet", healthPer);
        if (changed) {
            float max = maid.getMaxHealth();
            if (maid.getHealth() > max) maid.setHealth(max);
        }
    }

    private static boolean applyHealthModifier(LivingEntity maid,
                                               EquipmentSlot slot,
                                               UUID uuid, String name,
                                               double healthPerArmor) {
        if (slot.getType() != EquipmentSlot.Type.ARMOR) return false;
        AttributeInstance attr = maid.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return false;
        ItemStack stack = maid.getItemBySlot(slot);
        int ench = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DIFFICULTY_GIFT.get(), stack);
        double value = (ench > 0 && healthPerArmor > 0) ? healthPerArmor : 0.0;
        AttributeModifier existing = attr.getModifier(uuid);
        boolean changed = false;
        if (value > 0) {
            if (existing == null) {
                attr.addTransientModifier(new AttributeModifier(
                        uuid, name, value, AttributeModifier.Operation.ADDITION));
                changed = true;
            } else if (existing.getAmount() != value) {
                attr.removeModifier(uuid);
                attr.addTransientModifier(new AttributeModifier(
                        uuid, name, value, AttributeModifier.Operation.ADDITION));
                changed = true;
            }
        } else if (existing != null) {
            attr.removeModifier(uuid);
            changed = true;
        }
        return changed;
    }

    private static boolean hasArmorEnchanted(LivingEntity maid) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;
            ItemStack stack = maid.getItemBySlot(slot);
            if (EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.DIFFICULTY_GIFT.get(), stack) > 0) {
                return true;
            }
        }
        return false;
    }

    private static void applyOrRemove(LivingEntity entity, Attribute attribute,
                                      UUID uuid, String name,
                                      double value, boolean apply) {
        AttributeInstance attr = entity.getAttribute(attribute);
        if (attr == null) return;
        attr.removeModifier(uuid);
        if (apply && value != 0) {
            attr.addTransientModifier(new AttributeModifier(
                    uuid, name, value, AttributeModifier.Operation.ADDITION));
        }
    }

    // ========================================================================
    // 【C-1】强夺：女仆击杀触发稀有战利品保底（头/唱片）
    // ========================================================================
    @SubscribeEvent
    public static void onLivingDropsPlunder(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        Level level = victim.level();
        if (level.isClientSide()) return;

        DamageSource source = event.getSource();
        if (source == null) return;
        Entity killer = source.getEntity();
        if (!(killer instanceof LivingEntity livingKiller)) return;
        if (!TLMSafe.isTouhouMaid(livingKiller)) return;
        // AOE 类友伤过滤：女仆击杀友方（主人/其他女仆/村民/宠物）不触发强夺
        if (victim instanceof Player || AllyFilter.isFriendly(victim)) return;

        ItemStack weapon = livingKiller.getMainHandItem();
        int plunderLevel = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.PLUNDER.get(), weapon);
        if (plunderLevel <= 0) return;

        RandomSource random = level.getRandom();
        double chance = PlunderConfig.getRareLootChance(plunderLevel);
        if (random.nextFloat() >= chance) return;

        int count = PlunderConfig.rollRareLootCount(plunderLevel, random);

        List<ItemEntity> dropsList = new ArrayList<>(event.getDrops());
        for (ItemEntity dropEntity : dropsList) {
            ItemStack stack = dropEntity.getItem();
            if (PlunderHandlerHelper.isRareLoot(stack) && stack.getCount() < count) {
                stack.setCount(count);
            }
        }
        if (victim instanceof WitherSkeleton && !PlunderHandlerHelper.hasRareLootOf(event, Items.WITHER_SKELETON_SKULL)) {
            PlunderHandlerHelper.addDropAtVictim(event, victim,
                    new ItemStack(Items.WITHER_SKELETON_SKULL, count));
        }
        if (victim instanceof Creeper && !PlunderHandlerHelper.hasAnyMusicDisc(event)) {
            ItemStack disc = PlunderHandlerHelper.getRandomMusicDisc(random);
            if (!disc.isEmpty()) {
                disc.setCount(count);
                PlunderHandlerHelper.addDropAtVictim(event, victim, disc);
            }
        }
    }

    // ========================================================================
    // 【C-2】强夺：女仆击杀 LivingDeathEvent → 强制掉落装备（+耐久分布）
    // ========================================================================
    @SubscribeEvent
    public static void onLivingDeathPlunder(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Level level = victim.level();
        if (level.isClientSide()) return;
        if (victim instanceof Player) return;

        DamageSource source = event.getSource();
        if (source == null) return;
        Entity killer = source.getEntity();
        if (!(killer instanceof LivingEntity livingKiller)) return;
        if (!TLMSafe.isTouhouMaid(livingKiller)) return;
        if (AllyFilter.isFriendly(victim)) return;

        ItemStack weapon = livingKiller.getMainHandItem();
        int plunderLevel = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.PLUNDER.get(), weapon);
        if (plunderLevel <= 0) return;

        RandomSource random = level.getRandom();
        double dropChance = PlunderConfig.getEquipDropChance(plunderLevel);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            EquipmentSlot.Type type = slot.getType();
            if (type != EquipmentSlot.Type.HAND && type != EquipmentSlot.Type.ARMOR) continue;
            ItemStack equip = victim.getItemBySlot(slot);
            if (equip.isEmpty()) continue;
            if (random.nextFloat() >= dropChance) continue;

            ItemStack dropStack = equip.copy();
            PlunderHandlerHelper.applyDurability(dropStack, plunderLevel, random);
            PlunderHandlerHelper.spawnDropAt(level, victim, dropStack);
            victim.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    // ========================================================================
    // 内部工具：复用 PlunderHandler 的静态逻辑（不依赖 PlunderHandler 的访问权限）
    // ========================================================================
    private static final class PlunderHandlerHelper {

        static boolean isRareLoot(ItemStack stack) {
            Item item = stack.getItem();
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            String path = itemId.getPath();
            if (path.endsWith("_head") || path.endsWith("_skull")) return true;
            if (stack.is(ItemTags.MUSIC_DISCS)) return true;
            return false;
        }

        static boolean hasRareLootOf(LivingDropsEvent event, Item target) {
            for (ItemEntity e : event.getDrops()) {
                if (e.getItem().is(target)) return true;
            }
            return false;
        }

        static boolean hasAnyMusicDisc(LivingDropsEvent event) {
            for (ItemEntity e : event.getDrops()) {
                if (e.getItem().is(ItemTags.MUSIC_DISCS)) return true;
            }
            return false;
        }

        static ItemStack getRandomMusicDisc(RandomSource random) {
            var tagOpt = BuiltInRegistries.ITEM.getTag(ItemTags.CREEPER_DROP_MUSIC_DISCS);
            if (tagOpt.isEmpty()) return ItemStack.EMPTY;
            var items = tagOpt.get().stream().toList();
            if (items.isEmpty()) return ItemStack.EMPTY;
            Holder<Item> chosen = items.get(random.nextInt(items.size()));
            return new ItemStack(chosen.value());
        }

        static void applyDurability(ItemStack stack, int level, RandomSource random) {
            int maxDamage = stack.getMaxDamage();
            if (maxDamage <= 0) return;
            double ratio = PlunderConfig.rollEquipDurability(level, random);
            int damage = (int) Math.round(maxDamage * (1.0 - ratio));
            stack.setDamageValue(Math.max(0, Math.min(maxDamage, damage)));
        }

        static void spawnDropAt(Level level, LivingEntity victim, ItemStack stack) {
            ItemEntity ent = new ItemEntity(
                    level, victim.getX(), victim.getY(), victim.getZ(), stack);
            RandomSource random = level.getRandom();
            ent.setDeltaMovement(
                    (random.nextDouble() - 0.5) * 0.2,
                    random.nextDouble() * 0.3,
                    (random.nextDouble() - 0.5) * 0.2
            );
            level.addFreshEntity(ent);
        }

        static void addDropAtVictim(LivingDropsEvent event, LivingEntity victim, ItemStack stack) {
            ItemEntity ent = new ItemEntity(
                    victim.level(), victim.getX(), victim.getY(), victim.getZ(), stack);
            RandomSource random = victim.level().getRandom();
            ent.setDeltaMovement(
                    (random.nextDouble() - 0.5) * 0.2,
                    random.nextDouble() * 0.3,
                    (random.nextDouble() - 0.5) * 0.2
            );
            event.getDrops().add(ent);
        }
    }
}
