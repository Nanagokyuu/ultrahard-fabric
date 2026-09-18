package com.nanagokyuu.ultrahard.mixin;

import com.nanagokyuu.ultrahard.UltraHardAi;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Creeper.class)
public abstract class CreeperAiMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void ultrahard$evacuateNearbyMonsters(CallbackInfo ci) {
		// 放在苦力怕自身状态更新后，才能准确判断是否已经进入引爆阶段。
		UltraHardAi.tickCreeper((Creeper) (Object) this);
	}
}
