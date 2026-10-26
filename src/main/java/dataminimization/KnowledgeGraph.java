package dataminimization;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

/**
 * The knowledge graph of personal data types, services, purposes and conditions, backed by
 * an in-memory RDF store. Reasoning over the data relations is expressed as SPARQL 1.1
 * property paths, so the Java code never walks the graph by hand. Results of the path
 * queries are cached because the graph is immutable after loading.
 */
public final class KnowledgeGraph implements AutoCloseable {

    private static final String PREFIXES =
            "PREFIX data: <" + Vocabulary.DATA_NS + ">\n"
            + "PREFIX srv: <" + Vocabulary.SERVICE_NS + ">\n";

    /**
     * Everything that handing over a datum discloses or satisfies: first follow
     * containment and derivation downwards any number of times, then generalise
     * upwards any number of times. Generalising first and then decomposing would be
     * unsound (a Contact is not an Email).
     */
    private static final String COVERS_QUERY = PREFIXES
            + "SELECT DISTINCT ?x WHERE { ?p (^data:isPartOf|data:isMoreDetailedThan)*/data:isA* ?x }";

    /** Concrete data disclosed along with a datum: its parts and what can be derived from it. */
    private static final String EXPOSES_QUERY = PREFIXES
            + "SELECT DISTINCT ?x WHERE { ?p (^data:isPartOf|data:isMoreDetailedThan)+ ?x }";

    /** The reverse of {@link #COVERS_QUERY}: every datum whose disclosure covers ?p. */
    private static final String PROVIDERS_QUERY = PREFIXES
            + "SELECT DISTINCT ?x WHERE { ?x (^data:isPartOf|data:isMoreDetailedThan)*/data:isA* ?p . ?x a data:PersonalData }";

    private final Repository repository;
    private final Map<IRI, Set<IRI>> coversCache = new ConcurrentHashMap<>();
    private final Map<IRI, Set<IRI>> exposesCache = new ConcurrentHashMap<>();
    private final Map<IRI, Set<IRI>> providersCache = new ConcurrentHashMap<>();
    private final Map<IRI, Integer> costCache = new ConcurrentHashMap<>();
    private final Map<IRI, Integer> sensitivityCache = new ConcurrentHashMap<>();
    private final Map<IRI, List<Edge>> edgesCache = new ConcurrentHashMap<>();
    private final Map<IRI, List<Processing>> processingsCache = new ConcurrentHashMap<>();
    private final Map<IRI, Optional<IRI>> dpvCache = new ConcurrentHashMap<>();
    private volatile Set<IRI> personalDataCache;

    private KnowledgeGraph(Repository repository) {
        this.repository = repository;
    }

    // -- loading ----------------------------------------------------------------

    /** Loads the ontology shipped with the application ({@code model.ttl} on the classpath). */
    public static KnowledgeGraph loadDefault() {
        try (InputStream in = KnowledgeGraph.class.getResourceAsStream("/model.ttl")) {
            if (in == null) {
                throw new IllegalStateException("model.ttl not found on the classpath");
            }
            return load(in, RDFFormat.TURTLE);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read bundled model.ttl", e);
        }
    }

    /** Loads an ontology from a file; the format is guessed from the file extension (Turtle by default). */
    public static KnowledgeGraph load(Path file) throws IOException {
        RDFFormat format = Rio.getParserFormatForFileName(file.toString()).orElse(RDFFormat.TURTLE);
        try (InputStream in = Files.newInputStream(file)) {
            return load(in, format);
        }
    }

    public static KnowledgeGraph load(InputStream in, RDFFormat format) throws IOException {
        Repository repository = new SailRepository(new MemoryStore());
        repository.init();
        try (RepositoryConnection conn = repository.getConnection()) {
            conn.add(in, "", format);
        } catch (IOException | RuntimeException e) {
            repository.shutDown();
            throw e;
        }
        return new KnowledgeGraph(repository);
    }

    /** A copy of every statement in the graph, for validation and export. */
    public Model statements() {
        Model model = new LinkedHashModel();
        try (RepositoryConnection conn = repository.getConnection();
             RepositoryResult<Statement> rows = conn.getStatements(null, null, null, false)) {
            rows.forEach(model::add);
        }
        return model;
    }

