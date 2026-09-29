package ma.rdvsante.rendezvous.depot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import ma.rdvsante.rendezvous.domaine.EntreeFile;
import ma.rdvsante.rendezvous.domaine.EtatFile;

public interface DepotEntreeFile extends JpaRepository<EntreeFile, UUID> {

    /** La file visible à l'écran, dans l'ordre d'arrivée. */
    @Query("""
            SELECT e FROM EntreeFile e
            JOIN FETCH e.rendezVous r
            JOIN FETCH r.creneau c
            JOIN FETCH c.praticien
            WHERE e.clinique.id = :cliniqueId AND e.etat = :etat
            ORDER BY e.arriveLe
            """)
    List<EntreeFile> parEtat(UUID cliniqueId, EtatFile etat);

    /**
     * Le prochain patient à appeler, verrouillé le temps de la transaction.
     *
     * <p>Deux secrétaires cliquent sur « Suivant » en même temps : sans
     * verrou, elles lisent toutes deux la même ligne et appellent le même
     * patient, pendant que le suivant reste assis. {@code PESSIMISTIC_WRITE}
     * fait attendre la seconde, qui voit alors l'entrée déjà passée en
     * {@code APPELE} et prend la ligne d'après.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT e FROM EntreeFile e
            WHERE e.clinique.id = :cliniqueId AND e.etat = :etat
            ORDER BY e.arriveLe
            LIMIT 1
            """)
    Optional<EntreeFile> prochainAAppeler(UUID cliniqueId, EtatFile etat);

    /**
     * Une entrée avec tout ce dont la vue a besoin, déjà chargé.
     *
     * <p>Sans ce chargement, le service rendrait une entité dont les
     * associations sont des proxys ; le contrôleur les traverserait une fois
     * la session Hibernate fermée, et tomberait sur
     * {@code LazyInitializationException}. Le correctif n'est pas d'ouvrir la
     * session plus longtemps ({@code open-in-view}), mais de rendre un objet
     * complet : l'erreur apparaît alors au développement, pas en production.
     */
    @Query("""
            SELECT e FROM EntreeFile e
            JOIN FETCH e.clinique
            JOIN FETCH e.rendezVous r
            JOIN FETCH r.creneau c
            JOIN FETCH c.praticien
            WHERE e.id = :id
            """)
    Optional<EntreeFile> parIdComplet(UUID id);

    boolean existsByRendezVousId(UUID rendezVousId);

    Optional<EntreeFile> findByRendezVousId(UUID rendezVousId);
}
