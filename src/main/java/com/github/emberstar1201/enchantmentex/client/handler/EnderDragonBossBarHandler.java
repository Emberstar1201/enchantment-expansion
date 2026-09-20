package com.github.emberstar1201.enchantmentex.client.handler;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.MobBuffConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 在原版末影龙 BossBar 中显示当前生命值和最大生命值。 */
@Mod.EventBusSubscriber(
        modid = EnchantmentExpansion.MODID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EnderDragonBossBarHandler {
    private EnderDragonBossBarHandler() {
    }

    @SubscribeEvent
    public static void onBossBarRender(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent bossEvent = event.getBossEvent();
        Component bossNameComponent = bossEvent.getName();
        String bossName = bossNameComponent.getString().trim();
        String translationKey = bossNameComponent.getContents() instanceof TranslatableContents translatable
                ? translatable.getKey()
                : "";

        String localizedDragonName = Component.translatable("entity.minecraft.ender_dragon")
                .getString()
                .trim();
        String localizedWitherName = Component.translatable("entity.minecraft.wither")
                .getString()
                .trim();
        boolean dragon = "entity.minecraft.ender_dragon".equals(translationKey)
                || bossName.equals(localizedDragonName)
                || bossName.contains("Ender Dragon")
                || bossName.contains("末影龙");
        boolean wither = "entity.minecraft.wither".equals(translationKey)
                || bossName.equals(localizedWitherName)
                || bossName.contains("Wither")
                || bossName.contains("凋零");
        if (!dragon && !wither) {
            return;
        }

        // 不取消原版绘制：Forge 会在此事件返回后绘制血条和名称，避免自定义重绘被 GUI 流程覆盖。
        GuiGraphics graphics = event.getGuiGraphics();
        int x = event.getX();
        int y = event.getY();
        int width = 182;

        long maxHealth = wither
                ? Math.round(MobBuffConfig.witherHealth)
                : Math.round(MobBuffConfig.enderDragonHealth);
        long health = Math.round(bossEvent.getProgress() * maxHealth);
        String healthText = Math.max(0L, health) + "/" + maxHealth;

        // 数字位于血条正下方，不会覆盖原版血条或 Boss 名称。
        graphics.drawCenteredString(
                Minecraft.getInstance().font,
                healthText,
                x + width / 2,
                y + 7,
                0xFFFFFF);
    }
}
