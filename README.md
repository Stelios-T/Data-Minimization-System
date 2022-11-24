# **INTELLIGENT APPLICATION OF THE PRINCIPLE OF DATA MINIMIZATION**

The issue of privacy and data is becoming increasingly legislated and is one of the critical issues that concern modern societies. The degree and processing that has been collected with the development of technology is the main reason. To address these issues, the European Union passed the General Data Protection Regulation. Among the basic principles of the Regulation is the principle of data minimization. The aim of this repository is to present a technical solution to the application of the minimization principle. In this context, it introduces an innovative system that exploits semantic web technologies in order to make real-time decisions about which data satisfy the minimization principle, i.e. are appropriate, relevant and necessary for the purposes for which they are submitted to processing.

## RDF MODEL 

An RDF model was constructed, which represents a knowledge graph that presents the relationships between the types of personal data and the types of services with them.
More specifically, two graphs were created which are related to each other. A PersonalDataGraph graph and a ServicesGraph. Within the graph some namespace identifiers have been given which distinguish when the elements of the graph are data types or services. For this reason we have data: for the types of personal data and service: for the types of services.

#### PersonalDataGraph:

![Screenshot 2022-09-21 143735](https://user-images.githubusercontent.com/67365815/203770295-b675b1de-0bbe-4056-bf0b-a3dfd6dc45a4.jpg)


#### ServicesGraph:

![Screenshot 2022-09-21 152843](https://user-images.githubusercontent.com/67365815/203770345-dabfc826-7e60-4978-894e-fa1379012491.jpg)


#### Final Model:
![Screenshot 2022-09-09 145404](https://user-images.githubusercontent.com/67365815/203770370-d809e080-2e75-418d-b08d-b2a37fde2509.jpg)



## How to run

The system is build as a MAVEN project. Download the code and run it as is.


## Testing

```
ArrayList<String> service_required_data = new ArrayList<String>();
```
test 

```
service_required_data.add("data:IDCard");
service_required_data.add("data:Email");
service_required_data.add("data:IsAdult");
```

```
DecisionMaker filter = new DecisionMaker(main_model.getModel());
```
sfsf

```
filter.DataMinimizationFilter("service:SubscriptionSignUp", service_required_data);
```

