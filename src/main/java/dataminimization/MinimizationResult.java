package dataminimization;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.eclipse.rdf4j.model.IRI;

/**
 * A payload after minimization.
 *
 * @param minimized the payload that may be sent
 * @param decision  the decision on the original payload's data types
 * @param actions   what happened to each field
 * @param missing   required data the minimized payload still lacks
 */
public record MinimizationResult(ObjectNode minimized, Decision decision, List<FieldAction> actions, Set<IRI> missing) {

    /** What happened to one field. */
    public enum Action { KEEP, DROP, TRANSFORM, REQUEST }

    /**
     * @param field  the original field (for REQUEST, the field that could not be transformed)
     * @param action what was done
     * @param type   the field's data type, or the type to request
     * @param output the field written to the minimized payload, if any
     * @param reason why
     */
    public record FieldAction(String field, Action action, IRI type, String output, String reason) {
    }

    public MinimizationResult {
        actions = List.copyOf(actions);
        missing = Iris.sorted(missing);
    }

    @JsonProperty("complete")
    public boolean isComplete() {
        return missing.isEmpty();
    }

    /** Human-readable list of field actions; see {@link TextReports#minimization}. */
    public String report() {
        return TextReports.minimization(this);
    }
}
