package dataminimization;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.model.IRI;

/**
 * Result of checking a set of provided data against a service's needs.
 *
 * @param service     the service the data were submitted to
 * @param required    data the service cannot work without
 * @param optional    data the service may use
 * @param assessments one entry per distinct provided datum, in submission order
 * @param missing     required data that none of the provided data cover
 * @param verdict     overall outcome
 */
public record Decision(IRI service, Set<IRI> required, Set<IRI> optional,
                       List<DataAssessment> assessments, Set<IRI> missing, Verdict verdict) {

    public Decision {
        required = Set.copyOf(required);
        optional = Set.copyOf(optional);
        assessments = List.copyOf(assessments);
        missing = Set.copyOf(missing);
    }

    public boolean isCompliant() {
        return verdict == Verdict.COMPLIANT;
    }

    /** Provided data that should be rejected or replaced. */
    public List<DataAssessment> excessive() {
        return assessments.stream().filter(a -> !a.status().isMinimal()).toList();
    }

    /** Human-readable report of the decision. */
    public String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("Service: ").append(service.getLocalName()).append('\n');
        sb.append("  requires:    ").append(names(required)).append('\n');
        sb.append("  may require: ").append(names(optional)).append('\n');
        sb.append('\n').append("Provided data:\n");
        int width = assessments.stream().mapToInt(a -> a.datum().getLocalName().length()).max().orElse(0);
        for (DataAssessment a : assessments) {
            sb.append("  ").append(String.format("%-" + Math.max(width, 1) + "s  %-10s", a.datum().getLocalName(), a.status()));
            switch (a.status()) {
                case REQUIRED, OPTIONAL -> sb.append("named by the service");
                case SATISFIES -> sb.append("provides ").append(names(a.covers())).append(" and nothing more");
                case SUPERSET -> sb.append("provides ").append(names(a.covers()))
                        .append(" but also discloses ").append(names(a.exposes()))
                        .append("\n  ").append(" ".repeat(width)).append("  -> request ")
                        .append(names(a.substitutes())).append(" instead");
                case EXCESSIVE -> sb.append("not needed by this service -> reject");
                case UNKNOWN -> sb.append("not a known personal data type -> reject");
            }
            sb.append('\n');
        }
        sb.append('\n').append("Missing required data: ").append(missing.isEmpty() ? "none" : names(missing)).append('\n');
        sb.append("Verdict: ").append(verdict).append('\n');
        return sb.toString();
    }

    static String names(Set<IRI> iris) {
        if (iris.isEmpty()) {
            return "-";
        }
        return iris.stream().map(IRI::getLocalName).sorted(Comparator.naturalOrder()).collect(Collectors.joining(", "));
    }
}
