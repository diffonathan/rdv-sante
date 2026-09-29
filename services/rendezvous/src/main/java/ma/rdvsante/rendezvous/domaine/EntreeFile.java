package ma.rdvsante.rendezvous.domaine;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * La présence d'un patient dans la file d'attente du jour.
 *
 * <p>Il n'y a pas de champ « position ». Le rang se déduit de
 * {@code arriveLe}, qui ne change jamais : stocker une position obligerait à
 * renuméroter toute la file à chaque départ, et deux arrivées simultanées se
 * marcheraient dessus. Voir la migration V2 et {@code docs/architecture.md}.
 */
@Entity
@Table(name = "entree_file")
@Getter
@NoArgsConstructor
public class EntreeFile {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinique_id", nullable = false)
    private Clinique clinique;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rendez_vous_id", nullable = false, unique = true)
    private RendezVous rendezVous;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EtatFile etat;

    @Column(name = "arrive_le", nullable = false)
    private Instant arriveLe;

    @Column(name = "appele_le")
    private Instant appeleLe;

    @Column(name = "termine_le")
    private Instant termineLe;

    public EntreeFile(UUID id, Clinique clinique, RendezVous rendezVous, Instant arriveLe) {
        this.id = id;
        this.clinique = clinique;
        this.rendezVous = rendezVous;
        this.etat = EtatFile.EN_ATTENTE;
        this.arriveLe = arriveLe;
    }

    public void appeler(Instant quand) {
        exiger(EtatFile.EN_ATTENTE, "appeler");
        this.etat = EtatFile.APPELE;
        this.appeleLe = quand;
    }

    public void entrerEnConsultation() {
        exiger(EtatFile.APPELE, "faire entrer en consultation");
        this.etat = EtatFile.EN_CONSULTATION;
    }

    public void terminer(Instant quand) {
        if (etat != EtatFile.APPELE && etat != EtatFile.EN_CONSULTATION) {
            throw new IllegalStateException(
                    "Impossible de terminer une entrée « " + etat + " ».");
        }
        this.etat = EtatFile.TERMINE;
        this.termineLe = quand;
    }

    private void exiger(EtatFile attendu, String action) {
        if (etat != attendu) {
            throw new IllegalStateException(
                    "Impossible de " + action + " : l'entrée est « " + etat
                            + " », attendu « " + attendu + " ».");
        }
    }
}
