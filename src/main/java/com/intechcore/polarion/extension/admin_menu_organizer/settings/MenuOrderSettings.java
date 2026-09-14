package com.intechcore.polarion.extension.admin_menu_organizer.settings;

import ch.sbb.polarion.extension.generic.settings.GenericNamedSettings;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.settings.SettingsService;
import com.intechcore.polarion.extension.admin_menu_organizer.menu.MenuOrderRestorer;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.security.ISecurityService;
import org.jetbrains.annotations.NotNull;

import java.security.PrivilegedAction;

/**
 * Stores the ordering modes in the global scope and applies them as soon as they are saved. The menu is
 * one list for the whole server, so a project scope has nothing to configure.
 */
@SuppressWarnings("java:S2160")
public class MenuOrderSettings extends GenericNamedSettings<MenuOrderSettingsModel> {

    public static final String FEATURE_NAME = "menu-order";

    public MenuOrderSettings() {
        super(FEATURE_NAME);
    }

    public MenuOrderSettings(@NotNull SettingsService settingsService) {
        super(FEATURE_NAME, settingsService);
    }

    @Override
    public @NotNull MenuOrderSettingsModel defaultValues() {
        return new MenuOrderSettingsModel();
    }

    @Override
    public @NotNull MenuOrderSettingsModel save(@NotNull String scope, @NotNull SettingId id, @NotNull MenuOrderSettingsModel what) {
        if (!DEFAULT_SCOPE.equals(scope)) {
            throw new IllegalArgumentException("The administration menu order can only be set in the global scope");
        }
        // Server start reads only the Default setting: any other name would apply now and be lost at the next restart.
        if (!DEFAULT_NAME.equals(what.getName())) {
            throw new IllegalArgumentException("The administration menu order can only be stored in the Default setting");
        }
        return super.save(scope, id, what);
    }

    @Override
    public void afterSave(@NotNull MenuOrderSettingsModel what) {
        MenuOrderRestorer.apply(what.isFoldersAlphabetically(), what.isEntriesInDeclarationOrder());
    }

    /**
     * Applies what the Default setting holds once the delete is done, because generic has no hook after a
     * delete. A deleted Default reads as the defaults, which return the menu to Polarion's own order.
     */
    @Override
    public void delete(@NotNull String scope, @NotNull SettingId id) {
        super.delete(scope, id);
        MenuOrderSettingsModel stored = read(DEFAULT_SCOPE, SettingId.fromName(DEFAULT_NAME), null);
        MenuOrderRestorer.apply(stored.isFoldersAlphabetically(), stored.isEntriesInDeclarationOrder());
    }

    /** Reads the stored modes as the system user: at server start there is no user session to read them with. */
    public @NotNull MenuOrderSettingsModel readAsSystemUser() {
        return PlatformContext.getPlatform().lookupService(ISecurityService.class).doAsSystemUser(
                (PrivilegedAction<MenuOrderSettingsModel>) () -> read(DEFAULT_SCOPE, SettingId.fromName(DEFAULT_NAME), null));
    }
}
