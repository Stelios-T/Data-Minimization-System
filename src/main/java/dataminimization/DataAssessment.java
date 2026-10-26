package dataminimization;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.eclipse.rdf4j.model.IRI;

/**
 * Assessment of a single provided datum.
 *
 * @param datum        the provided personal data type
 * @param status       how it relates to the service's needs
 * @param covers       the needed data this datum provides (directly, as a part, by derivation or as a specialisation)
 * @param exposes      data this datum discloses that the service does not need
 * @param cost         disclosure cost of the datum (its sensitivity plus that of everything it exposes); 0 when unknown
 * @param alternatives for a {@link Status#SUPERSET} datum, cheaper sets of data that cover the same needs, cheapest first
 * @param because      the active conditions that made this datum required; empty otherwise
 */
public record DataAssessment(IRI datum, Status status, Set<IRI> covers, Set<IRI> exposes, int cost,
                             List<SetCover.Cover> alternatives, Set<IRI> because) {

    public DataAssessment {
        covers = Iris.sorted(covers);
        exposes = Iris.sorted(exposes);
        alternatives = List.copyOf(alternatives);
        because = Iris.sorted(because);
    }

    /** For a {@link Status#SUPERSET} datum, the needed data the service should request instead. */
    @JsonProperty("substitutes")
    public Set<IRI> substitutes() {
        return status == Status.SUPERSET ? covers : Set.of();
    }
}
