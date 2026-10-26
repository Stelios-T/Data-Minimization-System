package dataminimization;

import static dataminimization.Vocabulary.data;
import static dataminimization.Vocabulary.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.eclipse.rdf4j.model.IRI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PayloadMinimizerTest {

    static final Clock TODAY = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);
    static KnowledgeGraph graph;
    static PayloadMinimizer minimizer;
    static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void load() {
        graph = KnowledgeGraph.loadDefault();
        minimizer = new PayloadMinimizer(graph, ValueTransformers.standard(graph, TODAY));
    }

    @AfterAll
    static void close() {
        graph.close();
    }

    private static ObjectNode read(String file) throws Exception {
        return (ObjectNode) JSON.readTree(Files.readString(Path.of("examples", file)));
    }

    private static Map<String, IRI> mapping(String file) throws Exception {
        return PayloadMinimizer.parseMapping(JSON.readTree(Files.readString(Path.of("examples", file))));
    }

    @Test
    void signupPayloadKeepsEmailExtractsNamesDropsTheRest() throws Exception {
        MinimizationResult r = minimizer.minimize(service("SubscriptionSignUp"), null, Set.of(),
                read("signup-payload.json"), mapping("signup-mapping.json"), false);
        ObjectNode out = r.minimized();
        assertEquals("maria@example.org", out.get("email").asText());
        assertEquals("Maria", out.get("firstName").asText());
        assertEquals("Papadopoulou", out.get("lastName").asText());
        assertNull(out.get("id_card"));
        assertNull(out.get("is_adult"));
        assertNull(out.get("newsletter"));
        assertEquals(3, out.size());
        assertTrue(r.isComplete());
        assertEquals(Verdict.EXCESSIVE, r.decision().verdict());
        assertTrue(r.actions().stream().anyMatch(a -> a.field().equals("id_card") && a.action() == MinimizationResult.Action.TRANSFORM));
    }

    @Test
    void checkoutPayloadDerivesAdultFlagAndKeepsAccount() throws Exception {
        MinimizationResult r = minimizer.minimize(service("CheckoutPayAdult"), null, Set.of(),
                read("checkout-payload.json"), mapping("checkout-mapping.json"), false);
        ObjectNode out = r.minimized();
        assertTrue(out.get("isAdult").asBoolean());
        assertNull(out.get("dob"));
        assertEquals("GR16 0110 1250 0000 0001 2300 695", out.get("account").get("accountNumber").asText());
        assertNull(out.get("street_address"));
        assertTrue(r.isComplete());
    }

    @Test
    void minorIsNotAnAdult() throws Exception {
        ObjectNode payload = JSON.createObjectNode().put("dob", "2010-01-01");
        MinimizationResult r = minimizer.minimize(service("EntryAllowance"), null, Set.of(), payload,
                Map.of("dob", data("DateOfBirth")), false);
        assertFalse(r.minimized().get("isAdult").asBoolean());
    }

    @Test
    void undeterminableValueIsRequestedNotInvented() throws Exception {
        ObjectNode payload = JSON.createObjectNode().put("dob", "not a date");
        MinimizationResult r = minimizer.minimize(service("EntryAllowance"), null, Set.of(), payload,
                Map.of("dob", data("DateOfBirth")), false);
        assertEquals(0, r.minimized().size());
        assertEquals(Set.of(data("IsAdult")), r.missing());
        assertTrue(r.actions().stream().anyMatch(a -> a.action() == MinimizationResult.Action.REQUEST));
    }

    @Test
    void unmappedFieldsCanBeKept() throws Exception {
        ObjectNode payload = JSON.createObjectNode().put("email", "a@b.c").put("note", "hi");
        Map<String, IRI> m = Map.of("email", data("Email"));
        assertNull(minimizer.minimize(service("SubscriptionPay"), null, Set.of(), payload, m, false).minimized().get("note"));
        assertEquals("hi", minimizer.minimize(service("SubscriptionPay"), null, Set.of(), payload, m, true).minimized().get("note").asText());
    }

    @Test
    void fieldNamesFollowLowerCamelCase() {
        assertEquals("isAdult", ValueTransformers.fieldName(data("IsAdult")));
        assertEquals("idCard", ValueTransformers.fieldName(data("IDCard")));
        assertEquals("idNumber", ValueTransformers.fieldName(data("IDNumber")));
        assertEquals("fullName", ValueTransformers.fieldName(data("FullName")));
    }
}
