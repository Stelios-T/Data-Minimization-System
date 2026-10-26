# Evaluation

Generated on 2026-10-25 by `java -jar target/dms.jar --evaluate` on Mac OS X aarch64, Java 27.0.1.0.3, 8 cores. Regenerate it after changing the model or the scenarios.

## Corpus

38 decision scenarios and 5 disclosure-plan scenarios in `src/main/resources/scenarios.json`. Expected outcomes were labelled by hand from the relation semantics of the model; `ScenarioTest` enforces them, so the corpus doubles as a regression suite.

| Expected verdict | Scenarios |
|---|---|
| COMPLIANT | 12 |
| EXCESSIVE | 17 |
| INCOMPLETE | 4 |
| INCOMPLETE_AND_EXCESSIVE | 5 |

| Labelled datum status | Data points |
|---|---|
| REQUIRED | 20 |
| OPTIONAL | 5 |
| SATISFIES | 5 |
| SUPERSET | 14 |
| EXCESSIVE | 10 |
| UNKNOWN | 1 |

## Decision quality: knowledge graph vs exact-match baseline

The baseline is what a form validator does without a knowledge graph: accept a datum only if the service names it, and report a required datum as missing unless it is offered literally. The "reject" class is the privacy-relevant one.

| Metric | Knowledge graph | Exact match |
|---|---|---|
| Labelled data points | 55 | 55 |
| Reject precision | 100.0% | 83.3% |
| Reject recall | 100.0% | 100.0% |
| Reject F1 | 100.0% | 90.9% |
| Accept/reject accuracy | 100.0% | 90.9% |
| Verdict correct | 38/38 | 22/38 |
| Missing set correct | 38/38 | 21/38 |
| Substitute suggestion correct | 10/10 | not available |

Where the baseline fails: it cannot see that an ID card contains the names (so it reports them missing), that a date of birth implies adulthood, or that an account decomposes into exactly its number and balance. It also cannot name a narrower substitute.

## Latency on the bundled model (33 data types, 13 services)

Steady state after 200 warm-up calls per scenario; 2000 timed calls per decision scenario and 500 per plan scenario. Microseconds per call, single thread.

| Statistic | evaluate | plan |
|---|---|---|
| p50 | 23.1 | 19.0 |
| p95 | 69.3 | 30.6 |
| p99 | 106.8 | 49.9 |
| max | 8619.2 | 572.7 |
| samples | 76000 | 2500 |

## Scale

Synthetic models with the shape of the bundled one (see `SyntheticGraph`). One decision per model: a document and an unrelated part offered to a service that needs two parts and a coarse derived value. The first call pays for the SPARQL property-path queries; later calls hit the per-datum cache.

| Data types | Services | Load (ms) | First call (µs) | Warm p50 (µs) |
|---|---|---|---|---|
| 30 | 3 | 5 | 8263 | 50.4 |
| 100 | 10 | 4 | 6773 | 50.0 |
| 500 | 50 | 16 | 6291 | 45.8 |
| 2000 | 200 | 27 | 8276 | 46.3 |

## Per-scenario results

