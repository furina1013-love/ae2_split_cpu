package cn.furina.ae2split;

import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.crafting.CraftingBlockEntity;

import cn.furina.ae2split.content.SplitComponentBlock;
import cn.furina.ae2split.content.SplitComponentType;

/**
 * 方块 / 物品 / 方块实体类型的集中注册点。
 * 分裂元件复用 AE2 的 CraftingBlockEntity（只是 ICraftingUnitType 不同：0 字节 / 0 并行），
 * 因此不需要自定义方块实体类。
 */
public final class AE2SplitBlocks {
    /** 方块注册表（DeferredBlock 会自动带上 key）。 */
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Ae2SplitCpu.MODID);
    /** 物品注册表（方块物品）。 */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Ae2SplitCpu.MODID);
    /** 方块实体类型注册表。 */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(
            Registries.BLOCK_ENTITY_TYPE, Ae2SplitCpu.MODID);

    /** 分裂元件的单元类型实例（0 字节、0 并行、物品指向本方块自己）。 */
    public static final SplitComponentType SPLIT_COMPONENT_TYPE = new SplitComponentType();

    /**
     * 分裂元件方块：id = ae2-split-cpu:split_component。
     * 硬度/抗爆/音效按 PLAM 要求设置，并补上 AE2 金属方块常用的 mapColor 与 forceSolidOn。
     */
    public static final DeferredBlock<SplitComponentBlock> SPLIT_COMPONENT = BLOCKS.registerBlock(
            "split_component",
            properties -> new SplitComponentBlock(properties, SPLIT_COMPONENT_TYPE),
            BlockBehaviour.Properties.of()
                    .strength(2f, 8f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .mapColor(MapColor.METAL)
                    .forceSolidOn());

    /** 方块对应的 BlockItem：id = ae2-split-cpu:split_component。 */
    public static final DeferredItem<BlockItem> SPLIT_COMPONENT_ITEM = ITEMS.registerSimpleBlockItem(SPLIT_COMPONENT);

    /**
     * 方块实体类型：id = ae2-split-cpu:split_component，实例类型为 AE2 的 CraftingBlockEntity。
     * 注意这里的自引用：Builder 的 supplier 需要一个"类型实例"，而类型只在 build() 之后才存在，
     * 所以先用 AtomicReference 占位，build() 完毕后再回填（与 AE2 官方注册写法一致）。
     * 同时把 BlockEntityType 反向注入方块的 newBlockEntity 工厂（setBlockEntity）。
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CraftingBlockEntity>> SPLIT_COMPONENT_BE = BLOCK_ENTITY_TYPES
            .register("split_component", () -> {
                // 延迟取方块：此处方块注册已经完成（BLOCK 先于 BLOCK_ENTITY_TYPE），不会拿到 null。
                SplitComponentBlock block = SPLIT_COMPONENT.get();

                AtomicReference<BlockEntityType<CraftingBlockEntity>> typeHolder = new AtomicReference<>();
                BlockEntityType.BlockEntitySupplier<CraftingBlockEntity> supplier = (pos, state) -> new CraftingBlockEntity(
                        typeHolder.get(), pos, state);

                BlockEntityType<CraftingBlockEntity> type = BlockEntityType.Builder.of(supplier, block).build(null);
                typeHolder.set(type); // 回填，供上面 supplier 使用

                // AE2 用该映射给方块实体找"代表性物品"（掉落 / 提示等）。
                AEBaseBlockEntity.registerBlockEntityItem(type, SPLIT_COMPONENT_ITEM.get());

                // 没有 ticker（CraftingBlockEntity 自身由 CraftingService 驱动）。
                block.setBlockEntity(CraftingBlockEntity.class, type, null, null);
                return type;
            });

    private AE2SplitBlocks() {
    }

    /** 在 mod 构造器里调用一次，把三个注册表挂到 mod 事件总线。 */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }

    /** 便于外部（如 C 阶段）用统一入口拿到物品实例。 */
    public static Item splitComponentItem() {
        return SPLIT_COMPONENT_ITEM.get();
    }
}
