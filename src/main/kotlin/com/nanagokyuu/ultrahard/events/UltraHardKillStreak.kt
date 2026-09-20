package com.nanagokyuu.ultrahard.events

import com.nanagokyuu.ultrahard.UltraHardPlayerState
import com.nanagokyuu.ultrahard.config.UltraHardConfigs
import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties
import com.nanagokyuu.ultrahard.equipment.UltraHardEquipment
import com.nanagokyuu.ultrahard.elite.EliteMob
import com.nanagokyuu.ultrahard.network.CombatExperiencePayload
import java.util.UUID
import java.util.WeakHashMap
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.level.GameType
import kotlin.math.ceil

/** 负责当前生命战斗经验、死亡总结和模式切换清零；经验本身由玩家存档持久化。 */
internal object UltraHardKillStreak {
	/** 记录上一帧的游戏模式，用于检测进入创造或旁观并清空当前生命战斗经验。 */
	private val previousGameModes = HashMap<UUID, GameType>()
	/** 按玩家实体缓存上次同步值，重生或重登的新实体会自动收到完整快照。 */
	private val syncedExperience = WeakHashMap<ServerPlayer, CombatExperiencePayload>()

	fun register() {
		// 生物死亡后再统计击杀，确保只有真正死亡的敌对生物才会增加战斗经验。
		ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
			if (entity is ServerPlayer) {
				val wasUltraHard = UltraHardDifficulties.isUltraHard(entity.level())
				val experience = lifeCombatExperience(entity)
				val correction = (1.0f - UltraHardEquipment.killDamageMultiplier(entity)) * 100.0f
				resetCombatExperience(
					entity,
					if (wasUltraHard) "本次生命结束：战斗经验 ${experience}，敌人伤害修正 -${formatPercent(correction)}%，已清零。" else null,
				)
				return@register
			}
			if (entity.level() !is ServerLevel || !UltraHardDifficulties.isUltraHard(entity.level())) return@register
			if (!isHostile(entity)) return@register
			val killer = resolvePlayerKiller(source) ?: return@register
			if (killer.isCreative || killer.isSpectator) return@register
			addCombatExperience(killer, entity)
		}

	}

	/** 经验变化后立即同步，避免 HUD 等到下次击杀或重新登录才刷新。 */
	fun syncCombatExperience(player: ServerPlayer) {
		if (ServerPlayNetworking.canSend(player, CombatExperiencePayload.TYPE)) {
			val config = UltraHardConfigs.values
			val experience = (player as UltraHardPlayerState).ultrahardGetLifeCombatExperience()
			val active = UltraHardDifficulties.isUltraHard(player.level()) && !player.isCreative && !player.isSpectator && player.isAlive
			val payload = CombatExperiencePayload(experience, config.killDamageExperienceCap, active)
			if (syncedExperience[player] == payload) return
			ServerPlayNetworking.send(player, payload)
			syncedExperience[player] = payload
		}
	}

	/** 返回玩家当前生命累计的战斗经验。 */
	private fun lifeCombatExperience(player: ServerPlayer): Int =
		(player as UltraHardPlayerState).ultrahardGetLifeCombatExperience()

	/** 按被击杀怪物的最大生命值增加经验，并在达到经验节点时显示正反馈。 */
	private fun addCombatExperience(player: ServerPlayer, entity: Entity) {
		val state = player as UltraHardPlayerState
		val oldExperience = state.ultrahardGetLifeCombatExperience()
		val config = UltraHardConfigs.values
		val gainedExperience = if (isBoss(entity)) 1_000 else ceil((entity as net.minecraft.world.entity.LivingEntity).maxHealth.toDouble()).toInt().coerceIn(0, 100) + EliteMob.bonusExperience(entity)
		val newExperience = (oldExperience + gainedExperience).coerceAtMost(config.killDamageExperienceCap)
		state.ultrahardSetLifeCombatExperience(newExperience)
		syncCombatExperience(player)
		val interval = config.killDamageExperienceMilestone
		if (newExperience / interval <= oldExperience / interval) return

		val multiplier = UltraHardEquipment.killDamageMultiplier(player)
		val correction = (1.0f - multiplier) * 100.0f
		// 粒子和 Action Bar 都只在经验节点触发，不污染聊天框。
		player.level().sendParticles(
			ParticleTypes.HAPPY_VILLAGER,
			player.x,
			player.y + player.bbHeight * 0.5,
			player.z,
			12,
			0.35,
			0.5,
			0.35,
			0.05,
		)
		player.sendOverlayMessage(
			Component.literal("战斗磨炼：${newExperience}/${config.killDamageExperienceCap}｜敌人伤害修正：-${formatPercent(correction)}%"),
		)
	}

	/** 清空当前生命战斗经验，并在死亡时保留一条总结提示。 */
	private fun resetCombatExperience(player: ServerPlayer, message: String? = null) {
		val state = player as UltraHardPlayerState
		state.ultrahardSetLifeCombatExperience(0)
		syncCombatExperience(player)
		if (message != null) player.sendSystemMessage(Component.literal(message))
	}

	/** 只允许玩家本人或玩家发射的投射物获得战斗经验。 */
	private fun resolvePlayerKiller(source: net.minecraft.world.damagesource.DamageSource): ServerPlayer? {
		(source.entity as? ServerPlayer)?.let { return it }
		val projectile = source.directEntity as? Projectile ?: return null
		return projectile.owner as? ServerPlayer
	}

	/** 进入创造或旁观时立即清空战斗经验，避免通过指令保留战斗收益。 */
	fun trackGameMode(player: ServerPlayer) {
		val mode = player.gameMode()
		val previous = previousGameModes.put(player.uuid, mode)
		if (mode == GameType.CREATIVE || mode == GameType.SPECTATOR) {
			if (previous != mode || lifeCombatExperience(player) > 0) resetCombatExperience(player)
		}
	}

	private fun formatPercent(value: Float): String = "%.0f".format(value)

	/** 离开超困难时清空战斗经验；不额外发送聊天消息。 */
	fun clear(player: ServerPlayer) {
		if (lifeCombatExperience(player) > 0) resetCombatExperience(player)
	}

	/** 离线玩家只清理模式快照，不清理存档中的战斗经验。 */
	fun clearOffline(playerIds: Set<UUID>) {
		previousGameModes.keys.removeIf { it !in playerIds }
	}

	private fun isHostile(entity: Entity?): Boolean = entity is Enemy || entity is Monster

	private fun isBoss(entity: Entity): Boolean = entity is EnderDragon || entity is WitherBoss || entity is Warden
}
