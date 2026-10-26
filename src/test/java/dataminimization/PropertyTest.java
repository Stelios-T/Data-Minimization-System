package dataminimization;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterContainer;
import org.eclipse.rdf4j.model.IRI;

/** Laws that must hold for every service, purpose, context and offered set, checked on random inputs with jqwik. */
class PropertyTest {

    static final KnowledgeGraph GRAPH = KnowledgeGraph.loadDefault();
    static final DataMinimizationFilter FILTER = new DataMinimizationFilter(GRAPH);
    static final DisclosurePlanner PLANNER = new DisclosurePlanner(GRAPH);

    @AfterContainer
    static void close() {
        GRAPH.close();
    }

    /** A service with one of its purposes (or none) and a random subset of the declared conditions. */
    record Context(IRI service, IRI purpose, Set<IRI> conditions) {
    }

    @Provide
    Arbitrary<Context> contexts() {
        List<IRI> services = new ArrayList<>(GRAPH.services());
        return Arbitraries.of(services).flatMap(s -> {
            List<IRI> purposes = new ArrayList<>(GRAPH.purposes(s));
            purposes.add(null);
            return Combinators.combine(Arbitraries.of(purposes), Arbitraries.subsetOf(GRAPH.conditions()))
                    .as((p, c) -> new Context(s, p, c));
        });
    }

    @Provide
    Arbitrary<Set<IRI>> offered() {
        return Arbitraries.subsetOf(GRAPH.personalData()).ofMaxSize(6);
    }

    @Provide
    Arbitrary<IRI> datum() {
        return Arbitraries.of(new ArrayList<>(GRAPH.personalData()));
    }

    @Property(tries = 300)
    boolean exactlyTheRequiredSetIsCompliant(@ForAll("contexts") Context c) {
        Set<IRI> required = GRAPH.requirements(c.service(), c.purpose(), c.conditions()).required();
        return FILTER.evaluate(c.service(), c.purpose(), c.conditions(), required).verdict() == Verdict.COMPLIANT;
    }

    @Property(tries = 300)
    boolean addingDataNeverGrowsTheMissingSet(@ForAll("contexts") Context c, @ForAll("offered") Set<IRI> offered, @ForAll("datum") IRI extra) {
        Set<IRI> before = FILTER.evaluate(c.service(), c.purpose(), c.conditions(), offered).missing();
        Set<IRI> more = new HashSet<>(offered);
        more.add(extra);
        Set<IRI> after = FILTER.evaluate(c.service(), c.purpose(), c.conditions(), more).missing();
        return before.containsAll(after);
    }

    @Property(tries = 300)
    boolean compliantMeansNothingMissingAndEverythingMinimal(@ForAll("contexts") Context c, @ForAll("offered") Set<IRI> offered) {
        Decision d = FILTER.evaluate(c.service(), c.purpose(), c.conditions(), offered);
        boolean expected = d.missing().isEmpty() && d.assessments().stream().allMatch(a -> a.status().isMinimal());
        return (d.verdict() == Verdict.COMPLIANT) == expected;
    }

    @Property(tries = 300)
    boolean statusesAreConsistentWithCoverage(@ForAll("contexts") Context c, @ForAll("offered") Set<IRI> offered) {
        Decision d = FILTER.evaluate(c.service(), c.purpose(), c.conditions(), offered);
        Set<IRI> needed = d.requirements().needed();
        for (DataAssessment a : d.assessments()) {
            switch (a.status()) {
                case EXCESSIVE -> { if (!a.covers().isEmpty()) return false; }
                case SUPERSET, SATISFIES -> {
                    if (a.covers().isEmpty() || !java.util.Collections.disjoint(a.exposes(), needed)) return false;
                    if (needed.contains(a.datum())) return false;
                }
                case REQUIRED -> { if (!d.required().contains(a.datum())) return false; }
                case OPTIONAL -> { if (!d.optional().contains(a.datum())) return false; }
                case UNKNOWN -> { return false; } // only known data types are generated
            }
        }
        return true;
    }

    @Property(tries = 300)
    boolean minimalDisclosureIsNeverDearerThanACoveringOffer(@ForAll("contexts") Context c, @ForAll("offered") Set<IRI> offered) {
        Decision d = FILTER.evaluate(c.service(), c.purpose(), c.conditions(), offered);
        return !d.missing().isEmpty() || d.minimalCost() <= d.offeredCost();
    }

    @Property(tries = 300)
    boolean planIsCompleteExactlyWhenHeldDataCoverTheRequirements(@ForAll("contexts") Context c, @ForAll("offered") Set<IRI> held) {
        DisclosurePlan plan = PLANNER.plan(c.service(), c.purpose(), c.conditions(), held, false);
        Set<IRI> covered = new HashSet<>();
        held.forEach(h -> covered.addAll(GRAPH.covers(h)));
        boolean coverable = covered.containsAll(GRAPH.requirements(c.service(), c.purpose(), c.conditions()).required());
        return plan.isComplete() == coverable;
    }

    /**
     * A plan never discloses more than sending everything held would. This relies on the model
     * having no redundant requirements (a datum and something it already discloses both
     * required), which the validator reports as REDUNDANT_REQUIREMENT, and on no service
     * requiring a bare category.
     */
    @Property(tries = 300)
    boolean planNeverCostsMoreThanSendingEverythingHeld(@ForAll("contexts") Context c, @ForAll("offered") Set<IRI> held) {
        DisclosurePlan plan = PLANNER.plan(c.service(), c.purpose(), c.conditions(), held, false);
        int disclosedCost = plan.disclosed().stream().mapToInt(GRAPH::disclosureCost).sum();
        return plan.planCost() == disclosedCost && plan.planCost() <= plan.naiveCost();
    }
}
