package com.intechcore.polarion.extension.admin_menu_organizer.menu;

import com.polarion.alm.administration.web.server.AdministrationPageExtender;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Polarion's order follows the identity hash codes of the entries, so every repetition of a test builds
 * fresh entries and meets a different starting order.
 */
class MenuOrderPlannerTest {

    private static final int REPETITIONS = 50;

    private static AdministrationPageExtender entry(String folderId, String folderName, String name) {
        AdministrationPageExtender extender = new AdministrationPageExtender();
        extender.setId(name.toLowerCase());
        extender.setName(name);
        extender.setParentNodeId(folderId);
        extender.setParentNodeName(folderName);
        return extender;
    }

    /** Two extensions and an entry outside any folder, in declaration order. */
    private static List<AdministrationPageExtender> declared() {
        return List.of(
                entry("pdf", "PDF Export", "About"),
                entry("pdf", "PDF Export", "User Guide"),
                entry("pdf", "PDF Export", "CSS"),
                entry("diff", "diff tool", "About"),
                entry("diff", "diff tool", "Settings"),
                entry(null, null, "Audit Log"));
    }

    private static String folderOf(Object entry) {
        AdministrationPageExtender extender = (AdministrationPageExtender) entry;
        return extender.getParentNodeName() != null ? extender.getParentNodeName() : extender.getName();
    }

    private static List<String> folders(List<?> entries) {
        return new ArrayList<>(new LinkedHashSet<>(entries.stream().map(MenuOrderPlannerTest::folderOf).toList()));
    }

    private static List<Object> entriesOf(List<?> entries, String folder) {
        return entries.stream().filter(entry -> folderOf(entry).equals(folder)).map(Object.class::cast).toList();
    }

    private static void assertContiguousFolders(List<?> entries) {
        assertEquals(Set.copyOf(folders(entries)).size(), folders(entries).size());
        List<String> runs = new ArrayList<>();
        for (Object entry : entries) {
            if (runs.isEmpty() || !runs.getLast().equals(folderOf(entry))) {
                runs.add(folderOf(entry));
            }
        }
        assertEquals(folders(entries), runs, "every folder must be one contiguous block");
    }

    @RepeatedTest(REPETITIONS)
    void keepsPolarionsOrderWithBothModesOff() {
        List<AdministrationPageExtender> declared = declared();

        assertEquals(MenuOrderPlanner.polarionOrder(declared), MenuOrderPlanner.plan(declared, new MenuOrderModes(false, false)));
    }

    @Test
    void polarionOrderHoldsTheSameEntriesAsDeclared() {
        List<AdministrationPageExtender> declared = declared();

        assertEquals(Set.copyOf(declared), Set.copyOf(MenuOrderPlanner.polarionOrder(declared)));
    }

    @RepeatedTest(REPETITIONS)
    void ordersEntriesByDeclarationAndKeepsTheFoldersWherePolarionPutThem() {
        List<AdministrationPageExtender> declared = declared();
        List<AdministrationPageExtender> polarionOrder = MenuOrderPlanner.polarionOrder(declared);

        List<AdministrationPageExtender> planned = MenuOrderPlanner.plan(declared, new MenuOrderModes(false, true));

        assertContiguousFolders(planned);
        assertEquals(folders(polarionOrder), folders(planned));
        for (String folder : folders(planned)) {
            assertEquals(entriesOf(declared, folder), entriesOf(planned, folder));
        }
    }

    @RepeatedTest(REPETITIONS)
    void ordersFoldersAlphabeticallyAndKeepsTheEntriesWherePolarionPutThem() {
        List<AdministrationPageExtender> declared = declared();
        List<AdministrationPageExtender> polarionOrder = MenuOrderPlanner.polarionOrder(declared);

        List<AdministrationPageExtender> planned = MenuOrderPlanner.plan(declared, new MenuOrderModes(true, false));

        assertContiguousFolders(planned);
        assertEquals(List.of("Audit Log", "diff tool", "PDF Export"), folders(planned));
        for (String folder : folders(planned)) {
            assertEquals(entriesOf(polarionOrder, folder), entriesOf(planned, folder));
        }
    }

    /** A plain case-insensitive comparison puts every accented name after Z. */
    @RepeatedTest(REPETITIONS)
    void ordersAnAccentedFolderNameNextToItsBaseLetter() {
        AdministrationPageExtender zed = entry("zed", "Zed", "About");
        AdministrationPageExtender overview = entry("overview", "Übersicht", "About");
        AdministrationPageExtender tools = entry("tools", "tools", "About");
        AdministrationPageExtender apples = entry("apples", "Äpfel", "About");
        AdministrationPageExtender audit = entry("audit", "Audit Log", "About");
        List<AdministrationPageExtender> declared = List.of(zed, overview, tools, apples, audit);

        List<AdministrationPageExtender> planned = MenuOrderPlanner.plan(declared, new MenuOrderModes(true, true));

        assertEquals(List.of(apples, audit, tools, overview, zed), planned);
    }

    @RepeatedTest(REPETITIONS)
    void appliesBothModesTogether() {
        List<AdministrationPageExtender> declared = declared();

        List<AdministrationPageExtender> planned = MenuOrderPlanner.plan(declared, new MenuOrderModes(true, true));

        assertEquals(List.of(declared.get(5), declared.get(3), declared.get(4), declared.get(0), declared.get(1), declared.get(2)), planned);
    }

