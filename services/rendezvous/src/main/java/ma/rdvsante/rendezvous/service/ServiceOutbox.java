package ma.rdvsante.rendezvous.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ma.rdvsante.rendezvous.depot.DepotEvenementSortant;
import ma.rdvsante.rendezvous.domaine.EvenementSortant;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Écrit un événement dans l'outbox — jamais vers Kafka.
 *
 * <p>L'appel se fait <strong>depuis la transaction métier</strong> : la ligne
 * d'outbox est validée avec le rendez-vous, ou pas du tout. C'est toute
 * l'astuce de la décision D2 : deux systèmes sans transaction commune
 * deviennent un seul système, parce qu'on n'écrit plus que dans un seul.
 *
 * <p>{@link Propagation#MANDATORY} l'impose : appelée hors transaction, la
 * méthode échoue au lieu d'ouvrir la sienne. Sans ce garde-fou, un appel mal
 * placé écrirait l'événement même quand le rendez-vous est annulé par un
 * rollback — exactement la panne qu'on cherche à éliminer.
 *
 * <p>Note de version : Spring Boot 4 embarque <strong>Jackson 3</strong>
 * ({@code tools.jackson}), où les erreurs de sérialisation sont devenues non
 * vérifiées. On les rattrape quand même, pour transformer un défaut de
 * programmation en message lisible plutôt qu'en trace brute.
 */
@Service
public class ServiceOutbox {

    private final DepotEvenementSortant depot;
    private final ObjectMapper json;

    public ServiceOutbox(DepotEvenementSortant depot, ObjectMapper json) {
        this.depot = depot;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public EvenementSortant deposer(String topic, String agregatType, UUID agregatId, Object charge) {
        String corps;
        try {
            corps = json.writeValueAsString(charge);
        } catch (JacksonException e) {
            // Un événement non sérialisable est un défaut de programmation, pas
            // une panne d'exécution : on fait échouer la transaction métier
            // plutôt que de publier une charge utile vide.
            throw new IllegalStateException(
                    "Événement " + topic + " non sérialisable : " + e.getMessage(), e);
        }
        return depot.save(new EvenementSortant(
                UUID.randomUUID(), agregatType, agregatId, topic, corps));
    }
}
