package ma.rdvsante.rendezvous.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import ma.rdvsante.rendezvous.domaine.Clinique;
import ma.rdvsante.rendezvous.domaine.Creneau;
import ma.rdvsante.rendezvous.domaine.EntreeFile;
import ma.rdvsante.rendezvous.domaine.Praticien;
import ma.rdvsante.rendezvous.domaine.RendezVous;

/**
 * Ce que le service renvoie.
 *
 * <p>Des vues, pas les entités : exposer une entité JPA directement publie le
 * schéma de la base, déclenche des chargements paresseux pendant la
 * sérialisation, et fige la structure interne dans le contrat d'API. Chaque
 * vue est construite à partir d'un objet déjà chargé.
 */
public final class Vues {

    private Vues() {
    }

    public record CliniqueVue(UUID id, String nom, String ville, String adresse, String telephone) {
        public static CliniqueVue de(Clinique c) {
            return new CliniqueVue(c.getId(), c.getNom(), c.getVille(), c.getAdresse(), c.getTelephone());
        }
    }

    public record PraticienVue(UUID id, String nomComplet, String specialite,
                               UUID cliniqueId, String cliniqueNom) {
        public static PraticienVue de(Praticien p) {
            return new PraticienVue(p.getId(), p.nomComplet(), p.getSpecialite(),
                    p.getClinique().getId(), p.getClinique().getNom());
        }
    }

    public record CreneauVue(UUID id, Instant debut, Instant fin, long dureeMinutes,
                             UUID praticienId, String praticienNom) {
        public static CreneauVue de(Creneau c) {
            return new CreneauVue(c.getId(), c.getDebut(), c.getFin(),
                    Duration.between(c.getDebut(), c.getFin()).toMinutes(),
                    c.getPraticien().getId(), c.getPraticien().nomComplet());
        }
    }

    public record RendezVousVue(UUID id, String statut, Instant debut, Instant fin,
                                UUID patientId, String patientNom,
                                String praticienNom, String specialite,
                                String cliniqueNom, String cliniqueVille,
                                String motifAnnulation) {
        public static RendezVousVue de(RendezVous r) {
            Creneau c = r.getCreneau();
            Praticien p = c.getPraticien();
            return new RendezVousVue(
                    r.getId(), r.getStatut().name(), c.getDebut(), c.getFin(),
                    r.getPatientId(), r.getPatientNom(),
                    p.nomComplet(), p.getSpecialite(),
                    p.getClinique().getNom(), p.getClinique().getVille(),
                    r.getMotifAnnulation());
        }
    }

    /** @param rang position dans la file, à partir de 1 ; 0 si déjà appelé. */
    public record EntreeFileVue(UUID id, int rang, UUID rendezVousId,
                                String patientNom, String praticienNom,
                                Instant arriveLe, Instant appeleLe, String etat) {
        public static EntreeFileVue de(EntreeFile e, int rang) {
            RendezVous r = e.getRendezVous();
            return new EntreeFileVue(e.getId(), rang, r.getId(), r.getPatientNom(),
                    r.getCreneau().getPraticien().nomComplet(),
                    e.getArriveLe(), e.getAppeleLe(), e.getEtat().name());
        }
    }

    /** L'état complet de la file, tel qu'affiché et tel que poussé en SSE. */
    public record EtatFileVue(UUID cliniqueId, List<EntreeFileVue> enAttente,
                              List<EntreeFileVue> appeles, int nombreEnAttente,
                              Instant genereLe) {
    }
}
