package dataminimization;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.rdf4j.model.IRI;

/**
 * Deterministic, immutable collections of IRIs. Result records use these so that JSON, RDF
 * and API output come out in the same order on every run; {@code Set.copyOf} would
 * randomise the order per JVM.
 */
public final class Iris {

    /** Local name first, full IRI as tie-breaker. */
    public static final Comparator<IRI> BY_NAME = Comparator.comparing(IRI::getLocalName).thenComparing(IRI::stringValue);

    private Iris() {
    }

    /** An immutable set sorted by local name. */
    public static Set<IRI> sorted(Collection<IRI> iris) {
        TreeSet<IRI> set = new TreeSet<>(BY_NAME);
        set.addAll(iris);
        return Collections.unmodifiableSet(set);
    }

    /** An immutable map with keys sorted by local name and values sorted by local name. */
    public static Map<IRI, Set<IRI>> sortedMap(Map<IRI, ? extends Collection<IRI>> map) {
        TreeMap<IRI, Set<IRI>> sorted = new TreeMap<>(BY_NAME);
        map.forEach((k, v) -> sorted.put(k, sorted(v)));
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }
}
