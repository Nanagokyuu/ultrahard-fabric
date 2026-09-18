package com.nanagokyuu.ultrahard;

/**
 * 服务端玩家长期状态的访问契约，由 ServerPlayerStateMixin 注入实现。
 * 这些值会写入玩家存档，并在玩家实体替换时由事件代码复制；临时战斗状态不放在这里。
 * 时间字段以 Long.MIN_VALUE 表示尚未记录，不可直接与正常游戏时间相减。
 */
public interface UltraHardPlayerState {
	/** 是否已经获得过任意铁盔甲。 */
	boolean ultrahardHasIronMilestone();
	void ultrahardSetIronMilestone(boolean value);
	/** 是否已经获得过任意钻石盔甲。 */
	boolean ultrahardHasDiamondMilestone();
	void ultrahardSetDiamondMilestone(boolean value);
	/** 上一次进食时的世界 tick，用于计算长期未进食倍率。 */
	long ultrahardGetLastFoodTick();
	void ultrahardSetLastFoodTick(long value);
	/** 上一次睡眠治疗结算对应的主世界昼夜日期。 */
	long ultrahardGetLastSleepHealingDay();
	void ultrahardSetLastSleepHealingDay(long value);
	/** 是否已经向玩家发放过规则书。 */
	boolean ultrahardHasReceivedRulesBook();
	void ultrahardSetReceivedRulesBook(boolean value);
}
