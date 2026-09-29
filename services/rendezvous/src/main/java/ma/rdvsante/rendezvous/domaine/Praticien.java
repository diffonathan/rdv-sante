package ma.rdvsante.rendezvous.domaine;

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

@Entity
@Table(name = "praticien")
@Getter
@Setter
@NoArgsConstructor
public class Praticien {

    @Id
    private UUID id;

    /** LAZY partout : un agenda charge des centaines de créneaux, chacun
        tirerait sa clinique entière en EAGER. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinique_id", nullable = false)
    private Clinique clinique;

    @Column(nullable = false, length = 10)
    private String civilite;

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(nullable = false, length = 80)
    private String prenom;

    @Column(nullable = false, length = 120)
    private String specialite;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe = Instant.now();

    public Praticien(UUID id, Clinique clinique, String civilite, String nom,
                     String prenom, String specialite) {
        this.id = id;
        this.clinique = clinique;
        this.civilite = civilite;
        this.nom = nom;
        this.prenom = prenom;
        this.specialite = specialite;
    }

    /** « Dr Amina Benali », tel qu'affiché au patient. */
    public String nomComplet() {
        return civilite + " " + prenom + " " + nom;
    }
}
