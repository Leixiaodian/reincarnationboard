package com.example.reincarnationboard;

import com.example.reincarnationboard.board.BoardService;
import com.example.reincarnationboard.command.BoardCommand;
import com.example.reincarnationboard.config.BoardConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 服务端入口。
 * <p>
 * 公告栏用原版记分板侧栏实现：服务端维护一个共享目标 {@code rb_board}，
 * 另外给每个在线玩家单独下发一行「我的积分」（客户端本地的额外分数行，不写进服务端记分板）。
 * 客户端不需要装模组，原版客户端即可显示。
 */
public class ReincarnationBoard implements ModInitializer {

    public static final String MOD_ID = "reincarnationboard";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        BoardConfig.load();

        ServerTickEvents.END_SERVER_TICK.register(BoardService::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> BoardService.refresh(server, true));
        ServerLifecycleEvents.SERVER_STOPPING.register(BoardService::onServerStopping);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                BoardService.onPlayerJoin(server, handler.player));

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> BoardCommand.register(dispatcher));

        LOGGER.info("{} 已加载（纯服务端），配置：{}", MOD_ID, BoardConfig.path());
    }
}
