package ma.rdvsante.rendezvous.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ma.rdvsante.rendezvous.evenement.FileModifiee;

/**
 * Pousse l'état de la file vers les navigateurs connectés (décision D7 : SSE).
 *
 * <p>Les abonnés sont rangés par clinique : l'écran d'une clinique ne reçoit
 * que ses propres mouvements. La liste est une {@link CopyOnWriteArrayList}
 * parce qu'on la parcourt bien plus souvent qu'on ne la modifie, et qu'un
 * navigateur peut se déconnecter pendant qu'on itère dessus.
 */
@Component
public class DiffuseurFile {

    private static final Logger log = LoggerFactory.getLogger(DiffuseurFile.class);

    /** Zéro = pas d'expiration. Le navigateur se reconnecte seul via
        {@code EventSource}, mais une coupure toutes les 30 s ferait clignoter
        l'écran de la salle d'attente. */
    private static final long SANS_EXPIRATION = 0L;

    private final Map<UUID, List<SseEmitter>> abonnes = new ConcurrentHashMap<>();
    private final AssembleurFile assembleur;

    public DiffuseurFile(AssembleurFile assembleur) {
        this.assembleur = assembleur;
    }

    /** Ouvre un flux pour une clinique et envoie immédiatement l'état courant. */
    public SseEmitter abonner(UUID cliniqueId) {
        SseEmitter flux = new SseEmitter(SANS_EXPIRATION);
        abonnes.computeIfAbsent(cliniqueId, id -> new CopyOnWriteArrayList<>()).add(flux);

        Runnable retirer = () -> retirer(cliniqueId, flux);
        flux.onCompletion(retirer);
        flux.onTimeout(retirer);
        flux.onError(e -> retirer.run());

        // Sans ce premier envoi, l'écran resterait vide jusqu'au prochain
        // mouvement de la file — c'est-à-dire potentiellement des minutes.
        envoyer(flux, assembleur.etatCourant(cliniqueId));
        return flux;
    }

    /**
     * Diffuse après validation de la transaction.
     *
     * <p>{@link TransactionPhase#AFTER_COMMIT} est le point important :
     * diffuser pendant la transaction afficherait aux secrétaires un patient
     * qu'un rollback ferait disparaître une seconde plus tard.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void surFileModifiee(FileModifiee evenement) {
        List<SseEmitter> flux = abonnes.get(evenement.cliniqueId());
        if (flux == null || flux.isEmpty()) {
            return;
        }
        Vues.EtatFileVue etat = assembleur.etatCourant(evenement.cliniqueId());
        flux.forEach(f -> envoyer(f, etat));
    }

    private void envoyer(SseEmitter flux, Vues.EtatFileVue etat) {
        try {
            flux.send(SseEmitter.event().name("file").data(etat));
        } catch (IOException | IllegalStateException e) {
            // Onglet fermé : ce n'est pas une panne, c'est la vie d'un flux.
            flux.completeWithError(e);
        }
    }

    private void retirer(UUID cliniqueId, SseEmitter flux) {
        List<SseEmitter> liste = abonnes.get(cliniqueId);
        if (liste == null) {
            return;
        }
        liste.remove(flux);
        if (liste.isEmpty()) {
            abonnes.remove(cliniqueId);
        }
        log.debug("Flux fermé pour la clinique {} ({} abonné(s) restant(s))",
                cliniqueId, liste.size());
    }

    /** Nombre d'écrans connectés — utile en test et pour les métriques. */
    public int nombreAbonnes(UUID cliniqueId) {
        List<SseEmitter> liste = abonnes.get(cliniqueId);
        return liste == null ? 0 : liste.size();
    }
}
