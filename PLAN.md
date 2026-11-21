# PLAN — AE2 Split CPU (ae2_split_cpu) v1.0.0

目标：MC 1.21.1 + NeoForge 21.1.249 + AE2 19.2.19 的附属模组。让 AE2 合成 CPU 在下单时"分裂"出虚拟子 CPU，
每个子 CPU 恰好承担一个订单（最大可用字节 = 该订单字节数），字节与并行从主 CPU 临时扣除，订单结束自动恢复。
附带"分裂元件"方块（是否需要安装由配置决定）。

项目根：/root/java-mod-dev/ae2_split_cpu_1211
modid: ae2_split_cpu   包名: cn.furina.ae2split   版本 1.0.0
- AE2 源码（只读参考，官方 1.21.1 分支）：/tmp/ae2gh/src/main/java/appeng
- AE2 编译用 jar：/root/libs1211/appliedenergistics2-19.2.19.jar（已在 build.gradle 中 compileOnly）
- 构建：只用 mc_gradle 工具（projectDir 传项目根，task=build），禁止 shell 直调 gradlew。

## 1. 已核实的 AE2 事实（不要凭记忆改）
1. `appeng.me.cluster.implementations.CraftingCPUCluster implements IAECluster, ICraftingCPU`，**final**，public 构造 `CraftingCPUCluster(BlockPos min, BlockPos max)`，
   字段 `public final CraftingCpuLogic craftingLogic` 在构造器里用 this 建好；`private long storage; private int accelerator; private MachineSource machineSrc; private Component myName;`
2. `CraftingCPUCluster.getAvailableStorage()` 返回 storage；`getCoProcessors()` 返回 accelerator；两者都在 addBlockEntity 里累加，addBlockEntity 是包私有。
3. 集群方法：`getGrid() @Nullable`、`getLevel()`、`getSrc()`、`markDirty()`、`isActive()`、`getNode() @Nullable`、`getName()`、
   `updateOutput(GenericStack)`、`canBeAutoSelectedFor(IActionSource)`、`isPreferredFor(IActionSource)`、`getSelectionMode()`、
   `cancelJob()`、`submitJob(IGrid g, ICraftingPlan plan, IActionSource src, ICraftingRequester requester)`、
   `writeToNBT(CompoundTag, HolderLookup.Provider)`、`readFromNBT(CompoundTag, HolderLookup.Provider)`、`getBlockEntities()`、`destroy()`、`isDestroyed()`。
   这些方法内部依赖 getCore()/machineSrc，没有方块实体时会 NPE —— 这正是子 CPU 需要委托的原因。
4. `CraftingCpuLogic`：`trySubmitJob(IGrid, ICraftingPlan, IActionSource, ICraftingRequester)` 内做 busy/active/字节检查；
   `tickCraftingLogic(IEnergyService, CraftingService)`；`hasJob()`；`getInventory()` 返回 ListCraftingInventory，`public final KeyCounter list`；
   `writeToNBT/readFromNBT`（键 inventory/job）；`getLastLink()`。仓库插入走 `cluster.getGrid()/getSrc()/markDirty()`。
5. `CraftingService`（appeng.me.service）：`private final Set<CraftingCPUCluster> craftingCPUClusters`；
   `updateCPUClusters()` 每 tick 在 updateList 标记时 clear 后从 `grid.getMachines(CraftingBlockEntity.class)` 重建；
   `onServerEndTick()` 遍历该集合调 `cpu.craftingLogic.tickCraftingLogic(energyGrid, this)`；
   `getCpus()` 过滤 `isActive() && !isDestroyed()`；`submitJob(...)` 里 `target instanceof CraftingCPUCluster` 时直接用它。
6. `ICraftingUnitType` 是公开接口：`long getStorageBytes()`、`int getAcceleratorThreads()`、`Item getItemFromType()`。
   `AbstractCraftingUnitBlock<T extends CraftingBlockEntity>` 有 `public final ICraftingUnitType type`，构造 `(Properties, ICraftingUnitType)`。
   `CraftingBlockEntity` 构造 `(BlockEntityType<?>, BlockPos, BlockState)`，`getStorageBytes()/getAcceleratorThreads()` 取自方块 type。
