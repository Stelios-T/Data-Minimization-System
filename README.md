# Intelligent Application of the Principle of Data Minimization

A knowledge-graph based system that decides, in real time, whether the personal data a user offers to a service are **adequate, relevant and limited to what is necessary** for that service, as required by the GDPR data minimization principle (Art. 5(1)(c)).


Given a service and a set of offered data types, the system answers three questions:

1. Which offered data are needed as they are, and which are not needed at all?
2. Which offered data **contain** what is needed but also disclose more, and what narrower data should be requested instead?
3. Which required data are still missing?

```
$ java -jar target/dms.jar --service SubscriptionSignUp --data IDCard,Email,IsAdult

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

## Contents

- [Quick start](#quick-start)
- [Background](#background)
- [The knowledge graph](#the-knowledge-graph)
- [The decision](#the-decision)
- [Command-line usage](#command-line-usage)
- [Using it from Java](#using-it-from-java)
- [Extending the model](#extending-the-model)
- [Project layout](#project-layout)
- [Troubleshooting](#troubleshooting)

## Quick start

Requirements: JDK 17 or newer and Maven 3.6 or newer.

```bash
git clone <this repository>
cd Data-Minimization-System
mvn package                    # compiles, runs the tests, builds target/dms.jar
java -jar target/dms.jar       # runs the example above
java -jar target/dms.jar --list
java -jar target/dms.jar --service CheckoutPayAdult --data DateOfBirth,Account
```

## Background

Privacy is one of the most heavily legislated issues of modern societies, driven by the volume of personal data that technology makes it possible to collect and process. The European Union's General Data Protection Regulation sets out a small number of principles that every processing operation must respect. One of them, data minimization, states that personal data must be adequate, relevant and limited to what is necessary for the purposes for which they are processed.

The principle is easy to state and hard to apply: a service that needs to know whether a customer is an adult can be handed a full identity card, which also reveals a name, an exact date of birth, a parent's names and an ID number. This repository shows how semantic web technologies can make that judgement automatically. A knowledge graph describes the types of personal data, how they relate to each other, and which data each service needs. A decision component queries the graph to classify every offered datum and to report what is missing.

## The knowledge graph

The graph is written in Turtle in [`src/main/resources/model.ttl`](src/main/resources/model.ttl) and is the single source of truth: the Java code loads it at start-up and contains no knowledge of its own. It has two vocabularies, `data:` for personal data types and `srv:` for services, and five relations.

| Relation                        | Meaning                                                | Consequence for minimization                                                            |
| ------------------------------- | ------------------------------------------------------ | --------------------------------------------------------------------------------------- |
| `X data:isA Y`                | X is a specialisation of Y. *Email is a Contact.*    | Providing X **satisfies** a need for Y. Providing Y does **not** provide X. |
| `X data:isPartOf Y`           | X is contained in Y. *IDNumber is part of IDCard.*   | Providing Y **also discloses** X.                                                 |
| `X data:isMoreDetailedThan Y` | Y can be derived from X. *IsAdult from DateOfBirth.* | Providing X **also discloses** Y.                                                 |
| `S srv:requires D`            | S cannot work without D.                               | D must be covered by something offered.                                                 |
| `S srv:mayRequires D`         | S can optionally use D.                                | D is accepted but never demanded.                                                       |

`isPartOf` and `isMoreDetailedThan` are declared `owl:TransitiveProperty`. The code never walks the graph by hand. Everything that handing over a datum discloses or satisfies is one SPARQL 1.1 property-path query against an in-memory rdf4j store:

```sparql
SELECT DISTINCT ?x WHERE { ?p (^data:isPartOf|data:isMoreDetailedThan)*/data:isA* ?x }
```

Containment and derivation are followed first and generalisation last. The other order would be unsound: having "a contact" does not mean having an email address.

The model contains 33 personal data types in five groups (identity, contact, location, age, billing) and 13 services. The diagrams below were drawn for the original version of the model.

#### Personal data graph

![Personal data graph](https://user-images.githubusercontent.com/67365815/203770295-b675b1de-0bbe-4056-bf0b-a3dfd6dc45a4.jpg)

#### Services graph

![Services graph](https://user-images.githubusercontent.com/67365815/203770345-dabfc826-7e60-4978-894e-fa1379012491.jpg)

#### Combined model

![Combined model](https://user-images.githubusercontent.com/67365815/203770370-d809e080-2e75-418d-b08d-b2a37fde2509.jpg)

Three corrections were made to the model after the diagrams were drawn. `AirportCheckIn` requires `LastName`, not `MiddleName`. The edges `LastName isMoreDetailedThan FirstName` and `AccountBalance isMoreDetailedThan AccountNumber` were removed, because neither datum can be derived from the other.

## The decision

For a service and a set of offered data the filter returns a `Decision` holding one assessment per distinct datum.

| Status        | Meaning                                                                                                             | Action                                    |
| ------------- | ------------------------------------------------------------------------------------------------------------------- | ----------------------------------------- |
| `REQUIRED`  | Named by the service as required.                                                                                   | Accept.                                   |
| `OPTIONAL`  | Named by the service as optional.                                                                                   | Accept.                                   |
| `SATISFIES` | Not named, but discloses only data the service needs: a specialisation, or a container of exactly the needed parts. | Accept.                                   |
| `SUPERSET`  | Discloses needed data **and** data the service does not need.                                                 | Request the listed narrower data instead. |
| `EXCESSIVE` | Discloses nothing the service needs.                                                                                | Reject.                                   |
| `UNKNOWN`   | Not a personal data type in the graph.                                                                              | Reject.                                   |

The decision also lists the required data that nothing offered covers, and an overall verdict:

| Verdict                      | All required data covered | Nothing excessive offered |
| ---------------------------- | ------------------------- | ------------------------- |
| `COMPLIANT`                | yes                       | yes                       |
| `EXCESSIVE`                | yes                       | no                        |
| `INCOMPLETE`               | no                        | yes                       |
| `INCOMPLETE_AND_EXCESSIVE` | no                        | no                        |

## Command-line usage

```
java -jar target/dms.jar --service NAME --data A,B,C [--model FILE]
java -jar target/dms.jar --list [--model FILE]
java -jar target/dms.jar --help

  --service NAME   service the data are submitted to, e.g. SubscriptionSignUp
  --data A,B,C     personal data types offered, comma separated, e.g. IDCard,Email,IsAdult
  --model FILE     ontology file to use instead of the bundled model.ttl
  --list           print all services and the data they need
