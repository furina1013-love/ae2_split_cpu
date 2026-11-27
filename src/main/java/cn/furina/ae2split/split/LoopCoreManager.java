package cn.furina.ae2split.split;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import cn.furina.ae2split.content.LoopCoreBlock;

/**
 * 循环核心（Loop Core）的判定与“循环种子”注入。
 *
 * <p>AE2 默认不支持自引用配方：{@code a + b -> 2a} 这类配方在规划时会被
 * {@code CraftingTreeNode.notRecursive} 排除，规划器报“缺料”。循环核心的作用是把这种配方
 * 当成“净配方”处理：每执行一次，净产出 {@code +1a}、净消耗 {@code +1b}；执行时只需要 1 个 a
 * 作为“种子”在 CPU 内部循环使用（用完回到 CPU 库存，任务结束后随余料退回网络）。</p>
 *
 * <p>规划器改造在 mixin 里完成（CraftingTreeNode / CraftingTreeProcess / CraftingCalculation），
 * 本类只提供纯函数：配方净产出分析、CPU/网格是否含循环核心、下单时补种子。</p>
 *
 * <p>线程说明：{@link #analyze} 可能在合成计算线程被调用，因此只用不可变的配方数据并做缓存；
 * {@link #addLoopSeeds} / {@link #hasLoopCore} 只在服务端主线程调用。</p>
 */
public final class LoopCoreManager {

    /** 单个配方按“一次执行”计算的净收支。 */
    public record PatternInfo(Map<AEKey, Long> netPerCraft,
            Set<AEKey> recycled,
            Set<AEKey> seeds) {

        public static final PatternInfo NONE = new PatternInfo(Map.of(), Set.of(), Set.of());

        /** 该配方是否是“自循环配方”（至少一个输入同时是输出）。 */
        public boolean isLoop() {
            return !recycled.isEmpty();
        }

        /** 一次执行对 key 的净产出；负数表示净消耗。 */
        public long netFor(AEKey key) {
            Long value = netPerCraft.get(key);
            return value == null ? 0L : value;
        }

        /** 该配方能否为 key 提供净产出（正收益），用于放宽 notRecursive。 */
        public boolean supplies(AEKey key) {
            return netFor(key) > 0L && recycled.contains(key);
        }
    }

    /** 配方分析缓存（配方对象基本不变，用弱引用避免长期占用）。 */
    private static final Map<IPatternDetails, PatternInfo> CACHE = Collections
            .synchronizedMap(new WeakHashMap<>());

    /** 已经补过循环种子的 plan（同一 plan 可能被重试提交，避免重复扣种子）。 */
    private static final Map<ICraftingPlan, Boolean> SEEDED = Collections
            .synchronizedMap(new WeakHashMap<>());

    private LoopCoreManager() {
    }

    /** 分析一个配方每次执行时的净收支。 */
    public static PatternInfo analyze(@Nullable IPatternDetails details) {
        if (details == null) {
            return PatternInfo.NONE;
        }
        PatternInfo cached = CACHE.get(details);
        if (cached != null) {
            return cached;
        }
        PatternInfo info = compute(details);
        CACHE.put(details, info);
        return info;
    }

