package fr.samflix.vaniametrics.module.placeholder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;

import me.clip.placeholderapi.PlaceholderAPI;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Config;
import fr.samflix.vaniametrics.api.Gauge;
import fr.samflix.vaniametrics.api.MetricRegistry;
import fr.samflix.vaniametrics.api.Platform;

/**
 * PlaceholderAPI — the last-resort connector.
 *
 * <p>Nine plugins hook into it on this server. This one exposes any of their placeholders as a
 * metric, which covers in one go every plugin that will never have a module of its own. It's
 * also the only connector that doesn't measure anything itself: it relays.
 *
 * <p>Hence three guards, none of them optional.
 *
 * <ol>
 *   <li><b>A whitelist, empty by default.</b> This module does nothing until someone has written
 *       what they want. Exposing "every placeholder" makes no sense: there are hundreds, most of
 *       which render text.
 *   <li><b>Only what is numeric gets published</b>, and what isn't is reported once then
 *       ignored. A placeholder renders a string; betting that it converts means silently
 *       publishing {@code NaN} for a misspelled name.
 *   <li><b>Resolved with no player.</b> {@code setPlaceholders(null, …)} yields the global value.
 *       Per-player placeholders would reopen the door to cardinality, and for those a dedicated
 *       module exists — that's the whole point of this architecture.
 * </ol>
 *
 * <p>On Folia the collection runs on the global region: placeholders that read global state work,
 * those that read a world, a chunk or an entity are refused by Folia and rejected once, like a
 * misspelt name.
 *
 * <p>Every placeholder is one series of a single metric, labelled by its name:
 * {@code %plan_players_online_total%} becomes
 * {@code mc_placeholder_value{placeholder="plan_players_online_total"}}. The {@code placeholder}
 * domain says where the value comes from, which is exactly what you want to know about a relayed
 * number.
 */
public final class PlaceholderCollector implements Collector {

	private final Platform platform;
	private final List<String> requested;
	private final List<String> rejected = new ArrayList<>();

	private Gauge values;

	public PlaceholderCollector(Platform platform, Config config) {
		this.platform = platform;
		String list = config.getString("collector.placeholder.list", "");
		this.requested = list.isBlank()
				? List.of()
				: List.of(list.split("\\s*,\\s*"));
	}

	@Override
	public String name() {
		return "placeholder";
	}

	@Override
	public String source() {
		return "PlaceholderAPI";
	}

	@Override
	public boolean isBackground() {
		return true;
	}

	@Override
	public long intervalSeconds() {
		return 30;
	}

	@Override
	public boolean needsMainThread() {
		// A placeholder can query anything — a world, an inventory, the server map. So it's
		// resolved wherever those things are read.
		return true;
	}

	@Override
	public void declare(MetricRegistry r) {
		values = r.gauge("placeholder_value",
				"Value of a placeholder, relayed as-is. Only what converts to a number is "
						+ "published — the rest is reported once then ignored.",
				"placeholder");
		if (requested.isEmpty()) {
			platform.info("placeholder collector — nothing in "
					+ "collector.placeholder.list, nothing will be published");
		} else {
			platform.info("placeholder collector — " + requested.size() + " requested");
		}
	}

	@Override
	public void collect(MetricRegistry r) {
		for (String raw : requested) {
			String pattern = raw.startsWith("%") ? raw : "%" + raw + "%";
			if (rejected.contains(pattern)) {
				continue;
			}
			// null and no player: we want the GLOBAL value. A per-player placeholder would
			// create a per-player series, which this architecture is built to avoid.
			String rendered;
			try {
				rendered = PlaceholderAPI.setPlaceholders(null, pattern);
			} catch (RuntimeException e) {
				// On Folia this runs on the global region, where an expansion that reads a world
				// or an entity is refused. One such placeholder must not take the others down.
				reject(pattern, "threw " + e);
				continue;
			}
			if (rendered == null || rendered.equals(pattern)) {
				// Unchanged means no extension recognized it. Staying silent here would suggest
				// a null value when the name is actually wrong.
				reject(pattern, "no extension recognizes it");
				continue;
			}
			try {
				values.set(Double.parseDouble(rendered.trim().replace(',', '.')), shortName(pattern));
			} catch (NumberFormatException e) {
				reject(pattern, "renders \"" + rendered + "\", which is not a number");
			}
		}
	}

	/** Reported ONCE, then forgotten: a warning every thirty seconds is noise. */
	private void reject(String pattern, String reason) {
		rejected.add(pattern);
		platform.warn("placeholder " + pattern + " — " + reason + ", ignored from now on");
	}

	/** {@code %plan_players_online%} becomes {@code plan_players_online}. */
	private static String shortName(String pattern) {
		return pattern.replace("%", "").toLowerCase(Locale.ROOT);
	}

	/** So the module doesn't register itself if it has nothing to do. */
	public boolean hasSomethingToDo() {
		return !requested.isEmpty() && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
	}
}
