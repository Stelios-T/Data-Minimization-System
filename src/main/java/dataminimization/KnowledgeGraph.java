package dataminimization;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

/**
 * The knowledge graph of personal data types and services, backed by an in-memory
 * RDF store. All reasoning about the graph is expressed as SPARQL 1.1 property paths,
 * so the Java code never walks the graph by hand.
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

    private final Repository repository;

    private KnowledgeGraph(Repository repository) {
        this.repository = repository;
    }

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
        RDFFormat format = org.eclipse.rdf4j.rio.Rio.getParserFormatForFileName(file.toString())
                .orElse(RDFFormat.TURTLE);
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

    public boolean isPersonalData(IRI datum) {
        return hasType(datum, Vocabulary.PERSONAL_DATA);
    }

    public boolean isService(IRI service) {
        return hasType(service, Vocabulary.SERVICE);
    }

    /** All personal data types declared in the graph. */
    public Set<IRI> personalData() {
        return subjectsOfType(Vocabulary.PERSONAL_DATA);
    }

    /** All services declared in the graph. */
    public Set<IRI> services() {
        return subjectsOfType(Vocabulary.SERVICE);
    }

    /** Data the service cannot work without. */
    public Set<IRI> required(IRI service) {
        return objects(service, Vocabulary.REQUIRES);
    }

    /** Data the service can optionally use. */
    public Set<IRI> optional(IRI service) {
        return objects(service, Vocabulary.MAY_REQUIRES);
    }

    /**
     * Every datum that providing {@code datum} discloses or satisfies, including the
     * datum itself.
     */
    public Set<IRI> covers(IRI datum) {
        return query(COVERS_QUERY, datum);
    }

    /**
     * Concrete data disclosed along with {@code datum}: its parts, what can be derived
     * from it, and so on transitively. Does not include the datum itself or its
     * generalisations.
     */
    public Set<IRI> exposes(IRI datum) {
        return query(EXPOSES_QUERY, datum);
    }

    @Override
    public void close() {
        repository.shutDown();
    }

    // -- helpers --------------------------------------------------------------

    private boolean hasType(IRI subject, IRI type) {
        try (RepositoryConnection conn = repository.getConnection()) {
            return conn.hasStatement(subject, RDF.TYPE, type, false);
        }
    }

    private Set<IRI> subjectsOfType(IRI type) {
        Set<IRI> result = new LinkedHashSet<>();
        try (RepositoryConnection conn = repository.getConnection()) {
            for (Statement st : conn.getStatements(null, RDF.TYPE, type, false)) {
                Resource s = st.getSubject();
                if (s instanceof IRI iri) {
                    result.add(iri);
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private Set<IRI> objects(IRI subject, IRI predicate) {
        Set<IRI> result = new LinkedHashSet<>();
        try (RepositoryConnection conn = repository.getConnection()) {
            for (Statement st : conn.getStatements(subject, predicate, null, false)) {
                Value o = st.getObject();
                if (o instanceof IRI iri) {
                    result.add(iri);
                }
            }
        }
        return Collections.unmodifiableSet(result);
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
