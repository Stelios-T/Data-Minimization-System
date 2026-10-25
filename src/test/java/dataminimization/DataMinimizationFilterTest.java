package dataminimization;

import static dataminimization.Vocabulary.data;
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

class DataMinimizationFilterTest {

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

    private static DataAssessment of(Decision d, String localName) {
        IRI iri = data(localName);
        return d.assessments().stream().filter(a -> a.datum().equals(iri)).findFirst().orElseThrow();
    }

    @Test
    void shippedExampleFlagsSupersetAndExcess() {
        Decision d = filter.evaluate(service("SubscriptionSignUp"), List.of(data("IDCard"), data("Email"), data("IsAdult")));

        DataAssessment idCard = of(d, "IDCard");
        assertEquals(Status.SUPERSET, idCard.status());
        assertEquals(Set.of(data("FirstName"), data("LastName")), idCard.substitutes());
        assertTrue(idCard.exposes().containsAll(Set.of(data("IDNumber"), data("DateOfBirth"), data("MotherName"))));

        assertEquals(Status.REQUIRED, of(d, "Email").status());
        assertEquals(Status.EXCESSIVE, of(d, "IsAdult").status());
        assertTrue(d.missing().isEmpty());
        assertEquals(Verdict.EXCESSIVE, d.verdict());
        assertEquals(2, d.excessive().size());
    }

    @Test
    void dateOfBirthIsSupersetOfIsAdult() {
        Decision d = filter.evaluate(service("CheckoutPayAdult"), List.of(data("DateOfBirth"), data("Account")));

        DataAssessment dob = of(d, "DateOfBirth");
        assertEquals(Status.SUPERSET, dob.status());
        assertEquals(Set.of(data("IsAdult")), dob.substitutes());

        // Account decomposes into exactly the two required items, so it is acceptable.
        assertEquals(Status.SATISFIES, of(d, "Account").status());
        assertTrue(d.missing().isEmpty());
        assertEquals(Verdict.EXCESSIVE, d.verdict());
    }

    @Test
    void specialisationSatisfiesCategoryRequirement() {
        // Email is a Contact, so it covers Contact without disclosing anything else.
        assertTrue(graph.covers(data("Email")).contains(data("Contact")));
        assertTrue(graph.exposes(data("Email")).isEmpty());
    }

    @Test
    void overlappingProvidedDataAreNotReportedTwice() {
        Decision d = filter.evaluate(service("SubscriptionSignUp"), List.of(data("IDCard"), data("Passport")));
        assertEquals(2, d.assessments().size());
        assertEquals(Set.of(data("Email")), d.missing());
        assertEquals(Verdict.INCOMPLETE_AND_EXCESSIVE, d.verdict());
    }

    @Test
    void categoryDoesNotStandInForItsMembers() {
        Decision d = filter.evaluate(service("EntryAllowance"), List.of(data("Identity")));
        assertEquals(Status.EXCESSIVE, of(d, "Identity").status());
        assertEquals(Set.of(data("IsAdult")), d.missing());
        assertEquals(Verdict.INCOMPLETE_AND_EXCESSIVE, d.verdict());
    }

    @Test
    void missingRequiredDataAreReported() {
        Decision d = filter.evaluate(service("AirportCheckIn"), List.of(data("FirstName"), data("PassportNumber")));
        assertEquals(Set.of(data("LastName")), d.missing());
        assertEquals(Verdict.INCOMPLETE, d.verdict());
    }

    @Test
    void unknownServiceIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> filter.evaluate(service("Nope"), List.of(data("Email"))));
    }

    @Test
    void unknownDatumIsFlagged() {
        Decision d = filter.evaluate(service("SubscriptionSignUp"),
                List.of(data("FirstName"), data("LastName"), data("Email"), data("Foo")));
        assertEquals(Status.UNKNOWN, of(d, "Foo").status());
        assertEquals(Verdict.EXCESSIVE, d.verdict());
    }

    @Test
    void exactlyTheNeededDataIsCompliant() {
        Decision d = filter.evaluate(service("SubscriptionSignUp"),
                List.of(data("FirstName"), data("LastName"), data("Email")));
        assertEquals(Verdict.COMPLIANT, d.verdict());
        assertTrue(d.isCompliant());

        Decision withOptional = filter.evaluate(service("SubscriptionSignUp"),
                List.of(data("FirstName"), data("LastName"), data("Email"), data("TelephoneNumber")));
        assertEquals(Status.OPTIONAL, of(withOptional, "TelephoneNumber").status());
        assertEquals(Verdict.COMPLIANT, withOptional.verdict());
    }

    @Test
    void duplicateProvidedDataCollapse() {
        Decision d = filter.evaluate(service("EntryAllowance"), List.of(data("IsAdult"), data("IsAdult")));
        assertEquals(1, d.assessments().size());
        assertEquals(Verdict.COMPLIANT, d.verdict());
    }

    @Test
    void reportMentionsSubstitutesAndVerdict() {
        Decision d = filter.evaluate(service("SubscriptionSignUp"), List.of(data("IDCard"), data("Email"), data("IsAdult")));
        String report = d.report();
        assertTrue(report.contains("request FirstName, LastName instead"), report);
        assertTrue(report.contains("Verdict: EXCESSIVE"), report);
        assertTrue(report.contains("Missing required data: none"), report);
    }
}
