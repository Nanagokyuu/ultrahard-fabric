package com.nanagokyuu.ultrahard.rules

import com.nanagokyuu.ultrahard.UltraHardPlayerState
import com.nanagokyuu.ultrahard.config.UltraHardConfigs
import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.Filterable
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.WrittenBookContent

/**
 * 在玩家加入超困难世界时发放玩法说明，领取标记与玩家存档一起保存。
 * 书页在发放时读取配置并生成文本快照；之后修改配置不会更新已经发出的书。
 */
object UltraHardRulesBook {
	/** 背包满时掉落书籍，随后仍标记已领取，避免玩家反复重登刷取规则书。 */
	fun giveTo(player: ServerPlayer) {
		if (!UltraHardDifficulties.isUltraHard(player.level())) return
		val state = player as UltraHardPlayerState
		if (state.ultrahardHasReceivedRulesBook()) return
		val config = UltraHardConfigs.values

		val book = ItemStack(Items.WRITTEN_BOOK)
		book.set(
			DataComponents.WRITTEN_BOOK_CONTENT,
			WrittenBookContent(
				Filterable.passThrough("超困难规则"),
				"Ultra Hard",
				0,
				UltraHardRulePages.create(config),
				false,
			),
		)
		if (!player.inventory.add(book)) {
			player.drop(book, false)
		}
		state.ultrahardSetReceivedRulesBook(true)
	}
}
