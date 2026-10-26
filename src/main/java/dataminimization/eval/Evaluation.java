package dataminimization.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import dataminimization.DataAssessment;
import dataminimization.DataMinimizationFilter;
import dataminimization.Decision;
import dataminimization.DisclosurePlanner;
import dataminimization.KnowledgeGraph;
import dataminimization.Status;
import dataminimization.Verdict;
import dataminimization.Vocabulary;
import org.eclipse.rdf4j.model.IRI;

/**
 * Runs the labelled corpus through the system and through the exact-match baseline,
 * measures decision latency on the bundled model and on synthetic models of growing size,
 * and writes {@code evaluation.md} and {@code evaluation.csv}.
 */
public final class Evaluation {

    static final int WARMUP = 200;
    static final int SAMPLES = 2_000;
    static final int PLAN_SAMPLES = 500;
    static final int[] SCALE_GROUPS = {3, 10, 50, 200};

    private Evaluation() {
    }

    /** Confusion counts for the "reject" class. */
    static final class Confusion {
        int tp, fp, fn, tn;

        void add(boolean expectedReject, boolean predictedReject) {
            if (expectedReject && predictedReject) tp++;
            else if (!expectedReject && predictedReject) fp++;
            else if (expectedReject) fn++;
            else tn++;
        }

        int total() { return tp + fp + fn + tn; }
        double precision() { return tp + fp == 0 ? 1.0 : (double) tp / (tp + fp); }
        double recall() { return tp + fn == 0 ? 1.0 : (double) tp / (tp + fn); }
        double f1() { double p = precision(), r = recall(); return p + r == 0 ? 0 : 2 * p * r / (p + r); }
        double accuracy() { return total() == 0 ? 1.0 : (double) (tp + tn) / total(); }
    }

    record ScenarioRow(Scenario scenario, Verdict system, Verdict baseline, boolean systemMissingOk, boolean baselineMissingOk,
                       Boolean substitutesOk, double p50Micros) {
    }

    record ScaleRow(int types, int services, long loadMillis, double firstCallMicros, double warmP50Micros) {
    }

    public static void run(Path outDir) throws IOException {
        run(outDir, null);
    }

