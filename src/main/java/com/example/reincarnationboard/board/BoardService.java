package com.example.reincarnationboard.board;

import com.example.reincarnationboard.ReincarnationBoard;
import com.example.reincarnationboard.config.BoardConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 用原版记分板侧栏（sidebar）来显示公告栏。
 * <p>
 * 做法：
 * <ul>
 *   <li>建一个记分板目标 {@value #OBJECTIVE_NAME}，目标的显示名就是公告栏标题；</li>
 *   <li>每一行公告 / 名次用一个「假玩家」分数行表示，行的显示文字用 Score 的 display 覆盖、</li>
 *   <li>顺序靠分数值控制：公告用极大值、玩家名次用真实积分、页脚用极小值；</li>
 *   <li>文字行把分数格式设为 blank（不显示数字），名次行保留原版数字，让分数右对齐显示。</li>
 * </ul>
 * 全部是原版数据，原版客户端即可显示。
 */
public final class BoardService {

    /** 我们创建的记分板目标名（最多 16 字符，所以用短名）。 */
    public static final String OBJECTIVE_NAME = "rb_board";

    /** 原版侧栏最多显示 15 行。 */
    private static final int MAX_ROWS = 15;
    private static final String HOLDER_PREFIX = "rb_";

    private static int ticks;
    private static String lastSignature = "";
    private static boolean active;
    private static Objective previousSidebar;
    private static boolean truncationLogged;
    private static boolean failureLogged;

    private BoardService() {
    }

    public static void tick(MinecraftServer server) {
        BoardConfig config = BoardConfig.get();
        int interval = Math.max(1, config.refreshTicks);
        if (++ticks < interval) {
            return;
        }
        ticks = 0;
        refresh(server, false);
    }

    /** 重新计算并写入记分板；{@code force} 为 true 时忽略「内容没变」直接重写。 */
    public static void refresh(MinecraftServer server, boolean force) {
        BoardConfig config = BoardConfig.get();
        if (!config.enabled) {
            hide(server);
            return;
        }
        try {
            Snapshot snapshot = Snapshot.build(server, config);
            List<Line> lines = buildLines(snapshot, config, server.getPlayerList().getPlayerCount());
            Objective objective = ensureActive(server, config);
            if (objective == null) {
                return;
            }
            String signature = signature(lines, config);
            if (!force && signature.equals(lastSignature)) {
                return;
            }
            lastSignature = signature;
            applyLines(server, objective, lines);
        } catch (Throwable t) {
            if (!failureLogged) {
                failureLogged = true;
                ReincarnationBoard.LOGGER.error("更新公告栏失败（后续相同错误不再重复输出）", t);
            }
        }
    }

    /** 给命令用的调试/排查信息：当前公告栏每行实际显示的内容。 */
    public static List<String> describe(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            return List.of("公告栏当前未启用（记分板目标不存在）");
        }
        List<String> out = new ArrayList<>();
        out.add("目标: " + OBJECTIVE_NAME + " / 标题: " + objective.getDisplayName().getString());
        out.add("sidebar 显示: " + (scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) == objective));
        List<net.minecraft.world.scores.PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(objective));
        entries.sort(Comparator.comparingInt(net.minecraft.world.scores.PlayerScoreEntry::value).reversed()
                .thenComparing(net.minecraft.world.scores.PlayerScoreEntry::owner));
        for (net.minecraft.world.scores.PlayerScoreEntry entry : entries) {
            out.add(entry.ownerName().getString() + " | 值=" + entry.value()
                    + (entry.numberFormatOverride() == null ? "" : " | 隐藏数字"));
        }
        return out;
    }

    /** 服务器关闭时把记分板目标删掉，避免卸载模组后留下一个不会更新的死面板。 */
    public static void onServerStopping(MinecraftServer server) {
        try {
            hide(server);
        } catch (Throwable ignored) {
            // 关服阶段不阻塞。
        }
    }

    private static Objective ensureActive(MinecraftServer server, BoardConfig config) {
        Scoreboard scoreboard = server.getScoreboard();
        // 原版不允许空的目标显示名，给一个空格占位。
        Component title = config.title.isEmpty()
                ? Component.literal(" ")
                : LegacyText.parse(Texts.clip(config.title, 128));
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = scoreboard.addObjective(OBJECTIVE_NAME, ObjectiveCriteria.DUMMY, title,
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
            ReincarnationBoard.LOGGER.info("已创建公告栏记分板目标 {}", OBJECTIVE_NAME);
        } else if (!title.equals(objective.getDisplayName())) {
            objective.setDisplayName(title);
        }

        if (!active) {
            Objective current = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (current != null && !OBJECTIVE_NAME.equals(current.getName())) {
                previousSidebar = current;
            }
            active = true;
        }
        if (scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) != objective) {
            scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        }
        return objective;
    }

    private static void applyLines(MinecraftServer server, Objective objective, List<Line> lines) {
        Scoreboard scoreboard = server.getScoreboard();
        for (int i = 0; i < MAX_ROWS; i++) {
            ScoreHolder holder = ScoreHolder.forNameOnly(HOLDER_PREFIX + i);
            if (i < lines.size()) {
                Line line = lines.get(i);
                ScoreAccess access = scoreboard.getOrCreatePlayerScore(holder, objective);
                access.set(line.value());
                access.display(line.display());
                access.numberFormatOverride(line.blankNumber() ? BlankFormat.INSTANCE : null);
            } else if (scoreboard.getPlayerScoreInfo(holder, objective) != null) {
                scoreboard.resetSinglePlayerScore(holder, objective);
            }
        }
    }

    private static void hide(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective != null) {
            if (scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) == objective) {
                Objective restore = previousSidebar;
                if (restore != null && scoreboard.getObjective(restore.getName()) == null) {
                    restore = null;
                }
                if (restore != null && OBJECTIVE_NAME.equals(restore.getName())) {
                    restore = null;
                }
                scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, restore);
            }
            scoreboard.removeObjective(objective);
            ReincarnationBoard.LOGGER.info("已移除公告栏记分板目标 {}", OBJECTIVE_NAME);
        }
        active = false;
        previousSidebar = null;
        lastSignature = "";
    }

    private static List<Line> buildLines(Snapshot snapshot, BoardConfig config, int online) {
        List<RankedPlayer> ranked = snapshot.ranked();
        Map<String, String> values = new HashMap<>();
        values.put("online", String.valueOf(online));
        values.put("count", String.valueOf(ranked.size()));
        int slots = Math.max(Math.max(config.topCount, 3), Math.min(ranked.size(), 10));
        for (int n = 1; n <= slots; n++) {
            if (n <= ranked.size()) {
                RankedPlayer row = ranked.get(n - 1);
                values.put("top" + n, row.name() + " " + row.score());
                values.put("top" + n + "_name", row.name());
                values.put("top" + n + "_score", String.valueOf(row.score()));
            } else {
                values.put("top" + n, config.unrankedText);
                values.put("top" + n + "_name", config.unrankedText);
                values.put("top" + n + "_score", config.unrankedText);
            }
        }

        List<Line> lines = new ArrayList<>(MAX_ROWS);
        for (String raw : config.headerLines) {
            if (lines.size() >= MAX_ROWS) {
                warnTruncation();
                break;
            }
            String text = Texts.fill(raw, values, config.maxLineChars);
            lines.add(new Line(text, Integer.MAX_VALUE - lines.size(), LegacyText.parse(text), true));
        }

        if (snapshot.available()) {
            int shown = Math.min(config.topCount, ranked.size());
            for (int i = 0; i < shown && lines.size() < MAX_ROWS; i++) {
                RankedPlayer row = ranked.get(i);
                String color = colorFor(i, config);
                Map<String, String> lineValues = Map.of(
                        "rank", String.valueOf(i + 1),
                        "name", row.name(),
                        "score", String.valueOf(row.score()));
                String text = Texts.fill(color + config.rankFormat, lineValues, config.maxLineChars);
                lines.add(new Line(text, row.score(), LegacyText.parse(text), false));
            }
            if (shown == 0 && !config.emptyText.isEmpty() && lines.size() < MAX_ROWS) {
                String text = Texts.fill(config.emptyText, values, config.maxLineChars);
                lines.add(new Line(text, Integer.MAX_VALUE - lines.size(), LegacyText.parse(text), true));
            }
        } else if (!config.missingModText.isEmpty() && lines.size() < MAX_ROWS) {
            String text = Texts.fill(config.missingModText, values, config.maxLineChars);
            lines.add(new Line(text, Integer.MAX_VALUE - lines.size(), LegacyText.parse(text), true));
        }

        int footerCount = Math.min(config.footerLines.size(), MAX_ROWS - lines.size());
        if (footerCount < config.footerLines.size()) {
            warnTruncation();
        }
        for (int i = 0; i < footerCount; i++) {
            String text = Texts.fill(config.footerLines.get(i), values, config.maxLineChars);
            lines.add(new Line(text, Integer.MIN_VALUE + (footerCount - i), LegacyText.parse(text), true));
        }
        return lines;
    }

    private static String colorFor(int index, BoardConfig config) {
        List<String> colors = config.rankColors;
        if (colors.isEmpty()) {
            return "";
        }
        return colors.get(Math.min(index, colors.size() - 1));
    }

    private static String signature(List<Line> lines, BoardConfig config) {
        StringBuilder builder = new StringBuilder(256);
        builder.append(config.title).append('\u0001');
        for (Line line : lines) {
            builder.append(line.value()).append('\u0002')
                    .append(line.blankNumber() ? 'b' : 'n').append('\u0002')
                    .append(line.text()).append('\u0001');
        }
        return builder.toString();
    }

    private static void warnTruncation() {
        if (!truncationLogged) {
            truncationLogged = true;
            ReincarnationBoard.LOGGER.warn("公告栏内容超过原版侧栏上限 {} 行，后面的行会被忽略；请减少 headerLines / footerLines 或 topCount。", MAX_ROWS);
        }
    }

    /** 一行：值决定排序，text 用于比较是否变化，display 是真正发给客户端的文字。 */
    private record Line(String text, int value, Component display, boolean blankNumber) {
    }

    private static String resolveName(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getName().getString();
        }
        try {
            Optional<NameAndId> cached = server.services().nameToIdCache().get(id);
            if (cached.isPresent()) {
                return cached.get().name();
            }
        } catch (Throwable ignored) {
            // 离线玩家名查不到时用 UUID 前缀兜底。
        }
        return "玩家-" + id.toString().substring(0, 8);
    }

    /** 一次刷新内共用的排行数据。 */
    private record Snapshot(boolean available, List<RankedPlayer> ranked) {

        static Snapshot build(MinecraftServer server, BoardConfig config) {
            boolean available = ScoreSource.isInstalled();
            List<RankedPlayer> ranked = new ArrayList<>();
            if (available) {
                for (Map.Entry<UUID, Integer> entry : ScoreSource.collect(server).entrySet()) {
                    ranked.add(new RankedPlayer(entry.getKey(), resolveName(server, entry.getKey()), entry.getValue()));
                }
            }
            ranked.sort(Comparator.comparingInt(RankedPlayer::score).reversed()
                    .thenComparing(RankedPlayer::name, String.CASE_INSENSITIVE_ORDER));
            List<RankedPlayer> filtered = new ArrayList<>(ranked.size());
            for (RankedPlayer row : ranked) {
                if (row.score() >= config.minScore) {
                    filtered.add(row);
                }
            }
            return new Snapshot(available, List.copyOf(filtered));
        }
    }
}