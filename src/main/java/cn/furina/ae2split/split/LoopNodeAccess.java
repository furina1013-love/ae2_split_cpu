package cn.furina.ae2split.split;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.inv.CraftingSimulationState;

/** 由 CraftingTreeNodeMixin 实现：把包私有的节点信息/请求方法暴露给循环配方逻辑。 */
public interface LoopNodeAccess {

    AEKey ae2split$what();

    /** 调用节点的原始 request（包私有，只能由同包的 mixin 桥接）。 */
    void ae2split$requestNode(CraftingSimulationState inv, long requestedAmount, KeyCounter containerItems)
            throws CraftBranchFailure, InterruptedException;
}
