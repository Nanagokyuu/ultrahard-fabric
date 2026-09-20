package com.nanagokyuu.ultrahard.mixin.ai;

import com.nanagokyuu.ultrahard.elite.EliteMob;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在原版完成生物初始化后抽取一次精英词条。 */
@Mixin(Mob.class)
public abstract class EliteMobSpawnMixin {
	@Inject(method = "finalizeSpawn", at = @At("TAIL"))
	private void ultrahard$assignElite(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, SpawnGroupData spawnData, CallbackInfoReturnable<SpawnGroupData> cir) {
		EliteMob.assignOnSpawn((Mob) (Object) this);
	}
}
