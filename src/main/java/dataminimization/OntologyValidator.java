package dataminimization;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.rdf4j.common.exception.ValidationException;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDF4J;
import org.eclipse.rdf4j.model.vocabulary.SHACL;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.eclipse.rdf4j.sail.shacl.ShaclSail;

/**
 * Checks a knowledge graph for structural problems (SHACL shapes in {@code shapes.ttl})
 * and for modelling problems that shapes cannot express: cycles in containment or
 * derivation, requirements made redundant by other requirements, optional data already
 * disclosed by required data, unused processing nodes and services without needs.
 */
public final class OntologyValidator {

    private OntologyValidator() {
    }

    public static List<Violation> validate(KnowledgeGraph graph) {
        List<Violation> out = new ArrayList<>();
        Model data = graph.statements();
        out.addAll(shacl(data));
        out.addAll(cycles(data, Vocabulary.IS_PART_OF, "CYCLE_PART_OF", "containment"));
        out.addAll(cycles(data, Vocabulary.IS_MORE_DETAILED_THAN, "CYCLE_DERIVATION", "derivation"));
        out.addAll(dangling(data));
        out.addAll(requirementSmells(graph));
        out.addAll(unusedProcessings(data));
        out.addAll(servicesWithoutNeeds(graph));
        return out;
    }

    /** True when no violation has severity ERROR. */
    public static boolean isClean(List<Violation> violations) {
        return violations.stream().noneMatch(v -> v.severity() == Violation.Severity.ERROR);
    }

    // -- SHACL ------------------------------------------------------------------

    static Model shapes() {
        try (InputStream in = OntologyValidator.class.getResourceAsStream("/shapes.ttl")) {
            if (in == null) {
                throw new IllegalStateException("shapes.ttl not found on the classpath");
            }
            return Rio.parse(in, "", RDFFormat.TURTLE);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read bundled shapes.ttl", e);
        }
    }

    private static List<Violation> shacl(Model data) {
        Model shapes = shapes();
        SailRepository repo = new SailRepository(new ShaclSail(new MemoryStore()));
        repo.init();
        try (SailRepositoryConnection conn = repo.getConnection()) {
            conn.begin();
            conn.add(shapes, RDF4J.SHACL_SHAPE_GRAPH);
            conn.commit();
            conn.begin();
            conn.add(data);
            try {
                conn.commit();
                return List.of();
            } catch (RepositoryException e) {
                if (e.getCause() instanceof ValidationException ve) {
                    conn.rollback();
                    return report(ve.validationReportAsModel(), shapes);
                }
                throw e;
            }
        } finally {
            repo.shutDown();
        }
    }

    private static List<Violation> report(Model report, Model shapes) {
        List<Violation> out = new ArrayList<>();
        for (Value result : report.filter(null, SHACL.RESULT, null).objects()) {
            if (!(result instanceof Resource r)) {
                continue;
            }
            String component = Models.objectIRI(report.filter(r, SHACL.SOURCE_CONSTRAINT_COMPONENT, null))
                    .map(IRI::getLocalName).orElse("Unknown");
            String message = Models.objectString(report.filter(r, SHACL.RESULT_MESSAGE, null))
                    .or(() -> Models.objectResource(report.filter(r, SHACL.SOURCE_SHAPE, null))
                            .flatMap(shape -> Models.objectString(shapes.filter(shape, SHACL.MESSAGE, null))))
                    .orElse(component);
            String path = Models.objectIRI(report.filter(r, SHACL.RESULT_PATH, null)).map(p -> " on " + p.getLocalName()).orElse("");
            Set<IRI> subjects = new LinkedHashSet<>();
            Models.objectIRI(report.filter(r, SHACL.FOCUS_NODE, null)).ifPresent(subjects::add);
            out.add(new Violation(Violation.Severity.ERROR, "SHACL:" + component, message + path, subjects));
        }
        return out;
    }

    // -- structural checks --------------------------------------------------------

