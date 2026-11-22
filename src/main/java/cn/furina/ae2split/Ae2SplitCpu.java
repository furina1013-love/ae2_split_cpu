package cn.furina.ae2split;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * 模组入口：持有全部 DeferredRegister，并在构造器里把方块/物品/方块实体/创造模式标签页
 * 挂到 mod 事件总线，同时注册 COMMON 配置（config/ae2_split_cpu-common.toml）。
 * 这里只做"注册"，不含任何分裂算法逻辑（那在 split/ 与 mixin/ 包里）。
 */
@Mod(Ae2SplitCpu.MODID)
public class Ae2SplitCpu {
    /** 必须与 gradle.properties 的 mod_id、neoforge.mods.toml 的 modId 完全一致。 */
    public static final String MODID = "ae2_split_cpu";
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 创造模式标签页注册表（图标 = 分裂元件）。 */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB, MODID);

    /**
     * 本模组自己的创造模式标签页。
     * 标题用可翻译组件，键为 itemGroup.ae2_split_cpu（见 lang 文件）。
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2_split_cpu"))
                    .icon(() -> new ItemStack(AE2SplitBlocks.SPLIT_COMPONENT_ITEM.get()))
                    .displayItems((parameters, output) -> output.accept(AE2SplitBlocks.SPLIT_COMPONENT_ITEM.get()))
                    .build());

    public Ae2SplitCpu(IEventBus modEventBus, ModContainer modContainer) {
        // 方块 -> 物品 -> 方块实体类型 的注册顺序由 NeoForge 保证（BLOCK 先于 BLOCK_ENTITY_TYPE）。
        AE2SplitBlocks.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);

        // COMMON 配置：服务端与客户端都要读，文件为 config/ae2_split_cpu-common.toml。
        modContainer.registerConfig(ModConfig.Type.COMMON, AE2SplitConfig.SPEC);

        LOGGER.info("AE2 Split CPU ({}) initialized", MODID);
    }
}
