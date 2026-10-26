package dataminimization.eval;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import dataminimization.KnowledgeGraph;
import dataminimization.Requirements;
import dataminimization.Verdict;
import org.eclipse.rdf4j.model.IRI;

/**
 * The naive approach a form validator would take without a knowledge graph: a datum is
 * accepted only when the service names it, everything else is rejected, and a required
 * datum counts as missing unless it is offered literally.
 */
public final class ExactMatchBaseline {

    /** Accept/reject per datum, the literal missing set, and the resulting verdict. */
    public record Result(Map<IRI, Boolean> accepted, Set<IRI> missing, Verdict verdict) {
    }

    private final KnowledgeGraph graph;

    public ExactMatchBaseline(KnowledgeGraph graph) {
        this.graph = graph;
    }

    public Result evaluate(IRI service, IRI purpose, Set<IRI> conditions, Collection<IRI> provided) {
        Requirements req = graph.requirements(service, purpose, conditions);
        Map<IRI, Boolean> accepted = new LinkedHashMap<>();
        for (IRI d : new LinkedHashSet<>(provided)) {
            accepted.put(d, req.required().contains(d) || req.optional().contains(d));
        }
        Set<IRI> missing = new LinkedHashSet<>(req.required());
        missing.removeAll(provided);
        boolean minimal = accepted.values().stream().allMatch(Boolean::booleanValue);
        return new Result(accepted, missing, Verdict.of(missing.isEmpty(), minimal));
    }
}
