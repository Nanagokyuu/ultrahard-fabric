package com.nanagokyuu.ultrahard.elite

import com.nanagokyuu.ultrahard.config.UltraHardConfigs
import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.core.registries.Registries
import java.util.Collections
import java.util.Random

/** 精英怪的词条、名称、粒子、奖励经验和亡语。词条通过实体标签持久化。 */
object EliteMob {
	private const val ELITE_TAG = "ultrahard_elite"
	private const val AFFIX_PREFIX = "ultrahard_affix_"
	private const val HUNTER_MODIFIER = "ultrahard_hunter_range"
	private const val TOUGH_MODIFIER = "ultrahard_tough_armor"

	private val affixes = listOf(Affix.SWIFT, Affix.HUNTER, Affix.TOUGH, Affix.BLOODTHIRSTY, Affix.UNYIELDING, Affix.NIGHT_WALKER, Affix.DEATH_CALL)

	fun register() {
		ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
			if (entity is Mob) {
				if (hasAffix(entity, Affix.DEATH_CALL)) spawnDeathCall(entity)
				if (isElite(entity)) dropRareReward(entity)
			}
		}
		ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, source, _, damage, blocked ->
			if (blocked || damage <= 0.0f) return@register
			val attacker = source.entity as? Mob ?: return@register
			if (hasAffix(attacker, Affix.BLOODTHIRSTY)) attacker.heal(damage * 0.2f)
		}
		ServerTickEvents.END_SERVER_TICK.register { server ->
			for (level in server.allLevels) {
				for (entity in level.allEntities) {
					val mob = entity as? Mob ?: continue
					if (!isElite(mob)) continue
					if (level.gameTime % 10L == Math.floorMod(mob.getId().toLong(), 10L)) spawnParticles(level, mob)
					applyNightWalker(mob)
				}
			}
		}
	}

	@JvmStatic
	fun assignOnSpawn(mob: Mob) {
		val level = mob.level() as? ServerLevel ?: return
		if (!UltraHardDifficulties.isUltraHard(level) || isBoss(mob) || !isMonster(mob) || mob.entityTags().contains(ELITE_TAG)) return
		mob.addTag(ELITE_TAG)
		val random = mob.random
		val config = UltraHardConfigs.values
		val count = when {
			random.nextFloat() < config.eliteTripleChance -> 3
			random.nextFloat() < config.eliteDoubleChance -> 2
			random.nextFloat() < config.eliteSingleChance -> 1
			else -> 0
		}
		if (count == 0) return
		val candidates = affixes.filter { it != Affix.DEATH_CALL || isAdultZombie(mob) }.toMutableList()
		Collections.shuffle(candidates, Random(random.nextLong()))
		candidates.take(count).forEach { mob.addTag(AFFIX_PREFIX + it.id) }
		configure(mob)
		setDisplayName(mob)
	}

	fun isElite(mob: Mob): Boolean = mob.entityTags().contains(ELITE_TAG) && affixes.any { hasAffix(mob, it) }

	fun bonusExperience(entity: Entity): Int {
		val mob = entity as? Mob ?: return 0
		return affixes.count { hasAffix(mob, it) } * UltraHardConfigs.values.eliteExperiencePerAffix
	}

	private fun configure(mob: Mob) {
		if (hasAffix(mob, Affix.SWIFT)) mob.addEffect(MobEffectInstance(MobEffects.SPEED, 2_000_000_000, 0, true, false, true))
		if (hasAffix(mob, Affix.HUNTER)) {
			mob.getAttribute(Attributes.FOLLOW_RANGE)?.addOrReplacePermanentModifier(
				AttributeModifier(Identifier.fromNamespaceAndPath("ultrahard", HUNTER_MODIFIER), UltraHardConfigs.values.eliteHunterRangeBonus, AttributeModifier.Operation.ADD_VALUE),
			)
		}
		if (hasAffix(mob, Affix.TOUGH)) {
			mob.getAttribute(Attributes.ARMOR)?.addOrReplacePermanentModifier(
				AttributeModifier(Identifier.fromNamespaceAndPath("ultrahard", TOUGH_MODIFIER), UltraHardConfigs.values.eliteToughArmorBonus, AttributeModifier.Operation.ADD_VALUE),
			)
		}
		if (hasAffix(mob, Affix.UNYIELDING)) mob.addEffect(MobEffectInstance(MobEffects.RESISTANCE, 2_000_000_000, 0, true, false, true))
	}

	private fun applyNightWalker(mob: Mob) {
		if (!hasAffix(mob, Affix.NIGHT_WALKER)) return
		val level = mob.level() as? ServerLevel ?: return
		val time = Math.floorMod(level.getLevelData().getGameTime(), 24_000L)
		if (time >= 13_000L && time < 23_000L) mob.addEffect(MobEffectInstance(MobEffects.SPEED, 30, 1, true, false, true))
		else mob.removeEffect(MobEffects.SPEED)
	}

	private fun spawnParticles(level: ServerLevel, mob: Mob) {
		val particle = when {
			hasAffix(mob, Affix.NIGHT_WALKER) -> ParticleTypes.SOUL_FIRE_FLAME
			hasAffix(mob, Affix.BLOODTHIRSTY) -> ParticleTypes.DAMAGE_INDICATOR
			hasAffix(mob, Affix.UNYIELDING) -> ParticleTypes.ENCHANT
			else -> ParticleTypes.HAPPY_VILLAGER
		}
		level.sendParticles(particle, mob.x, mob.y + mob.bbHeight + 0.15, mob.z, 2, 0.18, 0.12, 0.18, 0.0)
	}

	private fun spawnDeathCall(mob: Mob) {
		if (!isAdultZombie(mob)) return
		val level = mob.level() as? ServerLevel ?: return
		repeat(2) { index ->
			val child = mob.type.create(level, EntitySpawnReason.TRIGGERED) as? Zombie ?: return@repeat
			child.setPos(mob.x + (index * 2 - 1) * 0.45, mob.y, mob.z + (1 - index) * 0.45)
			child.setBaby(true)
			level.addFreshEntity(child)
		}
		level.sendParticles(ParticleTypes.SOUL, mob.x, mob.y + 0.5, mob.z, 12, 0.35, 0.35, 0.35, 0.04)
	}

	private fun setDisplayName(mob: Mob) {
		val display = Component.empty()
		for (affix in affixes) if (hasAffix(mob, affix)) display.append(Component.literal(affix.displayName).withStyle(affix.color))
		display.append(mob.name.copy().withStyle(ChatFormatting.WHITE))
		mob.customName = display
		mob.setCustomNameVisible(true)
	}

	private fun hasAffix(mob: Mob, affix: Affix): Boolean = mob.entityTags().contains(AFFIX_PREFIX + affix.id)
	private fun isMonster(entity: Entity): Boolean = entity is Enemy || entity is Monster
	private fun isAdultZombie(entity: Mob): Boolean = entity is Zombie && !entity.isBaby
	private fun isBoss(entity: Entity): Boolean = entity is EnderDragon || entity is WitherBoss || entity is Warden

	private fun dropRareReward(mob: Mob) {
		val level = mob.level() as? ServerLevel ?: return
		val random = mob.random.nextFloat()
		// 附魔书的总概率高于附魔金苹果；附魔种类和等级从当前世界注册表随机选择。
		val reward = when {
			random < UltraHardConfigs.values.eliteEnchantedGoldenAppleChance -> ItemStack(Items.ENCHANTED_GOLDEN_APPLE)
		 random < UltraHardConfigs.values.eliteEnchantedGoldenAppleChance + UltraHardConfigs.values.eliteEnchantedBookChance -> randomEnchantedBook(level)
		else -> return
		}
		if (reward != null) mob.spawnAtLocation(level, reward)
	}

	private fun randomEnchantedBook(level: ServerLevel): ItemStack? {
		val holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getRandom(level.random).orElse(null) ?: return null
		val enchantment = holder.value()
		return ItemStack(Items.ENCHANTED_BOOK).also {
			it.enchant(holder, level.random.nextInt(enchantment.getMinLevel(), enchantment.getMaxLevel() + 1))
		}
	}

	private enum class Affix(val id: String, val displayName: String, val color: ChatFormatting) {
		SWIFT("swift", "迅捷", ChatFormatting.AQUA),
		HUNTER("hunter", "猎手", ChatFormatting.YELLOW),
		TOUGH("tough", "坚甲", ChatFormatting.GRAY),
		BLOODTHIRSTY("bloodthirsty", "嗜血", ChatFormatting.DARK_RED),
		UNYIELDING("unyielding", "不屈", ChatFormatting.GOLD),
		NIGHT_WALKER("night_walker", "夜行", ChatFormatting.DARK_PURPLE),
		DEATH_CALL("death_call", "亡语", ChatFormatting.DARK_GREEN),
	}
}
