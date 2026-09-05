package com.github.emberstar1201.enchantmentex.client.handler;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import vazkii.patchouli.api.PatchouliAPI;

import java.util.Map;

/**
 * 附魔书快捷查找（精确跳转修正）
 *
 * 背景问题：
 * - 帕秋莉内置的「物品栏 quick lookup」（按住 Ctrl 悬停物品一段时间自动打开对应条目）
 *   在建立「物品 → 条目」索引时使用 ItemStack.isSameItem（只比较物品类型、忽略 NBT）。
 * - 本模组所有附魔书都是 minecraft:enchanted_book + StoredEnchantments NBT 合成，
 *   导致 40+ 个附魔条目共享同一个索引键，后加载的覆盖先加载的，最终所有附魔书
 *   都会错跳到「星火不灭」条目。
 *
 * 本类解决方案：
 * - 我们自己监听 tooltip，解析附魔书的 StoredEnchantments NBT，得到精确的附魔 id。
 * - 附魔的注册名 == 帕秋莉条目 id（一一对应），用 PatchouliAPI.openBookEntry 精确打开。
 * - 用比帕秋莉默认更短的计时阈值（0.5s）抢先触发：一旦我们打开书，屏幕变为 GuiBook，
 *   帕秋莉的内部计时随即归零，不会再跳转到错误的条目标目。
 *
 * 软前置：仅当帕秋莉已加载时才生效（ModList 守卫），未安装帕秋莉时静默返回。
 */
public final class EnchantmentBookLookupHandler {
    // 终界之书的完整 ID（帕秋莉书籍）
    private static final ResourceLocation BOOK_ID =
            ResourceLocation.fromNamespaceAndPath(EnchantmentExpansion.MODID, "enchantment_expansion");

    // 我们自己的触发阈值（0.5 秒，明显短于帕秋莉默认的 quickLookup 时间）
    // 目的是在我们打开书之前，阻止帕秋莉用错误索引跳转到星火不灭。
    private static final float LOOKUP_THRESHOLD_SECONDS = 0.5F;

    // 记录按住 Ctrl 悬停附魔书的累计时间（用真实逝去时间累加，避免受帧率影响）
    private static float lookupTime = 0F;
    private static long lastEventNanos = 0L;

    private EnchantmentBookLookupHandler() {
    }

    @SubscribeEvent
    public static void onRenderTooltip(RenderTooltipEvent.Pre event) {
        // 帕秋莉未安装则不处理
        if (!ModList.get().isLoaded("patchouli")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        ItemStack stack = event.getItemStack();
        ResourceLocation enchant = findFirstModEnchantment(stack);
        // 悬停的不是「本模组附魔书」，重置计时并返回
        if (enchant == null) {
            resetTimer();
            return;
        }

        // 需要按住 Ctrl（或 Shift）才触发，与帕秋莉默认交互习惯保持一致
        if (!Screen.hasControlDown() && !Screen.hasShiftDown()) {
            resetTimer();
            return;
        }

        // 累加真实逝去时间
        long now = System.nanoTime();
        if (lastEventNanos != 0L) {
            lookupTime += (now - lastEventNanos) / 1e9f;
        }
        lastEventNanos = now;

        // 达到阈值：精确打开该附魔对应的帕秋莉条目
        if (lookupTime >= LOOKUP_THRESHOLD_SECONDS) {
            resetTimer();
            // 附魔注册名 == 条目 id，直接作为条目定位
            PatchouliAPI.get().openBookEntry(BOOK_ID,
                    ResourceLocation.fromNamespaceAndPath(EnchantmentExpansion.MODID, enchant.getPath()), 0);
        }
    }

    /**
     * 在附魔书中查找第一个属于本模组的附魔，返回其注册名。
     * 只有注册名在 enchantment_expansion 命名空间下的才算（对应帕秋莉条目的 id）。
     */
    private static ResourceLocation findFirstModEnchantment(ItemStack stack) {
        if (stack.getItem() != Items.ENCHANTED_BOOK) {
            return null;
        }
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
        for (Enchantment enchantment : enchantments.keySet()) {
            // 用 Forge 注册表获取附魔的注册名（Enchantment 没有 getRegistryName，需要经注册表查）
            ResourceLocation key = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
            if (key != null && EnchantmentExpansion.MODID.equals(key.getNamespace())) {
                return key;
            }
        }
        return null;
    }

    private static void resetTimer() {
        lookupTime = 0F;
        lastEventNanos = 0L;
    }
}
