package ma.rdvsante.rendezvous.depot;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ma.rdvsante.rendezvous.domaine.Clinique;

public interface DepotClinique extends JpaRepository<Clinique, UUID> {
}
