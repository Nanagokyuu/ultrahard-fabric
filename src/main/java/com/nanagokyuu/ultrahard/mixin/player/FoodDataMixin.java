package com.nanagokyuu.ultrahard.mixin.player;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 仅阻断食物系统的自然回血及对应额外消耗，保留进食功能和其他治疗来源，并兼容困难饥饿规则。
 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
	/**
	 * 在 Ultra Hard 下取消由食物和饱和度触发的自然回血。
	 * 药水以及其他直接调用 heal() 的治疗方式不受影响。
	 */
	@Redirect(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"
			)
	)
	private void ultrahard$cancelNaturalRegen(ServerPlayer player, float amount) {
		if (UltraHardDifficulties.isUltraHard(player.level())) {
			return;
		}
		player.heal(amount);
	}

	@Redirect(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V"
			)
	)
	private void ultrahard$cancelNaturalRegenExhaustion(
			FoodData foodData,
			float amount,
			@Local(argsOnly = true) ServerPlayer player
	) {
		// FoodData.tick 中的两次调用都属于自然回血分支。
		// 禁止这些调用后，在关闭自然回血的同时可以保留当前饱和度。
		if (!UltraHardDifficulties.isUltraHard(player.level())) {
			foodData.addExhaustion(amount);
		}
	}

	/**
	 * 在饥饿和食物 tick 的难度分支中，将 Ultra Hard 按困难模式处理。
	 */
	@ModifyExpressionValue(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerLevel;getDifficulty()Lnet/minecraft/world/Difficulty;"
			)
	)
	private Difficulty ultrahard$hardCompatible(Difficulty original) {
		return UltraHardDifficulties.asHardCompatible(original);
	}
}
