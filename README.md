# **INTELLIGENT APPLICATION OF THE PRINCIPLE OF DATA MINIMIZATION**

The issue of privacy and data is becoming increasingly legislated and is one of the critical issues that concern modern societies. The degree and processing that has been collected with the development of technology is the main reason. To address these issues, the European Union passed the General Data Protection Regulation. Among the basic principles of the Regulation is the principle of data minimization (Art. 5(1)(c)): personal data must be *adequate, relevant and limited to what is necessary* for the purposes for which they are processed.

This repository presents a technical solution for applying the minimization principle. A knowledge graph, expressed in RDF, describes the types of personal data, how they relate to each other, and which data each service needs. A decision component queries that graph to decide, for every datum a user offers to a service, whether it is necessary, whether it is more than necessary, or whether it should be rejected, and which required data are still missing.

## RDF MODEL

The knowledge graph lives in [`src/main/resources/model.ttl`](src/main/resources/model.ttl) (Turtle). It uses two vocabularies, `data:` for personal data types and `srv:` for services, and five relations:

| Relation | Meaning | Consequence for minimization |
|---|---|---|
| `X data:isA Y` | X is a specialisation of Y (Email is a Contact) | Providing X **satisfies** a need for Y. Providing Y does *not* provide X. |
| `X data:isPartOf Y` | X is contained in Y (IDNumber is part of IDCard) | Providing Y **also discloses** X. |
| `X data:isMoreDetailedThan Y` | Y can be derived from X (IsAdult from DateOfBirth) | Providing X **also discloses** Y. |
| `S srv:requires D` | S cannot work without D | D must be covered. |
| `S srv:mayRequires D` | S can optionally use D | D is accepted but never demanded. |

`isPartOf` and `isMoreDetailedThan` are declared as `owl:TransitiveProperty`. The decision component does not walk the graph in Java; it asks the triple store with SPARQL 1.1 property paths:

```sparql
# everything that handing over ?p discloses or satisfies
SELECT DISTINCT ?x WHERE { ?p (^data:isPartOf|data:isMoreDetailedThan)*/data:isA* ?x }
```

Decomposition and derivation are followed first, generalisation last. Following them in the other order would be unsound: having "a contact" does not mean having an email address.

#### PersonalDataGraph:

![Screenshot 2022-09-21 143735](https://user-images.githubusercontent.com/67365815/203770295-b675b1de-0bbe-4056-bf0b-a3dfd6dc45a4.jpg)

#### ServicesGraph:

![Screenshot 2022-09-21 152843](https://user-images.githubusercontent.com/67365815/203770345-dabfc826-7e60-4978-894e-fa1379012491.jpg)

#### Final Model:
![Screenshot 2022-09-09 145404](https://user-images.githubusercontent.com/67365815/203770370-d809e080-2e75-418d-b08d-b2a37fde2509.jpg)

The diagrams predate three corrections in `model.ttl`: `AirportCheckIn` requires `LastName` (not `MiddleName`), and the edges `LastName isMoreDetailedThan FirstName` and `AccountBalance isMoreDetailedThan AccountNumber` were removed because neither datum can be derived from the other.

## Decision

For a service and a set of offered data the filter returns a `Decision` with one assessment per datum:

| Status | Meaning |
|---|---|
| `REQUIRED` | Named by the service as required. |
| `OPTIONAL` | Named by the service as optional. |
| `SATISFIES` | Not named, but discloses only data the service needs (a specialisation, or a container of exactly the needed parts). |
| `SUPERSET` | Discloses needed data *and* data the service does not need. The decision names the narrower data to request instead. |
| `EXCESSIVE` | Discloses nothing the service needs. Reject. |
| `UNKNOWN` | Not a personal data type in the knowledge graph. Reject. |

plus the set of required data that nothing covers, and an overall verdict: `COMPLIANT`, `EXCESSIVE`, `INCOMPLETE` or `INCOMPLETE_AND_EXCESSIVE`.

## How to run

Requirements: JDK 17+ and Maven 3.

```bash
mvn test                       # run the test suite
mvn -q exec:java               # run the shipped example
mvn -q exec:java -Dexec.args="--service SubscriptionSignUp --data IDCard,Email,IsAdult"
mvn -q exec:java -Dexec.args="--list"                 # all services and their needs
mvn -q exec:java -Dexec.args="--model my.ttl --service X --data A,B"   # your own ontology
```

The exit code is 0 for `COMPLIANT`, 2 for any other verdict and 1 for a usage error, so the command can be used from scripts.

Example output:

```
Service: SubscriptionSignUp
  requires:    Email, FirstName, LastName
  may require: TelephoneNumber

Provided data:
  IDCard   SUPERSET  provides FirstName, LastName but also discloses DateOfBirth, DayOfBirth, ExactAge, FatherName, FullName, IDNumber, IsAdult, MiddleName, MonthOfBirth, MotherName, PersonalSign, YearOfBirth
           -> request FirstName, LastName instead
  Email    REQUIRED  named by the service
  IsAdult  EXCESSIVE not needed by this service -> reject

Missing required data: none
Verdict: EXCESSIVE
```

## Using it from Java

```java
try (KnowledgeGraph graph = KnowledgeGraph.loadDefault()) {
    DataMinimizationFilter filter = new DataMinimizationFilter(graph);
    Decision d = filter.evaluate(Vocabulary.service("CheckoutPayAdult"),
                                 List.of(Vocabulary.data("DateOfBirth"), Vocabulary.data("Account")));
    d.verdict();                 // EXCESSIVE
    d.assessments().get(0);      // DateOfBirth: SUPERSET, substitutes = {IsAdult}
    d.missing();                 // {}
}
```

## Project layout

```
src/main/resources/model.ttl                 the knowledge graph (edit this to add data types or services)
src/main/java/dataminimization/
    Vocabulary.java                          IRIs and namespaces
    KnowledgeGraph.java                      in-memory RDF store + SPARQL queries
    DataMinimizationFilter.java              the decision procedure
    Decision.java, DataAssessment.java       result types, with a text report
    Status.java, Verdict.java
    Main.java                                command-line interface
src/test/java/dataminimization/              scenario tests
```

## Extending the model

Add a new data type or service to `model.ttl`; no Java changes are needed:

```turtle
data:LoyaltyCardNumber a data:PersonalData ; data:isPartOf data:Account .

srv:LoyaltyLookup a srv:Service ;
    srv:requires data:LoyaltyCardNumber .
```
