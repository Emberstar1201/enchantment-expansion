package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.network.AccumulateChargePacket;
import com.github.emberstar1201.enchantmentex.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

// ========================================================================
// 【蓄积】客户端处理器
//
// 功能：每 tick 检测「使用键」（默认鼠标右键）的按住状态，
//       状态发生变化时发送 C2S 数据包到服务端。
//
// 为什么需要这个：
//   服务端无法直接读取客户端鼠标输入，蓄力必须由客户端
//   告知"右键是否按住"。玩家按住右键 → 蓄力计时开始；
//   松开右键 → 蓄力暂停。
//
// 使用 options.keyUse（原版"使用物品/右键"键），因此玩家
// 在设置中改右键键位后本附魔自动跟随，无需额外按键绑定。
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class AccumulateClientHandler {

    // 上一帧右键状态（用于检测状态变化，避免每帧发包）
    private static boolean lastRightDown = false;

    // ========================================================================
    // 客户端设置完成后 → 将 ClientTick 注册到 Forge 事件总线
    // ========================================================================
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                MinecraftForge.EVENT_BUS.addListener(AccumulateClientHandler::onClientTick)
        );
    }

    // ========================================================================
    // 每 tick 检测右键按住状态，状态变化时发送 C2S 数据包
    // keyUse 默认绑定鼠标右键（玩家可在控制设置中自定义）
    // ========================================================================
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean rightDown = mc.options.keyUse.isDown();
        if (rightDown != lastRightDown) {
            lastRightDown = rightDown;
            // 按住 → true（开始/继续蓄力）；松开 → false（暂停蓄力）
            NetworkHandler.CHANNEL.sendToServer(new AccumulateChargePacket(rightDown));
        }
    }
}