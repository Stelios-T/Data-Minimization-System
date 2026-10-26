package dataminimization;

import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import org.eclipse.rdf4j.model.IRI;

/**
 * JSON serialisation of decisions, plans, minimization results and violations. IRIs are
 * written as objects with the local name, the full IRI and, for personal data, the DPV-PD
 * match; IRIs used as map keys are written by local name.
 */
public final class JsonOutput {

    private JsonOutput() {
    }

    public static ObjectMapper mapper(KnowledgeGraph graph) {
        SimpleModule module = new SimpleModule("dms");
        module.addSerializer(IRI.class, new JsonSerializer<>() {
            @Override
            public void serialize(IRI iri, JsonGenerator gen, SerializerProvider p) throws IOException {
                gen.writeStartObject();
                gen.writeStringField("name", iri.getLocalName());
                gen.writeStringField("iri", iri.stringValue());
                if (graph != null && iri.getNamespace().equals(Vocabulary.DATA_NS)) {
                    Optional<IRI> dpv = graph.isPersonalData(iri) ? graph.dpvMatch(iri) : Optional.empty();
                    if (dpv.isPresent()) {
                        gen.writeStringField("dpv", dpv.get().stringValue());
                    }
                }
                gen.writeEndObject();
            }
        });
        module.addKeySerializer(IRI.class, new JsonSerializer<>() {
            @Override
            public void serialize(IRI iri, JsonGenerator gen, SerializerProvider p) throws IOException {
                gen.writeFieldName(iri.getLocalName());
            }
        });
        return new ObjectMapper()
                .registerModule(module)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }
}
