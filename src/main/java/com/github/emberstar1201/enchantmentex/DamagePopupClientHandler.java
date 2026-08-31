package com.github.emberstar1201.enchantmentex;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

// ========================================================================
// 【伤害/治疗浮动数字】客户端渲染
//
// 收到服务端广播（DamagePopupPacket）后，在世界渲染阶段
// （RenderLevelStageEvent.AFTER_PARTICLES）于指定世界坐标渲染
// 可视化数字：
//   - 伤害：红色，前缀 "-"（如 -7）
//   - 治疗：绿色，前缀 "+"（如 +4）
//
// 【动画】数字从生成位置向上飘动 RISE_HEIGHT 格，寿命 1 秒，
//         后半段透明度逐渐衰减至消失。
//
// 【渲染细节】
//   - Billboard 面向相机：与原版名牌一致，使用 camera.rotation() 旋转
//   - 全亮度光照（0xF000F0）：黑暗环境中数字依然清晰可见
//   - DisplayMode.NORMAL：尊重方块遮挡（不透墙），避免透视问题
//   - 水平随机偏移：多个数字同时出现时错开，避免完全重叠
//   - 数量上限 128：防止大规模战斗刷屏导致卡顿
//
// 仅客户端注册（Dist.CLIENT），服务端不加载本类。
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, value = Dist.CLIENT)
public class DamagePopupClientHandler {

    // 浮字寿命（毫秒）
    private static final long LIFETIME_MS = 1000L;
    // 上飘总高度（格）
    private static final double RISE_HEIGHT = 0.8;
    // 浮字最大同时存在数量（超量时丢弃最旧的）
    private static final int MAX_POPUPS = 128;
    // 水平随机偏移幅度（格）：错开连续出现的多个数字
    private static final double H_SPREAD = 0.5;
    // 文字缩放系数（与原版名牌一致）
    private static final float TEXT_SCALE = 0.025f;

    private static final Random RANDOM = new Random();

    // 独立渲染缓冲源：endBatch() 只刷新浮字缓冲，
    // 不影响 mc.renderBuffers().bufferSource() 中的世界渲染内容
    private static final MultiBufferSource.BufferSource IMMEDIATE_BUFFER =
            MultiBufferSource.immediate(new BufferBuilder(256));

    // 当前活跃浮字列表（仅客户端主线程访问：包处理与渲染都在主线程）
    private static final List<Popup> POPUPS = new ArrayList<>();

    // 浮字数据：世界坐标 + 数量 + 类型 + 生成时间戳
    private record Popup(double x, double y, double z, float amount, boolean heal, long birthMillis) {
    }

    // ========================================================================
    // 包接收入口：由 DamagePopupPacket 在客户端主线程调用
    // ========================================================================
    public static void addPopup(double x, double y, double z, float amount, boolean heal) {
        POPUPS.add(new Popup(
                x + (RANDOM.nextDouble() - 0.5) * H_SPREAD,
                y,
                z + (RANDOM.nextDouble() - 0.5) * H_SPREAD,
                amount,
                heal,
                Util.getMillis()
        ));

        // 超量保护：丢弃最旧的浮字
        while (POPUPS.size() > MAX_POPUPS) {
            POPUPS.remove(0);
        }
    }

    // ========================================================================
    // 世界渲染阶段：绘制所有活跃浮字
    // ========================================================================
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (POPUPS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = IMMEDIATE_BUFFER;
        Font font = mc.font;
        long now = Util.getMillis();

        Iterator<Popup> iterator = POPUPS.iterator();
        while (iterator.hasNext()) {
            Popup p = iterator.next();

            // 归一化年龄：0 = 刚生成，1 = 已过期
            float age = (now - p.birthMillis) / (float) LIFETIME_MS;
            if (age >= 1.0f) {
                iterator.remove();
                continue;
            }

            // 淡出曲线：前期几乎全不透明，后期快速衰减
            float alpha = 1.0f - age * age;
            int alphaChannel = (int) (alpha * 255.0f) << 24;

            // 伤害 = 红（0xFF5555），治疗 = 绿（0x55FF55）
            int rgb = p.heal ? 0x55FF55 : 0xFF5555;

            // 文本：伤害带 "-"，治疗带 "+"；数量向上取整保证可见
            String text = (p.heal ? "+" : "-") + Mth.ceil(p.amount);

            pose.pushPose();
            // 平移到浮字世界坐标（随年龄上飘）
            pose.translate(
                    (float) (p.x - camPos.x),
                    (float) (p.y + age * RISE_HEIGHT - camPos.y),
                    (float) (p.z - camPos.z)
            );
            // Billboard：面向相机（与原版名牌一致的旋转方式）
            pose.mulPose(event.getCamera().rotation());
            // 缩放：负 x/y 使文字正向可读
            pose.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

            font.drawInBatch(
                    text,
                    -font.width(text) / 2.0f,
                    0.0f,
                    alphaChannel | rgb,
                    false,
                    pose.last().pose(),
                    buffer,
                    Font.DisplayMode.NORMAL,
                    0,
                    0xF000F0
            );
            pose.popPose();
        }

        // 刷新独立缓冲源，确保浮字在本帧渲染（不影响全局渲染管线）
        buffer.endBatch();
    }
}
