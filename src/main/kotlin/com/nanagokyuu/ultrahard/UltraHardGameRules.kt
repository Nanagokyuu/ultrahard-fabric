package com.nanagokyuu.ultrahard

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.gamerules.GameRule
import net.minecraft.world.level.gamerules.GameRuleCategory

object UltraHardGameRules {
	lateinit var ULTRA_HARD: GameRule<Boolean>
		private set

	fun init() {
		ULTRA_HARD = GameRuleBuilder
			.forBoolean(false)
			.category(GameRuleCategory.MISC)
			.buildAndRegister(UltraHardMod.id("ultraHard"))
	}

	@JvmStatic
	fun isEnabled(level: Level): Boolean {
		if (level !is ServerLevel) return false
		return level.gameRules.get(ULTRA_HARD) == true
	}
}
