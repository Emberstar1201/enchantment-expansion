package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.item.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

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
//        回血速度 ×2、满血时优先扣除饥饿值抵挡伤害
//
// 【NBT 标签】
//   每件嵌入了星星的盔甲在 NBT 中存储：
//   - "EmbeddedStar" → 字符串，值为 "end_star" / "ocean_star" / "life_star"
//   - "EmbedCost" → 整数，表示此次嵌入的铁砧花费（仅记录，不实际扣除）
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StarEmbedHandler {

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack input = event.getLeft();      // 盔甲（铁砧第1位）
        ItemStack material = event.getRight();  // 星星（铁砧第2位）
        
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

    // ================================================================
    // 工具方法：判断物品是否为盔甲
    // ================================================================
    private static boolean isArmor(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem;
    }

    // ========================================================================
    // 工具方法：判断物品是否为星星（终界之星 / 海洋之星 / 生命之星）
    // ========================================================================
    private static boolean isStar(ItemStack stack) {
        return stack.is(ModItems.END_STAR.get()) 
            || stack.is(ModItems.OCEAN_STAR.get())
            || stack.is(ModItems.LIFE_STAR.get());
    }
}
