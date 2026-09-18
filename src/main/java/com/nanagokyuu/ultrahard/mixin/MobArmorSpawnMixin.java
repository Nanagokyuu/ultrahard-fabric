package com.nanagokyuu.ultrahard.mixin;

import com.nanagokyuu.ultrahard.UltraHardDifficulties;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(Mob.class)
public abstract class MobArmorSpawnMixin {
	@ModifyConstant(
			method = "populateDefaultEquipmentSlots",
			constant = @Constant(floatValue = 0.15f)
	)
	private float ultrahard$doubleArmorSpawnChance(float original) {
		Mob self = (Mob) (Object) this;
		return UltraHardDifficulties.isUltraHard(self.level()) ? original * 2.0f : original;
	}
}
