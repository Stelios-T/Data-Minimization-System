package dataminimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dataminimization.http.Server;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ServerTest {

    static KnowledgeGraph graph;
    static Javalin app;
    static String base;
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void start() {
        graph = KnowledgeGraph.loadDefault();
        app = new Server(graph).start(0);
        base = "http://localhost:" + app.port();
    }

    @AfterAll
    static void stop() {
        app.stop();
        graph.close();
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(base + path)).header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void listsServicesAndData() throws Exception {
        JsonNode services = JSON.readTree(get("/api/services").body());
        assertEquals(graph.services().size(), services.size());
        JsonNode data = JSON.readTree(get("/api/data").body());
        assertEquals(graph.personalData().size(), data.size());
        assertTrue(data.get(0).has("group") && data.get(0).has("sensitivity") && data.get(0).has("cost"));
        assertEquals(200, get("/api/services/SubscriptionSignUp").statusCode());
        assertEquals(400, get("/api/services/Nope").statusCode());
    }

    @Test
    void evaluateMatchesTheLibrary() throws Exception {
        HttpResponse<String> r = post("/api/evaluate", "{\"service\":\"SubscriptionSignUp\",\"data\":[\"IDCard\",\"Email\",\"IsAdult\"]}");
        assertEquals(200, r.statusCode());
        JsonNode body = JSON.readTree(r.body());
        assertEquals("EXCESSIVE", body.get("decision").get("verdict").asText());
        assertTrue(body.get("report").asText().contains("request FirstName, LastName instead"));

        HttpResponse<String> ctx = post("/api/evaluate", "{\"service\":\"RestaurantFinder\",\"context\":[\"ContainsAlcohol\"],\"data\":[\"City\"]}");
        assertEquals("INCOMPLETE", JSON.readTree(ctx.body()).get("decision").get("verdict").asText());
    }

    @Test
    void planAndMinimizeWork() throws Exception {
        JsonNode plan = JSON.readTree(post("/api/plan", "{\"service\":\"CheckoutPayAdult\",\"held\":[\"DateOfBirth\",\"Account\"]}").body());
        assertEquals(3, plan.get("plan").get("steps").size());

        JsonNode min = JSON.readTree(post("/api/minimize", "{\"service\":\"EntryAllowance\",\"payload\":{\"dob\":\"1990-01-01\"},\"mapping\":{\"dob\":\"DateOfBirth\"}}").body());
        assertTrue(min.get("result").get("minimized").get("isAdult").asBoolean());
    }

    @Test
    void badInputGives400() throws Exception {
        assertEquals(400, post("/api/evaluate", "{\"data\":[\"Email\"]}").statusCode());
        assertEquals(400, post("/api/evaluate", "not json").statusCode());
        assertEquals(400, post("/api/evaluate", "{\"service\":\"Nope\",\"data\":[]}").statusCode());
    }

    @Test
    void malformedBodiesAreRejectedInsteadOfGuessed() throws Exception {
        HttpResponse<String> misspelled = post("/api/evaluate", "{\"service\":\"EntryAllowance\",\"dat\":[\"IsAdult\"]}");
        assertEquals(400, misspelled.statusCode());
        assertTrue(misspelled.body().contains("unknown field"), misspelled.body());
        assertEquals(400, post("/api/evaluate", "{\"service\":\"EntryAllowance\"}").statusCode(), "missing data");
        assertEquals(400, post("/api/evaluate", "{\"service\":\"EntryAllowance\",\"data\":\"IsAdult\"}").statusCode(), "data not an array");
        assertEquals(400, post("/api/evaluate", "{\"service\":\"EntryAllowance\",\"data\":[\"\"]}").statusCode(), "empty name");
        assertEquals(400, post("/api/evaluate", "{\"service\":\"EntryAllowance\",\"data\":[\"IsAdult\"],\"context\":\"x\"}").statusCode(), "context not an array");
        assertEquals(400, post("/api/plan", "{\"service\":\"EntryAllowance\",\"held\":[\"IsAdult\"],\"includeOptional\":\"yes\"}").statusCode(), "flag not boolean");
        assertEquals(400, post("/api/minimize", "{\"service\":\"EntryAllowance\",\"payload\":{},\"mapping\":{\"dob\":1}}").statusCode(), "mapping value not a name");
        assertEquals(200, post("/api/evaluate", "{\"service\":\"EntryAllowance\",\"data\":[],\"purpose\":null}").statusCode(), "empty data is a valid question");
    }

    @Test
    void validationIsServedAndStable() throws Exception {
        assertEquals(get("/api/validate").body(), get("/api/validate").body());
        assertEquals("[ ]", get("/api/validate").body().trim());
    }

    @Test
    void bindsToLoopbackOnly() {
        assertTrue(app.jettyServer().server().getURI().getHost().startsWith("127.0.0.1")
                || app.jettyServer().server().getURI().getHost().equals("localhost"), app.jettyServer().server().getURI().toString());
    }

    @Test
    void servesTheDemoPage() throws Exception {
        HttpResponse<String> r = get("/");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("Data Minimization System"));
    }
}
