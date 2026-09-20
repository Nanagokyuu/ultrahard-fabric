package com.nanagokyuu.ultrahard.events

import com.nanagokyuu.ultrahard.events.damage.UltraHardDamageEvents
import com.nanagokyuu.ultrahard.events.player.UltraHardPlayerEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player

/** 超困难事件的兼容门面；具体逻辑按伤害和玩家生命周期拆分到子包。 */
object UltraHardEvents {
	fun register() {
		UltraHardDamageEvents.register()
		UltraHardPlayerEvents.register()
	}

	@JvmStatic
	fun hungerExhaustionMultiplier(player: Player): Float =
		UltraHardPlayerEvents.hungerExhaustionMultiplier(player)

	@JvmStatic
	fun recordEating(player: Player) = UltraHardPlayerEvents.recordEating(player)

	@JvmStatic
	fun copyPlayerState(oldPlayer: ServerPlayer, newPlayer: ServerPlayer) =
		UltraHardPlayerEvents.copyPlayerState(oldPlayer, newPlayer)
}
