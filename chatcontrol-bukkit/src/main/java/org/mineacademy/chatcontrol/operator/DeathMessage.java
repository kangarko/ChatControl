package org.mineacademy.chatcontrol.operator;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.PlaceholderPrefix;
import org.mineacademy.chatcontrol.model.PlayerMessageType;
import org.mineacademy.chatcontrol.model.WrappedSender;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.exception.FoScriptException;
import org.mineacademy.fo.exception.MissingEnumException;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.IsInList;
import org.mineacademy.fo.model.JavaScriptExecutor;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.region.DiskRegion;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.Remain;

import lombok.Getter;
import net.kyori.adventure.text.Component;

/**
 * Represents join, leave, kick or timed message broadcast
 */
@Getter
public final class DeathMessage extends PlayerMessage {

	/**
	 * Permission required for the killer that caused the rule to fire in
	 * order for the rule to apply
	 */
	@Nullable
	private Tuple<String, String> requireKillerPermission;

	/**
	 * JavaScript boolean output required to be true for the rule to apply
	 */
	@Nullable
	private String requireKillerScript;

	/**
	 * World names to require
	 */
	private final Set<String> requireKillerWorlds = new HashSet<>();

	/**
	 * Region names to require
	 */
	private final Set<String> requireKillerRegions = new HashSet<>();

	/**
	 * Killer items to require
	 */
	private final Set<String> requireKillerItems = new HashSet<>();

	/**
	 * Damage causes to require
	 */
	private final Set<DamageCause> requireCauses = new HashSet<>();

	/**
	 * Projectile types to require
	 */
	private final Set<EntityType> requireProjectiles = new HashSet<>();

	/**
	 * Blocks that caused damage to require
	 */
	private final Set<CompMaterial> requireBlocks = new HashSet<>();

	/**
	 * Killer types to require
	 */
	private final Set<EntityType> requireKillers = new HashSet<>();

	/**
	 * Bosses from the Boss plugin to require
	 */
	@Nullable
	private IsInList<String> requireBosses;

	/**
	 * The minimum damage to require
	 */
	private Double requireDamage;

	/**
	 * Permission to bypass the rule
	 */
	@Nullable
	private String ignoreKillerPermission;

	/**
	 * JavaScript boolean output when true for the rule to bypass
	 */
	@Nullable
	private String ignoreKillerScript;

	/**
	 * Gamemodes to ignore
	 */
	private final Set<GameMode> ignoreKillerGamemodes = new HashSet<>();

	/**
	 * World names to ignore
	 */
	private final Set<String> ignoreKillerWorlds = new HashSet<>();

	/**
	 * Region names to ignore
	 */
	private final Set<String> ignoreKillerRegions = new HashSet<>();

	/**
	 * Killer items to ignore
	 */
	private final Set<String> ignoreKillerItems = new HashSet<>();

	/**
	 * List of channels to ignore matching from
	 */
	private final Set<String> ignoreKillerChannels = new HashSet<>();

	/**
	 * Should we ignore NPCs from Citizens?
	 */
	private boolean requireNpc;

	/**
	 * Should we require the dead player to be a NPCs from Citizens?
	 */
	private boolean ignoreNpc;

	public DeathMessage(final String group) {
		super(PlayerMessageType.DEATH, group);
	}

