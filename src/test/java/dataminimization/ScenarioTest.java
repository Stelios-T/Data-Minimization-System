package dataminimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dataminimization.eval.Scenario;
import org.eclipse.rdf4j.model.IRI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Every labelled scenario in scenarios.json must produce exactly the expected decision. */
class ScenarioTest {

    static KnowledgeGraph graph;
    static DataMinimizationFilter filter;
    static DisclosurePlanner planner;
    static Scenario.Corpus corpus;

    @BeforeAll
    static void load() {
        graph = KnowledgeGraph.loadDefault();
        filter = new DataMinimizationFilter(graph);
        planner = new DisclosurePlanner(graph);
        corpus = Scenario.loadDefault();
    }

    @AfterAll
    static void close() {
        graph.close();
    }

    static Stream<Scenario> evaluations() {
        return corpus.evaluations().stream();
    }

    static Stream<Scenario.Plan> plans() {
        return corpus.plans().stream();
    }

    @Test
    void corpusIsLargeEnough() {
        assertTrue(corpus.evaluations().size() >= 30, "evaluations: " + corpus.evaluations().size());
        assertTrue(corpus.plans().size() >= 5, "plans: " + corpus.plans().size());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("evaluations")
    void scenarioMatchesExpectation(Scenario s) {
        Decision d = filter.evaluate(s.service(), s.purpose(), s.context(), s.provided());
        Map<IRI, Status> statuses = d.assessments().stream().collect(Collectors.toMap(DataAssessment::datum, DataAssessment::status));
        s.expectedStatuses().forEach((datum, status) -> assertEquals(status, statuses.get(datum), s.id() + " status of " + datum.getLocalName()));
        assertEquals(s.expectedMissing(), d.missing(), s.id() + " missing");
        assertEquals(s.expectedVerdict(), d.verdict(), s.id() + " verdict");
        s.expectedSubstitutes().forEach((datum, subs) -> {
            Set<IRI> actual = d.assessments().stream().filter(a -> a.datum().equals(datum)).findFirst().orElseThrow().substitutes();
            assertEquals(subs, actual, s.id() + " substitutes for " + datum.getLocalName());
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("plans")
    void planMatchesExpectation(Scenario.Plan p) {
        DisclosurePlan plan = planner.plan(p.service(), p.purpose(), p.context(), p.held(), p.includeOptional());
        assertEquals(p.expectedDisclosed(), plan.disclosed(), p.id() + " disclosed");
        assertEquals(p.expectedUnsatisfiable(), plan.unsatisfiable(), p.id() + " unsatisfiable");
    }
}
