package dataminimization;

import static dataminimization.Vocabulary.condition;
import static dataminimization.Vocabulary.data;
import static dataminimization.Vocabulary.purpose;
import static dataminimization.Vocabulary.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.eclipse.rdf4j.model.IRI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DisclosurePlannerTest {

    static KnowledgeGraph graph;
    static DisclosurePlanner planner;

    @BeforeAll
    static void load() {
        graph = KnowledgeGraph.loadDefault();
        planner = new DisclosurePlanner(graph);
    }

    @AfterAll
    static void close() {
        graph.close();
    }

    private static DisclosurePlan.Step step(DisclosurePlan p, String to) {
        IRI iri = data(to);
        return p.steps().stream().filter(s -> s.to().equals(iri)).findFirst().orElseThrow();
    }

    @Test
    void extractsNamesFromIdCardAndSendsEmailAsIs() {
        DisclosurePlan p = planner.plan(service("SubscriptionSignUp"), null, Set.of(), Set.of(data("IDCard"), data("Email")), false);
        assertTrue(p.isComplete());
        assertEquals(DisclosurePlan.Kind.EXTRACT, step(p, "FirstName").kind());
        assertEquals(List.of(data("IDCard"), data("FullName"), data("FirstName")), step(p, "FirstName").path());
        assertEquals(DisclosurePlan.Kind.EXTRACT, step(p, "LastName").kind());
        assertEquals(DisclosurePlan.Kind.DISCLOSE, step(p, "Email").kind());
        assertEquals(Set.of(data("FirstName"), data("LastName"), data("Email")), p.disclosed());
        assertTrue(p.planCost() < p.naiveCost(), p.planCost() + " < " + p.naiveCost());
    }

    @Test
    void derivesAdultFlagAndExtractsAccountParts() {
        DisclosurePlan p = planner.plan(service("CheckoutPayAdult"), null, Set.of(), Set.of(data("DateOfBirth"), data("Account")), false);
        assertTrue(p.isComplete());
        assertEquals(DisclosurePlan.Kind.DERIVE, step(p, "IsAdult").kind());
        assertEquals(List.of(data("DateOfBirth"), data("ExactAge"), data("IsAdult")), step(p, "IsAdult").path());
        assertEquals(DisclosurePlan.Kind.EXTRACT, step(p, "AccountNumber").kind());
        assertEquals(DisclosurePlan.Kind.EXTRACT, step(p, "AccountBalance").kind());
        assertTrue(p.report().contains("derive IsAdult from DateOfBirth via ExactAge locally"), p.report());
    }

    @Test
    void generalisesStreetAddressToCity() {
        DisclosurePlan p = planner.plan(service("LocalInfoFinder"), null, Set.of(), Set.of(data("StreetAddress")), false);
        assertEquals(DisclosurePlan.Kind.DERIVE, step(p, "City").kind());
        assertEquals(List.of(data("StreetAddress"), data("City")), step(p, "City").path());
    }

    @Test
    void categoryCannotProvideItsMembers() {
        DisclosurePlan p = planner.plan(service("SubscriptionSignUp"), null, Set.of(), Set.of(data("Contact")), false);
        assertEquals(Set.of(data("FirstName"), data("LastName"), data("Email")), p.unsatisfiable());
        assertTrue(p.steps().isEmpty());
    }

    @Test
    void prefersTheShortestPathAndHonoursPurposeAndContext() {
        DisclosurePlan p = planner.plan(service("CheckoutPayAdult"), purpose("AgeVerification"), Set.of(),
                Set.of(data("IDCard"), data("ExactAge")), false);
        assertEquals(1, p.steps().size());
        assertEquals(data("ExactAge"), step(p, "IsAdult").from());

        DisclosurePlan r = planner.plan(service("RestaurantFinder"), null, Set.of(condition("ContainsAlcohol")),
                Set.of(data("StreetAddress"), data("Passport")), false);
        assertTrue(r.isComplete());
        assertEquals(DisclosurePlan.Kind.DERIVE, step(r, "IsAdult").kind());
        assertEquals(data("Passport"), step(r, "IsAdult").from());
    }

    @Test
    void unknownHeldDataAreReportedNotSilentlyDropped() {
        DisclosurePlan p = planner.plan(service("EntryAllowance"), null, Set.of(), Set.of(data("Foo"), data("IsAdult")), false);
        assertEquals(Set.of(data("Foo")), p.unknown());
        assertTrue(p.isComplete());
        assertTrue(p.report().contains("ignored:     Foo"), p.report());
    }

    @Test
    void optionalDataIncludedOnlyWhenAsked() {
        Set<IRI> held = Set.of(data("FirstName"), data("LastName"), data("Email"), data("TelephoneNumber"));
        assertEquals(3, planner.plan(service("SubscriptionSignUp"), null, Set.of(), held, false).steps().size());
        DisclosurePlan with = planner.plan(service("SubscriptionSignUp"), null, Set.of(), held, true);
        assertEquals(4, with.steps().size());
        assertTrue(step(with, "TelephoneNumber").optional());
    }
}
