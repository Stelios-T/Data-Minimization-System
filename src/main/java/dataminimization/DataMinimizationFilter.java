package dataminimization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.rdf4j.model.IRI;

/**
 * Decides whether a set of personal data submitted to a service satisfies the data
 * minimization principle: every provided datum must be needed, and every required
 * datum must be covered.
 */
public final class DataMinimizationFilter {

    /** How many alternative covers to list for a superset datum. */
    static final int ALTERNATIVES = 3;

    private final KnowledgeGraph graph;

    public DataMinimizationFilter(KnowledgeGraph graph) {
        this.graph = graph;
    }

    /** Evaluates against all purposes of the service with no condition active. */
    public Decision evaluate(IRI service, Collection<IRI> provided) {
        return evaluate(service, null, Set.of(), provided);
    }

    /**
     * @param service    the service the data are submitted to
     * @param purpose    the purpose to evaluate for, or {@code null} for all purposes of the service together
     * @param conditions the conditions that currently hold
     * @param provided   the personal data types offered; duplicates are collapsed
     * @throws IllegalArgumentException for an unknown service, purpose or condition
     */
    public Decision evaluate(IRI service, IRI purpose, Set<IRI> conditions, Collection<IRI> provided) {
        Requirements req = graph.requirements(service, purpose, conditions);
        Set<IRI> required = req.required();
        Set<IRI> needed = req.needed();

        Set<IRI> learned = learnedFrom(needed);

        List<DataAssessment> assessments = new ArrayList<>();
        Set<IRI> coveredRequired = new HashSet<>();
        int offeredCost = 0;
        for (IRI datum : new LinkedHashSet<>(provided)) {
            DataAssessment a = assess(datum, req, needed, learned);
            assessments.add(a);
            offeredCost += a.cost();
            for (IRI c : a.covers()) {
                if (required.contains(c)) {
                    coveredRequired.add(c);
                }
            }
        }

        Set<IRI> missing = new LinkedHashSet<>(required);
        missing.removeAll(coveredRequired);
        boolean minimal = assessments.stream().allMatch(a -> a.status().isMinimal());

        List<SetCover.Cover> cheapest = cheapestCovers(required, null, 1);
        SetCover.Cover minimalDisclosure = cheapest.isEmpty() ? new SetCover.Cover(Set.of(), 0) : cheapest.get(0);

        Set<IRI> purposes = purpose == null ? graph.purposes(service) : Set.of(purpose);
        return new Decision(service, purpose, purposes, conditions, req, assessments, missing,
                Verdict.of(missing.isEmpty(), minimal), offeredCost, minimalDisclosure.chosen(), minimalDisclosure.cost());
    }

    /** The concrete data a service learns from the data it asks for: the needed data and everything they expose. */
    private Set<IRI> learnedFrom(Set<IRI> needed) {
        Set<IRI> learned = new HashSet<>(needed);
        for (IRI n : needed) {
            learned.addAll(graph.exposes(n));
        }
        return learned;
    }

    private DataAssessment assess(IRI datum, Requirements req, Set<IRI> needed, Set<IRI> learned) {
        if (!graph.isPersonalData(datum)) {
            return new DataAssessment(datum, Status.UNKNOWN, Set.of(), Set.of(), 0, List.of(), Set.of());
        }
        Set<IRI> covers = new LinkedHashSet<>(graph.covers(datum));
        covers.retainAll(needed);
        Set<IRI> exposes = new LinkedHashSet<>(graph.exposes(datum));
        exposes.removeAll(learned);
        int cost = graph.disclosureCost(datum);
        Set<IRI> because = req.conditional().getOrDefault(datum, Set.of());

        Status status;
        if (req.required().contains(datum)) {
            status = Status.REQUIRED;
        } else if (req.optional().contains(datum)) {
            status = Status.OPTIONAL;
        } else if (covers.isEmpty()) {
            status = Status.EXCESSIVE;
        } else if (!exposes.isEmpty()) {
            status = Status.SUPERSET;
        } else if (accounted(datum, needed, learned, new HashSet<>())) {
            status = Status.SATISFIES;
        } else {
            status = Status.SUPERSET;
        }
        List<SetCover.Cover> alternatives = status == Status.SUPERSET ? cheapestCovers(covers, datum, ALTERNATIVES) : List.of();
        return new DataAssessment(datum, status, covers, exposes, cost, alternatives, because);
    }

    /**
     * Whether a datum carries no information beyond what the service asked for: the service
     * learns it anyway, or it is a specialisation of something explicitly asked for (an Email
     * when a Contact is wanted), or it is a pure composite whose parts are all accounted for
     * (an Account when its number and balance are wanted). A datum from which a needed value is
     * merely derived (an ExactAge when IsAdult is wanted) is not accounted for: it says more.
     */
    private boolean accounted(IRI datum, Set<IRI> needed, Set<IRI> learned, Set<IRI> visiting) {
        if (learned.contains(datum)) {
            return true;
        }
        if (!visiting.add(datum)) {
            return false;
        }
        List<IRI> parts = new ArrayList<>();
        for (KnowledgeGraph.Edge e : graph.edges(datum)) {
            switch (e.relation()) {
                case PRESENT_AS -> {
                    if (needed.contains(e.target()) || generalisesToNeeded(e.target(), needed, new HashSet<>())) {
                        return true;
                    }
                }
                case EXTRACT -> parts.add(e.target());
                default -> { }
            }
        }
        return !parts.isEmpty() && parts.stream().allMatch(p -> accounted(p, needed, learned, visiting));
    }

    private boolean generalisesToNeeded(IRI datum, Set<IRI> needed, Set<IRI> seen) {
        if (!seen.add(datum)) {
            return false;
        }
        for (KnowledgeGraph.Edge e : graph.edges(datum)) {
            if (e.relation() == KnowledgeGraph.Relation.PRESENT_AS
                    && (needed.contains(e.target()) || generalisesToNeeded(e.target(), needed, seen))) {
                return true;
            }
        }
        return false;
    }

    /** Cheapest sets of known data types, other than {@code exclude}, that together cover {@code needed}. */
    private List<SetCover.Cover> cheapestCovers(Set<IRI> needed, IRI exclude, int k) {
        Map<IRI, Set<IRI>> candidates = new LinkedHashMap<>();
        for (IRI n : needed) {
            for (IRI provider : graph.providersOf(n)) {
                if (provider.equals(exclude)) {
                    continue;
                }
                candidates.computeIfAbsent(provider, p -> {
                    Set<IRI> c = new HashSet<>(graph.covers(p));
                    c.retainAll(needed);
                    return c;
                });
            }
        }
        return SetCover.cheapest(needed, candidates, graph::disclosureCost, k);
    }
}
