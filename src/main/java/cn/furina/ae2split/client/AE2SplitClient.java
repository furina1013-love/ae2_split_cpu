package cn.furina.ae2split.client;

import cn.furina.ae2split.AE2SplitBlocks;
import cn.furina.ae2split.Ae2SplitCpu;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 客户端入口（MC 1.21.1 / NeoForge）：把 {@link SplitComponentRenderer} 绑到本模组的
 * 方块实体类型上。类只在 {@link Dist#CLIENT} 下加载，服务端不会触碰客户端类。
 */
@EventBusSubscriber(modid = Ae2SplitCpu.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AE2SplitClient {

    private AE2SplitClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AE2SplitBlocks.SPLIT_COMPONENT_BE.get(),
                context -> new SplitComponentRenderer());
    }
}
