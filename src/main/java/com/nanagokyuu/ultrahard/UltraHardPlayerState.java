package com.nanagokyuu.ultrahard;

/** 保存 Ultra Hard 服务端规则所需的玩家长期进度。 */
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
