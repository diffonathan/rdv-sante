package ma.rdvsante.rendezvous.depot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ma.rdvsante.rendezvous.domaine.Creneau;
import ma.rdvsante.rendezvous.domaine.StatutRendezVous;

public interface DepotCreneau extends JpaRepository<Creneau, UUID> {

    /**
     * Les créneaux encore libres d'un praticien sur une fenêtre de temps.
     *
     * <p>« Libre » se lit ici comme « aucun rendez-vous non annulé ne le
     * référence ». La disponibilité n'est donc jamais stockée : elle se déduit
     * des rendez-vous, et ne peut pas se désynchroniser d'eux.
     */
    @Query("""
            SELECT c FROM Creneau c
            JOIN FETCH c.praticien p
            WHERE p.id = :praticienId
              AND c.debut >= :debut
              AND c.debut <  :fin
              AND NOT EXISTS (
                  SELECT 1 FROM RendezVous r
                  WHERE r.creneau = c AND r.statut <> :annule
              )
            ORDER BY c.debut
            """)
    List<Creneau> libres(UUID praticienId, Instant debut, Instant fin, StatutRendezVous annule);
}
