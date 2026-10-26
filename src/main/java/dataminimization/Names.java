package dataminimization;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.model.IRI;

/** Short, sorted, human-readable renderings of IRIs. */
public final class Names {

    private Names() {
    }

    /** "A, B, C" sorted by local name, or "-" when empty. */
    public static String of(Collection<IRI> iris) {
        if (iris.isEmpty()) {
            return "-";
        }
        return iris.stream().map(IRI::getLocalName).sorted(Comparator.naturalOrder()).collect(Collectors.joining(", "));
    }

    /** Like {@link #of} but appends "(when C1 or C2)" to data that are required only under conditions. */
    public static String withConditions(Set<IRI> iris, Map<IRI, Set<IRI>> conditional) {
        if (iris.isEmpty()) {
            return "-";
        }
        return iris.stream().sorted(Comparator.comparing(IRI::getLocalName))
                .map(i -> conditional.containsKey(i)
                        ? i.getLocalName() + " (when " + conditions(conditional.get(i)) + ")"
                        : i.getLocalName())
                .collect(Collectors.joining(", "));
    }

    /** "A" or "A or B": any one of the conditions suffices. */
    public static String conditions(Set<IRI> conditions) {
        return conditions.stream().map(IRI::getLocalName).sorted().collect(Collectors.joining(" or "));
    }
}
