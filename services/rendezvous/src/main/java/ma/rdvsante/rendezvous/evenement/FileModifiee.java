package ma.rdvsante.rendezvous.evenement;

import java.util.UUID;

/**
 * Événement <strong>interne</strong> au service : la file d'une clinique a
 * bougé.
 *
 * <p>À ne pas confondre avec {@link Evenements.FileAvancee}, qui part sur
 * Kafka vers les autres services. Celui-ci ne quitte jamais la JVM : il sert
 * uniquement à prévenir les navigateurs connectés en SSE. Deux besoins
 * différents, deux canaux — mélanger les deux obligerait à consommer notre
 * propre topic Kafka pour rafraîchir un écran.
 */
public record FileModifiee(UUID cliniqueId) {
}
