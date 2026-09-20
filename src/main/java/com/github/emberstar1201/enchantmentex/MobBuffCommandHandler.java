package com.github.emberstar1201.enchantmentex;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

// ========================================================================
// 【原版怪物强化】游戏内配置命令
//
// 需求：配置内容可以在游戏里开关，且仅 OP / 服主可用（不需要开世界「允许作弊」）。
//
// 权限说明：
//   hasPermission(2) 对应原版权限等级 2 —— 服务器 OP 与单人存档房主天然拥有，
//   普通玩家即使开了作弊也不满足；因此不会出现「谁都能改服务器配置」的情况。
//
// 命令树：
//   /ee mobbuff                       查看帮助
//   /ee mobbuff list                  列出全部配置项与当前值
//   /ee mobbuff get <路径>            查询单项（含默认值与范围）
//   /ee mobbuff set <路径> <值>       修改并写入配置文件
//   /ee mobbuff toggle <路径>         翻转布尔开关
//
// 实现说明：
//   - 该类不带 @Mod.EventBusSubscriber，由主类 EnchantmentExpansion 通过
//     MinecraftForge.EVENT_BUS.register(...) 显式注册（与 MobBuffHandler 一致）。
//   - 命令路径与 config/enchantment_expansion-mob_buff.toml 中的键完全一致，
//     例如 /ee mobbuff set zombie.maxHealth 35、/ee mobbuff toggle mobBuff.enabled。
//   - 改动会立即刷新静态缓存并落盘；已经生成、且血量/装备已确定的怪物不会回溯变更。
// ========================================================================
public final class MobBuffCommandHandler {

    /** 原版权限等级 2 = OP / 服主，无需开启世界作弊 */
    private static final int PERMISSION_LEVEL = 2;

    private static final String ROOT = "ee";
    private static final String SUB = "mobbuff";

    private static final String PATH_ARG = "path";
    private static final String VALUE_ARG = "value";

    private MobBuffCommandHandler() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal(ROOT)
                        // 根节点不加权限限制：Brigadier 允许不同模组共用同一根字面量，
                        // 权限只挂在 mobbuff 子节点上，避免与其它模组的 /ee 冲突。
                        .then(Commands.literal(SUB)
                                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                                .executes(MobBuffCommandHandler::showHelp)
                                .then(Commands.literal("list")
                                        .executes(MobBuffCommandHandler::listAll))
                                .then(Commands.literal("get")
                                        .then(Commands.argument(PATH_ARG, StringArgumentType.word())
                                                .suggests(MobBuffCommandHandler::suggestPaths)
                                                .executes(MobBuffCommandHandler::getOne)))
                                .then(Commands.literal("set")
                                        .then(Commands.argument(PATH_ARG, StringArgumentType.word())
                                                .suggests(MobBuffCommandHandler::suggestPaths)
                                                .then(Commands.argument(VALUE_ARG, StringArgumentType.word())
                                                        .executes(MobBuffCommandHandler::setOne))))
                                .then(Commands.literal("toggle")
                                        .then(Commands.argument(PATH_ARG, StringArgumentType.word())
                                                .suggests(MobBuffCommandHandler::suggestBooleanPaths)
                                                .executes(MobBuffCommandHandler::toggleOne))))
        );
    }

    // ====================================================================
    // 命令执行体
    // ====================================================================

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(
                "§6[怪物强化] 游戏内配置命令：\n"
                        + " §f/ee mobbuff list §7— 查看全部配置项与当前值\n"
                        + " §f/ee mobbuff get <路径> §7— 查询单项（默认值 / 范围）\n"
                        + " §f/ee mobbuff set <路径> <值> §7— 修改并写入配置文件\n"
                        + " §f/ee mobbuff toggle <路径> §7— 翻转布尔开关\n"
                        + "§7示例：§f/ee mobbuff set zombie.maxHealth 35"),
                false);
        return 1;
    }

    private static int listAll(CommandContext<CommandSourceStack> context) {
        Collection<MobBuffConfig.Entry> entries = MobBuffConfig.entries();
        StringBuilder text = new StringBuilder("§6[怪物强化] 共 " + entries.size() + " 项配置：");
        for (MobBuffConfig.Entry entry : entries) {
            text.append("\n §7").append(entry.path())
                    .append(" §f= §a").append(entry.currentValue());
        }
        context.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    private static int getOne(CommandContext<CommandSourceStack> context) {
        MobBuffConfig.Entry entry = resolve(context, StringArgumentType.getString(context, PATH_ARG));
        if (entry == null) {
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "§6" + entry.path() + " §f= §a" + entry.currentValue()
                        + "\n§7默认值：§f" + entry.defaultValue()
                        + " §7范围：§f" + entry.rangeText()
                        + " §7类型：§f" + entry.type().name().toLowerCase(Locale.ROOT)),
                false);
        return 1;
    }

    private static int setOne(CommandContext<CommandSourceStack> context) {
        MobBuffConfig.Entry entry = resolve(context, StringArgumentType.getString(context, PATH_ARG));
        if (entry == null) {
            return 0;
        }
        String rawValue = StringArgumentType.getString(context, VALUE_ARG);
        try {
            entry.setFromString(rawValue);
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal(
                    "§c设置失败：" + entry.path() + " " + e.getMessage()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "§a已设置 §6" + entry.path() + " §a= §f" + entry.currentValue()
                        + " §7（已写入配置文件）"), true);
        return 1;
    }

    private static int toggleOne(CommandContext<CommandSourceStack> context) {
        MobBuffConfig.Entry entry = resolve(context, StringArgumentType.getString(context, PATH_ARG));
        if (entry == null) {
            return 0;
        }
        if (entry.type() != MobBuffConfig.ValueType.BOOLEAN) {
            context.getSource().sendFailure(Component.literal(
                    "§c" + entry.path() + " 不是开关项（当前值 " + entry.currentValue()
                            + "），请用 §f/ee mobbuff set " + entry.path() + " <值>"));
            return 0;
        }
        boolean enabled = !Boolean.parseBoolean(entry.currentValue());
        entry.setFromString(Boolean.toString(enabled));
        context.getSource().sendSuccess(() -> Component.literal(
                "§a已" + (enabled ? "开启" : "关闭") + " §6" + entry.path()
                        + " §7（已写入配置文件）"), true);
        return 1;
    }

    // ====================================================================
    // 内部工具
    // ====================================================================

    /** 解析路径参数并查找配置项；找不到时回一条中文提示并返回 null */
    private static MobBuffConfig.Entry resolve(CommandContext<CommandSourceStack> context, String path) {
        MobBuffConfig.Entry entry = MobBuffConfig.entry(path);
        if (entry == null) {
            context.getSource().sendFailure(Component.literal(
                    "§c未找到配置项 §f" + path + "§c，可用 §f/ee mobbuff list §c查看全部路径"));
        }
        return entry;
    }

    /** 补全全部配置路径 */
    private static CompletableFuture<Suggestions> suggestPaths(CommandContext<CommandSourceStack> context,
                                                               SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (MobBuffConfig.Entry entry : MobBuffConfig.entries()) {
            if (entry.path().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(entry.path());
            }
        }
        return builder.buildFuture();
    }

    /** 补全布尔开关路径（toggle 只接受开关项） */
    private static CompletableFuture<Suggestions> suggestBooleanPaths(CommandContext<CommandSourceStack> context,
                                                                      SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (MobBuffConfig.Entry entry : MobBuffConfig.entries()) {
            if (entry.type() == MobBuffConfig.ValueType.BOOLEAN
                    && entry.path().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(entry.path());
            }
        }
        return builder.buildFuture();
    }
}
