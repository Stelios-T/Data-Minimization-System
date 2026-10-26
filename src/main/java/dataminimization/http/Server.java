package dataminimization.http;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dataminimization.DataMinimizationFilter;
import dataminimization.Decision;
import dataminimization.DiagramGenerator;
import dataminimization.DisclosurePlan;
import dataminimization.DisclosurePlanner;
import dataminimization.JsonOutput;
import dataminimization.KnowledgeGraph;
import dataminimization.MinimizationResult;
import dataminimization.OntologyValidator;
import dataminimization.PayloadMinimizer;
import dataminimization.Processing;
import dataminimization.Violation;
import dataminimization.Vocabulary;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import org.eclipse.rdf4j.model.IRI;

/**
 * A small HTTP API over the knowledge graph plus a static demo page at {@code /}.
 *
 * <pre>
 *   GET  /api/services                 services with purposes, needs and conditions
 *   GET  /api/services/{name}          one service
 *   GET  /api/data                     personal data types with group, sensitivity and DPV match
 *   GET  /api/validate                 ontology validation findings
 *   POST /api/evaluate                 {service, purpose?, context?, data[]}            -> {decision, report}
 *   POST /api/plan                     {service, purpose?, context?, held[], includeOptional?} -> {plan, report}
 *   POST /api/minimize                 {service, purpose?, context?, mapping{}, payload{}, keepUnmapped?} -> {result, report}
 * </pre>
 * Bad input gives 400 with {@code {"error": "..."}}.
 */
public final class Server {

    private final KnowledgeGraph graph;
    private final ObjectMapper json;
    private final DataMinimizationFilter filter;
    private final DisclosurePlanner planner;
    private final PayloadMinimizer minimizer;
    private volatile List<Violation> validation;

    public Server(KnowledgeGraph graph) {
        this.graph = graph;
        this.json = JsonOutput.mapper(graph);
        this.filter = new DataMinimizationFilter(graph);
        this.planner = new DisclosurePlanner(graph);
        this.minimizer = new PayloadMinimizer(graph);
    }

    /** Starts the server on the loopback interface; port 0 picks a free port (see {@link Javalin#port()}). */
    public Javalin start(int port) {
        return start("127.0.0.1", port);
    }

    /**
     * Starts the server bound to {@code host}. The API is unauthenticated and speaks plain
     * HTTP, so bind anything other than loopback only behind a reverse proxy.
     */
    public Javalin start(String host, int port) {
        Javalin app = Javalin.create(config -> {
            config.startup.showJavalinBanner = false;
            config.staticFiles.add("/public", Location.CLASSPATH);
            config.routes.get("/api/services", ctx -> reply(ctx, services()));
            config.routes.get("/api/services/{name}", ctx -> {
                IRI s = Vocabulary.service(ctx.pathParam("name"));
                if (!graph.isService(s)) {
                    throw new IllegalArgumentException("Unknown service: " + ctx.pathParam("name"));
                }
                reply(ctx, service(s));
            });
            config.routes.get("/api/data", ctx -> reply(ctx, data()));
            config.routes.get("/api/validate", ctx -> reply(ctx, validation()));
            config.routes.post("/api/evaluate", ctx -> {
                JsonNode body = body(ctx, Set.of("service", "purpose", "context", "data"), Set.of("service", "data"));
                Decision d = filter.evaluate(service(body), purpose(body), conditions(body), iris(body.path("data")));
                reply(ctx, Map.of("decision", d, "report", d.report()));
            });
            config.routes.post("/api/plan", ctx -> {
                JsonNode body = body(ctx, Set.of("service", "purpose", "context", "held", "includeOptional"), Set.of("service", "held"));
                DisclosurePlan p = planner.plan(service(body), purpose(body), conditions(body),
                        new LinkedHashSet<>(iris(body.path("held"))), body.path("includeOptional").asBoolean(false));
                reply(ctx, Map.of("plan", p, "report", p.report()));
            });
            config.routes.post("/api/minimize", ctx -> {
                JsonNode body = body(ctx, Set.of("service", "purpose", "context", "mapping", "payload", "keepUnmapped"),
                        Set.of("service", "mapping", "payload"));
                if (!(body.path("payload") instanceof ObjectNode payload)) {
                    throw new IllegalArgumentException("payload must be a JSON object");
                }
                if (!body.path("mapping").isObject()) {
                    throw new IllegalArgumentException("mapping must be a JSON object of field name to data type");
                }
                MinimizationResult r = minimizer.minimize(service(body), purpose(body), conditions(body), payload,
                        PayloadMinimizer.parseMapping(body.path("mapping")), body.path("keepUnmapped").asBoolean(false));
                reply(ctx, Map.of("result", r, "report", r.report()));
            });
            config.routes.exception(IllegalArgumentException.class, (e, ctx) -> {
                ctx.status(400);
                ctx.contentType("application/json").result("{\"error\": " + quote(e.getMessage()) + "}");
            });
        });
        return app.start(host, port);
    }

