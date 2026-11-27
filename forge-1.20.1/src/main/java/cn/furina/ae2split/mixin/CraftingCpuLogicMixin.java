package cn.furina.ae2split.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.CraftingSubmitResult;

import cn.furina.ae2split.split.LoopCoreManager;

/**
 * 提交循环配方任务时，把“循环种子”补进 {@code plan.usedItems()}：
 * AE2 的 {@code CraftingCpuHelper.tryExtractInitialItems} 会据此把种子从网络抽进 CPU 库存。
 * 种子在配方执行过程中循环使用，任务结束后作为余料随 CPU 库存退回网络。
 */
@Mixin(CraftingCpuLogic.class)
public abstract class CraftingCpuLogicMixin {

    @Inject(method = "trySubmitJob", at = @At("HEAD"), cancellable = true)
    private void ae2split$addLoopSeeds(IGrid grid, ICraftingPlan plan, IActionSource src,
            ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        try {
            var seeds = LoopCoreManager.loopSeeds(plan);
            if (seeds.isEmpty()) {
                return;
            }
            // 规划后被拆掉了循环核心：按“缺少循环种子”拒绝，而不是提交一个永远卡住的任务。
            if (!LoopCoreManager.gridHasLoopCore(grid)) {
                var first = seeds.entrySet().iterator().next();
                cir.setReturnValue(CraftingSubmitResult
                        .missingIngredient(new GenericStack(first.getKey(), first.getValue())));
                return;
            }
            LoopCoreManager.addLoopSeeds(grid, plan);
        } catch (RuntimeException ignored) {
            // 不能因为种子注入失败阻断正常下单；缺种子时 AE2 会以缺料形式返回。
        }
    }
}
