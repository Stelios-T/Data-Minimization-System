# Intelligent Application of the Principle of Data Minimization

[![CI](https://github.com/Stelios-T/Data-Minimization-System/actions/workflows/ci.yml/badge.svg)](https://github.com/Stelios-T/Data-Minimization-System/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/code-MIT-blue.svg)](LICENSE)
[![License: CC BY 4.0](https://img.shields.io/badge/ontology-CC%20BY%204.0-lightgrey.svg)](LICENSE-ONTOLOGY)

A knowledge-graph based system that decides, in real time, whether the personal data a user offers to a service are **adequate, relevant and limited to what is necessary** for the purpose it is processed for, as the GDPR data minimization principle (Art. 5(1)(c)) demands.

Given a service, optionally one of its purposes and the conditions that currently hold, and a set of offered data types, the system answers:

1. Which offered data are needed as they are, which say more than needed, and which are not needed at all?
2. For data that say more than needed, which narrower data should be requested instead, and at what disclosure cost?
3. Which required data are still missing?

It can also work from the user's side: given the data a user agent holds, it plans what to extract, derive or generalise locally so that only the minimum leaves the device, and it can apply that plan to a real JSON payload.

```
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

## Contents

- [Quick start](#quick-start)
- [Background](#background)
- [The knowledge graph](#the-knowledge-graph)
- [The decision](#the-decision)
- [Disclosure planning: the user's side](#disclosure-planning-the-users-side)
- [Minimizing a real payload](#minimizing-a-real-payload)
- [Validating the ontology](#validating-the-ontology)
- [Evaluation](#evaluation)
- [Command-line usage](#command-line-usage)
- [Machine-readable output](#machine-readable-output)
- [HTTP API and demo page](#http-api-and-demo-page)
- [Using it from Java](#using-it-from-java)
- [Extending the model](#extending-the-model)
- [Project layout](#project-layout)
- [Development](#development)
- [Citing and licenses](#citing-and-licenses)
- [Troubleshooting](#troubleshooting)

## Quick start

Requirements: JDK 17 or newer and Maven 3.6 or newer.

```bash
git clone <this repository>
cd Data-Minimization-System
mvn package                    # compiles, runs the tests, builds target/dms.jar
java -jar target/dms.jar       # runs the example above
java -jar target/dms.jar --list
java -jar target/dms.jar --service RestaurantFinder --data City --context ContainsAlcohol
java -jar target/dms.jar --service CheckoutPayAdult --plan --held DateOfBirth,Account
java -jar target/dms.jar --serve         # demo page and API at http://127.0.0.1:7070
```

## Background

Privacy is one of the most heavily legislated issues of modern societies, driven by the volume of personal data that technology makes it possible to collect and process. The European Union's General Data Protection Regulation sets out a small number of principles that every processing operation must respect. One of them, data minimization, states that personal data must be adequate, relevant and limited to what is necessary **in relation to the purposes** for which they are processed.

The principle is easy to state and hard to apply: a service that needs to know whether a customer is an adult can be handed a full identity card, which also reveals a name, an exact date of birth, a parent's names and an ID number. This repository shows how semantic web technologies can make that judgement automatically. A knowledge graph describes the types of personal data, how they relate to each other, and which data each service needs for each of its purposes. A decision component queries the graph to classify every offered datum, to name cheaper substitutes, and to report what is missing.

## The knowledge graph

The graph is written in Turtle in [`src/main/resources/model.ttl`](src/main/resources/model.ttl) and is the single source of truth: the Java code loads it at start-up and contains no knowledge of its own. It has two vocabularies, `data:` for personal data types and `srv:` for services, purposes, processing and conditions.

### Data relations

| Relation | Meaning | Consequence for minimization |
|---|---|---|
| `X data:isA Y` | X is a specialisation of Y. *Email is a Contact.* | Providing X **satisfies** a need for Y. Providing Y does **not** provide X. |
| `X data:isPartOf Y` | X is contained in Y. *IDNumber is part of IDCard.* | Providing Y **also discloses** X. |
| `X data:isMoreDetailedThan Y` | Y can be derived from X. *IsAdult from DateOfBirth.* | Providing X **also discloses** Y, and says more than Y. |
| `X data:sensitivity n` | Disclosure weight 1 (low) to 5 (Art. 9 special category). | The cost of a datum is its weight plus the weight of everything it discloses. |

`isPartOf` and `isMoreDetailedThan` are declared `owl:TransitiveProperty`. The code never walks the graph by hand. Everything that handing over a datum discloses or satisfies is one SPARQL 1.1 property-path query against an in-memory rdf4j store:

```sparql
SELECT DISTINCT ?x WHERE { ?p (^data:isPartOf|data:isMoreDetailedThan)*/data:isA* ?x }
```

Containment and derivation are followed first and generalisation last. The other order would be unsound: having "a contact" does not mean having an email address.

### Services, purposes, processing and conditions

Article 5(1)(c) is relative to a purpose, so requirements are not attached to a service directly but to the processing it performs for one purpose:

```turtle
srv:SubscriptionSignUp a srv:Service ;
    srv:hasProcessing srv:SubscriptionSignUp_AccountCreation , srv:SubscriptionSignUp_CustomerSupport .
srv:SubscriptionSignUp_AccountCreation a srv:Processing ; srv:hasPurpose srv:AccountCreation ;
    srv:requires    data:FirstName , data:LastName , data:Email .
srv:SubscriptionSignUp_CustomerSupport a srv:Processing ; srv:hasPurpose srv:CustomerSupport ;
    srv:mayRequires data:TelephoneNumber .
```

| Relation | Meaning |
|---|---|
| `S srv:hasProcessing P` | Service S performs processing P. |
| `P srv:hasPurpose U` | P serves exactly one purpose U. |
| `P srv:requires D` | P cannot work without D. |
| `P srv:mayRequires D` | P can optionally use D. |
| `P srv:requiresWhen [ srv:condition C ; srv:requires D ]` | P requires D only while condition C holds, e.g. `IsAdult` only when the order `ContainsAlcohol`. Several such statements for the same datum mean any one condition suffices. |

A decision can be taken for one purpose (`--purpose`) or, by default, for the union of all purposes of the service. Conditions that hold are passed with `--context`. A simple model may still attach `srv:requires` directly to a service.

### Alignment with the W3C Data Privacy Vocabulary

The schema is mapped to [DPV](https://w3id.org/dpv): `data:PersonalData`, `srv:Service`, `srv:Purpose`, `srv:Processing` and `srv:Condition` are subclasses of `dpv:PersonalData`, `dpv:Service`, `dpv:Purpose`, `dpv:Process` and `dpv:Context`; `srv:requires` and `srv:mayRequires` are sub-properties of `dpv:hasPersonalData` with necessity `dpv:Required` and `dpv:Optional`. Every data type that has a counterpart in DPV-PD carries a `skos:exactMatch`, `skos:closeMatch` or `skos:broadMatch` to it, so the model can be exchanged with DPV-based tools without importing the full vocabulary.

### Model contents

33 personal data types in five groups (identity, contact, location, age, billing), 13 services, 11 purposes and 3 conditions. Diagrams of the whole graph are generated from the model into [`docs/model.md`](docs/model.md) (Mermaid, rendered by GitHub) with `--diagrams`; a test and the CI fail when they are stale.

The figures drawn for the original thesis predate purposes, conditions and sensitivity weights, and three corrections (`AirportCheckIn` requires `LastName`, not `MiddleName`; the unsound edges `LastName isMoreDetailedThan FirstName` and `AccountBalance isMoreDetailedThan AccountNumber` were removed). One is kept here for reference:

![Original combined model (2022)](https://user-images.githubusercontent.com/67365815/203770370-d809e080-2e75-418d-b08d-b2a37fde2509.jpg)

## The decision

For a service, a purpose, a set of conditions and a set of offered data the filter returns a `Decision` holding one assessment per distinct datum.

| Status | Meaning | Action |
|---|---|---|
| `REQUIRED` | Named by the service as required (possibly because of an active condition). | Accept. |
| `OPTIONAL` | Named by the service as optional. | Accept. |
| `SATISFIES` | Not named, but says nothing beyond what was asked for: a specialisation of a requested category (an Email for a Contact), or a composite of exactly the requested parts (an Account for its number and balance). | Accept. |
| `SUPERSET` | Discloses needed data **and** more: extra parts, or a value from which the needed one is merely derived (an ExactAge for an IsAdult). | Request the listed narrower data instead; the cheapest alternatives are ranked. |
| `EXCESSIVE` | Discloses nothing the service needs. | Reject. |
| `UNKNOWN` | Not a personal data type in the graph. | Reject. |

The decision also lists the required data that nothing offered covers (with the condition that made them required), the total disclosure cost of what was offered, the cheapest set of data types that would cover every requirement (a weighted set cover over the graph), and an overall verdict:

| Verdict | All required data covered | Nothing excessive offered |
|---|---|---|
| `COMPLIANT` | yes | yes |
| `EXCESSIVE` | yes | no |
| `INCOMPLETE` | no | yes |
| `INCOMPLETE_AND_EXCESSIVE` | no | no |

## Disclosure planning: the user's side

Minimization works best before the data leave the user. The planner takes the data a user agent holds and computes, for every requirement, which held datum to use and how:

| Step | Meaning |
|---|---|
| `DISCLOSE` | The held datum is exactly what is required; send it. |
| `EXTRACT` | Take a part out of a held datum (the last name out of an ID card) and send only that. |
| `DERIVE` | Compute the required datum locally (an adult flag from a date of birth, a city from a street address) and send the result. |
| `PRESENT_AS` | The held datum is a specialisation of what is required; send it as is. |

Held entries that are not personal data types in the graph are listed as ignored rather than silently dropped.

```
$ java -jar target/dms.jar --service CheckoutPayAdult --plan --held DateOfBirth,Account

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

This is the negotiation model the thesis proposes: the service publishes its requirements (purpose, required and optional data, conditions); the user agent computes the plan locally; only the plan's outputs leave the device. It fits the selective-disclosure model of the EU Digital Identity Wallet and SD-JWT credentials, where a holder reveals individual claims, or predicates over them, instead of whole documents.

## Minimizing a real payload

`--minimize` applies a decision to a JSON object. A mapping file names the data type of each field; the minimizer keeps needed fields, drops excessive ones, and replaces superset fields by values it can compute locally: a date of birth becomes `isAdult`, a full name splits into first and last name, members of an object (an ID card with a `fullName`, an account with an `accountNumber`) are extracted by name along the containment relations of the graph. Values it cannot derive are reported as `REQUEST` rather than invented. Ages are computed against today's date in UTC; pass a `java.time.Clock` to `PayloadMinimizer` to change that, and build custom transformers with `ValueTransformers.Builder`.

```bash
java -jar target/dms.jar --service SubscriptionSignUp \
     --minimize examples/signup-payload.json --mapping examples/signup-mapping.json
```

prints the reduced payload on stdout and the per-field actions on stderr. Fields with no mapping are dropped unless `--keep-unmapped` is given.

## Validating the ontology

`--validate` checks a model with SHACL shapes (`src/main/resources/shapes.ttl`: every data type has one sensitivity in 1..5, relations point to declared types, every processing has exactly one purpose) and with checks shapes cannot express: cycles in containment or derivation, requirements made redundant because another required datum already discloses them, optional data already disclosed by required data, dangling references, unused processing nodes and services without needs. Errors give exit code 2; warnings are modelling smells. The bundled model is clean. Writing the validator found two real smells in an earlier version of this very model (a service that required `Country` next to `City`, which already implies it).

## Evaluation

`--evaluate` runs the 38 labelled decision scenarios and 5 planner scenarios in `src/main/resources/scenarios.json` through the system and through an exact-match baseline (accept only what the service names; report required data as missing unless offered literally), then measures latency on the bundled model and on synthetic models of 30 to 2,000 data types. Results are written to [`docs/evaluation.md`](docs/evaluation.md) and [`docs/evaluation.csv`](docs/evaluation.csv). The same corpus is enforced by `ScenarioTest`, so it doubles as a regression suite.

## Command-line usage

```
java -jar target/dms.jar --service NAME --data A,B,C [--purpose NAME] [--context C1,C2] [--json | --rdf] [--model FILE]
java -jar target/dms.jar --service NAME --plan --held A,B,C [--include-optional] [--purpose NAME] [--context C1,C2] [--json]
java -jar target/dms.jar --service NAME --minimize payload.json --mapping mapping.json [--keep-unmapped] [--purpose NAME] [--context C1,C2] [--json]
java -jar target/dms.jar --list [--json] [--model FILE]
java -jar target/dms.jar --validate [--json] [--model FILE]
java -jar target/dms.jar --evaluate [--out DIR] [--model FILE]
java -jar target/dms.jar --diagrams [--out DIR] [--model FILE]
java -jar target/dms.jar --serve [--host H] [--port N] [--model FILE]
java -jar target/dms.jar --help
```

With no arguments the shipped example is run. Exactly one mode per invocation: combining two modes, or a flag that does not apply to the mode (`--rdf` with `--plan`, `--json` with `--diagrams`), is a usage error. The exit code is `0` for `COMPLIANT` (or a complete plan, a complete minimized payload, a clean ontology), `2` for any other outcome and `1` for a usage or runtime error, so the tool can be used from scripts and CI. Logging is quiet by default (`simplelogger.properties`); set `-Dorg.slf4j.simpleLogger.defaultLogLevel=info` to see Jetty and rdf4j messages. The same commands work without building the jar through `mvn -q exec:java -Dexec.args="..."`. Run the tests alone with `mvn test`.

## Machine-readable output

`--json` prints the decision, plan, minimization result, validation findings or service list as JSON. IRIs appear as `{"name", "iri", "dpv"}` objects, the last being the DPV-PD concept where one is mapped, so downstream tools can work with DPV terms directly. The field set is explicit and tested (`OutputTest`): a decision has `service, purpose, purposes, conditions, requirements, assessments, missing, verdict, offeredCost, minimalDisclosure, minimalCost, compliant`; each assessment has `datum, status, covers, exposes, cost, alternatives, because, substitutes`; a plan has `service, purpose, conditions, requirements, held, steps, unsatisfiable, unknown, planCost, naiveCost, complete, disclosed`. Every collection of data types is sorted by name, so two runs produce byte-identical output and audit records can be diffed.

`--rdf` prints a decision as a PROV-O audit record in Turtle: the check is a `prov:Activity` that used the service, purpose, conditions and offered data and generated a decision entity with one assessment per datum. Kept together, such records let a controller show how each disclosure decision was taken, which is what the accountability principle (Art. 5(2)) asks for.

```bash
java -jar target/dms.jar --service SubscriptionSignUp --data IDCard,Email,IsAdult --json | jq .verdict
java -jar target/dms.jar --service EntryAllowance --data Passport --rdf
```

## HTTP API and demo page

`--serve` starts a small [Javalin](https://javalin.io) server bound to `127.0.0.1:7070` with a demo page at `/` and a JSON API. The API is unauthenticated and speaks plain HTTP; `--host 0.0.0.0` exposes it to the network and should only be used behind a reverse proxy that adds TLS and authentication.

| Method and path | Body | Returns |
|---|---|---|
| `GET /api/services` | | services with purposes, conditions and processing |
| `GET /api/services/{name}` | | one service |
| `GET /api/data` | | data types with group, sensitivity, cost and DPV match |
| `GET /api/validate` | | validation findings |
| `POST /api/evaluate` | `{service, purpose?, context?, data[]}` | `{decision, report}` |
| `POST /api/plan` | `{service, purpose?, context?, held[], includeOptional?}` | `{plan, report}` |
| `POST /api/minimize` | `{service, purpose?, context?, mapping{}, payload{}, keepUnmapped?}` | `{result, report}` |

Bodies are checked strictly: an unknown field (for example a misspelled `data`), a missing required field, a non-array `data`/`held`/`context`, an empty name or a non-boolean flag gives `400` with `{"error": "..."}` rather than an answer computed from an empty list. Unknown services, purposes and conditions also give `400`.

```bash
curl -s -X POST localhost:7070/api/evaluate -H 'content-type: application/json' \
     -d '{"service":"RestaurantFinder","context":["ContainsAlcohol"],"data":["City"]}'
```

The demo page lets you pick a service, a purpose and the conditions that hold, tick the data a user offers (or holds, on the Plan tab), or paste a payload and mapping (Minimize tab), and shows the text report next to the raw JSON.

## Using it from Java

```java
import dataminimization.*;
import java.util.List;
import java.util.Set;

try (KnowledgeGraph graph = KnowledgeGraph.loadDefault()) {           // or KnowledgeGraph.load(Path.of("my.ttl"))
    DataMinimizationFilter filter = new DataMinimizationFilter(graph);

    Decision d = filter.evaluate(
            Vocabulary.service("CheckoutPayAdult"),
            Vocabulary.purpose("AgeVerification"),            // or null for all purposes
            Set.of(),                                         // active conditions
            List.of(Vocabulary.data("DateOfBirth")));

    d.verdict();                              // EXCESSIVE
    d.assessments().get(0).status();          // SUPERSET
    d.assessments().get(0).substitutes();     // {IsAdult}
    d.assessments().get(0).alternatives();    // ranked cheaper covers
    d.minimalDisclosure();                    // {IsAdult}
    d.missing();                              // {}

    DisclosurePlan plan = new DisclosurePlanner(graph)
            .plan(Vocabulary.service("CheckoutPayAdult"), null, Set.of(),
                  Set.of(Vocabulary.data("DateOfBirth"), Vocabulary.data("Account")), false);
    plan.steps();                             // DERIVE IsAdult, EXTRACT AccountNumber, EXTRACT AccountBalance
}
```

`evaluate` throws `IllegalArgumentException` for an unknown service, purpose or condition. Unknown data types are reported with status `UNKNOWN` rather than thrown, so a single bad field does not hide the assessment of the others.

## Extending the model

Add data types, services, purposes or conditions to `model.ttl`; no Java changes are needed. Give every data type a sensitivity and, where one exists, a DPV-PD match.

```turtle
data:LoyaltyCardNumber a data:PersonalData ; data:sensitivity 2 ; data:isPartOf data:Account .

srv:LoyaltyLookup a srv:Service ; srv:hasProcessing srv:LoyaltyLookup_Lookup .
srv:LoyaltyLookup_Lookup a srv:Processing ; srv:hasPurpose srv:CustomerSupport ;
    srv:requires    data:LoyaltyCardNumber ;
    srv:mayRequires data:Email .
```

Then validate and run without rebuilding:

```bash
java -jar target/dms.jar --model my.ttl --validate
java -jar target/dms.jar --model my.ttl --service LoyaltyLookup --data Account
```

Keep the directions from the tables above. Use `isMoreDetailedThan` only when the object can genuinely be computed from the subject; the validator will tell you when a requirement is already implied by another.

## Project layout

```
src/main/resources/model.ttl                 the knowledge graph (CC BY 4.0)
src/main/resources/shapes.ttl                SHACL shapes the graph must satisfy
src/main/resources/scenarios.json            labelled scenarios for tests and evaluation
src/main/resources/public/index.html         the demo page
src/main/java/dataminimization/
    Vocabulary.java                          IRIs and namespaces
    KnowledgeGraph.java                      in-memory RDF store, SPARQL property paths, costs, DPV links
    Requirements.java, Processing.java       what a service needs for a purpose under conditions
    DataMinimizationFilter.java              the decision procedure
    SetCover.java                            cheapest covering sets
    Decision.java, DataAssessment.java       result types with a text report
    Status.java, Verdict.java                the classifications above
    DisclosurePlanner.java, DisclosurePlan.java   the user-side planner
    PayloadMinimizer.java, ValueTransformers.java, MinimizationResult.java   JSON payload minimization
    OntologyValidator.java, Violation.java   SHACL and structural validation
    JsonOutput.java, ProvenanceWriter.java   JSON and PROV-O output
    DiagramGenerator.java                    Mermaid diagrams from the model
    eval/                                    scenario corpus, exact-match baseline, synthetic graphs, evaluation writer
    http/Server.java                         the HTTP API
    Main.java                                command-line interface
src/test/java/dataminimization/              unit tests, the scenario suite, jqwik property tests, API tests
src/test/resources/bad-models/               ontologies that must fail validation
examples/                                    payloads and mappings for --minimize
docs/model.md, docs/evaluation.*             generated diagrams and evaluation results
.github/workflows/ci.yml                     CI: build, tests, validation, diagram freshness, API smoke test
```

## Development

`mvn package` runs the whole suite: unit tests, the labelled scenario suite, property-based tests (jqwik) of the laws the decision procedure must satisfy (exactly the required set is compliant; adding data never grows the missing set; a plan is complete exactly when the held data cover the requirements), the ontology validation of the bundled model and the diagram freshness check. CI runs the same on JDK 17 and 21, then validates the ontology, checks the diagrams, smoke-tests the API and uploads the jar.

After changing `model.ttl`:

```bash
java -jar target/dms.jar --validate
java -jar target/dms.jar --diagrams        # refresh docs/model.md
java -jar target/dms.jar --evaluate        # refresh docs/evaluation.md (optional, machine-specific timings)
```

See [`CHANGELOG.md`](CHANGELOG.md) for what changed between versions.

## Citing and licenses

The code is under the [MIT License](LICENSE); the knowledge graph, shapes and generated diagrams are under [CC BY 4.0](LICENSE-ONTOLOGY). Citation metadata is in [`CITATION.cff`](CITATION.cff) (GitHub shows a "Cite this repository" button from it) and `.zenodo.json`. To mint a DOI, enable the repository in [Zenodo](https://zenodo.org/account/settings/github/) and publish a GitHub release; Zenodo archives the release and reads `.zenodo.json` for the metadata.

## Troubleshooting

- **`mvn: command not found`**: install Maven (`brew install maven` on macOS, `sdk install maven` with SDKMAN, or your package manager).
- **`release version 17 not supported`**: the JDK is older than 17. Check with `java -version`.
- **`Unknown service` / `has no purpose` / `Unknown condition`**: names are case-sensitive local names without a prefix. Use `--list` to see the valid ones.
- **`error: RDFParseException: ...`** or **`error: file not found: ...`**: the `--model` file is not valid Turtle or does not exist. Only usage errors print the usage text.
- **`WARNING: Final field namespace ... mutated reflectively`** on JDK 26 or newer: a harmless notice from rdf4j. Silence it with `java --enable-final-field-mutation=ALL-UNNAMED -jar target/dms.jar ...`.
- **The first `mvn` run is slow**: Maven downloads rdf4j and its dependencies once; later runs are fast.