    private static PatternInfo compute(IPatternDetails details) {
        Map<AEKey, Long> consumed = new LinkedHashMap<>();
        Map<AEKey, Long> produced = new LinkedHashMap<>();

        for (IPatternDetails.IInput input : details.getInputs()) {
            GenericStack[] possible = input.getPossibleInputs();
            if (possible == null || possible.length == 0 || possible[0] == null) {
                continue;
            }
            GenericStack primary = possible[0];
            long amount = safeMultiply(primary.amount(), Math.max(1L, input.getMultiplier()));
            if (amount > 0L) {
                consumed.merge(primary.what(), amount, LoopCoreManager::safeAdd);
            }
        }
        for (GenericStack output : details.getOutputs()) {
            if (output != null && output.amount() > 0L) {
                produced.merge(output.what(), output.amount(), LoopCoreManager::safeAdd);
            }
        }

        if (consumed.isEmpty() || produced.isEmpty()) {
            return PatternInfo.NONE;
        }

        Set<AEKey> keys = new LinkedHashSet<>(consumed.keySet());
        keys.addAll(produced.keySet());

        Map<AEKey, Long> net = new LinkedHashMap<>();
        Set<AEKey> recycled = new LinkedHashSet<>();
        Set<AEKey> seeds = new LinkedHashSet<>();
        for (AEKey key : keys) {
            long in = consumed.getOrDefault(key, 0L);
            long out = produced.getOrDefault(key, 0L);
            long diff = safeAdd(out, -in);
            if (diff != 0L) {
                net.put(key, diff);
            }
            // 输入同时被输出覆盖：执行时不需要真的消耗掉，循环复用即可。
            if (in > 0L && out > 0L && diff >= 0L) {
                recycled.add(key);
                seeds.add(key);
            }
        }
        if (recycled.isEmpty()) {
            return PatternInfo.NONE;
        }
        return new PatternInfo(Map.copyOf(net), Set.copyOf(recycled), Set.copyOf(seeds));
    }

    private static long safeMultiply(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private static long safeAdd(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException e) {
            return b >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    /** 该 CPU 多方块里是否含循环核心。 */
    public static boolean hasLoopCore(@Nullable CraftingCPUCluster cpu) {
        if (cpu == null) {
            return false;
        }
        try {
            var it = cpu.getBlockEntities();
            while (it.hasNext()) {
                CraftingBlockEntity be = it.next();
                if (be != null && be.getBlockState().getBlock() instanceof LoopCoreBlock) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            // 拆解中的 CPU 可能抛异常：按“没有循环核心”处理。
        }
        return false;
    }

    /** 该网格里是否至少有一个成型的、含循环核心的 CPU。 */
    public static boolean gridHasLoopCore(@Nullable IGrid grid) {
        if (grid == null) {
            return false;
        }
        try {
            ICraftingService service = grid.getCraftingService();
            if (service == null) {
                return false;
            }
            for (ICraftingCPU cpu : service.getCpus()) {
                if (cpu instanceof CraftingCPUCluster cluster && hasLoopCore(cluster)) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            // 网格尚未就绪时按“没有循环核心”处理。
        }
        return false;
    }

    /** 计划里所有循环配方需要的“种子”（每个 key 一对）。 */
    public static Set<AEKey> loopSeeds(@Nullable ICraftingPlan plan) {
        if (plan == null || plan.simulation() || !plan.missingItems().isEmpty()) {
            return Set.of();
        }
        Set<AEKey> seeds = new LinkedHashSet<>();
        for (Map.Entry<IPatternDetails, Long> entry : plan.patternTimes().entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0L) {
                continue;
            }
            PatternInfo info = analyze(entry.getKey());
            if (info.isLoop()) {
                seeds.addAll(info.seeds());
            }
        }
        return seeds;
    }

    /**
     * 下单前给计划补“循环种子”：把种子加进 {@code usedItems}，CPU 提交任务时会把它从网络
     * 抽进自己的库存；任务结束后余料（含种子）由 AE2 自动退回网络。
     */
    public static void addLoopSeeds(@Nullable IGrid grid, @Nullable ICraftingPlan plan) {
        if (plan == null || !gridHasLoopCore(grid)) {
            return;
        }
        Set<AEKey> seeds = loopSeeds(plan);
        if (seeds.isEmpty()) {
            return;
        }
        if (SEEDED.putIfAbsent(plan, Boolean.TRUE) != null) {
            return;
        }
        KeyCounter used = plan.usedItems();
        for (AEKey seed : seeds) {
            used.add(seed, 1L);
        }
    }
}
