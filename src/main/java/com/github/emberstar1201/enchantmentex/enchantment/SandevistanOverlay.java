package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// ========================================================================
// 【斯安维斯坦】屏幕特效覆盖层（客户端专用）
//
//   1. 本地玩家是激活者：全屏淡青色调 + 上下边缘渐深 + 顶部剩余时间
//   2. 本地玩家在他人的时缓场内：极淡青色一闪，表示"世界被减缓了"
//
// 使用 Forge 1.20.1 的 RegisterGuiOverlaysEvent 注册，
// 绘制在快捷栏图层之上。
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SandevistanOverlay {

    // 激活者：全屏淡青色（ARGB，alpha≈9%）
    private static final int ACTIVATOR_FILL = 0x1800CCFF;
    // 被时缓的旁人：极淡青色（alpha≈4%）
    private static final int SLOWED_FILL = 0x0A00CCFF;
    // 边缘渐变条颜色
    private static final int EDGE_BASE = 0x000099CC;

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(),
                "sandevistan_effect",
                SandevistanOverlay::render);
    }

    private static void render(ForgeGui gui, GuiGraphics guiGraphics,
                               float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui) return;

        SandevistanClientHandler.ClientState ownState =
                SandevistanClientHandler.ACTIVE.get(mc.player.getUUID());

        if (ownState != null) {
            // ===== 激活者视角 =====
            guiGraphics.fill(0, 0, screenWidth, screenHeight, ACTIVATOR_FILL);

            // 上下边缘渐深（模拟全息面罩视野）
            int edgeBands = 14;
            for (int i = 0; i < edgeBands; i++) {
                int alpha = 0x22 - i * 2;  // 越靠屏幕中央越透明
                if (alpha <= 0) break;
                int color = (alpha << 24) | (EDGE_BASE & 0x00FFFFFF);
                // 顶部
                guiGraphics.fill(0, i, screenWidth, i + 1, color);
                // 底部
                guiGraphics.fill(0, screenHeight - 1 - i, screenWidth, screenHeight - i, color);
            }

            // 顶部剩余时间文字
            long remainTicks = Math.max(0, ownState.endTime - mc.level.getGameTime());
            double remainSeconds = remainTicks / 20.0;
            Font font = mc.font;
            Component text = Component.translatable(
                    "overlay.enchantment_expansion.sandevistan.active",
                    String.format("%.1f", remainSeconds));
            int textWidth = font.width(text);
            guiGraphics.drawString(font, text,
                    (screenWidth - textWidth) / 2, 26, 0x00E5FF, true);

        } else if (!SandevistanClientHandler.ACTIVE.isEmpty()) {
            // ===== 被他人时缓波及：极淡青色提示 =====
            guiGraphics.fill(0, 0, screenWidth, screenHeight, SLOWED_FILL);
        }
    }
}
