package com.github.emberstar1201.enchantmentex.world;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 营地定位指令，支持两种格式：
 * <ul>
 *   <li>{@code /ee locate camp} — 定位最近的丧尸娘营地</li>
 *   <li>{@code /ee camp locate} — 兼容旧格式</li>
 * </ul>
 *
 * <p>只返回营地生成器已成功放置并写入存档的营地坐标。</p>
 */
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CampLocateCommand {

    private CampLocateCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        // 新格式：/ee locate camp
        dispatcher.register(
                Commands.literal("ee")
                        .then(Commands.literal("locate")
                                .then(Commands.literal("camp")
                                        .executes(CampLocateCommand::locate)))
        );
        // 兼容旧格式：/ee camp locate
        dispatcher.register(
                Commands.literal("ee")
                        .then(Commands.literal("camp")
                                .then(Commands.literal("locate")
                                        .executes(CampLocateCommand::locate)))
        );
    }

    private static int locate(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        if (level.dimension() != ServerLevel.OVERWORLD) {
            source.sendFailure(Component.literal("丧尸娘营地只生成在主世界"));
            return 0;
        }

        BlockPos from = BlockPos.containing(source.getPosition());
        BlockPos nearest = ZombieGirlCampSavedData.get(level).findNearest(from);
        if (nearest == null) {
            source.sendFailure(Component.literal("尚未发现已生成的丧尸娘营地"));
            return 0;
        }

        int distance = (int) Math.sqrt(nearest.distSqr(from));
        // 坐标带点击传送，格式与原版 /locate 一致
        Component coords = ComponentUtils.wrapInSquareBrackets(
                        Component.translatable("chat.coordinates", nearest.getX(), nearest.getY(), nearest.getZ()))
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                "/tp @s " + nearest.getX() + " " + nearest.getY() + " " + nearest.getZ()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("chat.coordinates.tooltip"))));
        source.sendSuccess(() -> Component.literal("最近的丧尸娘营地位于 ")
                .append(coords)
                .append("（距离 " + distance + " 格）"), false);
        return 1;
    }

}
