package cn.furina.ae2split;

import net.neoforged.neoforge.common.ModConfigSpec;

import cn.furina.ae2split.split.ParallelScheme;

/**
 * COMMON 配置（config/ae2_split_cpu-common.toml）的键与默认值定义。
 * 只定义 SPEC 与取值句柄，不做任何业务判断；业务侧按需调用 xxx.get()，
 * 注意 ConfigValue.get() 只能在配置加载完成后（服务端/客户端启动阶段之后）调用。
 */
public final class AE2SplitConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** 总开关：关闭后 CPU 永远不分裂，行为等同原版 AE2。 */
    public static final ModConfigSpec.BooleanValue ENABLE_SPLITTING;
    /** 是否要求 CPU 结构中包含"分裂元件"方块才允许分裂。 */
    public static final ModConfigSpec.BooleanValue REQUIRE_SPLIT_COMPONENT;
    /** 每个主 CPU 最多分裂出多少个子 CPU（不含主 CPU 自身）。 */
    public static final ModConfigSpec.IntValue MAX_SPLITS;
    /** 并行分配方案。 */
    public static final ModConfigSpec.EnumValue<ParallelScheme> PARALLEL_SCHEME;
    /** FIXED 方案下每个子 CPU 固定拿到的并行数。 */
    public static final ModConfigSpec.IntValue FIXED_PARALLEL;
    /** 任何方案下子 CPU 至少拿到的并行数（0 表示允许不分配并行）。 */
    public static final ModConfigSpec.IntValue MIN_PARALLEL;
    /** 循环核心自身提供的存储字节；>0 时仅含循环核心的 CPU 也能成型并连网。 */
    public static final ModConfigSpec.IntValue LOOP_CORE_STORAGE;
    /** 执行循环配方是否必须由含循环核心的 CPU 执行。 */
    public static final ModConfigSpec.BooleanValue REQUIRE_LOOP_CORE;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("AE2 Split CPU settings / AE2 分裂 CPU 设置").push("split");

        ENABLE_SPLITTING = BUILDER
                .comment("Master switch. When false, crafting CPUs never split. / 总开关：关闭后 CPU 永不分裂。")
                .translation("ae2_split_cpu.configuration.split.enableSplitting")
                .define("enableSplitting", true);

        REQUIRE_SPLIT_COMPONENT = BUILDER
                .comment("Require the CPU multiblock to contain at least one split component block. / 是否要求 CPU 结构中包含分裂元件。")
                .translation("ae2_split_cpu.configuration.split.requireSplitComponent")
                .define("requireSplitComponent", true);

        MAX_SPLITS = BUILDER
                .comment("Maximum number of virtual sub-CPUs per crafting CPU (0 disables splitting). / 每个主 CPU 最多分裂出的子 CPU 数量（0 表示禁止分裂）。")
                .translation("ae2_split_cpu.configuration.split.maxSplits")
                .defineInRange("maxSplits", 3, 0, 64);

        PARALLEL_SCHEME = BUILDER
                .comment("How co-processor threads are distributed to sub-CPUs: EQUAL (default, even share) / PROPORTIONAL / ALL / FIXED. / 并行分配方案：EQUAL(默认，平均分) / PROPORTIONAL(按字节比例) / ALL / FIXED。")
                .translation("ae2_split_cpu.configuration.split.parallelScheme")
                .defineEnum("parallelScheme", ParallelScheme.EQUAL);

        FIXED_PARALLEL = BUILDER
                .comment("Threads per sub-CPU when parallelScheme = FIXED. / FIXED 方案下每个子 CPU 的并行数。")
                .translation("ae2_split_cpu.configuration.split.fixedParallel")
                .defineInRange("fixedParallel", 1, 0, 64);

        MIN_PARALLEL = BUILDER
                .comment("Lower bound of threads for any scheme. / 任何方案下子 CPU 的并行下限。")
                .translation("ae2_split_cpu.configuration.split.minParallel")
                .defineInRange("minParallel", 1, 0, 64);

        LOOP_CORE_STORAGE = BUILDER
                .comment("Storage bytes provided by one Loop Core. Default 0: the Loop Core is a gate, not storage; the CPU still needs a real storage unit. Set >0 only if you want a marker-only CPU to form. / 循环核心自带的存储字节。默认 0：它只是执行循环配方的门槛，不承担存储，CPU 仍需真正的 storage 单元；设为 >0 仅用于让纯标记 CPU 也能成型。")
                .translation("ae2_split_cpu.configuration.split.loopCoreStorageBytes")
                .defineInRange("loopCoreStorageBytes", 0, 0, 1024);

        REQUIRE_LOOP_CORE = BUILDER
                .comment("Require the executing CPU to contain a Loop Core before it may run loop recipes. / 执行循环配方是否要求目标 CPU 内含有循环核心。")
                .translation("ae2_split_cpu.configuration.split.requireLoopCore")
                .define("requireLoopCore", true);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private AE2SplitConfig() {
    }
}
