# 交接文档 —— AE2 Split CPU

> 给下一个接手的人 / 给未来的自己。最后更新：v1.1.1（2026-11）。
> 项目：<https://github.com/furina1013-love/ae2_split_cpu>
> 当前版本：**1.1.1**，双平台：MC 1.21.1 + NeoForge 21.1.x ／ MC 1.20.1 + Forge 47.x。

---

## 1. 用户的要求（原始需求，逐条）

按用户当时的话整理，右侧是"落在哪里"：

| # | 要求 | 实现位置 |
|---|---|---|
| 1 | 给 AE2 做附属，合成 CPU **下单时分裂** | `split/SplitCpuManager#createSplitCpu`（由 `mixin/CraftingCPUClusterMixin` 在 `submitJob` HEAD 调用） |
| 2 | 分裂出来的 CPU **最大可用字节 = 当前订单字节** | 子 CPU `getAvailableStorage()` 被 mixin 覆写为 `ae2split$splitBytes`，值来自 `plan.bytes()` |
| 3 | 对应字节/并行**临时从主 CPU 扣除**，订单结束恢复 | 主 CPU 的 `getAvailableStorage()/getCoProcessors()` 返回 `总量 - 子 CPU 预留之和`；子 CPU 回收后预留清零，天然恢复 |
| 4 | **并行按方案分配**，方案可配置 | `split/ParallelScheme` + `SplitCpuManager#allocateThreads`（EQUAL 默认 / PROPORTIONAL / ALL / FIXED） |
| 5 | 分裂**数量上限**（不含主 CPU），配置文件可调 | `maxSplits`（默认 3，范围 0–64） |
| 6 | 一个 CPU 同时跑两个订单时，CPU 界面里要看到**两个真正在跑的子 CPU + 一个空闲主 CPU** | 子 CPU 是真正的 `CraftingCPUCluster` 实例，注册进 `CraftingService.craftingCPUClusters`，AE2 界面自动列出（`Split #1`…） |
| 7 | 新增方块**分裂元件**，决定该 CPU 是否允许分裂 | `content/SplitComponentBlock` + `content/SplitComponentType`（0 字节 0 并行的合成单元），`requireSplitComponent` 控制是否强制要求 |
| 8 | 配置项：并行方案、是否需要元件（默认需要）、分裂上限 | `AE2SplitConfig`（COMMON 配置，`config/ae2_split_cpu-common.toml`） |
| 9 | 兼容 JEI、模组配置模组、其它 AE2 附属的 CPU | JEI 仅 optional 依赖；配置用加载器原生 TOML；子 CPU 走 AE2 原生 `submitJob`/`CraftingService` |
| 10 | 做 **1.20.1 Forge** 版，两个版本合并在一个发行版发布 | `forge-1.20.1/` 子工程；GitHub Release `v1.1.1` 里两个 jar 同一页 |
| 11 | 默认并行"分配太少"，改默认 | 默认方案由 `PROPORTIONAL` 改为 **`EQUAL`**（1.1.0 起），并加"并行分完不再分裂"的守卫 |
| 12 | modid 改成"不带下划线" | ⚠️ 试过连字符，**加载器不接受**（见坑 1），最终 modId = `ae2_split_cpu`，仅 jar 文件名用连字符 |
| 13 | 写 CurseForge 英文简介 + mcmod 中文简介（后者更短） | `docs/curseforge.md`、`docs/mcmod.md` |
| 14 | 做一张封面 | `art/make_cover3.py`（PIL 脚本）→ `cover/*.png` |
| 15 | git 流程改成 **推源码、CI 构建** | `.github/workflows/build.yml`（本次新增） |

---

## 2. 目录结构与职责

