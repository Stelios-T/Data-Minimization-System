package dataminimization;

import static dataminimization.Vocabulary.data;
import static dataminimization.Vocabulary.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.vocabulary.PROV;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OutputTest {

    static KnowledgeGraph graph;
    static Decision decision;

    @BeforeAll
    static void load() {
        graph = KnowledgeGraph.loadDefault();
        decision = new DataMinimizationFilter(graph).evaluate(service("SubscriptionSignUp"), List.of(data("IDCard"), data("Email"), data("IsAdult")));
    }

    @AfterAll
    static void close() {
        graph.close();
    }

    @Test
    void decisionSerialisesToJsonWithNamesAndDpvLinks() throws Exception {
        ObjectMapper mapper = JsonOutput.mapper(graph);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(decision));
        assertEquals("EXCESSIVE", json.get("verdict").asText());
        assertEquals("SubscriptionSignUp", json.get("service").get("name").asText());
        JsonNode email = json.get("assessments").get(1);
        assertEquals("Email", email.get("datum").get("name").asText());
        assertEquals("https://w3id.org/dpv/pd#EmailAddress", email.get("datum").get("dpv").asText());
        assertEquals("REQUIRED", email.get("status").asText());
        assertTrue(json.get("requirements").get("required").isArray());

        DisclosurePlan plan = new DisclosurePlanner(graph).plan(service("CheckoutPayAdult"), null, java.util.Set.of(),
                java.util.Set.of(data("DateOfBirth"), data("Account")), false);
        JsonNode p = mapper.readTree(mapper.writeValueAsString(plan));
        assertEquals(3, p.get("steps").size());
    }

    @Test
    void jsonContractIsExplicitAndOrdered() throws Exception {
        ObjectMapper mapper = JsonOutput.mapper(graph);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(decision));
        java.util.Set<String> fields = new java.util.TreeSet<>();
        json.fieldNames().forEachRemaining(fields::add);
        assertEquals(java.util.Set.of("assessments", "compliant", "conditions", "minimalCost", "minimalDisclosure", "missing",
                "offeredCost", "purpose", "purposes", "requirements", "service", "verdict"), fields);
        java.util.Set<String> assessment = new java.util.TreeSet<>();
        json.get("assessments").get(0).fieldNames().forEachRemaining(assessment::add);
        assertEquals(java.util.Set.of("alternatives", "because", "cost", "covers", "datum", "exposes", "status", "substitutes"), assessment);

        // IRI collections are sorted by local name, so output is identical across JVM runs
        java.util.List<String> exposes = new java.util.ArrayList<>();
        json.get("assessments").get(0).get("exposes").forEach(n -> exposes.add(n.get("name").asText()));
        assertEquals(exposes.stream().sorted().toList(), exposes);
        java.util.List<String> required = new java.util.ArrayList<>();
        json.get("requirements").get("required").forEach(n -> required.add(n.get("name").asText()));
        assertEquals(java.util.List.of("Email", "FirstName", "LastName"), required);

        DisclosurePlan plan = new DisclosurePlanner(graph).plan(service("CheckoutPayAdult"), null, java.util.Set.of(),
                java.util.Set.of(data("DateOfBirth"), data("Account")), false);
        java.util.Set<String> planFields = new java.util.TreeSet<>();
        mapper.readTree(mapper.writeValueAsString(plan)).fieldNames().forEachRemaining(planFields::add);
        assertEquals(java.util.Set.of("complete", "conditions", "disclosed", "held", "naiveCost", "planCost", "purpose",
                "requirements", "service", "steps", "unknown", "unsatisfiable"), planFields);
    }

    @Test
    void decisionSerialisesToProvenanceTurtle() throws Exception {
        StringWriter w = new StringWriter();
        ProvenanceWriter.write(decision, Instant.parse("2026-10-25T10:00:00Z"), Instant.parse("2026-10-25T10:00:01Z"), w);
        Model m = Rio.parse(new StringReader(w.toString()), "", RDFFormat.TURTLE);
        assertEquals(1, m.filter(null, RDF.TYPE, PROV.ACTIVITY).size());
        assertEquals(1, m.filter(null, PROV.GENERATED, null).size());
        assertEquals(3, m.filter(null, org.eclipse.rdf4j.model.util.Values.iri(ProvenanceWriter.NS, "assessment"), null).size());
        assertTrue(w.toString().contains("\"EXCESSIVE\""), w.toString());
    }

    @Test
    void committedDiagramsAreFresh() throws Exception {
        String generated = DiagramGenerator.markdown(graph);
        assertTrue(generated.contains("```mermaid"));
        assertTrue(generated.contains("IDNumber -->|isPartOf| IDCard"), generated);
        Path committed = Path.of("docs", "model.md");
        assertTrue(Files.exists(committed), "run: java -jar target/dms.jar --diagrams");
        assertEquals(Files.readString(committed), generated, "docs/model.md is stale; run: java -jar target/dms.jar --diagrams");
    }
}
