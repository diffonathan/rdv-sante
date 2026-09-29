package ma.rdvsante.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
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

import ma.rdvsante.notifications.TestcontainersConfiguration;
import ma.rdvsante.notifications.depot.DepotNotification;
import ma.rdvsante.notifications.domaine.Notification;

/**
 * La garde contre les doublons (décision D3).
 *
 * <p>Ce test existe parce que la première version <strong>ne marchait pas</strong>
 * et que rien ne le montrait : la garde s'appuyait sur {@code save()} d'une
 * entité à identifiant assigné, ce que Spring Data traduit en {@code merge()},
 * donc en SELECT + UPDATE. Aucune contrainte violée, aucune exception, et
 * chaque rejeu d'un topic créait une seconde notification. Le défaut n'est
 * apparu qu'en rembobinant Kafka.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IdempotenceIT {

    @Autowired private ServiceNotification service;
    @Autowired private DepotNotification notifications;

    private UUID evenement;

    @BeforeEach
    void preparer() {
        notifications.deleteAll();
        evenement = UUID.randomUUID();
    }

    @Test
    @DisplayName("le même événement livré deux fois ne produit qu'une notification")
    void deuxLivraisonsUneSeuleNotification() {
        boolean premiere = enregistrer();
        boolean seconde = enregistrer();

        assertThat(premiere).as("la première livraison crée la notification").isTrue();
        assertThat(seconde).as("la seconde est reconnue comme un doublon").isFalse();
        assertThat(notifications.countByEvenementId(evenement)).isEqualTo(1);
    }

    @Test
    @DisplayName("un rejeu complet du topic ne duplique rien")
    void rejeuComplet() {
        IntStream.range(0, 5).forEach(i -> enregistrer());
        assertThat(notifications.countByEvenementId(evenement)).isEqualTo(1);
    }

    @Test
    @DisplayName("deux consommateurs simultanés sur le même message : une seule notification")
    void livraisonsConcurrentes() throws Exception {
        int concurrents = 6;
        ExecutorService pool = Executors.newFixedThreadPool(concurrents);
        CountDownLatch depart = new CountDownLatch(1);

        List<Callable<Boolean>> tentatives = IntStream.range(0, concurrents)
                .<Callable<Boolean>>mapToObj(i -> () -> {
                    depart.await(10, TimeUnit.SECONDS);
                    return enregistrer();
                })
                .toList();

        List<Future<Boolean>> futurs = tentatives.stream().map(pool::submit).toList();
        depart.countDown();

        long creations = 0;
        for (Future<Boolean> f : futurs) {
            if (Boolean.TRUE.equals(f.get(30, TimeUnit.SECONDS))) {
                creations++;
            }
        }
        pool.shutdown();

        // C'est l'atomicité de ON CONFLICT DO NOTHING qui tient ici : un
        // « SELECT puis INSERT » aurait laissé passer plusieurs écritures.
        assertThat(creations).as("une seule création").isEqualTo(1);
        assertThat(notifications.countByEvenementId(evenement)).isEqualTo(1);
    }

    private boolean enregistrer() {
        return service.enregistrer(evenement, "rendezvous.reserve",
                Notification.Type.CONFIRMATION, "0661234567", "Fatima Zahra El Idrissi",
                "Votre rendez-vous est confirmé.");
    }
}
