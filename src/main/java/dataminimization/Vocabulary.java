package dataminimization;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.util.Values;

/** IRIs of the two vocabularies used by the knowledge graph. */
public final class Vocabulary {

    public static final String DATA_NS = "http://example.org/dms/data#";
    public static final String SERVICE_NS = "http://example.org/dms/service#";

    public static final IRI PERSONAL_DATA = Values.iri(DATA_NS, "PersonalData");
    public static final IRI SERVICE = Values.iri(SERVICE_NS, "Service");

    public static final IRI IS_A = Values.iri(DATA_NS, "isA");
    public static final IRI IS_PART_OF = Values.iri(DATA_NS, "isPartOf");
    public static final IRI IS_MORE_DETAILED_THAN = Values.iri(DATA_NS, "isMoreDetailedThan");

    public static final IRI REQUIRES = Values.iri(SERVICE_NS, "requires");
    public static final IRI MAY_REQUIRES = Values.iri(SERVICE_NS, "mayRequires");

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
}
