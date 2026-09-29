package ma.rdvsante.rendezvous.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import ma.rdvsante.rendezvous.TestcontainersConfiguration;
import ma.rdvsante.rendezvous.depot.DepotClinique;
import ma.rdvsante.rendezvous.depot.DepotCreneau;
import ma.rdvsante.rendezvous.depot.DepotEvenementSortant;
import ma.rdvsante.rendezvous.depot.DepotPraticien;
import ma.rdvsante.rendezvous.depot.DepotRendezVous;
import ma.rdvsante.rendezvous.domaine.Clinique;
import ma.rdvsante.rendezvous.domaine.Creneau;
import ma.rdvsante.rendezvous.domaine.Praticien;
import ma.rdvsante.rendezvous.domaine.RendezVous;
import ma.rdvsante.rendezvous.domaine.StatutRendezVous;
import ma.rdvsante.rendezvous.evenement.Evenements;

/**
 * Tests d'intégration contre un vrai PostgreSQL et un vrai Kafka.
 *
 * <p>La classe n'est <strong>pas</strong> {@code @Transactional} : le test de
 * concurrence a besoin que chaque fil ouvre sa propre transaction et la valide
 * vraiment. Une transaction de test englobante les mettrait tous dans le même
 * contexte, et la course qu'on cherche à provoquer n'aurait jamais lieu.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReservationIT {

    @Autowired private ServiceReservation reservation;
    @Autowired private DepotClinique depotClinique;
    @Autowired private DepotPraticien depotPraticien;
    @Autowired private DepotCreneau depotCreneau;
    @Autowired private DepotRendezVous depotRendezVous;
    @Autowired private DepotEvenementSortant depotOutbox;

    private Creneau creneau;

    @BeforeEach
    void semer() {
        Clinique clinique = depotClinique.save(new Clinique(UUID.randomUUID(),
                "Clinique Al Amal", "Marrakech", "12, avenue Mohammed VI", "+212524430001"));
        Praticien praticien = depotPraticien.save(new Praticien(UUID.randomUUID(),
                clinique, "Dr", "Benali", "Amina", "Médecine générale"));

        Instant debut = Instant.now().plus(Duration.ofDays(1));
        creneau = depotCreneau.save(new Creneau(UUID.randomUUID(), praticien,
                debut, debut.plus(Duration.ofMinutes(20))));
    }

    @Test
    @DisplayName("réserver écrit le rendez-vous ET son événement dans la même transaction")
    void reservationDeposeUnEvenement() {
        RendezVous rdv = reservation.reserver(creneau.getId(), UUID.randomUUID(),
                "Fatima Zahra El Idrissi", "0661234567");

        assertThat(rdv.getStatut()).isEqualTo(StatutRendezVous.CONFIRME);

        var evenements = depotOutbox.findByAgregatIdOrderByCreeLe(rdv.getId());
        assertThat(evenements).hasSize(1);
        assertThat(evenements.getFirst().getType()).isEqualTo(Evenements.TOPIC_RESERVE);
        // La clé de partition vaut l'identifiant de l'agrégat : c'est ce qui
        // garantit qu'une annulation ne doublera jamais sa réservation.
        assertThat(evenements.getFirst().getClePartition()).isEqualTo(rdv.getId().toString());
        assertThat(evenements.getFirst().getChargeUtile()).contains("Fatima Zahra El Idrissi");
    }

    @Test
    @DisplayName("huit patients se disputent le même créneau : un seul l'obtient")
    void unSeulGagneLaCourse() throws Exception {
        int concurrents = 8;
        ExecutorService pool = Executors.newFixedThreadPool(concurrents);
        CountDownLatch depart = new CountDownLatch(1);
        AtomicInteger succes = new AtomicInteger();
        AtomicInteger conflits = new AtomicInteger();

        List<Callable<Void>> tentatives = new ArrayList<>();
        for (int i = 0; i < concurrents; i++) {
            int n = i;
            tentatives.add(() -> {
                // Tous les fils attendent le même signal : sans cela, le
                // premier aurait fini avant que le dernier ne démarre, et il
                // n'y aurait aucune course à observer.
                depart.await(10, TimeUnit.SECONDS);
                try {
                    reservation.reserver(creneau.getId(), UUID.randomUUID(),
                            "Patient " + n, "066123456" + (n % 10));
                    succes.incrementAndGet();
                } catch (ErreursMetier.CreneauDejaPris e) {
                    conflits.incrementAndGet();
                } catch (Exception e) {
                    // Toute autre exception est un défaut : on la laisse
                    // remonter pour que le test échoue avec sa vraie cause.
                    throw e;
                }
                return null;
            });
        }

        List<Future<Void>> resultats = new ArrayList<>();
        for (Callable<Void> t : tentatives) {
            resultats.add(pool.submit(t));
        }
        depart.countDown();
        for (Future<Void> f : resultats) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(succes.get())
                .as("exactement une réservation doit aboutir")
                .isEqualTo(1);
        assertThat(conflits.get())
                .as("les sept autres doivent recevoir un conflit, pas une erreur serveur")
                .isEqualTo(concurrents - 1);

        long actifs = depotRendezVous.findAll().stream()
                .filter(r -> r.getCreneau().getId().equals(creneau.getId()))
                .filter(r -> r.getStatut() != StatutRendezVous.ANNULE)
                .count();
        assertThat(actifs)
                .as("la base ne contient qu'un rendez-vous actif sur ce créneau")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("annuler libère le créneau, qui redevient réservable")
    void annulationLibereLeCreneau() {
        RendezVous premier = reservation.reserver(creneau.getId(), UUID.randomUUID(),
                "Premier Patient", "0661111111");

        assertThatThrownBy(() -> reservation.reserver(creneau.getId(), UUID.randomUUID(),
                "Second Patient", "0662222222"))
                .isInstanceOf(ErreursMetier.CreneauDejaPris.class);

        reservation.annuler(premier.getId(), "Empêchement du patient");

        // L'index unique est PARTIEL : il n'inclut pas les annulés, donc la
        // seconde réservation passe maintenant. Un index unique ordinaire
        // aurait condamné ce créneau à jamais.
        RendezVous second = reservation.reserver(creneau.getId(), UUID.randomUUID(),
                "Second Patient", "0662222222");
        assertThat(second.getStatut()).isEqualTo(StatutRendezVous.CONFIRME);

        var evenements = depotOutbox.findByAgregatIdOrderByCreeLe(premier.getId());
        assertThat(evenements).extracting("type")
                .containsExactly(Evenements.TOPIC_RESERVE, Evenements.TOPIC_ANNULE);
    }

    @Test
    @DisplayName("annuler deux fois est refusé")
    void doubleAnnulation() {
        RendezVous rdv = reservation.reserver(creneau.getId(), UUID.randomUUID(),
                "Patient", "0663333333");
        reservation.annuler(rdv.getId(), "Premier motif");

        assertThatThrownBy(() -> reservation.annuler(rdv.getId(), "Second motif"))
                .isInstanceOf(ErreursMetier.ReglementNonRespecte.class)
                .hasMessageContaining("déjà annulé");
    }
}
