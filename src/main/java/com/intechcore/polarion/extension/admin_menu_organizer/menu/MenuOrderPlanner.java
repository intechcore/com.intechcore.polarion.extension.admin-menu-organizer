package com.intechcore.polarion.extension.admin_menu_organizer.menu;

import com.polarion.alm.administration.web.server.AdministrationPageExtender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Computes the order of the administration menu entries from their declared order. It changes nothing itself. */
final class MenuOrderPlanner {

    private MenuOrderPlanner() {
    }

    /**
     * The order Polarion builds at server start. {@code AbstractHiveMindPlatform.lookupSetConfigurationPoint}
     * passes the declared entries through {@code Set.copyOf}, and the provider copies that set into a list.
     * The iteration order of the set depends only on the identity hash codes of the entries and on a salt
     * chosen once per JVM start, so repeating the copy in the same JVM gives the same order.
     */
    static @NotNull List<Object> polarionOrder(@NotNull List<?> declaredOrder) {
        return new ArrayList<>(Set.copyOf(declaredOrder));
    }

    /**
     * Polarion's order with the requested modes applied. A folder moves as a whole: by its name when
     * {@code foldersAlphabetically} is set, otherwise to where its first entry is in Polarion's order. An
     * entry outside any folder is placed the same way, by its own name.
     */
    static @NotNull List<Object> plan(@NotNull List<?> declaredOrder, boolean foldersAlphabetically, boolean entriesInDeclarationOrder) {
        List<Object> polarionOrder = polarionOrder(declaredOrder);
        if (!foldersAlphabetically && !entriesInDeclarationOrder) {
            return polarionOrder;
        }

        Folders folders = new Folders(polarionOrder);
        Comparator<Object> order;
        if (foldersAlphabetically) {
            order = Comparator.<Object, String>comparing(folders::label, String.CASE_INSENSITIVE_ORDER).thenComparingInt(folders::firstIndex);
        } else {
            order = Comparator.comparingInt(folders::firstIndex);
        }
        Map<Object, Integer> entryIndexes = indexes(entriesInDeclarationOrder ? declaredOrder : polarionOrder);

        List<Object> planned = new ArrayList<>(polarionOrder);
        planned.sort(order.thenComparingInt(entryIndexes::get));
        return planned;
    }

    private static @NotNull Map<Object, Integer> indexes(@NotNull List<?> entries) {
        Map<Object, Integer> result = new IdentityHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            result.putIfAbsent(entries.get(i), i);
        }
        return result;
    }

    /**
     * Groups the entries the way Polarion's menu does: a folder is identified by its name, and an entry
     * that gives only the folder id joins the folder that another entry with the same id names. Polarion
     * places a folder where the first entry that names it is, so an entry giving only the id does not move
     * the folder.
     */
    private static final class Folders {
        private final Map<Object, String> folderOfEntry = new IdentityHashMap<>();
        private final Map<String, Integer> firstIndexes = new HashMap<>();
        private final Map<String, String> labels = new HashMap<>();

        Folders(@NotNull List<Object> entries) {
            Map<String, String> namesById = new HashMap<>();
            for (Object entry : entries) {
                AdministrationPageExtender extender = (AdministrationPageExtender) entry;
                if (isSet(extender.getParentNodeId()) && isSet(extender.getParentNodeName())) {
                    namesById.putIfAbsent(extender.getParentNodeId(), extender.getParentNodeName());
                }
            }

            for (int i = 0; i < entries.size(); i++) {
                AdministrationPageExtender extender = (AdministrationPageExtender) entries.get(i);
                String folderName = isSet(extender.getParentNodeName()) ? extender.getParentNodeName() : namesById.get(extender.getParentNodeId());
                String folder;
                String label;
                if (folderName != null) {
                    folder = "name:" + folderName;
                    label = folderName;
                } else if (isSet(extender.getParentNodeId())) {
                    folder = "id:" + extender.getParentNodeId();
                    label = extender.getParentNodeId();
                } else {
                    folder = "entry:" + i;
                    label = Objects.requireNonNullElse(extender.getName(), "");
                }
                folderOfEntry.put(extender, folder);
                labels.putIfAbsent(folder, label);
                if (isSet(extender.getParentNodeName())) {
                    firstIndexes.putIfAbsent(folder, i);
                }
            }
            for (int i = 0; i < entries.size(); i++) {
                firstIndexes.putIfAbsent(folderOfEntry.get(entries.get(i)), i);
            }
        }

        int firstIndex(@NotNull Object entry) {
            return firstIndexes.get(folderOfEntry.get(entry));
        }

        @NotNull String label(@NotNull Object entry) {
            return labels.get(folderOfEntry.get(entry));
        }

        private static boolean isSet(String value) {
            return value != null && !value.isEmpty();
        }
    }
}
