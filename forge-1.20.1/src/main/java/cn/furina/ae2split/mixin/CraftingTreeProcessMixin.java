package cn.furina.ae2split.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.inv.CraftingSimulationState;

import cn.furina.ae2split.split.LoopCalcAccess;
import cn.furina.ae2split.split.LoopCoreManager;
import cn.furina.ae2split.split.LoopNodeAccess;

/**
 * 把循环配方当成“净配方”来模拟：
 *
 * <ul>
 *   <li>自输入（输入同时也是输出）的子节点不再向网络索取，由配方的输出循环补足；</li>
 *   <li>只把“净产出”写入模拟库存，因此计划产物 = 请求量；</li>
 *   <li>配方次数仍然是真实执行次数，执行阶段照常使用原配方；种子由 {@code CraftingCpuLogicMixin} 注入。</li>
 * </ul>
 */
@Mixin(CraftingTreeProcess.class)
public abstract class CraftingTreeProcessMixin {

    @Shadow
    @Final
    private CraftingTreeNode parent;

    @Shadow
    @Final
    private CraftingCalculation job;

    @Shadow
    @Final
    IPatternDetails details;

    @Shadow
    @Final
    private Map<CraftingTreeNode, Long> nodes;

    @Unique
    private boolean ae2split$looped;

    @Unique
    private LoopCoreManager.PatternInfo ae2split$info = LoopCoreManager.PatternInfo.NONE;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2split$initLoop(ICraftingService cc, CraftingCalculation job, IPatternDetails details,
            CraftingTreeNode node, CallbackInfo ci) {
        if (this.parent == null || this.details == null || this.job == null) {
            return;
        }
        if (!(this.job instanceof LoopCalcAccess calc) || !calc.ae2split$isLoopEnabled()) {
            return;
        }
        if (!((Object) this.parent instanceof LoopNodeAccess nodeAccess)) {
            return;
        }
        AEKey what = nodeAccess.ae2split$what();
        LoopCoreManager.PatternInfo info = LoopCoreManager.analyze(this.details);
        if (!info.supplies(what)) {
            return;
        }
        // 没有种子就不放行循环：否则会规划出一个提交时必然缺料、或执行时永远卡住的计划。
        for (Map.Entry<AEKey, Long> seed : info.seeds().entrySet()) {
            if (!calc.ae2split$hasStock(seed.getKey(), seed.getValue())) {
                return;
            }
        }
        this.ae2split$looped = true;
        this.ae2split$info = info;
    }

    @Inject(method = "request", at = @At("HEAD"), cancellable = true)
    private void ae2split$loopRequest(CraftingSimulationState inv, long times, CallbackInfo ci)
            throws CraftBranchFailure, InterruptedException {
        if (!this.ae2split$looped) {
            return;
        }
        ci.cancel();
        if (times <= 0L) {
            return;
        }

        // 只请求“净消耗”的输入；输入同时是输出的那部分由配方自身循环。
        for (Map.Entry<CraftingTreeNode, Long> entry : this.nodes.entrySet()) {
            if (!((Object) entry.getKey() instanceof LoopNodeAccess nodeAccess)) {
                continue;
            }
            AEKey key = nodeAccess.ae2split$what();
            if (this.ae2split$info.recycled().contains(key)) {
                continue;
            }
            nodeAccess.ae2split$requestNode(inv, entry.getValue() * times, null);
        }

        // 循环 key 只写净产出（种子不重复计入）；其它输出按原配方毛产出写入。
        for (Map.Entry<AEKey, Long> entry : this.ae2split$info.grossOutputs().entrySet()) {
            AEKey key = entry.getKey();
            long perCraft = this.ae2split$info.recycled().contains(key)
                    ? Math.max(0L, this.ae2split$info.netFor(key))
                    : entry.getValue();
            if (perCraft > 0L) {
                inv.insert(key, perCraft * times, Actionable.MODULATE);
            }
        }

        inv.addCrafting(this.details, times);
        inv.addBytes(times);
    }

    @Inject(method = "getOutputCount", at = @At("HEAD"), cancellable = true)
    private void ae2split$netOutputCount(AEKey what, CallbackInfoReturnable<Long> cir) {
        if (!this.ae2split$looped) {
            return;
        }
        long net = this.ae2split$info.netFor(what);
        if (net > 0L) {
            cir.setReturnValue(net);
        }
    }

    @Inject(method = "limitsQuantity", at = @At("HEAD"), cancellable = true)
    private void ae2split$noQuantityLimit(CallbackInfoReturnable<Boolean> cir) {
        if (this.ae2split$looped) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
