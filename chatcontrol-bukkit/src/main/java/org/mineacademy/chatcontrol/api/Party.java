package org.mineacademy.chatcontrol.api;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.listener.ThirdPartiesListener;
import org.mineacademy.fo.CommonCore;
import org.mineacademy.fo.ValidCore;
import org.mineacademy.fo.model.HookManager;

import lombok.Getter;

/**
 * Represents a party for a channel.
 */
public abstract class Party {

	/**
	 * Registration by name
	 */
	private static final Map<String, Party> byName = new HashMap<>();

	/**
	 * This is a channel shown only to players in the same Faction as the sender.
	 */
	public static final Party FACTION = new Party("factions-faction") {
		@Override
		public boolean isInParty(final Player receiver, final Player sender) {
			return HookManager.getOnlineFactionPlayers(sender).contains(receiver);
		};
	};

	public static final Party FACTION_ALLY = new Party("factions-ally") {
		@Override
		public boolean isInParty(Player receiver, Player sender) {
			return HookManager.getOnlineFactionPlayers(sender).contains(receiver) || HookManager.getAlliedFactionPlayers(sender).contains(receiver);
		}
	};

	/**
	 * Chat for PlotSquared - only shown to players inside the plot.
	 */
	public static final Party PLOT = new Party("plotsquared-plot") {
		@Override
		public boolean isInParty(final Player receiver, final Player sender) {
			return HookManager.getPlotPlayers(sender).contains(receiver);
		}
	};

	/**
	 * This is a channel shown only to players in the same Town as the sender.
	 */
	public static final Party TOWN = new Party("towny-town") {
		@Override
		public boolean isInParty(final Player receiver, final Player sender) {
			return HookManager.getTownResidentsOnline(sender).contains(receiver);
		}
	};

	/**
	 * This is a channel shown only to players in the same Nation as the sender.
	 */
	public static final Party NATION = new Party("towny-nation") {
		@Override
		public boolean isInParty(final Player receiver, final Player sender) {
			return HookManager.getNationPlayersOnline(sender).contains(receiver);
		}
	};

	/**
	 * This is a channel shown only to same ally players as the sender.
	 */
	public static final Party ALLY = new Party("towny-ally") {
		@Override
		public boolean isInParty(final Player receiver, final Player sender) {
			return HookManager.getAllyPlayersOnline(sender).contains(receiver);
		}
	};

	/**
	 * Chat for mcMMO - only shown to players within the same party.
	 */
	public static final Party MCMMO = new Party("mcmmo-party") {
		@Override
		public boolean isInParty(final Player receiver, final Player sender) {
			return ThirdPartiesListener.getMcMMOPartyRecipients(sender).contains(receiver);
		}
	};

	/**
	 * Only chat with other players in the same land
	 */
	public static final Party LAND = new Party("lands-land") {
		@Override
		public boolean isInParty(final Player receiver, final Player sender) {
			return HookManager.getLandPlayers(sender).contains(receiver);
		}
	};

	/**
	 * Only chat with players belonging to the same island as you
	 * and having the given (or higher rank, from top to bottom)
	 */
	public static final Party ISLAND_VISITOR = new Party("bentobox-island-visitor") {
		@Override
		public boolean isInParty(final Player receiver, final Player sender) {
			final Set<UUID> visitors = HookManager.getBentoBoxVisitors(sender);

			return visitors.contains(sender.getUniqueId()) && visitors.contains(receiver.getUniqueId());

		}
	};

	public static final Party ISLAND_COOP = new Party("bentobox-island-coop") {
		@Override
		public boolean isInParty(final Player receiver, final Player sender) {
			final Set<UUID> coops = HookManager.getBentoBoxCoops(sender);

			return coops.contains(sender.getUniqueId()) && coops.contains(receiver.getUniqueId());
		}
	};

	public static final Party ISLAND_TRUSTED = new Party("bentobox-island-trusted") {
		@Override
		public boolean isInParty(final Player receiver, final Player sender) {
			final Set<UUID> trustees = HookManager.getBentoBoxTrustees(sender);

			return trustees.contains(sender.getUniqueId()) && trustees.contains(receiver.getUniqueId());
		}
	};

	public static final Party ISLAND_MEMBER = new Party("bentobox-island-member") {
		@Override
		public boolean isInParty(final Player receiver, final Player sender) {
			final Set<UUID> members = HookManager.getBentoBoxMembers(sender);

			return members.contains(sender.getUniqueId()) && members.contains(receiver.getUniqueId());
		}
	};

	public static final Party ISLAND_SUBOWNER = new Party("bentobox-island-subowner") {
		@Override
		public boolean isInParty(final Player receiver, final Player sender) {
			final Set<UUID> subowners = HookManager.getBentoBoxSubOwners(sender);

			return subowners.contains(sender.getUniqueId()) && subowners.contains(receiver.getUniqueId());
		}
	};

	public static final Party ISLAND_OWNER = new Party("bentobox-island-owner") {
		@Override
		public boolean isInParty(final Player receiver, final Player sender) {
			final Set<UUID> owners = HookManager.getBentoBoxOwners(sender);

			return owners.contains(sender.getUniqueId()) && owners.contains(receiver.getUniqueId());
		}
	};

	public static final Party ISLAND_MOD = new Party("bentobox-island-mod") {
		@Override
		public boolean isInParty(final Player receiver, final Player sender) {
			final Set<UUID> mods = HookManager.getBentoBoxMods(sender);

			return mods.contains(sender.getUniqueId()) && mods.contains(receiver.getUniqueId());
		}
	};

	public static final Party ISLAND_ADMIN = new Party("bentobox-island-admin") {
		@Override
		public boolean isInParty(final Player receiver, final Player sender) {
			final Set<UUID> admins = HookManager.getBentoBoxAdmins(sender);

			return admins.contains(sender.getUniqueId()) && admins.contains(receiver.getUniqueId());
		}
	};

	/**
	 * The unique party name
	 */
	@Getter
	private final String name;

	private Party(final String name) {
		this.name = name;

		byName.put(name, this);
	}

	/**
	 * Return true if the receiver is in the sender's party.
	 *
	 * @param receiver
	 * @param sender
	 * @return
	 */
	public abstract boolean isInParty(Player receiver, Player sender);

	/**
	 * Get the unique party name
	 */
	@Override
	public final String toString() {
		return this.name;
	}

	/**
	 * Register a new party into our API.
	 *
	 * @param name
	 * @param isInParty return true or false depending if the first argument (the receiver) is in the second argument's (the sender's) party
	 */
	public static void register(final String name, final BiFunction<Player, Player, Boolean> isInParty) {
		ValidCore.checkBoolean(!byName.containsKey(name), "Party named " + name + " already exists!");

		byName.put(name, new Party(name) {

			@Override
			public boolean isInParty(final Player receiver, final Player sender) {
				return isInParty.apply(receiver, sender);
			}
		});
	}

	/**
	 * Finds the party from the given name, throwing exception if failed.
	 *
	 * @param name
	 * @return
	 */
	public static Party fromKey(final String name) {
		for (final Map.Entry<String, Party> entry : byName.entrySet())
			if (entry.getKey().equalsIgnoreCase(name))
				return entry.getValue();

		throw new IllegalArgumentException("No such channel party: " + name + ". Available: " + CommonCore.join(byName.keySet()));
	}

	/**
	 *
	 * @return
	 */
	public static Set<String> getPartiesNames() {
		return Collections.unmodifiableSet(byName.keySet());
	}

	/**
	 *
	 * @return
	 */
	public static Collection<Party> getParties() {
		return Collections.unmodifiableCollection(byName.values());
	}
}