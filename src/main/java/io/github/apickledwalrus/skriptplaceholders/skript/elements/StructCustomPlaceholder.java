package io.github.apickledwalrus.skriptplaceholders.skript.elements;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.Skript;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.skript.lang.util.SimpleEvent;
import ch.njol.skript.registrations.EventValues;
import io.github.apickledwalrus.skriptplaceholders.SkriptPlaceholders;
import io.github.apickledwalrus.skriptplaceholders.placeholder.PlaceholderEvaluator;
import io.github.apickledwalrus.skriptplaceholders.placeholder.PlaceholderRegistry;
import io.github.apickledwalrus.skriptplaceholders.skript.PlaceholderEvent;
import io.github.apickledwalrus.skriptplaceholders.placeholder.PlaceholderPlugin;
import io.github.apickledwalrus.skriptplaceholders.util.FoliaScheduler;
import io.github.apickledwalrus.skriptplaceholders.skript.RelationalPlaceholderEvent;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.lang.entry.EntryContainer;
import org.skriptlang.skript.lang.script.Script;
import org.skriptlang.skript.lang.structure.Structure;

@Name("Custom Placeholder")
@Description({
	"A structure for creating custom placeholders.",
	"The code will be executed every time the placeholder plugin requests a value for the placeholder."
})
@Examples({
	"placeholderapi placeholder with the prefix \"skriptplaceholders\":",
		"\tif the identifier is \"author\": # Placeholder is \"%skriptplaceholders_author%\"",
			"\t\tset the result to \"APickledWalrus\"",
	"placeholderapi relational placeholder with the prefix \"skriptplaceholders\":",
		"\tif the identifier is \"longer_name\": # Placeholder is \"%rel_skriptplaceholders_longer_name%\"",
			"\t\tif the length of the name of the first player > the length of the name of the second player:",
				"\t\t\tset the result to the name of the first player",
			"\t\telse:",
				"\t\t\tset the result to the name of the second player",
	"mvdw placeholder named \"skriptplaceholders_author\":",
		"\t# Placeholder is \"{skriptplaceholders_author}\"",
		"\tset the result to \"APickledWalrus\""
})
@Since("1.0.0, 1.3.0 (MVdWPlaceholderAPI support), 1.7.0 (relational placeholder support)")
public class StructCustomPlaceholder extends Structure implements PlaceholderEvaluator {

	static {
		Skript.registerStructure(StructCustomPlaceholder.class,
				"(placeholder[ ]api|papi) [:relational] placeholder (with|for) [the] prefix %*string%",
				"(mvdw[ ]placeholder[ ]api|mvdw) placeholder [with [the] name|named] %*string%"
		);
		EventValues.registerEventValue(PlaceholderEvent.class, Player.class, event -> {
			OfflinePlayer player = event.getPlayer();
			return player != null ? player.getPlayer() : null;
		});
		EventValues.registerEventValue(PlaceholderEvent.class, OfflinePlayer.class, PlaceholderEvent::getPlayer);
	}

	private SectionNode source;
	private PlaceholderRegistry registry;
	private PlaceholderPlugin plugin;
	private String placeholder;

	private boolean isRelational;
	private Trigger trigger;

	/*
	 * PlaceholderAPI exposes a synchronous return type. On Folia we cannot block one region
	 * while another region evaluates a Skript trigger, so cross-region requests use the most
	 * recently computed value and schedule a refresh for the next safe entity/global context.
	 */
	private final ConcurrentMap<CacheKey, String> cachedResults = new ConcurrentHashMap<>();
	private final ConcurrentMap<CacheKey, Boolean> pendingEvaluations = new ConcurrentHashMap<>();

	@Override
	public boolean init(Literal<?> @NotNull [] args, int matchedPattern, @NotNull ParseResult parseResult, @Nullable EntryContainer entryContainer) {
		plugin = PlaceholderPlugin.values()[matchedPattern <= 1 ? matchedPattern : matchedPattern - 2];
		if (!plugin.isInstalled()) {
			Skript.error(plugin.getDisplayName() + " placeholders can not be created because the plugin is not installed.");
			return false;
		}

		//noinspection unchecked - Skript guarantees this will be a Literal<String>
		String placeholder = ((Literal<String>) args[0]).getSingle();
		String error = plugin.validatePrefix(placeholder);
		if (error != null) {
			Skript.error(error);
			return false;
		}
		assert entryContainer != null; // only null for simple structures
		this.source = entryContainer.getSource();
		this.placeholder = placeholder;

		this.registry = SkriptPlaceholders.getInstance().getRegistry();
		this.isRelational = parseResult.hasTag("relational");

		return true;
	}

