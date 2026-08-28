package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

// ========================================================================
// 【连锁挖掘】按键绑定配置类
//
// 注册「连锁挖掘」触发键（默认 ~ 反引号键），玩家可在
// Controls（控制）→ 附魔拓展 中自定义按键。
//
// 该按键用于“按住”模式：服务端在 BreakEvent 中检测此键是否被按住，
// 按下并挖掘时才会触发连锁破坏。
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ChainBreakerKeybindings {

    // 连锁挖掘触发键：默认 ~（反引号/波浪号键）
    public static final KeyMapping CHAIN_BREAKER_KEY = new KeyMapping(
            "key." + EnchantmentExpansion.MODID + ".chain_breaker",       // 国际化 key（语言文件登记）
            GLFW.GLFW_KEY_GRAVE_ACCENT,                                    // 默认 ~ 键
            "key.categories." + EnchantmentExpansion.MODID                // 按键分类（附魔拓展）
    );

    // ========================================================================
    // 注册按键映射（Mod 事件总线，客户端专用）
    // ========================================================================
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CHAIN_BREAKER_KEY);
    }
}