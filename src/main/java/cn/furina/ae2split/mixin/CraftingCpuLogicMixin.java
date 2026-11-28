package cn.furina.ae2split.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import cn.furina.ae2split.split.LoopCoreManager;

/**
 * 提交循环配方任务时：
 *
 * <ol>
 *   <li>{@code requireLoopCore=true} 时，目标 CPU（分裂子 CPU 会回查主 CPU）必须含循环核心，
 *       否则拒绝，避免循环任务落到一台没有循环核心的 CPU 上；</li>
 *   <li>把“循环种子”补进 {@code plan.usedItems()}：AE2 的
 *       {@code CraftingCpuHelper.tryExtractInitialItems} 会据此把种子从网络抽进 CPU 库存。</li>
 * </ol>
 */
@Mixin(CraftingCpuLogic.class)
public abstract class CraftingCpuLogicMixin {

    @Shadow
    @Final
    private CraftingCPUCluster cluster;

    @Inject(method = "trySubmitJob", at = @At("HEAD"), cancellable = true)
    private void ae2split$addLoopSeeds(IGrid grid, ICraftingPlan plan, IActionSource src,
            ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        try {
            var seeds = LoopCoreManager.loopSeeds(plan);
            if (seeds.isEmpty()) {
                return;
            }
            // 循环配方必须由含循环核心的 CPU 执行（可在配置里关闭该要求）。
            if (LoopCoreManager.requireLoopCore() && !LoopCoreManager.cpuHasLoopCore(this.cluster)) {
                cir.setReturnValue(CraftingSubmitResult.simpleError(CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND));
                return;
            }
            LoopCoreManager.addLoopSeeds(plan);
        } catch (RuntimeException ignored) {
            // 不能因为种子/门槛检查失败阻断正常下单；缺种子时 AE2 会以缺料形式返回。
        }
    }
}
