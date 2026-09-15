package com.nanagokyuu.ultrahard

import net.minecraft.world.Difficulty
import net.minecraft.world.level.Level

/**
 * Helpers for [Difficulty.ULTRAHARD] (added via classtweaker + [com.nanagokyuu.ultrahard.mixin.DifficultyMixin])
 * and Hard-compatible vanilla checks.
 */
object UltraHardDifficulties {
	const val HUNGER_EXHAUSTION_MULTIPLIER = 1.5f
	const val PLAYER_ATTACK_DAMAGE_MULTIPLIER = 5.0f
	const val SHIELD_MAX_DURABILITY = 10
	const val RAID_GROUPS = 7

	/** Same instance as [Difficulty.ULTRAHARD] after enum extension. */
	@JvmField
	val ULTRAHARD: Difficulty = Difficulty.ULTRAHARD

	@JvmStatic
	fun isUltraHard(difficulty: Difficulty): Boolean = difficulty === ULTRAHARD

	@JvmStatic
	fun isUltraHard(level: Level): Boolean = isUltraHard(level.difficulty)

	@JvmStatic
	fun isHardOrUltra(difficulty: Difficulty): Boolean =
		difficulty === Difficulty.HARD || isUltraHard(difficulty)

	/**
	 * Maps Ultra Hard → Hard so vanilla `difficulty == Difficulty.HARD` checks succeed.
	 * Peaceful / Easy / Normal are unchanged.
	 */
	@JvmStatic
	fun asHardCompatible(difficulty: Difficulty): Difficulty =
		if (isUltraHard(difficulty)) Difficulty.HARD else difficulty
}
