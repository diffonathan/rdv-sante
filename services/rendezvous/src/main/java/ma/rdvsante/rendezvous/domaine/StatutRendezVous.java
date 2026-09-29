package ma.rdvsante.rendezvous.domaine;

/**
 * Les quatre états d'un rendez-vous.
 *
 * <p>Les noms sont repris tels quels dans la contrainte {@code CHECK} de la
 * table : ajouter une valeur ici sans migration fera échouer l'insertion en
 * base — ce qui est voulu. Un enum et une contrainte qui divergent, c'est une
 * donnée invalide qui passe.
 */
public enum StatutRendezVous {

    /** Créneau pris, patient attendu. */
    CONFIRME,

    /** Annulé par le patient ou la clinique. Le créneau redevient libre. */
    ANNULE,

    /** Le patient est venu et a été reçu. */
    HONORE,

    /** Le patient n'est pas venu et n'a pas prévenu. */
    ABSENT;

    /** Seul un rendez-vous confirmé occupe encore son créneau. */
    public boolean occupeLeCreneau() {
        return this != ANNULE;
    }
}
