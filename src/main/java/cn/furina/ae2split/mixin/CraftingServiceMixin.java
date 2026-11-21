package cn.furina.ae2split.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;

import cn.furina.ae2split.split.SplitCpuManager;

/**
 * 让 AE2 的 {@link CraftingService} 认识本模组的"幽灵子 CPU"：
 *
 * <ul>
 *   <li>实现 {@link SplitCpuAccess}，允许分裂引擎往 {@code craftingCPUClusters} 集合增删子 CPU；</li>
 *   <li>{@code updateCPUClusters} 重建集合后把子 CPU 重新挂回去（并补登记 CraftingLink）；</li>
 *   <li>{@code onServerEndTick} 结束后回收已经完成的子 CPU，释放预留的字节 / 并行。</li>
 * </ul>
 */
@Mixin(CraftingService.class)
public abstract class CraftingServiceMixin implements SplitCpuAccess {

    /** AE2 保存所有已成型 CPU 的集合（私有 final 字段，mixin 只读引用、不替换实例）。 */
    @Shadow
    @Final
    private Set<CraftingCPUCluster> craftingCPUClusters;

    @Unique
    @Override
    public void ae2split$addCpu(CraftingCPUCluster cpu) {
        if (cpu != null) {
            this.craftingCPUClusters.add(cpu);
        }
    }

    @Unique
    @Override
    public void ae2split$removeCpu(CraftingCPUCluster cpu) {
        if (cpu != null) {
            this.craftingCPUClusters.remove(cpu);
        }
    }

    /** 集合被 updateCPUClusters 清空重建后，把管理器里仍存活的子 CPU 补回集合。 */
    @Inject(method = "updateCPUClusters", at = @At("TAIL"))
    private void ae2split$reattach(CallbackInfo ci) {
        SplitCpuManager.reattach((CraftingService) (Object) this, this.craftingCPUClusters);
    }

    /** 每 tick 末尾回收"没有任务且库存为空"的子 CPU。 */
    @Inject(method = "onServerEndTick", at = @At("TAIL"))
    private void ae2split$endOfTick(CallbackInfo ci) {
        SplitCpuManager.endOfTick((CraftingService) (Object) this);
    }
}
