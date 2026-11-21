package cn.furina.ae2split.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import cn.furina.ae2split.split.SplitCpuHandle;
import cn.furina.ae2split.split.SplitCpuManager;

/**
 * 对 AE2 的 {@link CraftingCPUCluster} 做两件事：
 *
 * <ol>
 *   <li>让"幽灵子 CPU"（没有方块实体的实例）把网络相关行为委托给主 CPU，
 *       避免 getCore()/machineSrc 为空导致的 NPE；</li>
 *   <li>在主 CPU 的可用字节 / 并行上扣除子 CPU 的预留值（实现"临时扣除、结束恢复"），
 *       并在下单时创建子 CPU 接管订单。</li>
 * </ol>
 *
 * <p>方法名与签名严格对照 AE2 19.2.19 源码（appeng.me.cluster.implementations.CraftingCPUCluster）。</p>
 */
@Mixin(CraftingCPUCluster.class)
public abstract class CraftingCPUClusterMixin implements SplitCpuHandle {

    /** 主 CPU 的总字节（addBlockEntity 里累加）。 */
    @Shadow
    private long storage;
    /** 主 CPU 的总并行（addBlockEntity 里累加）。 */
    @Shadow
    private int accelerator;
    /** 集群是否已被拆解（AE2 原字段）。 */
    @Shadow
    private boolean isDestroyed;

    /* ---------------- 子 CPU 私有状态（只存在于 mixin 注入的字段里） ---------------- */

    /** 子 CPU 的主集群；null 表示这是真实（主）CPU。 */
    @Unique
    private CraftingCPUCluster ae2split$parent;
    /** 子 CPU 从主 CPU 预留的字节数。 */
    @Unique
    private long ae2split$splitBytes;
    /** 子 CPU 从主 CPU 预留的并行数。 */
    @Unique
    private int ae2split$splitThreads;
    /** 子 CPU 在 CPU 列表里显示的名字。 */
    @Unique
    private Component ae2split$splitName;

    /** 由 SplitCpuManager 在创建子 CPU 时调用，写入分裂状态。 */
    @Unique
    @Override
    public void ae2split$initSplit(CraftingCPUCluster parent, long bytes, int threads, Component name) {
        this.ae2split$parent = parent;
        this.ae2split$splitBytes = bytes;
        this.ae2split$splitThreads = threads;
        this.ae2split$splitName = name;
    }

    @Unique
    @Override
    public boolean ae2split$isSplit() {
        return this.ae2split$parent != null;
    }

    @Unique
    @Override
    public CraftingCPUCluster ae2split$getParent() {
        return this.ae2split$parent;
    }

    @Unique
    @Override
    public long ae2split$getSplitBytes() {
        return this.ae2split$splitBytes;
    }

    @Unique
    @Override
    public int ae2split$getSplitThreads() {
        return this.ae2split$splitThreads;
    }

    @Unique
    @Override
    public void ae2split$markDestroyed() {
        this.isDestroyed = true;
    }

    /** 当前对象（mixin 目标实例）。 */
    @Unique
    private CraftingCPUCluster ae2split$self() {
        return (CraftingCPUCluster) (Object) this;
    }

    /* ---------------- 网络相关行为全部委托给主 CPU ---------------- */

    @Inject(method = "getGrid", at = @At("HEAD"), cancellable = true)
    private void ae2split$getGrid(CallbackInfoReturnable<IGrid> cir) {
        if (this.ae2split$parent != null) {
            cir.setReturnValue(this.ae2split$parent.getGrid());
        }
    }

    @Inject(method = "getLevel", at = @At("HEAD"), cancellable = true)
    private void ae2split$getLevel(CallbackInfoReturnable<Level> cir) {
        if (this.ae2split$parent != null) {
            cir.setReturnValue(this.ae2split$parent.getLevel());
        }
    }

    @Inject(method = "getSrc", at = @At("HEAD"), cancellable = true)
    private void ae2split$getSrc(CallbackInfoReturnable<IActionSource> cir) {
        if (this.ae2split$parent != null) {
            cir.setReturnValue(this.ae2split$parent.getSrc());
        }
    }

    @Inject(method = "markDirty", at = @At("HEAD"), cancellable = true)
    private void ae2split$markDirty(CallbackInfo ci) {
        if (this.ae2split$parent != null) {
            ci.cancel();
            this.ae2split$parent.markDirty();
        }
    }

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void ae2split$isActive(CallbackInfoReturnable<Boolean> cir) {
        if (this.ae2split$parent != null) {
            cir.setReturnValue(this.ae2split$parent.isActive());
        }
    }

    @Inject(method = "getNode", at = @At("HEAD"), cancellable = true)
    private void ae2split$getNode(CallbackInfoReturnable<IGridNode> cir) {
        if (this.ae2split$parent != null) {
            cir.setReturnValue(this.ae2split$parent.getNode());
        }
    }

    /** 子 CPU 的监视器输出直接交给主 CPU 的监视器显示。 */
    @Inject(method = "updateOutput", at = @At("HEAD"), cancellable = true)
    private void ae2split$updateOutput(GenericStack finalOutput, CallbackInfo ci) {
        if (this.ae2split$parent != null) {
            ci.cancel();
            this.ae2split$parent.updateOutput(finalOutput);
        }
    }