    /** Polarion's menu puts an entry that names only the folder id into the folder another entry names. */
    @RepeatedTest(REPETITIONS)
    void placesAnEntryGivingOnlyTheFolderIdInThatFolder() {
        AdministrationPageExtender about = entry("pdf", "PDF Export", "About");
        AdministrationPageExtender restApi = entry("pdf", null, "REST API");
        AdministrationPageExtender aaa = entry("aaa", "AAA", "About");
        List<AdministrationPageExtender> declared = List.of(about, restApi, aaa);

        List<AdministrationPageExtender> planned = MenuOrderPlanner.plan(declared, new MenuOrderModes(true, true));

        assertEquals(List.of(aaa, about, restApi), planned);
    }

    /**
     * Polarion places a folder where the first entry naming it is. An entry that gives only the folder id,
     * such as a REST API page, can come earlier in Polarion's order and must not move the folder.
     */
    @RepeatedTest(REPETITIONS)
    void keepsAFolderWhereItsFirstNamingEntryIsWhenAnEntryGivesOnlyItsId() {
        List<AdministrationPageExtender> declared = List.of(
                entry("pdf", null, "REST API"),
                entry("pdf", "PDF Export", "About"),
                entry("diff", null, "REST API"),
                entry("diff", "Diff Tool", "About"),
                entry("xml", null, "REST API"),
                entry("xml", "XML Repair", "About"));
        List<AdministrationPageExtender> polarionOrder = MenuOrderPlanner.polarionOrder(declared);

        List<AdministrationPageExtender> planned = MenuOrderPlanner.plan(declared, new MenuOrderModes(false, true));

        assertEquals(foldersAsPolarionPlacesThem(polarionOrder), foldersAsPolarionPlacesThem(planned));
    }

    private static List<String> foldersAsPolarionPlacesThem(List<?> entries) {
        return new ArrayList<>(new LinkedHashSet<>(entries.stream()
                .map(AdministrationPageExtender.class::cast)
                .map(AdministrationPageExtender::getParentNodeName)
                .filter(name -> name != null && !name.isEmpty())
                .toList()));
    }

    @RepeatedTest(REPETITIONS)
    void placesAnEntryGivingOnlyTheFolderNameInThatFolder() {
        AdministrationPageExtender about = entry("pdf", "PDF Export", "About");
        AdministrationPageExtender guide = entry(null, "PDF Export", "User Guide");
        AdministrationPageExtender zed = entry("zed", "Zed", "About");
        List<AdministrationPageExtender> declared = List.of(about, zed, guide);

        List<AdministrationPageExtender> planned = MenuOrderPlanner.plan(declared, new MenuOrderModes(true, true));

        assertEquals(List.of(about, guide, zed), planned);
    }

    @RepeatedTest(REPETITIONS)
    void keepsAFolderNamedOnlyByNameTogether() {
        AdministrationPageExtender first = entry(null, "Tools", "First");
        AdministrationPageExtender second = entry(null, "Tools", "Second");
        AdministrationPageExtender other = entry("abc", "Abc", "About");
        List<AdministrationPageExtender> declared = List.of(first, other, second);

        List<AdministrationPageExtender> planned = MenuOrderPlanner.plan(declared, new MenuOrderModes(true, true));

        assertEquals(List.of(other, first, second), planned);
    }

    @RepeatedTest(REPETITIONS)
    void neverChangesTheEntries() {
        List<AdministrationPageExtender> declared = declared();

        for (boolean folders : new boolean[]{false, true}) {
            for (boolean entries : new boolean[]{false, true}) {
                List<AdministrationPageExtender> planned = MenuOrderPlanner.plan(declared, new MenuOrderModes(folders, entries));
                assertEquals(declared.size(), planned.size());
                assertEquals(Set.copyOf(declared), Set.copyOf(planned));
            }
        }
    }

    /**
     * An entry that gives a parent id but no parent name, and no other entry names that id either.
     * Polarion shows the id as the folder label, so the planner sorts by it.
     */
    @Test
    void sortsAFolderThatOnlyHasAnId() {
        AdministrationPageExtender zzz = new AdministrationPageExtender();
        zzz.setId("zzz-entry");
        zzz.setParentNodeId("zzz-folder");
        AdministrationPageExtender aaa = new AdministrationPageExtender();
        aaa.setId("aaa-entry");
        aaa.setParentNodeId("aaa-folder");
        List<AdministrationPageExtender> declared = List.of(zzz, aaa);

        List<AdministrationPageExtender> planned = MenuOrderPlanner.plan(declared, new MenuOrderModes(true, true));

        assertEquals(List.of(aaa, zzz), planned);
    }

    /** An entry with an empty parent id belongs to no folder, so it is placed by its own name. */
    @Test
    void placesAnEntryWithAnEmptyParentIdOnItsOwn() {
        AdministrationPageExtender loose = new AdministrationPageExtender();
        loose.setId("loose");
        loose.setParentNodeId("");
        loose.setName("Loose entry");
        AdministrationPageExtender inFolder = new AdministrationPageExtender();
        inFolder.setId("about");
        inFolder.setParentNodeId("ext");
        inFolder.setParentNodeName("Alpha");
        List<AdministrationPageExtender> declared = List.of(loose, inFolder);

        List<AdministrationPageExtender> planned = MenuOrderPlanner.plan(declared, new MenuOrderModes(true, true));

        assertEquals(List.of(inFolder, loose), planned);
    }
}
