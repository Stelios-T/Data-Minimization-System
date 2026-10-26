package dataminimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.NoSuchFileException;

import org.eclipse.rdf4j.rio.RDFParseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The command-line contract: exit codes and error classes. */
class MainTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private PrintStream originalOut;

    @BeforeEach
    void capture() {
        originalOut = System.out;
        System.setOut(new PrintStream(out));
    }

    @AfterEach
    void restore() {
        System.setOut(originalOut);
    }

    @Test
    void exitCodesFollowTheVerdict() throws Exception {
        assertEquals(0, Main.run(new String[] {"--service", "SubscriptionSignUp", "--data", "FirstName,LastName,Email"}));
        assertEquals(2, Main.run(new String[] {"--service", "SubscriptionSignUp", "--data", "IDCard,Email,IsAdult"}));
        assertEquals(2, Main.run(new String[] {"--service", "RestaurantFinder", "--data", "City", "--context", "ContainsAlcohol"}));
        assertEquals(0, Main.run(new String[] {"--service", "CheckoutPayAdult", "--plan", "--held", "DateOfBirth,Account"}));
        assertEquals(0, Main.run(new String[] {"--validate"}));
        assertEquals(0, Main.run(new String[] {"--list"}));
        assertTrue(out.toString().contains("Verdict: EXCESSIVE"));
    }

    @Test
    void usageErrorsAreUsageExceptions() {
        assertThrows(Main.UsageException.class, () -> Main.run(new String[] {"--bogus"}));
        assertThrows(Main.UsageException.class, () -> Main.run(new String[] {"--service"}));
        assertThrows(Main.UsageException.class, () -> Main.run(new String[] {"--service", "EntryAllowance"}));
        assertThrows(Main.UsageException.class, () -> Main.run(new String[] {"--service", "EntryAllowance", "--plan"}));
        assertThrows(Main.UsageException.class, () -> Main.run(new String[] {"--serve", "--port", "abc"}));
        assertThrows(Main.UsageException.class, () -> Main.run(new String[] {"--serve", "--port", "70000"}));
    }

    @Test
    void conflictingFlagsAreRejected() {
        assertThrows(Main.UsageException.class, () -> Main.run(new String[] {"--list", "--validate"}));
        assertThrows(Main.UsageException.class, () -> Main.run(new String[] {"--service", "EntryAllowance", "--data", "IsAdult", "--json", "--rdf"}));
        assertThrows(Main.UsageException.class, () -> Main.run(new String[] {"--list", "--service", "EntryAllowance"}));
        assertThrows(Main.UsageException.class, () -> Main.run(new String[] {"--service", "EntryAllowance", "--plan", "--held", "IsAdult", "--rdf"}));
        assertThrows(Main.UsageException.class, () -> Main.run(new String[] {"--diagrams", "--json"}));
        assertThrows(Main.UsageException.class, () -> Main.run(new String[] {"--validate", "--mapping", "x.json"}));
    }

    @Test
    void helpAndJsonModesSucceed() throws Exception {
        assertEquals(0, Main.run(new String[] {"--help"}));
        assertTrue(out.toString().contains("usage:"));
        assertEquals(0, Main.run(new String[] {"--list", "--json"}));
        assertEquals(0, Main.run(new String[] {"--validate", "--json"}));
        assertEquals(2, Main.run(new String[] {"--service", "EntryAllowance", "--data", "Passport", "--rdf"}));
        assertTrue(out.toString().contains("prov:Activity"));
    }

    @Test
    void domainErrorsAreNotUsageErrors() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Main.run(new String[] {"--service", "Nope", "--data", "Email"}));
        assertTrue(!(e instanceof Main.UsageException));
        assertThrows(NoSuchFileException.class, () -> Main.run(new String[] {"--model", "missing.ttl", "--validate"}));
        assertThrows(RDFParseException.class, () -> Main.run(new String[] {"--model", "pom.xml", "--validate"}));
    }
}
