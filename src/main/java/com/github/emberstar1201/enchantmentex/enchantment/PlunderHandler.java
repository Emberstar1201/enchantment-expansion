package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.PlunderConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【强夺】附魔事件处理器（v3 调整版）
//
// 【事件拆分】
//   1. LivingDropsEvent —— 稀有战利品保底（头颅 / 唱片）
//   2. LivingDeathEvent —— 怪物武器/盔甲掉落（按等级控制概率与耐久度）
//
// 【一、稀有战利品保底】
//   I级 ：75%  概率保底，掉落 1~2 个
//   II级：100% 概率保底，掉落 2~3 个
//   III级：100% 概率保底，掉落 3~4 个
//   - 已在掉落列表中的头颅/唱片 → 数量补足到所掷数量
//   - 凋灵骷髅（原版 2.5% 掉头）→ 触发时补掉头颅
//   - 爬行者（原版需骷髅射杀才掉唱片）→ 触发时补掉随机唱片
//   - BOSS 固定掉落（下界之星、龙蛋）不受影响
//
// 【二、怪物武器/盔甲掉落】
//   I级 ：大概率掉落（默认 60%），耐久度约 25%（极低概率满耐久）
//   II级：100% 掉落，耐久度 50%~75%
//   III级：100% 掉落，满耐久
//   - 在 LivingDeathEvent 中提前处理（该事件先于原版 dropAllDeathLoot），
//     强制掉落时清空怪物对应装备槽 → 阻止原版 dropEquipment 重复掉落。
//   - 对玩家（PVP）不生效，只作用于怪物。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlunderHandler {

    // ========================================================================
    // 【事件 1】LivingDropsEvent —— 稀有战利品保底
    // ========================================================================
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        Level level = victim.level();

        // 仅服务端执行
        if (level.isClientSide()) {
            return;
        }

        // 击杀者必须是玩家
        if (event.getSource() == null || !(event.getSource().getEntity() instanceof Player killer)) {
            return;
        }

        // 检查玩家主手武器是否有"强夺"附魔
        ItemStack weaponStack = killer.getMainHandItem();
        int plunderLevel = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.PLUNDER.get(), weaponStack);
        if (plunderLevel <= 0) {
            return;
        }

        RandomSource random = level.getRandom();

        // 等级保底概率判定：未触发则本事件不做任何稀有战利品处理
        double chance = PlunderConfig.getRareLootChance(plunderLevel);
        if (random.nextFloat() >= chance) {
            return;
        }

        // 掷出本次稀有战利品数量（闭区间 [min, max]）
        int count = PlunderConfig.rollRareLootCount(plunderLevel, random);

        // ============ 第一阶段：对已在掉落列表中的稀有战利品补足数量 ============
        List<ItemEntity> dropsList = new ArrayList<>(event.getDrops());
        for (ItemEntity dropEntity : dropsList) {
            ItemStack stack = dropEntity.getItem();
            if (isRareLoot(stack) && stack.getCount() < count) {
                stack.setCount(count); // 补足到所掷数量
            }
        }

        // ============ 第二阶段：对特定生物主动补发稀有战利品 ============
        // 凋灵骷髅：原版 2.5% 概率掉头颅，强夺触发时必掉
        if (victim instanceof WitherSkeleton && !hasRareLootOf(event, Items.WITHER_SKELETON_SKULL)) {
            ItemStack skullStack = new ItemStack(Items.WITHER_SKELETON_SKULL, count);
            addDropAtVictim(event, victim, skullStack);
        }

        // 爬行者：原版需被骷髅射杀才掉唱片，强夺触发时必掉一张随机唱片
        if (victim instanceof Creeper && !hasAnyMusicDisc(event)) {
            ItemStack discStack = getRandomMusicDisc(random);
            if (!discStack.isEmpty()) {
                discStack.setCount(count);
                addDropAtVictim(event, victim, discStack);
            }
        }
    }

    // ========================================================================
    // 【事件 2】LivingDeathEvent —— 怪物武器/盔甲掉落
    //
    //   在死亡事件中：
    //   1. 按等级概率判定是否掉落该槽位装备
    //   2. 按等级耐久度范围设置掉落装备的剩余耐久
    //   3. 清空怪物对应装备槽 → 阻止原版 dropEquipment 重复掉落
    // ========================================================================
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Level level = victim.level();

        // 仅服务端执行
        if (level.isClientSide()) {
            return;
        }

        // PVP 不生效（不处理玩家）
        if (victim instanceof Player) {
            return;
        }

        DamageSource source = event.getSource();
        if (source == null || !(source.getEntity() instanceof Player killer)) {
            return;
        }

        // 检查玩家主手武器是否有"强夺"附魔
        ItemStack weaponStack = killer.getMainHandItem();
        int plunderLevel = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.PLUNDER.get(), weaponStack);
        if (plunderLevel <= 0) {
            return;
        }

        RandomSource random = level.getRandom();
        double dropChance = PlunderConfig.getEquipDropChance(plunderLevel);

        // 遍历全部装备槽（主手、副手、头、胸、腿、脚）
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            EquipmentSlot.Type type = slot.getType();
            if (type != EquipmentSlot.Type.HAND && type != EquipmentSlot.Type.ARMOR) {
                continue;
            }

            ItemStack equip = victim.getItemBySlot(slot);
            if (equip.isEmpty()) {
                continue;
            }

            // 概率判定：未过则保留原版随机掉落逻辑
            if (random.nextFloat() >= dropChance) {
                continue;
            }

            // 复制装备并按等级设置耐久
            ItemStack dropStack = equip.copy();
            applyPlunderDurability(dropStack, plunderLevel, random);

            // 在怪物位置生成掉落物
            spawnDropAt(level, victim, dropStack);

            // 清空槽位 → 原版 dropEquipment 不再掉落该物品，避免重复
            victim.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    // ========================================================================
    // 装备耐久度处理：按等级设置剩余耐久比例
    // ========================================================================
    private static void applyPlunderDurability(ItemStack stack, int level, RandomSource random) {
        int maxDamage = stack.getMaxDamage();
        if (maxDamage <= 0) {
            return; // 无耐久概念（非工具类）
        }

        double ratio = PlunderConfig.rollEquipDurability(level, random);
        int damage = (int) Math.round(maxDamage * (1.0 - ratio));
        // 满耐久：damage 可能为 0；钳制到合法范围
        stack.setDamageValue(Math.max(0, Math.min(maxDamage, damage)));
    }

    // ========================================================================
    // 在怪物位置生成一个掉落物实体（LivingDeathEvent 使用）
    // ========================================================================
    private static void spawnDropAt(Level level, LivingEntity victim, ItemStack stack) {
        ItemEntity dropEntity = new ItemEntity(
                level,
                victim.getX(), victim.getY(), victim.getZ(),
                stack
        );
        RandomSource random = level.getRandom();
        dropEntity.setDeltaMovement(
                (random.nextDouble() - 0.5) * 0.2,
                random.nextDouble() * 0.3,
                (random.nextDouble() - 0.5) * 0.2
        );
        level.addFreshEntity(dropEntity);
    }

    // ========================================================================
    // 在死亡生物位置生成一个掉落物，加入 LivingDropsEvent 列表
    // ========================================================================
    private static void addDropAtVictim(LivingDropsEvent event, LivingEntity victim, ItemStack stack) {
        ItemEntity dropEntity = new ItemEntity(
                victim.level(),
                victim.getX(), victim.getY(), victim.getZ(),
                stack
        );
        RandomSource random = victim.level().getRandom();
        dropEntity.setDeltaMovement(
                (random.nextDouble() - 0.5) * 0.2,
                random.nextDouble() * 0.3,
                (random.nextDouble() - 0.5) * 0.2
        );
        event.getDrops().add(dropEntity);
    }

    // ========================================================================
    // 工具方法：判断物品栈是否为"稀有战利品"（头颅 / 唱片）
    // ========================================================================
    private static boolean isRareLoot(ItemStack stack) {
        Item item = stack.getItem();

        // 1. 头颅类：所有玩家/怪物头颅（*_head / *_skull）
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        String path = itemId.getPath();
        if (path.endsWith("_head") || path.endsWith("_skull")) {
            return true;
        }

        // 2. 唱片类：原版 MUSIC_DISCS 标签
        if (stack.is(ItemTags.MUSIC_DISCS)) {
            return true;
        }

        // 不识别下界之星、龙蛋等 BOSS 固定掉落
        return false;
    }

    // ========================================================================
    // 工具方法：drops 列表中是否已包含指定物品
    // ========================================================================
    private static boolean hasRareLootOf(LivingDropsEvent event, Item target) {
        for (ItemEntity dropEntity : event.getDrops()) {
            if (dropEntity.getItem().is(target)) {
                return true;
            }
        }
        return false;
    }

    // ========================================================================
    // 工具方法：drops 列表中是否已有任何唱片
    // ========================================================================
    private static boolean hasAnyMusicDisc(LivingDropsEvent event) {
        for (ItemEntity dropEntity : event.getDrops()) {
            if (dropEntity.getItem().is(ItemTags.MUSIC_DISCS)) {
                return true;
            }
        }
        return false;
    }

    // ========================================================================
    // 工具方法：从 #creeper_drop_music_discs 标签中随机选一张唱片
    // ========================================================================
    private static ItemStack getRandomMusicDisc(RandomSource random) {
        var tagOptional = BuiltInRegistries.ITEM.getTag(ItemTags.CREEPER_DROP_MUSIC_DISCS);
        if (tagOptional.isEmpty()) {
            return ItemStack.EMPTY;
        }
        var items = tagOptional.get().stream().toList();
        if (items.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Holder<Item> chosen = items.get(random.nextInt(items.size()));
        return new ItemStack(chosen.value());
    }
}