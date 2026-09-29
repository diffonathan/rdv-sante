package ma.rdvsante.notifications.depot;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import ma.rdvsante.notifications.domaine.MessageTraite;

public interface DepotMessageTraite extends JpaRepository<MessageTraite, UUID> {

    /**
     * Pose la marque « déjà traité », et dit si elle est nouvelle.
     *
     * <p><strong>Pourquoi une requête native plutôt que {@code save()}.</strong>
     * L'identifiant de cette entité est <em>assigné</em>, pas généré. Spring
     * Data considère alors l'objet comme existant et appelle {@code merge()} :
     * Hibernate fait un SELECT puis un UPDATE. La clé primaire n'est jamais
     * violée, aucune exception n'est levée — et la garde contre les doublons
     * ne garde rien du tout. Le défaut est invisible en lecture de code, et il
     * ne se voit qu'en rejouant un topic entier.
     *
     * <p>{@code ON CONFLICT DO NOTHING} règle la question sans piloter le
     * contrôle de flux par une exception : la base répond 1 (inséré) ou 0
     * (déjà là), et l'opération reste atomique face à deux consommateurs
     * concurrents.
     *
     * @return 1 si l'événement est nouveau, 0 s'il avait déjà été traité
     */
    @Modifying
    @Query(value = """
            INSERT INTO message_traite (evenement_id, topic, traite_le)
            VALUES (:evenementId, :topic, now())
            ON CONFLICT (evenement_id) DO NOTHING
            """, nativeQuery = true)
    int marquerSiNouveau(UUID evenementId, String topic);
}
