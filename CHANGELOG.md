# Changelog

## Unreleased

### Concept
- Requirements are attached to the processing a service performs for a **purpose**; a decision can be taken for one purpose or for all (`--purpose`).
- **Conditional requirements** (`srv:requiresWhen`) activate with `--context`; the report says which condition made a datum required.
- **Sensitivity weights** on every data type; each decision reports the disclosure cost of what was offered, the cheapest covering set (weighted set cover) and ranked alternatives for superset data.
- Status rule refined: a datum from which a needed value is merely derived (ExactAge for IsAdult) is a SUPERSET; a specialisation of a requested category or a composite of exactly the requested parts SATISFIES.
- **Alignment with the W3C Data Privacy Vocabulary**: schema mapped to DPV classes and properties, data types linked to DPV-PD with SKOS matches.
- **Ontology validation** (`--validate`): SHACL shapes plus checks for cycles, redundant or already-covered requirements, dangling references, unused processing and services without needs.
- **Disclosure planner** (`--plan`): from the data a user agent holds, decide what to disclose, extract, derive or present, with the cost saved.
- **Payload minimizer** (`--minimize`): apply a decision to a JSON object, computing narrower values locally (adult flag from a birth date, names from a full name, members of objects).
- **Evaluation harness** (`--evaluate`): 38 labelled decision scenarios and 5 plan scenarios, comparison with an exact-match baseline, latency and scale benchmarks written to `docs/evaluation.md`.

### Build fix
- rdf4j pinned to 5.2.0: every rdf4j 6.x release is compiled for Java 25 and broke the JDK 17 and 21 CI jobs. Jetty 12.1 and SLF4J 2.0 are managed explicitly ahead of the rdf4j BOM, which would otherwise pin Jetty 9.4 and SLF4J 1.7. Verified with a full CI-equivalent run on JDK 17.

### Review fixes
- Every collection of data types in results is sorted by name, so `--json`, `--rdf` and API output are identical across runs.
- Explicit JSON contract (`compliant`, `complete`, `substitutes`, `disclosed` are deliberate fields; derived accessors are excluded) with a test asserting the field sets.
- The HTTP API rejects unknown or malformed body fields with `400` instead of answering from an empty list; `/api/validate` is computed once.
- The CLI reports parse errors, missing files and bind failures as one-line errors; the usage text is printed only for usage errors.
- A datum required under several conditions keeps all of them (any one suffices).
- `--serve` binds `127.0.0.1` by default (`--host` to change), prints the real host, and shuts down cleanly.
- Disclosure plans list held entries that are not known data types instead of dropping them.
- Text formatting moved out of the result records into `TextReports` and `Names`; the records keep a one-line `report()`.
- `Main` parses into an `Options` record with one `Mode`; conflicting modes or flags are usage errors; `--evaluate` honours `--model`; `--help` exits 0. Covered by `MainTest`.
- `KnowledgeGraph` caches processing nodes, edges, sensitivities and DPV matches, closes every rdf4j iteration, and fails fast on a non-integer sensitivity or a processing with several purposes.
- `ValueTransformers` is immutable (use `ValueTransformers.Builder`); `PayloadMinimizer` takes a `Clock` and defaults to UTC.
- `SetCover` prunes by the k-th best cost; planner paths return `Optional`; the diagram group for uncategorised data cannot collide with a real type.
- Build: Jackson managed by its BOM, `slf4j-simple` pinned, Javalin optional for library consumers, `simplelogger.properties` keeps the jar quiet, shade excludes duplicate metadata.
- Tests: JUnit assertions instead of `assert`, fixtures loaded from the classpath, the vacuous plan-cost law replaced by a real one, counts derived from the graph, jqwik database ignored by git.

### Repository
- `--json` output for every command and `--rdf` PROV-O audit records for decisions.
- Mermaid diagrams generated from the model (`--diagrams`, `docs/model.md`) with a freshness test.
- GitHub Actions CI on JDK 17 and 21: build, tests, validation, diagram freshness, API smoke test, jar artifact.
- Property-based tests (jqwik) for the laws the decision procedure must satisfy.
- HTTP API and demo page (`--serve`, Javalin).
- MIT license for the code, CC BY 4.0 for the ontology; `CITATION.cff`, `.zenodo.json`.

## 0.2.0 (2026-10-25) rewrite
- Package `dataminimization`; ontology moved to `model.ttl` and loaded at start-up; rdf4j 6 in-memory store with SPARQL property paths instead of a hand-written graph walk.
- Structured `Decision` with per-datum status, substitutes, missing data and a verdict; CLI with exit codes; executable jar.
- Fixed the inverted `isA` direction, duplicate reports, the missing-data report, unknown-name handling and an index slip (`AirportCheckIn` requires `LastName`).

## 0.0.1 (2022) original thesis demo