| Id | Scenario | Expected | Knowledge graph | Exact match | p50 (µs) |
|---|---|---|---|---|---|
| S01 | ID card offered for a sign-up | EXCESSIVE | EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | 79.0 |
| S02 | Exactly the required data | COMPLIANT | COMPLIANT | COMPLIANT | 37.0 |
| S03 | Required plus an optional datum | COMPLIANT | COMPLIANT | COMPLIANT | 39.3 |
| S04 | Full name instead of first and last | EXCESSIVE | EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | 39.3 |
| S05 | Two identity documents, no email | INCOMPLETE_AND_EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | 34.9 |
| S06 | A category instead of a member | INCOMPLETE_AND_EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | 22.1 |
| S07 | Unknown data type | EXCESSIVE | EXCESSIVE | EXCESSIVE | 24.0 |
| S08 | Purpose excludes the support phone | EXCESSIVE | EXCESSIVE | EXCESSIVE | 23.9 |
| S09 | Support purpose alone | COMPLIANT | COMPLIANT | COMPLIANT | 8.5 |
| S10 | Date of birth for an age check | EXCESSIVE | EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | 62.6 |
| S11 | Exact age for an age check | EXCESSIVE | EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | 57.5 |
| S12 | Payment purpose only | COMPLIANT | COMPLIANT | COMPLIANT | 21.0 |
| S13 | Age check missing | INCOMPLETE | INCOMPLETE | INCOMPLETE | 58.3 |
| S14 | Account container for payment | COMPLIANT | COMPLIANT | INCOMPLETE_AND_EXCESSIVE | 17.7 |
| S15 | Billing category for payment | INCOMPLETE_AND_EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | 11.8 |
| S16 | Identity category for entry | INCOMPLETE_AND_EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | 12.5 |
| S17 | Duplicate datum | COMPLIANT | COMPLIANT | COMPLIANT | 13.3 |
| S18 | Passport for entry | EXCESSIVE | EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | 18.6 |
| S19 | Restaurant search, no alcohol | COMPLIANT | COMPLIANT | COMPLIANT | 18.9 |
| S20 | Restaurant search with alcohol, age missing | INCOMPLETE | INCOMPLETE | INCOMPLETE | 28.4 |
| S21 | Restaurant search with alcohol, complete | COMPLIANT | COMPLIANT | COMPLIANT | 22.6 |
| S22 | Adult flag without the condition | EXCESSIVE | EXCESSIVE | EXCESSIVE | 10.9 |
| S23 | Street address and birth date for a restaurant with alcohol | EXCESSIVE | EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | 26.5 |
| S24 | Country for a city search | INCOMPLETE_AND_EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | 6.8 |
| S25 | Street address for a city search | EXCESSIVE | EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | 8.0 |
| S26 | Street address for postal delivery | COMPLIANT | COMPLIANT | INCOMPLETE_AND_EXCESSIVE | 20.4 |
| S27 | International delivery without a phone | INCOMPLETE | INCOMPLETE | INCOMPLETE_AND_EXCESSIVE | 25.8 |
| S28 | Phone offered for a domestic delivery | EXCESSIVE | EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | 19.7 |
| S29 | Whole passport at check-in | EXCESSIVE | EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | 35.8 |
| S30 | Last name missing at check-in | INCOMPLETE | INCOMPLETE | INCOMPLETE | 25.2 |
| S31 | Full name and optional ID number at check-in | EXCESSIVE | EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | 32.3 |
| S32 | Account for an order paid by bank account | EXCESSIVE | EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | 69.4 |
| S33 | Account number for an order not paid by bank account | EXCESSIVE | EXCESSIVE | EXCESSIVE | 25.9 |
| S34 | ID card to sign an invoice | EXCESSIVE | EXCESSIVE | INCOMPLETE_AND_EXCESSIVE | 17.5 |
| S35 | Signature alone | COMPLIANT | COMPLIANT | COMPLIANT | 7.5 |
| S36 | City and country for a map | EXCESSIVE | EXCESSIVE | EXCESSIVE | 7.3 |
| S37 | Subscription payment with receipt email | COMPLIANT | COMPLIANT | INCOMPLETE_AND_EXCESSIVE | 12.8 |
| S38 | City when only the country is wanted | COMPLIANT | COMPLIANT | COMPLIANT | 17.4 |

## Caveats

- The labels were written by the author of the model, so the quality figures measure agreement with the intended semantics, not with an independent expert panel. An external labelling study would strengthen the claim.
- Latency figures are from one machine and one run; treat them as orders of magnitude.
- The baseline is deliberately naive. A richer comparison would include a flat allow-list per service with hand-maintained aliases, which is what the knowledge graph replaces.
