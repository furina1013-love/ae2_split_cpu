<div align="center">

# AE2 Split CPU

**让 AE2 的合成 CPU 在下单时分裂 —— 一个 CPU，同时压多张订单。**

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.21.1-62B47A?style=flat-square)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-EA8C1E?style=flat-square)
![Forge](https://img.shields.io/badge/Forge-47.x-DA6A1E?style=flat-square)
![AE2](https://img.shields.io/badge/AE2-15.4.x%20%7C%2019.2.x-2E7CC4?style=flat-square)
![Build](https://github.com/furina1013-love/ae2_split_cpu/actions/workflows/build.yml/badge.svg)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)
![Release](https://img.shields.io/badge/Release-v1.2.3-success?style=flat-square)

[下载](https://github.com/furina1013-love/ae2_split_cpu/releases/latest) · [配置](#配置) · [从源码构建](#从源码构建) · [原理](#实现原理)

</div>

---

## 这是什么

AE2（应用能源2）的合成 CPU 一次只能跑一个订单。本模组把下单行为改成：

> 订单到达 → CPU 分裂出一个**虚拟子 CPU** → 子 CPU 的最大可用字节 = 这张订单需要的字节 →
> 对应字节与并行从主 CPU **临时扣除** → 订单结束，子 CPU 消失，主 CPU 数值**自动恢复**。

结果就是：一个物理 CPU 可以同时跑多个订单，CPU 界面里会出现「主 CPU 空闲 + N 个正在工作的分裂 CPU」。

## 效果

一个 64K 字节 / 8 并行的 CPU，同时下两张订单（32K 和 16K）：

```
AE2 终端 → CPU 状态页
┌─────────────────────────────────────────────────────────┐
│ 分裂 CPU #1   正在合成 …   32K 字节 / 4 并行   ██████░░░ │
│ 分裂 CPU #2   正在合成 …   16K 字节 / 2 并行   ███░░░░░░ │
│ 主 CPU        空闲         16K 字节 / 2 并行             │  ← 已被扣走 48K / 6 并行
└─────────────────────────────────────────────────────────┘
```

订单结束（完成/取消）后分裂 CPU 立即消失，主 CPU 恢复满额。

## 特性

- ⚡ **下单即分裂**：不需要额外操作，选中/自动选中的 CPU 会自动分裂
- 📏 **字节精确预留**：子 CPU 可用字节恰为该订单的 `plan.bytes()`
- 🧵 **并行可配置**：按字节比例 / 均分 / 全给 / 固定，四种方案
- 🔢 **分裂上限**：每个 CPU 最多几个子 CPU（不含主 CPU），配置项
- 🧩 **分裂元件方块**：默认要求 CPU 结构里装一块才允许分裂；成型后表面会亮起闪烁的点，一眼看出它有没有接进有效 CPU
- 🔁 **循环核心方块**：装在 CPU 里即可下单 `a + b = 2a` 这类自循环配方，规划器按“净产出”计算，种子借用后归还
- 💾 **存档持久化**：子 CPU 与进行中的订单随主 CPU 一起存读档
- 🔌 **兼容友好**：JEI 配方显示、标准 NeoForge 配置、其它 AE2 附属的 CPU 方块

## 安装

| 游戏版本 | 加载器 | 需要的 AE2 | 下载的 jar |
|---|---|---|---|
| **1.21.1** | NeoForge 21.1.x | AE2 **19.2.x** | `ae2-split-cpu-1.2.3-neoforge-1.21.1.jar` |
| **1.20.1** | Forge 47.x | AE2 **15.4.x** | `ae2-split-cpu-1.2.3-forge-1.20.1.jar` |

1. 按上表下载对应版本的 jar，丢进 `mods/` 文件夹
2. 安装对应版本的 **Applied Energistics 2**（必须，模组不内置）
3. 启动后生成配置：`config/ae2_split_cpu-common.toml`

> modid 固定为 `ae2_split_cpu`（下划线）。加载器只接受 `^[a-z][a-z0-9_]{1,63}$`，**带连字符的 id 会直接报「Invalid modId」**，所以 jar 文件名用连字符、注册名用下划线。

单人与服务端都可以用；服务端装了客户端也要装（含方块与网络逻辑）。

## 分裂元件

`ae2_split_cpu:split_component`（中文名「分裂元件」）—— 一个可以放进 CPU 多方块的方块，
本身 **0 字节 / 0 并行**，只作标记：CPU 结构里有它，这台 CPU 才允许分裂。

默认 **需要** 安装（`requireSplitComponent = true`）。合成：

```
P L P        P = 运算处理器  (ae2:calculation_processor)
L F L        L = 逻辑处理器  (ae2:logic_processor)
E R E        E = 工程处理器  (ae2:engineering_processor)
             F = 福鲁伊克斯水晶 (ae2:fluix_crystal)
             R = 红石       (minecraft:redstone)
```

任意 AE2 CPU 多方块都能用（它和 AE2 自带合成单元同属 `CraftingBlockEntity`，
所以其它 AE2 附属添加的 CPU 方块也兼容）。

### 闪烁点：判断它有没有接进有效 CPU

分裂元件成型后，方块表面（顶面 + 四个侧面）会亮起 **4 个会呼吸闪烁的点**：

| CPU 状态 | 表现 |
|---|---|
| 已成型的 CPU 且已供能 | 绿色/青色快速闪烁（有效） |
| 已成型的 CPU 但未供能 | 琥珀色缓慢闪烁 |
| 没接进 CPU（未成型） | 不显示任何点 |

点只在客户端渲染，状态直接读方块同步过来的 `FORMED` / `POWERED`，不会增加服务端负担。

## 循环核心

`ae2_split_cpu:loop_core`（中文名「循环核心」）—— 默认 **1 字节 / 0 并行** 的 CPU 标记方块
（`loopCoreStorageBytes` 可调；设为 0 即纯标记），它让 AE2 能规划 **自循环配方**，例如：

```
1 a + 1 b  ->  2 a      // 净效果：1 b -> 1 a，a 自己循环
```

原版 AE2 会把这种配方当成“递归/缺料”直接拒绝。装上循环核心后，规划器把它当成 **净配方**：
每执行一次净产出 `+1 a`、净消耗 `+1 b`；执行时 CPU 里需要 **足够启动一次配方的 a 作为种子**（`a+b=2a` 是 1 个 a；`2a+b=3a` 是 2 个 a），
任务结束后种子作为余料自动退回网络（“借用后归还，不计入消耗”）。

使用条件与步骤：

1. 在任意一台 CPU 的多方块结构里放至少 1 个循环核心（和分裂元件一样占一个单元位置）。
   循环核心默认自带 1 字节，所以只放循环核心 + 分裂元件的结构也能成型并连网；
   若把 `loopCoreStorageBytes` 设为 0，则必须再放至少一个真正的 storage 单元。
   成型后循环核心表面也会显示呼吸闪烁点（和分裂元件一致），没亮就是没进 CPU。
2. 网络里先准备 **足够启动一次配方的循环物品**（上例是 1 个 a）作为种子，否则下单会报缺料。
   种子数量 = 配方一次执行消耗的循环物品数，不是固定 1 个。
3. 正常在终端里下单循环物品即可；CPU 会按净配方执行，产物 = 下单数量。

合成：

```
E R E        E = 末影珍珠      (minecraft:ender_pearl)
R S R        R = 红石          (minecraft:redstone)
E R E        S = 分裂元件      (ae2_split_cpu:split_component)
             -> 1x 循环核心
```

> 循环规划是网络级的：只要网络上有一台成型的、含循环核心的 CPU，该网络就能规划循环配方；
> 任务由哪台 CPU 执行都可以（分裂 CPU 也可以）。

## 配置

`config/ae2_split_cpu-common.toml`（标准 NeoForge COMMON 配置，可用任何配置编辑器/命令修改）

```toml
[split]
    # 总开关：关闭后 CPU 永不分裂，行为等同原版 AE2
    enableSplitting = true
    # 是否要求 CPU 结构中包含分裂元件
    requireSplitComponent = true
    # 每个主 CPU 最多分裂出多少个子 CPU（不含主 CPU），0 = 禁止
    maxSplits = 3
    # 并行分配方案：EQUAL(默认) / PROPORTIONAL / ALL / FIXED
    parallelScheme = "EQUAL"
    # FIXED 方案下每个子 CPU 的固定并行数
    fixedParallel = 1
    # 任何方案下子 CPU 的并行下限
    minParallel = 1
```

| 配置项 | 默认 | 说明 |
|---|---|---|
| `enableSplitting` | `true` | 总开关 |
| `requireSplitComponent` | `true` | 是否必须装分裂元件 |
| `maxSplits` | `3` | 子 CPU 上限（不含主 CPU），0–64 |
| `parallelScheme` | `EQUAL` | 并行分配方案 |
| `fixedParallel` | `1` | FIXED 方案的固定并行 |
| `minParallel` | `1` | 任何方案的并行下限 |
| `loopCoreStorageBytes` | `1` | 每个循环核心自带的存储字节；0 = 纯标记（需配合 storage 才能成型） |

### 并行分配方案

| 方案 | 子 CPU 拿到的并行 |
|---|---|
| `EQUAL`（默认） | `ceil(剩余并行 ÷ 剩余分裂名额)`，保证每张单分到差不多的并行 |
| `PROPORTIONAL` | `floor(剩余并行 × 订单字节 ÷ 剩余字节)`，大单多给、小单少给 |
| `ALL` | 剩余并行全部给它 |
| `FIXED` | 固定 `fixedParallel` |

> 例：8 并行、上限 3，来第一张单 → `ceil(8 ÷ 3) = 3` 并行；第二张 → `ceil(5 ÷ 2) = 3`；第三张 → 2。
> 所有方案分配出去的总并行不会超过主 CPU 实际拥有量；剩余并行为 0 时不再分裂（避免子 CPU 接单后卡住）。

## 兼容性

| 项目 | 说明 |
|---|---|
| **JEI** | 配方数据驱动，JEI 自动显示；不装 JEI 也不影响运行（optional 依赖） |
| **模组配置模组** | 使用 NeoForge 官方 COMMON 配置（纯文本 TOML） |
| **其它 AE2 附属** | 只对 `CraftingCPUCluster` 生效；其它附属自实现的 `ICraftingCPU` 不会被碰（不分裂、不报错） |
| **AE2 版本** | 编译基准 19.2.19，依赖声明 `[19.0.0,)` |

## 从源码构建

需要 **JDK 21**。

```bash
# 1) 准备 AE2 编译依赖（二选一）
#    a. 把 appliedenergistics2-<版本>.jar 放到 libs/
#    b. 或构建时传路径：-Pae2Jar=/path/to/appliedenergistics2-19.2.19.jar
# 2) 构建
./gradlew build
# 产物：build/libs/ae2-split-cpu-1.2.3.jar
```

不想自己编译？直接用 [Releases](https://github.com/furina1013-love/ae2_split_cpu/releases/latest) 里的 jar。

## 实现原理

- **拦截点**：`CraftingCPUCluster.submitJob` 的 HEAD。主 CPU 空闲且还有配额时，创建一个
  「幽灵子 CPU」——同包 `CraftingCPUCluster` 实例，没有方块实体，但完整参与 AE2 的调度。
- **委托**：子 CPU 的 `getGrid/getLevel/getSrc/markDirty/isActive/getNode/...` 全部转发给主 CPU（Mixin）；
  只有 `getAvailableStorage/getCoProcessors/getName/getJobStatus` 使用自己的值。
- **扣减**：主 CPU 的 `getAvailableStorage/getCoProcessors` 返回 `总量 − 所有子 CPU 预留和`，
  天然实现「临时扣除 / 结束恢复」。
- **回收**：`CraftingService.onServerEndTick` 末尾扫描「无任务且库存为空」的子 CPU 并销毁；
  `updateCPUClusters` 重建集合后把存活子 CPU 重新挂回，并补登记 `CraftingLink`。
- **持久化**：主 CPU 的 NBT 里多一个 `ae2split_children` 列表，随主 CPU 存档。
- **循环规划**：`CraftingTreeNode/notRecursive` 放行自循环配方；`CraftingTreeProcess` 把配方当成净配方——
  跳过“输入同时也是输出”的子节点、只写入净产出；`CraftingCpuLogic.trySubmitJob` 在下单时把种子补进
  `plan.usedItems()`，CPU 提交任务时会把它从网络抽进库存，结束后随余料退回。
- **闪烁点**：客户端 `BlockEntityRenderer` 读取方块状态的 `FORMED/POWERED`，用 `entityTranslucent`
  在 5 个面各画 4 个全亮小四边形，按世界时间做正弦脉动。

## 已知限制

- CPU 监视器在分裂时显示子订单的输出，多个子 CPU 并发时会互相覆盖。
- 主 CPU 被破坏时先取消所有子订单并把余料退回网络；网络已满时余料可能留在 CPU 内部库存。
- 本模组依赖 Mixin 修改 AE2 内部类，AE2 大版本升级（19.2 → 19.3+）后需要重新核对签名。
- 循环核心目前只处理 **单配方直接自循环**（输入与输出含同一个 key）。跨多个配方的循环
  （`a -> b -> a`）仍会按缺料处理；完整的多配方循环求解可参考 Eco AE Extension 的 cycle solver。
- 每个循环 key 每次下单只注入 **一次配方所需的输入量** 作为种子，因此同一循环同时只有 1 个配方在跑（吞吐受配方耗时限制）。
  种子越多并不会自动提速；如果配方本身有多个输出/副产物，副产物会按普通余料退回网络。
- 循环配方若带容器物品（桶等），容器返还不会计入规划；这类配方请在普通 CPU 上使用。

## 许可证

[MIT](LICENSE)
