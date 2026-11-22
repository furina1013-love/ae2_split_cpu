package cn.furina.ae2split;

import net.minecraftforge.common.ForgeConfigSpec;

import cn.furina.ae2split.split.ParallelScheme;

/**
 * COMMON 配置（config/ae2_split_cpu-common.toml）的键与默认值定义。
 * 只定义 SPEC 与取值句柄，不做任何业务判断；业务侧按需调用 xxx.get()，
 * 注意 ConfigValue.get() 只能在配置加载完成后（服务端/客户端启动阶段之后）调用。
 */
public final class AE2SplitConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /** 总开关：关闭后 CPU 永远不分裂，行为等同原版 AE2。 */
    public static final ForgeConfigSpec.BooleanValue ENABLE_SPLITTING;
    /** 是否要求 CPU 结构中包含"分裂元件"方块才允许分裂。 */
    public static final ForgeConfigSpec.BooleanValue REQUIRE_SPLIT_COMPONENT;
    /** 每个主 CPU 最多分裂出多少个子 CPU（不含主 CPU 自身）。 */
    public static final ForgeConfigSpec.IntValue MAX_SPLITS;
    /** 并行分配方案。 */
    public static final ForgeConfigSpec.EnumValue<ParallelScheme> PARALLEL_SCHEME;
    /** FIXED 方案下每个子 CPU 固定拿到的并行数。 */
    public static final ForgeConfigSpec.IntValue FIXED_PARALLEL;
    /** 任何方案下子 CPU 至少拿到的并行数（0 表示允许不分配并行）。 */
    public static final ForgeConfigSpec.IntValue MIN_PARALLEL;

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.comment("AE2 Split CPU settings / AE2 分裂 CPU 设置").push("split");

        ENABLE_SPLITTING = BUILDER
                .comment("Master switch. When false, crafting CPUs never split. / 总开关：关闭后 CPU 永不分裂。")
                .define("enableSplitting", true);

        REQUIRE_SPLIT_COMPONENT = BUILDER
                .comment("Require the CPU multiblock to contain at least one split component block. / 是否要求 CPU 结构中包含分裂元件。")
                .define("requireSplitComponent", true);

        MAX_SPLITS = BUILDER
                .comment("Maximum number of virtual sub-CPUs per crafting CPU (0 disables splitting). / 每个主 CPU 最多分裂出的子 CPU 数量（0 表示禁止分裂）。")
                .defineInRange("maxSplits", 3, 0, 64);

        PARALLEL_SCHEME = BUILDER
                .comment("How co-processor threads are distributed to sub-CPUs: EQUAL (default, even share) / PROPORTIONAL / ALL / FIXED. / 并行分配方案：EQUAL(默认，平均分) / PROPORTIONAL(按字节比例) / ALL / FIXED。")
                .defineEnum("parallelScheme", ParallelScheme.EQUAL);

        FIXED_PARALLEL = BUILDER
                .comment("Threads per sub-CPU when parallelScheme = FIXED. / FIXED 方案下每个子 CPU 的并行数。")
                .defineInRange("fixedParallel", 1, 0, 64);

        MIN_PARALLEL = BUILDER
                .comment("Lower bound of threads for any scheme. / 任何方案下子 CPU 的并行下限。")
                .defineInRange("minParallel", 1, 0, 64);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private AE2SplitConfig() {
    }
}
