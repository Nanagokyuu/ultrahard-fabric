package com.nanagokyuu.ultrahard.command

import com.mojang.brigadier.CommandDispatcher
import com.nanagokyuu.ultrahard.bloodmoon.BloodMoon
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.permissions.Permissions

/** 超困难调试命令。所有子命令都要求权限等级 2。 */
object UltraHardCommands {
	fun register() {
		CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
			register(dispatcher)
		}
	}

	private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
		dispatcher.register(
			Commands.literal("ultrahard")
				.requires { source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }
				.then(
					Commands.literal("bloodmoon")
						.then(Commands.literal("start").executes { context ->
							val level = context.source.server.overworld()
							BloodMoon.startDebug(level)
							context.source.sendSuccess({ Component.literal("已将主世界推进到血月夜。") }, true)
							1
						})
						.then(Commands.literal("stop").executes { context ->
							BloodMoon.stopDebug(context.source.server.overworld())
							context.source.sendSuccess({ Component.literal("已结束当前血月调试状态。") }, true)
							1
						})
						.then(Commands.literal("status").executes { context ->
							val active = BloodMoon.isBloodMoon(context.source.server.overworld())
							context.source.sendSuccess({ Component.literal("血月状态：${if (active) "进行中" else "未进行"}。") }, false)
							if (active) 1 else 0
						})
				)
		)
	}
}