```

With no arguments the shipped example is run. The exit code is `0` for `COMPLIANT`, `2` for any other verdict and `1` for a usage error, so the tool can be used from scripts and CI.

The same commands work without building the jar, through the Maven exec plugin:

```bash
mvn -q exec:java -Dexec.args="--service SubscriptionSignUp --data IDCard,Email,IsAdult"
```

Run the tests alone with `mvn test`.

## Using it from Java

```java
import dataminimization.*;
import java.util.List;

try (KnowledgeGraph graph = KnowledgeGraph.loadDefault()) {           // or KnowledgeGraph.load(Path.of("my.ttl"))
    DataMinimizationFilter filter = new DataMinimizationFilter(graph);

    Decision d = filter.evaluate(
            Vocabulary.service("CheckoutPayAdult"),
            List.of(Vocabulary.data("DateOfBirth"), Vocabulary.data("Account")));

    d.verdict();                              // EXCESSIVE
    d.assessments().get(0).status();          // SUPERSET
    d.assessments().get(0).substitutes();     // {IsAdult}
    d.missing();                              // {}
    System.out.print(d.report());             // the text shown above
}
```

`evaluate` throws `IllegalArgumentException` for a service that is not in the graph. Unknown data types are reported with status `UNKNOWN` rather than thrown, so a single bad field does not hide the assessment of the others.

## Extending the model

Add data types or services to `model.ttl`. No Java changes are needed.

```turtle
data:LoyaltyCardNumber a data:PersonalData ; data:isPartOf data:Account .

srv:LoyaltyLookup a srv:Service ;
    srv:requires    data:LoyaltyCardNumber ;
    srv:mayRequires data:Email .
```

Then either rebuild, or point the tool at your file without rebuilding:

```bash
java -jar target/dms.jar --model src/main/resources/model.ttl --service LoyaltyLookup --data Account
```

When adding relations, keep the directions from the table above. In particular, use `isMoreDetailedThan` only when the object can genuinely be computed from the subject.

## Project layout

```
src/main/resources/model.ttl                 the knowledge graph
src/main/java/dataminimization/
    Vocabulary.java                          IRIs and namespaces
    KnowledgeGraph.java                      in-memory RDF store and the SPARQL queries
    DataMinimizationFilter.java              the decision procedure
    Decision.java, DataAssessment.java       result types, with a text report
    Status.java, Verdict.java                the classifications above
    Main.java                                command-line interface
src/test/java/dataminimization/              scenario tests (18), run by mvn test / mvn package
```

## Troubleshooting

- **`mvn: command not found`**: install Maven (`brew install maven` on macOS, `sdk install maven` with SDKMAN, or your package manager).
- **`release version 17 not supported`**: the JDK is older than 17. Check with `java -version`.
- **`Unknown service`**: service and data names are case-sensitive local names, without a prefix. Use `--list` to see the valid ones.
- **The first `mvn` run is slow**: Maven downloads rdf4j and its dependencies once; later runs are fast.
