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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "rendez_vous")
@Getter
@NoArgsConstructor
public class RendezVous {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creneau_id", nullable = false)
    private Creneau creneau;

    /** Vient du service « patients ». Pas de clé étrangère : un service ne
        référence pas les tables d'un autre. */
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    /** Copie volontaire : la file d'attente doit s'afficher même si le service
        « patients » est arrêté. */
    @Column(name = "patient_nom", nullable = false, length = 160)
    private String patientNom;

    @Column(name = "patient_telephone", nullable = false, length = 30)
    private String patientTelephone;

    /** STRING et non ORDINAL : un ORDINAL stocke 0,1,2… et réordonner l'enum
        un jour réécrirait silencieusement l'historique. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutRendezVous statut;

    @Column(name = "motif_annulation", length = 255)
    private String motifAnnulation;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe = Instant.now();

    @Column(name = "annule_le")
    private Instant annuleLe;

    public RendezVous(UUID id, Creneau creneau, UUID patientId,
                      String patientNom, String patientTelephone) {
        this.id = id;
        this.creneau = creneau;
        this.patientId = patientId;
        this.patientNom = patientNom;
        this.patientTelephone = patientTelephone;
        this.statut = StatutRendezVous.CONFIRME;
    }

    /**
     * Annule le rendez-vous et libère le créneau.
     *
     * <p>Le statut ne se modifie pas de l'extérieur : passer par une méthode
     * garantit que {@code annuleLe} est toujours renseigné en même temps, et
     * qu'on n'annule pas deux fois.
     */
    public void annuler(String motif, Instant quand) {
        if (statut == StatutRendezVous.ANNULE) {
            throw new IllegalStateException("Ce rendez-vous est déjà annulé.");
        }
        this.statut = StatutRendezVous.ANNULE;
        this.motifAnnulation = motif;
        this.annuleLe = quand;
    }

    public void marquerHonore() {
        exigerConfirme();
        this.statut = StatutRendezVous.HONORE;
    }

    public void marquerAbsent() {
        exigerConfirme();
        this.statut = StatutRendezVous.ABSENT;
    }

    private void exigerConfirme() {
        if (statut != StatutRendezVous.CONFIRME) {
            throw new IllegalStateException(
                    "Un rendez-vous « " + statut + " » ne peut plus changer d'état.");
        }
    }
}
