package dataminimization;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.rdf4j.model.IRI;

/**
 * Command-line entry point.
 *
 * <pre>
 *   --service NAME       service the data are submitted to (local name, e.g. SubscriptionSignUp)
 *   --data A,B,C         personal data types offered (local names, comma separated)
 *   --model FILE         ontology to use instead of the bundled model.ttl
 *   --list               list services and the data they need, then exit
 * </pre>
 * Without arguments the shipped example is run. Exit code 0 means COMPLIANT, 2 means any
 * other verdict, 1 means a usage error.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (IllegalArgumentException | IOException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println(usage());
            System.exit(1);
        }
    }

    static int run(String[] args) throws IOException {
        String service = null;
        List<String> data = new ArrayList<>();
        Path model = null;
        boolean list = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--service" -> service = value(args, ++i);
                case "--data" -> data.addAll(Arrays.asList(value(args, ++i).split("\\s*,\\s*")));
                case "--model" -> model = Path.of(value(args, ++i));
                case "--list" -> list = true;
                case "-h", "--help" -> {
                    System.out.println(usage());
                    return 0;
                }
                default -> throw new IllegalArgumentException("unexpected argument: " + args[i]);
            }
        }

        if (args.length == 0) {
            System.out.println("No arguments given; running the shipped example. Use --help for options.\n");
            service = "SubscriptionSignUp";
            data = List.of("IDCard", "Email", "IsAdult");
        }

        try (KnowledgeGraph graph = model == null ? KnowledgeGraph.loadDefault() : KnowledgeGraph.load(model)) {
            if (list) {
                for (IRI s : graph.services()) {
                    System.out.printf("%-20s requires %s%n", s.getLocalName(), Decision.names(graph.required(s)));
                    System.out.printf("%-20s may use  %s%n", "", Decision.names(graph.optional(s)));
                }
                return 0;
            }
            if (service == null || data.isEmpty()) {
                throw new IllegalArgumentException("--service and --data are both required");
            }
            List<IRI> provided = data.stream().map(Vocabulary::data).toList();
            Decision decision = new DataMinimizationFilter(graph).evaluate(Vocabulary.service(service), provided);
            System.out.print(decision.report());
            return decision.isCompliant() ? 0 : 2;
        }
    }

    private static String value(String[] args, int i) {
        if (i >= args.length) {
            throw new IllegalArgumentException("missing value for " + args[i - 1]);
        }
        return args[i];
    }

    private static String usage() {
        return String.join("\n",
                "usage: --service NAME --data A,B,C [--model FILE]",
                "       --list [--model FILE]",
                "",
                "  --service NAME   service the data are submitted to, e.g. SubscriptionSignUp",
                "  --data A,B,C     personal data types offered, e.g. IDCard,Email,IsAdult",
                "  --model FILE     ontology file to use instead of the bundled model.ttl",
                "  --list           print all services and the data they need");
    }
}
