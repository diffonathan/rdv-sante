package ma.rdvsante.notifications.domaine;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * La trace d'un événement déjà consommé.
 *
 * <p>Sa clé primaire est l'identifiant de l'événement : c'est elle, et non du
 * code, qui empêche le doublon. Un second message portant le même identifiant
 * fait échouer l'insertion, et le traitement s'arrête là.
 */
@Entity
@Table(name = "message_traite")
@Getter
@NoArgsConstructor
public class MessageTraite {

    @Id
    @Column(name = "evenement_id")
    private UUID evenementId;

    @Column(nullable = false, length = 80)
    private String topic;

    @Column(name = "traite_le", nullable = false)
    private Instant traiteLe = Instant.now();

    public MessageTraite(UUID evenementId, String topic) {
        this.evenementId = evenementId;
        this.topic = topic;
    }
}