    /** @param model ontology file to evaluate, or {@code null} for the bundled one */
    public static void run(Path outDir, Path model) throws IOException {
        Files.createDirectories(outDir);
        try (KnowledgeGraph graph = model == null ? KnowledgeGraph.loadDefault() : KnowledgeGraph.load(model)) {
            DataMinimizationFilter filter = new DataMinimizationFilter(graph);
            ExactMatchBaseline baseline = new ExactMatchBaseline(graph);
            DisclosurePlanner planner = new DisclosurePlanner(graph);
            Scenario.Corpus corpus = Scenario.loadDefault();

            Confusion system = new Confusion();
            Confusion naive = new Confusion();
            int systemVerdictOk = 0, baselineVerdictOk = 0, systemMissingOk = 0, baselineMissingOk = 0, substitutesOk = 0, substitutesTotal = 0;
            Map<Verdict, Integer> verdictCounts = new EnumMap<>(Verdict.class);
            Map<Status, Integer> statusCounts = new EnumMap<>(Status.class);
            List<ScenarioRow> rows = new ArrayList<>();
            List<Long> evaluateSamples = new ArrayList<>();

            for (Scenario s : corpus.evaluations()) {
                Decision d = filter.evaluate(s.service(), s.purpose(), s.context(), s.provided());
                ExactMatchBaseline.Result b = baseline.evaluate(s.service(), s.purpose(), s.context(), s.provided());
                Map<IRI, Status> statuses = d.assessments().stream().collect(Collectors.toMap(DataAssessment::datum, DataAssessment::status));
                s.expectedStatuses().forEach((datum, expected) -> {
                    boolean expectedReject = !expected.isMinimal();
                    system.add(expectedReject, !statuses.get(datum).isMinimal());
                    naive.add(expectedReject, !b.accepted().get(datum));
                    statusCounts.merge(expected, 1, Integer::sum);
                });
                verdictCounts.merge(s.expectedVerdict(), 1, Integer::sum);
                boolean sv = d.verdict() == s.expectedVerdict(), bv = b.verdict() == s.expectedVerdict();
                boolean sm = d.missing().equals(s.expectedMissing()), bm = b.missing().equals(s.expectedMissing());
                if (sv) systemVerdictOk++;
                if (bv) baselineVerdictOk++;
                if (sm) systemMissingOk++;
                if (bm) baselineMissingOk++;
                Boolean subsOk = null;
                if (!s.expectedSubstitutes().isEmpty()) {
                    substitutesTotal++;
                    subsOk = s.expectedSubstitutes().entrySet().stream().allMatch(e -> d.assessments().stream()
                            .filter(a -> a.datum().equals(e.getKey())).findFirst().map(a -> a.substitutes().equals(e.getValue())).orElse(false));
                    if (subsOk) substitutesOk++;
                }
                long[] samples = time(() -> filter.evaluate(s.service(), s.purpose(), s.context(), s.provided()), WARMUP, SAMPLES);
                for (long x : samples) evaluateSamples.add(x);
                rows.add(new ScenarioRow(s, d.verdict(), b.verdict(), sm, bm, subsOk, percentile(samples, 50) / 1000.0));
            }

            List<Long> planSamples = new ArrayList<>();
            for (Scenario.Plan p : corpus.plans()) {
                long[] samples = time(() -> planner.plan(p.service(), p.purpose(), p.context(), p.held(), p.includeOptional()), WARMUP, PLAN_SAMPLES);
                for (long x : samples) planSamples.add(x);
            }

            List<ScaleRow> scale = new ArrayList<>();
            for (int groups : SCALE_GROUPS) {
                long t0 = System.nanoTime();
                try (KnowledgeGraph big = SyntheticGraph.generate(groups)) {
                    long loadMillis = (System.nanoTime() - t0) / 1_000_000;
                    DataMinimizationFilter f = new DataMinimizationFilter(big);
                    IRI service = Vocabulary.service("Service0");
                    List<IRI> provided = List.of(Vocabulary.data("Doc0"), Vocabulary.data("Part0_4"));
                    long c0 = System.nanoTime();
                    f.evaluate(service, null, Set.of(), provided);
                    double firstCall = (System.nanoTime() - c0) / 1000.0;
                    long[] warm = time(() -> f.evaluate(service, null, Set.of(), provided), 50, 300);
                    scale.add(new ScaleRow(groups * SyntheticGraph.GROUP_SIZE, groups, loadMillis, firstCall, percentile(warm, 50) / 1000.0));
                }
            }

            long[] evalArr = evaluateSamples.stream().mapToLong(Long::longValue).toArray();
            long[] planArr = planSamples.stream().mapToLong(Long::longValue).toArray();
            Files.writeString(outDir.resolve("evaluation.md"), markdown(graph, corpus, system, naive, systemVerdictOk, baselineVerdictOk,
                    systemMissingOk, baselineMissingOk, substitutesOk, substitutesTotal, verdictCounts, statusCounts, evalArr, planArr, scale, rows),
                    StandardCharsets.UTF_8);
            Files.writeString(outDir.resolve("evaluation.csv"), csv(rows), StandardCharsets.UTF_8);
        }
    }

    static long[] time(Supplier<?> action, int warmup, int samples) {
        for (int i = 0; i < warmup; i++) action.get();
        long[] out = new long[samples];
        for (int i = 0; i < samples; i++) {
            long t = System.nanoTime();
            action.get();
            out[i] = System.nanoTime() - t;
        }
        return out;
    }

    static long percentile(long[] samples, int p) {
        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }

    private static String pct(double v) {
        return String.format("%.1f%%", v * 100);
    }