```
ae2_split_cpu_1211/            ← 仓库根 = MC 1.21.1 / NeoForge 工程
├─ .github/workflows/build.yml ← CI：矩阵构建两个平台，打 tag 时自动发 Release
├─ build.gradle gradle.properties settings.gradle
├─ HANDOVER.md                 ← 本文
├─ README.md                   ← 仓库首页（中文，含配置表/原理）
├─ PLAN.md                     ← 最初的实现计划（历史资料）
├─ docs/
│  ├─ curseforge.md            ← CurseForge 英文简介
│  └─ mcmod.md                 ← mcmod 中文简介（短版）
├─ src/main/java/cn/furina/ae2split/
│  ├─ Ae2SplitCpu.java         ← @Mod 入口：注册表、创造标签页、COMMON 配置
│  ├─ AE2SplitBlocks.java      ← 方块/物品/BlockEntityType 注册（复用 AE2 的 CraftingBlockEntity）
│  ├─ AE2SplitConfig.java      ← 配置项与默认值
│  ├─ content/
│  │  ├─ SplitComponentBlock.java  ← 分裂元件方块（继承 AE2 AbstractCraftingUnitBlock）
│  │  └─ SplitComponentType.java   ← ICraftingUnitType：0 字节 / 0 并行
│  ├─ split/
│  │  ├─ SplitCpuManager.java  ← 核心：分裂、预留、回收、持久化、并行分配
│  │  ├─ ParallelScheme.java   ← 并行方案枚举
│  │  ├─ SplitCpuAccess.java   ← 由 CraftingServiceMixin 实现（往 CPU 集合增删）
│  │  └─ SplitCpuHandle.java   ← 由 CraftingCPUClusterMixin 实现（子 CPU 私有状态）
│  └─ mixin/
│     ├─ CraftingCPUClusterMixin.java ← 委托 + 扣减 + 下单拦截 + NBT 存读
│     └─ CraftingServiceMixin.java    ← CPU 集合增删、结束回收、重挂接
├─ src/main/resources/         ← assets/ae2_split_cpu、data/ae2_split_cpu、mixins.json、mods.toml
├─ forge-1.20.1/               ← MC 1.20.1 / Forge 工程（同源码，改 Forge API 写法）
│  └─ libs/README.txt          ← 说明要自己放 AE2 15.4.12 的 jar（不随仓库分发）
├─ dist/                       ← 手工构建产物（CI 构建走 artifacts）
├─ cover/                      ← 封面图（logo-512/400/256、banner-1280x720/640）
└─ tools/cf_upload.py          ← （在上级目录 java-mod-dev/tools/）CurseForge 上传脚本
```

**两个工程的关系**：1.20.1 是同一套逻辑的端口，差异只有三类——
① 注册 API（`net.neoforged.*` → `net.minecraftforge.*`）；
② 配置 spec（`ModConfigSpec` → `ForgeConfigSpec`）；
③ AE2 15.4 的 NBT 方法没有 `HolderLookup.Provider` 参数。
其余类逐字相同，改一处要两边同步。

---

## 3. 构建

### CI（推荐，现在是唯一"正式"出包方式）

推送到 `main` 或任意 tag 会自动跑：

- **矩阵**：`neoforge-1.21.1`（JDK 21，根目录）与 `forge-1.20.1`（JDK 17，`forge-1.20.1/`）
- **AE2 编译期依赖**由 CI 自己从 CurseForge CDN 下载（`mediafilez.forgecdn.net`，无需鉴权），所以仓库里不需要也不允许提交 AE2 的 jar
- 产物：每个平台的 `dist/ae2-split-cpu-<版本>-<loader>.jar`，作为 **artifact** 上传
- **打 tag `v*` 时**：自动创建 GitHub Release 并把两个 jar 一起挂上去（`softprops/action-gh-release`）

发版流程：改 `mod_version` → 推到 main → `git tag -a v1.1.2 -m ... && git push --tags` → CI 出 release。

### 本地构建（调试用）

- MC 1.21.1：`./gradlew build`（需要 JDK 21；AE2 编译 jar 放 `libs/appliedenergistics2-19.2.19.jar`，没有则从 Modrinth Maven 拉）
- MC 1.20.1：`cd forge-1.20.1 && ./gradlew build`（JDK 17；AE2 jar 放 `forge-1.20.1/libs/appliedenergistics2-forge-15.4.12.jar`）
- 本机注意：Gradle wrapper 指向腾讯镜像（services.gradle.org 在容器里会超时）；`~/.gradle/init.d` 里有本机专用的仓库修正脚本，CI 不需要

