package dataminimization;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.eclipse.rdf4j.model.IRI;

/**
 * Result of checking a set of provided data against a service's needs.
 *
 * @param service           the service the data were submitted to
 * @param purpose           the purpose evaluated, or {@code null} when all purposes of the service were taken together
 * @param purposes          the purposes that were taken into account
 * @param conditions        the conditions that were active
 * @param requirements      what the service needs under that purpose and those conditions
 * @param assessments       one entry per distinct provided datum, in submission order
 * @param missing           required data that none of the provided data cover
 * @param verdict           overall outcome
 * @param offeredCost       total disclosure cost of the provided data
 * @param minimalDisclosure the cheapest set of known data types that covers every required datum
 * @param minimalCost       disclosure cost of that set
 */
public record Decision(IRI service, IRI purpose, Set<IRI> purposes, Set<IRI> conditions, Requirements requirements,
                       List<DataAssessment> assessments, Set<IRI> missing, Verdict verdict,
                       int offeredCost, Set<IRI> minimalDisclosure, int minimalCost) {

    public Decision {
        purposes = Iris.sorted(purposes);
        conditions = Iris.sorted(conditions);
        assessments = List.copyOf(assessments);
        missing = Iris.sorted(missing);
        minimalDisclosure = Iris.sorted(minimalDisclosure);
    }

    @JsonIgnore
    public Set<IRI> required() {
        return requirements.required();
    }

    @JsonIgnore
    public Set<IRI> optional() {
        return requirements.optional();
    }

    @JsonProperty("compliant")
    public boolean isCompliant() {
        return verdict == Verdict.COMPLIANT;
    }

    /** Provided data that should be rejected or replaced. */
    @JsonIgnore
    public List<DataAssessment> excessive() {
        return assessments.stream().filter(a -> !a.status().isMinimal()).toList();
    }

    /** Human-readable report of the decision; see {@link TextReports#decision}. */
    public String report() {
        return TextReports.decision(this);
    }
}
