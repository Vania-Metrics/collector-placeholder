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
 * PlaceholderAPI — LE CONNECTEUR DE DERNIER RECOURS.
 *
 * <p>Neuf plugins s'y branchent sur ce serveur. Celui-ci expose n'importe lequel de leurs
 * placeholders comme métrique, ce qui couvre d'un coup tous les plugins qui n'auront jamais de
 * module à eux. C'est aussi le seul connecteur qui ne mesure rien par lui-même : il relaie.
 *
 * <p>D'OÙ TROIS GARDES, ET AUCUN N'EST FACULTATIF.
 *
 * <ol>
 *   <li><b>Une liste blanche, et vide par défaut.</b> Ce module ne fait rien tant que personne
 *       n'a écrit ce qu'il veut. Exposer « tous les placeholders » n'a pas de sens : il y en a des
 *       centaines, la plupart rendent du texte.
 *   <li><b>Seul ce qui est NUMÉRIQUE est publié</b>, et ce qui ne l'est pas est signalé UNE FOIS
 *       puis ignoré. Un placeholder rend une chaîne ; parier qu'elle se convertit, c'est publier
 *       {@code NaN} en silence pour un nom mal orthographié.
 *   <li><b>Résolu SANS joueur.</b> {@code setPlaceholders(null, …)} rend la valeur globale. Les
 *       placeholders par joueur rouvriraient la porte à la cardinalité, et pour ceux-là il existe
 *       un module dédié — c'est tout l'objet de cette architecture.
 * </ol>
 *
 * <p>Le nom de la métrique est déduit du placeholder : {@code %plan_players_online_total%} devient
 * {@code mc_placeholder_plan_players_online_total}. Le domaine {@code placeholder} dit d'où vient
 * la valeur, ce qui est exactement ce qu'on veut savoir d'un chiffre relayé.
 */
public final class PlaceholderCollector implements Collector {

	private final Platform plateforme;
	private final List<String> demandes;
	private final List<String> refuses = new ArrayList<>();

	private Gauge valeurs;

	public PlaceholderCollector(Platform plateforme, Config config) {
		this.plateforme = plateforme;
		String liste = config.texte("collector.placeholder.list", "");
		this.demandes = liste.isBlank()
				? List.of()
				: List.of(liste.split("\\s*,\\s*"));
	}

	@Override
	public String nom() {
		return "placeholder";
	}

	@Override
	public String origine() {
		return "PlaceholderAPI";
	}

	@Override
	public boolean enFond() {
		return true;
	}

	@Override
	public long intervalleSecondes() {
		return 30;
	}

	@Override
	public boolean filPrincipal() {
		// Un placeholder peut interroger n'importe quoi — un monde, un inventaire, une carte du
		// serveur. On le résout donc là où ces choses se lisent.
		return true;
	}

	@Override
	public void declarer(MetricRegistry r) {
		valeurs = r.gauge("placeholder_value",
				"Valeur d'un placeholder, relayée telle quelle. Seul ce qui se convertit en "
						+ "nombre est publié — le reste est signalé une fois puis ignoré.",
				"placeholder");
		if (demandes.isEmpty()) {
			plateforme.info("collecteur placeholder — aucune demande dans "
					+ "collector.placeholder.list, rien ne sera publié");
		} else {
			plateforme.info("collecteur placeholder — " + demandes.size() + " demandé(s)");
		}
	}

	@Override
	public void relever(MetricRegistry r) {
		for (String brut : demandes) {
			String motif = brut.startsWith("%") ? brut : "%" + brut + "%";
			if (refuses.contains(motif)) {
				continue;
			}
			// null et pas un joueur : on veut la valeur GLOBALE. Un placeholder par joueur
			// ferait une série par joueur, ce que cette architecture évite exprès.
			String rendu = PlaceholderAPI.setPlaceholders(null, motif);
			if (rendu == null || rendu.equals(motif)) {
				// Inchangé = aucune extension ne l'a reconnu. Se taire ici laisserait croire à
				// une valeur nulle alors que le nom est faux.
				refuser(motif, "aucune extension ne le reconnaît");
				continue;
			}
			try {
				valeurs.set(Double.parseDouble(rendu.trim().replace(',', '.')), nomCourt(motif));
			} catch (NumberFormatException e) {
				refuser(motif, "rend « " + rendu + " », qui n'est pas un nombre");
			}
		}
	}

	/** Signalé UNE fois, puis oublié : un avertissement toutes les trente secondes est du bruit. */
	private void refuser(String motif, String raison) {
		refuses.add(motif);
		plateforme.avertir("placeholder " + motif + " — " + raison + ", ignoré désormais");
	}

	/** {@code %plan_players_online%} devient {@code plan_players_online}. */
	private static String nomCourt(String motif) {
		return motif.replace("%", "").toLowerCase(Locale.ROOT);
	}

	/** Pour que le module ne s'enregistre pas s'il n'a rien à faire. */
	public boolean aQuelqueChoseAFaire() {
		return !demandes.isEmpty() && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
	}
}
