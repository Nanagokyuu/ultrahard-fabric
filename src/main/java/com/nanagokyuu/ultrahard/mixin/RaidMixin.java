package com.nanagokyuu.ultrahard.mixin;

import com.nanagokyuu.ultrahard.UltraHardDifficulties;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Raid.class)
public abstract class RaidMixin {
	/** Vanilla Hard returns 7 groups; switch on ordinal would MatchException for ULTRAHARD. */
	private static final int HARD_NUM_GROUPS = 7;

	@Inject(method = "getNumGroups", at = @At("HEAD"), cancellable = true)
	private void ultrahard$hardGroups(Difficulty difficulty, CallbackInfoReturnable<Integer> cir) {
		if (UltraHardDifficulties.isUltraHard(difficulty)) {
			cir.setReturnValue(HARD_NUM_GROUPS);
		}
	}
}
