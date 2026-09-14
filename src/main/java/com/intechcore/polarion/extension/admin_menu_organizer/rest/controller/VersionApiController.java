package com.intechcore.polarion.extension.admin_menu_organizer.rest.controller;

import ch.sbb.polarion.extension.generic.util.ExtensionInfo;
import ch.sbb.polarion.extension.generic.rest.filter.Secured;
import ch.sbb.polarion.extension.generic.rest.model.Version;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * The only generic endpoint this extension keeps in its public REST API: the About page tests a token
 * against it. The UI reads everything else through the internal endpoints of its session.
 */
@Singleton
@Secured
@Tag(name = "Extension Information")
@Path("/api")
public class VersionApiController {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/version")
    @Operation(
            summary = "Returns version of Polarion's extension",
            responses = {
                    @ApiResponse(description = "Version information",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = Version.class))
                    )
            }
    )
    public Version getVersion() {
        return ExtensionInfo.getInstance().getVersion();
    }
}
