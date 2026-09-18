package com.nanagokyuu.ultrahard

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path

/**
 * 服务端玩法数值。首次运行会在 config/ultrahard.json 写出完整配置；修改后重启游戏或服务器生效。
 */
data class UltraHardConfig(
	val hungerExhaustionMultiplier: Float = 1.25f,
	val starvingExhaustionMultiplier: Float = 1.75f,
	val starvingAfterDays: Int = 2,
	val playerAttackCapFraction: Float = 0.25f,
	val playerAttackOverflowMultiplier: Float = 0.25f,
	val enemyDamageBaseMultiplier: Float = 1.0f,
	val enemyDamageAfterIronMultiplier: Float = 1.5f,
	val enemyDamageAfterDiamondMultiplier: Float = 2.0f,
	val durabilityDamageMultiplier: Int = 2,
	val mobArmorSpawnMultiplier: Float = 1.5f,
	// 睡满五秒且不足十点生命时的治疗量；至少十点生命时直接回满。
	val sleepHealingAmount: Float = 10.0f,
	val lifestealBaseRatio: Float = 0.1f,
	val lifestealMaximumRatioLevel: Int = 9,
	val lifestealMaximumRatio: Float = 1.0f,
	val lifestealMinimumHealing: Int = 1,
	val lifestealCooldownTicks: Int = 20,
	val desertPyramidLifestealOneWeight: Int = 5,
	val desertPyramidLifestealTwoWeight: Int = 4,
	val desertPyramidLifestealThreeWeight: Int = 3,
	val desertPyramidNoBookWeight: Int = 88,
	val raidGroups: Int = 8,
	val eighthWavePillagers: Int = 4,
	val eighthWaveVindicators: Int = 4,
	val eighthWaveWitches: Int = 2,
	val eighthWaveEvokers: Int = 2,
	val eighthWaveRavagers: Int = 3,
	val eighthWaveLifestealBookLevel: Int = 3,
	val eighthWaveEnchantedGoldenAppleChance: Float = 0.5f,
	val damageCapParticleCount: Int = 8,
	val aiUpdateIntervalTicks: Int = 10,
	val zombieCoordinationRadius: Double = 10.0,
	val zombieFlankDistance: Double = 2.5,
	val zombieFrontDistance: Double = 2.0,
	val zombieUnarmedYieldDistance: Double = 3.5,
	val zombieCoordinationSpeed: Double = 1.15,
	val skeletonMinimumDistance: Double = 4.0,
	val skeletonRetreatDistance: Double = 7.0,
	val skeletonRetreatSpeed: Double = 1.2,
	val skeletonArrowInaccuracy: Float = 0.0f,
	val creeperEvacuationRadius: Double = 6.0,
	val creeperEvacuationDistance: Double = 7.0,
	val creeperEvacuationSpeed: Double = 1.2,
	val witchBacklineDistance: Double = 8.0,
	val witchHealRadius: Double = 20.0,
	val witchRetreatSpeed: Double = 1.15,
	val undeadShelterSearchRadius: Int = 8,
	val undeadShelterSearchIntervalTicks: Int = 20,
	val undeadShelterSpeed: Double = 1.1,
)

object UltraHardConfigs {
	private val gson = GsonBuilder().setPrettyPrinting().create()
	private val path: Path = FabricLoader.getInstance().configDir.resolve("ultrahard.json")

	@Volatile
	@get:JvmStatic
	lateinit var values: UltraHardConfig
		private set

	fun load() {
		values = if (Files.exists(path)) {
			read(path)
		} else {
			UltraHardConfig().also(::write)
		}
	}

	private fun read(path: Path): UltraHardConfig {
		return try {
			val configJson = Files.newBufferedReader(path).use { reader ->
				JsonParser.parseReader(reader).asJsonObject
			}
			// 旧配置缺少新字段时，Gson 会为基础类型填零值；先补齐默认值再反序列化。
			val defaults = gson.toJsonTree(UltraHardConfig()).asJsonObject
			if (addMissingDefaults(configJson, defaults)) {
				Files.newBufferedWriter(path).use { writer: Writer -> gson.toJson(configJson, writer) }
			}
			gson.fromJson(configJson, UltraHardConfig::class.java)
		} catch (exception: Exception) {
			UltraHardMod.LOGGER.error("Could not read {}, using defaults", path, exception)
			UltraHardConfig()
		}
	}

	private fun addMissingDefaults(configJson: JsonObject, defaults: JsonObject): Boolean {
		var changed = false
		// 不覆盖已有配置，也保留将来版本或第三方写入的未知字段。
		for ((key, value) in defaults.entrySet()) {
			if (!configJson.has(key)) {
				configJson.add(key, value)
				changed = true
			}
		}
		return changed
	}

	private fun write(config: UltraHardConfig) {
		Files.createDirectories(path.parent)
		Files.newBufferedWriter(path).use { writer: Writer -> gson.toJson(config, writer) }
	}
}
