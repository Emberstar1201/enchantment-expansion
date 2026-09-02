package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.item.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ========================================================================
// 【强化卷轴】处理器
//
// 功能一：铁砧强化
//   将附魔装备 / 附魔书 + 强化卷轴放入铁砧 →
//     1. 从物品现有附魔中随机抽一个
//     2. 若该附魔未满级 → 提升一级
//     3. 若该附魔已满级 → 额外添加一个随机附魔（兼容性 + 适用性过滤）
//   卷轴为一次性消耗品（materialCost=1），经验消耗 3 级。
//
//   【确定性随机】AnvilUpdateEvent 每帧触发，为保证输出不闪烁，
//   使用基于目标物品 NBT 的确定性种子，相同输入 → 相同输出。
//
// 功能二：战利品注入
//   向所有原版遗迹宝箱注入强化卷轴，每个宝箱 10% 概率出现。
//   遗迹列表：地牢、废弃矿井、沙漠神殿、丛林神庙、要塞（图书馆/走廊/交叉口）、
//   下界要塞、末地城、末地船、古代城市（含冰盒）、埋藏宝藏、沉船（3种）、
//   海底废墟（大/小）、林地府邸、废弃传送门、堡垒遗迹（4种）、
//   村庄（武器匠/工具匠/盔甲匠）、掠夺者前哨站、雪屋。
// ========================================================================
public final class EnhancementScrollHandler {

    private EnhancementScrollHandler() {
    }

    // ========================================================================
    // 功能一：铁砧强化
    // ========================================================================
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        // 判断哪个是卷轴、哪个是附魔物品（不限制左右顺序）
        ItemStack scroll;
        ItemStack target;
        if (isScroll(left) && hasEnchantments(right)) {
            scroll = left;
            target = right;
        } else if (isScroll(right) && hasEnchantments(left)) {
            scroll = right;
            target = left;
        } else {
            return;
        }

        // 使用确定性种子：相同输入 → 相同随机结果（防止输出闪烁）
        long seed = computeSeed(target);
        RandomSource random = RandomSource.create(seed);

        ItemStack result = applyScroll(target.copy(), random);
        if (result.isEmpty()) {
            return;
        }

