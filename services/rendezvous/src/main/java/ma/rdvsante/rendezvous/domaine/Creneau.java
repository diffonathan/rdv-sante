package ma.rdvsante.rendezvous.domaine;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un intervalle de temps proposé par un praticien.
 *
 * <p>Le créneau ne sait pas s'il est pris : c'est le rendez-vous qui le
 * référence. L'unicité — un seul rendez-vous actif par créneau — est garantie
 * par un index partiel en base (décision D4), pas par un drapeau que deux
 * transactions pourraient lire avant de l'écrire.
 */
@Entity
@Table(name = "creneau")
@Getter
@Setter
@NoArgsConstructor
public class Creneau {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "praticien_id", nullable = false)
    private Praticien praticien;

    @Column(nullable = false)
    private Instant debut;

    @Column(nullable = false)
    private Instant fin;

    public Creneau(UUID id, Praticien praticien, Instant debut, Instant fin) {
        if (!fin.isAfter(debut)) {
            throw new IllegalArgumentException("La fin du créneau doit suivre son début.");
        }
        this.id = id;
        this.praticien = praticien;
        this.debut = debut;
        this.fin = fin;
    }

    public Duration duree() {
        return Duration.between(debut, fin);
    }

    /** Un créneau déjà commencé ne se réserve plus. */
    public boolean estPasse(Instant maintenant) {
        return !debut.isAfter(maintenant);
    }
}
