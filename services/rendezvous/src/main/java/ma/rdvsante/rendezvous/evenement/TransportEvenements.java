package ma.rdvsante.rendezvous.evenement;

import java.util.UUID;

/**
 * Par où sort un événement, une fois qu'il a quitté l'outbox.
 *
 * <p>Cette interface existe pour <strong>une</strong> raison : la démonstration
 * en ligne tourne sur un hébergement gratuit de 512 Mo, où quatre machines
 * virtuelles Java et un courtier Kafka ne tiennent pas. Les services y sont
 * donc réunis dans un seul processus, et les événements passent de l'un à
 * l'autre en mémoire au lieu de traverser le réseau.
 *
 * <p><strong>Ce qui ne change pas, et c'est le point.</strong> Le service
 * métier continue de n'écrire que dans PostgreSQL. L'outbox continue d'être
 * vidée par lots, de retenir les échecs, de compter les tentatives et de
 * repartir au tour suivant. Les consommateurs restent idempotents, et
 * l'identifiant de la ligne d'outbox voyage toujours avec le message. Seul le
 * tuyau change.
 *
 * <p>Autrement dit, ce n'est pas une architecture au rabais pour la
 * démonstration : c'est la même architecture, dont on a remplacé un adaptateur.
 * C'est précisément ce qu'une séparation entre métier et transport doit
 * permettre — et si elle ne le permettait pas, elle n'en serait pas une.
 *
 * <p>Le dépôt, lui, garde le vrai Kafka : c'est ce que les tests exercent,
 * contre un courtier réel démarré pour l'occasion.
 */
public interface TransportEvenements {

    /**
     * Envoie un événement, et rend la main seulement une fois qu'il est parti.
     *
     * <p>Synchrone à dessein : le publieur d'outbox ne marque la ligne comme
     * publiée qu'après le retour. Un envoi qui rendrait la main aussitôt ferait
     * marquer « publié » un message encore en vol, et une panne à cet instant
     * le perdrait sans laisser de trace.
     *
     * @param idEvenement l'identifiant de la ligne d'outbox, transporté avec le
     *                    message : c'est la clé de déduplication du consommateur
     * @throws Exception si l'envoi échoue ; l'appelant compte la tentative et
     *                   laisse la ligne en attente
     */
    void envoyer(String topic, String cle, String charge, UUID idEvenement) throws Exception;
}
