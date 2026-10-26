package dataminimization;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.eclipse.rdf4j.model.IRI;

/**
 * What a user agent should disclose to a service, computed on the user's side from the
 * data it holds. Only the outputs of the steps leave the device.
 *
 * @param service       the service
 * @param purpose       the purpose, or {@code null} for all purposes
 * @param conditions    active conditions
 * @param requirements  what the service needs
 * @param held          the data the user agent holds
 * @param steps         one step per satisfiable requirement
 * @param unsatisfiable requirements nothing held can provide
 * @param unknown       held entries that are not personal data types in the graph; they are ignored
 * @param planCost      disclosure cost of what the plan sends
 * @param naiveCost     disclosure cost of sending everything held as it is
 */
public record DisclosurePlan(IRI service, IRI purpose, Set<IRI> conditions, Requirements requirements, Set<IRI> held,
                             List<Step> steps, Set<IRI> unsatisfiable, Set<IRI> unknown, int planCost, int naiveCost) {

    /** How a requirement is met from held data. */
    public enum Kind {
        /** The held datum is exactly what is required. */
        DISCLOSE,
        /** Take a part out of a held datum and send only that part. */
        EXTRACT,
        /** Compute the required datum from a held one locally and send the result. */
        DERIVE,
        /** The held datum is a specialisation of the requirement; send it as is. */
        PRESENT_AS
    }

    /**
     * One step of the plan.
     *
     * @param kind     how the requirement is met
     * @param from     the held datum used
     * @param to       the requirement it meets
     * @param path     the data types visited from {@code from} to {@code to}, both included
     * @param optional true when the requirement was optional
     */
    public record Step(Kind kind, IRI from, IRI to, List<IRI> path, boolean optional) {
        public Step {
            path = List.copyOf(path);
        }
    }

    public DisclosurePlan {
        conditions = Iris.sorted(conditions);
        held = Iris.sorted(held);
        steps = List.copyOf(steps);
        unsatisfiable = Iris.sorted(unsatisfiable);
        unknown = Iris.sorted(unknown);
    }

    /** True when every required datum can be provided from the held data. */
    @JsonProperty("complete")
    public boolean isComplete() {
        return unsatisfiable.isEmpty();
    }

    /** Data types that will actually be sent. */
    @JsonProperty("disclosed")
    public Set<IRI> disclosed() {
        return Iris.sorted(steps.stream().map(Step::to).collect(Collectors.toSet()));
    }

    /** Human-readable plan; see {@link TextReports#plan}. */
    public String report() {
        return TextReports.plan(this);
    }
}
