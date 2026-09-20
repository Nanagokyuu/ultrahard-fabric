package com.nanagokyuu.ultrahard.nightlord

import net.minecraft.server.level.ServerLevel

/**
 * 夜之主的预留接口。
 *
 * 现在只记录未来 Boss 所需的身份和每个血月夜的自然生成限制，不注册实体、不设计技能，
 * 后续可以在这里接入真正的 EntityType、属性、渲染器和生成条件，而不需要重写血月系统。
 */
object NightLord {
	const val ENTITY_ID = "night_lord"
	private const val ENTITY_TAG = "ultrahard_night_lord"

	private val spawnedBloodMoonDays = HashSet<Long>()

	fun canNaturallySpawn(level: ServerLevel, bloodMoonDay: Long): Boolean =
		level.dimension() == net.minecraft.world.level.Level.OVERWORLD && bloodMoonDay !in spawnedBloodMoonDays

	fun markNaturallySpawned(bloodMoonDay: Long) {
		spawnedBloodMoonDays += bloodMoonDay
	}

	/** 未来的夜之主实体只要带上这个标签，血月系统就会自动进入永久夜晚。 */
	fun isPresent(level: ServerLevel): Boolean =
		level.allEntities.any { it.entityTags().contains(ENTITY_TAG) && it.isAlive }

	fun clearFinishedDays(currentDay: Long) {
		spawnedBloodMoonDays.removeIf { it < currentDay - 2 }
	}
}
