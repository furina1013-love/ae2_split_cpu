package cn.furina.ae2split.split;

import appeng.api.stacks.AEKey;

/** 由 CraftingCalculationMixin 实现：本次合成计算是否开启了循环配方，以及网络里是否有种子。 */
public interface LoopCalcAccess {

    boolean ae2split$isLoopEnabled();

    /** 计算开始时网络里是否至少有 amount 个 key（用于判断循环种子是否就绪）。 */
    boolean ae2split$hasStock(AEKey key, long amount);
}
