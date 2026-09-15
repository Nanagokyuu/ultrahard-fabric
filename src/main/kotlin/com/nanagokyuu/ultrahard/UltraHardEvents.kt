package com.nanagokyuu.ultrahard

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.Monster

object UltraHardEvents {
	private val bypassOneShot = ThreadLocal.withInitial { false }

	fun register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, _amount ->
			if (bypassOneShot.get()) {
				return@register true
			}

			val level = entity.level()
			if (level !is ServerLevel || !UltraHardDifficulties.isUltraHard(level)) {
				return@register true
			}

			val attacker = source.entity

			// Hostile mob damage instantly kills the player
			if (entity is ServerPlayer && isHostile(attacker)) {
				oneShot(entity, level, source)
				return@register false
			}

			// Ultra Hard player attacks any LivingEntity -> one-shot kill
			if (attacker is ServerPlayer && entity !== attacker) {
				oneShot(entity, level, source)
				return@register false
			}

			true
		}
	}

	private fun isHostile(entity: Entity?): Boolean {
		return entity is Enemy || entity is Monster
	}

	private fun oneShot(
		entity: LivingEntity,
		level: ServerLevel,
		source: net.minecraft.world.damagesource.DamageSource,
	) {
		if (!entity.isAlive) return
		bypassOneShot.set(true)
		try {
			entity.hurtServer(level, source, Float.MAX_VALUE)
		} finally {
			bypassOneShot.set(false)
		}
		if (entity.isAlive) {
			entity.kill(level)
		}
	}
}
