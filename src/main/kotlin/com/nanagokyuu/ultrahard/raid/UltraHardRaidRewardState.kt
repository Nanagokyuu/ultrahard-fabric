package com.nanagokyuu.ultrahard.raid

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.nanagokyuu.ultrahard.UltraHardMod
import java.util.UUID
import net.minecraft.core.UUIDUtil
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType

/** 将离线袭击奖励保存到主世界存档，避免重启服务器后丢失。 */
class UltraHardRaidRewardState(initial: Map<UUID, Reward> = emptyMap()) : SavedData() {
	data class Reward(val books: Int = 0, val apples: Int = 0, val totems: Int = 0) {
		companion object {
			val CODEC: Codec<Reward> = RecordCodecBuilder.create { instance ->
				instance.group(
					Codec.INT.fieldOf("books").forGetter(Reward::books),
					Codec.INT.fieldOf("apples").forGetter(Reward::apples),
					Codec.INT.fieldOf("totems").forGetter(Reward::totems),
				).apply(instance, ::Reward)
			}
		}
	}

	private val rewards = initial.toMutableMap()

	fun add(player: UUID, giveApple: Boolean) {
		val old = rewards[player] ?: Reward()
		rewards[player] = if (giveApple) old.copy(books = old.books + 1, apples = old.apples + 1)
		else old.copy(books = old.books + 1, totems = old.totems + 1)
		setDirty()
	}

	fun take(player: UUID): Reward? = rewards.remove(player)?.also { setDirty() }

	companion object {
		private val TYPE = SavedDataType(
			UltraHardMod.id("raid_rewards"),
			::UltraHardRaidRewardState,
			RecordCodecBuilder.create { instance ->
				instance.group(
					Codec.unboundedMap(UUIDUtil.STRING_CODEC, Reward.CODEC)
						.fieldOf("rewards")
						.forGetter { state -> state.rewards.toMap() },
				).apply(instance, ::UltraHardRaidRewardState)
			},
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE,
		)

		private fun state(level: ServerLevel): UltraHardRaidRewardState =
			level.server.overworld().dataStorage.computeIfAbsent(TYPE)

		@JvmStatic
		fun queue(level: ServerLevel, player: UUID, giveApple: Boolean) = state(level).add(player, giveApple)

		@JvmStatic
		fun take(level: ServerLevel, player: UUID): Reward? = state(level).take(player)
	}
}
