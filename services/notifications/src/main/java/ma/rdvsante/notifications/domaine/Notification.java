package ma.rdvsante.notifications.domaine;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor
public class Notification {

    public enum Type {
        BIENVENUE,
        CONFIRMATION,
        ANNULATION,
        BIENTOT_VOTRE_TOUR
    }

    public enum Canal {
        SMS,
        EMAIL
    }

    @Id
    private UUID id;

    /** L'événement Kafka qui a déclenché ce message — la trace remonte
        jusqu'à la ligne d'outbox du service émetteur. */
    @Column(name = "evenement_id", nullable = false)
    private UUID evenementId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Canal canal;

    @Column(nullable = false, length = 30)
    private String destinataire;

    @Column(name = "patient_nom", nullable = false, length = 160)
    private String patientNom;

    @Column(nullable = false, columnDefinition = "text")
    private String contenu;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe = Instant.now();

    @Column(name = "envoye_le")
    private Instant envoyeLe;

    public Notification(UUID id, UUID evenementId, Type type, Canal canal,
                        String destinataire, String patientNom, String contenu) {
        this.id = id;
        this.evenementId = evenementId;
        this.type = type;
        this.canal = canal;
        this.destinataire = destinataire;
        this.patientNom = patientNom;
        this.contenu = contenu;
    }

    public void marquerEnvoye(Instant quand) {
        this.envoyeLe = quand;
    }
}
