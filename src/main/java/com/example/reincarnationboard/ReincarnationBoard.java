package com.example.reincarnationboard;

import com.example.reincarnationboard.board.BoardService;
import com.example.reincarnationboard.command.BoardCommand;
import com.example.reincarnationboard.config.BoardConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 服务端入口。
 * <p>
 * 公告栏不是自定义 HUD，而是驱动原版记分板侧栏：先在记分板上建一个目标，
 * 再把每一行写成一个「假玩家」的分数行，并把它设为 sidebar 显示。
 * 这样原版客户端就能看到，客户端不用装任何模组。
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

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> BoardCommand.register(dispatcher));

        LOGGER.info("{} 已加载（纯服务端），配置：{}", MOD_ID, BoardConfig.path());
    }
}