    private static String micros(long nanos) {
        return String.format("%.1f", nanos / 1000.0);
    }

    static String markdown(KnowledgeGraph graph, Scenario.Corpus corpus, Confusion system, Confusion naive, int sVerdict, int bVerdict, int sMissing, int bMissing,
                           int subsOk, int subsTotal, Map<Verdict, Integer> verdicts, Map<Status, Integer> statuses,
                           long[] eval, long[] plan, List<ScaleRow> scale, List<ScenarioRow> rows) {
        int n = corpus.evaluations().size();
        StringBuilder sb = new StringBuilder();
        sb.append("# Evaluation\n\n");
        sb.append("Generated on ").append(LocalDate.now()).append(" by `java -jar target/dms.jar --evaluate` on ")
                .append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.arch")).append(", Java ")
                .append(System.getProperty("java.version")).append(", ").append(Runtime.getRuntime().availableProcessors())
                .append(" cores. Regenerate it after changing the model or the scenarios.\n\n");

        sb.append("## Corpus\n\n");
        sb.append(n).append(" decision scenarios and ").append(corpus.plans().size())
                .append(" disclosure-plan scenarios in `src/main/resources/scenarios.json`. Expected outcomes were labelled by hand from the relation semantics of the model; `ScenarioTest` enforces them, so the corpus doubles as a regression suite.\n\n");
        sb.append("| Expected verdict | Scenarios |\n|---|---|\n");
        verdicts.forEach((v, c) -> sb.append("| ").append(v).append(" | ").append(c).append(" |\n"));
        sb.append("\n| Labelled datum status | Data points |\n|---|---|\n");
        statuses.forEach((s, c) -> sb.append("| ").append(s).append(" | ").append(c).append(" |\n"));

        sb.append("\n## Decision quality: knowledge graph vs exact-match baseline\n\n");
        sb.append("The baseline is what a form validator does without a knowledge graph: accept a datum only if the service names it, and report a required datum as missing unless it is offered literally. The \"reject\" class is the privacy-relevant one.\n\n");
        sb.append("| Metric | Knowledge graph | Exact match |\n|---|---|---|\n");
        sb.append("| Labelled data points | ").append(system.total()).append(" | ").append(naive.total()).append(" |\n");
        sb.append("| Reject precision | ").append(pct(system.precision())).append(" | ").append(pct(naive.precision())).append(" |\n");
        sb.append("| Reject recall | ").append(pct(system.recall())).append(" | ").append(pct(naive.recall())).append(" |\n");
        sb.append("| Reject F1 | ").append(pct(system.f1())).append(" | ").append(pct(naive.f1())).append(" |\n");
        sb.append("| Accept/reject accuracy | ").append(pct(system.accuracy())).append(" | ").append(pct(naive.accuracy())).append(" |\n");
        sb.append("| Verdict correct | ").append(sVerdict).append('/').append(n).append(" | ").append(bVerdict).append('/').append(n).append(" |\n");
        sb.append("| Missing set correct | ").append(sMissing).append('/').append(n).append(" | ").append(bMissing).append('/').append(n).append(" |\n");
        sb.append("| Substitute suggestion correct | ").append(subsOk).append('/').append(subsTotal).append(" | not available |\n\n");
        sb.append("Where the baseline fails: it cannot see that an ID card contains the names (so it reports them missing), that a date of birth implies adulthood, or that an account decomposes into exactly its number and balance. It also cannot name a narrower substitute.\n\n");

        sb.append("## Latency on the evaluated model (").append(graph.personalData().size()).append(" data types, ")
                .append(graph.services().size()).append(" services)\n\n");
        sb.append("Steady state after ").append(WARMUP).append(" warm-up calls per scenario; ").append(SAMPLES).append(" timed calls per decision scenario and ")
                .append(PLAN_SAMPLES).append(" per plan scenario. Microseconds per call, single thread.\n\n");
        sb.append("| Statistic | evaluate | plan |\n|---|---|---|\n");
        for (int p : new int[] {50, 95, 99}) {
            sb.append("| p").append(p).append(" | ").append(micros(percentile(eval, p))).append(" | ").append(micros(percentile(plan, p))).append(" |\n");
        }
        sb.append("| max | ").append(micros(Arrays.stream(eval).max().orElse(0))).append(" | ").append(micros(Arrays.stream(plan).max().orElse(0))).append(" |\n");
        sb.append("| samples | ").append(eval.length).append(" | ").append(plan.length).append(" |\n\n");

        sb.append("## Scale\n\n");
        sb.append("Synthetic models with the shape of the bundled one (see `SyntheticGraph`). One decision per model: a document and an unrelated part offered to a service that needs two parts and a coarse derived value. The first call pays for the SPARQL property-path queries; later calls hit the per-datum cache.\n\n");
        sb.append("| Data types | Services | Load (ms) | First call (µs) | Warm p50 (µs) |\n|---|---|---|---|---|\n");
        for (ScaleRow r : scale) {
            sb.append("| ").append(r.types()).append(" | ").append(r.services()).append(" | ").append(r.loadMillis()).append(" | ")
                    .append(String.format("%.0f", r.firstCallMicros())).append(" | ").append(String.format("%.1f", r.warmP50Micros())).append(" |\n");
        }

        sb.append("\n## Per-scenario results\n\n| Id | Scenario | Expected | Knowledge graph | Exact match | p50 (µs) |\n|---|---|---|---|---|---|\n");
        for (ScenarioRow r : rows) {
            sb.append("| ").append(r.scenario().id()).append(" | ").append(r.scenario().label()).append(" | ").append(r.scenario().expectedVerdict())
                    .append(" | ").append(r.system()).append(" | ").append(r.baseline()).append(" | ").append(String.format("%.1f", r.p50Micros())).append(" |\n");
        }

        sb.append("\n## Caveats\n\n");
        sb.append("- The labels were written by the author of the model, so the quality figures measure agreement with the intended semantics, not with an independent expert panel. An external labelling study would strengthen the claim.\n");
        sb.append("- Latency figures are from one machine and one run; treat them as orders of magnitude.\n");
        sb.append("- The baseline is deliberately naive. A richer comparison would include a flat allow-list per service with hand-maintained aliases, which is what the knowledge graph replaces.\n");
        return sb.toString();
    }

