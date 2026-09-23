package fr.samflix.vaniametrics.module.placeholder;

import org.bukkit.plugin.java.JavaPlugin;

import fr.samflix.vaniametrics.api.VaniaMetrics;
import fr.samflix.vaniametrics.api.VaniaMetricsProvider;

/**
 * Le connecteur de dernier recours : n'importe quel placeholder en métrique.
 *
 * <p>Liste blanche vide par défaut, valeurs numériques seulement, résolution sans joueur. Trois gardes, aucun facultatif.
 */
public final class PlaceholderPaper extends JavaPlugin {

	private PlaceholderCollector collecteur;

	@Override
	public void onEnable() {
		VaniaMetrics metriques = VaniaMetricsProvider.get();
		collecteur = new PlaceholderCollector(metriques.plateforme(), metriques.config());
		if (!collecteur.aQuelqueChoseAFaire()) {
			// Rien de demandé : on n'enregistre même pas le collecteur. Un
			// instrument déclaré et jamais alimenté se lirait comme un zéro.
			getLogger().info("aucun placeholder demandé — module inactif.");
			return;
		}
		metriques.enregistrer(collecteur);
	}

	@Override
	public void onDisable() {
		if (collecteur != null) {
			VaniaMetricsProvider.chercher().ifPresent(m -> m.retirer(collecteur));
		}
	}
}
