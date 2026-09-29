package ma.rdvsante.rendezvous.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ma.rdvsante.rendezvous.domaine.RendezVous;
import ma.rdvsante.rendezvous.service.ServiceReservation;

@RestController
@RequestMapping("/api/rendez-vous")
class ControleurRendezVous {

    private final ServiceReservation reservation;

    ControleurRendezVous(ServiceReservation reservation) {
        this.reservation = reservation;
    }

    /**
     * Réserve un créneau.
     *
     * <p>Renvoie {@code 201 Created} avec l'en-tête {@code Location}, ou
     * {@code 409 Conflict} si le créneau vient d'être pris — voir
     * {@link GestionnaireErreurs}.
     */
    @PostMapping
    ResponseEntity<Vues.RendezVousVue> reserver(@Valid @RequestBody Demandes.Reservation demande) {
        RendezVous rdv = reservation.reserver(
                demande.creneauId(), demande.patientId(),
                demande.patientNom(), demande.patientTelephone());

        return ResponseEntity
                .created(URI.create("/api/rendez-vous/" + rdv.getId()))
                .body(Vues.RendezVousVue.de(rdv));
    }

    @GetMapping("/{id}")
    Vues.RendezVousVue consulter(@PathVariable UUID id) {
        return Vues.RendezVousVue.de(reservation.parId(id));
    }

    /**
     * Annule un rendez-vous.
     *
     * <p>{@code POST} sur une sous-ressource plutôt que {@code DELETE} : le
     * rendez-vous n'est pas supprimé, il change d'état et garde son motif.
     * Un {@code DELETE} laisserait croire qu'il disparaît de l'historique.
     */
    @PostMapping("/{id}/annulation")
    Vues.RendezVousVue annuler(@PathVariable UUID id,
                               @Valid @RequestBody Demandes.Annulation demande) {
        return Vues.RendezVousVue.de(reservation.annuler(id, demande.motif()));
    }
}
