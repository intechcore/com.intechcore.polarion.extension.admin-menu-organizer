package com.intechcore.polarion.extension.admin_menu_organizer.rest;

import ch.sbb.polarion.extension.generic.rest.GenericRestApplication;
import ch.sbb.polarion.extension.generic.rest.controller.info.ExtensionInfoApiController;
import ch.sbb.polarion.extension.generic.rest.controller.settings.NamedSettingsApiController;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import com.intechcore.polarion.extension.admin_menu_organizer.rest.controller.VersionApiController;
import com.intechcore.polarion.extension.admin_menu_organizer.settings.MenuOrderSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AdminMenuOrganizerRestApplication extends GenericRestApplication {

    /** Public generic controllers this extension does not use: its UI reads the same data through the internal ones. */
    private static final Set<Class<?>> UNUSED_GENERIC_API_CONTROLLERS = Set.of(
            ExtensionInfoApiController.class,
            NamedSettingsApiController.class
    );

    public AdminMenuOrganizerRestApplication() {
        NamedSettingsRegistry.INSTANCE.register(List.of(new MenuOrderSettings()));
    }

    @Override
    protected @NotNull Set<Class<?>> getGenericControllerClasses() {
        return super.getGenericControllerClasses().stream()
                .filter(controller -> !UNUSED_GENERIC_API_CONTROLLERS.contains(controller))
                .collect(Collectors.toSet());
    }

    @Override
    protected @NotNull Set<Class<?>> getExtensionControllerClasses() {
        return Set.of(VersionApiController.class);
    }
}