	/**
	 * @see org.mineacademy.chatcontrol.operator.PlayerMessage#onParse(java.lang.String, java.lang.String, java.lang.String[])
	 */
	@Override
	protected boolean onParse(String firstThreeParams, String theRestThree, final String[] args) {
		firstThreeParams = CommonCore.joinRange(0, 3, args, " ");
		theRestThree = CommonCore.joinRange(3, args);
		final List<String> theRestThreeSplit = splitVertically(theRestThree);

		final String firstTwoParams = CommonCore.joinRange(0, 2, args);
		final String theRestTwo = CommonCore.joinRange(2, args);
		final List<String> theRestTwoSplit = splitVertically(theRestTwo);

		if ("require killer perm".equals(firstThreeParams) || "require killer permission".equals(firstThreeParams)) {
			this.checkNotSet(this.requireKillerPermission, "require killer perm");
			final String[] split = theRestThree.split(" ");

			this.requireKillerPermission = new Tuple<>(split[0], split.length > 1 ? CommonCore.joinRange(1, split) : null);
		}

		else if ("require killer script".equals(firstThreeParams)) {
			this.checkNotSet(this.requireKillerScript, "require sender script");

			this.requireKillerScript = theRestThree;
		}

		else if ("require killer world".equals(firstThreeParams) || "require killer worlds".equals(firstThreeParams))
			this.requireKillerWorlds.addAll(theRestThreeSplit);

		else if ("require killer region".equals(firstThreeParams) || "require killer regions".equals(firstThreeParams))
			this.requireKillerRegions.addAll(theRestThreeSplit);

		else if ("require killer item".equals(firstThreeParams) || "require killer items".equals(firstThreeParams))
			for (final String name : theRestThreeSplit) {
				// Support wildcart
				if (name.contains("*"))
					this.requireKillerItems.add(name);

				else {
					final CompMaterial material = CompMaterial.fromString(name);

					if (material != null)
						this.requireKillerItems.add(material.name());
					else
						Common.warning("Invalid material in 'require killer item " + theRestThree + "' for death message (remove the rule if your MC version doesn't support this): " + this);
				}
			}

		else if ("require cause".equals(firstTwoParams) || "require causes".equals(firstTwoParams))
			for (final String name : theRestTwoSplit)
				try {
					final DamageCause cause = ReflectionUtil.lookupEnum(DamageCause.class, name);

					this.requireCauses.add(cause);

				} catch (final MissingEnumException ex) {
					if (!name.equals("WITHER") && !name.equals("WITHER") && !name.equals("THORNS") && !name.equals("FALLING_BLOCK"))
						throw ex;
				}

		else if ("require projectile".equals(firstTwoParams) || "require projectile".equals(firstTwoParams))
			for (final String name : theRestTwoSplit) {
				final EntityType type = ReflectionUtil.lookupEnum(EntityType.class, name);

				if (type != null)
					this.requireProjectiles.add(type);
				else
					Common.warning("Invalid entity in 'require projectile " + theRestTwo + "' for death message (remove the rule if your MC version doesn't support this): " + this);
			}

		else if ("require block".equals(firstTwoParams) || "require blocks".equals(firstTwoParams))
			for (final String name : theRestTwoSplit) {
				final CompMaterial type = CompMaterial.fromString(name);

				if (type != null)
					this.requireBlocks.add(type);
				else
					Common.warning("Invalid block in 'require block " + theRestTwo + "' for death message (remove the rule if your MC version doesn't support this): " + this);
			}

		else if ("require killer".equals(firstTwoParams) || "require killers".equals(firstTwoParams))
			for (final String name : theRestTwoSplit) {
				EntityType type = ReflectionUtil.lookupEnumSilent(EntityType.class, name.toUpperCase());

				// Convenience fix for 1.20.5+
				if (MinecraftVersion.atLeast(V.v1_20)) {
					if (type == null && name.equals("LIGHTNING"))
						type = ReflectionUtil.lookupEnumSilent(EntityType.class, "LIGHTNING_BOLT");

					if (type == null && name.equals("PRIMED_TNT"))
						type = ReflectionUtil.lookupEnumSilent(EntityType.class, "TNT");

					if (type == null && name.equals("ENDER_CRYSTAL"))
						type = ReflectionUtil.lookupEnumSilent(EntityType.class, "END_CRYSTAL");
				}

				if (MinecraftVersion.olderThan(V.v1_9)) {
					if ("DRAGON_FIREBALL".equals(name) || "ZOMBIE_VILLAGER".equals(name))
						continue;

					else if ("ZOMBIFIED_PIGLIN".equals(name))
						type = ReflectionUtil.lookupEnumSilent(EntityType.class, "PIG_ZOMBIE");
				}

				if (type != null)
					this.requireKillers.add(type);

				else if (!name.equals("WITHER_SKULL") && !name.equals("FIREWORK") && !name.equals("WITHER") && !name.equals("WITCH")) {
					final String note = MinecraftVersion.olderThan(V.v1_13) ? "You're on legacy Minecraft, chances are the entity does not exist. " : MinecraftVersion.atLeast(V.v1_20) ? "Minecraft 1.20.5 has updated entity types. " : "";

					CommonCore.warning("Invalid entity in 'require killer " + theRestTwo + "' for death message (" + note + "See https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/entity/EntityType.html for valid types): " + this);
				}
			}

		else if ("require boss".equals(firstTwoParams) || "require bosses".equals(firstTwoParams))
			this.requireBosses = IsInList.fromList(theRestTwoSplit);

		else if ("require damage".equals(firstTwoParams)) {
			this.checkNotSet(this.requireDamage, "require damage");
			checkBoolean(ValidCore.isDecimal(theRestTwo), "Wrong 'require damage' syntax, type only a whole decimal number such as 2.0 or 5.5");

			this.requireDamage = Double.parseDouble(theRestTwo);
		}

		else if ("require npc".equalsIgnoreCase(firstTwoParams)) {
			checkBoolean(!this.requireNpc, "Operator 'require npc' can only be used once in " + this);

			this.requireNpc = true;
		}

		else if ("ignore killer perm".equals(firstThreeParams) || "ignore killer permission".equals(firstThreeParams)) {
			this.checkNotSet(this.ignoreKillerPermission, "ignore sender perm");

			this.ignoreKillerPermission = theRestThree;
		}

		else if ("ignore killer script".equals(firstThreeParams)) {
			this.checkNotSet(this.ignoreKillerScript, "ignore sender script");

			this.ignoreKillerScript = theRestThree;
		}

		else if ("ignore killer gamemode".equals(firstThreeParams) || "ignore killer gamemodes".equals(firstThreeParams))
			for (final String modeName : theRestThreeSplit) {
				final GameMode gameMode = ReflectionUtil.lookupEnum(GameMode.class, modeName);

				this.ignoreKillerGamemodes.add(gameMode);
			}

		else if ("ignore killer world".equals(firstThreeParams) || "ignore killer worlds".equals(firstThreeParams))
			this.ignoreKillerWorlds.addAll(theRestThreeSplit);

		else if ("ignore killer region".equals(firstThreeParams) || "ignore killer regions".equals(firstThreeParams))
			this.ignoreKillerRegions.addAll(theRestThreeSplit);

		else if ("ignore killer item".equals(firstThreeParams) || "ignore killer items".equals(firstThreeParams))
			for (final String name : theRestThreeSplit) {

				// Support wildcart
				if (name.contains("*"))
					this.ignoreKillerItems.add(name);

				else {
					final CompMaterial material = CompMaterial.fromString(name);

					if (material != null)
						this.ignoreKillerItems.add(material.name());
					else
						Common.warning("Invalid material in 'ignore killer item " + theRestThree + "' for death message (remove the rule if your MC version doesn't support this): " + this);
				}
			}

		else if ("ignore killer channel".equals(firstThreeParams) || "ignore killer channels".equals(firstThreeParams))
			this.ignoreKillerChannels.addAll(theRestThreeSplit);

		else if ("ignore npc".equalsIgnoreCase(firstTwoParams)) {
			checkBoolean(!this.ignoreNpc, "Operator 'ignore npc' can only be used once in " + this);

			this.ignoreNpc = true;
		}

		else
			return super.onParse(firstThreeParams, theRestThree, args);

		return true;
	}

