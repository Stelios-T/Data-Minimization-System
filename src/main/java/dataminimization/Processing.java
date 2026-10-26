package dataminimization;

import java.util.Map;
import java.util.Set;

import org.eclipse.rdf4j.model.IRI;

/**
 * One processing operation a service performs for one purpose, with the data it needs.
 *
 * @param node        the processing node, or the service itself for requirements stated directly on the service
 * @param purpose     the purpose, or {@code null} for requirements stated directly on the service
 * @param required    data the processing cannot work without
 * @param optional    data the processing can optionally use
 * @param conditional data required only under a condition, as datum to the conditions (any one suffices)
 */
public record Processing(IRI node, IRI purpose, Set<IRI> required, Set<IRI> optional, Map<IRI, Set<IRI>> conditional) {

    public Processing {
        required = Iris.sorted(required);
        optional = Iris.sorted(optional);
        conditional = Iris.sortedMap(conditional);
    }
}
