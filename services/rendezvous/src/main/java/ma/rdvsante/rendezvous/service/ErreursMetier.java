package ma.rdvsante.rendezvous.service;

import java.util.UUID;

/**
 * Les trois familles d'erreurs métier du service.
 *
 * <p>Regroupées ici parce qu'elles n'ont de sens qu'ensemble : elles décrivent
 * les seules façons dont une demande légitime peut être refusée. Chacune se
 * traduit en un code HTTP précis dans {@code GestionnaireErreurs} — 404, 409 ou
 * 422 — et jamais en 500 : un créneau déjà pris n'est pas une panne du serveur.
 */
public final class ErreursMetier {

    private ErreursMetier() {
    }

    /** Ce qui est demandé n'existe pas. → 404 */
    public static class Introuvable extends RuntimeException {
        public Introuvable(String quoi, UUID id) {
            super(quoi + " " + id + " : introuvable.");
        }
    }

    /**
     * Le créneau vient d'être pris par quelqu'un d'autre. → 409
     *
     * <p>Cas de course réel, pas théorique : deux patients cliquent sur le
     * même créneau à la même seconde. L'index unique partiel en base tranche,
     * le perdant reçoit ceci.
     */
    public static class CreneauDejaPris extends RuntimeException {
        private final UUID creneauId;

        public CreneauDejaPris(UUID creneauId) {
            super("Le créneau " + creneauId + " vient d'être réservé par quelqu'un d'autre.");
            this.creneauId = creneauId;
        }

        public UUID creneauId() {
            return creneauId;
        }
    }

    /** La demande est comprise mais contraire à une règle. → 422 */
    public static class ReglementNonRespecte extends RuntimeException {
        public ReglementNonRespecte(String message) {
            super(message);
        }
    }
}
