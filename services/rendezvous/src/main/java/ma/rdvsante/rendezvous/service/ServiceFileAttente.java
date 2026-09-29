package ma.rdvsante.rendezvous.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ma.rdvsante.rendezvous.depot.DepotEntreeFile;
import ma.rdvsante.rendezvous.depot.DepotRendezVous;
import ma.rdvsante.rendezvous.domaine.Clinique;
import ma.rdvsante.rendezvous.domaine.EntreeFile;
import ma.rdvsante.rendezvous.domaine.EtatFile;
import ma.rdvsante.rendezvous.domaine.RendezVous;
import ma.rdvsante.rendezvous.domaine.StatutRendezVous;
import ma.rdvsante.rendezvous.evenement.Evenements;
import ma.rdvsante.rendezvous.evenement.FileModifiee;

/**
 * La file d'attente du jour : arrivées, appels, fins de consultation.
 *
 * <p>Le rang d'un patient n'est jamais stocké — il se calcule à la lecture, à
 * partir de l'ordre d'arrivée. Voir la migration V2 pour le raisonnement.
 */
@Service
public class ServiceFileAttente {

    private final DepotEntreeFile depotFile;
    private final DepotRendezVous depotRendezVous;
    private final ServiceOutbox outbox;
    private final ApplicationEventPublisher diffusion;
    private final Clock horloge;

    public ServiceFileAttente(DepotEntreeFile depotFile, DepotRendezVous depotRendezVous,
                              ServiceOutbox outbox, ApplicationEventPublisher diffusion,
                              Clock horloge) {
        this.depotFile = depotFile;
        this.depotRendezVous = depotRendezVous;
        this.outbox = outbox;
        this.diffusion = diffusion;
        this.horloge = horloge;
    }

    /** Le secrétariat enregistre l'arrivée d'un patient. */
    @Transactional
    public EntreeFile enregistrerArrivee(UUID rendezVousId) {
        RendezVous rdv = depotRendezVous.parIdComplet(rendezVousId)
                .orElseThrow(() -> new ErreursMetier.Introuvable("Rendez-vous", rendezVousId));

        if (rdv.getStatut() != StatutRendezVous.CONFIRME) {
            throw new ErreursMetier.ReglementNonRespecte(
                    "Un rendez-vous « " + rdv.getStatut() + " » ne peut pas entrer dans la file.");
        }
        if (depotFile.existsByRendezVousId(rendezVousId)) {
            throw new ErreursMetier.ReglementNonRespecte(
                    "Ce patient est déjà enregistré dans la file.");
        }

        Clinique clinique = rdv.getCreneau().getPraticien().getClinique();
        EntreeFile entree = depotFile.saveAndFlush(new EntreeFile(
                UUID.randomUUID(), clinique, rdv, horloge.instant()));

        // Les écrans connectés doivent voir la nouvelle arrivée. L'événement
        // n'est délivré qu'après validation de la transaction (voir
        // DiffuseurFile) : pousser avant, c'est risquer d'afficher un patient
        // que le rollback vient d'effacer.
        diffusion.publishEvent(new FileModifiee(clinique.getId()));
        return entree;
    }

    /**
     * Appelle le patient suivant.
     *
     * <p>La ligne est verrouillée par le dépôt le temps de la transaction :
     * deux secrétaires qui cliquent ensemble n'appellent pas le même patient.
     *
     * @return l'entrée appelée, ou {@code null} si la file est vide
     */
    @Transactional
    public EntreeFile appelerSuivant(UUID cliniqueId) {
        EntreeFile entree = depotFile.prochainAAppeler(cliniqueId, EtatFile.EN_ATTENTE)
                .orElse(null);
        if (entree == null) {
            return null;
        }

        Instant maintenant = horloge.instant();
        entree.appeler(maintenant);
        depotFile.save(entree);

        // Compté APRÈS l'appel : c'est le nombre de personnes encore devant
        // pour celles qui restent, donc l'information utile à la notification.
        int restants = depotFile.parEtat(cliniqueId, EtatFile.EN_ATTENTE).size();

        RendezVous rdv = entree.getRendezVous();
        Clinique clinique = entree.getClinique();
        outbox.deposer(Evenements.TOPIC_FILE, "EntreeFile", entree.getId(),
                new Evenements.FileAvancee(
                        clinique.getId(), clinique.getNom(),
                        rdv.getId(), rdv.getPatientId(), rdv.getPatientNom(),
                        rdv.getPatientTelephone(), maintenant, restants));

        diffusion.publishEvent(new FileModifiee(cliniqueId));

        // Rechargée complète : l'appelant est hors transaction quand il
        // construit sa vue (voir DepotEntreeFile#parIdComplet).
        return depotFile.parIdComplet(entree.getId()).orElseThrow();
    }

    @Transactional
    public EntreeFile faireEntrer(UUID entreeId) {
        EntreeFile entree = depotFile.findById(entreeId)
                .orElseThrow(() -> new ErreursMetier.Introuvable("Entrée de file", entreeId));
        entree.entrerEnConsultation();
        depotFile.save(entree);
        diffusion.publishEvent(new FileModifiee(entree.getClinique().getId()));
        return depotFile.parIdComplet(entreeId).orElseThrow();
    }

    @Transactional
    public EntreeFile terminer(UUID entreeId) {
        EntreeFile entree = depotFile.findById(entreeId)
                .orElseThrow(() -> new ErreursMetier.Introuvable("Entrée de file", entreeId));
        entree.terminer(horloge.instant());
        depotFile.save(entree);
        diffusion.publishEvent(new FileModifiee(entree.getClinique().getId()));
        return depotFile.parIdComplet(entreeId).orElseThrow();
    }

    /** La file en attente, dans l'ordre. Le rang est l'indice + 1. */
    @Transactional(readOnly = true)
    public List<EntreeFile> fileEnAttente(UUID cliniqueId) {
        return depotFile.parEtat(cliniqueId, EtatFile.EN_ATTENTE);
    }

    @Transactional(readOnly = true)
    public List<EntreeFile> dejaAppeles(UUID cliniqueId) {
        return depotFile.parEtat(cliniqueId, EtatFile.APPELE);
    }

    /**
     * Le rang d'un patient dans la file, à partir de 1.
     *
     * @return 0 si le patient n'est pas (ou plus) en attente
     */
    @Transactional(readOnly = true)
    public int rangDe(UUID cliniqueId, UUID rendezVousId) {
        List<EntreeFile> file = fileEnAttente(cliniqueId);
        for (int i = 0; i < file.size(); i++) {
            if (file.get(i).getRendezVous().getId().equals(rendezVousId)) {
                return i + 1;
            }
        }
        return 0;
    }
}
