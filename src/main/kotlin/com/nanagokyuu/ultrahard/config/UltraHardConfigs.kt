package com.nanagokyuu.ultrahard.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nanagokyuu.ultrahard.UltraHardMod
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import net.fabricmc.loader.api.FabricLoader

/** 配置只在初始化时加载；非法字段仅在内存回退，保留原值以便服主排查。 */
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
			var changed = false
			if (!configJson.has("configVersion")) {
				// 只迁移旧版默认的 25%；非默认比例视为服主的自定义设置并保留。
				if (runCatching { configJson.get("playerAttackOverflowMultiplier")?.asFloat }.getOrNull() == 0.25f) {
					configJson.addProperty("playerAttackOverflowMultiplier", 0.5f)
					changed = true
				}
			}
			// 旧版最低治疗字段已失效，移除以免误导服主。
			if (configJson.remove("lifestealMinimumHealing") != null) changed = true
			// 移除已被连续护甲倍率和新睡眠治疗模型替代的旧字段。
			for (key in listOf("enemyDamageBaseMultiplier", "enemyDamageAfterIronMultiplier", "enemyDamageAfterDiamondMultiplier", "sleepHealingAmount")) {
				if (configJson.remove(key) != null) changed = true
			}
			// 4 版将当前生命的击杀数量改为按怪物生命值累计的战斗经验。
			if (configJson.get("configVersion")?.asInt ?: 0 < 4) {
				for (key in listOf("killDamageReductionPerKill", "killDamageReductionMaximumKills", "killDamageMilestoneInterval")) {
					if (configJson.remove(key) != null) changed = true
				}
				configJson.addProperty("configVersion", 4)
				changed = true
			}
			if (addMissingDefaults(configJson, defaults)) changed = true
			if (changed) {
				Files.newBufferedWriter(path).use { writer: Writer -> gson.toJson(configJson, writer) }
			}
			// 先完成缺项补齐和版本迁移，再在副本中校验，不把非法原值覆盖掉。
			gson.fromJson(UltraHardConfigValidation.validate(configJson, defaults), UltraHardConfig::class.java)
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
