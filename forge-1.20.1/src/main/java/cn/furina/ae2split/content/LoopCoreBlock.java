package cn.furina.ae2split.content;

import net.minecraft.world.level.block.state.BlockBehaviour;

import appeng.block.crafting.AbstractCraftingUnitBlock;
import appeng.blockentity.crafting.CraftingBlockEntity;

/**
 * 循环核心方块：与分裂元件一样继承 AE2 的合成单元方块，因此自动获得
 * FORMED/POWERED 状态、多方块成型判定、拆解变回合成单元、以及与 CraftingBlockEntity 的绑定。
 * 本类只负责把 {@link LoopCoreType} 传进去，不包含任何循环/分裂算法
 * （那部分由 mixin 与 LoopCoreManager 负责）。
 */
public class LoopCoreBlock extends AbstractCraftingUnitBlock<CraftingBlockEntity> {

    public LoopCoreBlock(BlockBehaviour.Properties properties, LoopCoreType type) {
        super(properties, type);
    }
}
