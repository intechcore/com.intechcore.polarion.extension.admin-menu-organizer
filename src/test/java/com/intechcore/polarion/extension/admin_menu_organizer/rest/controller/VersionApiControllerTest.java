package com.intechcore.polarion.extension.admin_menu_organizer.rest.controller;

import ch.sbb.polarion.extension.generic.util.ExtensionInfo;
import ch.sbb.polarion.extension.generic.rest.model.Version;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class VersionApiControllerTest {

    @Test
    void returnsTheVersionOfTheExtension() {
        Version version = mock(Version.class);
        ExtensionInfo extensionInfo = mock(ExtensionInfo.class);
        when(extensionInfo.getVersion()).thenReturn(version);

        try (MockedStatic<ExtensionInfo> info = mockStatic(ExtensionInfo.class)) {
            info.when(ExtensionInfo::getInstance).thenReturn(extensionInfo);

            assertSame(version, new VersionApiController().getVersion());
        }
    }
}
