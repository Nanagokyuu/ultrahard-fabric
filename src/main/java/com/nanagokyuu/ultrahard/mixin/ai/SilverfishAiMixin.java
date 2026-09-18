package com.nanagokyuu.ultrahard.mixin.ai;

import com.nanagokyuu.ultrahard.UltraHardAi;
import net.minecraft.world.entity.monster.Silverfish;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在原版刻更新后追加群体包围策略；同目标蠹虫的排序和站位分配由战术模块完成。
 */
@Mixin(Silverfish.class)
public abstract class SilverfishAiMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void ultrahard$surroundPlayer(CallbackInfo ci) {
		// 蠹虫在 tick 末尾重新分配正面牵制、侧翼和背后位置。
		UltraHardAi.tickSilverfish((Silverfish) (Object) this);
	}
}
