package dataminimization;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.model.IRI;

/** Plain-text renderings of results, kept apart from the result records themselves. */
public final class TextReports {

    private TextReports() {
    }

    public static String decision(Decision d) {
        StringBuilder sb = new StringBuilder();
        Requirements req = d.requirements();
        sb.append("Service: ").append(d.service().getLocalName()).append('\n');
        sb.append("  purpose:     ").append(d.purpose() == null
                ? (d.purposes().isEmpty() ? "-" : "all (" + Names.of(d.purposes()) + ")") : d.purpose().getLocalName()).append('\n');
        if (!d.conditions().isEmpty()) {
            sb.append("  context:     ").append(Names.of(d.conditions())).append('\n');
        }
        sb.append("  requires:    ").append(Names.withConditions(req.required(), req.conditional())).append('\n');
        sb.append("  may require: ").append(Names.of(req.optional())).append('\n');
        if (!req.inactive().isEmpty()) {
            sb.append("  not active:  ").append(req.inactive().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(IRI::getLocalName)))
                    .map(e -> e.getKey().getLocalName() + " (only when " + Names.conditions(e.getValue()) + ")")
                    .collect(Collectors.joining(", "))).append('\n');
        }
        sb.append('\n').append("Provided data:\n");
        int width = Math.max(1, d.assessments().stream().mapToInt(a -> a.datum().getLocalName().length()).max().orElse(1));
        String indent = "  " + " ".repeat(width) + "  ";
        for (DataAssessment a : d.assessments()) {
            sb.append("  ").append(String.format("%-" + width + "s  %-10s %-8s", a.datum().getLocalName(), a.status(),
                    a.status() == Status.UNKNOWN ? "" : "cost " + a.cost()));
            switch (a.status()) {
                case REQUIRED, OPTIONAL -> {
                    sb.append("named by the service");
                    if (!a.because().isEmpty()) {
                        sb.append(" because ").append(Names.conditions(a.because()));
                    }
                }
                case SATISFIES -> sb.append("provides ").append(Names.of(a.covers())).append(" and nothing more");
                case SUPERSET -> {
                    sb.append("provides ").append(Names.of(a.covers())).append(" but also discloses ").append(Names.of(a.exposes()));
                    sb.append('\n').append(indent).append("-> request ").append(Names.of(a.substitutes())).append(" instead");
                    if (!a.alternatives().isEmpty()) {
                        sb.append("; options: ").append(a.alternatives().stream()
                                .map(c -> Names.of(c.chosen()) + " (cost " + c.cost() + ")")
                                .collect(Collectors.joining("; ")));
                    }
                }
                case EXCESSIVE -> sb.append("not needed by this service -> reject");
                case UNKNOWN -> sb.append("not a known personal data type -> reject");
            }
            sb.append('\n');
        }
        sb.append('\n').append("Missing required data: ")
                .append(d.missing().isEmpty() ? "none" : Names.withConditions(d.missing(), req.conditional())).append('\n');
        sb.append("Minimal disclosure: ").append(Names.of(d.minimalDisclosure())).append(" (cost ").append(d.minimalCost())
                .append("); offered cost ").append(d.offeredCost()).append('\n');
        sb.append("Verdict: ").append(d.verdict()).append('\n');
        return sb.toString();
    }

    public static String plan(DisclosurePlan p) {
        StringBuilder sb = new StringBuilder();
        Requirements req = p.requirements();
        sb.append("Service: ").append(p.service().getLocalName()).append('\n');
        sb.append("  purpose:     ").append(p.purpose() == null ? "all" : p.purpose().getLocalName()).append('\n');
        if (!p.conditions().isEmpty()) {
            sb.append("  context:     ").append(Names.of(p.conditions())).append('\n');
        }
        sb.append("  requires:    ").append(Names.withConditions(req.required(), req.conditional())).append('\n');
        sb.append("  may require: ").append(Names.of(req.optional())).append('\n');
        sb.append("  held:        ").append(Names.of(p.held())).append('\n');
        if (!p.unknown().isEmpty()) {
            sb.append("  ignored:     ").append(Names.of(p.unknown())).append(" (not personal data types in the graph)\n");
        }
        sb.append('\n').append("Disclosure plan:\n");
        for (DisclosurePlan.Step s : p.steps()) {
            String via = s.path().size() > 2
                    ? " via " + s.path().subList(1, s.path().size() - 1).stream().map(IRI::getLocalName).collect(Collectors.joining(" > "))
                    : "";
            String what = switch (s.kind()) {
                case DISCLOSE -> "send " + s.to().getLocalName();
                case EXTRACT -> "extract " + s.to().getLocalName() + " from " + s.from().getLocalName() + via + " and send only that";
                case DERIVE -> "derive " + s.to().getLocalName() + " from " + s.from().getLocalName() + via + " locally and send the result";
                case PRESENT_AS -> "send " + s.from().getLocalName() + " as a " + s.to().getLocalName();
            };
            sb.append("  ").append(String.format("%-10s", s.kind())).append(what).append(s.optional() ? " (optional)" : "").append('\n');
        }
        if (p.steps().isEmpty()) {
            sb.append("  nothing to send\n");
        }
        sb.append('\n').append("Cannot be provided: ").append(p.unsatisfiable().isEmpty() ? "none" : Names.of(p.unsatisfiable())).append('\n');
        sb.append("Disclosed: ").append(Names.of(p.disclosed())).append(" (cost ").append(p.planCost())
                .append("); sending everything held would cost ").append(p.naiveCost()).append('\n');
        return sb.toString();
    }

    public static String minimization(MinimizationResult r) {
        StringBuilder sb = new StringBuilder();
        int width = Math.max(1, r.actions().stream().mapToInt(a -> a.field().length()).max().orElse(1));
        for (MinimizationResult.FieldAction a : r.actions()) {
            sb.append(String.format("  %-" + width + "s  %-9s ", a.field(), a.action()));
            if (a.output() != null && !a.output().equals(a.field())) {
                sb.append("-> ").append(a.output()).append("  ");
            }
            sb.append(a.reason()).append('\n');
        }
        sb.append("Still missing: ").append(r.missing().isEmpty() ? "none" : Names.of(r.missing())).append('\n');
        return sb.toString();
    }

    public static String violation(Violation v) {
        return v.severity() + " " + v.code() + ": " + v.message() + (v.subjects().isEmpty() ? "" : " [" + Names.of(v.subjects()) + "]");
    }
}
