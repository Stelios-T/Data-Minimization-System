package dataminimization;

import static dataminimization.Vocabulary.data;
import static dataminimization.Vocabulary.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class KnowledgeGraphTest {

    static KnowledgeGraph graph;

    @BeforeAll
    static void load() {
        graph = KnowledgeGraph.loadDefault();
    }

    @AfterAll
    static void close() {
        graph.close();
    }

    @Test
    void bundledModelContainsAllTypesAndServices() {
        assertEquals(33, graph.personalData().size());
        assertEquals(13, graph.services().size());
        assertTrue(graph.isPersonalData(data("IDCard")));
        assertTrue(graph.isService(service("SubscriptionSignUp")));
        assertFalse(graph.isPersonalData(data("Foo")));
        assertFalse(graph.isService(service("Nope")));
    }

    @Test
    void idCardDisclosesItsPartsTransitively() {
        Set<?> covers = graph.covers(data("IDCard"));
        assertTrue(covers.containsAll(Set.of(data("IDCard"), data("FullName"), data("FirstName"), data("LastName"),
                data("DateOfBirth"), data("YearOfBirth"), data("IsAdult"), data("Identity"))));
        assertFalse(covers.contains(data("Email")));
    }

    @Test
    void aCategoryDoesNotDiscloseItsMembers() {
        // Having "a contact" does not mean having an email address. The old implementation got this backwards.
        assertEquals(Set.of(data("Contact")), graph.covers(data("Contact")));
        assertFalse(graph.covers(data("Identity")).contains(data("IsAdult")));
    }

    @Test
    void aSpecialisationSatisfiesItsCategory() {
        assertTrue(graph.covers(data("Email")).contains(data("Contact")));
        assertTrue(graph.exposes(data("Email")).isEmpty());
    }

    @Test
    void derivationIsTransitive() {
        assertTrue(graph.covers(data("DateOfBirth")).containsAll(Set.of(data("ExactAge"), data("IsAdult"))));
        assertTrue(graph.exposes(data("DateOfBirth")).containsAll(Set.of(data("ExactAge"), data("IsAdult"),
                data("DayOfBirth"))));
        assertFalse(graph.exposes(data("DateOfBirth")).contains(data("Age")), "categories are not exposure");
    }

    @Test
    void unsoundDerivationEdgesWereRemoved() {
        assertFalse(graph.covers(data("LastName")).contains(data("FirstName")));
        assertFalse(graph.covers(data("AccountBalance")).contains(data("AccountNumber")));
    }

    @Test
    void airportCheckInRequiresLastName() {
        Set<?> required = graph.required(service("AirportCheckIn"));
        assertEquals(Set.of(data("PassportNumber"), data("FirstName"), data("LastName")), required);
        assertEquals(Set.of(data("IDNumber")), graph.optional(service("AirportCheckIn")));
    }
}
