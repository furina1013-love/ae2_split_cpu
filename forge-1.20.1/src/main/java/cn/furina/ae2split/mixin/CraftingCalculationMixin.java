package cn.furina.ae2split.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.inv.NetworkCraftingSimulationState;

import cn.furina.ae2split.split.LoopCalcAccess;
import cn.furina.ae2split.split.LoopCoreManager;

/**
 * 给一次合成计算记录“网络里是否有循环核心”，并提供“种子是否在库存里”的查询。
 *
 * <p>规划发生在独立计算线程上，CPU 选择尚未发生，因此这里按“网格里是否存在成型的循环核心 CPU”
 * 来放行循环配方；提交任务时 {@code CraftingCpuLogicMixin} 会再检查一次，并给计划补种子。</p>
 */
@Mixin(CraftingCalculation.class)
public abstract class CraftingCalculationMixin implements LoopCalcAccess {

    @Shadow
    @Final
    private ICraftingSimulationRequester simRequester;

    @Shadow
    @Final
    private NetworkCraftingSimulationState networkInv;

    /** null = 尚未计算；延迟到第一次询问时再查网格，避免构造阶段网格未就绪。 */
    @Unique
    private Boolean ae2split$loopEnabled;

    @Unique
    @Override
    public boolean ae2split$isLoopEnabled() {
        Boolean cached = this.ae2split$loopEnabled;
        if (cached != null) {
            return cached;
        }
        boolean enabled = false;
        try {
            if (this.simRequester != null) {
                IGridNode node = this.simRequester.getGridNode();
                if (node != null) {
                    enabled = LoopCoreManager.loopCraftingAllowed(node.getGrid());
                }
            }
        } catch (RuntimeException ignored) {
            // 网格/CPU 尚在初始化：本次计算按原版处理。
        }
        this.ae2split$loopEnabled = enabled;
        return enabled;
    }

    @Unique
    @Override
    public boolean ae2split$hasStock(AEKey key, long amount) {
        if (key == null || amount <= 0L) {
            return false;
        }
        try {
            return this.networkInv != null
                    && this.networkInv.extract(key, amount, Actionable.SIMULATE) >= amount;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
