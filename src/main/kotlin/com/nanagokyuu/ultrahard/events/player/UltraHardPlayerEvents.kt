package com.nanagokyuu.ultrahard.events.player

import com.nanagokyuu.ultrahard.UltraHardPlayerState
import com.nanagokyuu.ultrahard.config.UltraHardConfigs
import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties
import com.nanagokyuu.ultrahard.events.UltraHardKillStreak
import com.nanagokyuu.ultrahard.events.UltraHardLifesteal
import com.nanagokyuu.ultrahard.events.UltraHardSleep
import com.nanagokyuu.ultrahard.recovery.UltraHardRecovery
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player

/** 注册玩家 tick、难度切换清理以及长期玩家状态迁移。 */
internal object UltraHardPlayerEvents {
	fun register() {
		ServerTickEvents.END_SERVER_TICK.register { server ->
			val currentTick = server.overworld().gameTime
			for (player in server.playerList.players) {
				UltraHardKillStreak.trackGameMode(player)
				UltraHardKillStreak.syncCombatExperience(player)
				if (!UltraHardDifficulties.isUltraHard(player.level())) {
					UltraHardKillStreak.clear(player)
					UltraHardLifesteal.clear(player)
					UltraHardSleep.clear(player)
					continue
				}
				initializeFoodClock(player, currentTick)
				UltraHardSleep.updateSleepState(player)
				UltraHardSleep.healSleepingPlayer(player)
				UltraHardLifesteal.tick(player, currentTick)
			}
			val onlinePlayerIds = server.playerList.players.map { it.uuid }.toSet()
			UltraHardLifesteal.clearOffline(onlinePlayerIds)
			UltraHardSleep.clearOffline(onlinePlayerIds)
			UltraHardKillStreak.clearOffline(onlinePlayerIds)
		}
	}

	@JvmStatic
	fun hungerExhaustionMultiplier(player: Player): Float {
		if (player !is ServerPlayer || !UltraHardDifficulties.isUltraHard(player.level())) return 1.0f
		val state = player as UltraHardPlayerState
		val currentTick = player.level().gameTime
		if (state.ultrahardGetLastFoodTick() == Long.MIN_VALUE) return UltraHardConfigs.values.hungerExhaustionMultiplier
		val starvingTicks = UltraHardConfigs.values.starvingAfterDays.toLong() * TICKS_PER_DAY
		return if (currentTick - state.ultrahardGetLastFoodTick() > starvingTicks) {
			UltraHardConfigs.values.starvingExhaustionMultiplier
		} else {
			UltraHardConfigs.values.hungerExhaustionMultiplier
		}
	}

	@JvmStatic
	fun recordEating(player: Player) {
		if (player is ServerPlayer && UltraHardDifficulties.isUltraHard(player.level())) {
			(player as UltraHardPlayerState).ultrahardSetLastFoodTick(player.level().gameTime)
		}
	}

	fun copyPlayerState(oldPlayer: ServerPlayer, newPlayer: ServerPlayer) {
		val oldState = oldPlayer as UltraHardPlayerState
		val newState = newPlayer as UltraHardPlayerState
		newState.ultrahardSetLastFoodTick(oldState.ultrahardGetLastFoodTick())
		newState.ultrahardSetLastSleepHealingDay(oldState.ultrahardGetLastSleepHealingDay())
		newState.ultrahardSetLifeCombatExperience(if (oldPlayer.isAlive) oldState.ultrahardGetLifeCombatExperience() else 0)
		newState.ultrahardSetReceivedRulesBook(oldState.ultrahardHasReceivedRulesBook())
		newState.ultrahardSetSoupCooldownUntil(oldState.ultrahardGetSoupCooldownUntil())
		UltraHardRecovery.syncSoupCooldown(newPlayer)
	}

	private fun initializeFoodClock(player: ServerPlayer, currentTick: Long) {
		val state = player as UltraHardPlayerState
		if (state.ultrahardGetLastFoodTick() == Long.MIN_VALUE) state.ultrahardSetLastFoodTick(currentTick)
	}

	private const val TICKS_PER_DAY = 24_000L
}
