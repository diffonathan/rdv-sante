package ma.rdvsante.rendezvous.api;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ma.rdvsante.rendezvous.service.ErreursMetier;

/**
 * Traduit les erreurs métier en réponses HTTP, au format
 * <a href="https://www.rfc-editor.org/rfc/rfc9457">RFC 9457</a>
 * ({@code application/problem+json}).
 *
 * <p>Sans cette classe, un créneau déjà pris remonterait en {@code 500} : le
 * client ne saurait pas s'il doit réessayer, choisir un autre créneau ou
 * prévenir l'exploitant. Le code HTTP est la première documentation d'une API.
 */
@RestControllerAdvice
class GestionnaireErreurs {

    private static final String BASE = "https://rdv-sante.ma/erreurs/";

    @ExceptionHandler(ErreursMetier.Introuvable.class)
    ProblemDetail introuvable(ErreursMetier.Introuvable e) {
        return probleme(HttpStatus.NOT_FOUND, "Ressource introuvable", e.getMessage(), "introuvable");
    }

    /**
     * 409 et non 400 : la demande était valide au moment où elle est partie.
     * C'est l'état du serveur qui a changé entre-temps, et réessayer sur un
     * AUTRE créneau a du sens — ce que 400 ne suggérerait pas.
     */
    @ExceptionHandler(ErreursMetier.CreneauDejaPris.class)
    ProblemDetail creneauPris(ErreursMetier.CreneauDejaPris e) {
        ProblemDetail p = probleme(HttpStatus.CONFLICT, "Créneau déjà réservé",
                e.getMessage(), "creneau-deja-pris");
        p.setProperty("creneauId", e.creneauId());
        return p;
    }

    /**
     * 422 et non 400 : la syntaxe est correcte, c'est la règle métier qui
     * refuse. Un 400 dirait au client que sa requête est mal formée, et il
     * chercherait le défaut au mauvais endroit.
     */
    @ExceptionHandler(ErreursMetier.ReglementNonRespecte.class)
    ProblemDetail reglement(ErreursMetier.ReglementNonRespecte e) {
        return probleme(HttpStatus.UNPROCESSABLE_ENTITY, "Règle non respectée",
                e.getMessage(), "regle-metier");
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail etatInvalide(IllegalStateException e) {
        return probleme(HttpStatus.UNPROCESSABLE_ENTITY, "Transition impossible",
                e.getMessage(), "transition-impossible");
    }

    /** Erreurs de validation : chaque champ fautif est nommé. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException e) {
        Map<String, String> champs = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> champs.putIfAbsent(f.getField(), f.getDefaultMessage()));

        ProblemDetail p = probleme(HttpStatus.BAD_REQUEST, "Demande invalide",
                "Certains champs sont incorrects.", "validation");
        p.setProperty("champs", champs);
        return p;
    }

    private ProblemDetail probleme(HttpStatus statut, String titre, String detail, String type) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(statut, detail);
        p.setTitle(titre);
        p.setType(URI.create(BASE + type));
        return p;
    }
}
