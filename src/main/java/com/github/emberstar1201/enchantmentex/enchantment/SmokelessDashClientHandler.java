package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.network.NetworkHandler;
import com.github.emberstar1201.enchantmentex.network.SmokelessDashPacket;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

// ========================================================================
// 【无烟冲击】客户端处理器
//
// 功能：
//   1. 监听鼠标滚轮事件 → 立即更新本地 SPEED_CACHE + 发送 C2S 调速包
//   2. 提供可配置的按键绑定（备选操作方式）
//   3. 仅鞘翅飞行 + 有附魔时生效
//
// ★ 关键设计：滚轮触发时立即更新 SPEED_CACHE
//   这样客户端 PlayerTickEvent 能立刻获取新倍率并加速，
//   无需等 10 tick 一次的 S2C 同步包，响应零延迟。
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SmokelessDashClientHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ================================================================
    // 按键绑定（备选操作方式）
    // ================================================================
    public static final KeyMapping DASH_SPEED_UP_KEY = new KeyMapping(
            "key." + EnchantmentExpansion.MODID + ".dash_speed_up",
            GLFW.GLFW_KEY_UNKNOWN,  // 默认无按键
            "key.categories." + EnchantmentExpansion.MODID
    );

    public static final KeyMapping DASH_SPEED_DOWN_KEY = new KeyMapping(
            "key." + EnchantmentExpansion.MODID + ".dash_speed_down",
            GLFW.GLFW_KEY_UNKNOWN,  // 默认无按键
            "key.categories." + EnchantmentExpansion.MODID
    );

    // ================================================================
    // 注册按键映射（Mod 事件总线）
    // ================================================================
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(DASH_SPEED_UP_KEY);
        event.register(DASH_SPEED_DOWN_KEY);
    }

    // ================================================================
    // 客户端设置完成 → 注册 ClientTick + MouseScroll 到 Forge 总线
    // ================================================================
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MinecraftForge.EVENT_BUS.addListener(SmokelessDashClientHandler::onClientTick);
            MinecraftForge.EVENT_BUS.addListener(SmokelessDashClientHandler::onMouseScroll);
            LOGGER.info("[SmokelessDash] 客户端监听器注册完成");
        });
    }

    // ================================================================
    // 辅助方法：检查当前客户端玩家是否穿着带附魔的鞘翅
    // 注意：不检查 isFallFlying()，滚轮事件可能发生在飞行前
    // ================================================================
    private static boolean hasEnchantedElytra() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return false;
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() != Items.ELYTRA) return false;
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.SMOKELESS_DASH.get(), chest);
        return level > 0;
    }

    // ================================================================
    // ★ 立即更新本地 SPEED_CACHE + 发送 C2S 数据包到服务端
    // ================================================================
    private static void applySpeedChange(double delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !player.isFallFlying()) return;
        if (!hasEnchantedElytra()) return;

        // ★ 立即更新本地 SPEED_CACHE（客户端加速零延迟）
        double current = SmokelessDashHandler.getCachedMultiplier(player.getUUID());
        double newMultiplier = Math.max(1.0,
                Math.min(Config.smokelessDashMaxMultiplier, current + delta));
        SmokelessDashHandler.SPEED_CACHE.put(player.getUUID(), newMultiplier);

        LOGGER.debug("[SmokelessDash] applySpeedChange: delta={}, 新倍率={}",
                String.format("%.3f", delta),
                String.format("%.2f", newMultiplier));

        // 发送 C2S 数据包让服务端同步更新
        NetworkHandler.CHANNEL.sendToServer(new SmokelessDashPacket(delta));
    }

    // ================================================================
    // ClientTick：检测备选按键（加速/减速）
    // ================================================================
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        if (DASH_SPEED_UP_KEY.consumeClick()) {
            applySpeedChange(Config.smokelessDashScrollSensitivity);
        }
        if (DASH_SPEED_DOWN_KEY.consumeClick()) {
            applySpeedChange(-Config.smokelessDashScrollSensitivity);
        }
    }

    // ================================================================
    // MouseScroll：监听滚轮事件 → 调整速度
    //
    // ScrollDelta 正值 = 向上滚（加速），负值 = 向下滚（减速）
    // 每次滚动调整 sensitivity（默认 0.1）
    // ================================================================
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (!player.isFallFlying()) return;          // 仅在滑翔中响应
        if (!hasEnchantedElytra()) return;

        // ★ Shift + 滚轮 = 调整飞行速度（阻止原版物品切换）
        if (player.isShiftKeyDown()) {
            double scrollDelta = event.getScrollDelta();
            double speedChange = scrollDelta * Config.smokelessDashScrollSensitivity;
            applySpeedChange(speedChange);
            event.setCanceled(true);
        }
        // 不按 Shift：滚轮正常切换物品，不做任何处理
    }
}