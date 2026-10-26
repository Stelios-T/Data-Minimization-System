package dataminimization;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.eclipse.rdf4j.model.IRI;

/**
 * Applies a {@link Decision} to a real JSON payload: keeps the fields the service needs,
 * drops the ones it does not, and replaces superset fields by the narrower values a
 * {@link ValueTransformers} can compute locally.
 */
public final class PayloadMinimizer {

    private final KnowledgeGraph graph;
    private final DataMinimizationFilter filter;
    private final ValueTransformers transformers;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Uses the built-in transformers with "today" taken in UTC, so an age does not depend on the server's zone. */
    public PayloadMinimizer(KnowledgeGraph graph) {
        this(graph, Clock.systemUTC());
    }

    public PayloadMinimizer(KnowledgeGraph graph, Clock clock) {
        this(graph, ValueTransformers.standard(graph, clock));
    }

    public PayloadMinimizer(KnowledgeGraph graph, ValueTransformers transformers) {
        this.graph = graph;
        this.filter = new DataMinimizationFilter(graph);
        this.transformers = transformers;
    }

    /**
     * @param service      the service the payload is sent to
     * @param purpose      one purpose, or {@code null} for all
     * @param conditions   active conditions
     * @param payload      the payload; top-level fields are the unit of minimization
     * @param mapping      field name to personal data type
     * @param keepUnmapped pass fields with no mapping through instead of dropping them
     */
    public MinimizationResult minimize(IRI service, IRI purpose, Set<IRI> conditions, ObjectNode payload,
                                       Map<String, IRI> mapping, boolean keepUnmapped) {
        Map<IRI, String> fieldOfType = new HashMap<>();
        List<IRI> provided = new ArrayList<>();
        for (Iterator<String> it = payload.fieldNames(); it.hasNext();) {
            String field = it.next();
            IRI type = mapping.get(field);
            if (type != null && !fieldOfType.containsKey(type)) {
                fieldOfType.put(type, field);
                provided.add(type);
            }
        }
        Decision decision = filter.evaluate(service, purpose, conditions, provided);
        Map<IRI, DataAssessment> byType = new HashMap<>();
        decision.assessments().forEach(a -> byType.put(a.datum(), a));

        ObjectNode out = mapper.createObjectNode();
        List<MinimizationResult.FieldAction> actions = new ArrayList<>();
        Set<IRI> covered = new LinkedHashSet<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = payload.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> e = it.next();
            String field = e.getKey();
            JsonNode value = e.getValue();
            IRI type = mapping.get(field);
            if (type == null) {
                if (keepUnmapped) {
                    out.set(field, value);
                    actions.add(new MinimizationResult.FieldAction(field, MinimizationResult.Action.KEEP, null, field, "no mapping; kept as requested"));
                } else {
                    actions.add(new MinimizationResult.FieldAction(field, MinimizationResult.Action.DROP, null, null, "no mapping to a personal data type"));
                }
                continue;
            }
            if (!field.equals(fieldOfType.get(type))) {
                actions.add(new MinimizationResult.FieldAction(field, MinimizationResult.Action.DROP, type, null,
                        "duplicate of " + fieldOfType.get(type) + " (" + type.getLocalName() + ")"));
                continue;
            }
            DataAssessment a = byType.get(type);
            switch (a.status()) {
                case REQUIRED, OPTIONAL, SATISFIES -> {
                    out.set(field, value);
                    covered.addAll(a.covers());
                    actions.add(new MinimizationResult.FieldAction(field, MinimizationResult.Action.KEEP, type, field,
                            a.status() == Status.SATISFIES ? "provides exactly " + Names.of(a.covers()) : a.status().name().toLowerCase() + " by the service"));
                }
                case EXCESSIVE -> actions.add(new MinimizationResult.FieldAction(field, MinimizationResult.Action.DROP, type, null,
                        type.getLocalName() + " is not needed by " + service.getLocalName()));
                case UNKNOWN -> actions.add(new MinimizationResult.FieldAction(field, MinimizationResult.Action.DROP, type, null,
                        type.getLocalName() + " is not a known personal data type"));
                default -> throw new IllegalStateException("unhandled status " + a.status());
                case SUPERSET -> {
                    for (IRI needed : a.covers().stream().sorted(Comparator.comparing(IRI::getLocalName)).toList()) {
                        if (covered.contains(needed)) {
                            continue;
                        }
                        Optional<JsonNode> derived = transformers.transform(type, needed, value);
                        String outField = ValueTransformers.fieldName(needed);
                        if (derived.isPresent()) {
                            out.set(outField, derived.get());
                            covered.add(needed);
                            actions.add(new MinimizationResult.FieldAction(field, MinimizationResult.Action.TRANSFORM, needed, outField,
                                    needed.getLocalName() + " computed locally from " + type.getLocalName()));
                        } else {
                            actions.add(new MinimizationResult.FieldAction(field, MinimizationResult.Action.REQUEST, needed, null,
                                    "cannot derive " + needed.getLocalName() + " from this value locally; request it from the user"));
                        }
                    }
                    actions.add(new MinimizationResult.FieldAction(field, MinimizationResult.Action.DROP, type, null,
                            type.getLocalName() + " discloses more than needed (" + Names.of(a.exposes()) + ")"));
                }
            }
        }
        Set<IRI> missing = new LinkedHashSet<>(decision.required());
        missing.removeAll(covered);
        return new MinimizationResult(out, decision, actions, missing);
    }

    /**
     * Parses a {@code {"field": "TypeLocalName"}} mapping.
     *
     * @throws IllegalArgumentException when the node is not an object of non-empty strings
     */
    public static Map<String, IRI> parseMapping(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("mapping must be a JSON object of field name to data type");
        }
        Map<String, IRI> mapping = new HashMap<>();
        node.fields().forEachRemaining(e -> {
            if (!e.getValue().isTextual() || e.getValue().asText().isBlank()) {
                throw new IllegalArgumentException("mapping value for '" + e.getKey() + "' must be a data type name");
            }
            mapping.put(e.getKey(), Vocabulary.data(e.getValue().asText()));
        });
        return mapping;
    }
}
