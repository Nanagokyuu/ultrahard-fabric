package com.nanagokyuu.ultrahard.ai.combat

import com.nanagokyuu.ultrahard.ai.AiSupport
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.monster.Endermite
import net.minecraft.world.entity.monster.Ravager
import net.minecraft.world.entity.monster.Silverfish
import net.minecraft.world.entity.monster.illager.Vindicator
import net.minecraft.world.entity.monster.spider.Spider
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

/** 管理蜘蛛、卫道士、劫掠兽和末影类生物的特殊战术行为。 */
internal object CombatAiSpecials {
	@JvmStatic
	fun tickSpider(spider: Spider) {
		// 蜘蛛优先从玩家视线外接近；玩家正面举盾时，直接切换为绕盾路线。
		if (!AiSupport.shouldUpdate(spider)) return
		val target = spider.target ?: return
		if (CombatAi.shouldFlankShield(spider, target)) {
			CombatAi.flankShield(spider, target, 1.25)
		} else if (!AiSupport.isBlockingTarget(target) && target.distanceToSqr(spider) > 9.0) {
			AiSupport.ambushApproach(spider, target, 2.5, 1.25)
		}
	}

	@JvmStatic
	fun tickVindicator(vindicator: Vindicator) {
		// 卫道士不在盾牌正面连续挥斧，先通过侧后方接近制造攻击角度。
		if (!AiSupport.shouldUpdate(vindicator)) return
		val target = vindicator.target ?: return
		if (CombatAi.shouldFlankShield(vindicator, target)) {
			CombatAi.flankShield(vindicator, target, 1.2)
		} else if (!AiSupport.isBlockingTarget(target) && target.distanceToSqr(vindicator) > 6.25) {
			AiSupport.ambushApproach(vindicator, target, 2.5, 1.2)
		}
	}

	@JvmStatic
	fun tickRavager(ravager: Ravager) {
		// 劫掠兽体型较大，使用更宽的侧翼路线，避免一直顶着盾牌冲撞。
		if (!AiSupport.shouldUpdate(ravager)) return
		val target = ravager.target ?: return
		if (CombatAi.shouldFlankShield(ravager, target)) {
			CombatAi.flankShield(ravager, target, 1.1)
		} else if (!AiSupport.isBlockingTarget(target) && target.distanceToSqr(ravager) > 16.0) {
			AiSupport.ambushApproach(ravager, target, 3.5, 1.1)
		}
	}

	@JvmStatic
	fun tickEnderman(enderman: EnderMan) {
		val level = AiSupport.ultraHardLevel(enderman) ?: return
		val target = enderman.target as? Player ?: return
		if (!target.isAlive) return

		val playerToEnderman = AiSupport.horizontalDirection(enderman.position().subtract(target.position()))
		val playerFacingEnderman = AiSupport.horizontalDirection(target.lookAngle).dot(playerToEnderman) > 0.6
		val shieldedFront = CombatAi.shouldFlankShield(enderman, target)
		// 玩家直视或正面举盾时，末影人不正面硬打，而是瞬移到盲区。
		if (!playerFacingEnderman && !shieldedFront && AiSupport.isBlockingTarget(target)) return
		val now = level.gameTime
		if (AiSupport.endermanTeleportCooldowns[enderman.uuid]?.let { now < it } == true) return

		val facing = AiSupport.horizontalDirection(target.lookAngle)
		val left = Vec3(-facing.z, 0.0, facing.x)
		val side = if (enderman.id % 2 == 0) 1.0 else -1.0
		val radius = if (shieldedFront) 3.5 else 3.0
		val destinations = listOf(
			target.position().subtract(facing.scale(radius)).add(left.scale(side)),
			target.position().subtract(facing.scale(radius)).add(left.scale(-side)),
			target.position().add(left.scale(side * radius)),
		)
		for (destination in destinations) {
			if (AiSupport.tryEndermanTeleport(enderman, destination)) {
				AiSupport.endermanTeleportCooldowns[enderman.uuid] = now + 20L
				return
			}
		}
	}

	@JvmStatic
	fun tickSilverfish(silverfish: Silverfish) {
		if (!AiSupport.shouldUpdate(silverfish)) return
		val level = AiSupport.ultraHardLevel(silverfish) ?: return
		val target = silverfish.target ?: return
		val group = level.getEntities(silverfish, silverfish.boundingBox.inflate(8.0)) { entity ->
			entity is Silverfish && entity.target === target && entity.isAlive
		}.filterIsInstance<Silverfish>().plus(silverfish).distinctBy { it.uuid }.sortedBy { it.uuid }
		val index = group.indexOf(silverfish).coerceAtLeast(0)
		// 蠹虫保留少量正面牵制单位，其余单位从侧面和背后包围玩家。
		if (CombatAi.shouldFlankShield(silverfish, target)) {
			if (index > 0 || group.size == 1) CombatAi.flankShield(silverfish, target, 1.3)
		} else if (!AiSupport.isBlockingTarget(target)) {
			AiSupport.swarmApproach(silverfish, target, index, group.size, 2.5, 1.3)
		}
	}

	@JvmStatic
	fun tickEndermite(endermite: Endermite) {
		if (!AiSupport.shouldUpdate(endermite)) return
		val target = endermite.target ?: return
		// 末影螨不瞬移，而是依靠高速侧移和频繁换位骚扰玩家。
		if (CombatAi.shouldFlankShield(endermite, target)) {
			CombatAi.flankShield(endermite, target, 1.35)
		} else if (!AiSupport.isBlockingTarget(target)) {
			AiSupport.ambushApproach(endermite, target, 2.0, 1.35)
		}
	}
}
