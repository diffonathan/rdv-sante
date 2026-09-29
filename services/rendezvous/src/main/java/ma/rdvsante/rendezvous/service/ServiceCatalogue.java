package ma.rdvsante.rendezvous.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ma.rdvsante.rendezvous.depot.DepotClinique;
import ma.rdvsante.rendezvous.depot.DepotCreneau;
import ma.rdvsante.rendezvous.depot.DepotPraticien;
import ma.rdvsante.rendezvous.depot.DepotRendezVous;
import ma.rdvsante.rendezvous.domaine.Clinique;
import ma.rdvsante.rendezvous.domaine.Creneau;
import ma.rdvsante.rendezvous.domaine.Praticien;
import ma.rdvsante.rendezvous.domaine.RendezVous;
import ma.rdvsante.rendezvous.domaine.StatutRendezVous;

/** Lecture seule : ce qu'on propose au patient avant qu'il réserve. */
@Service
public class ServiceCatalogue {

    /** Toutes les cliniques sont au Maroc ; l'agenda se lit en heure locale. */
    private static final ZoneId FUSEAU = ZoneId.of("Africa/Casablanca");

    private final DepotClinique depotClinique;
    private final DepotPraticien depotPraticien;
    private final DepotCreneau depotCreneau;
    private final DepotRendezVous depotRendezVous;
    private final Clock horloge;

    public ServiceCatalogue(DepotClinique depotClinique, DepotPraticien depotPraticien,
                            DepotCreneau depotCreneau, DepotRendezVous depotRendezVous,
                            Clock horloge) {
        this.depotClinique = depotClinique;
        this.depotPraticien = depotPraticien;
        this.depotCreneau = depotCreneau;
        this.depotRendezVous = depotRendezVous;
        this.horloge = horloge;
    }

    @Transactional(readOnly = true)
    public List<Clinique> cliniques() {
        return depotClinique.findAll();
    }

    @Transactional(readOnly = true)
    public Clinique clinique(UUID id) {
        return depotClinique.findById(id)
                .orElseThrow(() -> new ErreursMetier.Introuvable("Clinique", id));
    }

    @Transactional(readOnly = true)
    public List<Praticien> praticiens(UUID cliniqueId) {
        return depotPraticien.parClinique(cliniqueId);
    }

    /**
     * Les créneaux libres d'un praticien pour un jour donné.
     *
     * <p>La journée est délimitée en heure du Maroc, pas en UTC : demander
     * « les créneaux du 3 octobre » doit rendre ceux du 3 octobre tel que le
     * patient le vit, sans décalage d'une heure en été.
     *
     * <p>Les créneaux déjà commencés sont écartés ici plutôt qu'à la
     * réservation : proposer un créneau pour le refuser ensuite est une façon
     * sûre de perdre le patient.
     */
    @Transactional(readOnly = true)
    public List<Creneau> creneauxLibres(UUID praticienId, LocalDate jour) {
        Instant debutJour = jour.atStartOfDay(FUSEAU).toInstant();
        Instant finJour = jour.plusDays(1).atStartOfDay(FUSEAU).toInstant();
        Instant maintenant = horloge.instant();

        Instant borneBasse = debutJour.isBefore(maintenant) ? maintenant : debutJour;
        if (!borneBasse.isBefore(finJour)) {
            return List.of();
        }

        return depotCreneau.libres(praticienId, borneBasse, finJour, StatutRendezVous.ANNULE);
    }

    /** L'agenda d'une clinique pour un jour, en heure du Maroc. */
    @Transactional(readOnly = true)
    public List<RendezVous> agenda(UUID cliniqueId, LocalDate jour) {
        return depotRendezVous.agendaDuJour(
                cliniqueId,
                jour.atStartOfDay(FUSEAU).toInstant(),
                jour.plusDays(1).atStartOfDay(FUSEAU).toInstant(),
                StatutRendezVous.ANNULE);
    }

    /** Fenêtre par défaut proposée à l'écran : aujourd'hui et les 13 jours suivants. */
    public Duration fenetreProposee() {
        return Duration.ofDays(14);
    }
}
