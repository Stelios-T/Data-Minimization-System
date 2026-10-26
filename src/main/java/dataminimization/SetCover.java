package dataminimization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.model.IRI;

/**
 * Weighted set cover over personal data types: which set of data, at the lowest total
 * disclosure cost, covers everything a service needs.
 */
public final class SetCover {

    /** Candidates above this number switch from exact enumeration to a greedy heuristic. */
    static final int EXACT_LIMIT = 20;

    /** A set of data that together cover the needed items, and its total cost. */
    public record Cover(Set<IRI> chosen, int cost) {
        public Cover {
            chosen = Iris.sorted(chosen);
        }
    }

    private SetCover() {
    }

    /**
     * Returns up to {@code k} minimal covers of {@code needed}, cheapest first. A cover is
     * minimal when no proper subset of it also covers {@code needed}.
     *
     * @param needed          the items to cover
     * @param candidateCovers for each candidate datum, the needed items it covers
     * @param cost            disclosure cost of a candidate
     */
    public static List<Cover> cheapest(Set<IRI> needed, Map<IRI, Set<IRI>> candidateCovers, ToIntFunction<IRI> cost, int k) {
        if (needed.isEmpty()) {
            return List.of(new Cover(Set.of(), 0));
        }
        List<IRI> candidates = candidateCovers.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(needed::contains))
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparingInt(cost).thenComparing(IRI::getLocalName))
                .toList();
        Set<IRI> reachable = candidates.stream().flatMap(c -> candidateCovers.get(c).stream()).collect(Collectors.toSet());
        if (!reachable.containsAll(needed)) {
            return List.of();
        }
        if (candidates.size() > EXACT_LIMIT) {
            return List.of(greedy(needed, candidates, candidateCovers, cost));
        }
        Search search = new Search(needed, candidates, candidateCovers, cost, Math.min(needed.size(), candidates.size()), k);
        search.enumerate(0, new ArrayList<>(), 0);
        List<Cover> covers = new ArrayList<>(search.found);
        // a set reached by adding a cheap candidate before a complete one is a cover but not a minimal one
        covers.removeIf(c -> covers.stream().anyMatch(o -> o != c && c.chosen().size() > o.chosen().size() && c.chosen().containsAll(o.chosen())));
        covers.sort(ORDER);
        return covers.size() > k ? List.copyOf(covers.subList(0, k)) : List.copyOf(covers);
    }

    private static final Comparator<Cover> ORDER = Comparator.comparingInt(Cover::cost)
            .thenComparingInt(c -> c.chosen().size())
            .thenComparing(c -> c.chosen().stream().map(IRI::getLocalName).sorted().toList().toString());

    /**
     * Depth-first enumeration of candidate subsets in increasing cost order of candidates. A
     * branch is cut as soon as it covers everything (any superset would not be minimal) or
     * its cost already exceeds the k-th cheapest cover found so far.
     */
    private static final class Search {
        final Set<IRI> needed;
        final List<IRI> candidates;
        final Map<IRI, Set<IRI>> candidateCovers;
        final ToIntFunction<IRI> cost;
        final int maxSize;
        final int k;
        final List<Cover> found = new ArrayList<>();

        Search(Set<IRI> needed, List<IRI> candidates, Map<IRI, Set<IRI>> candidateCovers, ToIntFunction<IRI> cost, int maxSize, int k) {
            this.needed = needed;
            this.candidates = candidates;
            this.candidateCovers = candidateCovers;
            this.cost = cost;
            this.maxSize = maxSize;
            this.k = k;
        }

        void enumerate(int from, List<IRI> chosen, int chosenCost) {
            if (covers(chosen, needed, candidateCovers)) {
                found.add(new Cover(new LinkedHashSet<>(chosen), chosenCost));
                return;
            }
            if (chosen.size() == maxSize || chosenCost > bound()) {
                return;
            }
            for (int i = from; i < candidates.size(); i++) {
                IRI c = candidates.get(i);
                int next = chosenCost + cost.applyAsInt(c);
                if (next > bound()) {
                    continue;
                }
                chosen.add(c);
                enumerate(i + 1, chosen, next);
                chosen.remove(chosen.size() - 1);
            }
        }

        /** Cost of the k-th cheapest cover found so far, or unbounded while fewer than k are known. */
        int bound() {
            if (found.size() < k) {
                return Integer.MAX_VALUE;
            }
            return found.stream().mapToInt(Cover::cost).sorted().skip(k - 1).findFirst().orElse(Integer.MAX_VALUE);
        }
    }

    private static boolean covers(List<IRI> chosen, Set<IRI> needed, Map<IRI, Set<IRI>> candidateCovers) {
        Set<IRI> covered = new HashSet<>();
        for (IRI c : chosen) {
            covered.addAll(candidateCovers.get(c));
        }
        return covered.containsAll(needed);
    }

    private static Cover greedy(Set<IRI> needed, List<IRI> candidates, Map<IRI, Set<IRI>> candidateCovers, ToIntFunction<IRI> cost) {
        Set<IRI> remaining = new HashSet<>(needed);
        Set<IRI> chosen = new LinkedHashSet<>();
        while (!remaining.isEmpty()) {
            IRI best = null;
            double bestRatio = Double.MAX_VALUE;
            for (IRI c : candidates) {
                if (chosen.contains(c)) {
                    continue;
                }
                long gain = candidateCovers.get(c).stream().filter(remaining::contains).count();
                if (gain == 0) {
                    continue;
                }
                double ratio = (double) cost.applyAsInt(c) / gain;
                if (ratio < bestRatio) {
                    bestRatio = ratio;
                    best = c;
                }
            }
            chosen.add(best);
            remaining.removeAll(candidateCovers.get(best));
        }
        return new Cover(chosen, chosen.stream().mapToInt(cost).sum());
    }
}
