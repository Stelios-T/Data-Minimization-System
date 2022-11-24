import java.util.ArrayList;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import static org.eclipse.rdf4j.model.util.Values.iri;


public class DecisionMaker {
	
	private Model model;	
	
	public DecisionMaker(Model model) {
		this.model = model;
		
	}
	
	
	//Extracts the required Data for the Service
	//parameters: (Service name, option for two different functionalities (true [requires], false [may requires])
	private ArrayList<String> getRequiredData(String Service, boolean opt) {
		
			ArrayList<String> servicedata = new ArrayList<String>();

			//if subject is the service and object is a data and its relation is requires its added to list
			if (opt) {
				for (Statement st: model.filter(iri(Service), iri("service:requires"), null)) {
					servicedata.add(st.getObject().toString());
				}
			
			//if subject is the service and object is a data and its relation is mayrequires its added to list
			} else {
				for (Statement st: model.filter(iri(Service), iri("service:mayRequires"), null)) {
					servicedata.add(st.getObject().toString());
				}
			}	
		
		//returns the data needed for the service
		return servicedata;
		
	}

	
	//Searches and extracts the data that its requires for the service from the data that is given 
	//parameters: (data that will search in depth, data that is found so far ,so that it doesn't search same data that have already been searched)
	private ArrayList<String> extractRelationData(ArrayList<String> data, ArrayList<String> founddata) {
		
		//save all new data found here
		ArrayList<String> newdata = new ArrayList<String>();
		
		//iterates through the model to search data related to the data provided
		for (String i: data) {
			
			//if data is partOf or isA its added to list 
			for (Statement st: model.filter(null, iri("data:isA"), iri(i))) {
				newdata.add(st.getSubject().toString());
			}
			for (Statement st: model.filter(null, iri("data:isPartOf"), iri(i))) {
				newdata.add(st.getSubject().toString());
			}
			
			//if data is moredetailedthan its added to list 
			for (Statement st: model.filter(iri(i), iri("data:isMoreDetailedThan"), null)) {
				newdata.add(st.getObject().toString());
			}
		}
		
		//removes duplicate data with data that is already been found
		newdata.removeAll(founddata);
		return newdata;
	}

	

	
	//getContext():	 returns graph
	//getObject():	returns () -> (1)
	//getSubject():	 returns (1) -> ()
	//getPredicate():	returns relation
	public void DataMinimizationFilter(String service_type, ArrayList<String>  service_required_data) {
		
		ArrayList<String> least_data = new ArrayList<String>();

		//data that is required and mayrequired for Service
		ArrayList<String> is_required_data = new ArrayList<String>();
		ArrayList<String> may_required_data = new ArrayList<String>();
		
		//get data for service
		is_required_data = getRequiredData(service_type, true);
		may_required_data = getRequiredData(service_type, false);
		
		System.out.println("The Service: "+service_type+" requires "+is_required_data+"\nThe Service: "+service_type+" may require "+may_required_data+"\n");

		//search if data that is given is the ones that require. If its ok, its removed for list 
		for (String i: service_required_data) {
			if (is_required_data.contains(i)) {
				System.out.println("Data "+i+" is required for the Service and is provided");
				is_required_data.remove(i);
				
			} else if (may_required_data.contains(i)) {
				System.out.println("Data "+i+" is may required for the Service and is provided");
				may_required_data.remove(i);
				
			} else {
				System.out.println("Data "+i+" is NOT the expected data...Searching if it contains the necessary data");
				
			}
		}
		
		System.out.println("\n");

		
		//Search all the data and the're relations for the required data and print it
		for (String x: service_required_data) {
			
			//the reason for using two arrays is to keep track of the data that have been searched so far so that its not searched again
			ArrayList<String> total_found_data = new ArrayList<String>();
			ArrayList<String> current_data = new ArrayList<String>();
			
			//add first data to search its relations
			current_data.add(x);
			
			//when all the relations of data is search the list will be empty
			while (!current_data.isEmpty()) {
				
				//extract relation data from current data 
				current_data = extractRelationData(current_data, total_found_data);
				
				//save new extracted data to total data found
				total_found_data.addAll(current_data);
				
				//whenever a data that is required is found is being printed
				for (String i: current_data) {
					if (is_required_data.contains(i)) {
						System.out.println("Data "+i+" that is required for the Service can be found through "+x);
						least_data.add(i);
						
					}
					else if (may_required_data.contains(i)) {
						System.out.println("Data "+i+" is may required for the Service can be found through "+x);
						least_data.add(i);
					}
					
				}
			
			}
			
		}
		
	}

}
