package ma.rdvsante.patients.depot;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ma.rdvsante.patients.domaine.EvenementSortantPatient;

public interface DepotEvenementSortantPatient extends JpaRepository<EvenementSortantPatient, UUID> {

    /**
     * Le prochain lot à publier, verrouillé sans faire attendre les autres
     * instances ({@code SKIP LOCKED}) — voir le service « rendezvous » pour le
     * raisonnement complet.
     */
    @Query(value = """
            SELECT * FROM evenement_sortant_patient
            WHERE publie_le IS NULL
            ORDER BY cree_le
            LIMIT :taille
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<EvenementSortantPatient> lotAPublier(int taille);

    List<EvenementSortantPatient> findByAgregatIdOrderByCreeLe(UUID agregatId);
}
