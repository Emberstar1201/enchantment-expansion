package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.DarkWalkerConfig;
import com.github.emberstar1201.enchantmentex.ExplosiveArrowConfig;
import com.github.emberstar1201.enchantmentex.ChainArrowConfig;
import com.github.emberstar1201.enchantmentex.entity.GlacialArrowEntity;
import com.github.emberstar1201.enchantmentex.entity.ModEntities;
import com.github.emberstar1201.enchantmentex.util.AllyFilter;
import com.github.emberstar1201.enchantmentex.util.TLMSafe;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 车万女仆 · 附魔兼容性扩展（第 4 批）
//
// 本批接入：
//   (A) 攫取 Snatch       ——  LivingDamageEvent：女仆武器打落目标主手/一件护甲
//   (B) 终末将至 EndApproaches —— LivingHurtEvent：伤害倍率；攻速 modifier；
//                                LivingChangeTargetEvent：末影人不以女仆为目标
//   (C) 爆破箭矢 ExplosiveArrow —— 女仆射的箭按附魔打 NBT 标记，命中爆炸
//   (D) 贯穿链条 ChainArrow    —— 女仆射的箭按附魔打 NBT 标记，命中弹射
//   (E) 幽匿行者 DarkWalker    —— LivingChangeTargetEvent：监守者不以女仆为目标
//   (F) 琉璃冰魄箭 GlacialArrow —— 女仆射的普通 AbstractArrow（非冰川箭实体）
//                                在 EntityJoinLevelEvent 替换为 GlacialArrowEntity
//                                并立即散射子箭（复用同套 Config 数值）
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class TouhouMaidEnchantmentCompat4 {

    // 攫取
    private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{
            EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET};

    // 终末将至（女仆独立 UUID）
    private static final UUID MAID_END_ATTACK_SPEED =
            UUID.fromString("d2cfd1ff-51a0-4dd7-aa10-fffffffff001");

    // 爆破箭 / 贯穿链条 标记（女仆独立，避免与玩家 Handler 冲突）
    private static final String TAG_E_EXP = "EEMaidExplosive";
    private static final String TAG_E_CHN = "EEMaidChain";

    // 幽匿行者女仆活跃 UUID 集合（每 serverTick 刷新，供 Mixin/LCTE 判）
    private static final Set<UUID> MAID_DARK_WALKERS = new HashSet<>();

    // ========================================================================
    // 【A】攫取 LivingDamageEvent：女仆攻击者触发主手/护甲缴械
    // ========================================================================
    @SubscribeEvent
    public static void onLivingDamageSnatch(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        Level level = target.level();
        if (level.isClientSide()) return;
        DamageSource source = event.getSource();
        if (source == null) return;
        if (!(source.getEntity() instanceof LivingEntity attacker)) return;
        if (!TLMSafe.isTouhouMaid(attacker)) return;

        ItemStack weapon = attacker.getMainHandItem();
        int snatchLevel = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.SNATCH.get(), weapon);
        if (snatchLevel <= 0) return;

        // 目标本身就是玩家/友方（主人/女仆/村民）不缴械：避免女仆间 PvP 乱打装备
        if (target instanceof Player || AllyFilter.isFriendly(target)) return;

        double weaponChance = SnatchEnchantment.getTriggerChance(snatchLevel);
        double armorChance = SnatchEnchantment.getArmorTriggerChance(snatchLevel);

        ItemStack mainHand = target.getMainHandItem();
        if (!mainHand.isEmpty() && level.random.nextDouble() < weaponChance) {
            target.spawnAtLocation(mainHand.copy());
            target.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        List<EquipmentSlot> slots = new ArrayList<>();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!target.getItemBySlot(slot).isEmpty()) slots.add(slot);
        }
        if (!slots.isEmpty() && level.random.nextDouble() < armorChance) {
            EquipmentSlot chosen = slots.get(level.random.nextInt(slots.size()));
            ItemStack armor = target.getItemBySlot(chosen);
            target.spawnAtLocation(armor.copy());
            target.setItemSlot(chosen, ItemStack.EMPTY);
        }
    }

    // ========================================================================
    // 【B-1】终末将至 · 伤害倍率
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHurtEndApproaches(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (source == null) return;
        if (!(source.getEntity() instanceof LivingEntity attacker)) return;
        if (!TLMSafe.isTouhouMaid(attacker)) return;
        if (attacker.level().isClientSide()) return;
        ItemStack weapon = attacker.getMainHandItem();
        int level = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.END_APPROACHES.get(), weapon);
        if (level <= 0) return;
        float mul = (float) Config.endApproachesDamageMultiplier;
        event.setAmount(event.getAmount() * mul);
    }

    // ========================================================================
    // 【B-2】终末将至 · 末影人不以女仆为目标
    // ========================================================================
    @SubscribeEvent
    public static void onLivingChangeTargetEndApproaches(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof EnderMan enderman)) return;
        LivingEntity maid = event.getNewTarget();
        if (maid == null) return;
        if (!TLMSafe.isTouhouMaid(maid)) return;
        int level = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.END_APPROACHES.get(), maid.getMainHandItem());
        if (level <= 0) return;
        enderman.setTarget(null);
        enderman.setRemainingPersistentAngerTime(0);
        enderman.setPersistentAngerTarget(null);
        enderman.getNavigation().stop();
        event.setNewTarget(null);
    }

    // ========================================================================
    // 【B-3】终末将至 + 幽匿行者 —— ServerTick 维护 modifier 与 DarkWalker set
    // ========================================================================
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        MAID_DARK_WALKERS.clear();
        for (var level : server.getAllLevels()) {
            if (level.isClientSide) continue;
            for (LivingEntity maid : level.getEntitiesOfClass(
                    LivingEntity.class,
                    new AABB(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY),
                    TLMSafe::isTouhouMaid)) {

                // 终末将至：攻速 modifier
                double endBonus = Config.endApproachesAttackSpeedBonus;
                int levelEnd = EnchantmentHelper.getTagEnchantmentLevel(
                        ModEnchantments.END_APPROACHES.get(), maid.getMainHandItem());
                applyOrRemoveSingle(maid, Attributes.ATTACK_SPEED, MAID_END_ATTACK_SPEED,
                        "MaidEndAttackSpeed", endBonus, levelEnd > 0 && endBonus != 0);

                // 幽匿行者：激活条件缓存
                ItemStack boots = maid.getItemBySlot(EquipmentSlot.FEET);
                int dw = EnchantmentHelper.getTagEnchantmentLevel(ModEnchantments.DARK_WALKER.get(), boots);
                if (dw > 0 && DarkWalkerConfig.isEnabled() && !DarkWalkerConfig.restrictToBiome) {
                    MAID_DARK_WALKERS.add(maid.getUUID());
                }
            }
        }
    }

    private static void applyOrRemoveSingle(LivingEntity e, net.minecraft.world.entity.ai.attributes.Attribute attr,
                                            UUID uuid, String name, double value, boolean apply) {
        var inst = e.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(uuid);
        if (apply && value != 0) {
            inst.addTransientModifier(new AttributeModifier(uuid, name, value,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    // ========================================================================
    // 【E】幽匿行者 · LivingChangeTargetEvent 监守者：对激活的女仆直接清空目标
    // ========================================================================
    @SubscribeEvent
    public static void onLivingChangeTargetWardenDarkWalker(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Warden warden)) return;
        LivingEntity maid = event.getNewTarget();
        if (maid == null) return;
        if (!TLMSafe.isTouhouMaid(maid)) return;
        if (!MAID_DARK_WALKERS.contains(maid.getUUID())) return;
        warden.setTarget(null);
        event.setNewTarget(null);
    }

    // ========================================================================
    // 【C / D / F】EntityJoinLevelEvent：标记爆破/链条 + 替换琉璃冰魄箭实体
    // ========================================================================
    @SubscribeEvent
    public static void onArrowJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;

        if (!(arrow.getOwner() instanceof LivingEntity shooter)) return;
        if (!TLMSafe.isTouhouMaid(shooter)) return;

        ItemStack main = shooter.getMainHandItem();
        ItemStack off = shooter.getOffhandItem();
        boolean isBow = main.getItem() instanceof BowItem || main.getItem() instanceof CrossbowItem;
        ItemStack bow = isBow ? main : (off.getItem() instanceof BowItem || off.getItem() instanceof CrossbowItem ? off : ItemStack.EMPTY);
        if (bow.isEmpty()) return;

        CompoundTag tag = arrow.getPersistentData();

        // 优先处理琉璃冰魄箭：若命中则直接替换实体，爆破/链条不再打
        if (!(arrow instanceof GlacialArrowEntity)) {
            int ga = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.GLACIAL_ARROW.get(), bow);
            if (ga > 0) {
                replaceWithGlacial(event, arrow, shooter, bow);
                return;
            }
        }

        int exp = arrowLevel(bow, ModEnchantments.EXPLOSIVE_ARROW.get());
        if (exp > 0 && !tag.contains(TAG_E_EXP)) {
            tag.putInt(TAG_E_EXP, exp);
        }
        int chn = arrowLevel(bow, ModEnchantments.CHAIN_ARROW.get());
        if (chn > 0 && !tag.contains(TAG_E_CHN)) {
            tag.putInt(TAG_E_CHN, chn);
        }
    }

    private static int arrowLevel(ItemStack bow, net.minecraft.world.item.enchantment.Enchantment ench) {
        return EnchantmentHelper.getItemEnchantmentLevel(ench, bow);
    }

    // 把女仆射的普通 AbstractArrow 换成 GlacialArrowEntity（取消原箭加入世界）
    private static void replaceWithGlacial(EntityJoinLevelEvent event, AbstractArrow arrow,
                                           LivingEntity shooter, ItemStack bow) {
        event.setCanceled(true);
        Level level = arrow.level();

        Vec3 position = arrow.position();
        GlacialArrowEntity glacial = new GlacialArrowEntity(
                level, shooter, position.x, position.y, position.z);

        Vec3 look = shooter.getLookAngle();
        double originalSpeed = arrow.getDeltaMovement().length();
        double speedMult = Config.glacialArrowSpeed;
        glacial.setDeltaMovement(look.scale(originalSpeed * speedMult));
        glacial.setBaseDamage(arrow.getBaseDamage() * Config.glacialArrowDamage);
        glacial.setMaxPierceCount(Config.glacialArrowPierceCount);
        glacial.setXRot(arrow.getXRot());
        glacial.setYRot(arrow.getYRot());
        glacial.setNoGravity(arrow.isNoGravity());
        glacial.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

        if (level.addFreshEntity(glacial)) {
            glacial.spawnSubArrowsNow();
        }
    }

    // ========================================================================
    // 【C】爆破箭矢：ProjectileImpactEvent 触发爆炸
    // ========================================================================
    @SubscribeEvent
    public static void onProjectileImpactExplosive(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        Level level = arrow.level();
        if (level.isClientSide()) return;
        int el = arrow.getPersistentData().getInt(TAG_E_EXP);
        if (el <= 0) return;
        if (!ExplosiveArrowConfig.enabled) return;

        double radius = ExplosiveArrowConfig.getRadius(el);
        double maxDamage = ExplosiveArrowConfig.getDamage(el);
        HitResult hit = event.getRayTraceResult();
        Vec3 hitPos = hit.getLocation();
        Entity owner = arrow.getOwner();
        LivingEntity directTarget = null;
        if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult ehit) {
            if (ehit.getEntity() instanceof LivingEntity t && t.isAlive()) {
                directTarget = t;
                t.hurt(level.damageSources().generic(), (float) maxDamage);
            }
        }
        LivingEntity finalDirectTarget = directTarget;
        AABB aabb = new AABB(hitPos.x - radius, hitPos.y - radius, hitPos.z - radius,
                hitPos.x + radius, hitPos.y + radius, hitPos.z + radius);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, aabb,
                e -> e.isAlive() && e != owner && e != finalDirectTarget && !AllyFilter.isFriendly(e));
        for (LivingEntity e : nearby) {
            double d = Math.sqrt(e.distanceToSqr(hitPos));
            if (d > radius) continue;
            float falloff = (float) (maxDamage * (1.0 - d / radius));
            if (falloff <= 0) continue;
            e.hurt(level.damageSources().explosion(owner, owner), falloff);
        }
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0F, 1.0F);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.EXPLOSION,
                    hitPos.x, hitPos.y, hitPos.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        arrow.discard();
        event.setCanceled(true);
    }

    // ========================================================================
    // 【D】贯穿链条：弹射寻敌
    // ========================================================================
    @SubscribeEvent
    public static void onProjectileImpactChain(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        Level level = arrow.level();
        if (level.isClientSide()) return;
        int cl = arrow.getPersistentData().getInt(TAG_E_CHN);
        if (cl <= 0) return;
        if (!ChainArrowConfig.enabled) return;
        HitResult hit = event.getRayTraceResult();
        if (!(hit instanceof EntityHitResult entityHit)) return;
        if (!(entityHit.getEntity() instanceof LivingEntity firstTarget)) return;

        int bounceCount = ChainArrowConfig.getBounceCount(cl);
        float bounceDamage = (float) (arrow.getBaseDamage() * ChainArrowConfig.damageRatio);
        Entity owner = arrow.getOwner();
        firstTarget.hurt(level.damageSources().arrow(arrow, owner), bounceDamage);

        LivingEntity current = firstTarget;
        for (int i = 0; i < bounceCount; i++) {
            LivingEntity next = findNext(level, current, owner);
            if (next == null) break;
            next.hurt(level.damageSources().arrow(arrow, owner), bounceDamage);
            arrow.moveTo(next.getX(), next.getY() + next.getBbHeight() * 0.5, next.getZ());
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        next.getX(), next.getY() + next.getBbHeight() * 0.5, next.getZ(),
                        3, 0.2, 0.2, 0.2, 0.02);
            }
            current = next;
        }
        arrow.discard();
        event.setCanceled(true);
    }

    private static LivingEntity findNext(Level level, LivingEntity current, Entity owner) {
        AABB aabb = current.getBoundingBox().inflate(ChainArrowConfig.searchRange);
        List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, aabb,
                e -> e.isAlive() && e != current && e != owner && !AllyFilter.isFriendly(e));
        return list.stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(current)))
                .orElse(null);
    }

    /** Mixin 或其它类通过 "女仆 UUID" 查询是否处于幽匿行者激活态（未来接入 use 扩展） */
    public static boolean isMaidDarkWalker(UUID uuid) {
        return MAID_DARK_WALKERS.contains(uuid);
    }
}
