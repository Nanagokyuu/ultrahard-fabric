package com.nanagokyuu.ultrahard.recovery

import com.nanagokyuu.ultrahard.events.UltraHardEvents
import java.util.function.Consumer
import net.minecraft.ChatFormatting
import net.minecraft.advancements.triggers.CriteriaTriggers
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.item.ItemUtils
import net.minecraft.world.item.Items
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.level.Level

/** 自定义使用流程只在读条完成且治疗成功后消耗物品，松手、换手和受伤均不会提前扣除。 */
class RecoveryItem(properties: Properties, private val soup: Boolean) : Item(properties) {
	override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
		if (player is ServerPlayer) {
			val rejection = UltraHardRecovery.rejection(player, soup)
			if (rejection != null) {
				player.sendOverlayMessage(Component.translatable(rejection))
				return InteractionResult.FAIL
			}
		}
		return ItemUtils.startUsingInstantly(level, player, hand)
	}

	override fun getUseDuration(stack: ItemStack, entity: LivingEntity): Int = UltraHardRecovery.USE_TICKS

	override fun getUseAnimation(stack: ItemStack): ItemUseAnimation =
		if (soup) ItemUseAnimation.EAT else ItemUseAnimation.BRUSH

	override fun finishUsingItem(stack: ItemStack, level: Level, entity: LivingEntity): ItemStack {
		val player = entity as? ServerPlayer ?: return stack
		if (!UltraHardRecovery.begin(player, soup)) return stack
		CriteriaTriggers.CONSUME_ITEM.trigger(player, stack)
		player.awardStat(Stats.ITEM_USED.get(this))
		if (soup) {
			// 满饥饿也允许喝汤；食物收益和进食时钟仅在成功完成后更新，空碗沿用原版返还逻辑。
			stack.get(DataComponents.FOOD)?.let { player.foodData.eat(it) }
			UltraHardEvents.recordEating(player)
			return ItemUtils.createFilledResult(stack, player, ItemStack(Items.BOWL))
		}
		stack.consume(1, player)
		return stack
	}

	override fun appendHoverText(
		stack: ItemStack, context: TooltipContext, display: TooltipDisplay,
		tooltip: Consumer<Component>, flag: TooltipFlag,
	) {
		super.appendHoverText(stack, context, display, tooltip, flag)
		val prefix = if (soup) "tooltip.ultrahard.nourishing_soup" else "tooltip.ultrahard.simple_bandage"
		tooltip.accept(Component.translatable("$prefix.healing").withStyle(ChatFormatting.GRAY))
		tooltip.accept(Component.translatable("$prefix.limit").withStyle(ChatFormatting.GRAY))
		tooltip.accept(Component.translatable("tooltip.ultrahard.recovery_shared").withStyle(ChatFormatting.DARK_GRAY))
	}
}
