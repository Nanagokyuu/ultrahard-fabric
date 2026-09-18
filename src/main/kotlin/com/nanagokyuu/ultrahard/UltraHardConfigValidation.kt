package com.nanagokyuu.ultrahard

import com.google.gson.JsonObject

/** 每个数值字段都有明确边界；类型错误、非有限数、整数小数和越界值分别回退。 */
internal object UltraHardConfigValidation {
	private val integerRanges = mapOf(
		"configVersion" to 2..100,
		"starvingAfterDays" to 0..36_500,
		"durabilityDamageMultiplier" to 1..16,
		"lifestealMaximumRatioLevel" to 1..255,
		"lifestealCooldownTicks" to 0..72_000,
		"desertPyramidLifestealOneWeight" to 0..1_000_000,
		"desertPyramidLifestealTwoWeight" to 0..1_000_000,
		"desertPyramidLifestealThreeWeight" to 0..1_000_000,
		"desertPyramidNoBookWeight" to 0..1_000_000,
		"raidGroups" to 1..8,
		"eighthWavePillagers" to 0..128,
		"eighthWaveVindicators" to 0..128,
		"eighthWaveWitches" to 0..128,
		"eighthWaveEvokers" to 0..128,
		"eighthWaveRavagers" to 0..128,
		"eighthWaveLifestealBookLevel" to 1..255,
		"damageCapParticleCount" to 0..256,
		"aiUpdateIntervalTicks" to 1..1_200,
		"targetLockTicks" to 0..1_200,
		"undeadShelterSearchRadius" to 0..16,
		"undeadShelterSearchIntervalTicks" to 1..1_200,
	)
	private val fractionFields = setOf(
		"playerAttackCapFraction", "playerAttackOverflowMultiplier", "lifestealBaseRatio",
		"lifestealMaximumRatio", "eighthWaveEnchantedGoldenAppleChance",
	)

	fun validate(source: JsonObject, defaults: JsonObject): JsonObject {
		val result = source.deepCopy()
		for ((key, defaultValue) in defaults.entrySet()) {
			val value = result.get(key)
			val numeric = runCatching {
				if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) null else value.asDouble
			}.getOrNull()
			val integerRange = integerRanges[key]
			val range = when {
				integerRange != null -> integerRange.first.toDouble()..integerRange.last.toDouble()
				key in fractionFields -> 0.0..1.0
				key == "sleepHealingAmount" -> 0.0..1_024.0
				key == "skeletonArrowInaccuracy" -> 0.0..180.0
				key == "targetScanRadius" -> 1.0..64.0
				key == "threatMaximum" -> 1.0..1_000.0
				key.startsWith("threat") || key.startsWith("target") -> 0.0..100.0
				key.endsWith("Speed") -> 0.01..4.0
				key.endsWith("Distance") || key.endsWith("Radius") -> 0.0..64.0
				else -> 0.0..16.0
			}
			val validInteger = integerRange == null || runCatching { value.asBigDecimal.toBigIntegerExact(); true }.getOrDefault(false)
			if (numeric == null || !numeric.isFinite() || numeric !in range || !validInteger) {
				UltraHardMod.LOGGER.warn("配置项 {} 的值 {} 无效，允许范围 {}，本次使用默认值 {}。", key, value, range, defaultValue)
				result.add(key, defaultValue.deepCopy())
			}
		}
		// 合法的单项权重也可能组合成空奖池，整组回退避免抽取时没有可选项。
		val weights = listOf("desertPyramidLifestealOneWeight", "desertPyramidLifestealTwoWeight", "desertPyramidLifestealThreeWeight", "desertPyramidNoBookWeight")
		if (weights.sumOf { result.get(it).asInt } == 0) {
			weights.forEach { result.add(it, defaults.get(it).deepCopy()) }
			UltraHardMod.LOGGER.warn("沙漠神殿吸血奖池总权重为零，本次使用默认权重。")
		}
		return result
	}
}
