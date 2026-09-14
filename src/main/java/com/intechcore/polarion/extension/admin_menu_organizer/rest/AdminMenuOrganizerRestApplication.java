package com.intechcore.polarion.extension.admin_menu_organizer.rest;

import ch.sbb.polarion.extension.generic.rest.GenericRestApplication;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import com.intechcore.polarion.extension.admin_menu_organizer.settings.MenuOrderSettings;

import java.util.List;

public class AdminMenuOrganizerRestApplication extends GenericRestApplication {

    public AdminMenuOrganizerRestApplication() {
        NamedSettingsRegistry.INSTANCE.register(List.of(new MenuOrderSettings()));
    }
}