    // -- types ------------------------------------------------------------------

    public boolean isPersonalData(IRI datum) {
        return personalData().contains(datum);
    }

    public boolean isService(IRI service) {
        return hasType(service, Vocabulary.SERVICE);
    }

    public boolean isPurpose(IRI purpose) {
        return hasType(purpose, Vocabulary.PURPOSE);
    }

    public boolean isCondition(IRI condition) {
        return hasType(condition, Vocabulary.CONDITION);
    }

    /** All personal data types declared in the graph. */
    public Set<IRI> personalData() {
        Set<IRI> cached = personalDataCache;
        if (cached == null) {
            cached = subjectsOfType(Vocabulary.PERSONAL_DATA);
            personalDataCache = cached;
        }
        return cached;
    }

    /** All services declared in the graph. */
    public Set<IRI> services() {
        return subjectsOfType(Vocabulary.SERVICE);
    }

    /** All purposes declared in the graph. */
    public Set<IRI> purposes() {
        return subjectsOfType(Vocabulary.PURPOSE);
    }

    /** All conditions declared in the graph. */
    public Set<IRI> conditions() {
        return subjectsOfType(Vocabulary.CONDITION);
    }

    // -- services, purposes and requirements ------------------------------------

    /**
     * The processing operations of a service, one per purpose, plus one entry for
     * requirements stated directly on the service (purpose {@code null}) when there are any.
     */
    public List<Processing> processings(IRI service) {
        return processingsCache.computeIfAbsent(service, s -> {
            List<Processing> result = new ArrayList<>();
            try (RepositoryConnection conn = repository.getConnection()) {
                Processing direct = readProcessing(conn, s, null);
                if (!direct.required().isEmpty() || !direct.optional().isEmpty() || !direct.conditional().isEmpty()) {
                    result.add(direct);
                }
                for (IRI node : objects(conn, s, Vocabulary.HAS_PROCESSING)) {
                    Set<IRI> purposes = objects(conn, node, Vocabulary.HAS_PURPOSE);
                    if (purposes.size() > 1) {
                        throw new IllegalStateException("Processing " + node.getLocalName() + " has " + purposes.size()
                                + " purposes; exactly one is allowed (run --validate)");
                    }
                    result.add(readProcessing(conn, node, purposes.isEmpty() ? null : purposes.iterator().next()));
                }
            }
            return Collections.unmodifiableList(result);
        });
    }

