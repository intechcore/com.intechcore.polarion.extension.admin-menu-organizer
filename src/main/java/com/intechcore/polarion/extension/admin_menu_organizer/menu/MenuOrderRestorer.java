package com.intechcore.polarion.extension.admin_menu_organizer.menu;

import com.polarion.alm.administration.web.server.AdministrationPageExtender;
import com.polarion.alm.administration.web.server.AdministrationPageExtenderProvider;
import com.polarion.core.util.logging.Logger;
import com.polarion.platform.core.IPlatform;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.guice.ipi.GuicePlatform;
import com.polarion.platform.hivemind.IHiveMindPlatform;
import jakarta.inject.Inject;
import org.apache.hivemind.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orders the administration menu entries contributed through
 * {@code com.polarion.xray.webui.administrationPageExtenders}.
 * <p>
 * Up to Polarion 2512 {@code AdministrationPageExtenderProvider} received the HiveMind configuration
 * as a {@code List}, so the menu followed the order of {@code hivemodule.xml}. Polarion 2606 routes the
 * same configuration through {@code Set.copyOf} in
 * {@code AbstractHiveMindPlatform.lookupSetConfigurationPoint} and injects it as a {@code Set}. That is
 * a JDK immutable set whose iteration order follows element hash codes salted once per JVM start, and
 * {@code AdministrationPageExtender} does not override {@code hashCode}. Every menu entry of every
 * extension therefore lands in a different place after each restart.
 * <p>
 * The HiveMind registry still holds the configuration in declaration order. This class reads it, lets
 * {@link MenuOrderPlanner} compute the order, and hands the result back through
 * {@link AdministrationPageExtenderProvider#setAdministrationPageExtenders(Set)}, the setter Polarion
 * injects through. The setter copies its argument into a list of its own, so a {@link LinkedHashSet} in
 * the planned order becomes the menu order. Nothing is added and nothing is removed:
 * {@link MenuOrderPlanner} only permutes the entries it is given.
 * <p>
 * Every member called here is a public one: {@link IHiveMindPlatform#getRegistry()},
 * {@link Registry#getConfiguration(String)} and that setter. Public is the access modifier, not a
 * promise. None of {@code AdministrationPageExtenderProvider}, {@code IHiveMindPlatform},
 * {@code GuicePlatform} or the HiveMind {@code Registry} appears in Polarion's Open Java API or in its
 * Rendering API, the two sets the SDK publishes, and the administration menu has no documented
 * extension point at all. Calling them from source rather than through reflection buys a compile-time
 * check: a signature that changes in a future Polarion breaks the build here rather than at runtime on
 * a customer's server. It costs two entries in {@code Require-Bundle}
 * ({@code com.polarion.platform.hivemind} and {@code org.apache.hivemind}). Anything unexpected is
 * logged and leaves Polarion's own order in place.
 * <p>
 * The entries written are the registry's, which is the configuration Polarion itself copies into the
 * provider at start. A future Polarion that fills the provider from somewhere else would have its
 * entries replaced by the registry's. That is the price of dropping the reflective read of the
 * provider's own list, which has no public equivalent.
 * <p>
 * The provider must come from Guice, not from {@code PlatformContext.lookupService}, which answers with
 * the generated HiveMind singleton proxy. Member injection yields the real singleton, which is also the
 * object that builds the navigation tree.
 */
public final class MenuOrderRestorer {

    static final String CONFIG_ID = "com.polarion.xray.webui.administrationPageExtenders";

    private static final Logger logger = Logger.getLogger(MenuOrderRestorer.class);

    private MenuOrderRestorer() {
    }

    /**
     * Applies the order at server start. With both modes off it changes nothing, because Polarion's own
     * order is then what the modes ask for.
     * <p>
     * Requires Polarion's platform to be initialized. A webapp context listener satisfies that:
     * {@code PlatformService.start()} builds the Guice injector before Tomcat is started.
     */
    public static void applyAtStartup(@NotNull MenuOrderModes modes) {
        run(Trigger.STARTUP, modes);
    }

    /**
     * Applies the order after the modes change, including back to Polarion's own order when both are
     * off. The caller answers a request, so it is told what happened instead of only the log.
     *
     * @return whether the menu now holds the order the modes ask for
     */
    public static boolean apply(@NotNull MenuOrderModes modes) {
        return run(Trigger.MODES_CHANGED, modes);
    }

    /** Why the order is being applied. At server start an unconfigured menu is left untouched. */
    enum Trigger {
        STARTUP,
        MODES_CHANGED
    }

    private static boolean run(@NotNull Trigger trigger, @NotNull MenuOrderModes modes) {
        try {
            AdministrationPageExtenderProvider provider = lookupProvider();
            if (provider == null) {
                logger.warn("Administration menu order was not applied: the page extender provider was not injected");
                return false;
            }
            return apply(provider, PlatformContext.getPlatform(), trigger, modes);
        } catch (Exception | LinkageError e) {
            // Every Polarion type named here can move in a future release, and a failure must never take
            // the webapp start or the request down with it.
            logger.warn("Administration menu order was not applied, Polarion's own order is kept", e);
            return false;
        }
    }

    static boolean apply(@NotNull AdministrationPageExtenderProvider provider, @NotNull IPlatform platform,
                         @NotNull Trigger trigger, @NotNull MenuOrderModes modes) {
        // A server that never configured this extension keeps the order Polarion built, rather than the
        // one this extension computes for it. That order is what the modes ask for, so nothing is wrong.
        if (trigger == Trigger.STARTUP && modes.polarionsOwnOrder()) {
            return true;
        }
        List<AdministrationPageExtender> declaredOrder = readDeclaredOrder(platform);
        if (declaredOrder == null) {
            logger.warn("Administration menu order was not applied: the '%s' configuration is not readable".formatted(CONFIG_ID));
            return false;
        }

        List<AdministrationPageExtender> plannedOrder = MenuOrderPlanner.plan(declaredOrder, modes);
        // A LinkedHashSet holds the planned order, and the setter copies it into the list the provider
        // reads from. The menu is replaced in one write, so a reader building the navigation tree in
        // parallel sees either the previous order or the new one, never a mixture of the two. No lock
        // is taken: this is a single reference write, and Polarion's readers take none either.
        Set<AdministrationPageExtender> orderedEntries = new LinkedHashSet<>(plannedOrder);
        provider.setAdministrationPageExtenders(orderedEntries);

        logger.info("Ordered %d administration menu entries (folders alphabetically: %s, entries in declaration order: %s)"
                .formatted(orderedEntries.size(), modes.foldersAlphabetically(), modes.entriesInDeclarationOrder()));
        logger.debug(() -> "Administration menu order: %s".formatted(describe(plannedOrder)));
        return true;
    }

    /**
     * Renders the applied order for the log, so the result is verifiable on a running server without a
     * debugger. Entries are named {@code parentNodeId/id}.
     */
    static @NotNull String describe(@NotNull List<AdministrationPageExtender> entries) {
        return entries.stream()
                .map(extender -> "%s/%s".formatted(extender.getParentNodeId(), extender.getId()))
                .collect(Collectors.joining(", "));
    }

    /**
     * Obtains the real provider singleton by member injection. {@code UIModule} binds the concrete class
     * as a Guice singleton, so this is the instance Polarion's own navigation code reaches through its
     * HiveMind proxy.
     */
    static @Nullable AdministrationPageExtenderProvider lookupProvider() {
        ProviderProbe probe = new ProviderProbe();
        GuicePlatform.tryInjectMembers(probe);
        return probe.provider;
    }

    /**
     * Reads the configuration straight from the HiveMind registry, the only place that still holds the
     * entries in declaration order. A configuration holding anything but extenders is not used. The
     * result is a copy, so the registry's own list is never handed to a caller.
     */
    static @Nullable List<AdministrationPageExtender> readDeclaredOrder(@NotNull IPlatform platform) {
        if (!(platform instanceof IHiveMindPlatform hiveMindPlatform)) {
            // The caller warns once that the configuration is not readable. This detail says why.
            logger.debug(() -> "%s does not carry a HiveMind registry".formatted(platform.getClass().getName()));
            return null;
        }
        Registry registry = hiveMindPlatform.getRegistry();
        if (registry == null) {
            return null;
        }
        List<?> configuration = registry.getConfiguration(CONFIG_ID);
        if (configuration == null || configuration.isEmpty()
                || !configuration.stream().allMatch(AdministrationPageExtender.class::isInstance)) {
            return null;
        }
        // Every element was checked above, so no cast can fail here.
        return configuration.stream().map(AdministrationPageExtender.class::cast).toList();
    }

    /** Member-injection target used only to obtain the provider singleton, see {@link #lookupProvider()}. */
    static final class ProviderProbe {
        // Field injection is required: Guice populates this via tryInjectMembers() on an
        // already-constructed probe, which keeps this class free of Guice types.
        @SuppressWarnings("java:S6813")
        @Inject
        AdministrationPageExtenderProvider provider;
    }
}
