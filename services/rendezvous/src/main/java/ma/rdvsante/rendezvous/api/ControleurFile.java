package ma.rdvsante.rendezvous.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;
import ma.rdvsante.rendezvous.domaine.EntreeFile;
import ma.rdvsante.rendezvous.service.ServiceFileAttente;

/** L'écran du secrétariat, et le flux que regarde le patient. */
@RestController
@RequestMapping("/api/cliniques/{cliniqueId}/file")
class ControleurFile {

    private final ServiceFileAttente file;
    private final AssembleurFile assembleur;
    private final DiffuseurFile diffuseur;

    ControleurFile(ServiceFileAttente file, AssembleurFile assembleur, DiffuseurFile diffuseur) {
        this.file = file;
        this.assembleur = assembleur;
        this.diffuseur = diffuseur;
    }

    /** L'état de la file, à la demande. */
    @GetMapping
    Vues.EtatFileVue etat(@PathVariable UUID cliniqueId) {
        return assembleur.etatCourant(cliniqueId);
    }

    /**
     * Le même état, poussé à chaque mouvement (décision D7).
     *
     * <p>Côté navigateur : {@code new EventSource('/api/cliniques/…/file/flux')}.
     * La reconnexion après coupure réseau est gérée par le navigateur.
     */
    @GetMapping("/flux")
    SseEmitter flux(@PathVariable UUID cliniqueId) {
        return diffuseur.abonner(cliniqueId);
    }

    /** Le secrétariat enregistre l'arrivée d'un patient. */
    @PostMapping("/arrivees")
    ResponseEntity<Vues.EntreeFileVue> arrivee(@PathVariable UUID cliniqueId,
                                               @Valid @RequestBody Demandes.Arrivee demande) {
        EntreeFile entree = file.enregistrerArrivee(demande.rendezVousId());
        int rang = file.rangDe(cliniqueId, demande.rendezVousId());
        return ResponseEntity.ok(Vues.EntreeFileVue.de(entree, rang));
    }

    /**
     * Appelle le patient suivant.
     *
     * @return {@code 204 No Content} quand la file est vide — ce n'est pas une
     *         erreur, c'est une salle d'attente vide.
     */
    @PostMapping("/suivant")
    ResponseEntity<Vues.EntreeFileVue> suivant(@PathVariable UUID cliniqueId) {
        EntreeFile appele = file.appelerSuivant(cliniqueId);
        return appele == null
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(Vues.EntreeFileVue.de(appele, 0));
    }

    @PostMapping("/{entreeId}/consultation")
    Vues.EntreeFileVue faireEntrer(@PathVariable UUID entreeId) {
        return Vues.EntreeFileVue.de(file.faireEntrer(entreeId), 0);
    }

    @PostMapping("/{entreeId}/fin")
    Vues.EntreeFileVue terminer(@PathVariable UUID entreeId) {
        return Vues.EntreeFileVue.de(file.terminer(entreeId), 0);
    }
}
