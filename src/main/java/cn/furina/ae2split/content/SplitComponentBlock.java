package cn.furina.ae2split.content;

import net.minecraft.world.level.block.state.BlockBehaviour;

import appeng.block.crafting.AbstractCraftingUnitBlock;
import appeng.blockentity.crafting.CraftingBlockEntity;

/**
 * 分裂元件方块：继承 AE2 的合成单元方块，因此自动获得 FORMED/POWERED 状态、
 * 多方块成型判定、拆解变回合成单元、以及与 CraftingBlockEntity 的绑定。
 * 具体行为全部来自 AE2，本类只负责把 ICraftingUnitType 传进去。
 */
public class SplitComponentBlock extends AbstractCraftingUnitBlock<CraftingBlockEntity> {

    public SplitComponentBlock(BlockBehaviour.Properties properties, SplitComponentType type) {
        super(properties, type);
    }
}
