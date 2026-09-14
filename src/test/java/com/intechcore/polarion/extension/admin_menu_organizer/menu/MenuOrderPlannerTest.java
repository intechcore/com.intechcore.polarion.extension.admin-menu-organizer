package com.intechcore.polarion.extension.admin_menu_organizer.menu;

import com.polarion.alm.administration.web.server.AdministrationPageExtender;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuOrderPlannerTest {

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

    @Test
    void keepsPolarionsOrderWithBothModesOff() {
        List<AdministrationPageExtender> declared = declared();

        assertEquals(MenuOrderPlanner.polarionOrder(declared), MenuOrderPlanner.plan(declared, false, false));
    }

    @Test
    void polarionOrderHoldsTheSameEntriesAsDeclared() {
        List<AdministrationPageExtender> declared = declared();

        assertEquals(Set.copyOf(declared), Set.copyOf(MenuOrderPlanner.polarionOrder(declared)));
    }

    @Test
    void ordersEntriesByDeclarationAndKeepsTheFoldersWherePolarionPutThem() {
        List<AdministrationPageExtender> declared = declared();
        List<Object> polarionOrder = MenuOrderPlanner.polarionOrder(declared);

        List<Object> planned = MenuOrderPlanner.plan(declared, false, true);

        assertContiguousFolders(planned);
        assertEquals(folders(polarionOrder), folders(planned));
        for (String folder : folders(planned)) {
            assertEquals(entriesOf(declared, folder), entriesOf(planned, folder));
        }
    }

    @Test
    void ordersFoldersAlphabeticallyAndKeepsTheEntriesWherePolarionPutThem() {
        List<AdministrationPageExtender> declared = declared();
        List<Object> polarionOrder = MenuOrderPlanner.polarionOrder(declared);

        List<Object> planned = MenuOrderPlanner.plan(declared, true, false);

        assertContiguousFolders(planned);
        assertEquals(List.of("Audit Log", "diff tool", "PDF Export"), folders(planned));
        for (String folder : folders(planned)) {
            assertEquals(entriesOf(polarionOrder, folder), entriesOf(planned, folder));
        }
    }

    @Test
    void appliesBothModesTogether() {
        List<AdministrationPageExtender> declared = declared();

        List<Object> planned = MenuOrderPlanner.plan(declared, true, true);

        assertEquals(List.of(declared.get(5), declared.get(3), declared.get(4), declared.get(0), declared.get(1), declared.get(2)), planned);
    }

    /** Polarion's menu puts an entry that names only the folder id into the folder another entry names. */
    @Test
    void placesAnEntryGivingOnlyTheFolderIdInThatFolder() {
        AdministrationPageExtender about = entry("pdf", "PDF Export", "About");
        AdministrationPageExtender restApi = entry("pdf", null, "REST API");
        AdministrationPageExtender aaa = entry("aaa", "AAA", "About");
        List<AdministrationPageExtender> declared = List.of(about, restApi, aaa);

        List<Object> planned = MenuOrderPlanner.plan(declared, true, true);

        assertEquals(List.of(aaa, about, restApi), planned);
    }

    @Test
    void placesAnEntryGivingOnlyTheFolderNameInThatFolder() {
        AdministrationPageExtender about = entry("pdf", "PDF Export", "About");
        AdministrationPageExtender guide = entry(null, "PDF Export", "User Guide");
        AdministrationPageExtender zed = entry("zed", "Zed", "About");
        List<AdministrationPageExtender> declared = List.of(about, zed, guide);

        List<Object> planned = MenuOrderPlanner.plan(declared, true, true);

        assertEquals(List.of(about, guide, zed), planned);
    }

    @Test
    void keepsAFolderNamedOnlyByNameTogether() {
        AdministrationPageExtender first = entry(null, "Tools", "First");
        AdministrationPageExtender second = entry(null, "Tools", "Second");
        AdministrationPageExtender other = entry("abc", "Abc", "About");
        List<AdministrationPageExtender> declared = List.of(first, other, second);

        List<Object> planned = MenuOrderPlanner.plan(declared, true, true);

        assertEquals(List.of(other, first, second), planned);
    }

    @Test
    void neverChangesTheEntries() {
        List<AdministrationPageExtender> declared = declared();

        for (boolean folders : new boolean[]{false, true}) {
            for (boolean entries : new boolean[]{false, true}) {
                List<Object> planned = MenuOrderPlanner.plan(declared, folders, entries);
                assertEquals(declared.size(), planned.size());
                assertEquals(Set.copyOf(declared), Set.copyOf(planned));
            }
        }
    }
}
