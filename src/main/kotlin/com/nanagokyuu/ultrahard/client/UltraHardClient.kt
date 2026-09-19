package com.nanagokyuu.ultrahard.client

import com.mojang.blaze3d.platform.InputConstants
import com.nanagokyuu.ultrahard.UltraHardMod
import com.nanagokyuu.ultrahard.network.CombatExperiencePayload
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/** 客户端经验预览：按“=”键切换 HUD 显示，不改变服务端经验或伤害计算。 */
object UltraHardClient : ClientModInitializer {
	private val togglePreview = KeyMapping(
		"key.ultrahard.toggle_experience_preview",
		InputConstants.Type.KEYSYM,
		InputConstants.KEY_EQUALS,
		KeyMapping.Category.MISC,
	)
	private var previewVisible = false
	private var experience = 0
	private var maximum = 2000
	private var active = false

	override fun onInitializeClient() {
		KeyMappingHelper.registerKeyMapping(togglePreview)
		ClientPlayNetworking.registerGlobalReceiver(CombatExperiencePayload.TYPE) { payload, _ ->
			experience = payload.experience.coerceAtLeast(0)
			maximum = payload.maximum.coerceAtLeast(1)
			active = payload.active
		}
		// 仅在连接结束时清理快照，避免换维度的短暂无玩家状态丢失同步值。
		ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
			experience = 0
			active = false
		}
		ClientTickEvents.END_CLIENT_TICK.register { client ->
			while (togglePreview.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) previewVisible = !previewVisible
			}
		}
		HudElementRegistry.attachElementAfter(
			VanillaHudElements.BOSS_BAR,
			UltraHardMod.id("combat_experience_preview"),
		) { graphics, _ -> renderPreview(graphics, Minecraft.getInstance()) }
	}

	/** 左上角绘制数值和百分比进度，避开原版生命、护甲及经验条。 */
	private fun renderPreview(graphics: GuiGraphicsExtractor, client: Minecraft) {
		if (!previewVisible || !active || client.player == null || client.gui.screen() != null) return
		val progress = (experience.toFloat() / maximum).coerceIn(0.0f, 1.0f)
		val label = Component.translatable("hud.ultrahard.combat_experience", experience, maximum, (progress * 100).toInt())
		val barWidth = maxOf(160, client.font.width(label)).coerceAtMost(graphics.guiWidth() - 16)
		val left = 8
		val bottom = 26
		graphics.fill(left - 4, bottom - 18, left + barWidth + 4, bottom + 12, 0x99000000.toInt())
		graphics.text(client.font, label, left, bottom - 14, 0xFFFFFFFF.toInt())
		graphics.fill(left, bottom + 1, left + barWidth, bottom + 7, 0xFF202020.toInt())
		graphics.fill(left, bottom + 1, left + (barWidth * progress).toInt(), bottom + 7, 0xFF55CC55.toInt())
	}
}
