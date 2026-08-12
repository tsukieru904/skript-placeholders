package io.github.apickledwalrus.skriptplaceholders.skript.elements;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import io.github.apickledwalrus.skriptplaceholders.placeholder.PlaceholderPlugin;
import io.github.apickledwalrus.skriptplaceholders.SkriptPlaceholders;
import io.github.apickledwalrus.skriptplaceholders.util.FoliaScheduler;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Name("Placeholder Value")
@Description("An expression to obtain the value of a placeholder from a supported plugin.")
@Examples({
	"command /ping <player>:",
		"\ttrigger:",
			"\t\tset {_ping} to the placeholder \"player_ping\" from arg-1 # PlaceholderAPI",
			"\t\tset {_ping} to the placeholder \"{ping}\" from arg-1 # MVdWPlaceholderAPI",
			"\t\tsend \"Ping of %arg-1%: %{_ping}%\" to the player",
	"command /friend status <player> <player>:",
		"\ttrigger:",
			"\tset {_status} to the relational placeholder \"rel_friendship_status\" from arg-1 and arg-2",
			"\tsend \"Status: %{_status}%\" to the player"
})
@Since("1.0.0, 1.2.0 (MVdWPlaceholderAPI support), 1.7.0 (relational placeholder support)")
public class ExprPlaceholderValue extends SimpleExpression<String> {

	private static final ConcurrentMap<CacheKey, String> CACHE = new ConcurrentHashMap<>();
	private static final ConcurrentMap<CacheKey, Boolean> PENDING = new ConcurrentHashMap<>();

	static {
		Skript.registerExpression(ExprPlaceholderValue.class, String.class, ExpressionType.COMBINED,
				"[the] [value[s] of [the]] [:relational] placeholder[s] [in] %strings% [(for|from|of) %-players/offlineplayers%]",
				"[:relational] placeholder[s] %strings%'[s] value[s] [(for|from|of) %-players/offlineplayers%]"
		);
	}

	private boolean isRelational;
	private Expression<String> placeholders;
	@Nullable
	private Expression<OfflinePlayer> players;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		isRelational = parseResult.hasTag("relational");
		placeholders = (Expression<String>) exprs[0];
		players = (Expression<OfflinePlayer>) exprs[1];

		if (isRelational && (players == null || players.isSingle())) {
			Skript.error("There must be two players provided to use relational placeholders.");
			return false;
		}

		return true;
	}

	@Override
	protected String @NotNull [] get(@NotNull Event event) {
		String[] placeholders = this.placeholders.getArray(event);
		OfflinePlayer[] players = this.players != null ? this.players.getArray(event) : new OfflinePlayer[]{null};
		if (isRelational) {
			if (players.length == 2) {
				Player one = players[0].getPlayer();
				Player two = players[1].getPlayer();
				if (one != null && two != null) { // both are online
					return parseRelationalPlaceholders(placeholders, one, two);
				}
			}
			return new String[0];
		}
		return parsePlaceholders(placeholders, players);
	}

	private static String[] parsePlaceholders(String[] placeholders, @Nullable OfflinePlayer[] players) {
		List<String> values = new ArrayList<>();
		for (OfflinePlayer player : players) {
			for (String placeholder : placeholders) {
				String value = null;
				for (PlaceholderPlugin plugin : PlaceholderPlugin.getInstalledPlugins()) {
					CacheKey key = new CacheKey(plugin, placeholder, player == null ? null : player.getUniqueId(), null);
					String cached = CACHE.get(key);
					if (PENDING.putIfAbsent(key, Boolean.TRUE) == null) {
						final String[] result = new String[1];
						boolean ranNow = FoliaScheduler.runForPlayer(SkriptPlaceholders.getInstance(), player, () -> {
							try {
								result[0] = plugin.parsePlaceholder(placeholder, player);
								if (result[0] != null) CACHE.put(key, result[0]);
							} finally {
								PENDING.remove(key);
							}
						});
						if (ranNow) cached = result[0];
					}
					if (cached != null) {
						value = cached;
						break;
					}
				}
				if (value != null) values.add(value);
			}
		}
		return values.toArray(new String[0]);
	}

	private static String[] parseRelationalPlaceholders(String[] placeholders, Player one, Player two) {
		List<String> values = new ArrayList<>();
		for (String placeholder : placeholders) {
			for (PlaceholderPlugin plugin : PlaceholderPlugin.getInstalledPlugins()) {
				if (!plugin.supportsRelationalPlaceholders()) continue;
				CacheKey key = new CacheKey(plugin, placeholder, one.getUniqueId(), two.getUniqueId());
				String value = CACHE.get(key);
				if (PENDING.putIfAbsent(key, Boolean.TRUE) == null) {
					final String[] result = new String[1];
					boolean ranNow = FoliaScheduler.runRelational(SkriptPlaceholders.getInstance(), one, two, () -> {
						try {
							result[0] = plugin.parseRelationalPlaceholder(placeholder, one, two);
							if (result[0] != null) CACHE.put(key, result[0]);
						} finally {
							PENDING.remove(key);
						}
					});
					if (ranNow) value = result[0];
					if (!ranNow) PENDING.remove(key);
				}
				if (value != null) {
					values.add(value);
					break;
				}
			}
		}
		return values.toArray(new String[0]);
	}

	private record CacheKey(PlaceholderPlugin plugin, String placeholder, UUID first, UUID second) {}

	@Override
	public boolean isSingle() {
		// single if there is one placeholder, and it is relational OR there is only one player
		return placeholders.isSingle() && (isRelational || players == null || players.isSingle());
	}

	@Override
	public @NotNull Class<? extends String> getReturnType() {
		return String.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		StringBuilder string = new StringBuilder("the value of ");
		if (isRelational) {
			string.append("relational ");
		}
		string.append(placeholders.isSingle() ? "placeholder " : "placeholders ");
		string.append(placeholders.toString(event, debug));
		if (players != null) {
			string.append(" for ").append(players.toString(event, debug));
		}
		return string.toString();
	}

}
