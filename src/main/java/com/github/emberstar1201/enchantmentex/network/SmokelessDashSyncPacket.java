package com.github.emberstar1201.enchantmentex.network;

import com.github.emberstar1201.enchantmentex.enchantment.SmokelessDashHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// ========================================================================
// 【无烟冲击速度倍率同步包】（S2C：服务端 → 客户端）
//
// 服务端每 10 tick 发送一次，将当前速度倍率同步到客户端，
// 供 HUD 覆盖层显示当前速度数值。
//
// 客户端收到后更新 SmokelessDashHandler.SPEED_CACHE 缓存。
// ========================================================================
public class SmokelessDashSyncPacket {

    private final double multiplier; // 当前速度倍率

    public SmokelessDashSyncPacket(double multiplier) {
        this.multiplier = multiplier;
    }

    // 解码：从 buf 读取 multiplier
    public SmokelessDashSyncPacket(FriendlyByteBuf buf) {
        this.multiplier = buf.readDouble();
    }

    // 编码：将 multiplier 写入 buf
    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(this.multiplier);
    }

    // 处理：客户端收到后更新本地缓存
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        ctx.enqueueWork(() -> {
            // 确保仅在客户端执行
            if (ctx.getDirection().getReceptionSide().isClient()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    // ★ 更新 SPEED_CACHE（供 HUD 覆盖层读取）
                    SmokelessDashHandler.SPEED_CACHE.put(
                            mc.player.getUUID(), this.multiplier);
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}