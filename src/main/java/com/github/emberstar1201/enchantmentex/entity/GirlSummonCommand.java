package com.github.emberstar1201.enchantmentex.entity;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.function.Function;

// ========================================================================
// 【友好娘调试命令】/ee girl summon ...
//
// 快速在命令执行者 / 指定位置召唤丧尸娘 / 溺尸娘 / 幸存者少女，
// 可选自动驯服并归属给执行者或指定玩家。方便治愈、睡觉等功能测试。
//
// 命令树（与 /ee mobbuff 共用 /ee 根节点，互不冲突）：
//   /ee girl summon zombie_girl [tamed] [owner]
//   /ee girl summon drowned_girl [tamed] [owner]
//   /ee girl summon survivor_girl [tamed] [owner]
//
// 权限等级 2（OP / 服主），单人存档房主天然满足。
// ========================================================================
public final class GirlSummonCommand {

    /** 原版权限等级 2 = OP / 服主 */
    private static final int PERMISSION_LEVEL = 2;

    private GirlSummonCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("ee")
                        // 与 MobBuffCommandHandler 共用根节点，互不冲突
                        .then(Commands.literal("girl")
                                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                                .then(Commands.literal("summon")
                                        .then(Commands.literal("zombie_girl")
                                                .executes(ctx -> summonGirl(ctx,
                                                        ModEntities.ZOMBIE_GIRL.get(),
                                                        false, null))
                                                .then(Commands.literal("tamed")
                                                        .executes(ctx -> summonGirl(ctx,
                                                                ModEntities.ZOMBIE_GIRL.get(),
                                                                true, null))
                                                        .then(Commands.argument("owner", EntityArgument.player())
                                                                .executes(ctx -> summonGirl(ctx,
                                                                        ModEntities.ZOMBIE_GIRL.get(),
                                                                        true,
                                                                        EntityArgument.getPlayer(ctx, "owner"))))))
                                        .then(Commands.literal("drowned_girl")
                                                .executes(ctx -> summonGirl(ctx,
                                                        ModEntities.DROWNED_GIRL.get(),
                                                        false, null))
                                                .then(Commands.literal("tamed")
                                                        .executes(ctx -> summonGirl(ctx,
                                                                ModEntities.DROWNED_GIRL.get(),
                                                                true, null))
                                                        .then(Commands.argument("owner", EntityArgument.player())
                                                                .executes(ctx -> summonGirl(ctx,
                                                                        ModEntities.DROWNED_GIRL.get(),
                                                                        true,
                                                                        EntityArgument.getPlayer(ctx, "owner"))))))
                                        .then(Commands.literal("survivor_girl")
                                                .executes(ctx -> summonGirl(ctx,
                                                        ModEntities.SURVIVOR_GIRL.get(),
                                                        false, null))
                                                .then(Commands.literal("tamed")
                                                        .executes(ctx -> summonGirl(ctx,
                                                                ModEntities.SURVIVOR_GIRL.get(),
                                                                true, null))
                                                        .then(Commands.argument("owner", EntityArgument.player())
                                                                .executes(ctx -> summonGirl(ctx,
                                                                        ModEntities.SURVIVOR_GIRL.get(),
                                                                        true,
                                                                        EntityArgument.getPlayer(ctx, "owner"))))))
                                )
                        )
        );
    }

    /**
     * 实际执行召唤：
     *   - 从命令执行者位置（如果是玩家）或命令源位置（方块命令）生成；
     *   - tamed=true 时立即驯服并归属 owner（默认命令执行者）；
     *   - 设置 PersistenceRequired 防止和平模式 / 远离自动清除。
     */
    private static int summonGirl(CommandContext<CommandSourceStack> ctx,
                                   EntityType<? extends Mob> type,
                                   boolean tamed,
                                   Player explicitOwner) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();

        Mob entity = type.create(level);
        if (entity == null) {
            source.sendFailure(Component.literal("实体创建失败"));
            return 0;
        }

        // 召唤位置：命令执行者位置（玩家）或命令源方块位置
        double x, y, z;
        if (source.getEntity() instanceof ServerPlayer player) {
            x = player.getX();
            y = player.getY();
            z = player.getZ();
        } else {
            x = source.getPosition().x;
            y = source.getPosition().y;
            z = source.getPosition().z;
        }
        entity.moveTo(x, y, z, source.getRotation().y, 0.0F);
        entity.setPersistenceRequired();

        // 驯服 + 归属
        if (tamed) {
            Player owner = explicitOwner != null
                    ? explicitOwner
                    : (source.getEntity() instanceof Player sp ? sp : null);
            if (owner != null) {
                // ZombieGirlEntity / SurvivorGirlEntity / DrownedGirlEntity 的 bondTo 方法
                // 都来自 FriendlyGirlInventory 接口实现
                if (entity instanceof ZombieGirlEntity zg) {
                    zg.bondTo(owner);
                } else if (entity instanceof SurvivorGirlEntity sg) {
                    sg.bondTo(owner);
                } else if (entity instanceof DrownedGirlEntity dg) {
                    dg.bondTo(owner);
                }
            }
        }

        level.addFreshEntity(entity);

        // 反馈
        String typeName = type.toShortString();
        String msg;
        if (tamed && explicitOwner != null) {
            msg = "§a召唤了一只已驯服的 " + typeName + "，归属给 " + explicitOwner.getGameProfile().getName();
        } else if (tamed) {
            msg = "§a召唤了一只已驯服的 " + typeName + "，归属给你";
        } else {
            msg = "§a召唤了一只野生 " + typeName;
        }
        source.sendSuccess(() -> Component.literal(msg), false);

        return 1;
    }
}
