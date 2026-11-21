package cn.furina.ae2split.content;

import net.minecraft.world.item.Item;

import appeng.block.crafting.ICraftingUnitType;

import cn.furina.ae2split.AE2SplitBlocks;

/**
 * 分裂元件的"合成单元类型"：本身不提供存储字节，也不提供并行线程，
 * 它只是一个标记块——CPU 结构里含有它，才允许该 CPU 在配置开启时分裂。
 * getItemFromType 返回本模组的方块物品（AE2 内部用它做升级/移除时的物品替换）。
 */
public class SplitComponentType implements ICraftingUnitType {

    @Override
    public long getStorageBytes() {
        // 不提供存储：分裂元件只做标记。
        return 0;
    }

    @Override
    public int getAcceleratorThreads() {
        // 不提供并行：并行由主 CPU 的加速器与配置方案决定。
        return 0;
    }

    @Override
    public Item getItemFromType() {
        // 延迟调用，此时注册表已就绪。
        return AE2SplitBlocks.SPLIT_COMPONENT_ITEM.get();
    }
}