    @Inject(method = "canBeAutoSelectedFor", at = @At("HEAD"), cancellable = true)
    private void ae2split$canBeAutoSelectedFor(IActionSource source, CallbackInfoReturnable<Boolean> cir) {
        if (this.ae2split$parent != null) {
            cir.setReturnValue(this.ae2split$parent.canBeAutoSelectedFor(source));
        }
    }

    @Inject(method = "isPreferredFor", at = @At("HEAD"), cancellable = true)
    private void ae2split$isPreferredFor(IActionSource source, CallbackInfoReturnable<Boolean> cir) {
        if (this.ae2split$parent != null) {
            cir.setReturnValue(this.ae2split$parent.isPreferredFor(source));
        }
    }

    @Inject(method = "getSelectionMode", at = @At("HEAD"), cancellable = true)
    private void ae2split$getSelectionMode(CallbackInfoReturnable<CpuSelectionMode> cir) {
        if (this.ae2split$parent != null) {
            cir.setReturnValue(this.ae2split$parent.getSelectionMode());
        }
    }

    /* ---------------- 预留字节 / 并行 ---------------- */

    @Inject(method = "getAvailableStorage", at = @At("HEAD"), cancellable = true)
    private void ae2split$getAvailableStorage(CallbackInfoReturnable<Long> cir) {
        if (this.ae2split$parent != null) {
            cir.setReturnValue(this.ae2split$splitBytes);
            return;
        }
        long reserved = SplitCpuManager.reservedBytes(this.ae2split$self());
        if (reserved > 0L) {
            cir.setReturnValue(Math.max(0L, this.storage - reserved));
        }
    }

    @Inject(method = "getCoProcessors", at = @At("HEAD"), cancellable = true)
    private void ae2split$getCoProcessors(CallbackInfoReturnable<Integer> cir) {
        if (this.ae2split$parent != null) {
            cir.setReturnValue(this.ae2split$splitThreads);
            return;
        }
        int reserved = SplitCpuManager.reservedThreads(this.ae2split$self());
        if (reserved > 0) {
            cir.setReturnValue(Math.max(0, this.accelerator - reserved));
        }
    }

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void ae2split$getName(CallbackInfoReturnable<Component> cir) {
        if (this.ae2split$parent != null) {
            cir.setReturnValue(this.ae2split$splitName);
        }
    }

    /* ---------------- 下单拦截：主 CPU 分裂出子 CPU 接管订单 ---------------- */

    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true)
    private void ae2split$submitJob(IGrid grid, ICraftingPlan plan, IActionSource src, ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        // 子 CPU 走原版逻辑（它自己不会再分裂）。
        if (this.ae2split$parent != null) {
            return;
        }
        // 主 CPU 自己已有任务时不分裂：原版 trySubmitJob 会返回 CPU_BUSY，
        // 否则会把同一份字节 / 并行重复分配出去（CraftingService.submitJob 对显式 target 不做 busy 检查）。
        if (this.ae2split$self().isBusy()) {
            return;
        }
        CraftingCPUCluster child = SplitCpuManager.createSplitCpu(this.ae2split$self(), plan);
        if (child == null) {
            return; // 不满足分裂条件：放行原版下单流程
        }
        ICraftingSubmitResult result;
        try {
            result = child.craftingLogic.trySubmitJob(grid, plan, src, requester);
        } catch (RuntimeException e) {
            // 提交过程中抛异常：先清掉子 CPU，再把异常原样抛给调用方（不改变原版行为）。
            SplitCpuManager.dissolve(child);
            throw e;
        }
        if (result == null || !result.successful()) {
            // 缺料 / CPU 离线等：销毁子 CPU，让调用方拿到与原版一致的结果。
            SplitCpuManager.dissolve(child);
            if (result == null) {
                return; // 理论不会发生；退回原版逻辑兜底
            }
        }
        cir.setReturnValue(result);
    }

    /* ---------------- 持久化：主 CPU 顺带保存 / 载入子 CPU ---------------- */

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void ae2split$writeToNBT(CompoundTag data, CallbackInfo ci) {
        if (this.ae2split$parent != null) {
            return; // 子 CPU 不写自己的子 CPU（防止递归）
        }
        SplitCpuManager.writeChildren(this.ae2split$self(), data);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void ae2split$readFromNBT(CompoundTag data, CallbackInfo ci) {
        if (this.ae2split$parent != null) {
            return; // 子 CPU 不读子 CPU 列表
        }
        SplitCpuManager.readChildren(this.ae2split$self(), data);
    }

    /* ---------------- 拆解 ---------------- */

    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true)
    private void ae2split$destroy(CallbackInfo ci) {
        if (this.ae2split$parent != null) {
            // 子 CPU 没有方块实体，不能走 AE2 的 destroy 逻辑（会遍历空列表并 post 事件），
            // 只把自己标记为 destroyed 并清理管理器记录。
            ci.cancel();
            this.isDestroyed = true;
            SplitCpuManager.onChildDestroyed(this.ae2split$self());
            return;
        }
        // 主 CPU 拆解：先取消并清掉所有子 CPU，再让原版 destroy 继续。
        SplitCpuManager.destroyChildren(this.ae2split$self());
    }
}
