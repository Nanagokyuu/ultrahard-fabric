package com.nanagokyuu.ultrahard.config

/**
 * 服务端玩法数值。首次运行会在 config/ultrahard.json 写出完整配置；修改后重启游戏或服务器生效。
 */
data class UltraHardConfig(
	// 用于一次性迁移旧默认值；后续读取不覆盖玩家自行调整过的配置。
	val configVersion: Int = 4,
	// 消耗倍率作用于 exhaustion，不是每次直接扣除饥饿条；未进食天数按每昼夜 24000 游戏刻换算。
	val hungerExhaustionMultiplier: Float = 1.25f,
	val starvingExhaustionMultiplier: Float = 1.75f,
	val starvingAfterDays: Int = 2,
	// 软上限先保留最大生命值的一定比例，再折算超出部分；两个比例分别控制阈值和超额收益。
	val playerAttackCapFraction: Float = 0.25f,
	val playerAttackOverflowMultiplier: Float = 0.5f,
	// 敌人伤害随受击玩家的护甲值连续增长，额外伤害最多为 80%。
	val armorDamageScaling: Float = 0.04f,
	val maximumArmorDamageBonus: Float = 0.8f,
	// 倍增单次耐久消耗，不修改物品的最大耐久；金制工具和金制盔甲由注入代码排除。
	val durabilityDamageMultiplier: Int = 2,
	val mobArmorSpawnMultiplier: Float = 1.5f,
	// 睡满五秒后每日治疗一次；治疗量为最大生命值比例，并限制在最小/最大值之间。
	val sleepHealingFraction: Float = 0.4f,
	val sleepHealingMinimum: Float = 6.0f,
	val sleepHealingMaximum: Float = 12.0f,
	// 当前生命的战斗经验用于降低敌人伤害；死亡或切换到创造/旁观后清零。
	val killDamageReductionPerExperience: Float = 0.000125f,
	val killDamageExperienceCap: Int = 2_000,
	val killDamageMinimumMultiplier: Float = 0.75f,
	val killDamageExperienceMilestone: Int = 200,
	// 未达封顶等级时，吸血比例为基础比例乘以“等级 + 1”；达到封顶等级则直接使用最大比例。
	val lifestealBaseRatio: Float = 0.1f,
	val lifestealMaximumRatioLevel: Int = 9,
	val lifestealMaximumRatio: Float = 1.0f,
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
	// 仇恨按实际伤害积累并按秒衰减；最终优先级同时扣除装备和距离成本。
	val targetScanRadius: Double = 32.0,
	val threatPerDamage: Double = 2.0,
	val threatDecayPerSecond: Double = 1.0,
	val threatMaximum: Double = 40.0,
	val targetEquipmentPenalty: Double = 0.6,
	val targetDistancePenalty: Double = 0.35,
	val targetSwitchMargin: Double = 4.0,
	val targetLockTicks: Int = 60,
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
	val witchBacklineDistance: Double = 8.0,
	val witchHealRadius: Double = 20.0,
	val witchRetreatSpeed: Double = 1.15,
	val undeadShelterSearchRadius: Int = 8,
	val undeadShelterSearchIntervalTicks: Int = 20,
	val undeadShelterSpeed: Double = 1.1,
	val eliteSingleChance: Float = 0.25f,
	val eliteDoubleChance: Float = 0.05f,
	val eliteTripleChance: Float = 0.01f,
	val eliteExperiencePerAffix: Int = 10,
	val eliteHunterRangeBonus: Double = 8.0,
	val eliteToughArmorBonus: Double = 4.0,
	// 血月默认每八个世界日出现一次；奖励和亡灵潮均在服务端结算。
	val bloodMoonCycleDays: Int = 8,
	val bloodMoonNightMultiplier: Float = 3.0f,
	val bloodMoonKillThreshold: Int = 20,
	val bloodMoonSpawnIntervalTicks: Int = 40,
	val bloodMoonSpawnPerInterval: Int = 2,
	val eliteEnchantedBookChance: Float = 0.18f,
	val eliteEnchantedGoldenAppleChance: Float = 0.03f,
)
