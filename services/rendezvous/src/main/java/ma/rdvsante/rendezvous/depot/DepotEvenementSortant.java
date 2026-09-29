package ma.rdvsante.rendezvous.depot;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ma.rdvsante.rendezvous.domaine.EvenementSortant;

public interface DepotEvenementSortant extends JpaRepository<EvenementSortant, UUID> {

    /**
     * Le prochain lot d'événements à publier.
     *
     * <p>Requête native pour le {@code FOR UPDATE SKIP LOCKED} : quand
     * plusieurs instances du service tournent, chacune verrouille son lot et
     * les autres **sautent** les lignes déjà prises au lieu d'attendre. Sans
     * {@code SKIP LOCKED}, les publieurs se mettraient en file derrière le
     * premier et la publication ne serait pas parallélisable ; sans
     * {@code FOR UPDATE}, deux instances enverraient le même message.
     *
     * <p>L'ordre par {@code cree_le} garantit qu'une annulation ne part jamais
     * avant la réservation qu'elle annule.
     */
    @Query(value = """
            SELECT * FROM evenement_sortant
            WHERE publie_le IS NULL
            ORDER BY cree_le
            LIMIT :taille
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<EvenementSortant> lotAPublier(int taille);

    long countByPublieLeIsNull();

    List<EvenementSortant> findByAgregatIdOrderByCreeLe(UUID agregatId);
}
