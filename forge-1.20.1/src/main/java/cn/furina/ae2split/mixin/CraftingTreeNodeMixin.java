package cn.furina.ae2split.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.inv.CraftingSimulationState;

import cn.furina.ae2split.split.LoopCalcAccess;
import cn.furina.ae2split.split.LoopCoreManager;
import cn.furina.ae2split.split.LoopNodeAccess;

/**
 * 放宽 AE2 对“自引用配方”的递归检查：当本次计算所在网格有循环核心、且该配方能为当前节点
 * 提供净产出时，允许把它挂到节点上。真正的“跳过自输入、只产出净额”在
 * {@link CraftingTreeProcessMixin} 里完成。
 */
@Mixin(CraftingTreeNode.class)
public abstract class CraftingTreeNodeMixin implements LoopNodeAccess {

    @Shadow
    @Final
    private CraftingCalculation job;

    @Shadow
    @Final
    private AEKey what;

    /** 包私有方法，只能由同一 mixin 类桥接到接口里。 */
    @Shadow
    abstract void request(CraftingSimulationState inv, long requestedAmount, KeyCounter containerItems)
            throws CraftBranchFailure, InterruptedException;

    @Unique
    @Override
    public AEKey ae2split$what() {
        return this.what;
    }

    @Unique
    @Override
    public void ae2split$requestNode(CraftingSimulationState inv, long requestedAmount, KeyCounter containerItems)
            throws CraftBranchFailure, InterruptedException {
        this.request(inv, requestedAmount, containerItems);
    }

    @Inject(method = "notRecursive", at = @At("HEAD"), cancellable = true)
    private void ae2split$allowLoopPattern(IPatternDetails details, CallbackInfoReturnable<Boolean> cir) {
        if (this.what == null || details == null) {
            return;
        }
        if (!(this.job instanceof LoopCalcAccess calc) || !calc.ae2split$isLoopEnabled()) {
            return;
        }
        if (LoopCoreManager.analyze(details).supplies(this.what)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
