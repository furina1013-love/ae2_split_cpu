package cn.furina.ae2split.content;

import net.minecraft.world.item.Item;

import appeng.block.crafting.ICraftingUnitType;

import cn.furina.ae2split.AE2SplitBlocks;

/**
 * 循环核心的"合成单元类型"：不提供存储字节，也不提供并行线程，
 * 它只是一个标记块——CPU 结构里含有循环核心时，规划器才按循环配方处理。
 * getItemFromType 返回本模组的方块物品（AE2 内部用它做升级/移除时的物品替换）。
 */
public class LoopCoreType implements ICraftingUnitType {

    @Override
    public long getStorageBytes() {
        // 不提供存储：循环核心只做标记。
        return 0;
    }

    @Override
    public int getAcceleratorThreads() {
        // 不提供并行：并行由 CPU 的加速器与配置方案决定。
        return 0;
    }

    @Override
    public Item getItemFromType() {
        // 延迟调用，此时注册表已就绪。
        return AE2SplitBlocks.LOOP_CORE_ITEM.get();
    }
}
