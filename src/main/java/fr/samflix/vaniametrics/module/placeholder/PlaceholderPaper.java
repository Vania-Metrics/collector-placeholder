package fr.samflix.vaniametrics.module.placeholder;

import org.bukkit.plugin.java.JavaPlugin;

import fr.samflix.vaniametrics.api.VaniaMetrics;
import fr.samflix.vaniametrics.api.VaniaMetricsProvider;

/**
 * The last-resort connector: any placeholder as a metric.
 *
 * <p>Empty whitelist by default, numeric values only, resolved with no player. Three guards,
 * none optional.
 */
public final class PlaceholderPaper extends JavaPlugin {

	private PlaceholderCollector collector;

	@Override
	public void onEnable() {
		VaniaMetrics metrics = VaniaMetricsProvider.get();
		collector = new PlaceholderCollector(metrics.platform(), metrics.config());
		if (!collector.hasSomethingToDo()) {
			// Nothing requested: don't even register the collector. An instrument declared
			// and never fed would read like a zero.
			getLogger().info("no placeholder requested — module inactive.");
			return;
		}
		metrics.register(collector);
	}

	@Override
	public void onDisable() {
		if (collector != null) {
			VaniaMetricsProvider.find().ifPresent(m -> m.unregister(collector));
		}
	}
}
