package dataminimization.eval;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import dataminimization.KnowledgeGraph;
import org.eclipse.rdf4j.rio.RDFFormat;

/**
 * Generates ontologies of arbitrary size with the same shape as the bundled model, for
 * measuring how decision latency grows with the graph. Each group of ten data types has a
 * category, a document that is a kind of it, five parts of the document, and a three-step
 * derivation chain whose finest member is also part of the document. Each group has one
 * service that requires two parts and the coarsest member of the chain and may use a third part.
 */
public final class SyntheticGraph {

    private SyntheticGraph() {
    }

    /** Number of data types per group. */
    public static final int GROUP_SIZE = 10;

    public static String turtle(int groups) {
        StringBuilder sb = new StringBuilder();
        sb.append("@prefix data: <http://example.org/dms/data#> .\n@prefix srv: <http://example.org/dms/service#> .\n\n");
        for (int g = 0; g < groups; g++) {
            String cat = "data:Cat" + g, doc = "data:Doc" + g, fine = "data:Fine" + g, mid = "data:Mid" + g, coarse = "data:Coarse" + g;
            sb.append(cat).append(" a data:PersonalData ; data:sensitivity 2 .\n");
            sb.append(doc).append(" a data:PersonalData ; data:sensitivity 4 ; data:isA ").append(cat).append(" .\n");
            for (int p = 1; p <= 5; p++) {
                sb.append("data:Part").append(g).append('_').append(p).append(" a data:PersonalData ; data:sensitivity ")
                        .append(1 + p % 3).append(" ; data:isPartOf ").append(doc).append(" .\n");
            }
            sb.append(fine).append(" a data:PersonalData ; data:sensitivity 3 ; data:isPartOf ").append(doc)
                    .append(" ; data:isMoreDetailedThan ").append(mid).append(" .\n");
            sb.append(mid).append(" a data:PersonalData ; data:sensitivity 2 ; data:isMoreDetailedThan ").append(coarse).append(" .\n");
            sb.append(coarse).append(" a data:PersonalData ; data:sensitivity 1 .\n");
            sb.append("srv:Purpose").append(g).append(" a srv:Purpose .\n");
            sb.append("srv:Service").append(g).append(" a srv:Service ; srv:hasProcessing srv:Proc").append(g).append(" .\n");
            sb.append("srv:Proc").append(g).append(" a srv:Processing ; srv:hasPurpose srv:Purpose").append(g)
                    .append(" ; srv:requires data:Part").append(g).append("_1 , data:Part").append(g).append("_2 , ").append(coarse)
                    .append(" ; srv:mayRequires data:Part").append(g).append("_3 .\n\n");
        }
        return sb.toString();
    }

    public static KnowledgeGraph generate(int groups) {
        try {
            return KnowledgeGraph.load(new ByteArrayInputStream(turtle(groups).getBytes(StandardCharsets.UTF_8)), RDFFormat.TURTLE);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
