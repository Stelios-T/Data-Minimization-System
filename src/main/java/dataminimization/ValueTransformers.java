package dataminimization;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.BooleanNode;

import org.eclipse.rdf4j.model.IRI;

/**
 * Local transformations of personal data values: how a user agent turns a datum it holds
 * into the narrower datum a service needs. Specific transformers handle derivations
 * (a date of birth into an adult flag); a generic rule extracts members of JSON objects by
 * name, following the containment relations of the knowledge graph.
 */
public final class ValueTransformers {

    /** Turns one JSON value into another, or empty when the value has an unexpected shape. */
    @FunctionalInterface
    public interface Transformer extends Function<JsonNode, Optional<JsonNode>> {
    }

    private static final int MAX_DEPTH = 5;

    private final KnowledgeGraph graph;
    private final Map<String, Transformer> specific;

    /**
     * @param graph    the knowledge graph whose containment relations drive member extraction
     * @param specific transformers keyed by {@code "FromLocalName>ToLocalName"}; copied, so the instance is immutable and thread-safe
     */
    public ValueTransformers(KnowledgeGraph graph, Map<String, Transformer> specific) {
        this.graph = graph;
        this.specific = Map.copyOf(specific);
    }

    /** The built-in transformers; {@code clock} fixes "today" for age calculations. */
    public static ValueTransformers standard(KnowledgeGraph graph, Clock clock) {
        Builder t = new Builder();
        t.register("DateOfBirth", "IsAdult", v -> date(v).map(d -> BooleanNode.valueOf(Period.between(d, LocalDate.now(clock)).getYears() >= 18)));
        t.register("DateOfBirth", "ExactAge", v -> date(v).map(d -> new IntNode(Period.between(d, LocalDate.now(clock)).getYears())));
        t.register("DateOfBirth", "YearOfBirth", v -> date(v).map(d -> new IntNode(d.getYear())));
        t.register("DateOfBirth", "MonthOfBirth", v -> date(v).map(d -> new IntNode(d.getMonthValue())));
        t.register("DateOfBirth", "DayOfBirth", v -> date(v).map(d -> new IntNode(d.getDayOfMonth())));
        t.register("ExactAge", "IsAdult", v -> v.isInt() ? Optional.of(BooleanNode.valueOf(v.intValue() >= 18)) : Optional.empty());
        t.register("FullName", "FirstName", v -> words(v, 2).map(w -> new TextNode(w[0])));
        t.register("FullName", "LastName", v -> words(v, 2).map(w -> new TextNode(w[w.length - 1])));
        t.register("FullName", "MiddleName", v -> words(v, 3).map(w -> new TextNode(String.join(" ", java.util.Arrays.copyOfRange(w, 1, w.length - 1)))));
        return new ValueTransformers(graph, t.map);
    }

    /** Collects transformers before a {@link ValueTransformers} is built. */
    public static final class Builder {
        private final Map<String, Transformer> map = new HashMap<>();

        public Builder register(String fromLocalName, String toLocalName, Transformer transformer) {
            map.put(fromLocalName + ">" + toLocalName, transformer);
            return this;
        }

        public ValueTransformers build(KnowledgeGraph graph) {
            return new ValueTransformers(graph, map);
        }
    }

    /**
     * Produces the value of {@code to} from the value of {@code from}, trying a specific
     * transformer first and then member extraction along the containment relations.
     */
    public Optional<JsonNode> transform(IRI from, IRI to, JsonNode value) {
        return transform(from, to, value, 0);
    }

    private Optional<JsonNode> transform(IRI from, IRI to, JsonNode value, int depth) {
        if (from.equals(to)) {
            return Optional.of(value);
        }
        if (depth > MAX_DEPTH || value == null || value.isNull()) {
            return Optional.empty();
        }
        Transformer t = specific.get(from.getLocalName() + ">" + to.getLocalName());
        if (t != null) {
            Optional<JsonNode> r = t.apply(value);
            if (r.isPresent()) {
                return r;
            }
        }
        if (value.isObject()) {
            for (IRI x : graph.exposes(from)) {
                if (!graph.covers(x).contains(to)) {
                    continue;
                }
                JsonNode member = member((ObjectNode) value, x);
                if (member != null) {
                    Optional<JsonNode> r = transform(x, to, member, depth + 1);
                    if (r.isPresent()) {
                        return r;
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** The JSON field name used for a data type: {@code IsAdult} becomes {@code isAdult}, {@code IDCard} becomes {@code idCard}. */
    public static String fieldName(IRI type) {
        String n = type.getLocalName();
        int firstLower = 0;
        while (firstLower < n.length() && Character.isUpperCase(n.charAt(firstLower))) {
            firstLower++;
        }
        int cut = firstLower <= 1 ? 1 : (firstLower == n.length() ? firstLower : firstLower - 1);
        return n.substring(0, cut).toLowerCase() + n.substring(cut);
    }

    static String normalise(String name) {
        return name.replace("_", "").replace("-", "").toLowerCase();
    }

    private static JsonNode member(ObjectNode object, IRI type) {
        String wanted = normalise(type.getLocalName());
        var it = object.fieldNames();
        while (it.hasNext()) {
            String f = it.next();
            if (normalise(f).equals(wanted)) {
                return object.get(f);
            }
        }
        return null;
    }

    private static Optional<LocalDate> date(JsonNode v) {
        if (!v.isTextual()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(v.textValue()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Optional<String[]> words(JsonNode v, int atLeast) {
        if (!v.isTextual()) {
            return Optional.empty();
        }
        String[] w = v.textValue().trim().split("\\s+");
        return w.length >= atLeast ? Optional.of(w) : Optional.empty();
    }
}