---

## 4. 私有文件（不要提交）

统一放在 **`/root/java-mod-dev/_private/`**（700，文件 600）：

| 文件 | 用途 |
|---|---|
| `githubtoken.txt` | GitHub PAT（推仓库、调 GitHub API 用） |
| `curseforgetoken.txt` | CurseForge API key（❌ 目前那把是 UUID 形态，CF 不认，见坑 6） |
| `github andcurseforge token.txt` | 用户原始给的合并文件（历史） |
| `githubtoken.txt.bak` | 旧 token 备份 |
| `mihomo-proxy/` | 代理内核 + 用户订阅配置（含节点，属私有） |

规则：任何 token / 订阅 / 代理配置都放这里；提交前用 `git status` 确认没漏。仓库的 `.gitignore` 已排除 `libs/*.jar`、`build/`、`.gradle/`。

---

## 5. 公用工具（可复用）

| 路径 | 说明 |
|---|---|
| `/root/java-mod-dev/tools/cf_upload.py` | CurseForge 上传 CLI：`check` / `project` / `upload` / `files`。读取 `_private/curseforgetoken.txt` 或 `CF_API_KEY` |
| `/root/java-mod-dev/art/make_cover3.py` | 封面生成（纯 PIL：径向背景 + 等轴方块 + 能量缝 + 文字），改颜色/文案后重跑即可 |
| `/root/java-mod-dev/curseforge_upload/` | 给"手动上传 CurseForge"准备的一包东西：两个 jar、`upload_instructions.txt`、英文简介、中文简介 |
| `/root/tools/vineflower.jar` | 反编译器（核对 AE2 签名用） |
| `/root/libs1211/`、`/tmp/ae2_1201/` | 各版本 AE2 / JEI / guideme jar（编译期参考） |

**排查工具**：`javap -p -classpath <jar> <class>` 是核对 AE2/Forge 签名最快的方式；mixin 目标一定要对着真实 jar 看，不要凭记忆。

---

## 6. 实现原理（改代码前必读）

1. **幽灵子 CPU**：下单时 `new CraftingCPUCluster(parent.getBoundsMin(), parent.getBoundsMax())`，没有方块实体，但塞进 `CraftingService.craftingCPUClusters`，于是 AE2 的 CPU 列表 / 选择 / 监视器逻辑天然认识它。
2. **委托**：子 CPU 的 `getGrid/getLevel/getSrc/getNode/markDirty/isActive/updateOutput/canBeAutoSelectedFor/isPreferredFor/getSelectionMode` 全部转给主 CPU，避免 core 为空导致的 NPE。
3. **拦截点**：`CraftingCPUCluster#submitJob` 的 HEAD。手动选 CPU、自动选 CPU、其它附属下单都会经过这里。子 CPU 自身、主 CPU 忙、模拟 plan、0 字节 plan 一律放行原逻辑。
4. **预留与恢复**：主 CPU 的 `getAvailableStorage()/getCoProcessors()` 减去所有子 CPU 的预留和（`Math.max(0, …)`）；子 CPU 被回收后预留消失 = 自动恢复。
5. **回收**：`CraftingService#onServerEndTick` TAIL 扫描"没有任务且 inventory 为空"的子 CPU → 从集合移除、标记 destroyed。
6. **重挂接**：`CraftingService#updateCPUClusters` 会重建集合，TAIL 里把仍存活的子 CPU 塞回去，并重新登记 `CraftingLink`，否则订单会失去连接卡住。
7. **持久化**：主 CPU 的 NBT 里写 `ae2split_children`（每项 Bytes/Threads/Index/Logic），读档时重建；1.20.1 没有 `HolderLookup` 参数。
8. **并行方案**：`EQUAL`（默认）= `ceil(剩余并行 / 剩余名额)`；`PROPORTIONAL` = 按订单字节比例；`ALL` 全给；`FIXED` 固定值。分配总额不会超过真实并行；**剩余为 0 时不再分裂**（否则子 CPU 0 并行接单会永久卡住）。

