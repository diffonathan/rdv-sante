package ma.rdvsante.rendezvous.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ma.rdvsante.rendezvous.depot.DepotCreneau;
import ma.rdvsante.rendezvous.depot.DepotRendezVous;
import ma.rdvsante.rendezvous.domaine.Clinique;
import ma.rdvsante.rendezvous.domaine.Creneau;
import ma.rdvsante.rendezvous.domaine.Praticien;

/**
 * Les règles qui se vérifient sans base de données.
 *
 * <p>L'horloge est injectée et figée : tester « un créneau passé n'est plus
 * réservable » avec {@code Instant.now()} obligerait à créer des créneaux dans
 * un futur proche et à espérer que la machine soit assez rapide — un test qui
 * échoue une fois sur vingt, le pire des tests.
 */
@ExtendWith(MockitoExtension.class)
class ServiceReservationTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-03T09:00:00Z");

    @Mock
    private DepotCreneau depotCreneau;
    @Mock
    private DepotRendezVous depotRendezVous;
    @Mock
    private ServiceOutbox outbox;

    private ServiceReservation service;

    @BeforeEach
    void preparer() {
        Clock horlogeFigee = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
        service = new ServiceReservation(depotCreneau, depotRendezVous, outbox, horlogeFigee);
    }

    private Creneau creneauA(Instant debut) {
        Clinique clinique = new Clinique(UUID.randomUUID(), "Clinique Al Amal",
                "Marrakech", "12, avenue Mohammed VI", "+212524430001");
        Praticien praticien = new Praticien(UUID.randomUUID(), clinique, "Dr",
                "Benali", "Amina", "Médecine générale");
        return new Creneau(UUID.randomUUID(), praticien, debut, debut.plusSeconds(1200));
    }

    @Test
    @DisplayName("un créneau inexistant remonte en Introuvable, pas en NullPointerException")
    void creneauInexistant() {
        UUID id = UUID.randomUUID();
        when(depotCreneau.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserver(id, UUID.randomUUID(), "Amina", "0661234567"))
                .isInstanceOf(ErreursMetier.Introuvable.class)
                .hasMessageContaining(id.toString());

        verify(depotRendezVous, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("un créneau déjà commencé est refusé, et aucun événement n'est déposé")
    void creneauPasse() {
        Creneau passe = creneauA(MAINTENANT.minusSeconds(60));
        when(depotCreneau.findById(passe.getId())).thenReturn(Optional.of(passe));

        assertThatThrownBy(() ->
                service.reserver(passe.getId(), UUID.randomUUID(), "Amina", "0661234567"))
                .isInstanceOf(ErreursMetier.ReglementNonRespecte.class)
                .hasMessageContaining("a commencé");

        // Le point important : le refus arrive AVANT toute écriture. Un
        // événement déposé puis annulé par un rollback serait invisible ici,
        // mais bien réel en production si l'ordre était inversé.
        verify(depotRendezVous, never()).saveAndFlush(any());
        verify(outbox, never()).deposer(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("un créneau qui commence exactement maintenant est déjà trop tard")
    void creneauQuiCommenceALaSeconde() {
        Creneau limite = creneauA(MAINTENANT);
        when(depotCreneau.findById(limite.getId())).thenReturn(Optional.of(limite));

        assertThatThrownBy(() ->
                service.reserver(limite.getId(), UUID.randomUUID(), "Amina", "0661234567"))
                .isInstanceOf(ErreursMetier.ReglementNonRespecte.class);
    }

    @Test
    @DisplayName("un créneau à venir est réservé et l'événement part dans l'outbox")
    void reservationNominale() {
        Creneau futur = creneauA(MAINTENANT.plusSeconds(3600));
        UUID patient = UUID.randomUUID();
        when(depotCreneau.findById(futur.getId())).thenReturn(Optional.of(futur));
        when(depotRendezVous.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        var rdv = service.reserver(futur.getId(), patient, "Fatima Zahra", "0661234567");

        assertThat(rdv.getStatut().occupeLeCreneau()).isTrue();
        assertThat(rdv.getPatientId()).isEqualTo(patient);
        verify(outbox).deposer(anyString(), anyString(), any(), any());
    }
}
