package dataminimization;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.rdf4j.model.IRI;

/**
 * Computes, on the user's side, the minimal disclosure that satisfies a service: for each
 * requirement, which held datum to use and whether to send it, extract a part of it, or
 * derive the requirement from it locally. Reachability comes from the SPARQL paths in
 * {@link KnowledgeGraph#covers}; the explanation path is a breadth-first search over
 * {@link KnowledgeGraph#edges} that mirrors the same rule (decompose or derive first,
 * generalise last).
 */
public final class DisclosurePlanner {

    private final KnowledgeGraph graph;

    public DisclosurePlanner(KnowledgeGraph graph) {
        this.graph = graph;
    }

    /**
     * @param service         the service
     * @param purpose         one purpose, or {@code null} for all
     * @param conditions      active conditions
     * @param held            personal data types the user agent holds
     * @param includeOptional also plan steps for optional data when they can be provided without extra disclosure
     */
    public DisclosurePlan plan(IRI service, IRI purpose, Set<IRI> conditions, Set<IRI> held, boolean includeOptional) {
        Requirements req = graph.requirements(service, purpose, conditions);
        List<IRI> sortedHeld = held.stream().filter(graph::isPersonalData)
                .sorted(Comparator.comparing(IRI::getLocalName)).toList();
        Set<IRI> unknown = new LinkedHashSet<>(held);
        sortedHeld.forEach(unknown::remove);

        List<DisclosurePlan.Step> steps = new ArrayList<>();
        Set<IRI> unsatisfiable = new LinkedHashSet<>();
        List<IRI> targets = new ArrayList<>(req.required().stream().sorted(Comparator.comparing(IRI::getLocalName)).toList());
        if (includeOptional) {
            targets.addAll(req.optional().stream().sorted(Comparator.comparing(IRI::getLocalName)).toList());
        }
        for (IRI target : targets) {
            boolean optional = !req.required().contains(target);
            DisclosurePlan.Step best = null;
            for (IRI h : sortedHeld) {
                if (!graph.covers(h).contains(target)) {
                    continue;
                }
                Optional<List<IRI>> found = shortestPath(h, target);
                if (found.isEmpty()) {
                    continue;
                }
                List<IRI> path = found.get();
                DisclosurePlan.Step step = new DisclosurePlan.Step(kindOf(path), h, target, path, optional);
                if (best == null || path.size() < best.path().size()) {
                    best = step;
                }
            }
            if (best != null) {
                steps.add(best);
            } else if (!optional) {
                unsatisfiable.add(target);
            }
        }

        int planCost = steps.stream().mapToInt(s -> graph.disclosureCost(s.to())).sum();
        int naiveCost = sortedHeld.stream().mapToInt(graph::disclosureCost).sum();
        return new DisclosurePlan(service, purpose, conditions, req, held, steps, unsatisfiable, unknown, planCost, naiveCost);
    }

    /**
     * Kind of the step. DERIVE when any hop computes a new value (the result is not a verbatim
     * part of the held datum), else EXTRACT when a part is taken out, else PRESENT_AS when the
     * path only generalises.
     */
    private DisclosurePlan.Kind kindOf(List<IRI> path) {
        if (path.size() == 1) {
            return DisclosurePlan.Kind.DISCLOSE;
        }
        DisclosurePlan.Kind kind = DisclosurePlan.Kind.PRESENT_AS;
        for (int i = 0; i + 1 < path.size(); i++) {
            KnowledgeGraph.Relation r = relationBetween(path.get(i), path.get(i + 1));
            if (r == KnowledgeGraph.Relation.DERIVE) {
                return DisclosurePlan.Kind.DERIVE;
            }
            if (r == KnowledgeGraph.Relation.EXTRACT) {
                kind = DisclosurePlan.Kind.EXTRACT;
            }
        }
        return kind;
    }

    private KnowledgeGraph.Relation relationBetween(IRI from, IRI to) {
        for (KnowledgeGraph.Edge e : graph.edges(from)) {
            if (e.target().equals(to)) {
                return e.relation();
            }
        }
        throw new IllegalStateException("no edge " + from + " -> " + to);
    }

    /**
     * Shortest valid path from a held datum to a target. A path may decompose or derive any
     * number of times, then generalise any number of times, but never decompose after
     * generalising.
     */
    Optional<List<IRI>> shortestPath(IRI from, IRI to) {
        if (from.equals(to)) {
            return Optional.of(List.of(from));
        }
        record State(IRI node, boolean generalised, List<IRI> path) {
        }
        record Visited(IRI node, boolean generalised) {
        }
        Deque<State> queue = new ArrayDeque<>();
        Set<Visited> seen = new HashSet<>();
        queue.add(new State(from, false, List.of(from)));
        while (!queue.isEmpty()) {
            State s = queue.poll();
            for (KnowledgeGraph.Edge e : graph.edges(s.node())) {
                boolean generalising = e.relation() == KnowledgeGraph.Relation.PRESENT_AS;
                if (s.generalised() && !generalising) {
                    continue;
                }
                List<IRI> path = new ArrayList<>(s.path());
                path.add(e.target());
                if (e.target().equals(to)) {
                    return Optional.of(path);
                }
                if (seen.add(new Visited(e.target(), s.generalised() || generalising))) {
                    queue.add(new State(e.target(), s.generalised() || generalising, path));
                }
            }
        }
        return Optional.empty();
    }
}
