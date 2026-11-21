package cn.furina.ae2split.split;

/**
 * 子 CPU 的并行（协同处理线程）分配方案，对应配置项 split.parallelScheme。
 * 具体计算在下一阶段的 SplitCpuManager 中实现，本枚举只提供取值。
 */
public enum ParallelScheme {
    /** 按订单字节占剩余字节的比例分配：floor(剩余并行 * 订单字节 / 剩余字节)。 */
    PROPORTIONAL,
    /** 在剩余可分裂名额之间均分：ceil(剩余并行 / 剩余名额)。 */
    EQUAL,
    /** 剩余并行全部给该子 CPU。 */
    ALL,
    /** 固定为配置项 fixedParallel。 */
    FIXED
}
