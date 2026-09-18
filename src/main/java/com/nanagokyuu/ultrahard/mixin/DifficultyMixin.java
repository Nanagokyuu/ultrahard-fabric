package com.nanagokyuu.ultrahard.mixin;

import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 通过 Fabric 枚举扩展向 {@link Difficulty} 添加 {@code ULTRAHARD}。
 * 构造参数为 id=4、key="ultrahard"，与字符串表示和 /difficulty 命令保持一致。
 */
@Mixin(Difficulty.class)
public enum DifficultyMixin {
	ULTRAHARD(4, "ultrahard");

	@Shadow
	DifficultyMixin(int id, String key) {
	}
}
