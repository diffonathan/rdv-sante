package ma.rdvsante.patients.domaine;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Outbox transactionnel (décision D2), recopié depuis le service
 * « rendezvous ».
 *
 * <p>La duplication est assumée : un module partagé entre services obligerait
 * les deux à recompiler et à se redéployer ensemble à chaque évolution — le
 * couplage que le découpage sert précisément à défaire. Vingt lignes recopiées
 * coûtent moins cher qu'une dépendance commune.
 */
@Entity
// Nommée par son service : « rendezvous » a son propre outbox, et deux
// tables homonymes se confondent dès qu'on les regarde ensemble.
@Table(name = "evenement_sortant_patient")
@Getter
@NoArgsConstructor
public class EvenementSortantPatient {

    @Id
    private UUID id;

    @Column(name = "agregat_type", nullable = false, length = 60)
    private String agregatType;

    @Column(name = "agregat_id", nullable = false)
    private UUID agregatId;

    @Column(name = "cle_partition", nullable = false, length = 120)
    private String clePartition;

    @Column(nullable = false, length = 80)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "charge_utile", nullable = false, columnDefinition = "jsonb")
    private String chargeUtile;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe = Instant.now();

    @Column(name = "publie_le")
    private Instant publieLe;

    @Column(nullable = false)
    private int tentatives;

    @Column(name = "derniere_erreur")
    private String derniereErreur;

    public EvenementSortantPatient(UUID id, String agregatType, UUID agregatId,
                            String type, String chargeUtile) {
        this.id = id;
        this.agregatType = agregatType;
        this.agregatId = agregatId;
        this.clePartition = agregatId.toString();
        this.type = type;
        this.chargeUtile = chargeUtile;
    }

    public void marquerPublie(Instant quand) {
        this.publieLe = quand;
        this.derniereErreur = null;
    }

    public void marquerEchec(String erreur) {
        this.tentatives++;
        this.derniereErreur = erreur != null && erreur.length() > 500
                ? erreur.substring(0, 500)
                : erreur;
    }
}
