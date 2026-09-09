package com.github.emberstar1201.enchantmentex;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

/**
 * 帕秋莉手册软前置提醒处理器。
 *
 * 用途：当玩家进入世界（单人/局域网/服务器存档）时，若检测到未安装帕秋莉手册
 * （Patchouli，即本模组游戏内图鉴的软前置），就在聊天框里发一条本地化系统消息，
 * 提示「搭配帕秋莉手册体验更佳，装上后可获得游戏内说明书」。
 *
 * 设计要点：
 *  - 使用 PlayerLoggedInEvent（服务端事件）而不是客户端事件，确保各类模式都能触发。
 *  - 用 Component.translatable() 发送本地化组件，客户端按玩家所选语言渲染对应文案，
 *    因此只需在四语言 lang 文件（zh_cn/zh_hk/zh_tw/en_us）维护同一个翻译键即可。
 *  - 仅当 Patchouli 未装载时才提醒：已装 Patchouli 的玩家开局已获赠《终界之书》，
 *    无需再提醒安装前置；未装的玩家正是最需要这条提示的人。
 *  - 不做持久化抑制：未装 Patchouli 的玩家每次进世界都会看到提示，持续引导安装，
 *    直到其装上该前置为止。
 */
public final class PatchouliReminderHandler {
    /** Patchouli 模组 id（与本模组其他 Handler 保持一致） */
    private static final String PATCHOULI_MOD_ID = "patchouli";

    /** 四语言共用的翻译键（zh_cn/zh_hk/zh_tw/en_us 均维护该键） */
    private static final String REMINDER_KEY = "message.enchantment_expansion.patchouli_reminder";

    private PatchouliReminderHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return; // 非服务端玩家（如客户端逻辑端）不处理
        }
        // 已安装 Patchouli：无需提醒（玩家已能拿到图鉴/手册）
        if (ModList.get().isLoaded(PATCHOULI_MOD_ID)) {
            return;
        }
        // 向该玩家发送一条本地化系统消息，提示搭配手册体验更佳
        player.sendSystemMessage(Component.translatable(REMINDER_KEY));
    }
}
