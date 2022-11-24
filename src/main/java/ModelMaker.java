
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.util.ModelBuilder;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;


public class ModelMaker {
		
		private static ModelBuilder builder = new ModelBuilder();
		private static Model model;	
		
		public Model getModel() {	
			return model;
		}
	
		public void setNamespaces() {
			model = builder.setNamespace("d", "data:").setNamespace("s", "service:").build();
		}
							
		public void addType(PersonalData data) {		
			model = builder.namedGraph("d:PersonalDataGraph").add("d:"+data.data_type, RDF.TYPE, "d:PersonalData").build();		
		}
		
			
		public void addInnerDataConnection(PersonalData data1, PersonalData data2, String connection) {			
			model = builder.namedGraph("d:PersonalDataGraph").add("d:"+data2.data_type, "d:"+connection, "d:"+data1.data_type).build();		
		}
		
		
		public void addServiceDataConnection(Service service, PersonalData data, String connection) {
			model = builder.add("s:"+service.service_type, "s:"+connection, "d:"+data.data_type).build();		
		}
		
		
		//Connection with Service class
		public void addType(Service service) {
			model = builder.namedGraph("s:ServicesGraph").add("s:"+service.service_type, RDF.TYPE, "s:Service").build();	
		}
			
		
		public void exportModel() {	
			Rio.write(model, System.out, RDFFormat.RDFXML);
		}
		
		
		public void printModel() {
			// To see what's in our model, let's just print it to the screen
			for (Statement st : model) {
				System.out.println(st);
			}
			
		}

		
}

