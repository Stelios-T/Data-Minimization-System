package dataminimization.eval;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dataminimization.Status;
import dataminimization.Verdict;
import dataminimization.Vocabulary;
import org.eclipse.rdf4j.model.IRI;

/** One labelled evaluation scenario from {@code scenarios.json}. */
public record Scenario(String id, String label, IRI service, IRI purpose, Set<IRI> context, List<IRI> provided,
                       Map<IRI, Status> expectedStatuses, Set<IRI> expectedMissing, Verdict expectedVerdict,
                       Map<IRI, Set<IRI>> expectedSubstitutes, String rationale) {

    /** One labelled planner scenario. */
    public record Plan(String id, String label, IRI service, IRI purpose, Set<IRI> context, Set<IRI> held,
                       boolean includeOptional, Set<IRI> expectedDisclosed, Set<IRI> expectedUnsatisfiable) {
    }

    /** The whole corpus. */
    public record Corpus(List<Scenario> evaluations, List<Plan> plans) {
    }

    public static Corpus loadDefault() {
        try (InputStream in = Scenario.class.getResourceAsStream("/scenarios.json")) {
            if (in == null) {
                throw new IllegalStateException("scenarios.json not found on the classpath");
            }
            return parse(new ObjectMapper().readTree(in));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read scenarios.json", e);
        }
    }

    static Corpus parse(JsonNode root) {
        List<Scenario> evaluations = new ArrayList<>();
        for (JsonNode n : root.path("evaluations")) {
            JsonNode expect = n.path("expect");
            Map<IRI, Status> statuses = new LinkedHashMap<>();
            expect.path("statuses").fields().forEachRemaining(e -> statuses.put(Vocabulary.data(e.getKey()), Status.valueOf(e.getValue().asText())));
            Map<IRI, Set<IRI>> substitutes = new LinkedHashMap<>();
            expect.path("substitutes").fields().forEachRemaining(e -> substitutes.put(Vocabulary.data(e.getKey()), data(e.getValue())));
            evaluations.add(new Scenario(n.path("id").asText(), n.path("label").asText(), Vocabulary.service(n.path("service").asText()),
                    n.hasNonNull("purpose") ? Vocabulary.purpose(n.path("purpose").asText()) : null,
                    conditions(n.path("context")), dataList(n.path("provided")), statuses, data(expect.path("missing")),
                    Verdict.valueOf(expect.path("verdict").asText()), substitutes, n.path("rationale").asText()));
        }
        List<Plan> plans = new ArrayList<>();
        for (JsonNode n : root.path("plans")) {
            JsonNode expect = n.path("expect");
            plans.add(new Plan(n.path("id").asText(), n.path("label").asText(), Vocabulary.service(n.path("service").asText()),
                    n.hasNonNull("purpose") ? Vocabulary.purpose(n.path("purpose").asText()) : null,
                    conditions(n.path("context")), data(n.path("held")), n.path("includeOptional").asBoolean(false),
                    data(expect.path("disclosed")), data(expect.path("unsatisfiable"))));
        }
        return new Corpus(evaluations, plans);
    }

    private static Set<IRI> data(JsonNode array) {
        return new LinkedHashSet<>(dataList(array));
    }

    private static List<IRI> dataList(JsonNode array) {
        List<IRI> out = new ArrayList<>();
        array.forEach(x -> out.add(Vocabulary.data(x.asText())));
        return out;
    }

    private static Set<IRI> conditions(JsonNode array) {
        Set<IRI> out = new LinkedHashSet<>();
        array.forEach(x -> out.add(Vocabulary.condition(x.asText())));
        return out;
    }
}
