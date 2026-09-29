package ma.rdvsante.notifications.depot;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import ma.rdvsante.notifications.domaine.Notification;

public interface DepotNotification extends JpaRepository<Notification, UUID> {

    /** La boîte d'envoi, du plus récent au plus ancien. */
    List<Notification> findAllByOrderByCreeLeDesc(Limit limite);

    long countByEvenementId(UUID evenementId);
}
