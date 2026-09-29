package ma.rdvsante.patients.api;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ma.rdvsante.patients.service.ServicePatient;

/** Mêmes codes et même format (RFC 9457) que les autres services. */
@RestControllerAdvice
class GestionnaireErreurs {

    private static final String BASE = "https://rdv-sante.ma/erreurs/";

    @ExceptionHandler(ServicePatient.Introuvable.class)
    ProblemDetail introuvable(ServicePatient.Introuvable e) {
        return probleme(HttpStatus.NOT_FOUND, "Patient introuvable", e.getMessage(), "introuvable");
    }

    @ExceptionHandler(ServicePatient.ReglementNonRespecte.class)
    ProblemDetail reglement(ServicePatient.ReglementNonRespecte e) {
        return probleme(HttpStatus.UNPROCESSABLE_ENTITY, "Règle non respectée",
                e.getMessage(), "regle-metier");
    }

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
