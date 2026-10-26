package dataminimization;

import static dataminimization.Vocabulary.condition;
import static dataminimization.Vocabulary.data;
import static dataminimization.Vocabulary.purpose;
import static dataminimization.Vocabulary.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.eclipse.rdf4j.model.IRI;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PurposeAndConditionTest {

    static KnowledgeGraph graph;
    static DataMinimizationFilter filter;

    @BeforeAll
    static void load() {
        graph = KnowledgeGraph.loadDefault();
        filter = new DataMinimizationFilter(graph);
    }

    @AfterAll
    static void close() {
        graph.close();
    }

    @Test
    void servicesDeclarePurposes() {
        assertEquals(Set.of(purpose("AccountCreation"), purpose("CustomerSupport")), graph.purposes(service("SubscriptionSignUp")));
        assertEquals(Set.of(purpose("Payment"), purpose("AgeVerification")), graph.purposes(service("CheckoutPayAdult")));
        assertEquals(graph.purposes().size(), graph.services().stream().flatMap(s -> graph.purposes(s).stream()).distinct().count(),
                "every declared purpose is used by some service");
    }

    @Test
    void purposeRestrictsNeeds() {
        Requirements payment = graph.requirements(service("CheckoutPayAdult"), purpose("Payment"), Set.of());
        assertEquals(Set.of(data("AccountNumber"), data("AccountBalance")), payment.required());

        Requirements all = graph.requirements(service("CheckoutPayAdult"), null, Set.of());
        assertEquals(Set.of(data("AccountNumber"), data("AccountBalance"), data("IsAdult")), all.required());
    }

    @Test
    void evaluationForOnePurposeIgnoresTheOthers() {
        Decision d = filter.evaluate(service("CheckoutPayAdult"), purpose("Payment"), Set.of(),
                List.of(data("AccountNumber"), data("AccountBalance")));
        assertEquals(Verdict.COMPLIANT, d.verdict());

        Decision all = filter.evaluate(service("CheckoutPayAdult"), List.of(data("AccountNumber"), data("AccountBalance")));
        assertEquals(Set.of(data("IsAdult")), all.missing());
        assertEquals(Verdict.INCOMPLETE, all.verdict());
    }

    @Test
    void unknownPurposeOrConditionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> filter.evaluate(service("CheckoutPayAdult"), purpose("LocalSearch"), Set.of(), List.of(data("IsAdult"))));
        assertThrows(IllegalArgumentException.class,
                () -> filter.evaluate(service("RestaurantFinder"), null, Set.of(condition("Nope")), List.of(data("City"))));
    }

    @Test
    void conditionalRequirementActivatesWithContext() {
        Decision without = filter.evaluate(service("RestaurantFinder"), null, Set.of(), List.of(data("City")));
        assertEquals(Verdict.COMPLIANT, without.verdict());
        assertEquals(Set.of(data("IsAdult")), without.requirements().inactive().keySet());

        Decision with = filter.evaluate(service("RestaurantFinder"), null, Set.of(condition("ContainsAlcohol")), List.of(data("City")));
        assertEquals(Set.of(data("IsAdult")), with.missing());
        assertEquals(Verdict.INCOMPLETE, with.verdict());
        assertTrue(with.report().contains("IsAdult (when ContainsAlcohol)"), with.report());

        Decision satisfied = filter.evaluate(service("RestaurantFinder"), null, Set.of(condition("ContainsAlcohol")),
                List.of(data("City"), data("IsAdult")));
        assertEquals(Verdict.COMPLIANT, satisfied.verdict());
        assertEquals(Set.of(condition("ContainsAlcohol")), satisfied.assessments().get(1).because());
    }

    @Test
    void aDatumRequiredUnderTwoConditionsKeepsBoth() throws Exception {
        try (java.io.InputStream in = getClass().getResourceAsStream("/models/two-conditions.ttl");
             KnowledgeGraph g = KnowledgeGraph.load(in, org.eclipse.rdf4j.rio.RDFFormat.TURTLE)) {
            IRI s = service("S"), d = data("D"), a = condition("CondA"), b = condition("CondB");
            Requirements none = g.requirements(s, null, Set.of());
            assertEquals(Set.of(a, b), none.inactive().get(d));
            assertTrue(!none.required().contains(d));

            Requirements onlyA = g.requirements(s, null, Set.of(a));
            assertTrue(onlyA.required().contains(d));
            assertEquals(Set.of(a), onlyA.conditional().get(d));
            assertTrue(onlyA.inactive().isEmpty());

            Requirements both = g.requirements(s, null, Set.of(a, b));
            assertEquals(Set.of(a, b), both.conditional().get(d));
            Decision dec = new DataMinimizationFilter(g).evaluate(s, null, Set.of(a, b), List.of(data("A")));
            assertTrue(dec.report().contains("D (when CondA or CondB)"), dec.report());
        }
    }

    @Test
    void conditionalDatumIsExcessiveWhenConditionIsInactive() {
        Decision d = filter.evaluate(service("RestaurantFinder"), null, Set.of(), List.of(data("City"), data("IsAdult")));
        assertEquals(Status.EXCESSIVE, d.assessments().get(1).status());
    }

    @Test
    void listingShowsConditions() {
        assertEquals(Set.of(condition("ContainsAlcohol")), graph.conditionsOf(service("RestaurantFinder")));
        assertEquals(graph.conditions().size(), graph.services().stream().flatMap(s -> graph.conditionsOf(s).stream()).distinct().count(),
                "every declared condition is used by some service");
    }
}
