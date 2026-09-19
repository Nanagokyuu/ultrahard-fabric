package com.nanagokyuu.ultrahard.mixin.difficulty;

import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties;
import com.nanagokyuu.ultrahard.config.UltraHardConfigs;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 只修改原版护甲生成概率中的基础常量，后续装备种类选择仍由原版处理；非超困难返回原值。
 */
@Mixin(Mob.class)
public abstract class MobArmorSpawnMixin {
	@ModifyConstant(
			method = "populateDefaultEquipmentSlots",
			constant = @Constant(floatValue = 0.15f)
	)
	private float ultrahard$doubleArmorSpawnChance(float original) {
		Mob self = (Mob) (Object) this;
		return UltraHardDifficulties.isUltraHard(self.level())
				? original * UltraHardConfigs.getValues().getMobArmorSpawnMultiplier()
				: original;
	}
}
