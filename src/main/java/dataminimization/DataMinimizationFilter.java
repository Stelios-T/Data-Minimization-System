package dataminimization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.rdf4j.model.IRI;

/**
 * Decides whether a set of personal data submitted to a service satisfies the data
 * minimization principle: every provided datum must be needed, and every required
 * datum must be covered.
 */
public final class DataMinimizationFilter {

    private final KnowledgeGraph graph;

    public DataMinimizationFilter(KnowledgeGraph graph) {
        this.graph = graph;
    }

    /**
     * @param service  the service the data are submitted to
     * @param provided the personal data types offered; duplicates are collapsed
     * @throws IllegalArgumentException if the service is not in the knowledge graph
     */
    public Decision evaluate(IRI service, Collection<IRI> provided) {
        if (!graph.isService(service)) {
            throw new IllegalArgumentException("Unknown service: " + service);
        }
        Set<IRI> required = graph.required(service);
        Set<IRI> optional = graph.optional(service);
        Set<IRI> needed = new HashSet<>(required);
        needed.addAll(optional);

        List<DataAssessment> assessments = new ArrayList<>();
        Set<IRI> coveredRequired = new HashSet<>();
        for (IRI datum : new LinkedHashSet<>(provided)) {
            DataAssessment a = assess(datum, required, optional, needed);
            assessments.add(a);
            for (IRI c : a.covers()) {
                if (required.contains(c)) {
                    coveredRequired.add(c);
                }
            }
        }

        Set<IRI> missing = new LinkedHashSet<>(required);
        missing.removeAll(coveredRequired);
        boolean minimal = assessments.stream().allMatch(a -> a.status().isMinimal());
        return new Decision(service, required, optional, assessments, missing,
                Verdict.of(missing.isEmpty(), minimal));
    }

    private DataAssessment assess(IRI datum, Set<IRI> required, Set<IRI> optional, Set<IRI> needed) {
        if (!graph.isPersonalData(datum)) {
            return new DataAssessment(datum, Status.UNKNOWN, Set.of(), Set.of());
        }
        Set<IRI> covers = new LinkedHashSet<>(graph.covers(datum));
        covers.retainAll(needed);
        Set<IRI> exposes = new LinkedHashSet<>(graph.exposes(datum));
        exposes.removeAll(needed);

        Status status;
        if (required.contains(datum)) {
            status = Status.REQUIRED;
        } else if (optional.contains(datum)) {
            status = Status.OPTIONAL;
        } else if (covers.isEmpty()) {
            status = Status.EXCESSIVE;
        } else if (exposes.isEmpty()) {
            status = Status.SATISFIES;
        } else {
            status = Status.SUPERSET;
        }
        return new DataAssessment(datum, status, covers, exposes);
    }
}
