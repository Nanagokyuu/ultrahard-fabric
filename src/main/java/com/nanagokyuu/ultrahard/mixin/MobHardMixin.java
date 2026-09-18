package com.nanagokyuu.ultrahard.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nanagokyuu.ultrahard.UltraHardAi;
import com.nanagokyuu.ultrahard.UltraHardDifficulties;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.Ravager;
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
		if (self instanceof Vindicator vindicator) {
			// 灾厄近战单位优先从侧后方接近举盾玩家。
			UltraHardAi.tickVindicator(vindicator);
		} else if (self instanceof Pillager pillager) {
			// 掠夺者优先寻找侧后方射击位置，避免正面浪费弩箭。
			UltraHardAi.tickPillager(pillager);
		} else if (self instanceof Ravager ravager) {
			// 劫掠兽使用更宽的绕行半径，配合其他袭击单位制造夹击。
			UltraHardAi.tickRavager(ravager);
		} else if (self instanceof Evoker evoker) {
			// 唤魔者从侧后方施法，迫使玩家不断转身。
			UltraHardAi.tickEvoker(evoker);
		}
	}

	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void ultrahard$flankShieldedMelee(
			ServerLevel level,
			Entity target,
			CallbackInfoReturnable<Boolean> cir
	) {
		Mob self = (Mob) (Object) this;
		// 近战攻击落在盾牌正面时取消本次攻击，并让怪物重新寻找突破角度。
		if (!UltraHardDifficulties.isUltraHard(level) || !UltraHardAi.shouldCancelShieldedMelee(self, target)) return;
		if (self instanceof EnderMan enderman) {
			// 末影人不使用普通寻路绕行，而是瞬移到玩家的视线盲区。
			UltraHardAi.tickEnderman(enderman);
		} else {
			UltraHardAi.flankShield(self, (net.minecraft.world.entity.LivingEntity) target, 3.0, 1.2);
		}
		cir.setReturnValue(false);
	}

	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void ultrahard$ignoreHostileRetaliation(LivingEntity target, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		// 附近有玩家时，敌对生物可以互相造成伤害，但不能因误伤而互相锁定仇恨。
		if (target != null && UltraHardAi.shouldIgnoreHostileRetaliation(self, target)) {
			ci.cancel();
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
