package ma.rdvsante.rendezvous.domaine;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "clinique")
@Getter
@Setter
@NoArgsConstructor
public class Clinique {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String nom;

    @Column(nullable = false, length = 80)
    private String ville;

    @Column(nullable = false, length = 255)
    private String adresse;

    @Column(nullable = false, length = 30)
    private String telephone;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe = Instant.now();

    public Clinique(UUID id, String nom, String ville, String adresse, String telephone) {
        this.id = id;
        this.nom = nom;
        this.ville = ville;
        this.adresse = adresse;
        this.telephone = telephone;
    }
}
