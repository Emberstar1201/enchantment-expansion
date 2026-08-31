package com.github.emberstar1201.enchantmentex.network;

import com.github.emberstar1201.enchantmentex.DamagePopupClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// ========================================================================
// 【伤害/治疗浮动数字包】（S2C：服务端 → 客户端）
//
// 服务端监听 LivingHurtEvent / LivingHealEvent 后，将受伤/治疗事件
// （浮字起点世界坐标 + 数量 + 类型）广播给附近玩家的客户端。
//
// 客户端收到后调用 DamagePopupClientHandler.addPopup() 生成浮字，
// 在世界渲染阶段于实体头顶显示可视化数字（红=伤害，绿=治疗）。
// ========================================================================
public class DamagePopupPacket {

    // 浮字起点世界坐标（通常为实体头顶上方）
    private final double x;
    private final double y;
    private final double z;
    // 伤害/治疗数量
    private final float amount;
    // true = 治疗（绿字 +），false = 伤害（红字 -）
    private final boolean heal;

    public DamagePopupPacket(double x, double y, double z, float amount, boolean heal) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.amount = amount;
        this.heal = heal;
    }

    // 解码：按编码顺序读取
    public DamagePopupPacket(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.amount = buf.readFloat();
        this.heal = buf.readBoolean();
    }

    // 编码：写入缓冲区
    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeFloat(this.amount);
        buf.writeBoolean(this.heal);
    }

    // 处理：客户端收到后在主线程生成浮字
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        ctx.enqueueWork(() -> {
            // 确保仅在客户端执行
            if (ctx.getDirection().getReceptionSide().isClient()) {
                DamagePopupClientHandler.addPopup(
                        this.x, this.y, this.z, this.amount, this.heal);
            }
        });
        ctx.setPacketHandled(true);
    }
}
