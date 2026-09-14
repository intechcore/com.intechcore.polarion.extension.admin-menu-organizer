package com.intechcore.polarion.extension.admin_menu_organizer.settings;

import ch.sbb.polarion.extension.generic.settings.SettingsModel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

/** The two ordering modes of the administration menu. */
@Getter
@Setter
@NoArgsConstructor
public class MenuOrderSettingsModel extends SettingsModel {

    private static final String FOLDERS_ALPHABETICALLY = "FOLDERS ALPHABETICALLY";
    private static final String ENTRIES_IN_DECLARATION_ORDER = "ENTRIES IN DECLARATION ORDER";

    private boolean foldersAlphabetically;
    private boolean entriesInDeclarationOrder;

    /** The modes of a server with no stored setting: pages in declaration order, sections where Polarion put them. */
    public static @NotNull MenuOrderSettingsModel defaults() {
        MenuOrderSettingsModel defaults = new MenuOrderSettingsModel();
        defaults.setEntriesInDeclarationOrder(true);
        return defaults;
    }

    @Override
    protected String serializeModelData() {
        return serializeEntry(FOLDERS_ALPHABETICALLY, String.valueOf(foldersAlphabetically))
                + serializeEntry(ENTRIES_IN_DECLARATION_ORDER, String.valueOf(entriesInDeclarationOrder));
    }

    @Override
    protected void deserializeModelData(String serializedString) {
        foldersAlphabetically = Boolean.parseBoolean(deserializeEntry(FOLDERS_ALPHABETICALLY, serializedString));
        entriesInDeclarationOrder = Boolean.parseBoolean(deserializeEntry(ENTRIES_IN_DECLARATION_ORDER, serializedString));
    }
}
