package dataminimization;

import java.util.Set;

import org.eclipse.rdf4j.model.IRI;

/**
 * Assessment of a single provided datum.
 *
 * @param datum   the provided personal data type
 * @param status  how it relates to the service's needs
 * @param covers  the needed data this datum provides (directly, as a part, by derivation or as a specialisation)
 * @param exposes data this datum discloses that the service does not need
 */
public record DataAssessment(IRI datum, Status status, Set<IRI> covers, Set<IRI> exposes) {

    public DataAssessment {
        covers = Set.copyOf(covers);
        exposes = Set.copyOf(exposes);
    }

    /** For a {@link Status#SUPERSET} datum, the narrower data the service should request instead. */
    public Set<IRI> substitutes() {
        return status == Status.SUPERSET ? covers : Set.of();
    }
}
