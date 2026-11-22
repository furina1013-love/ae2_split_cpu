# AE2 分裂 CPU

让 **一个 AE2 合成 CPU 同时跑多个订单**。

原版 AE2 一个 CPU 同时只能处理一个合成任务，想并行就得再搭一套 CPU 多方块。装上本模组后，每次下单时 CPU 会自动分裂出一个**虚拟子 CPU**：它出现在 AE2 的 CPU 选择界面里，独自执行这个订单，字节数正好等于订单所需字节，并行则按配置方案分配。订单结束后资源自动合并回主 CPU。

## 功能

* **下单即分裂**：合成终端、无线终端、接口自动合成、以及其它 AE2 附属下单时都会触发；子 CPU 在 CPU 界面里显示为 `Split #1`、`Split #2`……
* **临时扣除**：子 CPU 占用的字节与并行在订单进行期间从主 CPU 扣除，结束后恢复，主 CPU 界面始终显示真实剩余量。
* **分裂上限可配**：每个主 CPU 最多分裂出 N 个子 CPU（不含主 CPU 本身）。
* **分裂元件**：一个 0 字节、0 并行的合成单元方块，放进 CPU 多方块即表示"该 CPU 可以分裂"；配置文件可设为必须安装。
* **并行方案**：EQUAL（默认，平均分配）、PROPORTIONAL（按订单字节比例）、ALL、FIXED，另可设下限。
* **兼容**：JEI（可选依赖）、模组配置界面、其它 AE2 附属的 CPU（子 CPU 就是标准 `CraftingCPUCluster`，走 AE2 原生流程）。

## 配置（config/ae2_split_cpu-common.toml）

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| enableSplitting | true | 总开关 |
| requireSplitComponent | true | 是否必须安装分裂元件 |
| maxSplits | 3 | 每个主 CPU 的最大子 CPU 数（0–64） |
| parallelScheme | EQUAL | 并行分配方案 |
| fixedParallel | 1 | FIXED 方案下每个子 CPU 的并行数 |
| minParallel | 1 | 并行分配下限 |

## 版本

* **1.21.1** + NeoForge 21.1.x + AE2 19.2.x
* **1.20.1** + Forge 47.x + AE2 15.4.x

MIT 协议，源码：https://github.com/furina1013-love/ae2_split_cpu

