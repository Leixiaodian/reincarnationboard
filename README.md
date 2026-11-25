# 转生苹果公告栏 (Reincarnation Board)

Minecraft **1.21.11** / Fabric 的**纯服务端**模组：用**原版记分板侧栏（积分板）**在屏幕右侧常驻显示
「转生苹果」积分排行榜前 N 名，**并且每个玩家还能看到自己的一行「我的积分」**；
标题、公告文字、名次格式、颜色都能在配置文件里改。

- **客户端不用装任何东西**，原版客户端就能看到（所有内容都是原版记分板数据）。
- 服务端需要 Fabric Loader **>= 0.19.0** + Fabric API。
- 软依赖「转生苹果」模组（`reincarnationapple`）：没装也能启动，面板会显示提示文字。

---

## 1. 效果

侧栏固定显示在屏幕右侧（原版记分板侧栏的位置），从上到下：

```
┌────────────────────────┐
│    转生苹果 · 积分榜     │  ← title（记分板目标名）
│   欢迎来到服务器         │  ← headerLines
│   在线：3 人            │
│                        │
│   1. Alice        128   │  ← 排行榜前 N 名（分数右对齐）
│   2. Bob           96   │
│   3. Carol         64   │
│                        │
│   我的积分：96           │  ← 每个玩家自己的行（只在本人客户端显示）
│                        │
│   食用转生苹果进入维度   │  ← footerLines
│   第一名：Alice         │
└────────────────────────┘
```

原理：服务端创建一个记分板目标 `rb_board` 并设为 sidebar 显示；每一行是一个「假玩家」分数行，
行的显示文字用 Score 的 display 覆盖，**文字行的数字格式设为 blank（不显示数字）**，
名次行保留原版数字，所以积分会像记分板一样右对齐显示。
内容变化时才写记分板，默认每秒检查一次。

「我的积分」这一行不写进服务端共享记分板，而是用原版 `ClientboundSetScorePacket`
**单独发给对应玩家**：每个客户端的本地记分板里多出自己的一行，各看各的，互相看不到。
它排在本服名次之后、页脚之前（数字隐藏，分数直接写在文字里），同样走原版记分板渲染。
刚进服时会多补发几次，避免目标还没同步到客户端导致这一行丢失。

### 限制（原版记分板本身的限制）

- 侧栏最多 **15 行**。排行榜/公告是全服共享的，「我的积分」是每人本地额外的一行；开启 `showOwnScore` 时
  共享部分最多只排 14 行（`headerLines` + 排行榜 + `footerLines`），超出的行会被忽略（日志会提示一次）。
- 排行榜和公告对全服玩家相同；只有「我的积分」行是按人单独下发的。
- 会**占用原版 sidebar 显示槽**。关闭模组（`/reincarnationboard toggle` 或把 `enabled` 改成 false 后重载）时，
  会移除自己创建的目标并恢复之前占用的那个侧栏目标。

---

## 2. 配置：公告栏内容全部可改

服务端配置文件：**`config/reincarnationboard.json`**（首次启动自动生成）。
改完执行 **`/reincarnationboard reload`** 立即生效，不用重启。

默认内容：

```json
{
  "enabled": true,
  "refreshTicks": 20,
  "title": "§6转生苹果 · 积分榜",
  "topCount": 3,
  "rankFormat": "%rank%. %name%",
  "rankColors": ["§e", "§f", "§6"],
  "emptyText": "§7还没有人上榜",
  "minScore": 1,
  "showOwnScore": true,
  "ownFormat": "§b我的积分：§e%score%",
  "ownUnrankedText": "§7未上榜",
  "headerLines": [],
  "footerLines": [],
  "missingModText": "§c未检测到「转生苹果」模组",
  "unrankedText": "§7—",
  "maxLineChars": 32
}
```

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 总开关。关闭时移除目标并恢复原 sidebar。 |
| `refreshTicks` | `20` | 刷新间隔（tick），20 tick = 1 秒。 |
| `title` | `§6转生苹果 · 积分榜` | 侧栏标题（即记分板目标显示名）。留空则不显示标题。 |
| `topCount` | `3` | 显示前几名（0 表示只显示公告文字）。 |
| `rankFormat` | `%rank%. %name%` | 名次行左侧文字格式，可用 `%rank%` `%name%` `%score%`。 |
| `rankColors` | `["§e","§f","§6"]` | 第 1/2/3……名的前缀颜色，不够用时循环取最后一个。 |
| `emptyText` | `§7还没有人上榜` | 排行榜为空时的提示行。留空则不显示。 |
| `minScore` | `1` | 低于该积分的玩家不上榜。 |
| `showOwnScore` | `true` | 是否给每个玩家显示自己的一行「我的积分」。 |
| `ownFormat` | `§b我的积分：§e%score%` | 「我的积分」行格式，可用 `%player%` `%score%` `%rank%`；留空则不显示。 |
| `ownUnrankedText` | `§7未上榜` | 自己没上榜时 `%rank%` 的替代文字。 |
| `headerLines` | `[]` | 标题下方、排行榜上方的自定义文字（一行一个）。**公告写这里**。 |
| `footerLines` | `[]` | 排行榜下方的自定义文字（一行一个）。 |
| `missingModText` | `§c未检测到「转生苹果」模组` | 没装转生苹果时的提示。留空则不显示。 |
| `unrankedText` | `§7—` | `%topN%` 等占位符没有对应名次时的文字。 |
| `maxLineChars` | `32` | 单行最多字符数（防止公告太长把侧栏撑满屏幕）。 |

