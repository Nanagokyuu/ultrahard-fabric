package com.nanagokyuu.ultrahard

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

object UltraHardCommands {
	fun register() {
		CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, environment ->
			registerCommands(dispatcher, registryAccess, environment)
		}
	}

	private fun registerCommands(
		dispatcher: CommandDispatcher<CommandSourceStack>,
		@Suppress("UNUSED_PARAMETER") registryAccess: CommandBuildContext,
		@Suppress("UNUSED_PARAMETER") environment: Commands.CommandSelection,
	) {
		dispatcher.register(
			Commands.literal("ultrahard")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("on").executes { setUltraHard(it, true) })
				.then(Commands.literal("off").executes { setUltraHard(it, false) })
				.then(Commands.literal("status").executes { status(it) })
				.executes { status(it) }
		)
	}

	private fun setUltraHard(context: CommandContext<CommandSourceStack>, enabled: Boolean): Int {
		val source = context.source
		val level = source.level
		val server = source.server
		level.gameRules.set(UltraHardGameRules.ULTRA_HARD, enabled, server)
		val key = if (enabled) "command.ultrahard.enabled" else "command.ultrahard.disabled"
		source.sendSuccess({ Component.translatable(key) }, true)
		return Command.SINGLE_SUCCESS
	}

	private fun status(context: CommandContext<CommandSourceStack>): Int {
		val source = context.source
		val enabled = UltraHardGameRules.isEnabled(source.level)
		val key = if (enabled) "command.ultrahard.status.on" else "command.ultrahard.status.off"
		source.sendSuccess({ Component.translatable(key) }, false)
		return Command.SINGLE_SUCCESS
	}
}
