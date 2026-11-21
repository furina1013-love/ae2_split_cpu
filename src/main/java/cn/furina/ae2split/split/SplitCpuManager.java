package cn.furina.ae2split.split;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.crafting.CraftingLink;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;

import cn.furina.ae2split.AE2SplitConfig;
import cn.furina.ae2split.content.SplitComponentBlock;

/**
 * 子 CPU 注册表与生命周期管理器（全部静态方法）。
 *
 * <p>核心模型：主 CPU（有方块实体的真实 CraftingCPUCluster）在下单时"分裂"出一个没有方块实体的
 * 幽灵子 CPU。子 CPU 的所有网络行为（grid / 节点 / 脏标记）都由 CraftingCPUClusterMixin 委托给主 CPU；
 * 它只保留自己的预留字节、分配到的并行和显示名。主 CPU 的可用字节 / 并行在 mixin 里减去本类统计的
 * 预留值，从而实现"临时扣除、结束恢复"。</p>
 *
 * <p>注意：本类只在服务端主线程使用（AE2 的 CPU tick / 下单都在服务端主线程），因此内部集合没做额外同步。</p>
 */
public final class SplitCpuManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** NBT 中保存子 CPU 列表的键。 */
    private static final String TAG_CHILDREN = "ae2split_children";

    /**
     * 主 CPU -> 子 CPU 列表。CraftingCPUCluster 没有重写 equals/hashCode，因此这里就是身份比较；
     * 用 WeakHashMap 是为了在主 CPU 被彻底回收后不留下强引用（子 CPU 本身也持有主 CPU 引用）。
     */
    private static final Map<CraftingCPUCluster, List<CraftingCPUCluster>> CHILDREN = new WeakHashMap<>();

    private SplitCpuManager() {
    }

    /** 把一个 CraftingCPUCluster 当作子 CPU 句柄访问（mixin 在运行时会实现 SplitCpuHandle）。 */
    private static SplitCpuHandle handle(CraftingCPUCluster cpu) {
        return (SplitCpuHandle) (Object) cpu;
    }

    /** 该集群是否由本模组创建的子 CPU（mixin 未生效时恒为 false，即退化为原版行为）。 */
    public static boolean isSplit(CraftingCPUCluster cpu) {
        // CraftingCPUCluster 是 final 且编译期未声明实现该接口，必须先转成 Object 再做 instanceof。
        return cpu != null && (Object) cpu instanceof SplitCpuHandle h && h.ae2split$isSplit();
    }

    /** 汇总某个主 CPU 下所有子 CPU 预留的字节数；没有子 CPU 时返回 0。 */
    public static long reservedBytes(CraftingCPUCluster parent) {
        List<CraftingCPUCluster> children = CHILDREN.get(parent);
        if (children == null || children.isEmpty()) {
            return 0L;
        }
        long sum = 0L;
        for (CraftingCPUCluster child : children) {
            if (child != null) {
                sum += handle(child).ae2split$getSplitBytes();
            }
        }
        return sum;
    }

    /** 汇总某个主 CPU 下所有子 CPU 已分配走的并行数；没有子 CPU 时返回 0。 */
    public static int reservedThreads(CraftingCPUCluster parent) {
        List<CraftingCPUCluster> children = CHILDREN.get(parent);
        if (children == null || children.isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (CraftingCPUCluster child : children) {
            if (child != null) {
                sum += handle(child).ae2split$getSplitThreads();
            }
        }
        return sum;
    }

    /**
     * 尝试为主 CPU 创建一个子 CPU 并接管当前订单。
     *
     * @return 创建并注册成功的子 CPU；不满足分裂条件时返回 null（调用方应放行原版逻辑）。
     */
    public static CraftingCPUCluster createSplitCpu(CraftingCPUCluster parent, ICraftingPlan plan) {
        if (parent == null || plan == null) {
            return null;
        }
        // 子 CPU 上绝不再分裂，防止递归；已拆解的主 CPU 也不再分裂。
        if (isSplit(parent) || parent.isDestroyed()) {
            return null;
        }
        // 模拟用的 plan 不真正执行，不值得为它创建子 CPU；0 字节的 plan 也没有分裂价值。
        if (plan.simulation() || plan.bytes() <= 0L) {
            return null;
        }

        final boolean enabled;
        final boolean requireComponent;
        final int maxSplits;
        try {
            enabled = AE2SplitConfig.ENABLE_SPLITTING.get();
            requireComponent = AE2SplitConfig.REQUIRE_SPLIT_COMPONENT.get();
            maxSplits = AE2SplitConfig.MAX_SPLITS.get();
        } catch (IllegalStateException e) {
            // 配置尚未加载（例如世界加载早期）时安全降级为"不分裂"。
            return null;
        }
        if (!enabled || maxSplits <= 0) {
            return null;
        }

        List<CraftingCPUCluster> children = CHILDREN.get(parent);
        int used = children == null ? 0 : children.size();
        if (used >= maxSplits) {
            return null;
        }
        if (requireComponent && !hasSplitComponent(parent)) {
            return null;
        }

        // 剩余字节：mixin 已经扣掉了既有子 CPU 的预留。
        long remaining = parent.getAvailableStorage();
        if (remaining <= 0L || plan.bytes() > remaining) {
            return null;
        }

        try {
            int availThreads = Math.max(0, parent.getCoProcessors());
            // 并行已被前面的子 CPU 瓜分完时不再分裂：0 并行的子 CPU 接单后会一直卡住。
            if (availThreads < 1) {
                return null;
            }
            int threads = allocateThreads(availThreads, plan.bytes(), remaining, used, maxSplits);

            int index = used;
            Component name = buildName(parent, index);

            CraftingCPUCluster child = new CraftingCPUCluster(parent.getBoundsMin(), parent.getBoundsMax());
            // 先写入父 CPU 与预留信息，再让子 CPU 参与任何逻辑（readFromNBT / 任务执行都依赖这些字段）。
            handle(child).ae2split$initSplit(parent, plan.bytes(), threads, name);

            CHILDREN.computeIfAbsent(parent, key -> new ArrayList<>()).add(child);
            registerToService(parent, child);
            // 便于进游戏后确认分裂确实发生（默认日志级别下不输出）。
            LOGGER.debug("AE2 Split CPU: {} split off sub-CPU #{} ({} bytes, {} threads)",
                    parent.getBoundsMin(), index + 1, plan.bytes(), threads);
            return child;
        } catch (RuntimeException e) {
            LOGGER.warn("AE2 Split CPU: failed to create sub-CPU for {}", parent.getBoundsMin(), e);
            return null;
        }
    }

    /** 按配置的并行方案计算子 CPU 应分到的线程数，并夹到 [0, avail] 与 minParallel 下限之间。 */
    private static int allocateThreads(int availThreads, long planBytes, long remainingBytes, int used, int maxSplits) {
        if (availThreads <= 0) {
            return 0;
        }
        final ParallelScheme scheme;
        final int fixed;
        final int minParallel;
        try {
            scheme = AE2SplitConfig.PARALLEL_SCHEME.get();
            fixed = AE2SplitConfig.FIXED_PARALLEL.get();
            minParallel = AE2SplitConfig.MIN_PARALLEL.get();
        } catch (IllegalStateException e) {
            return 0;
        }

        long raw = switch (scheme) {
            case PROPORTIONAL -> (long) Math.floor((double) availThreads * (double) planBytes
                    / (double) Math.max(1L, remainingBytes));
            case EQUAL -> {
                int slots = Math.max(1, maxSplits - used);
                yield (availThreads + (long) slots - 1L) / slots;
            }
            case ALL -> availThreads;
            case FIXED -> fixed;
        };

        int threads = (int) Math.max(0L, Math.min(raw, availThreads));
        if (minParallel > 0) {
            threads = Math.max(threads, Math.min(minParallel, availThreads));
        }
        return Math.min(threads, availThreads);
    }

    /** 子 CPU 显示名：主 CPU 有名字则在后面追加序号，否则用 "Split #N"。 */
    private static Component buildName(CraftingCPUCluster parent, int index) {
        Component base = null;
        try {
            base = parent.getName();
        } catch (RuntimeException ignored) {
            // 没有核心方块时 getName 也可能意外失败，退回默认名字。
        }
        return base != null
                ? base.copy().append(" #" + (index + 1))
                : Component.literal("Split #" + (index + 1));
    }

    /** 主 CPU 的多方块结构里是否含有本模组的"分裂元件"方块。 */
    private static boolean hasSplitComponent(CraftingCPUCluster parent) {
        try {
            Iterator<CraftingBlockEntity> it = parent.getBlockEntities();
            while (it.hasNext()) {
                CraftingBlockEntity be = it.next();
                if (be != null && be.getBlockState().getBlock() instanceof SplitComponentBlock) {
                    return true;
                }
            }
        } catch (RuntimeException e) {
            LOGGER.warn("AE2 Split CPU: failed to scan block entities for split component", e);
        }
        return false;
    }

    /** 把子 CPU 加进父 CPU 所在网格的 CraftingService 集合（拿不到网格时静默跳过，由 reattach 兜底）。 */
    private static void registerToService(CraftingCPUCluster parent, CraftingCPUCluster child) {
        IGrid grid = safeGrid(parent);
        if (grid == null) {
            return;
        }
        try {
            ICraftingService service = grid.getCraftingService();
            if (service instanceof SplitCpuAccess access) {
                access.ae2split$addCpu(child);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("AE2 Split CPU: failed to register sub-CPU into crafting service", e);
        }
    }

    /** 安全地取主 CPU 的网格：拆解中 / 无核心方块时返回 null 而不是抛异常。 */
    private static IGrid safeGrid(CraftingCPUCluster cpu) {
        if (cpu == null) {
            return null;
        }
        try {
            return cpu.getGrid();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 销毁一个子 CPU：从管理器和 CraftingService 集合中移除，并把它标记为 destroyed。
     * 不会调用 cancel（订单失败时本来就没有任务）。
     */
    public static void dissolve(CraftingCPUCluster child) {
        if (child == null || !((Object) child instanceof SplitCpuHandle h)) {
            return;
        }
        CraftingCPUCluster parent = h.ae2split$getParent();
        if (parent != null) {
            removeFromParentList(parent, child);
            IGrid grid = safeGrid(parent);
            if (grid != null) {
                try {
                    ICraftingService service = grid.getCraftingService();
                    if (service instanceof SplitCpuAccess access) {
                        access.ae2split$removeCpu(child);
                    }
                } catch (RuntimeException e) {
                    LOGGER.warn("AE2 Split CPU: failed to remove sub-CPU from crafting service", e);
                }
            }
        }
        h.ae2split$markDestroyed();
    }

    /** 外部（例如有人直接 destroy 子 CPU）触发的销毁：先取消任务，再走 dissolve 的清理流程。 */
    public static void onChildDestroyed(CraftingCPUCluster child) {
        if (child == null || !isSplit(child)) {
            return;
        }
        try {
            child.craftingLogic.cancel();
        } catch (RuntimeException e) {
            LOGGER.warn("AE2 Split CPU: failed to cancel job of destroyed sub-CPU", e);
        }
        dissolve(child);
    }

    /** 从某个主 CPU 的子列表里移除指定子 CPU，并在列表为空时删掉整个映射项。 */
    private static void removeFromParentList(CraftingCPUCluster parent, CraftingCPUCluster child) {
        List<CraftingCPUCluster> children = CHILDREN.get(parent);
        if (children == null) {
            return;
        }
        children.remove(child);
        if (children.isEmpty()) {
            CHILDREN.remove(parent);
        }
    }

    /**
     * 每 tick 结束时的回收：没有任务且本地库存为空的子 CPU 视为结束，销毁并释放预留的字节 / 并行。
     * 遍历副本，避免遍历过程中修改集合。
     */
    public static void endOfTick(CraftingService service) {
        if (service == null || CHILDREN.isEmpty()) {
            return;
        }
        List<CraftingCPUCluster> snapshot = new ArrayList<>();
        for (List<CraftingCPUCluster> children : CHILDREN.values()) {
            snapshot.addAll(children);
        }
        for (CraftingCPUCluster child : snapshot) {
            if (child == null) {
                continue;
            }
            try {
                if (!child.craftingLogic.hasJob() && child.craftingLogic.getInventory().list.isEmpty()) {
                    CraftingCPUCluster parent = handle(child).ae2split$getParent();
                    if (parent != null) {
                        removeFromParentList(parent, child);
                    }
                    if (service instanceof SplitCpuAccess access) {
                        access.ae2split$removeCpu(child);
                    }
                    handle(child).ae2split$markDestroyed();
                }
            } catch (RuntimeException e) {
                LOGGER.warn("AE2 Split CPU: failed to reclaim finished sub-CPU", e);
            }
        }
    }

    /**
     * CraftingService.updateCPUClusters 重建集合后，把仍然有效的子 CPU 重新塞回集合，
     * 并为带任务的子 CPU 重新登记它的 CraftingLink（否则订单会失去连接而卡住）。
     */
    public static void reattach(CraftingService service, Set<CraftingCPUCluster> cpus) {
        if (service == null || cpus == null || CHILDREN.isEmpty()) {
            return;
        }
        // 顺手清理主 CPU 已经被拆解、但管理器里还残留的子 CPU，避免集合里出现"孤儿"。
        for (Iterator<Map.Entry<CraftingCPUCluster, List<CraftingCPUCluster>>> it = CHILDREN.entrySet()
                .iterator(); it.hasNext();) {
            Map.Entry<CraftingCPUCluster, List<CraftingCPUCluster>> entry = it.next();
            CraftingCPUCluster owner = entry.getKey();
            if (owner == null || owner.isDestroyed()) {
                for (CraftingCPUCluster orphan : entry.getValue()) {
                    if (orphan != null) {
                        handle(orphan).ae2split$markDestroyed();
                    }
                }
                it.remove();
            }
        }
        for (CraftingCPUCluster parent : new ArrayList<>(cpus)) {
            if (parent == null) {
                continue;
            }
            List<CraftingCPUCluster> children = CHILDREN.get(parent);
            if (children == null || children.isEmpty()) {
                continue;
            }
            for (CraftingCPUCluster child : new ArrayList<>(children)) {
                if (child == null) {
                    continue;
                }
                try {
                    cpus.add(child);
                    if (child.craftingLogic.getLastLink() instanceof CraftingLink link) {
                        service.addLink(link);
                    }
                } catch (RuntimeException e) {
                    LOGGER.warn("AE2 Split CPU: failed to reattach sub-CPU", e);
                }
            }
        }
    }

    /**
     * 主 CPU 被拆解 / 集群解散时调用：取消所有子任务、从集合移除、标记 destroyed 并清空记录。
     */
    public static void destroyChildren(CraftingCPUCluster parent) {
        if (parent == null) {
            return;
        }
        List<CraftingCPUCluster> children = CHILDREN.remove(parent);
        if (children == null || children.isEmpty()) {
            return;
        }
        IGrid grid = safeGrid(parent);
        for (CraftingCPUCluster child : new ArrayList<>(children)) {
            if (child == null) {
                continue;
            }
            try {
                child.craftingLogic.cancel();
            } catch (RuntimeException e) {
                LOGGER.warn("AE2 Split CPU: failed to cancel sub-CPU job while dissolving", e);
            }
            if (grid != null) {
                try {
                    ICraftingService service = grid.getCraftingService();
                    if (service instanceof SplitCpuAccess access) {
                        access.ae2split$removeCpu(child);
                    }
                } catch (RuntimeException e) {
                    LOGGER.warn("AE2 Split CPU: failed to detach sub-CPU while dissolving", e);
                }
            }
            handle(child).ae2split$markDestroyed();
        }
        children.clear();
    }

    /**
     * 把主 CPU 的子 CPU 列表写进它的 NBT（只写一层：子 CPU 自己不会再写子 CPU）。
     * 每项结构：{Bytes:long, Threads:int, Index:int, Logic:CompoundTag}。
     */
    public static void writeChildren(CraftingCPUCluster parent, CompoundTag data, HolderLookup.Provider registries) {
        if (parent == null || data == null || isSplit(parent)) {
            return;
        }
        List<CraftingCPUCluster> children = CHILDREN.get(parent);
        if (children == null || children.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        for (int i = 0; i < children.size(); i++) {
            CraftingCPUCluster child = children.get(i);
            if (child == null) {
                continue;
            }
            try {
                CompoundTag entry = new CompoundTag();
                entry.putLong("Bytes", handle(child).ae2split$getSplitBytes());
                entry.putInt("Threads", handle(child).ae2split$getSplitThreads());
                entry.putInt("Index", i);
                CompoundTag logic = new CompoundTag();
                child.writeToNBT(logic, registries);
                entry.put("Logic", logic);
                list.add(entry);
            } catch (RuntimeException e) {
                LOGGER.warn("AE2 Split CPU: failed to serialize sub-CPU, skipped", e);
            }
        }
        if (!list.isEmpty()) {
            data.put(TAG_CHILDREN, list);
        }
    }

    /**
     * 读档时重建主 CPU 的子 CPU。会先清掉内存里可能残留的旧子 CPU（避免重复），
     * 再逐个 new + 初始化 + 反序列化；任何单个子 CPU 失败都只丢弃该项，不影响主 CPU 读档。
     */
    public static void readChildren(CraftingCPUCluster parent, CompoundTag data, HolderLookup.Provider registries) {
        if (parent == null || data == null || isSplit(parent)) {
            return;
        }
        if (!data.contains(TAG_CHILDREN, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = data.getList(TAG_CHILDREN, Tag.TAG_LIST);
        if (list.isEmpty()) {
            return;
        }

        // 先清掉旧的所有子 CPU（重读同一集群时可能残留）。
        destroyChildren(parent);

        IGrid grid = safeGrid(parent);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            long bytes = entry.getLong("Bytes");
            int threads = entry.getInt("Threads");
            int index = entry.getInt("Index");
            CraftingCPUCluster child = null;
            try {
                child = new CraftingCPUCluster(parent.getBoundsMin(), parent.getBoundsMax());
                // 必须先初始化，readFromNBT 期间子 CPU 的 getGrid()/updateOutput() 都会委托回主 CPU。
                handle(child).ae2split$initSplit(parent, bytes, threads, buildName(parent, index));
                child.readFromNBT(entry.getCompound("Logic"), registries);
            } catch (RuntimeException e) {
                LOGGER.warn("AE2 Split CPU: failed to restore sub-CPU #{}, dropped", i, e);
                if (child != null) {
                    handle(child).ae2split$markDestroyed();
                }
                continue;
            }
            CHILDREN.computeIfAbsent(parent, key -> new ArrayList<>()).add(child);
        }

        // 读档发生在 updateCPUClusters 之外，这里能拿到网格就顺手登记；拿不到则由 reattach 补。
        List<CraftingCPUCluster> restored = CHILDREN.get(parent);
        if (restored != null && grid != null) {
            try {
                ICraftingService service = grid.getCraftingService();
                if (service instanceof SplitCpuAccess access) {
                    for (CraftingCPUCluster child : restored) {
                        access.ae2split$addCpu(child);
                    }
                }
            } catch (RuntimeException e) {
                LOGGER.warn("AE2 Split CPU: failed to register restored sub-CPUs", e);
            }
        }
    }
}
