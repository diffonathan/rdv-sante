package ma.rdvsante.notifications.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ma.rdvsante.notifications.depot.DepotNotification;
import ma.rdvsante.notifications.domaine.Notification;

/**
 * La boîte d'envoi, consultable.
 *
 * <p>Aucun contrat SMS n'étant souscrit pour une démonstration, les messages
 * sont affichés ici au lieu de partir. C'est aussi ce qui permet de
 * <em>montrer</em> que la chaîne événementielle fonctionne de bout en bout :
 * une réservation faite à l'écran apparaît ici une seconde plus tard, sans que
 * les deux services se soient parlé directement.
 */
@RestController
@RequestMapping("/api/notifications")
class ControleurBoiteEnvoi {

    private final DepotNotification depot;

    ControleurBoiteEnvoi(DepotNotification depot) {
        this.depot = depot;
    }

    record NotificationVue(UUID id, String type, String canal, String destinataire,
                           String patientNom, String contenu, Instant creeLe, Instant envoyeLe) {
        static NotificationVue de(Notification n) {
            return new NotificationVue(n.getId(), n.getType().name(), n.getCanal().name(),
                    n.getDestinataire(), n.getPatientNom(), n.getContenu(),
                    n.getCreeLe(), n.getEnvoyeLe());
        }
    }

    @GetMapping
    List<NotificationVue> boiteEnvoi(@RequestParam(defaultValue = "50") int limite) {
        return depot.findAllByOrderByCreeLeDesc(Limit.of(Math.clamp(limite, 1, 200))).stream()
                .map(NotificationVue::de)
                .toList();
    }
}
