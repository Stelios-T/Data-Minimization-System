package dataminimization;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.rdf4j.model.IRI;

/**
 * What a service needs, after choosing a purpose (or all purposes) and a set of active conditions.
 *
 * @param required    data that must be covered; includes conditional requirements whose condition is active
 * @param optional    data that is accepted but never demanded
 * @param conditional for each conditionally required datum that is active, the active conditions that triggered it
 * @param inactive    conditional requirements none of whose conditions is active, as datum to those conditions
 */
public record Requirements(Set<IRI> required, Set<IRI> optional, Map<IRI, Set<IRI>> conditional, Map<IRI, Set<IRI>> inactive) {

    public Requirements {
        required = Iris.sorted(required);
        optional = Iris.sorted(optional);
        conditional = Iris.sortedMap(conditional);
        inactive = Iris.sortedMap(inactive);
    }

    /** Required and optional data together. */
    public Set<IRI> needed() {
        Set<IRI> all = new LinkedHashSet<>(required);
        all.addAll(optional);
        return Iris.sorted(all);
    }
}