    /** Validation of the (immutable) graph, computed once on first request. */
    private List<Violation> validation() {
        List<Violation> v = validation;
        if (v == null) {
            synchronized (this) {
                if (validation == null) {
                    validation = OntologyValidator.validate(graph);
                }
                v = validation;
            }
        }
        return v;
    }

    private String quote(String s) {
        try {
            return json.writeValueAsString(s == null ? "" : s);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return "\"error\"";
        }
    }

    // -- views ------------------------------------------------------------------

    private List<Map<String, Object>> services() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (IRI s : graph.services()) {
            out.add(service(s));
        }
        return out;
    }

    private Map<String, Object> service(IRI s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", s.getLocalName());
        m.put("iri", s.stringValue());
        m.put("purposes", graph.purposes(s).stream().map(IRI::getLocalName).sorted().toList());
        m.put("conditions", graph.conditionsOf(s).stream().map(IRI::getLocalName).sorted().toList());
        List<Processing> processings = graph.processings(s);
        m.put("processing", processings);
        return m;
    }

    private List<Map<String, Object>> data() {
        Map<IRI, String> group = new LinkedHashMap<>();
        DiagramGenerator.groups(graph).forEach((g, members) -> members.forEach(d -> group.put(d, g)));
        List<Map<String, Object>> out = new ArrayList<>();
        for (IRI d : graph.personalData()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", d.getLocalName());
            m.put("iri", d.stringValue());
            m.put("group", group.getOrDefault(d, DiagramGenerator.OTHER_GROUP));
            m.put("sensitivity", graph.sensitivity(d));
            m.put("cost", graph.disclosureCost(d));
            graph.dpvMatch(d).ifPresent(x -> m.put("dpv", x.stringValue()));
            out.add(m);
        }
        return out;
    }

    // -- request helpers ----------------------------------------------------------

    /**
     * Parses the body and rejects unknown or missing fields, so a misspelled key is a 400
     * instead of a plausible-looking answer computed from an empty list.
     */
    private JsonNode body(Context ctx, Set<String> allowed, Set<String> required) {
        JsonNode node;
        try {
            node = json.readTree(ctx.body());
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            throw new IllegalArgumentException("malformed JSON: " + e.getOriginalMessage());
        }
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("request body must be a JSON object");
        }
        List<String> unknown = new ArrayList<>();
        node.fieldNames().forEachRemaining(f -> {
            if (!allowed.contains(f)) {
                unknown.add(f);
            }
        });
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unknown field(s) " + unknown + "; allowed: " + allowed.stream().sorted().toList());
        }
        for (String r : required) {
            if (!node.has(r)) {
                throw new IllegalArgumentException("missing field: " + r);
            }
        }
        for (String arrayField : List.of("data", "held", "context")) {
            if (node.has(arrayField)) {
                requireStringArray(node.get(arrayField), arrayField);
            }
        }
        if (node.has("purpose") && !node.get("purpose").isNull() && !node.get("purpose").isTextual()) {
            throw new IllegalArgumentException("purpose must be a string or null");
        }
        for (String flag : List.of("includeOptional", "keepUnmapped")) {
            if (node.has(flag) && !node.get(flag).isBoolean()) {
                throw new IllegalArgumentException(flag + " must be a boolean");
            }
        }
        return node;
    }

    private static void requireStringArray(JsonNode node, String field) {
        if (!node.isArray()) {
            throw new IllegalArgumentException(field + " must be an array of names");
        }
        for (JsonNode x : node) {
            if (!x.isTextual() || x.asText().isBlank()) {
                throw new IllegalArgumentException(field + " must contain non-empty strings");
            }
        }
    }

    private static IRI service(JsonNode body) {
        if (!body.hasNonNull("service")) {
            throw new IllegalArgumentException("service is required");
        }
        return Vocabulary.service(body.get("service").asText());
    }

    private static IRI purpose(JsonNode body) {
        return body.hasNonNull("purpose") && !body.get("purpose").asText().isBlank() ? Vocabulary.purpose(body.get("purpose").asText()) : null;
    }

    private static Set<IRI> conditions(JsonNode body) {
        Set<IRI> out = new LinkedHashSet<>();
        body.path("context").forEach(c -> out.add(Vocabulary.condition(c.asText())));
        return out;
    }

    private static List<IRI> iris(JsonNode array) {
        List<IRI> out = new ArrayList<>();
        array.forEach(x -> out.add(Vocabulary.data(x.asText())));
        return out;
    }

    private void reply(Context ctx, Object value) throws Exception {
        ctx.contentType("application/json").result(json.writeValueAsString(value));
    }
}
