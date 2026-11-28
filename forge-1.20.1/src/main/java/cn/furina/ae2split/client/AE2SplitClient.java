package cn.furina.ae2split.client;

import cn.furina.ae2split.AE2SplitBlocks;
import cn.furina.ae2split.Ae2SplitCpu;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端入口（MC 1.20.1 / Forge）：把 {@link SplitComponentRenderer} 绑到本模组的
 * 方块实体类型上。类只在 {@link Dist#CLIENT} 下加载，服务端不会触碰客户端类。
 */
@Mod.EventBusSubscriber(modid = Ae2SplitCpu.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AE2SplitClient {

    private AE2SplitClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AE2SplitBlocks.SPLIT_COMPONENT_BE.get(),
                context -> new SplitComponentRenderer());
        // 循环核心同样用这套“成型点亮”指示，避免玩家无法判断它有没有接进有效 CPU。
        event.registerBlockEntityRenderer(AE2SplitBlocks.LOOP_CORE_BE.get(),
                context -> new SplitComponentRenderer());
    }
}
