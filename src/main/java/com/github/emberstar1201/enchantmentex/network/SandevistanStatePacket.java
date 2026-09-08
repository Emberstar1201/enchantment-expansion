package com.github.emberstar1201.enchantmentex.network;

import com.github.emberstar1201.enchantmentex.enchantment.SandevistanClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

// ========================================================================
// 【斯安维斯坦状态同步包】（S2C：服务端 → 客户端）
//
// 激活/结束时由服务端广播给时缓范围附近的所有玩家：
//   - active = true ：某玩家激活了时缓（客户端驱动 FOV/色调/粒子特效）
//   - active = false：该玩家的时缓结束（客户端移除特效）
//
// 字段：
//   activatorId  激活者 UUID（客户端据此判断"自己是激活者"还是"被时缓的旁人"）
//   active       是否处于激活状态
//   timeScale    时缓倍率（用于特效强度；结束包为 0）
//   endGameTime  服务端维度游戏时间 tick（客户端据此估算剩余秒数）
// ========================================================================
public class SandevistanStatePacket {

    private final UUID activatorId;
    private final boolean active;
    private final double timeScale;
    private final long endGameTime;

    public SandevistanStatePacket(UUID activatorId, boolean active, double timeScale, long endGameTime) {
        this.activatorId = activatorId;
        this.active = active;
        this.timeScale = timeScale;
        this.endGameTime = endGameTime;
    }

    // 解码
    public SandevistanStatePacket(FriendlyByteBuf buf) {
        this.activatorId = buf.readUUID();
        this.active = buf.readBoolean();
        this.timeScale = buf.readDouble();
        this.endGameTime = buf.readLong();
    }

    // 编码
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.activatorId);
        buf.writeBoolean(this.active);
        buf.writeDouble(this.timeScale);
        buf.writeLong(this.endGameTime);
    }

    // 处理：客户端更新本地时缓状态表
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        ctx.enqueueWork(() -> {
            if (ctx.getDirection().getReceptionSide().isClient()) {
                SandevistanClientHandler.onStateReceived(activatorId, active, timeScale, endGameTime);
            }
        });
        ctx.setPacketHandled(true);
    }
}
