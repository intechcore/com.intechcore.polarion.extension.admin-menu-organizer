package com.intechcore.polarion.extension.admin_menu_organizer.settings;

import ch.sbb.polarion.extension.generic.settings.SettingsModel;
import com.intechcore.polarion.extension.admin_menu_organizer.menu.MenuOrderModes;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The two ordering modes of the administration menu. Both are off by default, which changes nothing. */
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@NoArgsConstructor
public class MenuOrderSettingsModel extends SettingsModel {

    private static final String FOLDERS_ALPHABETICALLY = "FOLDERS ALPHABETICALLY";
    private static final String ENTRIES_IN_DECLARATION_ORDER = "ENTRIES IN DECLARATION ORDER";

    private boolean foldersAlphabetically;
    private boolean entriesInDeclarationOrder;

    /** The stored flags as the menu package takes them. Not a bean getter, so it is not serialized. */
    public MenuOrderModes modes() {
        return new MenuOrderModes(foldersAlphabetically, entriesInDeclarationOrder);
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
