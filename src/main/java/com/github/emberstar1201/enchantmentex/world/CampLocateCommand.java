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
 * <p>使用确定性哈希扫描预测候选位置，并通过生成器的未缓存群系查询过滤非平原位置，
 * 不访问区块存储，不触发区块加载。</p>
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
        BlockPos nearest = locateNearestCamp(level, from);
        if (nearest == null) {
            source.sendFailure(Component.literal("附近 10000 格内未发现丧尸娘营地（需在平原区域）"));
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

    /**
     * 确定性营地搜索：在玩家周围按 CHUNK_SPACING 网格扫描，
     * 用 shouldHaveCamp 判定候选位置，并用未缓存群系采样排除非平原位置。
     * 全程不读取区块，不触发区块加载。
     */
    private static BlockPos locateNearestCamp(ServerLevel level, BlockPos from) {
        long worldSeed = level.getSeed();
        int fromChunkX = from.getX() >> 4;
        int fromChunkZ = from.getZ() >> 4;

        // 将玩家坐标对齐到网格起点
        int baseX = Math.floorDiv(fromChunkX, ZombieGirlCampHandler.CHUNK_SPACING) * ZombieGirlCampHandler.CHUNK_SPACING;
        int baseZ = Math.floorDiv(fromChunkZ, ZombieGirlCampHandler.CHUNK_SPACING) * ZombieGirlCampHandler.CHUNK_SPACING;

        BlockPos nearest = null;
        double bestDist = Double.MAX_VALUE;

        // 搜索半径内的网格点数
        int range = ZombieGirlCampHandler.SEARCH_RADIUS_CHUNKS / ZombieGirlCampHandler.CHUNK_SPACING;

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                int chunkX = baseX + dx * ZombieGirlCampHandler.CHUNK_SPACING;
                int chunkZ = baseZ + dz * ZombieGirlCampHandler.CHUNK_SPACING;

                // 确定性哈希判定：不加载区块，纯数学计算
                if (!ZombieGirlCampHandler.shouldHaveCamp(worldSeed, chunkX, chunkZ)) {
                    continue;
                }

                int blockX = (chunkX << 4) + 8;
                int blockZ = (chunkZ << 4) + 8;
                if (!ZombieGirlCampHandler.isPlainsBiomeWithoutLoading(
                        level, new BlockPos(blockX, 64, blockZ))) {
                    continue;
                }

                // 计算距离（XZ 平面）
                double dist = Math.pow(blockX - from.getX(), 2) + Math.pow(blockZ - from.getZ(), 2);
                if (dist < bestDist) {
                    bestDist = dist;
                    nearest = new BlockPos(blockX, 64, blockZ);
                }
            }
        }
        return nearest;
    }
}