7. `AEBaseEntityBlock.newBlockEntity` 用内部 blockEntityType（通过 setBlockEntity 注入）；AE2 的注册写法：
   `BlockEntityType.Builder.of((pos,state)->factory.create(type.get(),pos,state), blocks...).build(null)` 然后对每个方块
   `((AEBaseEntityBlock)block).setBlockEntity(entityClass, type, clientTicker, serverTicker)`；CraftingBlockEntity 没有 ticker。
8. CPU 界面完全由 `CraftingStatusMenu.createCpuList()` 调 `grid.getCraftingService().getCpus()` 生成（名字/字节/并行/进度），
   只要子 CPU 出现在 getCpus() 里就会显示；无需改客户端。
9. NeoForge 配置：`ModContainer.registerConfig(ModConfig.Type.COMMON, ModConfigSpec)`，
   Builder 有 `defineEnum(path, default)`、`defineInRange`、`define`、`comment`、`push/pop`。

## 2. 核心设计
子 CPU = 一个"幽灵" CraftingCPUCluster 实例（没有方块实体），通过 mixin 把下列行为委托给主集群：
getGrid/getLevel/getSrc/markDirty/isActive/getNode/canBeAutoSelectedFor/isPreferredFor/getSelectionMode；
自己的 getAvailableStorage()=预留字节、getCoProcessors()=分配并行、getName()=自定义名；updateOutput 空操作。
主集群的 getAvailableStorage()/getCoProcessors() 返回 storage/accelerator 减去所有子 CPU 预留的和（自动实现"临时扣除/结束恢复"）。

下单拦截点：`CraftingCPUCluster.submitJob(...)` 的 HEAD（手动选 CPU 与自动选 CPU 都会走到这里）：
- 若 this 是子 CPU → 放行（正常路径）。
- 若允许分裂（配置 enableSplitting、未达 maxSplits、requireSplitComponent 时集群含分裂元件、剩余字节 >= plan.bytes()）：
  创建子 CPU，注册进 CraftingService 的集合，然后 `return child.craftingLogic.trySubmitJob(g, plan, src, requester)`（cancellable）。
  若返回不成功（如缺料），销毁子 CPU 再原样返回该结果。
- 否则放行。

结束回收：CraftingService.onServerEndTick 的 TAIL 里遍历子 CPU：`!hasJob() && inventory.list.isEmpty()` → 从集合移除、从管理器移除、标记 destroyed。

并行分配方案（配置枚举 ParallelScheme）：
- PROPORTIONAL（默认）：`floor(剩余并行 * plan.bytes() / 剩余字节)`，下限 minParallel、上限剩余并行，剩余字节=0 时不分裂。
- EQUAL：`ceil(剩余并行 / (maxSplits - 已用子CPU数))`。
- ALL：剩余并行全部给子 CPU。
- FIXED：固定 fixedParallel。
并行不足 1 时给 0（CraftingCpuLogic 本身有 +1 基础并行）。

持久化：主集群 writeToNBT 追加子 CPU 列表（bytes/threads/index + 子 logic 的 NBT）；readFromNBT 时重建（先清空重建，避免重复）。
CraftingService.updateCPUClusters 的 TAIL 把管理器里、其父集群在集合内的子 CPU 重新加回集合，并为子 CPU 调 `addLink((CraftingLink) child.craftingLogic.getLastLink())`。

## 3. 文件清单
cn/furina/ae2split/
  Ae2SplitCpu.java            @Mod 主类：DeferredRegister 注册、registerConfig、事件
  AE2SplitConfig.java         ModConfigSpec（见 §5）
  AE2SplitBlocks.java         DeferredRegister.Blocks/Items/BlockEntityType + 方块/BE 注册与 setBlockEntity
  content/SplitComponentBlock.java       extends AbstractCraftingUnitBlock<CraftingBlockEntity>，构造传 SplitComponentType
  content/SplitComponentType.java        implements ICraftingUnitType：0 字节、0 并行、item 返回本模组方块物品
  content/SplitComponentBlockEntity.java 可选；能直接用 CraftingBlockEntity 就不新建
  split/SplitCpuManager.java  子 CPU 注册表、创建/销毁/重挂、预留统计、并行分配
  split/ParallelScheme.java   枚举 PROPORTIONAL/EQUAL/ALL/FIXED
  mixin/CraftingCPUClusterMixin.java  @Mixin(CraftingCPUCluster.class)
  mixin/CraftingServiceMixin.java     @Mixin(CraftingService.class) implements SplitCpuAccess
  mixin/SplitCpuAccess.java           接口：ae2split$addCpu / ae2split$removeCpu / ae2split$cpus
