package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.network.NetworkHandler;
import com.github.emberstar1201.enchantmentex.network.SandevistanActivatePacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// ========================================================================
// 【斯安维斯坦】客户端处理器
//
// 职责：
//   1. 注册激活键（默认 X 键，可在 选项→按键设置 中自定义）
//   2. 按键按下 → 发送 C2S 激活请求包（服务端权威校验）
//   3. 接收 S2C 状态包 → 维护本地时缓状态表
//   4. 激活者本人：FOV 拉伸 + 青蓝色拖尾粒子
//   （屏幕色调与文字提示在 SandevistanOverlay 中绘制）
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SandevistanClientHandler {

    // 激活键：默认 X 键
    public static final KeyMapping SANDE_KEY = new KeyMapping(
            "key." + EnchantmentExpansion.MODID + ".sandevistan",
            GLFW.GLFW_KEY_X,
            "key.categories." + EnchantmentExpansion.MODID
    );

    // 青蓝色主题粒子
    private static final DustParticleOptions CYAN_DUST =
            new DustParticleOptions(new Vector3f(0.0f, 0.75f, 1.0f), 1.3f);

    // ========================================================================
    // 本地时缓状态表：激活者 UUID → 状态（S2C 包更新）
    // ========================================================================
    public static class ClientState {
        public final double scale;
        public final long endTime;

        ClientState(double scale, long endTime) {
            this.scale = scale;
            this.endTime = endTime;
        }
    }

    public static final Map<UUID, ClientState> ACTIVE = new ConcurrentHashMap<>();

    /** S2C 状态包入口（由 SandevistanStatePacket 调用，仅客户端） */
    public static void onStateReceived(UUID activatorId, boolean active,
                                       double scale, long endTime) {
        if (active) {
            ACTIVE.put(activatorId, new ClientState(scale, endTime));
        } else {
            ACTIVE.remove(activatorId);
        }
    }

    /** 本地玩家是否正在斯安维斯坦时缓中（本人是激活者） */
    public static boolean isLocalActivating() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && ACTIVE.containsKey(mc.player.getUUID());
    }

    // ========================================================================
    // 注册按键映射（Mod 事件总线）
    // ========================================================================
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SANDE_KEY);
    }

    // ========================================================================
    // 客户端设置：注册 Forge 总线上的 tick / FOV 监听
    // ========================================================================
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MinecraftForge.EVENT_BUS.addListener(SandevistanClientHandler::onClientTick);
            MinecraftForge.EVENT_BUS.addListener(SandevistanClientHandler::onComputeFov);
        });
    }

    // ========================================================================
    // 客户端 tick：检测按键 + 激活者拖尾粒子
    // ========================================================================
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            // 离开世界时清空状态，避免残留
            ACTIVE.clear();
            return;
        }

        // 按键：一次按下只触发一次
        if (SANDE_KEY.consumeClick()) {
            NetworkHandler.CHANNEL.sendToServer(new SandevistanActivatePacket());
        }

        // 激活者拖尾粒子（每 2 tick，青色光尘从身后散落）
        if (isLocalActivating() && mc.level.getGameTime() % 2 == 0) {
            Player player = mc.player;
            Vec3 pos = player.position();
            Vec3 look = player.getLookAngle();
            double px = pos.x - look.x * 0.8;
            double py = pos.y + 0.9;
            double pz = pos.z - look.z * 0.8;
            for (int i = 0; i < 3; i++) {
                mc.level.addParticle(CYAN_DUST,
                        px + (mc.level.random.nextDouble() - 0.5) * 0.6,
                        py + (mc.level.random.nextDouble() - 0.5) * 0.8,
                        pz + (mc.level.random.nextDouble() - 0.5) * 0.6,
                        0.0, 0.0, 0.0);
            }
        }
    }

    // ========================================================================
    // FOV 拉伸：激活者视野扩张，强化"自己变快"的速度感
    // ========================================================================
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (isLocalActivating()) {
            event.setFOV(event.getFOV() * 1.22);
        }
    }
}
