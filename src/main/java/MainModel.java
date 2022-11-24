import java.util.ArrayList;
import java.util.List;


public class MainModel {
	
	public static void main(String[] args) {
		
		ModelMaker main_model = new ModelMaker();	
		
		main_model.setNamespaces();
		
		List<PersonalData> alldata = new ArrayList<PersonalData>();
			
			alldata.add(new PersonalData("Identity"));
				alldata.add(new PersonalData("FullName"));
					alldata.add(new PersonalData("FirstName"));
					alldata.add(new PersonalData("MiddleName"));
					alldata.add(new PersonalData("LastName"));
				
				alldata.add(new PersonalData("IDCard"));
					alldata.add(new PersonalData("IDNumber"));
					alldata.add(new PersonalData("PersonalSign"));
					alldata.add(new PersonalData("FatherName"));
					alldata.add(new PersonalData("MotherName"));
			
				alldata.add(new PersonalData("Passport"));
					alldata.add(new PersonalData("PassportNumber"));
			
			
			alldata.add(new PersonalData("Contact"));
				alldata.add(new PersonalData("TelephoneNumber"));
				alldata.add(new PersonalData("Email"));
				alldata.add(new PersonalData("FaxNumber"));
		
			
			alldata.add(new PersonalData("Location"));
				alldata.add(new PersonalData("StreetAddress"));
					alldata.add(new PersonalData("StreetName"));
					alldata.add(new PersonalData("StreetNumber"));
				alldata.add(new PersonalData("City"));
				alldata.add(new PersonalData("Country"));
	
				
			alldata.add(new PersonalData("Age"));
				alldata.add(new PersonalData("IsAdult"));
				alldata.add(new PersonalData("ExactAge"));
				alldata.add(new PersonalData("DateOfBirth"));
					alldata.add(new PersonalData("DayOfBirth"));
					alldata.add(new PersonalData("MonthOfBirth"));
					alldata.add(new PersonalData("YearOfBirth"));
			
			alldata.add(new PersonalData("Billing"));
				alldata.add(new PersonalData("Account"));
					alldata.add(new PersonalData("AccountNumber"));
					alldata.add(new PersonalData("AccountBalance"));
					

			
		for (PersonalData pd : alldata) {
			main_model.addType(pd);
		}
		
	
		
		List<Service> allservices = new ArrayList<Service>();
			allservices.add(new Service("SignInvoice"));
			allservices.add(new Service("LocalInfoFinder"));
			allservices.add(new Service("MapProvision"));
			allservices.add(new Service("ShopFinder"));
			allservices.add(new Service("RestaurantFinder"));
			allservices.add(new Service("OrderPlacement"));
			allservices.add(new Service("EntryAllowance"));
			allservices.add(new Service("CheckoutPay"));
			allservices.add(new Service("CheckoutPayAdult"));
			allservices.add(new Service("AirportCheckIn"));
			allservices.add(new Service("SubscriptionPay"));
			allservices.add(new Service("SubscriptionSignUp"));
			allservices.add(new Service("PostalFormFilling"));
		
		for (Service srvc : allservices) {
			main_model.addType(srvc);
		}
		
		
		
		//////////////////////////////////////////////
		////////////////  D A T A   //////////////////
		//////////////////////////////////////////////
		 	
		//Identity -> FullName, IdCard, Passport
		main_model.addInnerDataConnection(alldata.get(0), alldata.get(1), "isA");
		main_model.addInnerDataConnection(alldata.get(0), alldata.get(5), "isA");
		main_model.addInnerDataConnection(alldata.get(0), alldata.get(10), "isA");
		
		//Passport -> PassportNumber
		main_model.addInnerDataConnection(alldata.get(10), alldata.get(11), "isPartOf");
		
		//FullName -> First,Middle,Last Name
		main_model.addInnerDataConnection(alldata.get(1), alldata.get(2), "isPartOf");
		main_model.addInnerDataConnection(alldata.get(1), alldata.get(3), "isPartOf");
		main_model.addInnerDataConnection(alldata.get(1), alldata.get(4), "isPartOf");
		
		//First -> LastName
		main_model.addInnerDataConnection(alldata.get(2), alldata.get(4), "isMoreDetailedThan");
		
		
		
		//IDCard -> IDNumber, Father,MotherName, PersonalSign
		main_model.addInnerDataConnection(alldata.get(5), alldata.get(6), "isPartOf");
		main_model.addInnerDataConnection(alldata.get(5), alldata.get(7), "isPartOf");
		main_model.addInnerDataConnection(alldata.get(5), alldata.get(8), "isPartOf");
		main_model.addInnerDataConnection(alldata.get(5), alldata.get(9), "isPartOf");
		
		//IDCard, Passport <- FullName
		main_model.addInnerDataConnection(alldata.get(5), alldata.get(1), "isPartOf");
		main_model.addInnerDataConnection(alldata.get(10), alldata.get(1), "isPartOf");
		
		
		
		//Contact -> Telephone, Email, Fax
		main_model.addInnerDataConnection(alldata.get(12), alldata.get(13), "isA");
		main_model.addInnerDataConnection(alldata.get(12), alldata.get(14), "isA");
		main_model.addInnerDataConnection(alldata.get(12), alldata.get(15), "isA");
		
		
		
		//Location -> Country, City, StreetAddress
		main_model.addInnerDataConnection(alldata.get(16), alldata.get(17), "isA");
		main_model.addInnerDataConnection(alldata.get(16), alldata.get(20), "isA");
		main_model.addInnerDataConnection(alldata.get(16), alldata.get(21), "isA");
		
		//StreetAddress -> StreetNumber,Name
		main_model.addInnerDataConnection(alldata.get(17), alldata.get(18), "isPartOf");
		main_model.addInnerDataConnection(alldata.get(17), alldata.get(19), "isPartOf");
		
		//City <- Country
		main_model.addInnerDataConnection(alldata.get(21), alldata.get(20), "isMoreDetailedThan");
		//StreetAddress <- City
		main_model.addInnerDataConnection(alldata.get(20), alldata.get(17), "isMoreDetailedThan");
		
			
		
		//Age -> IsAdult, ExactAge, DateOfBirth
		main_model.addInnerDataConnection(alldata.get(22), alldata.get(23), "isA");
		main_model.addInnerDataConnection(alldata.get(22), alldata.get(24), "isA");
		main_model.addInnerDataConnection(alldata.get(22), alldata.get(25), "isA");
		
		//DateOfBirth -> DayOfBirth, MonthOfBirth, YearOfBirth
		main_model.addInnerDataConnection(alldata.get(25), alldata.get(26), "isPartOf");
		main_model.addInnerDataConnection(alldata.get(25), alldata.get(27), "isPartOf");
		main_model.addInnerDataConnection(alldata.get(25), alldata.get(28), "isPartOf");
		
		//IDCard, Passport <- DateOfBirth
		main_model.addInnerDataConnection(alldata.get(5), alldata.get(25), "isPartOf");
		main_model.addInnerDataConnection(alldata.get(10), alldata.get(25), "isPartOf");
		
		//Passport <- Country
		main_model.addInnerDataConnection(alldata.get(10), alldata.get(21), "isPartOf");

		//ExactAge <- IsAdult
		main_model.addInnerDataConnection(alldata.get(23), alldata.get(24), "isMoreDetailedThan");
		
		//DateOfBirth <- ExactAge
		main_model.addInnerDataConnection(alldata.get(24), alldata.get(25), "isMoreDetailedThan");

		
		//Billing -> Account
		main_model.addInnerDataConnection(alldata.get(29), alldata.get(30), "isA");
		
		//Account -> AccountNumber, AccountBalance
		main_model.addInnerDataConnection(alldata.get(30), alldata.get(31), "isPartOf");
		main_model.addInnerDataConnection(alldata.get(30), alldata.get(32), "isPartOf");
		
		//AccountBalance <- AccountNumber
		main_model.addInnerDataConnection(alldata.get(31), alldata.get(32), "isMoreDetailedThan");
		
		
		
		///////////////////////////////////////////////
		///////////// S E R V I C E S /////////////////
		///////////////////////////////////////////////

		
		
		//SignInvoice -> PersonalSign, First,Last Name
		main_model.addServiceDataConnection(allservices.get(0), alldata.get(7), "requires");
		main_model.addServiceDataConnection(allservices.get(0), alldata.get(2), "mayRequires");
		main_model.addServiceDataConnection(allservices.get(0), alldata.get(4), "mayRequires");
		
		//LocalInfoFinder -> City
		main_model.addServiceDataConnection(allservices.get(1), alldata.get(20), "requires");
		
		//MapProvision -> City, Country
		main_model.addServiceDataConnection(allservices.get(2), alldata.get(20), "requires");
		main_model.addServiceDataConnection(allservices.get(2), alldata.get(21), "mayRequires");
		
		//ShopFinder -> City 
		main_model.addServiceDataConnection(allservices.get(3), alldata.get(20), "requires");

		//RestaurantFinder -> City, IsAdult, ExactAge
		main_model.addServiceDataConnection(allservices.get(4), alldata.get(20), "requires");
		main_model.addServiceDataConnection(allservices.get(4), alldata.get(23), "mayRequires");
		main_model.addServiceDataConnection(allservices.get(4), alldata.get(24), "mayRequires");
		
		//OrderPlacement -> First,Last Name, Email, AccountInfo
		main_model.addServiceDataConnection(allservices.get(5), alldata.get(2), "requires");
		main_model.addServiceDataConnection(allservices.get(5), alldata.get(4), "requires");
		main_model.addServiceDataConnection(allservices.get(5), alldata.get(14), "requires");
		main_model.addServiceDataConnection(allservices.get(5), alldata.get(31), "mayRequires");
		
		//EntryAllowance -> IsAdult
		main_model.addServiceDataConnection(allservices.get(6), alldata.get(23), "requires");
		
		//CheckoutPay -> AccountNumber, AccountBalance
		main_model.addServiceDataConnection(allservices.get(7), alldata.get(31), "requires");
		main_model.addServiceDataConnection(allservices.get(7), alldata.get(32), "requires");
		
		//CheckoutPayAdult -> IsAdult, AccountNumber, AccountBalance
		main_model.addServiceDataConnection(allservices.get(8), alldata.get(23), "requires");
		main_model.addServiceDataConnection(allservices.get(8), alldata.get(31), "requires");
		main_model.addServiceDataConnection(allservices.get(8), alldata.get(32), "requires");
		
		//AirportCheckIn -> PassportNumber, FirstName, LastName, IDNumber
		main_model.addServiceDataConnection(allservices.get(9), alldata.get(11), "requires");
		main_model.addServiceDataConnection(allservices.get(9), alldata.get(2), "requires");
		main_model.addServiceDataConnection(allservices.get(9), alldata.get(3), "requires");
		main_model.addServiceDataConnection(allservices.get(9), alldata.get(6), "mayRequires");	
		
		//SubscriptionPay -> AccountNumber, AccountBalance, Email
		main_model.addServiceDataConnection(allservices.get(10), alldata.get(31), "requires");
		main_model.addServiceDataConnection(allservices.get(10), alldata.get(32), "requires");
		main_model.addServiceDataConnection(allservices.get(10), alldata.get(14), "mayRequires");

		//SubscriptionSignUp -> FirstName, LastName, Email
		main_model.addServiceDataConnection(allservices.get(11), alldata.get(2), "requires");
		main_model.addServiceDataConnection(allservices.get(11), alldata.get(4), "requires");
		main_model.addServiceDataConnection(allservices.get(11), alldata.get(14), "requires");
		main_model.addServiceDataConnection(allservices.get(11), alldata.get(13), "mayRequires");
		
		//PostalFormFilling -> Country, City, StreetName, StreetNumber, Email, Telephone
		main_model.addServiceDataConnection(allservices.get(12), alldata.get(21), "requires");
		main_model.addServiceDataConnection(allservices.get(12), alldata.get(20), "requires");
		main_model.addServiceDataConnection(allservices.get(12), alldata.get(18), "requires");
		main_model.addServiceDataConnection(allservices.get(12), alldata.get(19), "requires");
		main_model.addServiceDataConnection(allservices.get(12), alldata.get(14), "mayRequires");
		main_model.addServiceDataConnection(allservices.get(12), alldata.get(13), "mayRequires");		
		
		
		//example
		ArrayList<String> service_required_data = new ArrayList<String>();
		service_required_data.add("data:IDCard");
		service_required_data.add("data:Email");
		service_required_data.add("data:IsAdult");

		DecisionMaker filter = new DecisionMaker(main_model.getModel());
		filter.DataMinimizationFilter("service:SubscriptionSignUp", service_required_data);
		
		main_model.exportModel();
				
	}
	

}