	/**
	 * Collect all options we have to debug
	 *
	 * @return
	 */
	@Override
	protected Map<String, Object> collectOptions() {
		final Map<String, Object> map = super.collectOptions();

		map.putAll(CommonCore.newHashMap(
				"Require Killer Permission", this.requireKillerPermission,
				"Require Killer Script", this.requireKillerScript,
				"Require Killer Worlds", this.requireKillerWorlds,
				"Require Killer Regions", this.requireKillerRegions,
				"Require Killer Items", this.requireKillerItems,
				"Require Causes", this.requireCauses,
				"Require Projectiles", this.requireProjectiles,
				"Require Blocks", this.requireBlocks,
				"Require Killers", this.requireKillers,
				"Require Bosses", this.requireBosses,
				"Require Damage", this.requireDamage,
				"Require NPC", this.requireNpc,
				"Ignore Killer Permission", this.ignoreKillerPermission,
				"Ignore Killer Script", this.ignoreKillerScript,
				"Ignore Killer Gamemodes", this.ignoreKillerGamemodes,
				"Ignore Killer Worlds", this.ignoreKillerWorlds,
				"Ignore Killer Regions", this.ignoreKillerRegions,
				"Ignore Killer Items", this.ignoreKillerItems,
				"Ignore Killer Channels", this.ignoreKillerChannels,
				"Ignore NPC", this.ignoreNpc));

		return map;
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Represents a check that is implemented by this class
	 */
	public static final class DeathMessageCheck extends PlayerMessageCheck<DeathMessage> {

		/**
		 * The killer entity
		 */
		@Nullable
		private final Entity killer;

		/**
		 * If the killer is man?
		 */
		private final boolean killerIsDude;

		/**
		 * If the killer is NPC?
		 */
		private final boolean killerIsNPC;

		/**
		 * The killer type or null
		 */
		@Nullable
		private EntityType killerType;

		/**
		 * The killer item in his hands or null
		 */
		@Nullable
		private ItemStack killerItemStack;

		/**
		 * The killer item in his hands or null
		 */
		@Nullable
		private CompMaterial killerItemMaterial;

		/**
		 * Get the damage cause
		 */
		private DamageCause damageCause;

		/**
		 * The projectile or null
		 */
		@Nullable
		private EntityType projectile;

		/**
		 * The block type or null
		 */
		@Nullable
		private CompMaterial blockType;

		/**
		 * If player died by a Boss this is his name
		 */
		@Nullable
		private String bossName = "";

		/**
		 * Create a new death message
		 *
		 * @param player
		 * @param originalMessage
		 */
		protected DeathMessageCheck(final WrappedSender wrapped, final String originalMessage) {
			super(PlayerMessageType.DEATH, wrapped, originalMessage);

			final Player player = wrapped.getPlayer();
			this.killer = player.getKiller() != null ? player.getKiller() : player.getLastDamageCause() instanceof EntityDamageByEntityEvent ? ((EntityDamageByEntityEvent) player.getLastDamageCause()).getDamager() : null;
			this.killerIsDude = this.killer instanceof Player;
			this.killerIsNPC = HookManager.isNPC(this.killer);
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.PlayerMessage.PlayerMessageCheck#canFilter(org.mineacademy.chatcontrol.operator.PlayerMessage)
		 */
		@Override
		protected boolean canFilter(final DeathMessage operator) {

			// ----------------------------------------------------------------
			// Prepare variables we use later
			// ----------------------------------------------------------------

			final Player killer = this.wrappedSender.getPlayer().getKiller();
			final EntityDamageEvent lastDamageCause = this.wrappedSender.getPlayer().getLastDamageCause();

			if (lastDamageCause == null) {
				CommonCore.logTimed(24 * 60 * 60, "Unexpected null last damage cause for " + this.wrappedSender.getName() + ", messages not broadcasted. This message appears once per 24 hours. This may be your combat logger plugin, in this case it is safe to ignore.");

				return false;
			}

			this.damageCause = lastDamageCause.getCause();

			if (lastDamageCause instanceof EntityDamageByEntityEvent) {
				final EntityDamageByEntityEvent event = (EntityDamageByEntityEvent) lastDamageCause;
				final Entity damager = event.getDamager();

				if (damager != null) {
					this.killerType = damager.getType();

					if (damager instanceof LivingEntity) {
						final ItemStack hand = ((LivingEntity) damager).getEquipment().getItemInHand();

						this.killerItemStack = hand;
						this.killerItemMaterial = CompMaterial.fromItem(hand);

						if (this.killerItemMaterial == null)
							this.killerItemMaterial = CompMaterial.fromString(hand.getType().name());
					}

					if (HookManager.isBossLoaded())
						this.bossName = HookManager.getBossName(damager);

					if ((this.bossName == null || this.bossName.isEmpty()) && HookManager.isMythicMobsLoaded())
						this.bossName = HookManager.getMythicMobName(damager);

					if (damager instanceof Projectile) {
						final ProjectileSource shooter = ((Projectile) damager).getShooter();

						this.projectile = damager.getType();

						if (shooter instanceof BlockProjectileSource) {
							final Block block = ((BlockProjectileSource) shooter).getBlock();

							this.blockType = CompMaterial.fromBlock(block);

						} else if (shooter instanceof LivingEntity) {
							final LivingEntity entity = (LivingEntity) shooter;

							this.killerType = entity.getType();

							final ItemStack hand = ((LivingEntity) shooter).getEquipment().getItemInHand();

							this.killerItemStack = hand;
							this.killerItemMaterial = CompMaterial.fromItem(hand);

							if (this.killerItemMaterial == null)
								this.killerItemMaterial = CompMaterial.fromString(hand.getType().name());
						}
					}
				}

				if (damager instanceof FallingBlock) {
					final FallingBlock falling = (FallingBlock) damager;

					this.blockType = CompMaterial.fromMaterial(falling.getMaterial());
				}
			}

			else if (lastDamageCause instanceof EntityDamageByBlockEvent) {
				final EntityDamageByBlockEvent event = (EntityDamageByBlockEvent) lastDamageCause;
				final Block block = event.getDamager();

				if (block != null)
					this.blockType = CompMaterial.fromBlock(block);
			}

			Debugger.debug("operator", "Cause: " + lastDamageCause.getCause() + " from " + lastDamageCause + ", Killer: " + this.killerType + ", Projectile: " + this.projectile + ", Block: " + this.blockType + ", Boss: " + this.bossName);

			// ----------------------------------------------------------------
			// Check for specific death require conditions
			// ----------------------------------------------------------------

			if (!operator.getRequireCauses().isEmpty() && !operator.getRequireCauses().contains(lastDamageCause.getCause()))
				return false;

			if (!operator.getRequireProjectiles().isEmpty() && (this.projectile == null || !operator.getRequireProjectiles().contains(this.projectile)))
				return false;

			if (!operator.getRequireBlocks().isEmpty() && (this.blockType == null || !operator.getRequireBlocks().contains(this.blockType)))
				return false;

			if (!operator.getRequireKillers().isEmpty() && (this.killerType == null || !operator.getRequireKillers().contains(this.killerType)))
				return false;

			if (operator.getRequireBosses() != null && !HookManager.isBossLoaded() && !HookManager.isMythicMobsLoaded())
				return false;

			if (operator.getRequireBosses() != null && (this.bossName == null || "".equals(this.bossName)))
				return false;

			if (operator.getRequireBosses() != null && !operator.getRequireBosses().contains(this.bossName))
				return false;

			if (operator.getRequireDamage() != null && Remain.getFinalDamage(lastDamageCause) < operator.getRequireDamage())
				return false;

			if (operator.isRequireNpc() && !HookManager.isNPC(this.wrappedSender.getPlayer()))
				return false;

			if (operator.isIgnoreNpc() && HookManager.isNPC(this.wrappedSender.getPlayer()))
				return false;

			if (killer != null && !this.killerIsNPC) {
				final WrappedSender wrappedKiller = WrappedSender.fromPlayer(killer);

				// ----------------------------------------------------------------
				// Require
				// ----------------------------------------------------------------

				if (this.killerIsDude && operator.getRequireKillerPermission() != null) {
					final String permission = operator.getRequireKillerPermission().getKey();
					final String noPermissionMessage = operator.getRequireKillerPermission().getValue();

					if (!killer.hasPermission(permission)) {
						if (noPermissionMessage != null) {
							this.wrappedReceiver.sendMessage(this.replaceSenderVariablesLegacy(noPermissionMessage, operator));

							throw new EventHandledException(true);
						}

						Debugger.debug("operator", "\tno required killer permission");
						return false;
					}
				}

				if (operator.getRequireKillerScript() != null) {
					final Object result;

					try {
						result = JavaScriptExecutor.run(this.replaceVariablesForLegacy(operator.getRequireKillerScript(), operator, wrappedKiller), CommonCore.newHashMap("player", this.wrappedSender.getPlayer(), "killer", killer));

					} catch (final FoScriptException ex) {
						CommonCore.logFramed(
								"Error parsing 'require killer script' in death message!",
								"Message " + operator.getUniqueName() + " in " + operator.getFile(),
								"",
								"Raw script: " + operator.getRequireKillerScript(),
								"Evaluated script with variables replaced: '" + this.replaceVariablesForLegacy(operator.getRequireKillerScript(), operator, wrappedKiller) + "'",
								"Sender: " + this.wrappedSender,
								"Error: " + ex.getMessage(),
								"",
								"Check that the evaluated script",
								"above is a valid JavaScript!");

						throw ex;
					}

					if (result != null) {
						checkBoolean(result instanceof Boolean, "require killer condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

						if (!((boolean) result)) {
							Debugger.debug("operator", "\tno required killer script");

							return false;
						}
					}
				}

				if (!operator.getRequireKillerWorlds().isEmpty() && !ValidCore.isInList(killer.getWorld().getName(), operator.getRequireKillerWorlds())) {
					Debugger.debug("operator", "\tno required killer worlds");

					return false;
				}

				if (!operator.getRequireKillerRegions().isEmpty()) {
					final List<String> regions = DiskRegion.findRegionNames(killer.getLocation());
					boolean found = false;

					for (final String requireRegionName : operator.getRequireKillerRegions())
						if (regions.contains(requireRegionName)) {
							found = true;

							break;
						}

					if (!found) {
						Debugger.debug("operator", "\tno required killer regions");

						return false;
					}
				}

				if (!operator.getRequireKillerItems().isEmpty()) {
					boolean found = false;

					if (this.killerItemMaterial == null)
						return false;

					final String killerItemName = this.killerItemMaterial.name().toLowerCase();

					for (final String requiredItem : operator.getRequireKillerItems()) {
						final String starless = requiredItem.replace("*", "").toLowerCase();

						final boolean matches = requiredItem.charAt(0) == '*' ? killerItemName.endsWith(starless)
								: requiredItem.charAt(requiredItem.length() - 1) == '*' ? killerItemName.startsWith(starless)
										: this.killerItemMaterial == CompMaterial.valueOf(requiredItem);

						if (matches) {
							found = true;

							break;
						}
					}

					if (!found)
						return false;
				}

				// ----------------------------------------------------------------
				// Ignore
				// ----------------------------------------------------------------

				if (this.killerIsDude && operator.getIgnoreKillerPermission() != null && killer.hasPermission(operator.getIgnoreKillerPermission())) {
					Debugger.debug("operator", "\tignore killer permission found");

					return false;
				}

				if (operator.getIgnoreKillerScript() != null) {
					final Object result;

					try {
						result = JavaScriptExecutor.run(this.replaceSenderVariablesLegacy(operator.getIgnoreKillerScript(), operator), CommonCore.newHashMap("player", this.wrappedSender.getPlayer(), "killer", killer));

					} catch (final FoScriptException ex) {
						CommonCore.logFramed(
								"Error parsing 'ignore killer script' in death message!",
								"Message " + operator.getUniqueName() + " in " + operator.getFile(),
								"",
								"Raw script: " + operator.getIgnoreKillerScript(),
								"Evaluated script with variables replaced: '" + this.replaceVariablesForLegacy(operator.getIgnoreKillerScript(), operator, wrappedKiller) + "'",
								"Sender: " + this.wrappedSender,
								"Error: " + ex.getMessage(),
								"",
								"Check that the evaluated script",
								"above is a valid JavaScript!");

						throw ex;
					}

					if (result != null) {
						checkBoolean(result instanceof Boolean, "ignore killer script condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for rule " + operator);

						if (((boolean) result)) {
							Debugger.debug("operator", "\tignore killer script found");

							return false;
						}
					}
				}

				if (this.killerIsDude && operator.getIgnoreKillerGamemodes().contains(killer.getGameMode())) {
					Debugger.debug("operator", "\tignore killer gamemodes found");

					return false;
				}

				if (operator.getIgnoreKillerWorlds().contains(killer.getWorld().getName())) {
					Debugger.debug("operator", "\tignore killer worlds found");

					return false;
				}

				for (final String playersRegion : DiskRegion.findRegionNames(killer.getLocation()))
					if (operator.getIgnoreKillerRegions().contains(playersRegion)) {
						Debugger.debug("operator", "\tignore killer regions found");

						return false;
					}

				if (this.killerItemMaterial != null) {
					final String killerItemName = this.killerItemMaterial.name().toLowerCase();

					for (final String ignoredItem : operator.getIgnoreKillerItems()) {
						final String starless = ignoredItem.replace("*", "").toLowerCase();

						final boolean matches = ignoredItem.charAt(0) == '*' ? killerItemName.endsWith(starless)
								: ignoredItem.charAt(ignoredItem.length() - 1) == '*' ? killerItemName.startsWith(starless) : this.killerItemMaterial == CompMaterial.valueOf(ignoredItem);

						if (matches)
							return false;
					}
				}

			} // end killer != null

			return super.canFilter(operator);
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.PlayerMessage.PlayerMessageCheck#prepareVariables(org.mineacademy.chatcontrol.operator.PlayerMessage)
		 */
		@Override
		protected Map<String, Object> prepareVariables(final WrappedSender wrapped, final DeathMessage operator) {
			final Map<String, Object> map = super.prepareVariables(wrapped, operator);
			final String killerItemFormatted = this.killerItemMaterial != null ? ChatUtil.capitalizeFully(this.killerItemMaterial.toString()) : "Air";
			final Component killerItemLabel = this.killerItemStack != null ? Component.text(killerItemFormatted).hoverEvent(Remain.convertItemStackToHoverEvent(this.killerItemStack)) : null;
			final String killerItemName = this.killerItemStack != null && this.killerItemStack.hasItemMeta() && this.killerItemStack.getItemMeta().hasDisplayName() ? this.killerItemStack.getItemMeta().getDisplayName() : killerItemFormatted;

			if (this.killer instanceof Player)
				map.putAll(SyncedCache.getPlaceholders(((Player) this.killer).getName(), ((Player) this.killer).getUniqueId(), PlaceholderPrefix.KILLER));

			map.putAll(CommonCore.newHashMap(
					// Override name even if the killer is a player due to some fixes
					"killer", this.killer == null ? "" : this.killer,
					"killer_name", this.killer == null ? "" : this.getKillerName(),

					"killer_type", this.killerType == null ? "" : ChatUtil.capitalizeFully(this.killerType),
					"killer_item", killerItemLabel == null ? "Air" : SimpleComponent.serializeAdventureToMini(killerItemLabel),
					"killer_item_name", killerItemName,
					"block_type", this.blockType == null ? "" : this.blockType.name(),
					"block_type_formatted", this.blockType == null ? "" : ChatUtil.capitalizeFully(this.blockType),
					"cause", this.damageCause.name(),
					"cause_formatted", ChatUtil.capitalizeFully(this.damageCause),
					"projectile", this.projectile == null ? "" : this.projectile.name(),
					"projectile_formatted", this.projectile == null ? "" : ChatUtil.capitalizeFully(this.projectile.name()),
					"boss_name", CommonCore.getOrDefault(this.bossName, "")));

			return map;
		}

		/*
		 * Get the properly formatted killer name for the death message
		 */
		private String getKillerName() {
			if (this.killer instanceof Player)
				return ((Player) this.killer).getName();

			final String fallback = ChatUtil.capitalizeFully(this.killerType);

			try {
				final String name = this.killer.isCustomNameVisible() ? this.killer.getCustomName() : this.killer.getName();

				// Return the custom name or fallback in case the name contains known health plugin letters
				return name.contains("♡") || name.contains("♥") || name.contains("❤") || name.contains("■") ? fallback : name;

			} catch (final Error err) {
				// MC version incompatible call for Entity#getName
				return fallback;
			}
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.PlayerMessage.PlayerMessageCheck#getMessagePlayerForVariables()
		 */
		@Override
		protected WrappedSender getMessagePlayerForVariables() {
			return this.killer instanceof Player && !this.killerIsNPC ? WrappedSender.fromPlayer((Player) this.killer) : this.wrappedSender;
		}

		/**
		 * @see org.mineacademy.chatcontrol.operator.Operator.OperatorCheck#getOperators()
		 */
		@Override
		public List<DeathMessage> getOperators() {
			return PlayerMessages.getInstance().getMessages(PlayerMessageType.DEATH);
		}
	}
}
