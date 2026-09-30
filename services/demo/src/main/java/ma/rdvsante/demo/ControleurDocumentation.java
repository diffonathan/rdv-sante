package ma.rdvsante.demo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ma.rdvsante.documentation.Documentation;

/**
 * La documentation technique, derrière un mot de passe.
 *
 * <p>Le jumeau servlet de celui de la passerelle : même contrat, mêmes
 * précautions, même texte — qui vient du module {@code documentation} et n'est
 * donc écrit qu'une fois. Deux implémentations sont nécessaires parce que la
 * passerelle est réactive et que celle-ci ne l'est pas ; deux textes ne
 * l'auraient pas été.
 *
 * <p>Trois précautions, et chacune répond à une façon concrète de forcer la
 * porte ou de faire fuiter le mot de passe :
 *
 * <ol>
 *   <li>le mot de passe vient d'une variable d'environnement — ce dépôt est
 *       public ;</li>
 *   <li>la comparaison est faite en temps constant : un {@code equals}
 *       s'arrête au premier caractère différent, et cette différence de durée
 *       laisse deviner le mot de passe caractère par caractère ;</li>
 *   <li>cinq essais par minute et par adresse.</li>
 * </ol>
 *
 * <p>Le contenu ne quitte le serveur qu'une fois le mot de passe vérifié. Une
 * documentation écrite dans le front et simplement masquée partirait dans le
 * paquet JavaScript : il suffirait de l'ouvrir pour tout lire.
 */
@RestController
@RequestMapping("/api/documentation")
class ControleurDocumentation {

    private static final int ESSAIS_PAR_MINUTE = 5;

    private final String motDePasseAttendu;

    private final Map<String, Essais> essaisParAdresse = new ConcurrentHashMap<>();

    ControleurDocumentation(@Value("${rdv.documentation.mot-de-passe:}") String motDePasseAttendu) {
        this.motDePasseAttendu = motDePasseAttendu;
    }

    @PostMapping
    ResponseEntity<Object> ouvrir(@RequestBody(required = false) Demande demande,
                                  HttpServletRequest requete) {
        String adresse = adresseDe(requete);

        if (tropDEssais(adresse)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Trop d'essais. Réessayez dans une minute."));
        }

        String saisi = demande == null || demande.motDePasse() == null ? "" : demande.motDePasse();

        // Un mot de passe non configuré FERME la porte. L'inverse
        // transformerait une variable oubliée au déploiement en accès libre,
        // sans aucun signe visible.
        if (motDePasseAttendu.isEmpty() || !comparaisonConstante(motDePasseAttendu, saisi)) {
            compterEssai(adresse);

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Mot de passe incorrect."));
        }

        essaisParAdresse.remove(adresse);

        return ResponseEntity.ok(Documentation.sections());
    }

    private static boolean comparaisonConstante(String attendu, String saisi) {
        return MessageDigest.isEqual(
                attendu.getBytes(StandardCharsets.UTF_8),
                saisi.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * L'adresse du visiteur.
     *
     * <p>Derrière le répartiteur de l'hébergeur, {@code getRemoteAddr()} rend
     * l'adresse du répartiteur — la même pour tout le monde, ce qui ferait
     * partager le compteur d'essais par tous les visiteurs. On lit donc
     * l'en-tête transmis, en ne gardant que la PREMIÈRE adresse : les
     * suivantes peuvent être ajoutées par le client lui-même.
     */
    private String adresseDe(HttpServletRequest requete) {
        String transmise = requete.getHeader("X-Forwarded-For");

        if (transmise != null && !transmise.isBlank()) {
            return transmise.split(",")[0].trim();
        }

        return requete.getRemoteAddr() == null ? "inconnue" : requete.getRemoteAddr();
    }

    private boolean tropDEssais(String adresse) {
        Essais essais = essaisParAdresse.get(adresse);

        return essais != null
                && essais.depuis().isAfter(Instant.now().minus(Duration.ofMinutes(1)))
                && essais.nombre() >= ESSAIS_PAR_MINUTE;
    }

    private void compterEssai(String adresse) {
        essaisParAdresse.compute(adresse, (cle, essais) -> {
            if (essais == null || essais.depuis().isBefore(Instant.now().minus(Duration.ofMinutes(1)))) {
                return new Essais(1, Instant.now());
            }

            return new Essais(essais.nombre() + 1, essais.depuis());
        });
    }

    record Demande(String motDePasse) {}

    private record Essais(int nombre, Instant depuis) {}
}
