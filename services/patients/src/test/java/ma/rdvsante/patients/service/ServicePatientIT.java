package ma.rdvsante.patients.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import ma.rdvsante.patients.TestcontainersConfiguration;
import ma.rdvsante.patients.depot.DepotEvenementSortantPatient;
import ma.rdvsante.patients.depot.DepotPatient;
import ma.rdvsante.patients.domaine.Patient;
import ma.rdvsante.patients.evenement.Evenements;

/**
 * L'enregistrement d'un patient est <strong>idempotent sur le téléphone</strong>.
 *
 * <p>Le front appelle ce service avant chaque réservation sans savoir si le
 * dossier existe. Réserver trois fois depuis trois navigateurs ne doit pas
 * créer trois patients, sans quoi le même numéro recevrait trois fois chaque
 * rappel.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ServicePatientIT {

    private static final String TELEPHONE = "0667889900";

    @Autowired private ServicePatient service;
    @Autowired private DepotPatient depot;
    @Autowired private DepotEvenementSortantPatient outbox;

    @BeforeEach
    void nettoyer() {
        outbox.deleteAll();
        depot.deleteAll();
    }

    /** Neutralise le reformatage jsonb de PostgreSQL. */
    private static String sansEspaces(ma.rdvsante.patients.domaine.EvenementSortantPatient e) {
        return e.getChargeUtile().replace(" ", "");
    }

    private Patient enregistrer(String prenom, String nom) {
        return service.enregistrer(nom, prenom, TELEPHONE, null, Patient.Canal.SMS, true);
    }

    @Test
    @DisplayName("un nouveau patient est créé et son inscription annoncée")
    void creationNominale() {
        Patient p = enregistrer("Rachid", "Benjelloun");

        assertThat(p.nomComplet()).isEqualTo("Rachid Benjelloun");
        assertThat(depot.count()).isEqualTo(1);

        var evenements = outbox.findByAgregatIdOrderByCreeLe(p.getId());
        assertThat(evenements).hasSize(1);
        assertThat(evenements.getFirst().getType()).isEqualTo(Evenements.TOPIC_ENREGISTRE);
        // « creation: true » : c'est ce drapeau qui évite un message de
        // bienvenue à chaque réservation ultérieure.
        //
        // Les espaces sont retirées avant comparaison : la colonne est de type
        // jsonb, et PostgreSQL REFORMATE le document à l'écriture (clés
        // réordonnées, espace après les deux-points). Comparer la chaîne brute
        // rendrait le test dépendant d'un détail de stockage.
        assertThat(sansEspaces(evenements.getFirst())).contains("\"creation\":true");
    }

    @Test
    @DisplayName("le même téléphone ne crée pas un second dossier")
    void memeTelephoneMemeDossier() {
        Patient premier = enregistrer("Rachid", "Benjelloun");
        Patient second = enregistrer("Rachid", "Benjelloun");

        assertThat(second.getId()).isEqualTo(premier.getId());
        assertThat(depot.count()).isEqualTo(1);

        // Deux événements, mais le second annonce une mise à jour.
        var evenements = outbox.findByAgregatIdOrderByCreeLe(premier.getId());
        assertThat(evenements).hasSize(2);
        assertThat(sansEspaces(evenements.get(1))).contains("\"creation\":false");
    }

    @Test
    @DisplayName("un nom corrigé met à jour le dossier existant")
    void miseAJourDuNom() {
        Patient premier = enregistrer("Rachid", "Benjeloun");
        Patient corrige = enregistrer("Rachid", "Benjelloun");

        assertThat(corrige.getId()).isEqualTo(premier.getId());
        assertThat(depot.findByTelephone(TELEPHONE)).get()
                .extracting(Patient::getNom).isEqualTo("Benjelloun");
    }

    @Test
    @DisplayName("sans consentement, on refuse plutôt que de promettre des rappels")
    void consentementObligatoire() {
        assertThatThrownBy(() ->
                service.enregistrer("Benjelloun", "Rachid", TELEPHONE, null,
                        Patient.Canal.SMS, false))
                .isInstanceOf(ServicePatient.ReglementNonRespecte.class)
                .hasMessageContaining("consentement");

        assertThat(depot.count()).isZero();
    }

    @Test
    @DisplayName("quatre inscriptions simultanées du même numéro : un seul dossier")
    void inscriptionsConcurrentes() throws Exception {
        int concurrents = 4;
        ExecutorService pool = Executors.newFixedThreadPool(concurrents);
        CountDownLatch depart = new CountDownLatch(1);

        List<Callable<Void>> tentatives = IntStream.range(0, concurrents)
                .<Callable<Void>>mapToObj(i -> () -> {
                    depart.await(10, TimeUnit.SECONDS);
                    try {
                        enregistrer("Rachid", "Benjelloun");
                    } catch (Exception e) {
                        // L'index unique peut faire échouer un des fils : c'est
                        // le comportement attendu, pas un défaut. Ce qui compte
                        // est qu'aucun doublon n'atteigne la base.
                    }
                    return null;
                })
                .toList();

        List<Future<Void>> futurs = new ArrayList<>();
        tentatives.forEach(t -> futurs.add(pool.submit(t)));
        depart.countDown();
        for (Future<Void> f : futurs) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(depot.count())
                .as("l'index unique sur le téléphone empêche tout doublon")
                .isEqualTo(1);
    }
}
