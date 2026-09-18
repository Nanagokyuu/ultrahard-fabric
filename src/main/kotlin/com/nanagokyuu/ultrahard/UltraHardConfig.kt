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
	// 消耗倍率作用于 exhaustion，不是每次直接扣除饥饿条；未进食天数按每昼夜 24000 游戏刻换算。
	val hungerExhaustionMultiplier: Float = 1.25f,
	val starvingExhaustionMultiplier: Float = 1.75f,
	val starvingAfterDays: Int = 2,
	// 软上限先保留最大生命值的一定比例，再折算超出部分；两个比例分别控制阈值和超额收益。
	val playerAttackCapFraction: Float = 0.25f,
	val playerAttackOverflowMultiplier: Float = 0.25f,
	// 按受击玩家曾获得的护甲阶段选择一个倍率，三档不叠乘；指定首领不使用此倍率。
	val enemyDamageBaseMultiplier: Float = 1.0f,
	val enemyDamageAfterIronMultiplier: Float = 1.5f,
	val enemyDamageAfterDiamondMultiplier: Float = 2.0f,
	// 倍增单次耐久消耗，不修改物品的最大耐久；金制工具和金制盔甲由注入代码排除。
	val durabilityDamageMultiplier: Int = 2,
	val mobArmorSpawnMultiplier: Float = 1.5f,
	// 睡满五秒且不足十点生命时的治疗量；至少十点生命时直接回满。
	val sleepHealingAmount: Float = 10.0f,
	// 未达封顶等级时，吸血比例为基础比例乘以“等级 + 1”；达到封顶等级则直接使用最大比例。
	val lifestealBaseRatio: Float = 0.1f,
	val lifestealMaximumRatioLevel: Int = 9,
	val lifestealMaximumRatio: Float = 1.0f,
	val lifestealMinimumHealing: Int = 1,
	val lifestealCooldownTicks: Int = 20,
	// 战利品池每次只抽取一个结果；实际概率为单项权重除以包含空奖在内的总权重。
	val desertPyramidLifestealOneWeight: Int = 5,
	val desertPyramidLifestealTwoWeight: Int = 4,
	val desertPyramidLifestealThreeWeight: Int = 3,
	val desertPyramidNoBookWeight: Int = 88,
	// 兵种数量用于配置指定的最终波次；字段名沿用“第八波”，默认总波次为 8。
	val raidGroups: Int = 8,
	val eighthWavePillagers: Int = 4,
	val eighthWaveVindicators: Int = 4,
	val eighthWaveWitches: Int = 2,
	val eighthWaveEvokers: Int = 2,
	val eighthWaveRavagers: Int = 3,
	val eighthWaveLifestealBookLevel: Int = 3,
	val eighthWaveEnchantedGoldenAppleChance: Float = 0.5f,
	val damageCapParticleCount: Int = 8,
	// 时间单位为游戏刻，距离单位为格；取模用的更新间隔必须大于零，导航速度为倍率。
	val aiUpdateIntervalTicks: Int = 10,
	val hostileRetaliationSuppressionRadius: Double = 32.0,
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

/** 配置只在初始化时加载；补齐缺失字段不会覆盖已有值，也不会自动校验所有数值范围。 */
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

	/** 读取失败时记录错误并在内存使用默认值，不用默认配置覆盖损坏文件，便于人工排查。 */
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