---

## 7. 踩过的坑（血泪，务必先看）

1. **modId 只允许 `^[a-z][a-z0-9_]{1,63}$`**（NeoForge `loader-4.0.44.jar` → `ModInfo.class` 里就是这个正则）。连字符会直接 `Invalid modId found in file`。1.1.0 栽在这，1.1.1 修回 `ae2_split_cpu`。jar 文件名可以随意（用连字符没问题），**注册名不行**。
2. **Mixin 配置声明的包是排他区**：`cn.furina.ae2split.mixin` 里不能放被外部引用的普通类，否则 `IllegalClassLoadError ... cannot be referenced directly`，而且会连累 AE2 初始化。接口/常量都放 `split/` 包。
3. **1.20.1 用 Forge 的真实 jar 必须 `fg.deobf`**：直接 `files(...)` 会因为 SRG 名（`m_142194_` 等）对不上官方映射而编译失败；FG 报 "files(...) dependencies are not deobfuscated"，要用 `flatDir { dirs 'libs' }` + `fg.deobf('local:appliedenergistics2-forge:15.4.12')`。
4. **AE2 版本差异**：1.20.1（15.4.x）与 1.21.1（19.2.x）的 `CraftingCPUCluster` 只差 NBT 方法的 `HolderLookup.Provider` 参数；三个 `@Shadow`（storage/accelerator/isDestroyed）和 18 个 `@Inject` 目标同名同签名。**大版本升级必须重新 `javap` 核对**。
5. **Mixin 声明方式**：Forge 1.20.1 走 jar MANIFEST 的 `MixinConfigs: ae2split.mixins.json`；NeoForge 1.21.1 走 `neoforge.mods.toml` 的 `[[mixins]] config="..."`。两边别写混。
6. **CurseForge API 从本容器不可达**：`api.curseforge.com` 对任何请求返回 CloudFront 403（换 29 个代理节点 + GitHub runner 都一样）；而 `mediafilez.forgecdn.net` 正常。另有：用户给的 36 位 UUID 不是 CF API key（CF 的 key 是 `$2a$10$...` 约 60 字符），所以上传至今未完成，见"待办"。
7. **GitHub 直连不稳**：`git push` 偶发 `GnuTLS recv error`，重试或加 `-c http.version=HTTP/1.1` 即可；大文件下载走 `ghproxy.net` 镜像更稳（本地）。
8. **构建产物版本号有两处**：1.20.1 工程的 `build.gradle` 里是字面量 `version = "1.1.1"`，`gradle.properties` 的 `mod_version` 只管 `mods.toml` 展开，改版本要**两处都改**（1.21.1 工程只有 `gradle.properties`）。

---

## 8. 已知限制 / 未做的事

- CPU 监视器显示的是主 CPU 的状态；子 CPU 的输出走原订单的返回机器。
- 拆掉主 CPU 会取消子任务并归还资源。
- 子 CPU 不落地、不占方块，随主 CPU 存读档重建。
- **CurseForge 尚未发布**（缺可用 API key）；mcmod 也未提交（需手动）。
- 1.20.1 版本只做了编译验证，尚未在真实客户端长期跑（1.21.1 版本用户已实测可以启动）。

## 9. 待办

1. 拿到**真正的 CurseForge API key**（`$2a$10$...`）→ 用 `tools/cf_upload.py` 或 GitHub Actions（runner 出口可通）建项目 + 传 1.1.1 两个 jar。
2. mcmod 建条目：用 `docs/mcmod.md` + `cover/logo-256.png`。
3. 旧存档兼容提示（modid 变更史：`ae2_split_cpu` → 1.1.0 的 `ae2-split-cpu` → 1.1.1 回到 `ae2_split_cpu`）已在 Release 里说明。
4. 可选优化：CPU 监视器显示子 CPU、子 CPU 命名可配、`CHILDREN` 用弱引用清理。