    /** Purposes a service processes data for. */
    public Set<IRI> purposes(IRI service) {
        Set<IRI> result = new LinkedHashSet<>();
        for (Processing p : processings(service)) {
            if (p.purpose() != null) {
                result.add(p.purpose());
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /** Conditions that change what a service requires. */
    public Set<IRI> conditionsOf(IRI service) {
        Set<IRI> result = new LinkedHashSet<>();
        for (Processing p : processings(service)) {
            p.conditional().values().forEach(result::addAll);
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * What a service needs for a purpose under the given conditions.
     *
     * @param service          the service; must exist
     * @param purpose          one of the service's purposes, or {@code null} for the union over all of them
     * @param activeConditions conditions that currently hold; each must be a declared condition
     * @throws IllegalArgumentException for an unknown service, purpose or condition
     */
    public Requirements requirements(IRI service, IRI purpose, Set<IRI> activeConditions) {
        if (!isService(service)) {
            throw new IllegalArgumentException("Unknown service: " + service);
        }
        if (purpose != null) {
            Set<IRI> known = purposes(service);
            if (!known.contains(purpose)) {
                throw new IllegalArgumentException("Service " + service.getLocalName() + " has no purpose " + purpose
                        + "; its purposes are " + Names.of(known));
            }
        }
        for (IRI c : activeConditions) {
            if (!isCondition(c)) {
                throw new IllegalArgumentException("Unknown condition: " + c);
            }
        }
        Set<IRI> required = new LinkedHashSet<>();
        Set<IRI> optional = new LinkedHashSet<>();
        Map<IRI, Set<IRI>> conditional = new LinkedHashMap<>();
        Map<IRI, Set<IRI>> inactive = new LinkedHashMap<>();
        for (Processing p : processings(service)) {
            if (purpose != null && !purpose.equals(p.purpose())) {
                continue;
            }
            required.addAll(p.required());
            optional.addAll(p.optional());
            p.conditional().forEach((datum, conditions) -> {
                Set<IRI> active = new LinkedHashSet<>(conditions);
                active.retainAll(activeConditions);
                if (!active.isEmpty()) {
                    required.add(datum);
                    conditional.computeIfAbsent(datum, d -> new LinkedHashSet<>()).addAll(active);
                } else {
                    inactive.computeIfAbsent(datum, d -> new LinkedHashSet<>()).addAll(conditions);
                }
            });
        }
        optional.removeAll(required);
        inactive.keySet().removeAll(required);
        return new Requirements(required, optional, conditional, inactive);
    }

    /** Data the service cannot work without, over all purposes, with no condition active. */
    public Set<IRI> required(IRI service) {
        return requirements(service, null, Set.of()).required();
    }

    /** Data the service can optionally use, over all purposes, with no condition active. */
    public Set<IRI> optional(IRI service) {
        return requirements(service, null, Set.of()).optional();
    }

    // -- data relations ---------------------------------------------------------

    /** Every datum that providing {@code datum} discloses or satisfies, including the datum itself. */
    public Set<IRI> covers(IRI datum) {
        return coversCache.computeIfAbsent(datum, d -> query(COVERS_QUERY, d));
    }

    /**
     * Concrete data disclosed along with {@code datum}: its parts, what can be derived from
     * it, and so on transitively. Does not include the datum itself or its generalisations.
     */
    public Set<IRI> exposes(IRI datum) {
        return exposesCache.computeIfAbsent(datum, d -> query(EXPOSES_QUERY, d));
    }

    /** Every personal data type whose disclosure covers {@code needed}, including {@code needed} itself. */
    public Set<IRI> providersOf(IRI needed) {
        return providersCache.computeIfAbsent(needed, d -> query(PROVIDERS_QUERY, d));
    }

    /** One step a user agent can take from a datum it holds: a relation and the datum it leads to. */
    public record Edge(Relation relation, IRI target) {
    }

    /** The three ways a held datum yields another one. */
    public enum Relation {
        /** The target is a part of the held datum (reverse of {@code data:isPartOf}). */
        EXTRACT,
        /** The target can be computed from the held datum ({@code data:isMoreDetailedThan}). */
        DERIVE,
        /** The held datum is a specialisation of the target ({@code data:isA}); nothing changes hands. */
        PRESENT_AS
    }

    /** Outgoing steps from a datum, in the direction that disclosure flows. */
    public List<Edge> edges(IRI datum) {
        return edgesCache.computeIfAbsent(datum, d -> {
            List<Edge> result = new ArrayList<>();
            try (RepositoryConnection conn = repository.getConnection()) {
                for (IRI part : subjects(conn, Vocabulary.IS_PART_OF, d)) {
                    result.add(new Edge(Relation.EXTRACT, part));
                }
                for (IRI derived : objects(conn, d, Vocabulary.IS_MORE_DETAILED_THAN)) {
                    result.add(new Edge(Relation.DERIVE, derived));
                }
                for (IRI general : objects(conn, d, Vocabulary.IS_A)) {
                    result.add(new Edge(Relation.PRESENT_AS, general));
                }
            }
            result.sort(Comparator.comparing((Edge e) -> e.relation().ordinal()).thenComparing(e -> e.target().getLocalName()));
            return Collections.unmodifiableList(result);
        });
    }

    /**
     * Disclosure weight declared on the datum, 1 when absent.
     *
     * @throws IllegalStateException when the declared value is not an integer
     */
    public int sensitivity(IRI datum) {
        return sensitivityCache.computeIfAbsent(datum, d -> {
            try (RepositoryConnection conn = repository.getConnection();
                 RepositoryResult<Statement> rows = conn.getStatements(d, Vocabulary.SENSITIVITY, null, false)) {
                for (Statement st : rows) {
                    if (st.getObject() instanceof Literal lit) {
                        try {
                            return lit.intValue();
                        } catch (NumberFormatException e) {
                            throw new IllegalStateException("data:sensitivity of " + d.getLocalName() + " is not an integer: " + lit.getLabel());
                        }
                    }
                }
            }
            return 1;
        });
    }

    /** Total disclosure cost of a datum: its own sensitivity plus that of everything it exposes. */
    public int disclosureCost(IRI datum) {
        return costCache.computeIfAbsent(datum, d -> {
            int total = sensitivity(d);
            for (IRI x : exposes(d)) {
                total += sensitivity(x);
            }
            return total;
        });
    }

    /** The closest DPV-PD concept linked to a datum via SKOS (exact, close or broad match). */
    public Optional<IRI> dpvMatch(IRI datum) {
        return dpvCache.computeIfAbsent(datum, d -> {
            try (RepositoryConnection conn = repository.getConnection()) {
                for (IRI p : List.of(Vocabulary.SKOS_EXACT_MATCH, Vocabulary.SKOS_CLOSE_MATCH, Vocabulary.SKOS_BROAD_MATCH)) {
                    Set<IRI> matches = objects(conn, d, p);
                    if (!matches.isEmpty()) {
                        return Optional.of(matches.iterator().next());
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public void close() {
        repository.shutDown();
    }

    // -- helpers --------------------------------------------------------------

    private Processing readProcessing(RepositoryConnection conn, IRI node, IRI purpose) {
        Set<IRI> required = objects(conn, node, Vocabulary.REQUIRES);
        Set<IRI> optional = objects(conn, node, Vocabulary.MAY_REQUIRES);
        Map<IRI, Set<IRI>> conditional = new LinkedHashMap<>();
        try (RepositoryResult<Statement> rows = conn.getStatements(node, Vocabulary.REQUIRES_WHEN, null, false)) {
            for (Statement st : rows) {
                if (st.getObject() instanceof Resource cr) {
                    Set<IRI> conditions = objects(conn, cr, Vocabulary.HAS_CONDITION);
                    for (IRI datum : objects(conn, cr, Vocabulary.REQUIRES)) {
                        conditional.computeIfAbsent(datum, d -> new LinkedHashSet<>()).addAll(conditions);
                    }
                }
            }
        }
        return new Processing(node, purpose, required, optional, conditional);
    }

    private boolean hasType(IRI subject, IRI type) {
        try (RepositoryConnection conn = repository.getConnection()) {
            return conn.hasStatement(subject, RDF.TYPE, type, false);
        }
    }

    private Set<IRI> subjectsOfType(IRI type) {
        try (RepositoryConnection conn = repository.getConnection()) {
            return Collections.unmodifiableSet(subjects(conn, RDF.TYPE, type));
        }
    }

    private static Set<IRI> subjects(RepositoryConnection conn, IRI predicate, Value object) {
        Set<IRI> result = new LinkedHashSet<>();
        try (RepositoryResult<Statement> rows = conn.getStatements(null, predicate, object, false)) {
            for (Statement st : rows) {
                if (st.getSubject() instanceof IRI iri) {
                    result.add(iri);
                }
            }
        }
        return result;
    }

    private static Set<IRI> objects(RepositoryConnection conn, Resource subject, IRI predicate) {
        Set<IRI> result = new LinkedHashSet<>();
        try (RepositoryResult<Statement> rows = conn.getStatements(subject, predicate, null, false)) {
            for (Statement st : rows) {
                if (st.getObject() instanceof IRI iri) {
                    result.add(iri);
                }
            }
        }
        return result;
    }

    private Set<IRI> query(String sparql, IRI p) {
        Set<IRI> result = new LinkedHashSet<>();
        try (RepositoryConnection conn = repository.getConnection()) {
            TupleQuery q = conn.prepareTupleQuery(sparql);
            q.setBinding("p", p);
            try (TupleQueryResult rows = q.evaluate()) {
                for (BindingSet row : rows) {
                    Value x = row.getValue("x");
                    if (x instanceof IRI iri) {
                        result.add(iri);
                    }
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
