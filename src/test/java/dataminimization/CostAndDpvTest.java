package dataminimization;

import static dataminimization.Vocabulary.data;
import static dataminimization.Vocabulary.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.rdf4j.model.IRI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CostAndDpvTest {

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
    void everyDatumHasASensitivityAndContainersCostMore() {
        for (IRI d : graph.personalData()) {
            int s = graph.sensitivity(d);
            assertTrue(s >= 1 && s <= 5, d + " sensitivity " + s);
        }
        int idCard = graph.disclosureCost(data("IDCard"));
        int fullName = graph.disclosureCost(data("FullName"));
        int names = graph.disclosureCost(data("FirstName")) + graph.disclosureCost(data("LastName"));
        assertTrue(idCard > fullName && fullName > names, idCard + " > " + fullName + " > " + names);
    }

    @Test
    void minimalDisclosureIsTheCheapestCover() {
        Decision d = filter.evaluate(service("SubscriptionSignUp"), List.of(data("IDCard"), data("Email"), data("IsAdult")));
        assertEquals(Set.of(data("FirstName"), data("LastName"), data("Email")), d.minimalDisclosure());
        assertEquals(4, d.minimalCost());
        assertTrue(d.offeredCost() > d.minimalCost());

        DataAssessment idCard = d.assessments().get(0);
        assertEquals(Status.SUPERSET, idCard.status());
        assertEquals(Set.of(data("FirstName"), data("LastName")), idCard.alternatives().get(0).chosen());
        assertEquals(2, idCard.alternatives().get(0).cost());
        assertTrue(idCard.alternatives().stream().anyMatch(c -> c.chosen().equals(Set.of(data("FullName")))));
    }

    @Test
    void providersIncludeContainersAndDerivationSources() {
        Set<IRI> providers = graph.providersOf(data("IsAdult"));
        assertTrue(providers.containsAll(Set.of(data("IsAdult"), data("ExactAge"), data("DateOfBirth"), data("IDCard"), data("Passport"))));
        assertTrue(!providers.contains(data("Age")), "a category does not provide its members");
    }

    @Test
    void setCoverExactAndGreedyAgreeOnSmallInputs() {
        IRI a = data("A"), b = data("B"), c = data("C"), ab = data("AB"), abc = data("ABC");
        Map<IRI, Set<IRI>> covers = Map.of(a, Set.of(a), b, Set.of(b), c, Set.of(c), ab, Set.of(a, b), abc, Set.of(a, b, c));
        Map<IRI, Integer> cost = Map.of(a, 1, b, 1, c, 1, ab, 3, abc, 2);
        List<SetCover.Cover> best = SetCover.cheapest(Set.of(a, b, c), covers, cost::get, 3);
        assertEquals(Set.of(abc), best.get(0).chosen());
        assertEquals(2, best.get(0).cost());
        assertEquals(Set.of(a, b, c), best.get(1).chosen());
        assertTrue(SetCover.cheapest(Set.of(data("Z")), covers, cost::get, 1).isEmpty(), "uncoverable");
    }

    @Test
    void dpvMappingsPointToRealDpvPdTerms() throws IOException {
        Set<String> terms = new HashSet<>();
        try (InputStream in = getClass().getResourceAsStream("/dpv-pd-terms.txt")) {
            terms.addAll(List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")));
        }
        int mapped = 0;
        for (IRI d : graph.personalData()) {
            Optional<IRI> m = graph.dpvMatch(d);
            if (m.isPresent()) {
                mapped++;
                assertEquals(Vocabulary.DPV_PD_NS, m.get().getNamespace(), d.toString());
                assertTrue(terms.contains(m.get().getLocalName()), m.get() + " is not a DPV-PD term");
            }
        }
        assertTrue(mapped >= 25, "mapped " + mapped);
        assertEquals(Optional.of(Vocabulary.data("Email")).map(x -> "EmailAddress"), graph.dpvMatch(data("Email")).map(IRI::getLocalName));
    }
}
