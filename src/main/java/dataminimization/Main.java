package dataminimization;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dataminimization.eval.Evaluation;
import dataminimization.http.Server;
import io.javalin.Javalin;
import org.eclipse.rdf4j.model.IRI;

/**
 * Command-line entry point. Run with {@code --help} for the options. Exit code 0 means
 * COMPLIANT (or a complete plan, a complete minimized payload, a clean ontology, a successful
 * listing or export), 2 means any other outcome, 1 means a usage or runtime error.
 */
public final class Main {

    /** A problem with the command line itself; the usage text is printed for these only. */
    static final class UsageException extends IllegalArgumentException {
        UsageException(String message) {
            super(message);
        }
    }

    /** What the invocation asks for. Exactly one mode per invocation. */
    enum Mode { DECIDE, PLAN, MINIMIZE, LIST, VALIDATE, EVALUATE, DIAGRAMS, SERVE }

    /** Parsed command line. */
    record Options(Mode mode, String service, String purpose, List<String> data, List<String> context, List<String> held,
                   boolean includeOptional, Path payload, Path mapping, boolean keepUnmapped, Path model, Path out,
                   boolean json, boolean rdf, String host, int port) {
    }

    private Main() {
    }

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (UsageException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println();
            System.err.println(usage());
            System.exit(1);
        } catch (NoSuchFileException e) {
            System.err.println("error: file not found: " + e.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException | IllegalStateException | IOException | InterruptedException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            // rdf4j parse errors, Jetty bind failures and the like
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            System.err.println("error: " + e.getClass().getSimpleName() + ": " + message);
            System.exit(1);
        }
    }

    static int run(String[] args) throws IOException, InterruptedException {
        Options o;
        try {
            o = parse(args);
        } catch (HelpRequested h) {
            System.out.println(usage());
            return 0;
        }
        return switch (o.mode()) {
            case EVALUATE -> evaluate(o);
            case SERVE -> serve(o);
            case DIAGRAMS -> withGraph(o, Main::diagrams);
            case VALIDATE -> withGraph(o, Main::validate);
            case LIST -> withGraph(o, Main::list);
            case PLAN -> withGraph(o, Main::plan);
            case MINIMIZE -> withGraph(o, Main::minimize);
            case DECIDE -> withGraph(o, Main::decide);
        };
    }

    // -- argument parsing -----------------------------------------------------------

