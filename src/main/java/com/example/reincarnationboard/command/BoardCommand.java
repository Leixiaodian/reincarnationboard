package com.example.reincarnationboard.command;

import com.example.reincarnationboard.board.BoardService;
import com.example.reincarnationboard.board.ScoreSource;
import com.example.reincarnationboard.config.BoardConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

/**
 * 管理员命令：重载配置、开关公告栏、改积分（方便调试和运营）。
 */
public final class BoardCommand {

    private BoardCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(build("reincarnationboard"));
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("zhaboard").redirect(root));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("reload").executes(BoardCommand::reload))
                .then(Commands.literal("status").executes(BoardCommand::status))
                .then(Commands.literal("toggle").executes(BoardCommand::toggle))
                .then(Commands.literal("score")
                        .then(Commands.literal("set")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(BoardCommand::setScore))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                .executes(BoardCommand::addScore)))));
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        BoardConfig.load();
        BoardConfig config = BoardConfig.get();
        BoardService.refresh(context.getSource().getServer(), true);
        context.getSource().sendSuccess(() -> Component.literal("§a公告栏配置已重载："
                + (config.enabled ? "§a已启用" : "§7已关闭")
                + "§r，前 " + config.topCount + " 名，刷新间隔 " + config.refreshTicks + " tick"), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        List<String> lines = BoardService.describe(context.getSource().getServer());
        for (String line : lines) {
            context.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return lines.size();
    }

    private static int toggle(CommandContext<CommandSourceStack> context) {
        BoardConfig config = BoardConfig.get();
        config.enabled = !config.enabled;
        BoardConfig.save();
        BoardService.refresh(context.getSource().getServer(), true);
        context.getSource().sendSuccess(() ->
                Component.literal(config.enabled ? "§a公告栏已启用" : "§7公告栏已关闭"), true);
        return 1;
    }

    private static int setScore(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        int value = IntegerArgumentType.getInteger(context, "value");
        return applyScoreChange(context, targets, value, true);
    }

    private static int addScore(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        return applyScoreChange(context, targets, amount, false);
    }

    private static int applyScoreChange(CommandContext<CommandSourceStack> context,
                                        Collection<ServerPlayer> targets, int value, boolean set) {
        if (!ScoreSource.isInstalled()) {
            context.getSource().sendFailure(Component.literal("§c未检测到「转生苹果」模组，无法修改积分"));
            return 0;
        }
        for (ServerPlayer player : targets) {
            if (set) {
                ScoreSource.setScore(player, value);
            } else {
                ScoreSource.addScore(player, value);
            }
        }
        BoardService.refresh(context.getSource().getServer(), true);
        String message = set
                ? "§a已将 " + targets.size() + " 名玩家的积分设为 §e" + value
                : "§a已给 " + targets.size() + " 名玩家增加 §e" + value + " §a分";
        context.getSource().sendSuccess(() -> Component.literal(message), true);
        return targets.size();
    }
}