resources/
  META-INF/neoforge.mods.toml（已写，保留）
  ae2split.mixins.json  {"required":true,"minVersion":"0.8","package":"cn.furina.ae2split.mixin","compatibilityLevel":"JAVA_21","refmap":null
                         ,"mixins":["CraftingCPUClusterMixin","CraftingServiceMixin"],"injectors":{"defaultRequire":1}}  —— 不要写 refmap 字段值，省略即可
  assets/ae2_split_cpu/blockstates/split_component.json、models/block/split_component.json、models/item/split_component.json
  assets/ae2_split_cpu/textures/block/split_component.png（16x16，纯 Python zlib 生成，参考色调 #7a8fa6 / #4d5b6b）
  assets/ae2_split_cpu/lang/en_us.json、zh_cn.json（方块名 Split Component / 分裂元件）
  data/ae2_split_cpu/recipe/split_component.json（有序合成，材料用 ae2:calculation_processor/ae2:logic_processor/ae2:fluix_crystal/ae2:engineering_processor + minecraft:redstone）
  data/ae2_split_cpu/loot_table/blocks/split_component.json（自掉落，不用 loot table 也行——AE2 方块默认掉落需 loot table，必须写）

## 4. Mixin 细节
CraftingCPUClusterMixin：
- @Unique 字段：splitParent（CraftingCPUCluster）、splitBytes（long）、splitThreads（int）、splitName（Component）
- @Unique 方法：ae2split$initSplit(parent, bytes, threads, name)、ae2split$isSplit()、ae2split$getParent()、ae2split$getSplitBytes()、ae2split$getSplitThreads()
- HEAD cancellable 注入（用 (CraftingCPUCluster)(Object)this 造型）：getGrid/getLevel/getSrc/markDirty/isActive/getNode/updateOutput/canBeAutoSelectedFor/isPreferredFor/getSelectionMode → 委托 parent；destroy → 子 CPU 只置 isDestroyed（调 SplitCpuManager.onChildDestroyed）
- HEAD cancellable 返回值：getAvailableStorage→splitBytes、getCoProcessors→splitThreads、getName→splitName
- getAvailableStorage/getCoProcessors 对"主集群"（非子）返回 storage-预留 / accelerator-预留（@Shadow 私有字段 storage/accelerator）
- submitJob HEAD cancellable：见 §2
- writeToNBT TAIL / readFromNBT TAIL：持久化子 CPU
注意：mixin 里调用其他 mixin 注入的方法时用接口强转或静态工具；不要 @Shadow 不存在的字段。

CraftingServiceMixin：
- @Shadow private Set<CraftingCPUCluster> craftingCPUClusters;（final 字段 shadow 可以）
- updateCPUClusters TAIL：SplitCpuManager.reattach(this)
- onServerEndTick TAIL：SplitCpuManager.endOfTick(this)
- 实现 SplitCpuAccess：ae2split$addCpu(cpu) → craftingCPUClusters.add(cpu)；ae2split$removeCpu(cpu) → craftingCPUClusters.remove(cpu)

## 5. 配置（COMMON，文件 config/ae2_split_cpu-common.toml）
[split]
enableSplitting = true           （总开关）
requireSplitComponent = true     （CPU 必须包含分裂元件）
maxSplits = 3                    （每个主 CPU 的子 CPU 上限，不含主 CPU，0..64）
parallelScheme = "PROPORTIONAL"  （PROPORTIONAL/EQUAL/ALL/FIXED）
fixedParallel = 1                （FIXED 方案使用，0..64）
minParallel = 1                  （任何方案的下限，0..64）
注释中英文都写清楚。

## 6. 验收
1. `mc_gradle build` 成功产出 build/libs/ae2_split_cpu-1.0.0.jar。
2. 编译期不依赖 JEI 类（JEI 只是数据驱动兼容，不写 JEI 插件）。
3. mixin 目标方法名/描述符与 §1 完全一致；不能用 AE2 内部类之外的东西。
4. 所有新增类都要有中文注释说明作用。
