package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.item.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
// ========================================================================
// 【星星嵌入系统】铁砧合并处理器
//
// 核心机制：
//   - 玩家在铁砧中：盔甲（第1位）+ 星星（第2位）
//   - 合并后：盔甲保留原属性，获得星星的所有被动效果
//   - 星星本身不被消耗（仍在副手/背包），可重复嵌入其他盔甲
//   - 无附魔光效，通过 NBT 标签"EmbeddedStar"区分已嵌入状态
//
// 【支持的星星类型】
//   1. 终界之星 (END_STAR)
//      → 玩家穿戴此盔甲后持续获得：飞行、80%减伤、经验伤害加成、
//        免疫挖掘惩罚、免疫移动减速
//   2. 海洋之星 (OCEAN_STAR)
//      → 玩家穿戴此盔甲后持续获得：水下挖掘加速、水流免疫、
//        不扣氧气、守卫者中立化
//   3. 生命之星 (LIFE_STAR)
//      → 玩家穿戴此盔甲后持续获得：生命上限 +30 (20→50)、
//        回血速度 ×2、饥饿与饱和度消耗速度 −50%
//   4. 虚空之星 (VOID_STAR)
//      → 玩家穿戴此盔甲后持续获得：免疫摔落伤害、免疫虚空伤害、
//        坠入虚空时传送回出生点（或已设置的床/重生锚）
//   5. 星辉之星 (STARLIGHT_STAR)
//      → 玩家穿戴此盔甲后持续获得：夜间移速 +20%、夜间夜视、
//        击杀生物经验掉落 ×2
//   6. 晨曦之星 (DAWN_STAR)
//      → 玩家穿戴此盔甲后持续获得：击杀积累「晨光」，满层爆发「晨曦」
//        （180 秒内伤害 ×1.25、暴击率 +10%、移速 +10%、每 2 秒回 2.5 点生命）
//
// 【NBT 标签】
//   每件嵌入了星星的盔甲在 NBT 中存储：
//   - "EmbeddedStar" → 字符串，值为 "end_star" / "ocean_star" / "life_star"
//                              / "void_star" / "starlight_star" / "dawn_star"
//   - "EmbedCost" → 整数，表示此次嵌入的铁砧花费（仅记录，不实际扣除）
// ========================================================================
public class StarEmbedHandler {

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack input = event.getLeft();      // 盔甲（铁砧第1位）
        ItemStack material = event.getRight();  // 星星（铁砧第2位）

        // 左槽放已嵌入星星的盔甲、右槽留空：进入取出模式。
        if (isArmorWithEmbeddedStar(input) && material.isEmpty()) {
            ItemStack result = input.copy();
            result.removeTagKey("EmbeddedStar");
            result.removeTagKey("EmbedCost");
            event.setOutput(result);
            event.setCost(5);
            event.setMaterialCost(0);
            return;
        }

        // 校验：第1位必须是盔甲，第2位必须是星星
        if (!isArmor(input) || !isStar(material)) {
            return;
        }

        // ================================================================
        // 星星类型判断 → 构建合并后的物品
        // ================================================================
        String starType = null;
        int baseCost = 5;  // 基础铁砧花费（5 级）
        
        if (material.is(ModItems.END_STAR.get())) {
            starType = "end_star";
            baseCost = 5;  // 终界之星嵌入花费
        } else if (material.is(ModItems.OCEAN_STAR.get())) {
            starType = "ocean_star";
            baseCost = 5;  // 海洋之星嵌入花费
        } else if (material.is(ModItems.LIFE_STAR.get())) {
            starType = "life_star";
            baseCost = 5;  // 生命之星嵌入花费
        } else if (material.is(ModItems.VOID_STAR.get())) {
            starType = "void_star";
            baseCost = 5;  // 虚空之星嵌入花费
        } else if (material.is(ModItems.STARLIGHT_STAR.get())) {
            starType = "starlight_star";
            baseCost = 5;  // 星辉之星嵌入花费
        } else if (material.is(ModItems.DAWN_STAR.get())) {
            starType = "dawn_star";
            baseCost = 5;  // 晨曦之星嵌入花费
        }

        if (starType == null) {
            return;
        }

        // ================================================================
        // 构建嵌入后的盔甲：复制输入盔甲，写入 NBT 标签
        // ================================================================
        ItemStack result = input.copy();
        result.removeTagKey("EmbeddedStar");  // 清空可能的旧标签
        result.removeTagKey("EmbedCost");
        
        result.getOrCreateTag().putString("EmbeddedStar", starType);
        result.getOrCreateTag().putInt("EmbedCost", baseCost);

        // ================================================================
        // 设置铁砧合并结果
        // ================================================================
        event.setOutput(result);
        event.setCost(baseCost);
        event.setMaterialCost(0);  // 星星不被消耗
    }

    // 取出模式完成后，将星星返还给玩家；背包已满时掉落在脚下。
    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        ItemStack result = event.getOutput();
        ItemStack input = event.getLeft();
        if (!isArmor(result) || result.hasTag() && result.getTag().contains("EmbeddedStar")) {
            return;
        }


        if (!isArmorWithEmbeddedStar(input)) {
            return;
        }

        ItemStack star = starForType(input.getTag().getString("EmbeddedStar"));
        if (star.isEmpty()) {
            return;
        }

        Player player = event.getEntity();
        if (!player.addItem(star)) {
            player.drop(star, false);
        }
    }

    // ========================================================================
    // 【ItemTooltipEvent】在盔甲物品提示中显示已嵌入的星星信息
    //
    // 说明：嵌入信息存储在第 1 位盔甲的 NBT 中，这里在渲染 tooltip 时读取
    //       "EmbeddedStar" 标签，追加对应星星的效果说明文字。
    // ========================================================================
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!isArmor(stack)) {
            return;
        }
        StarEmbedUtils.appendEmbeddedStarInfo(stack, event.getToolTip());
    }

    private static boolean isArmorWithEmbeddedStar(ItemStack stack) {
        return isArmor(stack)
                && stack.hasTag()
                && stack.getTag().contains("EmbeddedStar")
                && !stack.getTag().getString("EmbeddedStar").isEmpty();
    }

    private static ItemStack starForType(String starType) {
        return switch (starType) {
            case "end_star" -> new ItemStack(ModItems.END_STAR.get());
            case "ocean_star" -> new ItemStack(ModItems.OCEAN_STAR.get());
            case "life_star" -> new ItemStack(ModItems.LIFE_STAR.get());
            case "void_star" -> new ItemStack(ModItems.VOID_STAR.get());
            case "starlight_star" -> new ItemStack(ModItems.STARLIGHT_STAR.get());
            case "dawn_star" -> new ItemStack(ModItems.DAWN_STAR.get());
            default -> ItemStack.EMPTY;
        };
    }

    // ========================================================================
    // 工具方法：判断物品是否为盔甲
    // ========================================================================
    private static boolean isArmor(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem;
    }

    // ========================================================================
    // 工具方法：判断物品是否为星星
    // （终界之星 / 海洋之星 / 生命之星 / 虚空之星 / 星辉之星 / 晨曦之星）
    // ========================================================================
    private static boolean isStar(ItemStack stack) {
        return stack.is(ModItems.END_STAR.get()) 
            || stack.is(ModItems.OCEAN_STAR.get())
            || stack.is(ModItems.LIFE_STAR.get())
            || stack.is(ModItems.VOID_STAR.get())
            || stack.is(ModItems.STARLIGHT_STAR.get())
            || stack.is(ModItems.DAWN_STAR.get());
    }
}
