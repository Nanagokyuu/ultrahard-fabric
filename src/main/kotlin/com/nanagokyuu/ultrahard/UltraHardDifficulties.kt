package com.nanagokyuu.ultrahard

import net.minecraft.world.Difficulty
import net.minecraft.world.level.Level

/**
 * 提供 Ultra Hard 难度的辅助方法。
 *
 * ULTRAHARD 由 ClassTweaker 和
 * [com.nanagokyuu.ultrahard.mixin.difficulty.DifficultyMixin] 添加，并提供将其
 * 映射为困难模式以复用原版困难逻辑的方法。
 */
object UltraHardDifficulties {
	/** 枚举扩展完成后，与 [Difficulty.ULTRAHARD] 相同的实例。 */
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
	 * 将 Ultra Hard 映射为 Hard，使原版的
	 * `difficulty == Difficulty.HARD` 判断继续生效。
	 * 和平、简单、普通难度保持不变。
	 */
	@JvmStatic
	fun asHardCompatible(difficulty: Difficulty): Difficulty =
		if (isUltraHard(difficulty)) Difficulty.HARD else difficulty
}
