# AE2 Split CPU（分裂 CPU）

Minecraft **1.21.1** · NeoForge **21.1.249** · 依赖 **AE2 19.2.x**（编译基准 19.2.19）

让 AE2 的合成 CPU 在**下单时分裂**：每个订单交给一个虚拟子 CPU 独立执行，
字节数与并行从主 CPU 临时扣除，订单结束自动恢复。主 CPU 本身保持空闲。

## 效果

一个 8 并行 / 64K 字节的 CPU，同时下两个订单：

```
CPU 列表（AE2 终端 → CPU 状态页）
  #1  分裂 CPU #2   正在合成 …   32K 字节 / 4 并行
  #2  分裂 CPU #1   正在合成 …   16K 字节 / 2 并行
  #3  主 CPU        空闲         16K 字节 / 2 并行   ← 被扣掉 48K / 6 并行
```

- 子 CPU 的「最大可用字节数」恰好等于该订单需要的字节数（`plan.bytes()`）。
- 订单结束（完成 / 取消）后子 CPU 立即消失，主 CPU 的字节与并行自动恢复。
- 子 CPU 会真实出现在 AE2 的 CPU 界面里，带名字、字节、并行、进度、已用时间。

## 分裂元件（Split Component）

`ae2_split_cpu:split_component`，一个可放进 CPU 多方块的方块（0 字节 / 0 并行，纯标记）。
默认配置下 **CPU 结构里必须有至少一个分裂元件，该 CPU 才会分裂**（`requireSplitComponent`）。

- 合成：3×3 有序，三色处理器 + 福鲁伊克斯水晶 + 红石（见 `data/ae2_split_cpu/recipe/split_component.json`）
- 在任意 AE2 CPU 多方块里都能用（包括其它 AE2 附属添加的 CPU 方块，因为它们同属 `CraftingBlockEntity`）

## 配置

`config/ae2_split_cpu-common.toml`（标准 NeoForge COMMON 配置，任何配置编辑模组/命令都能改）

```toml
[split]
    # 总开关：关闭后 CPU 永不分裂，行为等同原版 AE2
    enableSplitting = true
    # 是否要求 CPU 结构中包含分裂元件
    requireSplitComponent = true
    # 每个主 CPU 最多分裂出多少子 CPU（不含主 CPU 自身），0 = 禁止
    maxSplits = 3
    # 并行分配方案：PROPORTIONAL / EQUAL / ALL / FIXED
    parallelScheme = "PROPORTIONAL"
    # FIXED 方案下每个子 CPU 固定并行数
    fixedParallel = 1
    # 任何方案下子 CPU 的并行下限
    minParallel = 1
```

并行方案：

| 方案 | 子 CPU 分到的并行 |
|---|---|
| PROPORTIONAL（默认） | `floor(剩余并行 × 订单字节 ÷ 剩余字节)` |
| EQUAL | `ceil(剩余并行 ÷ 剩余分裂名额)` |
| ALL | 剩余并行全部给它 |
| FIXED | 固定 `fixedParallel` |

## 兼容性

- **JEI**：配方完全数据驱动，JEI 直接显示分裂元件的合成表；不装 JEI 也不影响运行（只声明 optional 依赖）。
- **模组配置模组**：走 NeoForge 官方 COMMON 配置，文本 TOML，可被任意配置编辑器修改。
- **其它 AE2 附属的 CPU**：只对 `CraftingCPUCluster` 生效；其它附属若实现自己的 `ICraftingCPU` 类，
  本模组不会碰它（不分裂、不报错）。分裂元件可装进任何 `CraftingBlockEntity` 多方块。
- **存档**：子 CPU 及其任务随主 CPU 一起写入 NBT（键 `ae2split_children`），区块重载 / 重启后能恢复。

## 实现要点（给维护者）

- 拦截点：`CraftingCPUCluster.submitJob` 的 HEAD。主 CPU 空闲且有配额时创建「幽灵子 CPU」
  （同包 `CraftingCPUCluster` 实例，没有方块实体），把订单提交给它。
- 子 CPU 的 `getGrid/getLevel/getSrc/markDirty/isActive/getNode/...` 全部委托主 CPU（Mixin）；
  只有 `getAvailableStorage/getCoProcessors/getName` 用自己预留的值。
- 主 CPU 的 `getAvailableStorage/getCoProcessors` 返回 `总字节 − 子 CPU 预留和`，天然满足「临时扣除 / 结束恢复」。
- 回收：`CraftingService.onServerEndTick` 末尾扫描「无任务且库存为空」的子 CPU 并销毁；
  `updateCPUClusters` 重建集合后把存活子 CPU 重新挂回并补登记 `CraftingLink`。

## 已知限制

- CPU 监视器在分裂时会显示子订单的输出（多个子 CPU 并发时互相覆盖）。
- 主 CPU 被破坏时会取消所有子订单并把余料退回网络；极端情况下（网络满）余料可能留在 CPU 内部库存。
- 本环境无法启动 Minecraft 服务端（容器内 JVM 原生崩溃，与模组无关），
  因此「进游戏实测」这一项需要在你的客户端/服务端验证；编译期、字节码签名、Mixin 配置解析均已通过。
