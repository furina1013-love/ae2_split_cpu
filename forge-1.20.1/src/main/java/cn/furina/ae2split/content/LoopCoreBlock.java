package cn.furina.ae2split.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import appeng.block.crafting.AbstractCraftingUnitBlock;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.core.definitions.AEBlocks;

import cn.furina.ae2split.AE2SplitBlocks;

/**
 * 循环核心方块（1.20.1）：与分裂元件一样继承 AE2 的合成单元方块。
 * 额外提供“潜行右键空手拆回基础合成单元 + 循环核心物品”，对齐 1.21.1 的 transform 行为。
 */
public class LoopCoreBlock extends AbstractCraftingUnitBlock<CraftingBlockEntity> {

    public LoopCoreBlock(BlockBehaviour.Properties properties, LoopCoreType type) {
        super(properties, type);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if (!level.isClientSide) {
                level.setBlock(pos, AEBlocks.CRAFTING_UNIT.block().defaultBlockState(), 3);
                player.getInventory().placeItemBackInInventory(new ItemStack(AE2SplitBlocks.LOOP_CORE_ITEM.get()));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(state, level, pos, player, hand, hit);
    }
}
