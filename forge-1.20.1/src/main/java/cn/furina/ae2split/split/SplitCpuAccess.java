package cn.furina.ae2split.split;

import appeng.me.cluster.implementations.CraftingCPUCluster;

/**
 * 由 {@link CraftingServiceMixin} 实现（mixin 会在运行时把该接口挂到 AE2 的 CraftingService 上）。
 * 分裂引擎通过这个接口往 CraftingService 的 craftingCPUClusters 集合里增删"幽灵"子 CPU，
 * 而不需要访问它的私有字段；这样即使 AE2 改了内部字段名，只要还有这个集合就能工作。
 */
public interface SplitCpuAccess {

    /** 把一个子 CPU 加入 CraftingService 的 CPU 集合（使其出现在 getCpus() / 界面 / 计算中）。 */
    void ae2split$addCpu(CraftingCPUCluster cpu);

    /** 把一个子 CPU 从 CraftingService 的 CPU 集合移除（任务结束 / 主机拆解时调用）。 */
    void ae2split$removeCpu(CraftingCPUCluster cpu);
}
