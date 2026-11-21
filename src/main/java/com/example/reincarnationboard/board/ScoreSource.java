package com.example.reincarnationboard.board;

import com.example.reincarnationapple.api.ScoreApi;
import com.example.reincarnationapple.api.ScoreApiProvider;
import com.example.reincarnationboard.ReincarnationBoard;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 「转生苹果」积分的读取入口。
 * <p>
 * 对单个在线玩家读写积分，走官方接口 {@link ScoreApiProvider}。
 * 排行榜需要**所有玩家**（包括离线的）的积分，而官方接口只接受在线 {@link ServerPlayer}，
 * 所以这里在能安全拿到内部存档时直接读它的 SavedData；拿不到就退化成「只统计在线玩家」，
 * 不会因此报错或崩溃。
 */
public final class ScoreSource {

    private static final int UNKNOWN = 0;
    private static final int AVAILABLE = 1;
    private static final int UNAVAILABLE = 2;

    private static int reflectionState = UNKNOWN;
    private static Method scoreDataGet;
    private static Field scoresField;

    private ScoreSource() {
    }

    /** 转生苹果是否已加载且接口可用。 */
    public static boolean isInstalled() {
        if (!FabricLoader.getInstance().isModLoaded("reincarnationapple")) {
            return false;
        }
        try {
            api();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static int getScore(ServerPlayer player) {
        return api().getScore(player);
    }

    public static void setScore(ServerPlayer player, int value) {
        api().setScore(player, value);
    }

    public static void addScore(ServerPlayer player, int amount) {
        api().addScore(player, amount);
    }

    private static ScoreApi api() {
        return ScoreApiProvider.get();
    }

    /**
     * 收集参与排行的玩家积分。优先读转生苹果的全局存档（含离线玩家），失败时只统计在线玩家。
     */
    public static Map<UUID, Integer> collect(MinecraftServer server) {
        if (ensureGlobalAccess()) {
            try {
                Object data = scoreDataGet.invoke(null, server);
                Object raw = scoresField.get(data);
                if (raw instanceof Map<?, ?> map) {
                    Map<UUID, Integer> scores = new HashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (entry.getKey() instanceof String key && entry.getValue() instanceof Number value) {
                            try {
                                scores.put(UUID.fromString(key), value.intValue());
                            } catch (IllegalArgumentException ignored) {
                                // 存档里出现了不是 UUID 的键，跳过。
                            }
                        }
                    }
                    return scores;
                }
            } catch (Throwable t) {
                disableGlobalAccess("读取转生苹果积分存档失败：" + t);
            }
        }
        return onlineScores(server);
    }

    private static Map<UUID, Integer> onlineScores(MinecraftServer server) {
        Map<UUID, Integer> scores = new HashMap<>();
        try {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                scores.put(player.getUUID(), getScore(player));
            }
        } catch (Throwable t) {
            ReincarnationBoard.LOGGER.warn("读取在线玩家积分失败：{}", t.toString());
        }
        return scores;
    }

    private static synchronized boolean ensureGlobalAccess() {
        if (reflectionState != UNKNOWN) {
            return reflectionState == AVAILABLE;
        }
        try {
            Class<?> scoreDataClass = Class.forName("com.example.reincarnationapple.data.ScoreData");
            Method get = scoreDataClass.getMethod("get", MinecraftServer.class);
            Field field = scoreDataClass.getDeclaredField("scores");
            field.setAccessible(true);
            scoreDataGet = get;
            scoresField = field;
            reflectionState = AVAILABLE;
        } catch (Throwable t) {
            disableGlobalAccess("无法读取转生苹果全局积分（" + t + "），排行榜将只统计在线玩家");
        }
        return reflectionState == AVAILABLE;
    }

    private static synchronized void disableGlobalAccess(String message) {
        if (reflectionState == AVAILABLE) {
            ReincarnationBoard.LOGGER.warn(message);
        }
        reflectionState = UNAVAILABLE;
        scoreDataGet = null;
        scoresField = null;
    }
}
