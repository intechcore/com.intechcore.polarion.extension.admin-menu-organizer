package com.intechcore.polarion.extension.admin_menu_organizer.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.NotNull;
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
 * Every save writes a revision of the global {@code Default} setting, and a revision cannot be
 * removed through the API. The modes are put back, the history is not: run this against a
 * development instance.
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
    private static final String REVISIONS = SETTINGS + "/names/Default/revisions?scope=";
    private static final String SETTING = SETTINGS + "/names/Default?scope=";

    private final PolarionSystemTestSupport polarion = new PolarionSystemTestSupport();

    /** Set once the state of the server was read, so a setup that failed puts nothing back. */
    private boolean captured;

    /**
     * The modes of the server as the run found them, or null when it stored none. They are one
     * global setting, which the browser suite writes too: run one suite at a time against an
     * instance, or one cleanup puts back what the other one was using.
     */
    private String storedModes;

    @BeforeAll
    void signInAndRememberWhatTheServerHolds() {
        PolarionSystemTestSupport.assumeAPolarionIsRunning();
        polarion.logIn();

        // A server that stores nothing answers the content endpoint with the defaults, which reads
        // the same as two false modes. The revisions tell the two apart: they are empty exactly
        // while nothing is stored, and a run that found no setting has to leave none behind.
        if (revisionCount() > 0) {
            JsonNode stored = polarion.json(CONTENT);
            storedModes = modes(mode(stored, "foldersAlphabetically"), mode(stored, "entriesInDeclarationOrder"));
        }
        captured = true;
    }

    /**
     * Whatever the server held before the run is what it holds after it. The write is checked and
     * read back: a silent failure here would leave a live server carrying the modes of a test.
     */
    @AfterAll
    void restoreWhatTheServerHeld() {
        if (!captured) {
            return;
        }
        if (storedModes == null) {
            HttpResponse<String> deleted = polarion.delete(SETTING);
            assertThat(deleted.statusCode()).as("removing the setting the run created: %s", deleted.body()).isIn(200, 204);
            assertThat(revisionCount()).as("the server stored no setting before the run").isZero();
            return;
        }
        HttpResponse<String> restored = polarion.put(CONTENT, storedModes);
        assertThat(restored.statusCode()).as("restoring the stored modes: %s", restored.body()).isIn(200, 204);
        JsonNode read = polarion.json(CONTENT);
        assertThat(modes(mode(read, "foldersAlphabetically"), mode(read, "entriesInDeclarationOrder")))
                .as("the modes the server held before the run").isEqualTo(storedModes);
    }


    /**
     * Reads one mode. An answer of the wrong shape, such as {@code {}} or a field that is not a
     * boolean, would otherwise arrive here as false, and the run would remember "both modes off" as
     * what the server held and write that back at the end.
     */
    private static boolean mode(@NotNull JsonNode content, @NotNull String field) {
        JsonNode value = content.path(field);
        if (!value.isBoolean()) {
            throw new IllegalStateException("The setting holds no boolean '%s': %s".formatted(field, content));
        }
        return value.booleanValue();
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
        assertThat(mode(read, "foldersAlphabetically")).isTrue();
        assertThat(mode(read, "entriesInDeclarationOrder")).isTrue();

        read = saveAndReadBack(false, true);
        assertThat(mode(read, "foldersAlphabetically")).isFalse();
        assertThat(mode(read, "entriesInDeclarationOrder")).isTrue();

        read = saveAndReadBack(false, false);
        assertThat(mode(read, "foldersAlphabetically")).isFalse();
        assertThat(mode(read, "entriesInDeclarationOrder")).isFalse();
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

        assertThat(mode(defaults, "foldersAlphabetically")).isFalse();
        assertThat(mode(defaults, "entriesInDeclarationOrder")).isFalse();
    }

    private int revisionCount() {
        JsonNode revisions = polarion.json(REVISIONS);
        assertThat(revisions.isArray()).as("the revisions of the setting").isTrue();
        return revisions.size();
    }

    /**
     * Every save is kept, which is what the Revisions button of the page offers. Two saves of two
     * different values, so a store that overwrote its history or dropped the newest value would show.
     */
    @Test
    void keepsEveryRevisionOfTheSetting() {
        int before = revisionCount();

        saveAndReadBack(true, false);
        saveAndReadBack(false, true);

        assertThat(revisionCount()).as("two saves, two more revisions").isGreaterThanOrEqualTo(before + 2);
        JsonNode read = polarion.json(CONTENT);
        assertThat(mode(read, "foldersAlphabetically")).isFalse();
        assertThat(mode(read, "entriesInDeclarationOrder")).isTrue();
    }

}