        event.setOutput(result);
        event.setCost(3);
        event.setMaterialCost(1);
    }

    // ========================================================================
    // 判断物品栈是否为强化卷轴
    // ========================================================================
    private static boolean isScroll(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ModItems.ENHANCEMENT_SCROLL.get();
    }

    // ========================================================================
    // 判断物品是否带有附魔（附魔装备或附魔书）
    // ========================================================================
    private static boolean hasEnchantments(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        boolean isBook = stack.getItem() == Items.ENCHANTED_BOOK;
        String tagName = isBook ? "StoredEnchantments" : "Enchantments";
        return stack.hasTag() && stack.getTag().contains(tagName, Tag.TAG_LIST)
                && stack.getTag().getList(tagName, Tag.TAG_COMPOUND).size() > 0;
    }

    // ========================================================================
    // 计算确定性种子：基于目标物品的类型和 NBT
    // ========================================================================
    private static long computeSeed(ItemStack target) {
        long seed = target.getItem().hashCode();
        if (target.hasTag()) {
            seed = seed * 31L + target.getTag().hashCode();
        }
        return seed;
    }

    // ========================================================================
    // 核心逻辑：对目标物品应用强化卷轴效果
    //
    // 返回修改后的物品栈；若无法强化则返回 ItemStack.EMPTY。
    // ========================================================================
    private static ItemStack applyScroll(ItemStack target, RandomSource random) {
        boolean isBook = target.getItem() == Items.ENCHANTED_BOOK;

        // 读取物品上的所有附魔
        Map<Enchantment, Integer> enchantments = readEnchantments(target, isBook);
        if (enchantments.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // 随机抽一个附魔
        List<Enchantment> enchList = new ArrayList<>(enchantments.keySet());
        Enchantment chosen = enchList.get(random.nextInt(enchList.size()));
        int currentLevel = enchantments.get(chosen);

        if (currentLevel < chosen.getMaxLevel()) {
            // 未满级 → 提升一级
            enchantments.put(chosen, currentLevel + 1);
        } else {
            // 已满级 → 额外添加一个随机附魔
            Enchantment newEnch = pickRandomNewEnchantment(
                    target, enchantments.keySet(), isBook, random);
            if (newEnch == null) {
                // 没有可添加的附魔 → 卷轴无法使用
                return ItemStack.EMPTY;
            }
            enchantments.put(newEnch, 1);
        }

        // 写回附魔
        writeEnchantments(target, enchantments, isBook);
        return target;
    }

    // ========================================================================
    // 读取物品上的附魔（自动区分附魔书 StoredEnchantments / 普通装备 Enchantments）
    // ========================================================================
    private static Map<Enchantment, Integer> readEnchantments(ItemStack stack, boolean isBook) {
        Map<Enchantment, Integer> map = new HashMap<>();
        String tagName = isBook ? "StoredEnchantments" : "Enchantments";
        if (!stack.hasTag() || !stack.getTag().contains(tagName, Tag.TAG_LIST)) {
            return map;
        }
        ListTag list = stack.getTag().getList(tagName, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
            if (id == null) {
                continue;
            }
            Enchantment ench = ForgeRegistries.ENCHANTMENTS.getValue(id);
            if (ench != null) {
                map.put(ench, entry.getInt("lvl"));
            }
        }
        return map;
    }

    // ========================================================================
    // 写入附魔到物品（自动区分附魔书 / 普通装备）
    // ========================================================================
    private static void writeEnchantments(ItemStack stack,
                                           Map<Enchantment, Integer> enchantments,
                                           boolean isBook) {
        ListTag list = new ListTag();
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(entry.getKey());
            if (id == null) {
                continue;
            }
            CompoundTag enchEntry = new CompoundTag();
            enchEntry.putString("id", id.toString());
            enchEntry.putInt("lvl", entry.getValue());
            list.add(enchEntry);
        }
        stack.getOrCreateTag().put(isBook ? "StoredEnchantments" : "Enchantments", list);
    }

    // ========================================================================
    // 随机选取一个新的附魔添加到物品上
    //
    // 过滤条件：
    //   1. 不与现有附魔重复
    //   2. 非诅咒附魔（排除消失诅咒、绑定诅咒）
    //   3. 对非附魔书物品，必须可附魔于该物品（canEnchant）
    //   4. 与所有现有附魔兼容（双向 checkCompatibility）
    // ========================================================================
    private static Enchantment pickRandomNewEnchantment(ItemStack stack,
                                                        Set<Enchantment> existing,
                                                        boolean isBook,
                                                        RandomSource random) {
        List<Enchantment> candidates = new ArrayList<>();
        for (Enchantment ench : ForgeRegistries.ENCHANTMENTS.getValues()) {
            // 跳过已有附魔
            if (existing.contains(ench)) {
                continue;
            }
            // 跳过诅咒附魔
            if (ench.isCurse()) {
                continue;
            }
            // 非附魔书：必须可附魔于该物品
            if (!isBook && !ench.canEnchant(stack)) {
                continue;
            }
            // 兼容性检查（checkCompatibility 为 protected，通过反射调用）
            boolean compatible = true;
            for (Enchantment ex : existing) {
                if (!areCompatible(ench, ex)) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                candidates.add(ench);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    // ========================================================================
    // 兼容性检查：通过反射调用 protected 的 checkCompatibility
    // Enchantment.checkCompatibility 为 protected，外部无法直接调用。
    // 使用反射 + setAccessible 绕过访问限制，invoke 时虚拟分派到子类重写。
    // ========================================================================
    private static boolean areCompatible(Enchantment a, Enchantment b) {
        if (a == b) {
            return false;
        }
        try {
            var method = Enchantment.class.getDeclaredMethod("checkCompatibility", Enchantment.class);
            method.setAccessible(true);
            return (boolean) method.invoke(a, b);
        } catch (Exception e) {
            // 反射失败时保守处理：假设兼容
            return true;
        }
    }

    // ========================================================================
    // 功能二：战利品注入
    // ========================================================================

    // 所有原版遗迹宝箱的战利品表 ID
    private static final Set<ResourceLocation> STRUCTURE_CHESTS = Set.of(
            ResourceLocation.parse("minecraft:chests/simple_dungeon"),
            ResourceLocation.parse("minecraft:chests/abandoned_mineshaft"),
            ResourceLocation.parse("minecraft:chests/desert_pyramid"),
            ResourceLocation.parse("minecraft:chests/jungle_temple"),
            ResourceLocation.parse("minecraft:chests/stronghold_library"),
            ResourceLocation.parse("minecraft:chests/stronghold_corridor"),
            ResourceLocation.parse("minecraft:chests/stronghold_crossing"),
            ResourceLocation.parse("minecraft:chests/nether_bridge"),
            ResourceLocation.parse("minecraft:chests/end_city_treasure"),
            ResourceLocation.parse("minecraft:chests/end_ship_treasure"),
            ResourceLocation.parse("minecraft:chests/ancient_city"),
            ResourceLocation.parse("minecraft:chests/ancient_city_ice_box"),
            ResourceLocation.parse("minecraft:chests/buried_treasure"),
            ResourceLocation.parse("minecraft:chests/shipwreck_map"),
            ResourceLocation.parse("minecraft:chests/shipwreck_supply"),
            ResourceLocation.parse("minecraft:chests/shipwreck_treasure"),
            ResourceLocation.parse("minecraft:chests/underwater_ruin_small"),
            ResourceLocation.parse("minecraft:chests/underwater_ruin_big"),
            ResourceLocation.parse("minecraft:chests/woodland_mansion"),
            ResourceLocation.parse("minecraft:chests/ruined_portal"),
            ResourceLocation.parse("minecraft:chests/bastion_treasure"),
            ResourceLocation.parse("minecraft:chests/bastion_other"),
            ResourceLocation.parse("minecraft:chests/bastion_bridge"),
            ResourceLocation.parse("minecraft:chests/bastion_hoglin_stable"),
            ResourceLocation.parse("minecraft:chests/village/village_weaponsmith"),
            ResourceLocation.parse("minecraft:chests/village/village_toolsmith"),
            ResourceLocation.parse("minecraft:chests/village/village_armorer"),
            ResourceLocation.parse("minecraft:chests/pillager_outpost"),
            ResourceLocation.parse("minecraft:chests/igloo_chest")
    );

    // 遗迹卷轴出现概率
    private static final float SCROLL_CHANCE = 0.1f;

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        if (!STRUCTURE_CHESTS.contains(event.getName())) {
            return;
        }

        // 构建卷轴战利品池：必定 roll 1 次，但卷轴条目只有 10% 概率通过条件
        // → 每个遗迹宝箱 10% 概率出现一张强化卷轴
        LootPool pool = LootPool.lootPool()
                .name("enhancement_scroll")
                .setRolls(ConstantValue.exactly(1.0F))
                .setBonusRolls(ConstantValue.exactly(0.0F))
                .add(LootItem.lootTableItem(ModItems.ENHANCEMENT_SCROLL.get())
                        .setWeight(1)
                        .when(LootItemRandomChanceCondition.randomChance(SCROLL_CHANCE))
                )
                .build();

        event.getTable().addPool(pool);
    }
}
