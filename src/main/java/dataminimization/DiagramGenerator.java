package dataminimization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.rdf4j.model.IRI;

/**
 * Renders the knowledge graph as Mermaid diagrams in Markdown, so the documentation is
 * generated from the model instead of drawn by hand and can be checked for freshness.
 */
public final class DiagramGenerator {

    private static final Comparator<IRI> BY_NAME = Comparator.comparing(IRI::getLocalName);

    private DiagramGenerator() {
    }

    public static String markdown(KnowledgeGraph graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Knowledge graph\n\n");
        sb.append("Generated from `src/main/resources/model.ttl` by `java -jar target/dms.jar --diagrams`. Do not edit by hand; regenerate after changing the model. ");
        sb.append("Node labels show the sensitivity weight in parentheses. Solid arrows: `isPartOf` (part to whole) and `isMoreDetailedThan` (fine to coarse). Dotted arrows: `isA` (member to category).\n\n");

        sb.append("## Personal data\n\n");
        Map<String, List<IRI>> groups = groups(graph);
        List<String> groupKeys = groups.keySet().stream().sorted().toList();
        for (String key : groupKeys) {
            sb.append("### ").append(key).append("\n\n```mermaid\ngraph LR\n");
            Set<IRI> members = new LinkedHashSet<>(groups.get(key).stream().sorted(BY_NAME).toList());
            Set<IRI> shown = new LinkedHashSet<>(members);
            for (IRI d : members) {
                for (KnowledgeGraph.Edge e : graph.edges(d)) {
                    shown.add(e.target());
                }
            }
            for (IRI d : shown.stream().sorted(BY_NAME).toList()) {
                sb.append("  ").append(id(d)).append("[\"").append(d.getLocalName()).append(" (").append(graph.sensitivity(d)).append(")\"]")
                        .append(members.contains(d) ? "" : ":::other").append('\n');
            }
            for (IRI d : members) {
                for (KnowledgeGraph.Edge e : graph.edges(d)) {
                    switch (e.relation()) {
                        case EXTRACT -> sb.append("  ").append(id(e.target())).append(" -->|isPartOf| ").append(id(d)).append('\n');
                        case DERIVE -> sb.append("  ").append(id(d)).append(" -->|isMoreDetailedThan| ").append(id(e.target())).append('\n');
                        case PRESENT_AS -> sb.append("  ").append(id(d)).append(" -.->|isA| ").append(id(e.target())).append('\n');
                    }
                }
            }
            sb.append("  classDef other stroke-dasharray: 4 4,opacity: 0.6\n");
            sb.append("```\n\n");
        }

        sb.append("## Services\n\n");
        sb.append("Each service is shown with its processing nodes (purpose in parentheses). Solid arrows: `requires`. Dashed arrows: `mayRequires`. Dotted arrows carry the condition under which a datum is required.\n\n");
        for (IRI s : graph.services().stream().sorted(BY_NAME).toList()) {
            sb.append("### ").append(s.getLocalName()).append("\n\n```mermaid\ngraph LR\n");
            sb.append("  ").append(id(s)).append("([\"").append(s.getLocalName()).append("\"])\n");
            Set<IRI> data = new LinkedHashSet<>();
            for (Processing p : graph.processings(s)) {
                String pid = id(p.node());
                String label = p.purpose() == null ? "direct" : p.purpose().getLocalName();
                sb.append("  ").append(pid).append("{{\"").append(label).append("\"}}\n");
                sb.append("  ").append(id(s)).append(" --> ").append(pid).append('\n');
                for (IRI d : p.required().stream().sorted(BY_NAME).toList()) {
                    sb.append("  ").append(pid).append(" -->|requires| ").append(id(d)).append('\n');
                    data.add(d);
                }
                for (IRI d : p.optional().stream().sorted(BY_NAME).toList()) {
                    sb.append("  ").append(pid).append(" -.->|mayRequires| ").append(id(d)).append('\n');
                    data.add(d);
                }
                p.conditional().entrySet().stream().sorted(Map.Entry.comparingByKey(BY_NAME)).forEach(e -> {
                    sb.append("  ").append(pid).append(" -.->|\"requires when ").append(Names.conditions(e.getValue())).append("\"| ").append(id(e.getKey())).append('\n');
                    data.add(e.getKey());
                });
            }
            for (IRI d : data.stream().sorted(BY_NAME).toList()) {
                sb.append("  ").append(id(d)).append("[\"").append(d.getLocalName()).append(" (").append(graph.sensitivity(d)).append(")\"]\n");
            }
            sb.append("```\n\n");
        }

        sb.append("## DPV-PD mapping\n\n| Data type | Sensitivity | DPV-PD concept |\n|---|---|---|\n");
        graph.personalData().stream().sorted(Comparator.comparing(IRI::getLocalName)).forEach(d ->
                sb.append("| ").append(d.getLocalName()).append(" | ").append(graph.sensitivity(d)).append(" | ")
                        .append(graph.dpvMatch(d).map(m -> "`pd:" + m.getLocalName() + "`").orElse("-")).append(" |\n"));
        return sb.toString();
    }

    /** Group name used for data types that belong to no category. */
    public static final String OTHER_GROUP = "(no category)";

    /**
     * Data types grouped by the local name of the top-level category they generalise to;
     * categories without a parent head their own group, isolated types go to {@link #OTHER_GROUP}.
     */
    public static Map<String, List<IRI>> groups(KnowledgeGraph graph) {
        Map<String, List<IRI>> groups = new LinkedHashMap<>();
        for (IRI d : graph.personalData()) {
            IRI top = topCategory(graph, d, new LinkedHashSet<>());
            groups.computeIfAbsent(top == null ? OTHER_GROUP : top.getLocalName(), k -> new ArrayList<>()).add(d);
        }
        return groups;
    }

    /**
     * The category a datum belongs to: climb {@code isA} as far as possible; a datum without a
     * category of its own inherits the category of the first whole it is a direct part of.
     * Returns the datum itself when it is a top-level category, {@code null} when it is isolated.
     */
    private static IRI topCategory(KnowledgeGraph graph, IRI d, Set<IRI> seen) {
        if (!seen.add(d)) {
            return null;
        }
        for (KnowledgeGraph.Edge e : graph.edges(d)) {
            if (e.relation() == KnowledgeGraph.Relation.PRESENT_AS) {
                IRI up = topCategory(graph, e.target(), seen);
                return up == null ? e.target() : up;
            }
        }
        for (IRI whole : graph.personalData()) {
            boolean directPart = graph.edges(whole).stream()
                    .anyMatch(e -> e.relation() == KnowledgeGraph.Relation.EXTRACT && e.target().equals(d));
            if (directPart) {
                IRI up = topCategory(graph, whole, seen);
                if (up != null) {
                    return up;
                }
            }
        }
        boolean isCategory = graph.personalData().stream().anyMatch(x -> graph.edges(x).stream()
                .anyMatch(e -> e.relation() == KnowledgeGraph.Relation.PRESENT_AS && e.target().equals(d)));
        return isCategory ? d : null;
    }

    static String id(IRI iri) {
        return iri.getLocalName().replaceAll("[^A-Za-z0-9_]", "_");
    }
}