### 2.1 颜色

所有文字都支持原版格式符：`§6` 金、`§e` 黄、`§a` 绿、`§c` 红、`§7` 灰、`§l` 加粗、`§o` 斜体、`§r` 重置……

### 2.2 占位符

公共行（`title`、`headerLines`、`footerLines`、`rankFormat`）可用：

| 占位符 | 含义 |
| --- | --- |
| `%online%` | 在线人数 |
| `%count%` | 上榜人数（同分一并计入） |
| `%topN%` | 第 N 名的「名字 分数」 |
| `%topN_name%` | 第 N 名的名字 |
| `%topN_score%` | 第 N 名的分数 |

「我的积分」行（`ownFormat`）额外支持：

| 占位符 | 含义 |
| --- | --- |
| `%player%` | 当前玩家名字 |
| `%score%` | 当前玩家积分 |
| `%rank%` | 当前玩家名次（没上榜时用 `ownUnrankedText`） |
| `%online%` | 在线人数 |
| `%count%` | 上榜人数 |

`N` 从 1 开始，最大到 `topCount`（至少支持前 3 名）。

### 2.3 公告示例

```json
{
  "title": "§6§l转生苹果 · 积分榜",
  "topCount": 3,
  "headerLines": ["§e欢迎来到服务器", "§7在线：§f%online% §7人"],
  "footerLines": ["§7食用 §c转生苹果 §7进入维度", "§8第一名：%top1_name% §7· §8上榜 %count% 人"],
  "rankFormat": "%rank%. %name%",
  "ownFormat": "§b我的积分：§e%score% §8(第 %rank% 名)",
  "maxLineChars": 28
}
```

---

## 3. 命令

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/reincarnationboard reload` | OP（2 级） | 重新读取配置并立刻重写公告栏 |
| `/reincarnationboard toggle` | OP（2 级） | 开关公告栏（会写回配置；关闭时恢复原 sidebar） |
| `/reincarnationboard status` | OP（2 级） | 打印当前公告栏每行实际内容（排查配置用） |
| `/reincarnationboard score set <玩家> <分数>` | OP（2 级） | 直接设置玩家积分（调用转生苹果公开接口） |
| `/reincarnationboard score add <玩家> <数量>` | OP（2 级） | 给玩家加/扣积分 |

`/zhaboard` 是同一个命令的短别名。`score` 子命令在没装转生苹果时会提示并拒绝执行。

---

## 4. 排行榜数据从哪来

- 单个玩家积分：走「转生苹果」的公开接口 `ScoreApi`。
- 全局排行榜（含离线玩家）：读取转生苹果的积分存档 `<存档>/data/reincarnationapple_scores.dat`；
  读不到就自动退化成「只统计在线玩家」，只记一条警告，不会崩。
- 玩家名字：在线玩家用当前名字；离线玩家从服务器 `usercache.json` 查（缓存过期或查不到时显示 `玩家-<UUID前8位>`）。

---

## 5. 构建

```
# 仓库自带 wrapper（第一次会下载 Gradle，需要联网）
.\gradlew.bat build

# 或使用本机已缓存的 Gradle，离线构建
gradle build --offline
```

产物：`build/libs/reincarnationboard-1.2.0.jar`（部署时不要上传 `-sources.jar`）。

> `libs/reincarnationapple-1.0-SNAPSHOT.jar` 是编译/开发联调用的「转生苹果」API jar，
> 只参与编译，不会被打进正式 jar；版本不一致时替换这个文件即可。

开发联调：`gradlew runServer`（运行目录 `run/`，已在 `.gitignore` 里排除）。

## 6. 安装

只把 jar 放进**服务端**的 `mods/`，重启服务器即可。客户端什么都不用装；
服务端还需要 Fabric API 和（建议）`reincarnationapple`。

---

## 许可证

MIT，见 [LICENSE.txt](LICENSE.txt)。