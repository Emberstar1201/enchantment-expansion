package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.enchantment.ModEnchantments;
import com.github.emberstar1201.enchantmentex.item.ModItems;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
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
import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 人权剑（Sword of the Free Will）事件处理器
//
// 【职责】
//   1. 凋灵击败后掉落人权剑（LivingDropsEvent）
//   2. 主动技能「人的意志」右键触发（PlayerInteractEvent.RightClickItem）
//   3. 主动技能加成：攻击时降下闪电 + 伤害提升（LivingHurtEvent）
//   4. 被动技能「晨曦」背包中提供护甲 + 减伤（PlayerTickEvent + LivingHurtEvent）
//   5. 冷却结束聊天栏提醒（PlayerTickEvent）
//   6. 工具提示显示内置附魔（ItemTooltipEvent）
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SwordOfTheFreeWillHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ============================================================
    // PersistentData 键名（所有数据存储在玩家 NBT 中）
    // ============================================================
    private static final String KEY_COOLDOWN_END = "SOTFW_CooldownEnd";      // 冷却结束 tick
    private static final String KEY_BUFF_END = "SOTFW_BuffEnd";            // 增益效果结束 tick
    private static final String KEY_NOTIFIED = "SOTFW_CooldownNotified";   // 是否已通知冷却结束

    // ============================================================
    // 修饰符 UUID（护甲被动，用于 Attributes.ARMOR）
    // ============================================================
    private static final UUID ARMOR_MODIFIER_UUID =
            UUID.fromString("d4e5f6a7-b8c9-0123-4567-890abcdef012");

    // 修饰符名称
    private static final String ARMOR_MODIFIER_NAME = "SOTFW Armor Bonus";

    // ============================================================
    // 工具方法：检查玩家背包/手中是否有人权剑
    // ============================================================
    private static boolean hasSwordInInventory(Player player) {
        // 检查主手
        if (player.getMainHandItem().getItem() instanceof SwordOfTheFreeWill) return true;
        // 检查副手
        if (player.getOffhandItem().getItem() instanceof SwordOfTheFreeWill) return true;
        // 检查背包（9~35 为主背包，0~8 为快捷栏）
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof SwordOfTheFreeWill) return true;
        }
        return false;
    }

    // ============================================================
    // 工具方法：检查玩家是否持有增益效果
    // ============================================================
    private static boolean hasActiveBuff(Player player) {
        long buffEnd = player.getPersistentData().getLong(KEY_BUFF_END);
        return buffEnd > player.level().getGameTime();
    }

    // ============================================================
    // 工具方法：检查玩家是否在冷却中
    // ============================================================
    private static boolean isOnCooldown(Player player) {
        long cooldownEnd = player.getPersistentData().getLong(KEY_COOLDOWN_END);
        return cooldownEnd > player.level().getGameTime();
    }

    // ============================================================
    // 工具方法：获取剩余冷却秒数
    // ============================================================
    private static int getRemainingCooldownSeconds(Player player) {
        long cooldownEnd = player.getPersistentData().getLong(KEY_COOLDOWN_END);
        long remaining = cooldownEnd - player.level().getGameTime();
        return Math.max(0, (int)(remaining / 20)); // tick → 秒
    }

    // ============================================================
    // 工具方法：为人权剑添加内置附魔 NBT（确保工具提示显示）
    // ============================================================
    private static ItemStack createEnchantedSword() {
        ItemStack sword = new ItemStack(ModItems.SWORD_OF_THE_FREE_WILL.get());

        // 直接操作 NBT 绕过兼容性限制（锋利 + 亡灵杀手不能共存）
        CompoundTag tag = sword.getOrCreateTag();
        ListTag enchantments = new ListTag();

        // 存储附魔 NBT：{id: "minecraft:sharpness", lvl: 10}
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

        tag.put("Enchantments", enchantments);
        sword.setTag(tag);

        return sword;
    }

    // ============================================================
    // 工具方法：在目标位置播放金色闪电粒子
    // ============================================================
    private static void spawnGoldenLightningParticles(ServerLevel level, double x, double y, double z) {
        // ELECTRIC_SPARK = 金色闪电粒子，数量 30，速度 0.2
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                x, y + 1, z,
                30, 0.5, 0.5, 0.5, 0.2);
        // DRAGON_BREATH 作为金色闪光辅助
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
                x, y + 1, z,
                10, 0.3, 0.3, 0.3, 0.1);
    }

    // ============================================================
    // 工具方法：扫描修改属性的修饰符状态
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
    // 事件1：凋灵掉落人权剑（LivingDropsEvent）
    // — 仅在凋灵死亡时无条件掉落一次
    // — 如果已有其他模组添加了该剑，不再重复添加
    // ============================================================
    @SubscribeEvent
    public static void onWitherDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof WitherBoss)) return;
        if (event.getEntity().level().isClientSide()) return;

        // 检查是否已有该剑的掉落
        boolean alreadyDropped = false;
        for (ItemEntity itemEntity : event.getDrops()) {
            if (itemEntity.getItem().getItem() instanceof SwordOfTheFreeWill) {
                alreadyDropped = true;
                break;
            }
        }
        if (alreadyDropped) return;

        // 创建附魔人权剑
        ItemStack sword = createEnchantedSword();

        // 添加到掉落列表
        ItemEntity swordEntity = new ItemEntity(
                event.getEntity().level(),
                event.getEntity().getX(),
                event.getEntity().getY(),
                event.getEntity().getZ(),
                sword
        );
        // 让剑不会被立即拾取，允许玩家看到掉落动画
        swordEntity.setPickUpDelay(10);
        event.getDrops().add(swordEntity);

        LOGGER.info("[SOTFW] 凋灵已被击败，人权剑已掉落");
    }

    // ============================================================
    // 事件2：右键激活主动技能（PlayerInteractEvent.RightClickItem）
    //
    // 「人的意志」：
    //   消耗当前 25% 生命值（生命值低于 50% 不再消耗，但仍可激活）
    //   获得 10 分钟伤害提升 150% 增益效果
    //   冷却时间 15 分钟
    //   激活时播放金色闪电粒子 + 雷声
    // ============================================================
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SwordOfTheFreeWill)) return;

        // 检查冷却
        if (isOnCooldown(player)) {
            int remaining = getRemainingCooldownSeconds(player);
            player.sendSystemMessage(Component.translatable(
                    "message.enchantment_expansion.sotfw.cooldown",
                    remaining / 60, remaining % 60
            ).withStyle(ChatFormatting.RED));
            return;
        }

        // ================================================================
        // 激活技能
        // ================================================================
        long gameTime = player.level().getGameTime();

        // 消耗生命值（如果当前生命值 > 50%）
        float maxHealth = player.getMaxHealth();
        float currentHealth = player.getHealth();
        if (currentHealth > maxHealth * Config.swordHealthThreshold) { // 默认 50%
            float healthCost = currentHealth * (float) Config.swordHealthCostPercent; // 默认 25%
            player.setHealth(currentHealth - healthCost);
        }

        // 设置增益效果持续时间（默认 600 秒 = 12000 tick）
        player.getPersistentData().putLong(KEY_BUFF_END,
                gameTime + (long)(Config.swordBuffDuration * 20));

        // 设置冷却结束时间（默认 900 秒 = 18000 tick）
        player.getPersistentData().putLong(KEY_COOLDOWN_END,
                gameTime + (long)(Config.swordCooldown * 20));

        // 重置冷却通知标记
        player.getPersistentData().putBoolean(KEY_NOTIFIED, false);

        // 播放金色闪电粒子 + 雷声（服务端广播给所有客户端）
        if (player instanceof ServerPlayer serverPlayer) {
            ServerLevel level = serverPlayer.serverLevel();

            // 在玩家位置播放粒子
            spawnGoldenLightningParticles(level,
                    player.getX(), player.getY(), player.getZ());

            // 播放雷声（使用 LIGHTNING_BOLT 撞击音效）
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
                    2.0F, 0.8F);
        }

        // 发送激活消息
        player.sendSystemMessage(Component.translatable(
                "message.enchantment_expansion.sotfw.activated"
        ).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        LOGGER.debug("[SOTFW] {} 激活了「人的意志」", player.getName().getString());
    }

    // ============================================================
    // 事件3：攻击时触发（LivingHurtEvent）
    //
    // 作为攻击者时（持有正义之剑）：
    //   a) 如果增益效果激活，伤害提升 150%
    //   b) 如果增益效果激活且目标为怪物，90% 概率降下闪电
    //      闪电造成固定真实伤害（默认 20）
    //
    // 作为受害者时（背包有剑）：
    //   a) 受到的所有伤害降低 25%
    // ============================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        // ================================================================
        // — 玩家作为攻击者 —
        // ================================================================
        if (event.getSource().getEntity() instanceof Player attacker) {
            ItemStack weapon = attacker.getMainHandItem();
            if (weapon.getItem() instanceof SwordOfTheFreeWill) {
                // ---- a) 伤害提升（如果增益激活） ----
                if (hasActiveBuff(attacker)) {
                    float original = event.getAmount();
                    float boosted = original * (1.0f + (float) Config.swordDamageBoostPercent);
                    event.setAmount(boosted);
                }

                // ---- b) 闪电触发（如果增益激活、目标为怪物、概率判定通过） ----
                if (hasActiveBuff(attacker) && event.getEntity() instanceof Monster monster) {
                    // 概率判定
                    if (attacker.getRandom().nextDouble() < Config.swordLightningChance) {
                        Level level = monster.level();

                        // 召唤闪电实体（视觉 + 音效）
                        if (level instanceof ServerLevel serverLevel) {
                            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
                            if (lightning != null) {
                                lightning.moveTo(monster.getX(), monster.getY(), monster.getZ());
                                lightning.setVisualOnly(false); // 闪电会正常造成伤害和火焰
                                serverLevel.addFreshEntity(lightning);
                            }

                            // 播放金色闪电粒子
                            spawnGoldenLightningParticles(serverLevel,
                                    monster.getX(), monster.getY(), monster.getZ());
                        }

                        // 应用固定真实伤害（魔法伤害无视护甲）
                        float lightningDamage = (float) Config.swordLightningDamage;
                        monster.hurt(monster.level().damageSources().magic(), lightningDamage);

                        // 被闪电击中的怪物着火（视觉效果）
                        monster.setRemainingFireTicks(40); // 2 秒火焰
                    }
                }
            }
        }

        // ================================================================
        // — 玩家作为受害者 —
        // ================================================================
        if (event.getEntity() instanceof Player player) {
            // 检查背包中是否有人权剑
            if (hasSwordInInventory(player)) {
                // 25% 伤害减免
                float reduction = (float) Config.swordPassiveDamageReduction;
                event.setAmount(event.getAmount() * (1.0f - reduction));
            }
        }
    }

    // ============================================================
    // 事件4：玩家 Tick 事件（PlayerTickEvent）
    //
    // 用途：
    //   a) 管理护甲修饰符（背包有人权剑时添加 10 护甲）
    //   b) 冷却结束通知
    // ============================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;

        Player player = event.player;
        boolean hasSword = hasSwordInInventory(player);

        // ---- a) 护甲修饰符管理 ----
        manageArmorModifier(player, hasSword);

        // ---- b) 冷却结束通知 ----
        long cooldownEnd = player.getPersistentData().getLong(KEY_COOLDOWN_END);
        boolean notified = player.getPersistentData().getBoolean(KEY_NOTIFIED);

        if (cooldownEnd > 0 && !notified) {
            // 冷却已结束（当前时间 >= 冷却结束时间）
            if (player.level().getGameTime() >= cooldownEnd) {
                player.sendSystemMessage(Component.translatable(
                        "message.enchantment_expansion.sotfw.cooldown_done"
                ).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));

                player.getPersistentData().putBoolean(KEY_NOTIFIED, true);
                LOGGER.debug("[SOTFW] {} 的「人的意志」冷却已结束", player.getName().getString());
            }
        }
    }

    // ============================================================
    // 事件5：物品丢弃时清除修饰符（ItemTossEvent）
    // ============================================================
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (event.getEntity().getItem().getItem() instanceof SwordOfTheFreeWill) {
            if (event.getPlayer() != null) {
                // 丢弃剑时立即移除护甲修饰符
                AttributeInstance armorAttr = event.getPlayer().getAttribute(Attributes.ARMOR);
                if (armorAttr != null) {
                    armorAttr.removeModifier(ARMOR_MODIFIER_UUID);
                }
            }
        }
    }

    // ============================================================
    // 事件6：工具提示——显示内置附魔和描述（ItemTooltipEvent）
    // ============================================================
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SwordOfTheFreeWill)) return;

        List<Component> tooltip = event.getToolTip();

        // 在物品名下方添加一行分隔提示（内置附魔）
        // 默认 tooltip 已经包含物品名 + 属性 + 附魔，所以这里不做额外处理
        // 附魔标签会自动显示因为 NBT 中有 Enchantments 数据
    }
}