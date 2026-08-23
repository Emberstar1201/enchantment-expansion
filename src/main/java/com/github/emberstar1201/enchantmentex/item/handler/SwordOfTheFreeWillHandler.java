package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.enchantment.ModEnchantments;
import com.github.emberstar1201.enchantmentex.item.ModItems;
import com.github.emberstar1201.enchantmentex.entity.CustomLightningEntity;
import com.github.emberstar1201.enchantmentex.item.SwordOfTheFreeWill;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 人权剑（Sword of the Free Will）事件处理器
//
// 【职责】
//   1. 凋灵击败后掉落人权剑（LivingDropsEvent）
//      - 剑掉落时附带「防摧毁」（invulnerable）
//      - 掉落物上方持续 5 秒生成金色光柱粒子
//   2. 主动技能「人的意志」右键触发（PlayerInteractEvent.RightClickItem）
//   3. 主动技能加成：攻击时降下闪电 + 伤害提升（LivingHurtEvent）
//   4. 被动「晨曦」背包中提供护甲 + 减伤（PlayerTickEvent + LivingHurtEvent）
//   5. 冷却结束聊天栏提醒（PlayerTickEvent）
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SwordOfTheFreeWillHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ============================================================
    // PersistentData 键名
    // ============================================================
    private static final String KEY_COOLDOWN_END = "SOTFW_CooldownEnd";
    private static final String KEY_BUFF_END = "SOTFW_BuffEnd";
    private static final String KEY_NOTIFIED = "SOTFW_CooldownNotified";

    // ============================================================
    // 修饰符 UUID（护甲）
    // ============================================================
    private static final UUID ARMOR_MODIFIER_UUID =
            UUID.fromString("d4e5f6a7-b8c9-0123-4567-890abcdef012");
    private static final String ARMOR_MODIFIER_NAME = "SOTFW Armor Bonus";

    // ============================================================
    // 光柱粒子跟踪表
    //   key   = 掉落物 ItemEntity 的 UUID
    //   value = 该掉落物生成时所在维度的游戏时间（用于计算 5 秒到期）
    //
    // 为什么用 ConcurrentHashMap？
    //   LevelTickEvent 可能在多个维度线程中并行触发，ConcurrentHashMap
    //   保证线程安全，避免并发修改异常。
    // ============================================================
    private static final Map<UUID, Long> pendingBeamEntities = new ConcurrentHashMap<>();

    // 光柱持续时间（tick，5 秒 = 100 tick）
    private static final long BEAM_DURATION_TICKS = 100;

    // ============================================================
    // 工具方法：检查背包是否有人权剑
    // ============================================================
    private static boolean hasSwordInInventory(Player player) {
        if (player.getMainHandItem().getItem() instanceof SwordOfTheFreeWill) return true;
        if (player.getOffhandItem().getItem() instanceof SwordOfTheFreeWill) return true;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof SwordOfTheFreeWill) return true;
        }
        return false;
    }

    // ============================================================
    // 工具方法：检查增益是否激活
    // ============================================================
    private static boolean hasActiveBuff(Player player) {
        return player.getPersistentData().getLong(KEY_BUFF_END) > player.level().getGameTime();
    }

    // ============================================================
    // 工具方法：检查冷却是否激活
    // ============================================================
    private static boolean isOnCooldown(Player player) {
        return player.getPersistentData().getLong(KEY_COOLDOWN_END) > player.level().getGameTime();
    }

    // ============================================================
    // 工具方法：剩余冷却秒数
    // ============================================================
    private static int getRemainingCooldownSeconds(Player player) {
        long remaining = player.getPersistentData().getLong(KEY_COOLDOWN_END)
                - player.level().getGameTime();
        return Math.max(0, (int) (remaining / 20));
    }

    // ============================================================
    // 工具方法：创建带内置附魔的人权剑
    //   锋利 X + 亡灵杀手 X + 击退 II + 拂晓 I + 星火不灭 I
    // ============================================================
    private static ItemStack createEnchantedSword() {
        ItemStack sword = new ItemStack(ModItems.SWORD_OF_THE_FREE_WILL.get());

        CompoundTag tag = sword.getOrCreateTag();
        ListTag enchantments = new ListTag();

        CompoundTag sharpness = new CompoundTag();
        sharpness.putString("id", "minecraft:sharpness");
        sharpness.putShort("lvl", (short) 10);
        enchantments.add(sharpness);

        CompoundTag smite = new CompoundTag();
        smite.putString("id", "minecraft:smite");
        smite.putShort("lvl", (short) 10);
        enchantments.add(smite);

        CompoundTag knockback = new CompoundTag();
        knockback.putString("id", "minecraft:knockback");
        knockback.putShort("lvl", (short) 2);
        enchantments.add(knockback);

        CompoundTag dawn = new CompoundTag();
        dawn.putString("id", MODID + ":dawn");
        dawn.putShort("lvl", (short) 1);
        enchantments.add(dawn);

        CompoundTag eternalSpark = new CompoundTag();
        eternalSpark.putString("id", MODID + ":eternal_spark");
        eternalSpark.putShort("lvl", (short) 1);
        enchantments.add(eternalSpark);

        tag.put("Enchantments", enchantments);
        sword.setTag(tag);

        return sword;
    }

    // ============================================================
    // 工具方法：播放金色闪电粒子（技能激活效果）
    // ============================================================
    private static void spawnGoldenLightningParticles(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                x, y + 1, z, 30, 0.5, 0.5, 0.5, 0.2);
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
                x, y + 1, z, 10, 0.3, 0.3, 0.3, 0.1);
    }

    // ============================================================
    // 工具方法：护甲修饰符管理
    // ============================================================
    private static void manageArmorModifier(Player player, boolean shouldHave) {
        AttributeInstance armorAttr = player.getAttribute(Attributes.ARMOR);
        if (armorAttr == null) return;

        AttributeModifier existing = armorAttr.getModifier(ARMOR_MODIFIER_UUID);
        if (shouldHave) {
            if (existing == null) {
                armorAttr.addTransientModifier(
                        new AttributeModifier(ARMOR_MODIFIER_UUID, ARMOR_MODIFIER_NAME,
                                Config.swordPassiveArmor, AttributeModifier.Operation.ADDITION));
            } else if (existing.getAmount() != Config.swordPassiveArmor) {
                armorAttr.removeModifier(ARMOR_MODIFIER_UUID);
                armorAttr.addTransientModifier(
                        new AttributeModifier(ARMOR_MODIFIER_UUID, ARMOR_MODIFIER_NAME,
                                Config.swordPassiveArmor, AttributeModifier.Operation.ADDITION));
            }
        } else {
            if (existing != null) {
                armorAttr.removeModifier(ARMOR_MODIFIER_UUID);
            }
        }
    }

    // ============================================================
    // 事件1：凋灵掉落人权剑 + 防摧毁 + 光柱粒子
    // ============================================================
    @SubscribeEvent
    public static void onWitherDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof WitherBoss)) return;
        if (event.getEntity().level().isClientSide()) return;

        // 防止重复掉落
        for (ItemEntity itemEntity : event.getDrops()) {
            if (itemEntity.getItem().getItem() instanceof SwordOfTheFreeWill) return;
        }

        Level level = event.getEntity().level();

        // 创建人权剑
        ItemStack sword = createEnchantedSword();

        ItemEntity swordEntity = new ItemEntity(
                level,
                event.getEntity().getX(),
                event.getEntity().getY(),
                event.getEntity().getZ(),
                sword
        );

        // ================================================================
        // ★ 防摧毁：免疫火焰、爆炸、闪电、岩浆 ★
        //   setInvulnerable(true) 使 ItemEntity 免疫所有伤害类型，
        //   包括火焰、爆炸、闪电、岩浆、仙人掌等。
        //   配合掉落延迟（10 tick），玩家有充足时间看到并拾取。
        // ================================================================
        swordEntity.setInvulnerable(true);
        swordEntity.setPickUpDelay(10);

        event.getDrops().add(swordEntity);

        // ================================================================
        // ★ 将掉落物 UUID 加入光柱粒子跟踪表 ★
        //   LevelTickEvent 会自动为跟踪表中的掉落物播放心得 END_ROD 粒子
        // ================================================================
        pendingBeamEntities.put(swordEntity.getUUID(), level.getGameTime());

        LOGGER.info("[SOTFW] 凋灵已被击败，人权剑已掉落（防摧毁 + 光柱激活）");
    }

    // ============================================================
    // 事件2：右键激活主动技能（PlayerInteractEvent.RightClickItem）
    // ============================================================
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SwordOfTheFreeWill)) return;

        if (isOnCooldown(player)) {
            int remaining = getRemainingCooldownSeconds(player);
            player.sendSystemMessage(Component.translatable(
                    "message.enchantment_expansion.sotfw.cooldown",
                    remaining / 60, remaining % 60
            ).withStyle(ChatFormatting.RED));
            return;
        }

        long gameTime = player.level().getGameTime();

        // 消耗生命值（> 50% 时才消耗）
        float maxHealth = player.getMaxHealth();
        float currentHealth = player.getHealth();
        if (currentHealth > maxHealth * Config.swordHealthThreshold) {
            float healthCost = currentHealth * (float) Config.swordHealthCostPercent;
            player.setHealth(currentHealth - healthCost);
        }

        // 设置增益（默认 600 秒）
        player.getPersistentData().putLong(KEY_BUFF_END,
                gameTime + (long) (Config.swordBuffDuration * 20));
        // 设置冷却（默认 900 秒）
        player.getPersistentData().putLong(KEY_COOLDOWN_END,
                gameTime + (long) (Config.swordCooldown * 20));
        // 重置通知标记
        player.getPersistentData().putBoolean(KEY_NOTIFIED, false);

        // 粒子 + 音效
        if (player instanceof ServerPlayer serverPlayer) {
            ServerLevel serverLevel = serverPlayer.serverLevel();
            spawnGoldenLightningParticles(serverLevel,
                    player.getX(), player.getY(), player.getZ());
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 2.0F, 0.8F);
        }

        player.sendSystemMessage(Component.translatable(
                "message.enchantment_expansion.sotfw.activated"
        ).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        LOGGER.debug("[SOTFW] {} 激活了「人的意志」", player.getName().getString());
    }

    // ============================================================
    // 事件3：攻击/受击事件（LivingHurtEvent）
    // ============================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        // ---- 玩家作为攻击者 ----
        if (event.getSource().getEntity() instanceof Player attacker) {
            ItemStack weapon = attacker.getMainHandItem();
            if (weapon.getItem() instanceof SwordOfTheFreeWill) {
                if (hasActiveBuff(attacker)) {
                    // 伤害提升
                    event.setAmount(event.getAmount()
                            * (1.0f + (float) Config.swordDamageBoostPercent));
                }

                if (hasActiveBuff(attacker) && event.getEntity() instanceof Monster monster) {
                    if (attacker.getRandom().nextDouble() < Config.swordLightningChance) {
                        if (monster.level() instanceof ServerLevel serverLevel) {
                            // ★ 使用自定义闪电实体（纯视觉）：
                            //   原版闪电会销毁附近掉落物、误伤玩家、点燃方块，
                            //   自定义闪电只保留渲染与雷声，副作用全部取消。
                            CustomLightningEntity.spawn(serverLevel,
                                    monster.getX(), monster.getY(), monster.getZ());
                            spawnGoldenLightningParticles(serverLevel,
                                    monster.getX(), monster.getY(), monster.getZ());
                        }
                        // 额外魔法伤害（只对目标怪物，不波及其他实体）
                        monster.hurt(monster.level().damageSources().magic(),
                                (float) Config.swordLightningDamage);
                        monster.setRemainingFireTicks(40);
                    }
                }
            }
        }

        // ---- 玩家作为受害者 ----
        if (event.getEntity() instanceof Player player) {
            if (hasSwordInInventory(player)) {
                event.setAmount(event.getAmount()
                        * (1.0f - (float) Config.swordPassiveDamageReduction));
            }
        }
    }

    // ============================================================
    // 事件4：玩家 Tick（护甲管理 + 冷却通知）
    // ============================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;

        Player player = event.player;
        boolean hasSword = hasSwordInInventory(player);

        // 护甲修饰符
        manageArmorModifier(player, hasSword);

        // 冷却通知
        long cooldownEnd = player.getPersistentData().getLong(KEY_COOLDOWN_END);
        boolean notified = player.getPersistentData().getBoolean(KEY_NOTIFIED);

        if (cooldownEnd > 0 && !notified && player.level().getGameTime() >= cooldownEnd) {
            player.sendSystemMessage(Component.translatable(
                    "message.enchantment_expansion.sotfw.cooldown_done"
            ).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            player.getPersistentData().putBoolean(KEY_NOTIFIED, true);
            LOGGER.debug("[SOTFW] {} 的「人的意志」冷却已结束", player.getName().getString());
        }
    }

    // ============================================================
    // 事件5：物品丢弃时清除护甲修饰符
    // ============================================================
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (event.getEntity().getItem().getItem() instanceof SwordOfTheFreeWill) {
            if (event.getPlayer() != null) {
                AttributeInstance armorAttr = event.getPlayer().getAttribute(Attributes.ARMOR);
                if (armorAttr != null) {
                    armorAttr.removeModifier(ARMOR_MODIFIER_UUID);
                }
            }
        }
    }

    // ============================================================
    // 事件6：世界 Tick 事件 — 人权剑掉落光柱粒子
    //
    // 原理：
    //   每当凋灵死亡掉落人权剑时，剑的 ItemEntity UUID 被加入
    //   pendingBeamEntities 表。此方法每 tick 遍历该表，为每个
    //   未超时的掉落物播放心得金色 END_ROD 粒子（从地面升起的光柱）。
    //
    // 为什么不用 getEntities() 全扫描？
    //   直接通过 UUID 定位实体（level.getEntity(uuid)）是 O(1) 查找，
    //   比全维度遍历快得多。Active 实体数量通常极少（1 个），开销可忽略。
    // ============================================================
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;

        long now = serverLevel.getGameTime();

        // 遍历所有待处理的光柱实体
        pendingBeamEntities.entrySet().removeIf(entry -> {
            UUID entityId = entry.getKey();
            long spawnTime = entry.getValue();
            long elapsed = now - spawnTime;

            // 已超过 5 秒 → 移除，不再处理
            if (elapsed >= BEAM_DURATION_TICKS) return true;

            // 从世界中获取该实体
            Entity entity = serverLevel.getEntity(entityId);
            if (entity == null || !entity.isAlive()) return true; // 已被拾取 → 移除

            // ============================================================
            // 播放金色光柱粒子
            //   每 tick 生成 4 个 END_ROD 粒子，从物品下方到上方 2 格，
            //   形成持续升起的金色光柱效果。
            // ============================================================
            double x = entity.getX();
            double y = entity.getY();
            double z = entity.getZ();

            // 底部扩散光晕
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    x, y + 0.1, z,
                    2, 0.3, 0.0, 0.3, 0.01);
            // 中部上升粒子
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    x, y + 1.0, z,
                    2, 0.2, 0.3, 0.2, 0.02);
            // 顶部爆发
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    x, y + 2.0, z,
                    1, 0.3, 0.1, 0.3, 0.03);

            // 附加金色闪光（ELECTRIC_SPARK）加强光柱视觉效果
            if (elapsed % 10 == 0) { // 每 0.5 秒额外闪一次
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        x, y + 1.0, z,
                        6, 0.5, 0.8, 0.5, 0.1);
            }

            return false; // 保留在表中
        });
    }

    // ============================================================
    // 事件7：工具提示（不需要额外操作，NBT 附魔自动显示）
    // ============================================================
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        // 无需额外处理，Enchantments NBT 由父类自动渲染
    }
}