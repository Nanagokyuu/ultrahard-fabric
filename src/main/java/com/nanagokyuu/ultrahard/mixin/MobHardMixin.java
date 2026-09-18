package com.nanagokyuu.ultrahard.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nanagokyuu.ultrahard.UltraHardAi;
import com.nanagokyuu.ultrahard.UltraHardDifficulties;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobHardMixin {
	/** 统一处理所有 Mob 的 Ultra Hard 通用行为，具体战术再交给 UltraHardAi 分派。 */
	@ModifyExpressionValue(
			method = "populateDefaultEquipmentSlots",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;getDifficulty()Lnet/minecraft/world/Difficulty;"
			)
	)
	private Difficulty ultrahard$asHard(Difficulty original) {
		return UltraHardDifficulties.asHardCompatible(original);
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void ultrahard$seekDaytimeShelter(CallbackInfo ci) {
		// 在通用 Mob AI 步骤末尾统一处理目标选择、亡灵避阳和骷髅控距。
		Mob self = (Mob) (Object) this;
		UltraHardAi.prioritizeUnshieldedTarget(self);
		UltraHardAi.tickUndeadShelter(self);
		if (self instanceof AbstractSkeleton skeleton) {
			UltraHardAi.tickSkeleton(skeleton);
		}
	}

	@Inject(method = "doHurtTarget", at = @At("RETURN"))
	private void ultrahard$placeCobwebOnSpiderHit(
			ServerLevel level,
			Entity target,
			CallbackInfoReturnable<Boolean> cir
	) {
		Mob self = (Mob) (Object) this;
		// 只在蜘蛛的近战攻击实际命中玩家后尝试放置蛛网，具体冷却和持续时间由 AI 管理。
		if (!cir.getReturnValue() || !(self instanceof Spider) || !(target instanceof Player)
				|| !UltraHardDifficulties.isUltraHard(level)) {
			return;
		}
		UltraHardAi.placeSpiderWeb(level, (net.minecraft.server.level.ServerPlayer) target);
	}
}
