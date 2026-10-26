package dataminimization;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.util.Values;

/**
 * IRIs of the vocabularies used by the knowledge graph. Services, purposes and conditions
 * share the {@code srv:} namespace, so {@link #service}, {@link #purpose} and
 * {@link #condition} mint IRIs that differ only by convention; {@link KnowledgeGraph}
 * checks the declared type before using one.
 */
public final class Vocabulary {

    public static final String DATA_NS = "http://example.org/dms/data#";
    public static final String SERVICE_NS = "http://example.org/dms/service#";
    public static final String DPV_NS = "https://w3id.org/dpv#";
    public static final String DPV_PD_NS = "https://w3id.org/dpv/pd#";
    public static final String SKOS_NS = "http://www.w3.org/2004/02/skos/core#";

    public static final IRI PERSONAL_DATA = Values.iri(DATA_NS, "PersonalData");
    public static final IRI SERVICE = Values.iri(SERVICE_NS, "Service");
    public static final IRI PURPOSE = Values.iri(SERVICE_NS, "Purpose");
    public static final IRI PROCESSING = Values.iri(SERVICE_NS, "Processing");
    public static final IRI CONDITION = Values.iri(SERVICE_NS, "Condition");
    public static final IRI CONDITIONAL_REQUIREMENT = Values.iri(SERVICE_NS, "ConditionalRequirement");

    public static final IRI IS_A = Values.iri(DATA_NS, "isA");
    public static final IRI IS_PART_OF = Values.iri(DATA_NS, "isPartOf");
    public static final IRI IS_MORE_DETAILED_THAN = Values.iri(DATA_NS, "isMoreDetailedThan");
    public static final IRI SENSITIVITY = Values.iri(DATA_NS, "sensitivity");

    public static final IRI HAS_PROCESSING = Values.iri(SERVICE_NS, "hasProcessing");
    public static final IRI HAS_PURPOSE = Values.iri(SERVICE_NS, "hasPurpose");
    public static final IRI REQUIRES = Values.iri(SERVICE_NS, "requires");
    public static final IRI MAY_REQUIRES = Values.iri(SERVICE_NS, "mayRequires");
    public static final IRI REQUIRES_WHEN = Values.iri(SERVICE_NS, "requiresWhen");
    public static final IRI HAS_CONDITION = Values.iri(SERVICE_NS, "condition");

    public static final IRI SKOS_EXACT_MATCH = Values.iri(SKOS_NS, "exactMatch");
    public static final IRI SKOS_CLOSE_MATCH = Values.iri(SKOS_NS, "closeMatch");
    public static final IRI SKOS_BROAD_MATCH = Values.iri(SKOS_NS, "broadMatch");

    private Vocabulary() {
    }

    /** IRI of a personal data type from its local name, e.g. {@code data("IDCard")}. */
    public static IRI data(String localName) {
        return Values.iri(DATA_NS, localName);
    }

    /** IRI of a service from its local name, e.g. {@code service("SubscriptionSignUp")}. */
    public static IRI service(String localName) {
        return Values.iri(SERVICE_NS, localName);
    }

    /** IRI of a purpose from its local name, e.g. {@code purpose("Payment")}. */
    public static IRI purpose(String localName) {
        return Values.iri(SERVICE_NS, localName);
    }

    /** IRI of a condition from its local name, e.g. {@code condition("ContainsAlcohol")}. */
    public static IRI condition(String localName) {
        return Values.iri(SERVICE_NS, localName);
    }
}
