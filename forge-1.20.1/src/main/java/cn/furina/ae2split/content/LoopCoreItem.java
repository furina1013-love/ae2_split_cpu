package cn.furina.ae2split.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import appeng.core.definitions.AEBlocks;

import cn.furina.ae2split.AE2SplitBlocks;

/**
 * 循环核心物品（1.20.1 专用）：
 * AE2 15.4 没有 1.21.1 的 CraftingUnitTransformRecipe，所以这里手工实现“潜行右键基础合成单元
 * → 替换成循环核心”，以及配合 {@link LoopCoreBlock} 的潜行右键拆回。
 */
public class LoopCoreItem extends BlockItem {

    public LoopCoreItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (state.getBlock() == AEBlocks.CRAFTING_UNIT.block()) {
            if (!context.getLevel().isClientSide) {
                BlockPos pos = context.getClickedPos();
                context.getLevel().setBlock(pos, AE2SplitBlocks.LOOP_CORE.get().defaultBlockState(), 3);
                context.getItemInHand().shrink(1);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return super.useOn(context);
    }
}
