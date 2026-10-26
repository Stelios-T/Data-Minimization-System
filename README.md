# Intelligent Application of the Principle of Data Minimization

[![CI](https://github.com/Stelios-T/Data-Minimization-System/actions/workflows/ci.yml/badge.svg)](https://github.com/Stelios-T/Data-Minimization-System/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/code-MIT-blue.svg)](LICENSE)
[![License: CC BY 4.0](https://img.shields.io/badge/ontology-CC%20BY%204.0-lightgrey.svg)](LICENSE-ONTOLOGY)

**How can a service get the information it needs without collecting more personal data than necessary?**

This project explores one way to answer that question.

It is a knowledge-graph-based system that evaluates personal data disclosures against the requirements of a service. Its goal is to help services follow the GDPR principle of data minimization (Article 5(1)(c)): personal data should be adequate, relevant, and limited to what is necessary for the purpose for which they are processed.

Instead of treating every data type as an isolated piece of information, the system understands how different types of data relate to one another. For example, a date of birth can be used to determine whether someone is an adult, while an identity card may reveal a person's name, date of birth, and identification number.

This makes it possible to ask a simple but important question: **Can we achieve the same purpose by disclosing less information?**

Given a service, an optional purpose, the conditions that currently apply, and the data being offered, the system can determine:

- **What is needed:** Which data satisfy the service's requirements?
- **What reveals too much:** Which data disclose additional information that the service does not need?
- **What to request instead:** Can a broader or more detailed piece of data be replaced with a narrower alternative? What is the disclosure cost of each option?
- **What is missing:** Which required data have not been provided?

The system also supports the user's perspective. It can examine the data available on a user's device and work out what to extract, derive, or generalise locally before anything is sent to a service. It can then apply that plan to an actual JSON payload.

The aim is to make data minimization something that can be evaluated and applied systematically, rather than something that depends entirely on manual inspection.

## See it in action

Suppose someone wants to create an account. The service needs their first name, last name, and email address.

Instead of providing those details directly, the person offers an identity card, an email address, and an indication that they are an adult.

The identity card contains the names the service needs, but it also reveals other information. The adult indication is unnecessary for account creation.

The system identifies both issues:

```text
$ java -jar target/dms.jar --service SubscriptionSignUp --data IDCard,Email,IsAdult

Service: SubscriptionSignUp
  purpose:     all (AccountCreation, CustomerSupport)
  requires:    Email, FirstName, LastName
  may require: TelephoneNumber

Provided data:
  IDCard   SUPERSET   cost 29 provides FirstName, LastName but also discloses DateOfBirth, DayOfBirth, ExactAge, FatherName, FullName, IDNumber, IsAdult, MiddleName, MonthOfBirth, MotherName, PersonalSign, YearOfBirth
           -> request FirstName, LastName instead; options: FirstName, LastName (cost 2); FullName (cost 5); Passport (cost 23)
  Email    REQUIRED   cost 2  named by the service
  IsAdult  EXCESSIVE  cost 1  not needed by this service -> reject

Missing required data: none
Minimal disclosure: Email, FirstName, LastName (cost 4); offered cost 32
Verdict: EXCESSIVE
```

The result shows that the service already has everything it needs, but the disclosure contains unnecessary information.

Rather than sharing the identity card, the user could provide just their first name, last name, and email address.

The example illustrates the project's central idea: **satisfy the service's legitimate data requirements while disclosing as little additional information as possible.**

## Contents

- [Quick start](#quick-start)
- [Background](#background)
- [The knowledge graph](#the-knowledge-graph)
- [How decisions are made](#how-decisions-are-made)
- [Planning disclosure on the user's side](#planning-disclosure-on-the-users-side)
- [Minimizing a real JSON payload](#minimizing-a-real-json-payload)
- [Validating the knowledge graph](#validating-the-knowledge-graph)
- [Evaluation](#evaluation)
- [Command-line usage](#command-line-usage)
- [Machine-readable output](#machine-readable-output)
- [HTTP API and demo page](#http-api-and-demo-page)
- [Using the system from Java](#using-the-system-from-java)
- [Extending the model](#extending-the-model)
- [Project layout](#project-layout)
- [Development and testing](#development-and-testing)
- [Citing and licenses](#citing-and-licenses)
- [Troubleshooting](#troubleshooting)

## Quick start

### Requirements

You will need:

- JDK 17 or newer
- Maven 3.6 or newer

### Build and run

Clone the repository, build the project, and run the example:

```bash
git clone <this repository>
cd Data-Minimization-System

mvn package
java -jar target/dms.jar
```

The build compiles the code, runs the tests, and creates `target/dms.jar`.

Here are a few other commands to try:

```bash
# List the services, data types, purposes, and conditions
java -jar target/dms.jar --list

# Evaluate a disclosure for a restaurant-finding service
java -jar target/dms.jar --service RestaurantFinder --data City --context ContainsAlcohol

# Plan a minimal disclosure using data already held by the user
java -jar target/dms.jar --service CheckoutPayAdult --plan --held DateOfBirth,Account

# Start the web demo and HTTP API
java -jar target/dms.jar --serve
```

After starting the server, open `http://127.0.0.1:7070` in your browser to explore the demo.

## Background

Personal data are collected and processed in many everyday interactions, from creating an account to paying for an order or verifying someone's age.

Often, a service needs only a small piece of information, but the way that information is provided reveals much more.

For example, a service selling age-restricted products may need to know whether a customer is an adult. A full identity card can establish that fact, but it may also reveal the customer's full name, exact date of birth, parents' names, and identification number.

Could the service verify adulthood without receiving all that information?

The GDPR addresses this problem through the principle of data minimization. Under Article 5(1)(c), personal data must be adequate, relevant, and limited to what is necessary in relation to the purposes for which they are processed.

The principle is straightforward, but applying it consistently can be difficult. A service's requirements depend on what it does, why it does it, and the circumstances in which the data are used. Information that is necessary in one situation may be unnecessary in another.

This project explores how semantic web technologies can help make those decisions more systematic.

A knowledge graph describes:

- The types of personal data that exist.
- How different data types relate to one another.
- What each service needs for each processing purpose.
- Which additional requirements apply under particular conditions.
- How much information a disclosure reveals.

The Java application queries that graph to assess proposed disclosures, identify less revealing alternatives, and determine whether required information is missing.

The result is a model-driven approach to data minimization: the knowledge graph describes the domain, while the Java code applies the decision rules.

## The knowledge graph

The knowledge graph is defined in Turtle format in [`src/main/resources/model.ttl`](src/main/resources/model.ttl).

It is the source of truth for the system's domain knowledge. When the application starts, it loads the graph into memory. The Java code does not maintain a separate, hard-coded list of data types or service requirements.

The model uses two namespaces:

- `data:` describes personal data and the relationships between data types.
- `srv:` describes services, purposes, processing activities, and conditions.

### How data types relate to one another

The graph uses relationships to describe what a piece of data contains, what it represents, and what can be derived from it.

| Relationship | Meaning | Example and effect |
|---|---|---|
| `X data:isA Y` | X is a more specific kind of Y. | An email address is a type of contact information. Providing an email address can satisfy a request for contact information, but providing contact information does not necessarily provide an email address. |
| `X data:isPartOf Y` | X is contained within Y. | An ID number is part of an identity card. Providing the card also reveals the ID number. |
| `X data:isMoreDetailedThan Y` | Y can be derived from X. | A date of birth can be used to determine whether someone is an adult. Providing the date of birth reveals more than providing only the adult-status result. |
| `X data:sensitivity n` | Assigns a disclosure weight from 1 to 5. | A higher weight means a greater disclosure cost. Weight 5 is used for special-category data under Article 9. |

These relationships allow the system to reason about information beyond the exact data type named in a request.

For example, if a service needs an ID number and a user provides an identity card, the system can recognise that the card contains the required number. It can also recognise that the card reveals additional information.

The distinction between containment, derivation, and generalisation matters. An email address can satisfy a request for contact information, but general contact information cannot automatically satisfy a request for an email address.

### How the relationships are queried

The relationships `data:isPartOf` and `data:isMoreDetailedThan` are declared as transitive OWL properties.

Rather than manually traversing the graph in Java, the application uses a SPARQL 1.1 property-path query against an in-memory rdf4j store:

```sparql
SELECT DISTINCT ?x WHERE {
  ?p (^data:isPartOf|data:isMoreDetailedThan)*/data:isA* ?x
}
```

The query follows containment and derivation relationships before following generalisation relationships. This ordering prevents the system from incorrectly treating a broad category as proof that a specific piece of information is available.

### How services and purposes are represented

Data minimization must be evaluated in relation to a purpose. For that reason, the model connects service requirements to individual processing activities rather than assuming that every service always needs the same information.

Consider this example:

```turtle
srv:SubscriptionSignUp a srv:Service ;
    srv:hasProcessing
        srv:SubscriptionSignUp_AccountCreation ,
        srv:SubscriptionSignUp_CustomerSupport .

srv:SubscriptionSignUp_AccountCreation a srv:Processing ;
    srv:hasPurpose srv:AccountCreation ;
    srv:requires data:FirstName ,
                data:LastName ,
                data:Email .

srv:SubscriptionSignUp_CustomerSupport a srv:Processing ;
    srv:hasPurpose srv:CustomerSupport ;
    srv:mayRequires data:TelephoneNumber .
```

Here, account creation requires a first name, last name, and email address. Customer support may use a telephone number, but it is not necessarily required.

The model uses the following relationships:

| Relationship | Meaning |
|---|---|
| `S srv:hasProcessing P` | Service S performs processing activity P. |
| `P srv:hasPurpose U` | Processing P serves purpose U. |
| `P srv:requires D` | Processing P requires data D. |
| `P srv:mayRequires D` | Processing P may optionally use data D. |
| `P srv:requiresWhen [ srv:condition C ; srv:requires D ]` | Processing P requires data D when condition C holds. |

For example, a service might require an adulthood check only when an order contains alcohol. The requirement can be activated by passing `ContainsAlcohol` as a condition.

If several conditions are defined for the same conditional requirement, any one of those conditions is sufficient to activate it.

By default, the system evaluates the combined requirements of all purposes associated with a service. You can use `--purpose` to evaluate one specific purpose and `--context` to provide the conditions that currently apply.

For simpler models, requirements can also be attached directly to a service.

### Compatibility with the W3C Data Privacy Vocabulary

The model is aligned with the [W3C Data Privacy Vocabulary (DPV)](https://w3id.org/dpv).

Its core classes map to corresponding DPV concepts:

- `data:PersonalData` maps to `dpv:PersonalData`.
- `srv:Service` maps to `dpv:Service`.
- `srv:Purpose` maps to `dpv:Purpose`.
- `srv:Processing` maps to `dpv:Process`.
- `srv:Condition` maps to `dpv:Context`.

The `srv:requires` and `srv:mayRequires` properties are sub-properties of `dpv:hasPersonalData`, with the corresponding necessity classifications `dpv:Required` and `dpv:Optional`.

Where a data type has a corresponding concept in DPV-PD, the model records an appropriate `skos:exactMatch`, `skos:closeMatch`, or `skos:broadMatch`.

This provides a way to connect the project's model to other DPV-based tools without having to import the entire DPV vocabulary.

### What the model contains

The bundled knowledge graph currently includes:

- 33 personal data types across five groups: identity, contact, location, age, and billing.
- 13 services.
- 11 purposes.
- 3 conditions.

You can generate diagrams directly from the model using `--diagrams`. The generated Mermaid diagrams are stored in [`docs/model.md`](docs/model.md) and rendered by GitHub. Automated tests and CI checks ensure that the diagrams stay in sync with the model.

The original thesis diagrams date from before purposes, conditions, and sensitivity weights were added. The model has also been corrected since then: `AirportCheckIn` now requires `LastName` rather than `MiddleName`, and two unsound derivation relationships have been removed (`LastName` being more detailed than `FirstName`, and `AccountBalance` being more detailed than `AccountNumber`).

The original combined diagram is retained here for reference:

![Original combined model (2022)](https://user-images.githubusercontent.com/67365815/203770370-d809e080-2e75-418d-b08d-b2a37fde2509.jpg)

## How decisions are made

The decision component evaluates a proposed disclosure against the requirements of a service.

For each distinct data type being offered, it determines whether the data are required, acceptable, unnecessarily revealing, or unrelated to the service's needs.

### Understanding the results

| Status | Meaning | Recommended action |
|---|---|---|
| `REQUIRED` | The service explicitly requires this data, either directly or because an applicable condition makes it necessary. | Accept. |
| `OPTIONAL` | The service identifies this data as optional. | Accept. |
| `SATISFIES` | The data are not explicitly named, but they satisfy a requirement without revealing anything beyond what was requested. This includes a more specific type of a requested category, such as an email address for contact information, or a composite containing exactly the required parts. | Accept. |
| `SUPERSET` | The data satisfy one or more requirements but also reveal additional information. | Request narrower data instead. The system ranks alternative ways to satisfy the requirements. |
| `EXCESSIVE` | The data do not satisfy any of the service's requirements. | Reject. |
| `UNKNOWN` | The data type is not defined in the knowledge graph. | Reject or add the missing type to the model. |

For example, an identity card may receive the `SUPERSET` status when a service needs only a first and last name. The card provides those names, but it also reveals other personal details.

The system can recommend requesting the first and last names separately, or another less revealing alternative.

### What else does the system report?

Beyond the status of each offered datum, the result includes:

- **Missing requirements:** Required data that are not covered by anything offered, including the conditions that made them necessary.
- **Offered disclosure cost:** The total cost of disclosing the offered data.
- **Minimal disclosure:** The lowest-cost combination of data types that covers all requirements.
- **Minimal disclosure cost:** The cost of that combination.
- **Overall verdict:** Whether the proposed disclosure satisfies the model's requirements without unnecessary data.

The minimal disclosure is calculated as a weighted set-cover problem. In other words, the system searches for the lowest-cost combination of data types that covers every requirement.

### Understanding the overall verdict

| Verdict | All required data covered? | Any excessive data offered? |
|---|---|---|
| `COMPLIANT` | Yes | No |
| `EXCESSIVE` | Yes | Yes |
| `INCOMPLETE` | No | No |
| `INCOMPLETE_AND_EXCESSIVE` | No | Yes |

These verdicts describe the outcome of the system's model-based evaluation. They should not be interpreted as a definitive legal determination that a real-world processing activity complies with the GDPR. That conclusion also depends on whether the model accurately represents the service, its purposes, and the applicable circumstances.

## Planning disclosure on the user's side

The service-side evaluation asks whether the data being offered are appropriate.

The disclosure planner takes the opposite perspective: **if the user already has the necessary information, how can they satisfy the service's requirements while revealing as little as possible?**

The planner examines the data held by a user agent and creates a plan for each requirement. Whenever possible, the required information is extracted or derived locally, so that only the result needs to leave the device.

The planner supports four kinds of actions:

| Action | Meaning | Example |
|---|---|---|
| `DISCLOSE` | Send the held datum because it is exactly what the service requires. | Send an email address when an email address is required. |
| `EXTRACT` | Extract a required piece of information from a larger datum. | Extract a last name from an identity card. |
| `DERIVE` | Calculate the required information locally from another datum. | Determine whether someone is an adult from their date of birth. |
| `PRESENT_AS` | Use a held datum that is a more specific type of the required category. | Provide an email address to satisfy a request for contact information. |

If the user agent holds data types that do not exist in the knowledge graph, the planner reports them as ignored rather than silently discarding them.

### Example

Suppose a payment service needs an account number, an account balance, and confirmation that the customer is an adult.

The user holds an account object and their date of birth.

Run:

```bash
java -jar target/dms.jar --service CheckoutPayAdult --plan --held DateOfBirth,Account
```

The planner produces:

```text
Service: CheckoutPayAdult
  purpose:     all
  requires:    AccountBalance, AccountNumber, IsAdult
  may require: -
  held:        Account, DateOfBirth

Disclosure plan:
  EXTRACT   extract AccountBalance from Account and send only that
  EXTRACT   extract AccountNumber from Account and send only that
  DERIVE    derive IsAdult from DateOfBirth via ExactAge locally and send the result

Cannot be provided: none
Disclosed: AccountBalance, AccountNumber, IsAdult (cost 8); sending everything held would cost 21
```

The user can satisfy the requirements without sending the entire account object or revealing their date of birth.

This is the negotiation model proposed by the thesis:

1. The service publishes its purposes, requirements, optional data, and applicable conditions.
2. The user's agent examines the available data locally.
3. The agent determines what to extract or calculate.
4. Only the resulting disclosure is sent to the service.

This approach is compatible in principle with selective-disclosure mechanisms used by technologies such as the EU Digital Identity Wallet and SD-JWT credentials, where a holder can reveal individual claims or supported predicates rather than an entire credential.

The planner itself does not implement a credential protocol or cryptographic proof system. It determines the data-disclosure plan that such a system could help carry out.

## Minimizing a real JSON payload

The planner describes what should be disclosed. The payload minimizer takes the next step and applies a minimization decision to an actual JSON object.

Use `--minimize` with a JSON payload and a mapping file.

The mapping file tells the system which personal data type each JSON field represents. Using the knowledge graph and the available value transformations, the minimizer can:

- Keep fields that satisfy the service's requirements.
- Remove fields that are not needed.
- Replace overly revealing values with narrower values that can be derived locally.
- Extract values from nested objects when the model describes the corresponding containment relationships.
- Report values that must be requested because they cannot be derived from the available payload.

For example, the minimizer may be able to derive an `isAdult` flag from a date of birth, split a full name into first and last names, or extract an account number from an account object.

It does not invent values that it cannot derive. Instead, it reports that the value needs to be requested.

### Run the minimizer

```bash
java -jar target/dms.jar \
     --service SubscriptionSignUp \
     --minimize examples/signup-payload.json \
     --mapping examples/signup-mapping.json
```

The reduced JSON payload is printed to standard output, while the per-field actions are written to standard error.

Fields without a mapping are dropped by default. To preserve them, pass `--keep-unmapped`.

Age-related calculations use the current date in UTC. If you use `PayloadMinimizer` directly from Java, you can provide a `java.time.Clock` to control the date. Custom transformations can be registered through `ValueTransformers.Builder`.

**Important:** The minimizer can only produce correct results when the mappings, relationships, and available transformations accurately describe the payload. Review the generated output before using it in a real data-processing workflow.

## Validating the knowledge graph

A knowledge graph is only useful if its relationships and requirements make sense.

The `--validate` command checks the model for structural problems and modelling mistakes before you rely on its decisions.

```bash
java -jar target/dms.jar --validate
```

Validation combines SHACL shapes with additional checks implemented in Java.

The SHACL rules in `src/main/resources/shapes.ttl` check requirements such as:

- Every data type has exactly one sensitivity value between 1 and 5.
- Relationships point to declared data types.
- Every processing activity has exactly one purpose.

The additional structural checks look for problems that SHACL alone does not express in this model, including:

- Cycles in containment or derivation relationships.
- Requirements made redundant because another required datum already discloses them.
- Optional data that are already disclosed by required data.
- References to missing model elements.
- Processing activities that are never used.
- Services with no defined data requirements.

Errors produce exit code `2`. Warnings indicate modelling issues that may deserve attention, even when they do not make the model invalid.

The bundled model passes validation.

The validator has also helped uncover genuine modelling mistakes. For example, an earlier version of the model required `Country` alongside `City`, even though the graph already represented `Country` as information implied by `City`. The validator identified this redundancy.

## Evaluation

The project includes an evaluation framework for testing both the quality of the decisions and the performance of the implementation.

Run:

```bash
java -jar target/dms.jar --evaluate
```

The evaluation uses:

- 38 labelled scenarios for disclosure decisions.
- 5 scenarios for disclosure planning.
- An exact-match baseline for comparison.
- The bundled knowledge graph.
- Synthetic graphs containing between 30 and 2,000 data types for performance measurements.

The baseline accepts only the exact data types named by a service. If a required type is not offered literally, the baseline reports it as missing, even when a related data type could satisfy the requirement.

This provides a simple point of comparison for assessing the benefits of reasoning about data relationships.

The results are written to:

- [`docs/evaluation.md`](docs/evaluation.md)
- [`docs/evaluation.csv`](docs/evaluation.csv)

The same labelled scenarios are used by `ScenarioTest`, so they also serve as regression tests. Changes to the decision procedure must continue to pass these scenarios.

Performance measurements depend on the environment in which the evaluation runs. Treat timing results as measurements of the tested setup rather than universal performance guarantees.

## Command-line usage

The application supports several modes. Each invocation runs exactly one mode.

### Evaluate a disclosure

```bash
java -jar target/dms.jar \
  --service NAME \
  --data A,B,C \
  [--purpose NAME] \
  [--context C1,C2] \
  [--json | --rdf] \
  [--model FILE]
```

### Plan a disclosure

```bash
java -jar target/dms.jar \
  --service NAME \
  --plan \
  --held A,B,C \
  [--include-optional] \
  [--purpose NAME] \
  [--context C1,C2] \
  [--json]
```

### Minimize a JSON payload

```bash
java -jar target/dms.jar \
  --service NAME \
  --minimize payload.json \
  --mapping mapping.json \
  [--keep-unmapped] \
  [--purpose NAME] \
  [--context C1,C2] \
  [--json]
```

### List the available model elements

```bash
java -jar target/dms.jar --list [--json] [--model FILE]
```

### Validate the model

```bash
java -jar target/dms.jar --validate [--json] [--model FILE]
```

### Run the evaluation

```bash
java -jar target/dms.jar --evaluate [--out DIR] [--model FILE]
```

### Generate model diagrams

```bash
java -jar target/dms.jar --diagrams [--out DIR] [--model FILE]
```

### Start the HTTP server

```bash
java -jar target/dms.jar --serve [--host H] [--port N] [--model FILE]
```

### Show help

```bash
java -jar target/dms.jar --help
```

If you run the application without arguments, it executes the bundled example.

The `--purpose` option selects a particular purpose. Without it, the system evaluates the combined requirements of all purposes associated with the service. The `--context` option specifies which conditions currently hold.

Use `--model` to load an alternative Turtle model instead of the bundled one.

### Exit codes

The exit code makes it possible to use the application in scripts and continuous integration workflows.

| Exit code | Meaning |
|---|---|
| `0` | The evaluation is `COMPLIANT`, the plan is complete, the minimized payload is complete, or the ontology passes validation. |
| `2` | The evaluation is not compliant, the plan or minimization is incomplete, or validation reports errors. |
| `1` | A usage error or runtime error occurred. |

A decision with the `EXCESSIVE` verdict therefore exits with code `2`, even if all required information is present.

Logging is quiet by default, as configured in `simplelogger.properties`. To see Jetty and rdf4j messages, set:

```bash
-Dorg.slf4j.simpleLogger.defaultLogLevel=info
```

For example:

```bash
java -Dorg.slf4j.simpleLogger.defaultLogLevel=info \
     -jar target/dms.jar --serve
```

You can also run the application through Maven without first building the JAR:

```bash
mvn -q exec:java -Dexec.args="--list"
```

To run only the tests:

```bash
mvn test
```

## Machine-readable output

For integrations, scripts, and automated workflows, the application can return results in JSON or RDF rather than only human-readable text.

### JSON output

Pass `--json` in a supported mode to receive a machine-readable representation of the result.

IRIs are represented as objects containing their name, IRI, and DPV mapping where one is available:

```json
{
  "name": "...",
  "iri": "...",
  "dpv": "..."
}
```

The output schemas are explicit and tested by `OutputTest`.

A decision contains:

- `service`
- `purpose`
- `purposes`
- `conditions`
- `requirements`
- `assessments`
- `missing`
- `verdict`
- `offeredCost`
- `minimalDisclosure`
- `minimalCost`
- `compliant`

Each assessment contains:

- `datum`
- `status`
- `covers`
- `exposes`
- `cost`
- `alternatives`
- `because`
- `substitutes`

A disclosure plan contains:

- `service`
- `purpose`
- `conditions`
- `requirements`
- `held`
- `steps`
- `unsatisfiable`
- `unknown`
- `planCost`
- `naiveCost`
- `complete`
- `disclosed`

Data-type collections are sorted by name. This makes the output deterministic: repeated runs with the same inputs produce byte-identical JSON, making comparisons and audit-log diffs easier.

### RDF provenance output

For disclosure decisions, `--rdf` produces a Turtle audit record using the W3C PROV-O vocabulary.

The evaluation is represented as a `prov:Activity` that uses the service, purpose, conditions, and offered data. It generates a decision entity containing an assessment for each datum.

These records can be stored and compared to help explain how a disclosure decision was reached.

This supports accountability and traceability, which are relevant to the GDPR accountability principle in Article 5(2). The records document the system's decision, but they do not independently establish that the underlying processing is legally compliant.

### Examples

Extract the overall verdict from a JSON result:

```bash
java -jar target/dms.jar \
  --service SubscriptionSignUp \
  --data IDCard,Email,IsAdult \
  --json | jq .verdict
```

Generate an RDF audit record:

```bash
java -jar target/dms.jar \
  --service EntryAllowance \
  --data Passport \
  --rdf
```

## HTTP API and demo page

The project includes a small web interface and HTTP API for exploring the model without writing Java code or running individual command-line evaluations.

Start the server:

```bash
java -jar target/dms.jar --serve
```

By default, the [Javalin](https://javalin.io) server listens on `127.0.0.1:7070`.

Open `http://127.0.0.1:7070` to use the demo page.

The interface lets you select a service, purpose, and active conditions, then choose which data to evaluate. You can also explore disclosure planning or paste a JSON payload and mapping into the minimization interface.

Each operation displays a readable report alongside the raw JSON result.

### Available endpoints

| Method and path | Request body | Response |
|---|---|---|
| `GET /api/services` | None | Services, their purposes, conditions, and processing activities. |
| `GET /api/services/{name}` | None | Details of a single service. |
| `GET /api/data` | None | Data types, groups, sensitivity, cost, and DPV mappings. |
| `GET /api/validate` | None | Knowledge-graph validation findings. |
| `POST /api/evaluate` | `{service, purpose?, context?, data[]}` | A disclosure decision and its report. |
| `POST /api/plan` | `{service, purpose?, context?, held[], includeOptional?}` | A disclosure plan and its report. |
| `POST /api/minimize` | `{service, purpose?, context?, mapping{}, payload{}, keepUnmapped?}` | A minimized payload and its report. |

Optional fields are marked with `?`.

### Input validation

The API checks request bodies before running an evaluation.

It returns HTTP `400` with a response such as:

```json
{"error": "..."}
```

when a request contains an unknown field, omits a required field, supplies the wrong type for `data`, `held`, or `context`, uses an empty name, or provides a non-boolean value for a boolean flag.

Unknown services, purposes, and conditions also produce `400` responses rather than being silently ignored.

### Example request

Evaluate a disclosure for a restaurant-finding service:

```bash
curl -s -X POST localhost:7070/api/evaluate \
  -H 'content-type: application/json' \
  -d '{"service":"RestaurantFinder","context":["ContainsAlcohol"],"data":["City"]}'
```

### Security note

The API is unauthenticated and uses plain HTTP.

Binding it to `127.0.0.1` keeps the default listener on the local machine. Setting `--host 0.0.0.0` exposes the server to the network interfaces available to the machine.

**Do not expose this server directly to an untrusted network.** If you need remote access, put it behind a properly configured reverse proxy that provides TLS and authentication.

## Using the system from Java

You can use the decision procedure and disclosure planner directly from another Java application.

The following example loads the bundled knowledge graph, evaluates a disclosure, and creates a disclosure plan:

```java
import dataminimization.*;
import java.util.List;
import java.util.Set;

try (KnowledgeGraph graph = KnowledgeGraph.loadDefault()) {
    // Alternatively, load a custom model:
    // KnowledgeGraph.load(Path.of("my.ttl"))

    DataMinimizationFilter filter = new DataMinimizationFilter(graph);

    Decision d = filter.evaluate(
            Vocabulary.service("CheckoutPayAdult"),
            Vocabulary.purpose("AgeVerification"),
            Set.of(), // Active conditions
            List.of(Vocabulary.data("DateOfBirth")));

    d.verdict();                           // EXCESSIVE
    d.assessments().get(0).status();       // SUPERSET
    d.assessments().get(0).substitutes();  // {IsAdult}
    d.assessments().get(0).alternatives(); // Ranked, cheaper alternatives
    d.minimalDisclosure();                 // {IsAdult}
    d.missing();                           // {}

    DisclosurePlan plan = new DisclosurePlanner(graph)
            .plan(
                    Vocabulary.service("CheckoutPayAdult"),
                    null, // Evaluate all purposes
                    Set.of(),
                    Set.of(
                            Vocabulary.data("DateOfBirth"),
                            Vocabulary.data("Account")),
                    false);

    plan.steps();
    // DERIVE IsAdult, EXTRACT AccountNumber, EXTRACT AccountBalance
}
```

The `Vocabulary` class provides convenient access to the model's service, purpose, and data-type IRIs.

### Handling invalid input

The `evaluate` method throws `IllegalArgumentException` if the service, purpose, or condition is unknown.

Unknown data types are handled differently: they receive the `UNKNOWN` status in the result rather than causing the entire evaluation to fail. This allows the application to report problems with individual fields without hiding the assessment of the remaining data.

## Extending the model

One of the main advantages of this approach is that you can change the domain knowledge without changing the Java implementation.

To add a data type, service, purpose, or condition, edit `src/main/resources/model.ttl`.

For each new data type, provide a sensitivity value and, where an appropriate concept exists, a DPV-PD mapping.

For example, you could add a loyalty-card number and a service that uses it to look up a customer's account:

```turtle
data:LoyaltyCardNumber a data:PersonalData ;
    data:sensitivity 2 ;
    data:isPartOf data:Account .

srv:LoyaltyLookup a srv:Service ;
    srv:hasProcessing srv:LoyaltyLookup_Lookup .

srv:LoyaltyLookup_Lookup a srv:Processing ;
    srv:hasPurpose srv:CustomerSupport ;
    srv:requires data:LoyaltyCardNumber ;
    srv:mayRequires data:Email .
```

After saving the changes, validate the model and try the new service:

```bash
java -jar target/dms.jar --model my.ttl --validate

java -jar target/dms.jar \
  --model my.ttl \
  --service LoyaltyLookup \
  --data Account
```

You do not need to rebuild the JAR to use a different model. The `--model` option tells the application which Turtle file to load.

### Modelling guidelines

When adding relationships, make sure they reflect what is genuinely true about the data.

In particular, use `data:isMoreDetailedThan` only when the less detailed value can actually be derived from the more detailed one.

For example, a date of birth can be used to determine whether someone is an adult, but a first name cannot generally be derived from a last name.

Keep the direction of every relationship consistent with the definitions above. An incorrect relationship can lead the system to accept a disclosure that does not really satisfy a requirement, or to underestimate how much information is revealed.

Run `--validate` after every significant model change. The validator can identify redundant requirements and several other structural mistakes before they affect the decisions.

## Project layout

The repository is organised around the knowledge graph, the decision procedure, and the tools used to test and expose them.

```text
src/main/resources/
    model.ttl                 The knowledge graph (CC BY 4.0)
    shapes.ttl                SHACL validation rules
    scenarios.json            Labelled test and evaluation scenarios
    public/index.html          Browser-based demo page

src/main/java/dataminimization/
    Vocabulary.java           Namespaces and IRIs
    KnowledgeGraph.java       RDF store, SPARQL queries, costs, DPV mappings
    Requirements.java         Requirements for a processing activity
    Processing.java            Processing and purpose information
    DataMinimizationFilter.java
                              The disclosure decision procedure
    SetCover.java             Lowest-cost requirement coverage
    Decision.java             Decision results and text reports
    DataAssessment.java       Assessment of an individual datum
    Status.java               Data assessment statuses
    Verdict.java              Overall decision verdicts
    DisclosurePlanner.java    User-side disclosure planning
    DisclosurePlan.java       Disclosure plan results
    PayloadMinimizer.java     JSON payload minimization
    ValueTransformers.java    Local data transformations
    MinimizationResult.java   Payload minimization results
    OntologyValidator.java    Graph validation
    Violation.java            Validation findings
    JsonOutput.java           JSON output
    ProvenanceWriter.java     PROV-O audit records
    DiagramGenerator.java     Mermaid diagram generation
    eval/                     Evaluation and synthetic models
    http/Server.java          HTTP API and web server
    Main.java                 Command-line interface

src/test/java/dataminimization/
                              Unit tests, scenarios, property-based tests,
                              and API tests

src/test/resources/bad-models/
                              Models that should fail validation

examples/                     Sample JSON payloads and mappings

docs/
    model.md                  Generated knowledge-graph diagrams
    evaluation.md              Evaluation report
    evaluation.csv             Evaluation measurements

.github/workflows/ci.yml      Continuous integration workflow
```

## Development and testing

The project uses Maven to build the application and run its automated checks.

Run the complete build and test suite with:

```bash
mvn package
```

The suite includes:

- Unit tests for the main components.
- Labelled decision and planning scenarios.
- Property-based tests using jqwik.
- Validation of the bundled knowledge graph.
- Checks that generated diagrams are up to date.

The property-based tests check important behaviours of the decision procedure, including:

- Offering exactly the required data produces a compliant decision.
- Adding more offered data does not increase the set of missing requirements.
- A disclosure plan is complete exactly when the held data can cover all requirements.

The CI workflow runs the checks on JDK 17 and JDK 21. It also validates the ontology, checks the generated diagrams, smoke-tests the API, and uploads the built JAR.

### After changing the knowledge graph

Run:

```bash
java -jar target/dms.jar --validate

java -jar target/dms.jar --diagrams
```

The first command checks the model. The second regenerates `docs/model.md`.

To refresh the evaluation report and measurements, run:

```bash
java -jar target/dms.jar --evaluate
```

Evaluation timings depend on the machine running the command, so regenerated timing results may differ from those committed to the repository.

For a summary of changes between versions, see [`CHANGELOG.md`](CHANGELOG.md).

## Citing and licenses

The project uses separate licenses for its code and its knowledge graph.

- **Source code:** [MIT License](LICENSE)
- **Knowledge graph, SHACL shapes, and generated diagrams:** [Creative Commons Attribution 4.0 International (CC BY 4.0)](LICENSE-ONTOLOGY)

Citation metadata is available in [`CITATION.cff`](CITATION.cff). GitHub uses this file to provide a *Cite this repository* option. Additional metadata is stored in `.zenodo.json`.

To obtain a DOI, connect the repository to [Zenodo](https://zenodo.org/account/settings/github/) and publish a GitHub release. Zenodo can archive the release and use the repository's metadata to create a citation record.

## Troubleshooting

### `mvn: command not found`

Maven is not installed or is not available on your `PATH`.

Install it using your preferred package manager. For example, on macOS with Homebrew:

```bash
brew install maven
```

If you use SDKMAN:

```bash
sdk install maven
```

### `release version 17 not supported`

Your Java installation is older than JDK 17.

Check your version:

```bash
java -version
```

### `class file has wrong version 69.0, should be 61.0`

A dependency was compiled for a newer Java than the build targets. Every rdf4j 6.x release is compiled for Java 25, so the project pins rdf4j 5.x to keep working on JDK 17. If you bump rdf4j to 6.x, raise `maven.compiler.release` in `pom.xml` and the JDK matrix in `.github/workflows/ci.yml` together. The Jetty and SLF4J entries at the top of `dependencyManagement` must stay ahead of the rdf4j BOM, which would otherwise pin Jetty 9.4 and SLF4J 1.7.

Install a supported JDK and make sure your environment uses it.

### `Unknown service`, `has no purpose`, or `Unknown condition`

Names are case-sensitive and should be provided as local names without namespace prefixes.

For example, use `SubscriptionSignUp`, not `srv:SubscriptionSignUp`.

Run:

```bash
java -jar target/dms.jar --list
```

to inspect the available model elements.

### `RDFParseException` or `file not found`

The supplied model file may contain invalid Turtle syntax, or the path may be incorrect.

Check the file path and syntax, then validate the model:

```bash
java -jar target/dms.jar --model my.ttl --validate
```

### Warning about reflective mutation of a final field on JDK 26 or newer

You may see a warning similar to:

```text
WARNING: Final field namespace ... mutated reflectively
```

This is a harmless notice from rdf4j. You can suppress it with:

```bash
java --enable-final-field-mutation=ALL-UNNAMED \
     -jar target/dms.jar --list
```

### The first Maven build is slow

This is expected. Maven needs to download rdf4j and the project's other dependencies the first time you build it.

Subsequent builds are generally faster because the dependencies are cached locally.

---

**In short:** This project explores how a knowledge graph can help services request only the personal data they need, identify less revealing alternatives, and give users more control over what they disclose. It combines semantic modelling, automated decision-making, local disclosure planning, and JSON payload minimization in a single Java application.
