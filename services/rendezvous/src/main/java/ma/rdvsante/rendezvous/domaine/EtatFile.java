package ma.rdvsante.rendezvous.domaine;

/** Où en est un patient dans la file d'attente du jour. */
public enum EtatFile {

    /** Arrivé, enregistré par le secrétariat, attend son tour. */
    EN_ATTENTE,

    /** Son nom vient d'être appelé. */
    APPELE,

    /** Il est entré chez le praticien. */
    EN_CONSULTATION,

    /** La consultation est finie. */
    TERMINE;

    /** Seuls ceux-là comptent dans le rang affiché au patient. */
    public boolean compteDansLaFile() {
        return this == EN_ATTENTE;
    }
}
