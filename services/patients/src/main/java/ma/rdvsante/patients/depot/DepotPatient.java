package ma.rdvsante.patients.depot;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ma.rdvsante.patients.domaine.Patient;

public interface DepotPatient extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByTelephone(String telephone);
}
