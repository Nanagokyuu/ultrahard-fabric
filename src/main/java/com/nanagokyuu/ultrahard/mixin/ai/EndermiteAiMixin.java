package com.nanagokyuu.ultrahard.mixin.ai;

import com.nanagokyuu.ultrahard.UltraHardAi;
import net.minecraft.world.entity.monster.Endermite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Endermite.class)
public abstract class EndermiteAiMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void ultrahard$harassFromTheSide(CallbackInfo ci) {
		// 末影螨不使用瞬移，而是依靠高速侧移持续骚扰玩家。
		UltraHardAi.tickEndermite((Endermite) (Object) this);
	}
}
