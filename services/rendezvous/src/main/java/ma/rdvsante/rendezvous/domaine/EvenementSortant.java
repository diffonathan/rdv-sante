package ma.rdvsante.rendezvous.domaine;

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
 * Une ligne d'outbox : un événement écrit dans la même transaction que la
 * donnée métier, publié vers Kafka ensuite (décision D2).
 *
 * <p>C'est la pièce qui empêche les deux pannes classiques : un rendez-vous
 * enregistré dont personne n'est prévenu, ou une notification pour un
 * rendez-vous qui n'existe pas.
 */
@Entity
@Table(name = "evenement_sortant")
@Getter
@NoArgsConstructor
public class EvenementSortant {

    /** Sert aussi de clé d'idempotence côté consommateur (décision D3). */
    @Id
    private UUID id;

    @Column(name = "agregat_type", nullable = false, length = 60)
    private String agregatType;

    @Column(name = "agregat_id", nullable = false)
    private UUID agregatId;

    /** Clé de partition Kafka : tous les événements d'un même agrégat vont
        dans la même partition, donc leur ordre est garanti. */
    @Column(name = "cle_partition", nullable = false, length = 120)
    private String clePartition;

    /** Le nom du topic, par exemple {@code rendezvous.reserve}. */
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

    public EvenementSortant(UUID id, String agregatType, UUID agregatId,
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
        // Tronqué : une trace Kafka complète fait plusieurs kilo-octets et on
        // ne veut pas d'une table d'outbox gonflée par des piles d'appels.
        this.derniereErreur = erreur != null && erreur.length() > 500
                ? erreur.substring(0, 500)
                : erreur;
    }

    public boolean estPublie() {
        return publieLe != null;
    }
}
