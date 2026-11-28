package cn.furina.ae2split;

import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.crafting.CraftingBlockEntity;

import cn.furina.ae2split.content.LoopCoreBlock;
import cn.furina.ae2split.content.LoopCoreType;
import cn.furina.ae2split.content.SplitComponentBlock;
import cn.furina.ae2split.content.SplitComponentType;

/**
 * 方块 / 物品 / 方块实体类型的集中注册点（Forge 1.20.1）。
 * 分裂元件复用 AE2 的 CraftingBlockEntity，只是 ICraftingUnitType 为 0 字节 / 0 并行。
 */
public final class AE2SplitBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Ae2SplitCpu.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Ae2SplitCpu.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(
            ForgeRegistries.BLOCK_ENTITY_TYPES, Ae2SplitCpu.MODID);

    /** 分裂元件的单元类型（0 字节、0 并行）。 */
    public static final SplitComponentType SPLIT_COMPONENT_TYPE = new SplitComponentType();

    /** 分裂元件方块：ae2_split_cpu:split_component。 */
    public static final RegistryObject<SplitComponentBlock> SPLIT_COMPONENT = BLOCKS.register("split_component",
            () -> new SplitComponentBlock(BlockBehaviour.Properties.of()
                    .strength(2f, 8f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .mapColor(MapColor.METAL)
                    .forceSolidOn(), SPLIT_COMPONENT_TYPE));

    /** 方块物品。 */
    public static final RegistryObject<Item> SPLIT_COMPONENT_ITEM = ITEMS.register("split_component",
            () -> new BlockItem(SPLIT_COMPONENT.get(), new Item.Properties()));

    /** 方块实体类型：复用 AE2 的 CraftingBlockEntity，并把类型反向注入方块的工厂。 */
    public static final RegistryObject<BlockEntityType<CraftingBlockEntity>> SPLIT_COMPONENT_BE = BLOCK_ENTITY_TYPES
            .register("split_component", () -> {
                SplitComponentBlock block = SPLIT_COMPONENT.get();
                AtomicReference<BlockEntityType<CraftingBlockEntity>> typeHolder = new AtomicReference<>();
                BlockEntityType<CraftingBlockEntity> type = BlockEntityType.Builder
                        .of((pos, state) -> new CraftingBlockEntity(typeHolder.get(), pos, state), block)
                        .build(null);
                typeHolder.set(type);
                AEBaseBlockEntity.registerBlockEntityItem(type, SPLIT_COMPONENT_ITEM.get());
                block.setBlockEntity(CraftingBlockEntity.class, type, null, null);
                return type;
            });

    /** 循环核心的单元类型（0 字节、0 并行）。 */
    public static final LoopCoreType LOOP_CORE_TYPE = new LoopCoreType();

    /** 循环核心方块：ae2_split_cpu:loop_core，属性与分裂元件一致。 */
    public static final RegistryObject<LoopCoreBlock> LOOP_CORE = BLOCKS.register("loop_core",
            () -> new LoopCoreBlock(BlockBehaviour.Properties.of()
                    .strength(2f, 8f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .mapColor(MapColor.METAL)
                    .forceSolidOn(), LOOP_CORE_TYPE));

    /** 方块物品：ae2_split_cpu:loop_core。 */
    public static final RegistryObject<Item> LOOP_CORE_ITEM = ITEMS.register("loop_core",
            () -> new cn.furina.ae2split.content.LoopCoreItem(LOOP_CORE.get(), new Item.Properties()));

    /** 循环核心的方块实体类型：复用 AE2 的 CraftingBlockEntity，并把类型反向注入方块的工厂。 */
    public static final RegistryObject<BlockEntityType<CraftingBlockEntity>> LOOP_CORE_BE = BLOCK_ENTITY_TYPES
            .register("loop_core", () -> {
                LoopCoreBlock block = LOOP_CORE.get();
                AtomicReference<BlockEntityType<CraftingBlockEntity>> typeHolder = new AtomicReference<>();
                BlockEntityType<CraftingBlockEntity> type = BlockEntityType.Builder
                        .of((pos, state) -> new CraftingBlockEntity(typeHolder.get(), pos, state), block)
                        .build(null);
                typeHolder.set(type);
                AEBaseBlockEntity.registerBlockEntityItem(type, LOOP_CORE_ITEM.get());
                block.setBlockEntity(CraftingBlockEntity.class, type, null, null);
                return type;
            });

    private AE2SplitBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
    }

    public static Item splitComponentItem() {
        return SPLIT_COMPONENT_ITEM.get();
    }
}
