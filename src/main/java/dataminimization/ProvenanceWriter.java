package dataminimization;

import java.io.Writer;
import java.time.Instant;
import java.util.UUID;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.util.ModelBuilder;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.PROV;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;

/**
 * Writes a decision as a PROV-O audit record in Turtle: the check is a {@code prov:Activity}
 * that used the service, purpose, conditions and offered data and generated a decision
 * entity with one assessment per datum. Stored records let a controller demonstrate
 * compliance with the accountability principle (Art. 5(2)).
 */
public final class ProvenanceWriter {

    public static final String NS = "http://example.org/dms/decision#";
    public static final IRI SOFTWARE_AGENT = Values.iri(NS, "DataMinimizationSystem");

    private ProvenanceWriter() {
    }

    public static Model model(Decision d, Instant startedAt, Instant endedAt) {
        String id = UUID.randomUUID().toString();
        IRI check = Values.iri(NS, "check-" + id);
        IRI decision = Values.iri(NS, "decision-" + id);
        ModelBuilder b = new ModelBuilder()
                .setNamespace("dms", NS).setNamespace("prov", PROV.NAMESPACE)
                .setNamespace("data", Vocabulary.DATA_NS).setNamespace("srv", Vocabulary.SERVICE_NS)
                .setNamespace("xsd", XSD.NAMESPACE);
        b.subject(SOFTWARE_AGENT).add(RDF.TYPE, PROV.SOFTWARE_AGENT).add(Values.iri("http://www.w3.org/2000/01/rdf-schema#label"), "Data Minimization System");
        b.subject(check)
                .add(RDF.TYPE, PROV.ACTIVITY).add(RDF.TYPE, Values.iri(NS, "MinimizationCheck"))
                .add(PROV.STARTED_AT_TIME, Values.literal(startedAt.toString(), XSD.DATETIME))
                .add(PROV.ENDED_AT_TIME, Values.literal(endedAt.toString(), XSD.DATETIME))
                .add(PROV.WAS_ASSOCIATED_WITH, SOFTWARE_AGENT)
                .add(PROV.USED, d.service())
                .add(PROV.GENERATED, decision);
        if (d.purpose() != null) {
            b.add(PROV.USED, d.purpose()).add(Values.iri(NS, "purpose"), d.purpose());
        }
        for (IRI c : d.conditions()) {
            b.add(Values.iri(NS, "condition"), c);
        }
        for (DataAssessment a : d.assessments()) {
            b.add(PROV.USED, a.datum()).add(Values.iri(NS, "offered"), a.datum());
        }
        b.subject(decision)
                .add(RDF.TYPE, PROV.ENTITY).add(RDF.TYPE, Values.iri(NS, "Decision"))
                .add(PROV.WAS_GENERATED_BY, check)
                .add(Values.iri(NS, "service"), d.service())
                .add(Values.iri(NS, "verdict"), d.verdict().name())
                .add(Values.iri(NS, "offeredCost"), d.offeredCost())
                .add(Values.iri(NS, "minimalCost"), d.minimalCost());
        for (IRI m : d.missing()) {
            b.add(Values.iri(NS, "missing"), m);
        }
        for (IRI m : d.minimalDisclosure()) {
            b.add(Values.iri(NS, "minimalDisclosure"), m);
        }
        int i = 0;
        for (DataAssessment a : d.assessments()) {
            Resource node = Values.iri(NS, "assessment-" + id + "-" + i++);
            b.subject(decision).add(Values.iri(NS, "assessment"), node);
            b.subject(node).add(RDF.TYPE, Values.iri(NS, "Assessment"))
                    .add(Values.iri(NS, "datum"), a.datum())
                    .add(Values.iri(NS, "status"), a.status().name())
                    .add(Values.iri(NS, "cost"), a.cost());
            for (IRI c : a.covers()) {
                b.add(Values.iri(NS, "covers"), c);
            }
            for (IRI e : a.exposes()) {
                b.add(Values.iri(NS, "exposes"), e);
            }
            for (IRI c : a.because()) {
                b.add(Values.iri(NS, "because"), c);
            }
        }
        return b.build();
    }

    public static void write(Decision d, Instant startedAt, Instant endedAt, Writer out) {
        Rio.write(model(d, startedAt, endedAt), out, RDFFormat.TURTLE);
    }
}
