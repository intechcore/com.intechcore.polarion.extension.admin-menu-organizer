package com.intechcore.polarion.extension.admin_menu_organizer.settings;

import com.intechcore.polarion.extension.admin_menu_organizer.menu.MenuOrderModes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuOrderSettingsModelTest {

    private static MenuOrderSettingsModel model(boolean foldersAlphabetically, boolean entriesInDeclarationOrder) {
        MenuOrderSettingsModel model = new MenuOrderSettingsModel();
        model.setFoldersAlphabetically(foldersAlphabetically);
        model.setEntriesInDeclarationOrder(entriesInDeclarationOrder);
        return model;
    }

    @Test
    void bothModesAreOffByDefault() {
        MenuOrderSettingsModel model = new MenuOrderSettingsModel();

        assertFalse(model.isFoldersAlphabetically());
        assertFalse(model.isEntriesInDeclarationOrder());
        assertTrue(model.modes().polarionsOwnOrder());
    }

    @Test
    void modes_carriesBothFlags() {
        assertEquals(new MenuOrderModes(true, false), model(true, false).modes());
        assertEquals(new MenuOrderModes(false, true), model(false, true).modes());
    }

    /** The stored text is what a revision holds, so a mode has to survive the round trip. */
    @Test
    void survivesSerialization() {
        MenuOrderSettingsModel stored = new MenuOrderSettingsModel();
        stored.setName("Default");
        stored.setFoldersAlphabetically(true);
        stored.setEntriesInDeclarationOrder(true);

        MenuOrderSettingsModel read = new MenuOrderSettingsModel();
        read.deserialize(stored.serialize());

        assertTrue(read.isFoldersAlphabetically());
        assertTrue(read.isEntriesInDeclarationOrder());
    }

    @Test
    void readsAMissingEntryAsOff() {
        MenuOrderSettingsModel read = new MenuOrderSettingsModel();

        read.deserialize(new MenuOrderSettingsModel().serialize());

        assertFalse(read.isFoldersAlphabetically());
        assertFalse(read.isEntriesInDeclarationOrder());
    }

    @Test
    void equalsAndHashCodeFollowBothModes() {
        assertEquals(model(true, true), model(true, true));
        assertEquals(model(true, true).hashCode(), model(true, true).hashCode());
        assertNotEquals(model(true, true).hashCode(), model(false, false).hashCode());
        assertNotEquals(model(true, true), model(true, false));
        assertNotEquals(model(true, true), model(false, true));
        assertNotEquals(model(true, true), null);
        assertNotEquals(model(true, true), "not a model");
    }

    @Test
    void equalsCoversTheInheritedFieldsAndSubclasses() {
        MenuOrderSettingsModel model = model(true, true);
        assertEquals(model, model);

        MenuOrderSettingsModel named = model(true, true);
        named.setName("Default");
        assertNotEquals(model, named);

        // A type that refuses to be compared is not equal, whatever its fields hold.
        MenuOrderSettingsModel refusing = new RefusesComparison();
        refusing.setFoldersAlphabetically(true);
        refusing.setEntriesInDeclarationOrder(true);
        assertNotEquals(model, refusing);
    }

    /** Stands in for a future subclass that does not want to equal its parent type. */
    private static final class RefusesComparison extends MenuOrderSettingsModel {
        @Override
        protected boolean canEqual(Object other) {
            return false;
        }
    }
}
