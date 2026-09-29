package ma.rdvsante.rendezvous.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ma.rdvsante.rendezvous.service.ServiceCatalogue;

/** Ce que le patient consulte avant de réserver. */
@RestController
@RequestMapping("/api")
class ControleurCatalogue {

    private final ServiceCatalogue catalogue;

    ControleurCatalogue(ServiceCatalogue catalogue) {
        this.catalogue = catalogue;
    }

    @GetMapping("/cliniques")
    List<Vues.CliniqueVue> cliniques() {
        return catalogue.cliniques().stream().map(Vues.CliniqueVue::de).toList();
    }

    @GetMapping("/cliniques/{id}")
    Vues.CliniqueVue clinique(@PathVariable UUID id) {
        return Vues.CliniqueVue.de(catalogue.clinique(id));
    }

    @GetMapping("/cliniques/{id}/praticiens")
    List<Vues.PraticienVue> praticiens(@PathVariable UUID id) {
        return catalogue.praticiens(id).stream().map(Vues.PraticienVue::de).toList();
    }

    /** L'agenda du jour d'une clinique : ce que le secrétariat pointe. */
    @GetMapping("/cliniques/{id}/rendez-vous")
    List<Vues.RendezVousVue> agenda(
            @PathVariable UUID id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour) {

        LocalDate cible = jour != null ? jour : LocalDate.now();
        return catalogue.agenda(id, cible).stream().map(Vues.RendezVousVue::de).toList();
    }

    /**
     * Les créneaux encore libres d'un praticien, pour un jour donné.
     *
     * @param jour au format {@code 2026-10-03}. Sans valeur, c'est aujourd'hui.
     */
    @GetMapping("/praticiens/{id}/creneaux")
    List<Vues.CreneauVue> creneaux(
            @PathVariable UUID id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour) {

        LocalDate cible = jour != null ? jour : LocalDate.now();
        return catalogue.creneauxLibres(id, cible).stream().map(Vues.CreneauVue::de).toList();
    }
}
