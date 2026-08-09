package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【攫取】附魔事件处理器
//
// 触发时机：LivingDropsEvent —— 实体死亡时，即将生成掉落物列表之前。
//
// 核心流程：
//   1. 从 DamageSource 获取击杀者；若不是玩家或空手，直接返回。
//   2. 读取击杀者主手剑上的"攫取"附魔等级，无附魔则返回。
//   3. 遍历被击杀生物的 5 个装备槽：
//        4 个护甲槽 (getArmorSlots) + 主手武器槽 (getMainHandItem)
//   4. 对每个非空装备，按附魔等级对应概率（独立ROLL）判定是否抢夺成功：
//        成功 → 复制 ItemStack（保留所有附魔/NBT/耐久）
//              → 塞入击杀者背包；若背包已满则在玩家位置生成掉落物
//              → 从被击杀生物身上移除该装备（设为 EMPTY）
//              → 同时从 LivingDropsEvent 的 drops 列表移除同名物品，
//                防止"掉落+进背包"双重复制
//        失败 → 什么都不做，让原版掉落机制正常执行
//
// 判定为"每个装备槽独立ROLL"：
//   5级附魔（80%/槽）对全套钻石+剑僵尸 5 个槽 = 最高概率 32.8% 一次全偷
//   也可能只偷到一个头盔就收手，符合"攫取"的随机感。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class SnatchHandler {

    // 抓取目标装备槽：5 个
    //   用户明确要求：getArmorSlots()（head, chest, legs, feet 共4个）
    //               + getMainHandItem()（主手，1个）
    // 注意：副手（盾牌）和身上的背包物品不抓取，严格对齐用户需求。
    private static final EquipmentSlot[] TARGET_SLOTS = new EquipmentSlot[]{
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND
    };

    // ========================================================================
    // LivingDropsEvent：实体死亡、原版掉落物已收集。
    //   event.getEntity() = 死亡的生物
    //   event.getDrops() = 原版掉落的 ItemEntity 列表（可修改）
    //   event.getSource() = 伤害来源（能取到真正的击杀者）
    // ========================================================================
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        Level level = victim.level();

        // 仅服务端执行：逻辑（修改掉落物、给玩家背包）都必须在服务端
        if (level.isClientSide()) {
            return;
        }

        // 1. 判断真正的击杀者是否是玩家
        if (event.getSource() == null || !(event.getSource().getEntity() instanceof Player killer)) {
            return;
        }

        // 2. 检查玩家主手武器是否有"攫取"附魔
        ItemStack weaponStack = killer.getMainHandItem();
        int snatchLevel = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.SNATCH.get(), weaponStack);
        if (snatchLevel <= 0) {
            return;
        }

        // 3. 根据等级拿到本槽判定概率（SnatchEnchantment 内有钳制逻辑）
        double chancePerSlot = SnatchEnchantment.getTriggerChance(snatchLevel);

        // 4. 遍历 5 个目标装备槽，每槽独立判定
        //    使用下标遍历，同步修改 victim 装备槽（设为 EMPTY）
        //    同时收集"被攫取成功的 ItemStack"引用，用于最后从 drops 去重
        List<ItemStack> snatchedItems = new ArrayList<>();

        for (EquipmentSlot slot : TARGET_SLOTS) {
            ItemStack equipStack = victim.getItemBySlot(slot);

            // 跳过空槽位（AIR）
            if (equipStack.isEmpty()) {
                continue;
            }

            // 5. 独立概率 ROLL：[0, 1) 随机浮点数 < 概率 → 成功
            if (level.random.nextDouble() >= chancePerSlot) {
                continue; // 本槽抢夺失败，原版正常处理
            }

            // ================================================================
            //  抢夺成功！
            // ================================================================

            // 5a. 复制一份装备（保留所有附魔/NBT/耐久/经验修补/自定义名）
            //     因为从 victim 装备中取出后原引用会被丢弃，所以 copy 一份给玩家
            ItemStack copyForPlayer = equipStack.copy();

            // 5b. 从被击杀生物的装备槽中移除这件装备
            //     → 目的：若原版掉落机制会"掉落这件装备"（取决于 dropChance 配置），
            //        我们已把它清空，避免再次掉落。再配合下面从 drops 列表过滤双保险。
            victim.setItemSlot(slot, ItemStack.EMPTY);

            // 5c. 尝试把装备直接放入玩家背包
            //     Inventory.add() 返回 true = 全部放入成功；false = 全部或部分没放进去
            boolean fullyAdded = killer.getInventory().add(copyForPlayer);

            if (!fullyAdded && !copyForPlayer.isEmpty()) {
                // 5d. 背包已满（或没完全装下）→ 在玩家所在位置生成地面掉落物
                //     ItemEntity 默认存在 5 分钟，随机轻微上下浮动，不会立即消失
                ItemEntity dropEntity = new ItemEntity(
                        level,
                        killer.getX(), killer.getY(), killer.getZ(),
                        copyForPlayer
                );
                // 给掉落物轻微的向上/随机速度，像原版玩家丢东西
                dropEntity.setDeltaMovement(
                        (level.random.nextDouble() - 0.5) * 0.2,
                        level.random.nextDouble() * 0.2,
                        (level.random.nextDouble() - 0.5) * 0.2
                );
                level.addFreshEntity(dropEntity);
            }

            // 5e. 记录到"已攫取列表"，后面会从 event.getDrops() 中移除匹配的
            //     → 双重保险，防止同一装备既进玩家背包又出现在掉落列表
            snatchedItems.add(equipStack); // 存原引用（与 drops 中 ItemStack.getItem() 对比）
        }

        // 6. 双重保险：若"被攫取的装备"仍然存在于 LivingDropsEvent drops 列表
        //    → 移除对应 ItemEntity，避免"一份进背包、一份掉地上"的复制 BUG
        if (!snatchedItems.isEmpty()) {
            event.getDrops().removeIf(dropEntity -> {
                ItemStack droppedStack = dropEntity.getItem();
                // 被移除的装备：Item 种类完全一致 + 数量一致 → 认为是同一件装备
                for (ItemStack snatched : snatchedItems) {
                    if (droppedStack.getItem() == snatched.getItem()
                            && droppedStack.getCount() == snatched.getCount()) {
                        return true; // 从 drops 移除
                    }
                }
                return false;
            });
        }
    }
}
