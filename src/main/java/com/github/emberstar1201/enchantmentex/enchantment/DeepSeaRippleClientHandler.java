package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.DeepSeaRippleConfig;
import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.network.DeepSeaRippleScrollPacket;
import com.github.emberstar1201.enchantmentex.network.NetworkHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

// ========================================================================
// 【深海的涟漪】客户端处理器
//
// 功能：
//   1. 监听鼠标滚轮事件 InputEvent.MouseScrollingEvent
//   2. 当【Shift 按住】+【靴子有深海的涟漪附魔】+【玩家在水中】时：
//        · 取消本次滚轮事件（防止切换物品栏/视角俯仰）
//        · 调整本地缓存中的游泳倍率（零延迟响应）
//        · 发送 C2S 数据包让服务端同步倍率
// ========================================================================
@Mod.EventBusSubscriber(
        modid = EnchantmentExpansion.MODID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public class DeepSeaRippleClientHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ================================================================
    // 客户端启动完成后 → 将 MouseScrollingEvent 注册到 Forge 总线
    // ================================================================
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                MinecraftForge.EVENT_BUS.addListener(
                        DeepSeaRippleClientHandler::onMouseScroll
                )
        );
        LOGGER.info("[DeepSeaRipple] 客户端滚轮监听器注册完成");
    }

    // ================================================================
    // 辅助方法：判断客户端玩家是否满足“可调节游泳速度”的所有条件
    // ================================================================
    private static boolean canAdjust(Player player) {
        if (player == null) return false;
        if (!DeepSeaRippleConfig.isEnabled()) return false;

        // 检查靴子附魔
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.DEEP_SEA_RIPPLE.get(), boots);
        if (level <= 0) return false;

        // 必须在水中（含潜水/游泳状态）
        return player.isInWater() || player.isSwimming();
    }

    // ================================================================
    // 鼠标滚轮监听
    //   · 必须 Shift 按住 + canAdjust 通过才处理
    //   · ScrollDelta 正值 = 加速，负值 = 减速
    //   · 满足条件时 setCanceled(true) 阻止物品栏切换
    // ================================================================
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        if (!player.isShiftKeyDown()) return;
        if (!canAdjust(player)) return;

        double scrollDelta = event.getScrollDelta();
        if (Math.abs(scrollDelta) < 0.001) return;

        // 计算变化量（sensitivity * 滚轮单位）
        double delta = scrollDelta * DeepSeaRippleConfig.getScrollSensitivity();

        // 立即更新客户端 CACHE → 本地 PlayerTick 零延迟生效
        double current = DeepSeaRippleHandler.getCachedMultiplier(player.getUUID());
        double max = DeepSeaRippleConfig.getMaxSwimMultiplier();
        double newVal = Math.max(1.0, Math.min(max, current + delta));
        DeepSeaRippleHandler.MULTIPLIER_CACHE.put(player.getUUID(), newVal);

        // 发送 C2S 同步
        NetworkHandler.CHANNEL.sendToServer(new DeepSeaRippleScrollPacket(delta));

        // 取消滚轮默认行为（防止切换选中物品）
        event.setCanceled(true);
    }
}
