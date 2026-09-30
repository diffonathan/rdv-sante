package ma.rdvsante.gateway.documentation;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import ma.rdvsante.documentation.Documentation;

import reactor.core.publisher.Mono;

/**
 * La documentation technique, derrière un mot de passe.
 *
 * Elle s'adresse à un recruteur ou à un développeur qui veut aller plus loin
 * que l'écran « Sous le capot », lequel reste volontairement sans jargon.
 *
 * <h2>Pourquoi le contenu est rendu par le serveur, et non embarqué</h2>
 *
 * La tentation est d'écrire la documentation dans le front et de masquer la
 * page tant que le mot de passe n'est pas saisi. Cela ne protège rien : le
 * texte part dans le paquet JavaScript, et il suffit de l'ouvrir pour tout
 * lire sans jamais taper le mot de passe.
 *
 * Ici, le contenu ne quitte le serveur qu'une fois le mot de passe vérifié.
 * C'est la différence entre une porte fermée et une porte peinte sur un mur.
 *
 * <h2>Trois précautions</h2>
 *
 * <ol>
 *   <li>Le mot de passe vient d'une variable d'environnement. Ce dépôt est
 *       public : une valeur écrite dans le code serait lisible par tous, et le
 *       verrou ne verrouillerait rien.</li>
 *   <li>La comparaison est faite en temps constant. Un {@code equals} s'arrête
 *       au premier caractère différent, et cette différence de durée — quelques
 *       microsecondes, mesurables sur des milliers d'essais — laisse deviner le
 *       mot de passe caractère par caractère.</li>
 *   <li>Cinq essais par minute et par adresse. Sans cela, même un mot de passe
 *       long finit par tomber : une machine en essaie des millions par heure.</li>
 * </ol>
 *
 * <p>Non configurée, la variable FERME la porte au lieu de l'ouvrir avec une
 * chaîne vide. Une variable oubliée au déploiement serait sinon un accès libre,
 * sans le moindre signe visible.
 */
@RestController
@RequestMapping("/api/documentation")
class ControleurDocumentation {

    private static final int ESSAIS_PAR_MINUTE = 5;

    private final String motDePasseAttendu;

    /** Compteur d'essais par adresse. Une carte en mémoire suffit : la
     *  passerelle est le seul point d'entrée, et un redémarrage qui remet les
     *  compteurs à zéro n'ouvre aucune brèche — il faudrait avoir gardé la
     *  main assez longtemps pour que cela change quelque chose. */
    private final Map<String, Essais> essaisParAdresse = new ConcurrentHashMap<>();

    ControleurDocumentation(@Value("${rdv.documentation.mot-de-passe:}") String motDePasseAttendu) {
        this.motDePasseAttendu = motDePasseAttendu;
    }

    @PostMapping
    Mono<ResponseEntity<Object>> ouvrir(@RequestBody Demande demande, ServerWebExchange echange) {
        String adresse = adresseDe(echange);

        if (tropDEssais(adresse)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Trop d'essais. Réessayez dans une minute.")));
        }

        String saisi = demande == null || demande.motDePasse() == null ? "" : demande.motDePasse();

        if (motDePasseAttendu.isEmpty() || !comparaisonConstante(motDePasseAttendu, saisi)) {
            compterEssai(adresse);

            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Mot de passe incorrect.")));
        }

        essaisParAdresse.remove(adresse);

        return Mono.just(ResponseEntity.ok(Documentation.sections()));
    }

    /**
     * Compare deux chaînes en un temps qui ne dépend pas de l'endroit où elles
     * divergent. On parcourt TOUJOURS la longueur attendue, et l'écart de
     * longueur est accumulé dans le même résultat plutôt que court-circuité.
     */
    private static boolean comparaisonConstante(String attendu, String saisi) {
        byte[] a = attendu.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = saisi.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return java.security.MessageDigest.isEqual(a, b);
    }

    private String adresseDe(ServerWebExchange echange) {
        var distante = echange.getRequest().getRemoteAddress();

        return distante == null ? "inconnue" : distante.getAddress().getHostAddress();
    }

    private boolean tropDEssais(String adresse) {
        Essais essais = essaisParAdresse.get(adresse);

        return essais != null
                && essais.depuis().isAfter(Instant.now().minus(Duration.ofMinutes(1)))
                && essais.nombre() >= ESSAIS_PAR_MINUTE;
    }

    private void compterEssai(String adresse) {
        essaisParAdresse.compute(adresse, (cle, essais) -> {
            // Fenêtre expirée : on repart de zéro plutôt que d'accumuler
            // indéfiniment des essais vieux d'une heure.
            if (essais == null || essais.depuis().isBefore(Instant.now().minus(Duration.ofMinutes(1)))) {
                return new Essais(1, Instant.now());
            }

            return new Essais(essais.nombre() + 1, essais.depuis());
        });
    }

    record Demande(String motDePasse) {}

    private record Essais(int nombre, Instant depuis) {}
}
