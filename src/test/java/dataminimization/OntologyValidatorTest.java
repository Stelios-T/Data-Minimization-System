package dataminimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OntologyValidatorTest {

    @Test
    void bundledModelIsClean() {
        try (KnowledgeGraph graph = KnowledgeGraph.loadDefault()) {
            List<Violation> violations = OntologyValidator.validate(graph);
            assertEquals(List.of(), violations, violations.toString());
        }
    }

    static Stream<Arguments> badModels() {
        return Stream.of(
                Arguments.of("cycle-partof", "CYCLE_PART_OF"),
                Arguments.of("cycle-derivation", "CYCLE_DERIVATION"),
                Arguments.of("redundant", "REDUNDANT_REQUIREMENT"),
                Arguments.of("optional-covered", "OPTIONAL_ALREADY_COVERED"),
                Arguments.of("missing-sensitivity", "SHACL:MinCountConstraintComponent"),
                Arguments.of("dangling", "DANGLING_REFERENCE,SHACL:ClassConstraintComponent"),
                Arguments.of("no-purpose", "SHACL:MinCountConstraintComponent"),
                Arguments.of("unused-processing", "UNUSED_PROCESSING"),
                Arguments.of("service-without-needs", "SERVICE_WITHOUT_NEEDS"),
                Arguments.of("condition-missing", "SHACL:MinCountConstraintComponent"));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("badModels")
    void badModelTriggersExpectedCode(String fixture, String code) throws IOException {
        try (java.io.InputStream in = getClass().getResourceAsStream("/bad-models/" + fixture + ".ttl");
             KnowledgeGraph graph = KnowledgeGraph.load(in, org.eclipse.rdf4j.rio.RDFFormat.TURTLE)) {
            List<Violation> violations = OntologyValidator.validate(graph);
            assertEquals(Set.of(code.split(",")), violations.stream().map(Violation::code).collect(Collectors.toSet()),
                    fixture + " produced " + violations);
        }
    }

    @Test
    void warningsDoNotMakeTheGraphUnclean() {
        List<Violation> onlyWarnings = List.of(new Violation(Violation.Severity.WARNING, "X", "x", java.util.Set.of()));
        assertTrue(OntologyValidator.isClean(onlyWarnings));
        assertFalse(OntologyValidator.isClean(List.of(new Violation(Violation.Severity.ERROR, "X", "x", java.util.Set.of()))));
    }
}
