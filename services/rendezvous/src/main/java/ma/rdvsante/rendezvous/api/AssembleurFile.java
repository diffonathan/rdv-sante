package ma.rdvsante.rendezvous.api;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import ma.rdvsante.rendezvous.domaine.EntreeFile;
import ma.rdvsante.rendezvous.service.ServiceFileAttente;

/**
 * Construit l'instantané de la file — le même objet pour la requête REST et
 * pour le flux SSE.
 *
 * <p>Deux chemins de construction auraient fini par diverger : l'écran affiché
 * au chargement ne serait plus tout à fait celui que la mise à jour temps réel
 * remplace.
 */
@Component
public class AssembleurFile {

    private final ServiceFileAttente service;
    private final Clock horloge;

    public AssembleurFile(ServiceFileAttente service, Clock horloge) {
        this.service = service;
        this.horloge = horloge;
    }

    public Vues.EtatFileVue etatCourant(UUID cliniqueId) {
        List<Vues.EntreeFileVue> attente = numeroter(service.fileEnAttente(cliniqueId));
        List<Vues.EntreeFileVue> appeles = service.dejaAppeles(cliniqueId).stream()
                .map(e -> Vues.EntreeFileVue.de(e, 0))
                .toList();
        return new Vues.EtatFileVue(cliniqueId, attente, appeles, attente.size(),
                horloge.instant());
    }

    /** Le rang naît ici, de l'ordre de la liste — il n'est nulle part stocké. */
    private List<Vues.EntreeFileVue> numeroter(List<EntreeFile> file) {
        return IntStream.range(0, file.size())
                .mapToObj(i -> Vues.EntreeFileVue.de(file.get(i), i + 1))
                .toList();
    }
}
