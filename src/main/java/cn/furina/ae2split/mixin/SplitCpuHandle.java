package cn.furina.ae2split.mixin;

import net.minecraft.network.chat.Component;

import appeng.me.cluster.implementations.CraftingCPUCluster;

/**
 * 由 {@link CraftingCPUClusterMixin} 实现（mixin 会把该接口挂到 AE2 的 CraftingCPUCluster 上）。
 * 因为 CraftingCPUCluster 是 final 且在编译期看不到 mixin 注入的 @Unique 成员，
 * 分裂引擎只能通过这个接口来初始化 / 读取 / 销毁子 CPU 的私有分裂状态。
 */
public interface SplitCpuHandle {

    /**
     * 把该集群标记为"某个主 CPU 的子 CPU"，并写入预留字节、分配并行与显示名。
     * 只在创建子 CPU（下单分裂 / 读档重建）时调用一次。
     */
    void ae2split$initSplit(CraftingCPUCluster parent, long bytes, int threads, Component name);

    /** 是否为子 CPU（parent != null）。 */
    boolean ae2split$isSplit();

    /** 子 CPU 的主集群；主 CPU 返回 null。 */
    CraftingCPUCluster ae2split$getParent();

    /** 子 CPU 预留（占用）的字节数；主 CPU 无意义。 */
    long ae2split$getSplitBytes();

    /** 子 CPU 分配到的并行（协同处理线程）数；主 CPU 无意义。 */
    int ae2split$getSplitThreads();

    /** 直接把 isDestroyed 置 true（不触发 AE2 的 destroy 逻辑，避免递归）。 */
    void ae2split$markDestroyed();
}