    private static List<Violation> cycles(Model data, IRI relation, String code, String what) {
        List<Violation> out = new ArrayList<>();
        Set<Resource> subjects = data.filter(null, relation, null).subjects();
        for (Resource start : subjects) {
            if (!(start instanceof IRI s)) {
                continue;
            }
            Set<Resource> seen = new LinkedHashSet<>();
            List<Resource> frontier = new ArrayList<>(List.of(s));
            while (!frontier.isEmpty()) {
                List<Resource> next = new ArrayList<>();
                for (Resource node : frontier) {
                    for (Value o : data.filter(node, relation, null).objects()) {
                        if (o.equals(s)) {
                            out.add(new Violation(Violation.Severity.ERROR, code,
                                    s.getLocalName() + " reaches itself through " + what, Set.of(s)));
                            next.clear();
                            frontier = List.of();
                            break;
                        }
                        if (o instanceof Resource res && seen.add(res)) {
                            next.add(res);
                        }
                    }
                    if (frontier.isEmpty()) {
                        break;
                    }
                }
                frontier = next;
            }
        }
        return out;
    }

    private static List<Violation> dangling(Model data) {
        List<Violation> out = new ArrayList<>();
        for (IRI p : List.of(Vocabulary.IS_A, Vocabulary.IS_PART_OF, Vocabulary.IS_MORE_DETAILED_THAN, Vocabulary.REQUIRES,
                Vocabulary.MAY_REQUIRES, Vocabulary.HAS_PURPOSE, Vocabulary.HAS_PROCESSING, Vocabulary.HAS_CONDITION)) {
            for (Statement st : data.filter(null, p, null)) {
                if (st.getObject() instanceof IRI o && !data.contains(o, RDF.TYPE, null)) {
                    out.add(new Violation(Violation.Severity.ERROR, "DANGLING_REFERENCE",
                            o.getLocalName() + " is referenced by " + p.getLocalName() + " but never declared", Set.of(o)));
                }
            }
        }
        return out;
    }

    private static List<Violation> requirementSmells(KnowledgeGraph graph) {
        List<Violation> out = new ArrayList<>();
        for (IRI service : graph.services()) {
            for (Processing p : graph.processings(service)) {
                Set<IRI> required = new LinkedHashSet<>(p.required());
                required.addAll(p.conditional().keySet());
                for (IRI r1 : required) {
                    for (IRI r2 : required) {
                        if (!r1.equals(r2) && graph.covers(r1).contains(r2)) {
                            out.add(new Violation(Violation.Severity.WARNING, "REDUNDANT_REQUIREMENT",
                                    p.node().getLocalName() + " requires " + r2.getLocalName() + " which " + r1.getLocalName()
                                    + " already discloses", Set.of(p.node(), r1, r2)));
                        }
                    }
                    for (IRI o : p.optional()) {
                        if (graph.covers(r1).contains(o)) {
                            out.add(new Violation(Violation.Severity.WARNING, "OPTIONAL_ALREADY_COVERED",
                                    p.node().getLocalName() + " may require " + o.getLocalName() + " but required "
                                    + r1.getLocalName() + " already discloses it", Set.of(p.node(), r1, o)));
                        }
                    }
                }
            }
        }
        return out;
    }

    private static List<Violation> unusedProcessings(Model data) {
        List<Violation> out = new ArrayList<>();
        for (Resource p : data.filter(null, RDF.TYPE, Vocabulary.PROCESSING).subjects()) {
            if (p instanceof IRI iri && !data.contains(null, Vocabulary.HAS_PROCESSING, iri)) {
                out.add(new Violation(Violation.Severity.WARNING, "UNUSED_PROCESSING",
                        iri.getLocalName() + " is not attached to any service", Set.of(iri)));
            }
        }
        return out;
    }

    private static List<Violation> servicesWithoutNeeds(KnowledgeGraph graph) {
        List<Violation> out = new ArrayList<>();
        for (IRI s : graph.services()) {
            boolean any = graph.processings(s).stream()
                    .anyMatch(p -> !p.required().isEmpty() || !p.optional().isEmpty() || !p.conditional().isEmpty());
            if (!any) {
                out.add(new Violation(Violation.Severity.WARNING, "SERVICE_WITHOUT_NEEDS",
                        s.getLocalName() + " declares no personal data needs", Set.of(s)));
            }
        }
        return out;
    }
}
