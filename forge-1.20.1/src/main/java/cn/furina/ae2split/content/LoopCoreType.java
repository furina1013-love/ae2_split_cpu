package cn.furina.ae2split.content;

import net.minecraft.world.item.Item;

import appeng.block.crafting.ICraftingUnitType;

import cn.furina.ae2split.AE2SplitBlocks;
import cn.furina.ae2split.AE2SplitConfig;

/**
 * 循环核心的"合成单元类型"：不提供存储字节，也不提供并行线程，
 * 它只是一个标记块——CPU 结构里含有循环核心时，规划器才按循环配方处理。
 * getItemFromType 返回本模组的方块物品（AE2 内部用它做升级/移除时的物品替换）。
 */
public class LoopCoreType implements ICraftingUnitType {

    @Override
    public long getStorageBytes() {
        // 默认 0 字节：循环核心只做“是否允许执行循环配方”的门槛，不承担存储。
        // 若希望仅含标记块的 CPU 也能成型，可把 loopCoreStorageBytes 调成 >0。
        try {
            return Math.max(0L, AE2SplitConfig.LOOP_CORE_STORAGE.get());
        } catch (RuntimeException e) {
            // 配置尚未加载/读取异常时绝不能抛出：AE2 的成型校验会因此认定 CPU 无效。
            return 0L;
        }
    }

    @Override
    public int getAcceleratorThreads() {
        // 不提供并行：并行由 CPU 的加速器与配置方案决定。
        return 0;
    }

    @Override
    public Item getItemFromType() {
        // 延迟调用，此时注册表已就绪；极端情况下也不能抛异常，否则 onReady 会中断、CPU 无法成型。
        try {
            return AE2SplitBlocks.LOOP_CORE_ITEM.get();
        } catch (RuntimeException e) {
            return net.minecraft.world.item.Items.AIR;
        }
    }
}
