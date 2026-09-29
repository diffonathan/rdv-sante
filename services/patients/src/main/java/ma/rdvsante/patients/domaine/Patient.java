package ma.rdvsante.patients.domaine;

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
@Table(name = "patient")
@Getter
@NoArgsConstructor
public class Patient {

    public enum Canal {
        SMS,
        EMAIL
    }

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(nullable = false, length = 80)
    private String prenom;

    /** Identifie le patient : c'est le seul identifiant qu'il connaît par cœur. */
    @Column(nullable = false, length = 30)
    private String telephone;

    @Column(length = 160)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal_prefere", nullable = false, length = 20)
    private Canal canalPrefere;

    /** Sans consentement, aucune notification ne part. */
    @Column(name = "consent_contact", nullable = false)
    private boolean consentContact;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe = Instant.now();

    @Column(name = "maj_le", nullable = false)
    private Instant majLe = Instant.now();

    public Patient(UUID id, String nom, String prenom, String telephone, String email,
                   Canal canalPrefere, boolean consentContact) {
        this.id = id;
        this.nom = nom;
        this.prenom = prenom;
        this.telephone = telephone;
        this.email = email;
        this.canalPrefere = canalPrefere;
        this.consentContact = consentContact;
    }

    /**
     * Met à jour ce qui peut changer.
     *
     * <p>Le téléphone n'en fait pas partie : il identifie le patient. Le
     * modifier reviendrait à fusionner deux personnes, ce qui ne se décide pas
     * au détour d'un formulaire de réservation.
     */
    public void actualiser(String nom, String prenom, String email, Canal canal,
                           boolean consentement, Instant quand) {
        this.nom = nom;
        this.prenom = prenom;
        this.email = email;
        this.canalPrefere = canal;
        this.consentContact = consentement;
        this.majLe = quand;
    }

    public String nomComplet() {
        return prenom + " " + nom;
    }
}