    static String csv(List<ScenarioRow> rows) {
        StringBuilder sb = new StringBuilder("id,label,service,purpose,context,provided,expected_verdict,system_verdict,baseline_verdict,system_missing_ok,baseline_missing_ok,substitutes_ok,system_p50_us\n");
        for (ScenarioRow r : rows) {
            Scenario s = r.scenario();
            sb.append(s.id()).append(',').append(quote(s.label())).append(',').append(s.service().getLocalName()).append(',')
                    .append(s.purpose() == null ? "" : s.purpose().getLocalName()).append(',')
                    .append(quote(s.context().stream().map(IRI::getLocalName).collect(Collectors.joining(";")))).append(',')
                    .append(quote(s.provided().stream().map(IRI::getLocalName).collect(Collectors.joining(";")))).append(',')
                    .append(s.expectedVerdict()).append(',').append(r.system()).append(',').append(r.baseline()).append(',')
                    .append(r.systemMissingOk()).append(',').append(r.baselineMissingOk()).append(',')
                    .append(r.substitutesOk() == null ? "" : r.substitutesOk()).append(',').append(String.format("%.1f", r.p50Micros())).append('\n');
        }
        return sb.toString();
    }

    private static String quote(String s) {
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    public static void main(String[] args) throws IOException {
        run(Path.of(args.length > 0 ? args[0] : "docs"), args.length > 1 ? Path.of(args[1]) : null);
    }
}
