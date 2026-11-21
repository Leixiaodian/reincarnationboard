package com.example.reincarnationboard.config;

import com.example.reincarnationboard.ReincarnationBoard;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 公告栏内容配置（服务端）。
 * <p>
 * 首次启动生成 {@code config/reincarnationboard.json}，改完执行 {@code /reincarnationboard reload} 即可生效，
 * 不需要重启服务器。所有文字都支持原版 {@code §} 颜色/格式码。
 * <p>
 * 因为公告栏是用原版记分板侧栏实现的，侧栏内容对全服所有玩家相同，
 * 所以占位符只支持全服信息：{@code %online%} {@code %count%}，
 * 以及 {@code %top1%} {@code %top1_name%} {@code %top1_score%}（数字可换）。
 */
public final class BoardConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static BoardConfig instance = new BoardConfig();

    // ===== 开关与刷新 =====

    /** 是否启用公告栏。关闭后会移除自己创建的记分板目标，并恢复原来的侧栏。 */
    public boolean enabled = true;

    /** 刷新间隔（tick）。20 tick = 1 秒。 */
    public int refreshTicks = 20;

    // ===== 标题与排行榜 =====

    /** 侧栏顶部标题（记分板目标名），留空则不显示标题。 */
    public String title = "§6转生苹果 · 积分榜";

    /** 显示前几名。0 表示不显示排行榜，只显示自定义公告文字。 */
    public int topCount = 3;

    /** 名次行左侧文字格式。占位符：%rank% %name% %score%（分数右侧原生显示，一般不用 %score%）。 */
    public String rankFormat = "%rank%. %name%";

    /** 第 1 名、第 2 名、第 3 名……的前缀颜色，不够用时循环取最后一个。 */
    public List<String> rankColors = new ArrayList<>(List.of("§e", "§f", "§6"));

    /** 排行榜为空时的提示（算作一行文字）。留空则不显示。 */
    public String emptyText = "§7还没有人上榜";

    /** 低于该积分的玩家不上榜。 */
    public int minScore = 1;

    // ===== 自定义公告文字 =====

    /** 标题下方、排行榜上方的自定义文字行（可写公告）。支持全部占位符。 */
    public List<String> headerLines = new ArrayList<>();

    /** 排行榜下方的自定义文字行（可写公告）。支持全部占位符。 */
    public List<String> footerLines = new ArrayList<>();

    /** 未安装「转生苹果」时显示的文字；留空则什么也不显示。 */
    public String missingModText = "§c未检测到「转生苹果」模组";

    /** 占位符 %topN% / %topN_name% 没有对应名次时用的文字。 */
    public String unrankedText = "§7—";

    /** 单行最多显示多少个字符（防止公告写太长把侧栏撑满屏幕）。 */
    public int maxLineChars = 32;

    private BoardConfig() {
    }

    public static BoardConfig get() {
        return instance;
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("reincarnationboard.json");
    }

    /** 读取配置；文件不存在时生成一份默认配置。 */
    public static void load() {
        Path path = path();
        BoardConfig loaded = new BoardConfig();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                BoardConfig parsed = GSON.fromJson(reader, BoardConfig.class);
                if (parsed != null) {
                    loaded = parsed;
                }
            } catch (Exception e) {
                ReincarnationBoard.LOGGER.warn("读取 {} 失败，本次使用默认配置：{}", path, e.toString());
            }
        }
        loaded.sanitize();
        instance = loaded;
        // 回写一次，把缺失/写错的字段补成默认值，方便管理员对照。
        save();
    }

    public static void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
            }
        } catch (Exception e) {
            ReincarnationBoard.LOGGER.warn("保存 {} 失败：{}", path, e.toString());
        }
    }

    private void sanitize() {
        this.refreshTicks = clamp(this.refreshTicks, 1, 72000);
        this.topCount = clamp(this.topCount, 0, 15);
        this.minScore = Math.max(0, this.minScore);
        this.maxLineChars = clamp(this.maxLineChars, 8, 128);
        this.title = nullTo(this.title, "");
        this.rankFormat = nullTo(this.rankFormat, "%rank%. %name%");
        this.emptyText = nullTo(this.emptyText, "");
        this.missingModText = nullTo(this.missingModText, "");
        this.unrankedText = nullTo(this.unrankedText, "");
        this.headerLines = clean(this.headerLines);
        this.footerLines = clean(this.footerLines);
        this.rankColors = clean(this.rankColors);
        if (this.rankColors.isEmpty()) {
            this.rankColors.add("§e");
            this.rankColors.add("§f");
            this.rankColors.add("§6");
        }
    }

    private static List<String> clean(List<String> values) {
        List<String> out = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (value != null) {
                    out.add(value);
                }
            }
        }
        return out;
    }

    private static String nullTo(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
