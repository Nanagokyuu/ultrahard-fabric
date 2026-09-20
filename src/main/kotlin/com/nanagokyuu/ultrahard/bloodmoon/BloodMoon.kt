package com.nanagokyuu.ultrahard.bloodmoon

import com.nanagokyuu.ultrahard.config.UltraHardConfigs
import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties
import com.nanagokyuu.ultrahard.nightlord.NightLord
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.Level
import net.minecraft.world.clock.WorldClocks
import java.util.UUID

/**
 * 血月事件的服务端状态机。
 *
 * 满月周期按八天计算；预告、击杀统计、亡灵潮和奖励均由服务端决定，客户端只负责显示月亮颜色。
 * 血月期间把主世界时钟速率调整为三分之一，因此实体仍然正常 tick，只有昼夜时钟变慢。
 */
object BloodMoon {
	private const val DAY_TICKS = 24_000L
	private const val NIGHT_START = 13_000L
	private const val NIGHT_END = 23_000L

	private val states = HashMap<ServerLevel, BloodMoonSavedState>()
	/** 只在速率真正变化时写入世界时钟，避免每 tick 重发时钟状态包。 */
	private val appliedClockRates = HashMap<ServerLevel, Float>()
	private val lastWarningDays = HashSet<Long>()
	private val undeadIds = listOf(
		"zombie", "zombie_villager", "husk", "drowned", "skeleton", "stray", "bogged", "wither_skeleton", "phantom",
	)

