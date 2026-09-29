package ma.rdvsante.notifications.service;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ma.rdvsante.notifications.depot.DepotMessageTraite;
import ma.rdvsante.notifications.depot.DepotNotification;
import ma.rdvsante.notifications.domaine.Notification;

/**
 * Écrit une notification, une seule fois par événement.
 *
 * <p>L'idempotence (décision D3) est traitée ici plutôt que dans chaque
 * consommateur : trois consommateurs, un seul endroit où le doublon est
 * arrêté. Oublier la garde dans un nouveau consommateur devient impossible,
 * puisqu'il passe forcément par cette méthode.
 */
@Service
public class ServiceNotification {

    private static final Logger log = LoggerFactory.getLogger(ServiceNotification.class);

    private final DepotMessageTraite dejaVus;
    private final DepotNotification notifications;
    private final Clock horloge;

    public ServiceNotification(DepotMessageTraite dejaVus, DepotNotification notifications,
                               Clock horloge) {
        this.dejaVus = dejaVus;
        this.notifications = notifications;
        this.horloge = horloge;
    }

    /**
     * @return {@code true} si la notification a été créée, {@code false} si
     *         l'événement avait déjà été traité.
     */
    @Transactional
    public boolean enregistrer(UUID evenementId, String topic, Notification.Type type,
                               String destinataire, String patientNom, String contenu) {

        // La base tranche, en une seule instruction atomique : 1 = nouveau,
        // 0 = doublon. Voir DepotMessageTraite#marquerSiNouveau pour la raison
        // — save() aurait fait un merge et la garde n'aurait rien gardé.
        if (dejaVus.marquerSiNouveau(evenementId, topic) == 0) {
            log.debug("Événement {} déjà traité, ignoré.", evenementId);
            return false;
        }

        Notification notification = new Notification(
                UUID.randomUUID(), evenementId, type, Notification.Canal.SMS,
                destinataire, patientNom, contenu);

        // En démonstration, « envoyer » c'est écrire dans la boîte d'envoi.
        // Le jour où un fournisseur SMS est branché, c'est la seule ligne à
        // remplacer — et elle restera dans la même transaction, donc un envoi
        // raté fera rejouer le message plutôt que de le perdre.
        notification.marquerEnvoye(horloge.instant());
        notifications.save(notification);

        log.info("Notification {} pour {} ({})", type, patientNom, destinataire);
        return true;
    }
}
