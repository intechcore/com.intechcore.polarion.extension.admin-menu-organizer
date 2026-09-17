package com.intechcore.polarion.extension.admin_menu_organizer.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs against a running Polarion, never on CI: {@code mvn -Psystem-tests test}.
 * <p>
 * The settings endpoints of the generic framework need a session. Under a token they answer 409,
 * "Cannot find request attributes in the request context", so this signs in with the login form.
 * <p>
 * The order the menu is drawn in is asserted by the Playwright suite in {@code ui/systest}, which
 * reads it from the page. What is asserted here is everything up to that point: the setting is
 * stored where it belongs, refused where it does not, and the save reaches the extension.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MenuOrderSystemTest {

    private static final String SETTINGS = "/polarion/admin-menu-organizer/rest/internal/settings/menu-order";
    private static final String CONTENT = SETTINGS + "/names/Default/content?scope=";

    private final PolarionSystemTestSupport polarion = new PolarionSystemTestSupport();
    private String storedModes;

    @BeforeAll
    void signInAndRememberTheStoredModes() {
        PolarionSystemTestSupport.assumeAPolarionIsRunning();
        polarion.logIn();

        JsonNode stored = polarion.json(CONTENT);
        storedModes = modes(stored.path("foldersAlphabetically").asBoolean(), stored.path("entriesInDeclarationOrder").asBoolean());
    }

    /** Whatever the server held before the run is what it holds after it. */
    @AfterAll
    void restoreTheStoredModes() {
        if (storedModes != null) {
            polarion.put(CONTENT, storedModes);
        }
    }

    private static String modes(boolean foldersAlphabetically, boolean entriesInDeclarationOrder) {
        return """
                {"foldersAlphabetically":%s,"entriesInDeclarationOrder":%s}"""
                .formatted(foldersAlphabetically, entriesInDeclarationOrder);
    }

    private JsonNode saveAndReadBack(boolean foldersAlphabetically, boolean entriesInDeclarationOrder) {
        HttpResponse<String> saved = polarion.put(CONTENT, modes(foldersAlphabetically, entriesInDeclarationOrder));
        // Generic answers a stored setting with 204 and no body.
        assertThat(saved.statusCode()).as("saving the modes: %s", saved.body()).isIn(200, 204);
        return polarion.json(CONTENT);
    }

    @Test
    void storesBothModesAndReadsThemBack() {
        JsonNode read = saveAndReadBack(true, true);
        assertThat(read.path("foldersAlphabetically").asBoolean()).isTrue();
        assertThat(read.path("entriesInDeclarationOrder").asBoolean()).isTrue();

        read = saveAndReadBack(false, true);
        assertThat(read.path("foldersAlphabetically").asBoolean()).isFalse();
        assertThat(read.path("entriesInDeclarationOrder").asBoolean()).isTrue();

        read = saveAndReadBack(false, false);
        assertThat(read.path("foldersAlphabetically").asBoolean()).isFalse();
        assertThat(read.path("entriesInDeclarationOrder").asBoolean()).isFalse();
    }

    /**
     * The menu is one list for the whole server, so a project scope has nothing to configure. The
     * scope is spelled the way generic wants it, otherwise its own check refuses the call first and
     * this extension never sees it.
     */
    @Test
    void refusesAProjectScope() {
        HttpResponse<String> response = polarion.put(
                SETTINGS + "/names/Default/content?scope=" + PolarionSystemTestSupport.encode("project/elibrary/"),
                modes(true, true));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("global scope");
    }

    /** Server start reads the Default setting only: another name would apply now and be lost at restart. */
    @Test
    void refusesAnotherSettingName() {
        HttpResponse<String> response = polarion.put(
                SETTINGS + "/names/Other/content?scope=", modes(true, true));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Default setting");
    }

    @Test
    void offersTheDefaultsWithBothModesOff() {
        JsonNode defaults = polarion.json(SETTINGS + "/default-content");

        assertThat(defaults.path("foldersAlphabetically").asBoolean()).isFalse();
        assertThat(defaults.path("entriesInDeclarationOrder").asBoolean()).isFalse();
    }

    @Test
    void keepsEveryRevisionOfTheSetting() {
        saveAndReadBack(true, false);

        JsonNode revisions = polarion.json(SETTINGS + "/names/Default/revisions?scope=");

        assertThat(revisions.isArray()).isTrue();
        assertThat(revisions).as("a saved setting has at least one revision").isNotEmpty();
    }

    /**
     * The save reorders the menu in the same request, and the extension says so in the log. Without a
     * readable log directory this is skipped: the rendered order is asserted by the Playwright suite.
     */
    @Test
    void reordersTheMenuWhenTheModesAreSaved() throws IOException, InterruptedException {
        Path logDirectory = Path.of(System.getenv().getOrDefault("POLARION_LOG_DIR",
                System.getenv().getOrDefault("POLARION_HOME", "/opt/polarion") + "/data/logs/main"));
        assumeTrue(Files.isDirectory(logDirectory), "No readable Polarion log directory at " + logDirectory);

        long linesBefore = orderedLines(logDirectory);
        saveAndReadBack(true, true);
        // The write happens inside the request, the log flush right after it.
        Thread.sleep(2000);

        assertThat(orderedLines(logDirectory)).as("a line reporting the new order").isGreaterThan(linesBefore);
    }

    private static long orderedLines(Path logDirectory) throws IOException {
        Path newest;
        try (Stream<Path> logs = Files.list(logDirectory)) {
            newest = logs.filter(path -> path.getFileName().toString().startsWith("log4j-"))
                    .max(Comparator.comparing(path -> path.toFile().lastModified()))
                    .orElseThrow(() -> new IllegalStateException("No log file in " + logDirectory));
        }
        try (Stream<String> lines = Files.lines(newest)) {
            List<String> ordered = lines.filter(line -> line.contains("Ordered ") && line.contains("administration menu entries")).toList();
            return ordered.size();
        }
    }
}
