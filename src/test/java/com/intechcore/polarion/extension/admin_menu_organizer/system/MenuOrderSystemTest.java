package com.intechcore.polarion.extension.admin_menu_organizer.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against a running Polarion, never on CI: {@code mvn -Psystem-tests test}.
 * <p>
 * The settings endpoints of the generic framework need a session. Under a token they answer 409,
 * "Cannot find request attributes in the request context", so this signs in with the login form.
 * <p>
 * The order the menu is drawn in is asserted by the Playwright suite in {@code ui/systest}, which
 * reads it from the page. What is asserted here is everything up to that point: the setting is
 * stored where it belongs and refused where it does not. Whether the save reached the menu is a
 * question about the drawn order, so it belongs to that other suite, and this repository does not
 * assert log output.
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

    /**
     * Whatever the server held before the run is what it holds after it. The write is checked and
     * read back: a silent failure here would leave a live server carrying the modes of a test.
     */
    @AfterAll
    void restoreTheStoredModes() {
        if (storedModes == null) {
            return;
        }
        HttpResponse<String> restored = polarion.put(CONTENT, storedModes);
        assertThat(restored.statusCode()).as("restoring the stored modes: %s", restored.body()).isIn(200, 204);
        JsonNode read = polarion.json(CONTENT);
        assertThat(modes(read.path("foldersAlphabetically").asBoolean(), read.path("entriesInDeclarationOrder").asBoolean()))
                .as("the modes the server held before the run").isEqualTo(storedModes);
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

}
