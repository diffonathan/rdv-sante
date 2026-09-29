package ma.rdvsante.rendezvous.depot;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ma.rdvsante.rendezvous.domaine.Praticien;

public interface DepotPraticien extends JpaRepository<Praticien, UUID> {

    /** {@code JOIN FETCH} : sans lui, afficher dix praticiens déclenche dix
        requêtes de plus pour aller chercher leur clinique (N+1). */
    @Query("""
            SELECT p FROM Praticien p
            JOIN FETCH p.clinique c
            WHERE c.id = :cliniqueId
            ORDER BY p.nom, p.prenom
            """)
    List<Praticien> parClinique(UUID cliniqueId);
}
