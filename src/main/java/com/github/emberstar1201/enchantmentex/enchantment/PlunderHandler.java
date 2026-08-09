package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
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
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.tags.ItemTags;
import net.minecraft.core.Holder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【强夺】附魔事件处理器
//
// 触发时机：LivingDropsEvent —— 实体死亡、原版掉落列表已收集完成。
//
// 核心机制：
//   1. 稀有战利品（已存在于 drops 列表中的头颅/唱片等）→ 强制增加数量 = 等级值
//      （例如 Lv III +3，原本 1 个凋灵骷髅头变成 4 个）
//   2. 对特定"按原版概率触发"的稀有掉落，主动补发：
//      a) 凋灵骷髅（WitherSkeleton）：原版 2.5% 概率掉头颅，强夺让此次必掉 1 个
//         → 如果 drops 中没有头颅，主动补一个；如果已有，count += level
//      b) 爬行者（Creeper）：原版需被骷髅射杀才掉唱片，强夺让被玩家剑杀也必掉
//         → 主动从 #creeper_drop_music_discs 标签中随机选一张加入 drops
//   3. BOSS 固定掉落（下界之星、龙蛋）不受影响：
//      - 下界之星：凋灵死亡时已通过特殊掉落加入 drops，本处理器不识别其为"稀有"
//        战利品（仅识别头颅、唱片两类）→ 自然不受加成
//      - 龙蛋：末影龙死亡时生成在返回传送门上，不通过 LivingDropsEvent → 完全不经过本处理器
//
// 数量加成公式：extraCount = PlunderEnchantment.getExtraRareLootCount(level)
//   Lv I = +1, Lv II = +2, Lv III = +3
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class PlunderHandler {

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        Level level = victim.level();

        // 仅服务端执行
        if (level.isClientSide()) {
            return;
        }

        // 1. 判断击杀者是否是玩家
        if (event.getSource() == null || !(event.getSource().getEntity() instanceof Player killer)) {
            return;
        }

        // 2. 检查玩家主手武器是否有"强夺"附魔
        ItemStack weaponStack = killer.getMainHandItem();
        int plunderLevel = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.PLUNDER.get(), weaponStack);
        if (plunderLevel <= 0) {
            return;
        }

        // 3. 计算额外掉落数量（按等级）
        int extraCount = PlunderEnchantment.getExtraRareLootCount(plunderLevel);

        // 4. 第一阶段：对已在 drops 列表中的稀有战利品，增加数量
        //    遍历 drops 中每个 ItemEntity，识别"头颅类"和"唱片类"
        //    若是稀有物品，把 ItemStack 的 count 增加 extraCount
        List<ItemEntity> dropsList = new ArrayList<>(event.getDrops());  // 复制避免并发修改
        for (ItemEntity dropEntity : dropsList) {
            ItemStack stack = dropEntity.getItem();
            if (isRareLoot(stack)) {
                // 增加数量：原 count + extraCount
                stack.grow(extraCount);
                // ItemEntity 内部 ItemStack 已被修改（同一引用），无需重新 setItem
            }
        }

        // 5. 第二阶段：对特定生物主动补发稀有战利品
        //    5a. 凋灵骷髅：原版 2.5% 概率掉头颅，强夺让此次必掉
        if (victim instanceof WitherSkeleton witherSkelly) {
            // 检查 drops 中是否已包含凋灵骷髅头颅
            boolean alreadyHasSkull = false;
            for (ItemEntity dropEntity : event.getDrops()) {
                if (dropEntity.getItem().is(Items.WITHER_SKELETON_SKULL)) {
                    alreadyHasSkull = true;
                    break;
                }
            }
            // 如果原版这次没掉头颅（2.5% 概率外），主动补一个
            if (!alreadyHasSkull) {
                ItemStack skullStack = new ItemStack(Items.WITHER_SKELETON_SKULL);
                skullStack.setCount(1 + extraCount);  // 基础1 + 等级加成
                addDropAtVictim(event, witherSkelly, skullStack);
            }
            // 如果已经掉过头颅，第一阶段已 grow 过数量，这里不再补
        }

        //    5b. 爬行者：原版需被骷髅射杀才掉唱片，强夺让玩家剑杀也必掉
        if (victim instanceof Creeper creeper) {
            // 从原版 #creeper_drop_music_discs 标签中随机选一张
            // 该标签包含 13、cat、blocks、chirp、far、mall、mellohi、stal、strad、ward、11、wait、otherside 等
            ItemStack discStack = getRandomMusicDisc(level.getRandom());
            if (!discStack.isEmpty()) {
                discStack.setCount(1 + extraCount);  // 基础1 + 等级加成
                addDropAtVictim(event, creeper, discStack);
            }
        }
    }

    // ========================================================================
    // 工具方法：判断物品栈是否为"稀有战利品"
    //
    // 用户要求的稀有战利品范围：头颅、唱片（用户列举的"头颅、唱片、下界之星"中，
    //   下界之星属 BOSS 固定掉落，本处理器不识别其为稀有，避免对 BOSS 加成）
    // ========================================================================
    private static boolean isRareLoot(ItemStack stack) {
        Item item = stack.getItem();

        // 1. 头颅类：所有玩家/怪物头颅（player_head, skeleton_skull, zombie_head,
        //    creeper_head, wither_skeleton_skull, dragon_head, piglin_head）
        // 用 ResourceLocation 前缀匹配 "_head" / "_skull"，避免 mapping 字段差异
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        String path = itemId.getPath();
        if (path.endsWith("_head") || path.endsWith("_skull")) {
            return true;
        }

        // 2. 唱片类：用原版 ItemTags MUSIC_DISCS 标签（包含所有 13 张唱片）
        //    该标签跨版本稳定，比硬编码列表更稳健
        if (stack.is(ItemTags.MUSIC_DISCS)) {
            return true;
        }

        // 不识别下界之星、龙蛋等 BOSS 掉落（用户明确要求 BOSS 不受影响）
        return false;
    }

    // ========================================================================
    // 工具方法：从 #creeper_drop_music_discs 标签中随机选一张唱片
    // 该标签是原版"爬行者被骷髅射杀时掉落的唱片集合"，包含 12 张主世界唱片
    //   （不含 pigstep、5、relic，因为它们来自其他途径）
    // 返回空栈表示标签为空（不应发生，但防御性处理）
    // ========================================================================
    private static ItemStack getRandomMusicDisc(RandomSource random) {
        // 用 holder 遍历标签，避免直接 getTag 集合的并发问题
        var holders = ItemTags.CREEPER_DROP_MUSIC_DISCS;
        // 通过 BuiltInRegistries 获取该标签下的所有 item holder
        var tagOptional = BuiltInRegistries.ITEM.getTag(holders);
        if (tagOptional.isEmpty()) {
            return ItemStack.EMPTY;
        }
        var items = tagOptional.get().stream().toList();
        if (items.isEmpty()) {
            return ItemStack.EMPTY;
        }
        // 随机选一个 holder，再取 Item
        Holder<Item> chosen = items.get(random.nextInt(items.size()));
        return new ItemStack(chosen.value());
    }

    // ========================================================================
    // 工具方法：在死亡生物位置生成一个掉落物，加入 LivingDropsEvent 列表
    // LivingDropsEvent.getDrops() 是 Collection<ItemEntity>，可添加新掉落物
    // 必须在死亡生物位置生成（而非玩家位置），符合原版掉落机制
    // ========================================================================
    private static void addDropAtVictim(LivingDropsEvent event, LivingEntity victim, ItemStack stack) {
        ItemEntity dropEntity = new ItemEntity(
                victim.level(),
                victim.getX(), victim.getY(), victim.getZ(),
                stack
        );
        // 给掉落物轻微随机初速度（像原版生物掉落时一样散开）
        RandomSource random = victim.level().getRandom();
        dropEntity.setDeltaMovement(
                (random.nextDouble() - 0.5) * 0.2,
                random.nextDouble() * 0.3,
                (random.nextDouble() - 0.5) * 0.2
        );
        // 加入事件 drops 列表，事件结束后由原版一并生成到世界中
        event.getDrops().add(dropEntity);
    }
}