	fun register() {
		ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
			recordKill(entity, source.entity ?: source.directEntity)
		}
		ServerPlayerEvents.JOIN.register { player -> deliverPendingReward(player) }
		ServerTickEvents.START_SERVER_TICK.register { server ->
			val level = server.overworld()
		if (!UltraHardDifficulties.isUltraHard(level)) {
				resetClockRate(server, level)
				return@register
			}
			tick(level, server)
		}
	}

	/** 将主世界时钟推进到下一次血月夜，供服主快速验证血月玩法。 */
	fun startDebug(level: ServerLevel) {
		val server = level.server
		val currentTime = level.getOverworldClockTime()
		val currentDay = Math.floorDiv(currentTime, DAY_TICKS)
		val cycle = UltraHardConfigs.values.bloodMoonCycleDays.toLong()
		val bloodMoonDay = currentDay + Math.floorMod(-currentDay, cycle)
		setClockTime(server, level, bloodMoonDay * DAY_TICKS + NIGHT_START)
		state(level).resetForDay(bloodMoonDay)
	}

	/** 结束调试血月并把时钟推进到下一天，避免下一刻再次触发奖励结算。 */
	fun stopDebug(level: ServerLevel) {
		val server = level.server
		val time = level.getOverworldClockTime()
		val day = Math.floorDiv(time, DAY_TICKS)
		setClockTime(server, level, (day + 1) * DAY_TICKS)
		val state = state(level)
		state.started = false
		state.endedNaturally = true
		state.skipped = true
		state.setDirty()
		resetClockRate(server, level)
	}

	/** 当前世界时钟是否处于血月夜，用于客户端染红月亮和其它玩法判断。 */
	@JvmStatic
	fun isBloodMoon(level: Level): Boolean {
		if (level.dimension() != Level.OVERWORLD) return false
		val time = level.getOverworldClockTime()
		val day = Math.floorDiv(time, DAY_TICKS)
		val timeOfDay = Math.floorMod(time, DAY_TICKS)
		return isBloodMoonDay(day) && timeOfDay in NIGHT_START until NIGHT_END
	}

	private fun state(level: ServerLevel): BloodMoonSavedState =
		states.getOrPut(level) { BloodMoonSavedState.get(level) }

	private fun tick(level: ServerLevel, server: MinecraftServer) {
		val time = level.getOverworldClockTime()
		val day = Math.floorDiv(time, DAY_TICKS)
		val timeOfDay = Math.floorMod(time, DAY_TICKS)
		val state = state(level)
		NightLord.clearFinishedDays(day)

		warnBeforeBloodMoon(level, day, timeOfDay)
		if (!isBloodMoonDay(day)) {
			// 如果玩家睡过血月，时间会直接跳到下一天；此时 started 仍为 true，
			// 标记 skipped 后清空状态，保证之前的击杀数不会兑换奖励。
			if (state.started && !state.endedNaturally) state.skipped = true
			if (state.started || state.bloodMoonDay != Long.MIN_VALUE) {
				state.started = false
				state.bloodMoonDay = Long.MIN_VALUE
				state.setDirty()
			}
			resetClockRate(server, level)
			return
		}

		if (state.bloodMoonDay != day) {
			state.resetForDay(day)
		}

		if (timeOfDay in NIGHT_START until NIGHT_END) {
			if (!state.started) {
				state.started = true
				state.setDirty()
				announce(level, "血月升起了！亡灵潮正在逼近。", ChatFormatting.DARK_RED)
			}
			setClockRate(server, level, 1.0f / UltraHardConfigs.values.bloodMoonNightMultiplier)
			if (level.gameTime % UltraHardConfigs.values.bloodMoonSpawnIntervalTicks == 0L) spawnUndeadTide(level)
		} else {
			// 夜之主的正式实体尚未注册；未来实体只需带上预留标签，
			// 这里就会暂停世界时钟，让血月永远停在夜晚直到它被击杀。
			if (state.started && timeOfDay >= NIGHT_END && NightLord.isPresent(level)) {
				setClockRate(server, level, 0.0f)
				return
			}
			if (state.started && timeOfDay >= NIGHT_END && !state.endedNaturally) {
				state.endedNaturally = true
				state.setDirty()
				resetClockRate(server, level)
				finishRewards(level, state)
				announce(level, "血月结束了。", ChatFormatting.GRAY)
			} else if (state.started && timeOfDay < NIGHT_START && state.bloodMoonDay == day) {
				state.skipped = true
				state.setDirty()
			}
		}
	}

	private fun warnBeforeBloodMoon(level: ServerLevel, day: Long, timeOfDay: Long) {
		if (timeOfDay !in NIGHT_START until NIGHT_END) return
		val cycle = UltraHardConfigs.values.bloodMoonCycleDays.toLong()
		val daysUntilBloodMoon = Math.floorMod(-day, cycle)
		if (daysUntilBloodMoon in 1..2 && lastWarningDays.add(day)) {
			announce(level, "不祥的红光正在接近……血月将在 $daysUntilBloodMoon 天后降临。", ChatFormatting.RED)
		}
	}

	private fun isBloodMoonDay(day: Long): Boolean =
		Math.floorMod(day, UltraHardConfigs.values.bloodMoonCycleDays.toLong()) == 0L

	private fun setClockRate(server: MinecraftServer, level: ServerLevel, rate: Float) {
		if (appliedClockRates[level] == rate) return
		val holder = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD)
		level.clockManager().setRate(holder, rate)
		appliedClockRates[level] = rate
	}

	private fun setClockTime(server: MinecraftServer, level: ServerLevel, time: Long) {
		val holder = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD)
		level.clockManager().setTotalTicks(holder, time)
		appliedClockRates.remove(level)
	}

	private fun resetClockRate(server: MinecraftServer, level: ServerLevel) {
		// 速率恢复为 1 后，普通昼夜和睡眠跳夜回到原版速度。
		setClockRate(server, level, 1.0f)
	}

	private fun spawnUndeadTide(level: ServerLevel) {
		// 每个刷新间隔按玩家分别抽取位置和兵种，避免所有亡灵潮堆在同一坐标。
		val players = level.players().filter { !it.isCreative && !it.isSpectator && it.isAlive }
		if (players.isEmpty()) return
		val typeRegistry = level.registryAccess().lookupOrThrow(Registries.ENTITY_TYPE)
		val random = level.random
		for (player in players) {
			repeat(UltraHardConfigs.values.bloodMoonSpawnPerInterval) {
				val angle = random.nextDouble() * Math.PI * 2.0
				val distance = 10 + random.nextInt(9)
				val x = player.blockPosition().x + (kotlin.math.cos(angle) * distance).toInt()
				val z = player.blockPosition().z + (kotlin.math.sin(angle) * distance).toInt()
				val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
				val pos = BlockPos(x, y, z)
				val id = undeadIds[random.nextInt(undeadIds.size)]
				val key = ResourceKey.create(Registries.ENTITY_TYPE, net.minecraft.resources.Identifier.withDefaultNamespace(id))
				val type = typeRegistry.getOrThrow(key).value()
				if (canSpawnNear(level, player, pos, type)) type.spawn(level, pos, EntitySpawnReason.EVENT)
			}
		}
	}

	private fun canSpawnNear(level: ServerLevel, player: ServerPlayer, pos: BlockPos, type: EntityType<*>): Boolean {
		if (player.blockPosition().distSqr(pos) < 64.0) return false
		if (!level.isInWorldBounds(pos) || !level.getBlockState(pos).isAir) return false
		return type.canSpawn(level)
	}

	private fun recordKill(entity: LivingEntity, killerEntity: Entity?) {
		// 只统计血月正在进行时、主世界内由玩家完成的普通敌对生物击杀。
		val level = entity.level() as? ServerLevel ?: return
		if (!isBloodMoon(level) || level.dimension() != Level.OVERWORLD || isBoss(entity) || !isHostile(entity)) return
		val player = killerEntity as? ServerPlayer
			?: (killerEntity as? Projectile)?.owner as? ServerPlayer
			?: return
		val state = state(level)
		state.kills[player.uuid] = (state.kills[player.uuid] ?: 0) + 1
		state.setDirty()
	}

	private fun finishRewards(level: ServerLevel, state: BloodMoonSavedState) {
		// 奖励按玩家独立统计；睡眠跳夜会提前标记 skipped，因此这里直接不发任何奖励。
		if (state.skipped) return
		val threshold = UltraHardConfigs.values.bloodMoonKillThreshold
		for ((uuid, kills) in state.kills) {
			if (kills < threshold) continue
			val player = level.server.playerList.getPlayer(uuid)
			if (player == null) {
				state.pendingRewards[uuid] = (state.pendingRewards[uuid] ?: 0) + 1
				state.setDirty()
				continue
			}
			giveReward(player, level, kills)
		}
	}

	private fun deliverPendingReward(player: ServerPlayer) {
		val state = state(player.level().server.overworld())
		val count = state.pendingRewards.remove(player.uuid) ?: return
		state.setDirty()
		val level = player.level()
		repeat(count) { giveReward(player, level, UltraHardConfigs.values.bloodMoonKillThreshold) }
	}

	private fun giveReward(player: ServerPlayer, level: ServerLevel, kills: Int) {
		val reward = randomEnchantedBook(level) ?: return
		if (!player.inventory.add(reward)) player.drop(reward, false)
		player.sendSystemMessage(Component.literal("你在血月中击杀了 $kills 个怪物，获得了随机附魔书。"))
	}

	private fun randomEnchantedBook(level: ServerLevel): ItemStack? {
		val holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getRandom(level.random).orElse(null) ?: return null
		val enchantment = holder.value()
		return ItemStack(Items.ENCHANTED_BOOK).also { it.enchant(holder, level.random.nextInt(enchantment.getMinLevel(), enchantment.getMaxLevel() + 1)) }
	}

	private fun announce(level: ServerLevel, message: String, color: ChatFormatting) {
		level.players().forEach { it.sendSystemMessage(Component.literal(message).withStyle(color)) }
	}

	private fun isHostile(entity: Entity): Boolean = entity is Enemy || entity is Monster
	private fun isBoss(entity: Entity): Boolean = entity is net.minecraft.world.entity.boss.enderdragon.EnderDragon || entity is net.minecraft.world.entity.boss.wither.WitherBoss || entity is net.minecraft.world.entity.monster.warden.Warden
}
