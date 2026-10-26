package dataminimization;

import java.util.Set;

import org.eclipse.rdf4j.model.IRI;

/**
 * A problem found in the knowledge graph.
 *
 * @param severity ERROR makes the graph unusable; WARNING is a modelling smell
 * @param code     stable identifier, e.g. {@code CYCLE_PART_OF} or {@code SHACL:MinCountConstraintComponent}
 * @param message  human-readable explanation
 * @param subjects the nodes involved
 */
public record Violation(Severity severity, String code, String message, Set<IRI> subjects) {

    public enum Severity { ERROR, WARNING }

    public Violation {
        subjects = Iris.sorted(subjects);
    }

    @Override
    public String toString() {
        return TextReports.violation(this);
    }
}
