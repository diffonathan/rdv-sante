package ma.rdvsante.rendezvous.depot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ma.rdvsante.rendezvous.domaine.RendezVous;
import ma.rdvsante.rendezvous.domaine.StatutRendezVous;

public interface DepotRendezVous extends JpaRepository<RendezVous, UUID> {

    /**
     * Le rendez-vous qui occupe ce créneau, s'il y en a un.
     *
     * <p>Sert au message d'erreur, pas à la décision : c'est l'index unique
     * partiel qui arbitre la double réservation (décision D4). Cette requête
     * arrive trop tard pour empêcher quoi que ce soit — deux transactions
     * peuvent toutes deux la voir vide avant d'insérer.
     */
    @Query("""
            SELECT r FROM RendezVous r
            WHERE r.creneau.id = :creneauId AND r.statut <> :annule
            """)
    Optional<RendezVous> occupantDuCreneau(UUID creneauId, StatutRendezVous annule);

    @Query("""
            SELECT r FROM RendezVous r
            JOIN FETCH r.creneau c
            JOIN FETCH c.praticien p
            JOIN FETCH p.clinique
            WHERE r.id = :id
            """)
    Optional<RendezVous> parIdComplet(UUID id);

    @Query("""
            SELECT r FROM RendezVous r
            JOIN FETCH r.creneau c
            JOIN FETCH c.praticien p
            JOIN FETCH p.clinique
            WHERE r.patientId = :patientId
            ORDER BY c.debut DESC
            """)
    List<RendezVous> parPatient(UUID patientId);
}