	@Override
	public boolean load() {
		ParserInstance parser = getParser();
		Script script = parser.getCurrentScript();

		parser.setCurrentEvent("custom placeholder", isRelational ? RelationalPlaceholderEvent.class : PlaceholderEvent.class);

		// TODO better SkriptEvent?
		//noinspection ConstantConditions - getCurrentEventName will not be null as we set it right before
		trigger = new Trigger(script, parser.getCurrentEventName(), new SimpleEvent(), ScriptLoader.loadItems(source));
		int lineNumber = source.getLine();
		trigger.setLineNumber(lineNumber);
		trigger.setDebugLabel(script + ": line " + lineNumber);

		// Placeholder providers mutate global registration state. On Folia this must happen on
		// the global region; on Paper the helper executes immediately on the main thread.
		FoliaScheduler.runGlobal(SkriptPlaceholders.getInstance(),
				() -> registry.registerPlaceholder(plugin, placeholder, this));

		return true;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		switch (plugin) {
			case PLACEHOLDER_API:
				return "placeholderapi " + (isRelational ? "relational " : "") + "placeholder with the prefix " + placeholder;
			case MVDW_PLACEHOLDER_API:
				return "mvdwplaceholderapi placeholder named " + placeholder;
			default:
				throw new IllegalArgumentException("Unable to handle PlaceholderPlugin: " + plugin);
		}
	}

	@Override
	public @Nullable String evaluate(String placeholder, @Nullable OfflinePlayer player) {
		if (isRelational) { // a relational placeholder structure cannot evaluate non-relational placeholders
			return null;
		}

		CacheKey key = new CacheKey(this, placeholder, player == null ? null : player.getUniqueId(), null);
		String cached = cachedResults.get(key);
		if (pendingEvaluations.putIfAbsent(key, Boolean.TRUE) == null) {
			boolean ranNow = FoliaScheduler.runForPlayer(SkriptPlaceholders.getInstance(), player, () -> {
				try {
					PlaceholderEvent event = new PlaceholderEvent(placeholder, player);
					trigger.execute(event);
					if (event.getResult() != null) {
						cachedResults.put(key, event.getResult());
					}
				} finally {
					pendingEvaluations.remove(key);
				}
			});
			if (ranNow) {
				cached = cachedResults.get(key);
			}
		}
		return cached;
	}

	@Override
	public @Nullable String evaluateRelational(String placeholder, Player one, Player two) {
		if (!isRelational) { // a non-relational placeholder structure cannot evaluate relational placeholders
			return null;
		}

		CacheKey key = new CacheKey(this, placeholder, one.getUniqueId(), two.getUniqueId());
		String cached = cachedResults.get(key);
		if (pendingEvaluations.putIfAbsent(key, Boolean.TRUE) == null) {
			boolean ranNow = FoliaScheduler.runRelational(SkriptPlaceholders.getInstance(), one, two, () -> {
				try {
					RelationalPlaceholderEvent event = new RelationalPlaceholderEvent(placeholder, one, two);
					trigger.execute(event);
					if (event.getResult() != null) {
						cachedResults.put(key, event.getResult());
					}
				} finally {
					pendingEvaluations.remove(key);
				}
			});
			if (ranNow) {
				cached = cachedResults.get(key);
			}
			// When the two players are owned by different regions, the current placeholder API
			// request cannot be made safe by waiting. Keep the previous value instead.
			if (!ranNow) {
				pendingEvaluations.remove(key);
			}
		}
		return cached;
	}

	@Override
	public void unload() {
		cachedResults.keySet().removeIf(key -> key.owner == this);
		pendingEvaluations.keySet().removeIf(key -> key.owner == this);
		FoliaScheduler.runGlobal(SkriptPlaceholders.getInstance(),
				() -> registry.unregisterPlaceholder(plugin, placeholder, this));
	}

	private static final class CacheKey {
		private final StructCustomPlaceholder owner;
		private final String placeholder;
		private final UUID first;
		private final UUID second;

		private CacheKey(StructCustomPlaceholder owner, String placeholder, UUID first, UUID second) {
			this.owner = owner;
			this.placeholder = placeholder;
			this.first = first;
			this.second = second;
		}


		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof CacheKey other)) return false;
			return owner == other.owner && java.util.Objects.equals(placeholder, other.placeholder)
					&& java.util.Objects.equals(first, other.first) && java.util.Objects.equals(second, other.second);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(System.identityHashCode(owner), placeholder, first, second);
		}
	}


}
