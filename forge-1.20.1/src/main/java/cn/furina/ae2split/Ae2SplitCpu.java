package cn.furina.ae2split;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * 模组入口（Forge 1.20.1）：注册方块/物品/方块实体类型/创造模式标签页，并挂上 COMMON 配置
 * （config/ae2_split_cpu-common.toml）。分裂算法全部在 split/ 与 mixin/ 包里。
 */
@Mod(Ae2SplitCpu.MODID)
public class Ae2SplitCpu {
    /** 必须与 gradle.properties 的 mod_id、mods.toml 的 modId 完全一致。 */
    public static final String MODID = "ae2_split_cpu";
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 创造模式标签页（图标 = 分裂元件）。 */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<CreativeModeTab> CREATIVE_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2_split_cpu"))
                    .icon(() -> new ItemStack(AE2SplitBlocks.SPLIT_COMPONENT_ITEM.get()))
                    .displayItems((parameters, output) -> output.accept(AE2SplitBlocks.SPLIT_COMPONENT_ITEM.get()))
                    .build());

    public Ae2SplitCpu() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        AE2SplitBlocks.register(modBus);
        CREATIVE_TABS.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AE2SplitConfig.SPEC);
        LOGGER.info("AE2 Split CPU ({}) initialized", MODID);
    }
}
