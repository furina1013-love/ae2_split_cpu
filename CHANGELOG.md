# Changelog / 更新记录

所有版本的双平台 jar 在 GitHub Releases 页面：<https://github.com/furina1013-love/ae2_split_cpu/releases>

## [1.2.4] - 2026-11-28

### 配置界面汉化
- 为全部 7 个配置项添加 `translation` 键，并补齐中英文 `lang`（名称 + tooltip）：
  `enableSplitting`、`requireSplitComponent`、`maxSplits`、`parallelScheme`、`fixedParallel`、`minParallel`、`loopCoreStorageBytes`。
- 配置根标题、`common` 分区标题也加了翻译。中文客户端在 NeoForge 自带配置界面中直接显示中文，不用打开 TOML。

## [1.2.3] - 2026-11-28

### 循环核心接入 / 连网修复
- 循环核心（`loop_core`）现在也注册了闪烁点渲染器：成型且供能=绿色快闪，成型未供能=琥珀色慢闪，未成型不显示。
- 新增配置 `loopCoreStorageBytes`，默认 **1**：AE2 要求 CPU 内至少一个单元存储字节 > 0 才会成型；默认 1 让“循环核心 + 分裂元件”这类纯标记结构也能成型并连网。设为 0 可恢复纯标记（此时必须另配 storage）。
- 1.21.1 新增 `ae2:crafting_unit_transform` 配方：拿着循环核心右键基础合成单元可直接替换成循环核心（1.20.1 的 AE2 15.4 没有该配方机制，只能放置）。
- 文档补充：循环核心必须放进 CPU 多方块内部；AE2 的合成单元未成型时不会连接网络。

## [1.2.2] - 2026-11-28

### 循环配方模拟修正
- 种子数量改为按“一次配方的输入量”计算：`a+b=2a` 需要 1 个 a，`2a+b=3a` 需要 2 个 a。
- 修正“输入也是输出但净亏”的 key：现在写入毛产出，避免模拟少算/多算。

## [1.2.1] - 2026-11-28

### 修复
- 无种子时禁止 `notRecursive` 放行循环配方，避免规划阶段无限递归/栈溢出。
- Forge `mods.toml` 版本改用 `${mod_version}` 占位符，修复 Forge jar 元数据版本停留在 1.1.1 的问题。

## [1.2.0] - 2026-11-28

### 新功能
- **分裂元件闪烁点**：分裂元件接入成型 CPU 后，顶面与四个侧面各显示 4 个呼吸闪烁点；绿色=已供能，琥珀色=未供能，不显示=未成型。
- **循环核心（`loop_core`）**：放进 CPU 后可以下单 `a+b=2a` 这类自循环配方。规划器按净产出计算，运行时借用种子并在任务结束后归还。
  - 支持单配方直接自循环；跨配方环（`a→b→a`）仍按缺料处理。
  - 下单前网络里需要足够启动一次配方的种子；带容器物品的循环配方暂不支持。
- 双平台（NeoForge 1.21.1 / Forge 1.20.1）同步实现；GitHub Actions 矩阵构建并自动发布。
