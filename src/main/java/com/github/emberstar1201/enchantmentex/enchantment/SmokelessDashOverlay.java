package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// ========================================================================
// 【无烟冲击】HUD 速度显示器（客户端专用）
//
// 在鞘翅飞行时，在屏幕上方中央显示当前速度倍率数值。
//
// 显示格式："速度：1.0x"
//   1.0x - 1.5x  白色 (0xFFFFFF)
//   1.6x - 2.0x  黄色 (0xFFFF55)
//   2.1x+        金色 (0xFFAA00)
//
// 速度倍率来源（优先级递减）：
//   1. SmokelessDashHandler.SPEED_CACHE（由 S2C 同步包更新，最准确）
//   2. 玩家 PersistentData（当缓存尚未填充时的后备方案）
//   3. 根据实际飞行速度估算（最坏情况后备）
//
// 实现方式：使用 Forge 1.20.1 的 RegisterGuiOverlaysEvent 注册自定义覆盖层，
// 这样不需要依赖已移除的 RenderGameOverlayEvent。
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SmokelessDashOverlay {

    private static final int TEXT_Y_OFFSET = 12;

    // ================================================================
    // 注册自定义 GUI 覆盖层（位于 HOTBAR 之上）
    // ================================================================
    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(),
                "smokeless_dash_speed",
                SmokelessDashOverlay::renderSpeedOverlay);
    }

    // ================================================================
    // 覆盖层渲染回调（每帧调用）
    // IGuiOverlay 接口：(ForgeGui, GuiGraphics, float, int, int)
    // ================================================================
    private static void renderSpeedOverlay(ForgeGui gui,
                                           GuiGraphics guiGraphics,
                                           float partialTick,
                                           int screenWidth,
                                           int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !player.isFallFlying()) return;
        if (!hasEnchantedElytra(player)) return;

        double multiplier = readMultiplier(player);
        Font font = mc.font;

        String text = String.format("速度：%.1fx", multiplier);
        int color = getColorForSpeed(multiplier);
        int textWidth = font.width(text);

        // 水平居中，顶部偏下
        int x = (screenWidth - textWidth) / 2;
        int y = TEXT_Y_OFFSET;

        // 带阴影绘制，确保在任何背景下都清晰可读
        guiGraphics.drawString(font, text, x, y, color, true);
    }

    // ================================================================
    // 检查玩家鞘翅上是否有无烟冲击附魔
    // ================================================================
    private static boolean hasEnchantedElytra(Player player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() != Items.ELYTRA) return false;
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.SMOKELESS_DASH.get(), chest);
        return level > 0;
    }

    // ================================================================
    // 读取当前速度倍率（三优先级回退策略）
    // ================================================================
    private static double readMultiplier(Player player) {
        // ★ 优先级 1：SPEED_CACHE（S2C 同步包更新，最准确）
        double cached = SmokelessDashHandler.getCachedMultiplier(player.getUUID());
        if (cached > 1.0) return cached;

        // ★ 优先级 2：PersistentData（单机模式下可直接读取）
        double nbtValue = player.getPersistentData()
                .getCompound("SmokelessDashData")
                .getDouble("speed");
        if (nbtValue >= 1.0) return nbtValue;

        // ★ 优先级 3：根据实际飞行速度估算（最坏情况后备方案）
        double horizSpeed = Math.sqrt(
                player.getDeltaMovement().x * player.getDeltaMovement().x +
                player.getDeltaMovement().z * player.getDeltaMovement().z);
        // 原版鞘翅水平滑翔约 0.75 blocks/tick（0° 俯仰时）
        double estimated = horizSpeed / 0.75;
        return Math.max(1.0, Math.round(estimated * 10.0) / 10.0);
    }

    // ================================================================
    // 速度颜色映射
    // ================================================================
    private static int getColorForSpeed(double multiplier) {
        if (multiplier >= 2.1) return 0xFFAA00; // 金色（高速）
        if (multiplier >= 1.6) return 0xFFFF55; // 黄色（中高速）
        return 0xFFFFFF; // 白色（常规速度）
    }
}