    static Options parse(String[] args) {
        EnumSet<Mode> modes = EnumSet.noneOf(Mode.class);
        String service = null, purpose = null, host = "127.0.0.1";
        List<String> data = new ArrayList<>(), context = new ArrayList<>(), held = new ArrayList<>();
        boolean includeOptional = false, keepUnmapped = false, json = false, rdf = false;
        Path payload = null, mapping = null, model = null, out = Path.of("docs");
        int port = 7070;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--service" -> service = value(args, ++i);
                case "--purpose" -> purpose = value(args, ++i);
                case "--data" -> data.addAll(split(value(args, ++i)));
                case "--context" -> context.addAll(split(value(args, ++i)));
                case "--held" -> held.addAll(split(value(args, ++i)));
                case "--include-optional" -> includeOptional = true;
                case "--minimize" -> { modes.add(Mode.MINIMIZE); payload = Path.of(value(args, ++i)); }
                case "--mapping" -> mapping = Path.of(value(args, ++i));
                case "--keep-unmapped" -> keepUnmapped = true;
                case "--model" -> model = Path.of(value(args, ++i));
                case "--out" -> out = Path.of(value(args, ++i));
                case "--json" -> json = true;
                case "--rdf" -> rdf = true;
                case "--host" -> host = value(args, ++i);
                case "--port" -> port = parsePort(value(args, ++i));
                case "--plan" -> modes.add(Mode.PLAN);
                case "--list" -> modes.add(Mode.LIST);
                case "--validate" -> modes.add(Mode.VALIDATE);
                case "--evaluate" -> modes.add(Mode.EVALUATE);
                case "--diagrams" -> modes.add(Mode.DIAGRAMS);
                case "--serve" -> modes.add(Mode.SERVE);
                case "-h", "--help" -> throw new HelpRequested();
                default -> throw new UsageException("unexpected argument: " + args[i]);
            }
        }

        if (args.length == 0) {
            System.out.println("No arguments given; running the shipped example. Use --help for options.\n");
            service = "SubscriptionSignUp";
            data = List.of("IDCard", "Email", "IsAdult");
        }
        if (modes.size() > 1) {
            throw new UsageException("choose one of " + modes.stream().map(m -> "--" + m.name().toLowerCase()).toList());
        }
        Mode mode = modes.isEmpty() ? Mode.DECIDE : modes.iterator().next();
        if (json && rdf) {
            throw new UsageException("--json and --rdf are mutually exclusive");
        }
        if (rdf && mode != Mode.DECIDE) {
            throw new UsageException("--rdf applies only to a decision (--service with --data)");
        }
        if (json && (mode == Mode.EVALUATE || mode == Mode.DIAGRAMS || mode == Mode.SERVE)) {
            throw new UsageException("--json does not apply to --" + mode.name().toLowerCase());
        }
        if (mode == Mode.DECIDE || mode == Mode.PLAN || mode == Mode.MINIMIZE) {
            if (service == null) {
                throw new UsageException("--service is required");
            }
        } else if (service != null || !data.isEmpty() || !held.isEmpty()) {
            throw new UsageException("--service, --data and --held do not apply to --" + mode.name().toLowerCase());
        }
        if (mode == Mode.DECIDE && data.isEmpty()) {
            throw new UsageException("--data is required");
        }
        if (mode == Mode.PLAN && held.isEmpty()) {
            throw new UsageException("--plan needs --held A,B,C");
        }
        if (mode == Mode.MINIMIZE && mapping == null) {
            throw new UsageException("--minimize needs --mapping FILE");
        }
        if (mode != Mode.MINIMIZE && mapping != null) {
            throw new UsageException("--mapping applies only to --minimize");
        }
        return new Options(mode, service, purpose, List.copyOf(data), List.copyOf(context), List.copyOf(held), includeOptional,
                payload, mapping, keepUnmapped, model, out, json, rdf, host, port);
    }

    /** Thrown by {@link #parse} for {@code --help}; handled in {@link #run} by printing usage with exit 0. */
    static final class HelpRequested extends RuntimeException {
    }

    // -- modes ---------------------------------------------------------------------

    @FunctionalInterface
    private interface GraphCommand {
        int run(KnowledgeGraph graph, Options o) throws IOException;
    }

    private static int withGraph(Options o, GraphCommand command) throws IOException {
        try (KnowledgeGraph graph = loadGraph(o)) {
            return command.run(graph, o);
        }
    }

    private static KnowledgeGraph loadGraph(Options o) throws IOException {
        return o.model() == null ? KnowledgeGraph.loadDefault() : KnowledgeGraph.load(o.model());
    }

    private static int evaluate(Options o) throws IOException {
        Evaluation.run(o.out(), o.model());
        System.out.println("Wrote " + o.out().resolve("evaluation.md") + " and " + o.out().resolve("evaluation.csv"));
        return 0;
    }

    private static int serve(Options o) throws IOException, InterruptedException {
        try (KnowledgeGraph graph = loadGraph(o)) {
            Javalin app = new Server(graph).start(o.host(), o.port());
            CountDownLatch stop = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(stop::countDown));
            System.out.println("Listening on http://" + o.host() + ":" + app.port() + "  (Ctrl-C to stop)");
            if (!"127.0.0.1".equals(o.host()) && !"localhost".equals(o.host())) {
                System.out.println("Warning: the API is unauthenticated and uses plain HTTP; " + o.host() + " may be reachable from other machines.");
            }
            stop.await();
            app.stop();
            return 0;
        }
    }

    private static int diagrams(KnowledgeGraph graph, Options o) throws IOException {
        Files.createDirectories(o.out());
        Files.writeString(o.out().resolve("model.md"), DiagramGenerator.markdown(graph));
        System.out.println("Wrote " + o.out().resolve("model.md"));
        return 0;
    }

    private static int validate(KnowledgeGraph graph, Options o) throws IOException {
        List<Violation> violations = OntologyValidator.validate(graph);
        if (o.json()) {
            System.out.println(JsonOutput.mapper(graph).writeValueAsString(violations));
        } else {
            violations.forEach(System.out::println);
            System.out.println(violations.isEmpty() ? "Ontology is clean."
                    : violations.size() + " finding(s), " + (OntologyValidator.isClean(violations) ? "no errors." : "with errors."));
        }
        return OntologyValidator.isClean(violations) ? 0 : 2;
    }

    private static int list(KnowledgeGraph graph, Options o) throws IOException {
        if (o.json()) {
            Map<String, Object> services = new LinkedHashMap<>();
            for (IRI s : graph.services()) {
                services.put(s.getLocalName(), graph.processings(s));
            }
            System.out.println(JsonOutput.mapper(graph).writeValueAsString(services));
            return 0;
        }
        for (IRI s : graph.services()) {
            System.out.println(s.getLocalName());
            for (Processing p : graph.processings(s)) {
                String purpose = p.purpose() == null ? "(no purpose)" : p.purpose().getLocalName();
                if (!p.required().isEmpty()) {
                    System.out.printf("  %-22s requires %s%n", purpose, Names.of(p.required()));
                }
                if (!p.optional().isEmpty()) {
                    System.out.printf("  %-22s may use  %s%n", purpose, Names.of(p.optional()));
                }
                p.conditional().forEach((datum, conditions) -> System.out.printf("  %-22s requires %s when %s%n",
                        purpose, datum.getLocalName(), Names.conditions(conditions)));
            }
        }
        return 0;
    }

    private static int plan(KnowledgeGraph graph, Options o) throws IOException {
        Set<IRI> heldIris = new LinkedHashSet<>(o.held().stream().map(Vocabulary::data).toList());
        DisclosurePlan result = new DisclosurePlanner(graph)
                .plan(Vocabulary.service(o.service()), purposeOf(o), conditionsOf(o), heldIris, o.includeOptional());
        if (o.json()) {
            System.out.println(JsonOutput.mapper(graph).writeValueAsString(result));
        } else {
            System.out.print(result.report());
        }
        return result.isComplete() ? 0 : 2;
    }

    private static int minimize(KnowledgeGraph graph, Options o) throws IOException {
        ObjectMapper plain = new ObjectMapper();
        JsonNode body = plain.readTree(Files.readString(o.payload()));
        if (!(body instanceof ObjectNode object)) {
            throw new IllegalArgumentException("payload must be a JSON object");
        }
        Map<String, IRI> fieldTypes = PayloadMinimizer.parseMapping(plain.readTree(Files.readString(o.mapping())));
        MinimizationResult result = new PayloadMinimizer(graph)
                .minimize(Vocabulary.service(o.service()), purposeOf(o), conditionsOf(o), object, fieldTypes, o.keepUnmapped());
        ObjectMapper out = JsonOutput.mapper(graph);
        if (o.json()) {
            System.out.println(out.writeValueAsString(result));
        } else {
            System.out.println(out.writeValueAsString(result.minimized()));
            System.err.print(result.report());
        }
        return result.isComplete() ? 0 : 2;
    }

    private static int decide(KnowledgeGraph graph, Options o) throws IOException {
        List<IRI> provided = o.data().stream().map(Vocabulary::data).toList();
        Instant started = Instant.now();
        Decision decision = new DataMinimizationFilter(graph)
                .evaluate(Vocabulary.service(o.service()), purposeOf(o), conditionsOf(o), provided);
        Instant ended = Instant.now();
        if (o.json()) {
            System.out.println(JsonOutput.mapper(graph).writeValueAsString(decision));
        } else if (o.rdf()) {
            StringWriter w = new StringWriter();
            ProvenanceWriter.write(decision, started, ended, w);
            System.out.print(w);
        } else {
            System.out.print(decision.report());
        }
        return decision.isCompliant() ? 0 : 2;
    }

    // -- helpers --------------------------------------------------------------------

    private static IRI purposeOf(Options o) {
        return o.purpose() == null ? null : Vocabulary.purpose(o.purpose());
    }

    private static Set<IRI> conditionsOf(Options o) {
        return new LinkedHashSet<>(o.context().stream().map(Vocabulary::condition).toList());
    }

    private static List<String> split(String csv) {
        return Arrays.stream(csv.split("\\s*,\\s*")).filter(s -> !s.isBlank()).toList();
    }

    private static String value(String[] args, int i) {
        if (i >= args.length) {
            throw new UsageException("missing value for " + args[i - 1]);
        }
        return args[i];
    }

    private static int parsePort(String s) {
        try {
            int p = Integer.parseInt(s);
            if (p < 0 || p > 65535) {
                throw new UsageException("--port must be between 0 and 65535");
            }
            return p;
        } catch (NumberFormatException e) {
            throw new UsageException("--port must be a number");
        }
    }

    static String usage() {
        return String.join("\n",
                "usage: --service NAME --data A,B,C [--purpose NAME] [--context C1,C2] [--json | --rdf] [--model FILE]",
                "       --service NAME --plan --held A,B,C [--include-optional] [--purpose NAME] [--context C1,C2] [--json]",
                "       --service NAME --minimize payload.json --mapping mapping.json [--keep-unmapped] [--purpose NAME] [--context C1,C2] [--json]",
                "       --list [--json] [--model FILE]",
                "       --validate [--json] [--model FILE]",
                "       --evaluate [--out DIR] [--model FILE]",
                "       --diagrams [--out DIR] [--model FILE]",
                "       --serve [--host H] [--port N] [--model FILE]",
                "",
                "  --service NAME   service the data are submitted to, e.g. SubscriptionSignUp",
                "  --data A,B,C     personal data types offered, e.g. IDCard,Email,IsAdult",
                "  --purpose NAME   evaluate for one purpose of the service only (default: all purposes)",
                "  --context C1,C2  conditions that currently hold, e.g. ContainsAlcohol",
                "  --plan           compute what a user agent holding --held should disclose, and how",
                "  --held A,B,C     personal data types the user agent holds",
                "  --include-optional  also plan optional data the service may use",
                "  --minimize FILE  minimize a JSON payload; prints the reduced payload, actions go to stderr",
                "  --mapping FILE   JSON object mapping payload fields to data types, e.g. {\"dob\": \"DateOfBirth\"}",
                "  --keep-unmapped  pass payload fields without a mapping through instead of dropping them",
                "  --list           print all services with their purposes and the data they need",
                "  --validate       check the ontology (SHACL shapes, cycles, redundant requirements)",
                "  --evaluate       run the labelled corpus, the baseline comparison and the benchmarks; writes --out/evaluation.md (default docs/)",
                "  --diagrams       write Mermaid diagrams of the model to --out/model.md (default docs/)",
                "  --serve          start the HTTP API and demo page on 127.0.0.1:7070",
                "  --host H         bind the API to another interface (unauthenticated; use behind a proxy)",
                "  --port N         port for --serve (default 7070)",
                "  --json           print the result as JSON (decision, plan, minimize, validate, list)",
                "  --rdf            print the decision as a PROV-O audit record in Turtle",
                "  --model FILE     ontology file to use instead of the bundled model.ttl",
                "  --out DIR        output directory for --evaluate and --diagrams (default docs)");
    }
}
