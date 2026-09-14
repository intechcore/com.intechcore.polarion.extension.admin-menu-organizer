package com.intechcore.polarion.extension.admin_menu_organizer.menu;

import com.polarion.alm.administration.web.server.AdministrationPageExtender;
import com.polarion.alm.administration.web.server.AdministrationPageExtenderProvider;
import com.polarion.core.util.logging.Logger;
import com.polarion.platform.core.IPlatform;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.guice.ipi.GuicePlatform;
import jakarta.inject.Inject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
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
 * The HiveMind registry still holds the configuration in declaration order, and the provider keeps its
 * entries in a mutable list. This class reads both, lets {@link MenuOrderPlanner} compute the order, and
 * writes it into that list as a permutation of the entries Polarion already holds.
 * <p>
 * The registry is reached through two public methods ({@code IHiveMindPlatform.getRegistry()} and
 * {@code Registry.getConfiguration(String)}), reflectively so that the {@code org.apache.hivemind} types
 * are never named at compile time and stay out of {@code Require-Bundle}. The provider's list is read
 * from a private field, which has no public equivalent. Anything unexpected is logged and leaves
 * Polarion's own order in place.
 * <p>
 * The provider must come from Guice, not from {@code PlatformContext.lookupService}. Its HiveMind
 * service point uses the default singleton service model, whose {@code getService()} returns a generated
 * proxy: the proxy carries no field to read, so the reflective read finds nothing. Member injection
 * yields the real singleton the proxy delegates to, which is also the object that builds the navigation
 * tree.
 */
public final class MenuOrderRestorer {

    static final String CONFIG_ID = "com.polarion.xray.webui.administrationPageExtenders";
    private static final String EXTENDERS_FIELD = "extenders";
    private static final String GET_REGISTRY_METHOD = "getRegistry";
    private static final String GET_CONFIGURATION_METHOD = "getConfiguration";

    private static final Logger logger = Logger.getLogger(MenuOrderRestorer.class);

    private MenuOrderRestorer() {
    }

    /**
     * Applies the order at server start. It first checks that {@link MenuOrderPlanner#polarionOrder(List)}
     * reproduces the order Polarion built, because switching the modes off later relies on it. With both
     * modes off it then changes nothing.
     * <p>
     * Requires Polarion's platform to be initialized. A webapp context listener satisfies that:
     * {@code PlatformService.start()} builds the Guice injector before Tomcat is started.
     */
    public static void applyAtStartup(boolean foldersAlphabetically, boolean entriesInDeclarationOrder) {
        run(true, foldersAlphabetically, entriesInDeclarationOrder);
    }

    /** Applies the order after the modes change, including back to Polarion's own order when both are off. */
    public static void apply(boolean foldersAlphabetically, boolean entriesInDeclarationOrder) {
        run(false, foldersAlphabetically, entriesInDeclarationOrder);
    }

    private static void run(boolean atStartup, boolean foldersAlphabetically, boolean entriesInDeclarationOrder) {
        try {
            AdministrationPageExtenderProvider provider = lookupProvider();
            if (provider == null) {
                logger.warn("Administration menu order was not applied: the page extender provider was not injected");
                return;
            }
            apply(provider, PlatformContext.getPlatform(), atStartup, foldersAlphabetically, entriesInDeclarationOrder);
        } catch (Exception | LinkageError e) {
            // Every Polarion type named here can move in a future release, and a failure must never take
            // the webapp start or the request down with it.
            logger.warn("Administration menu order was not applied, Polarion's own order is kept", e);
        }
    }

    static void apply(@NotNull AdministrationPageExtenderProvider provider, @NotNull IPlatform platform,
                      boolean atStartup, boolean foldersAlphabetically, boolean entriesInDeclarationOrder) throws ReflectiveOperationException {
        List<AdministrationPageExtender> liveOrder = readLiveOrder(provider);
        if (liveOrder == null) {
            logger.warn("Administration menu order was not applied: %s has no readable '%s' list"
                    .formatted(provider.getClass().getName(), EXTENDERS_FIELD));
            return;
        }
        List<AdministrationPageExtender> declaredOrder = readDeclaredOrder(platform);
        if (declaredOrder == null) {
            logger.warn("Administration menu order was not applied: the '%s' configuration is not readable".formatted(CONFIG_ID));
            return;
        }

        Outcome outcome;
        // The lock covers the already-ordered check too, so of two concurrent calls one does the work and
        // the other finds it done. Polarion's readers never take this monitor, see restore(List, List).
        synchronized (liveOrder) {
            if (atStartup) {
                checkPolarionOrder(liveOrder, declaredOrder);
                if (!foldersAlphabetically && !entriesInDeclarationOrder) {
                    return;
                }
            }
            outcome = restore(liveOrder, MenuOrderPlanner.plan(declaredOrder, foldersAlphabetically, entriesInDeclarationOrder));
        }
        switch (outcome) {
            case APPLIED -> {
                logger.info("Ordered %d administration menu entries (folders alphabetically: %s, entries in declaration order: %s)"
                        .formatted(liveOrder.size(), foldersAlphabetically, entriesInDeclarationOrder));
                logger.debug(() -> "Administration menu order: %s".formatted(describe(liveOrder)));
            }
            case ALREADY_ORDERED -> logger.debug(() -> "Administration menu already holds its %d entries in the requested order"
                    .formatted(liveOrder.size()));
            case MISMATCH -> logger.warn("Administration menu order was not applied: Polarion's menu entries do not match its own configuration");
        }
    }

    private static void checkPolarionOrder(@NotNull List<AdministrationPageExtender> liveOrder, @NotNull List<AdministrationPageExtender> declaredOrder) {
        List<AdministrationPageExtender> polarionOrder = MenuOrderPlanner.polarionOrder(declaredOrder);
        if (liveOrder.size() == polarionOrder.size() && alreadyOrdered(liveOrder, polarionOrder)) {
            logger.debug("Polarion's own administration menu order is reproducible");
        } else {
            logger.warn("Polarion's own administration menu order cannot be reproduced: switching the ordering modes off will not return to it exactly");
        }
    }

    /**
     * Copies {@code targetOrder} into {@code liveOrder} position by position.
     * <p>
     * Only a permutation is applied, and only when both lists hold exactly the same objects. The size
     * never changes and no position is ever empty, so a reader building the navigation tree in parallel
     * cannot observe a null entry. It can, while the loop runs, briefly see one entry twice and another
     * not at all: that reader renders one menu with a duplicated entry, and the next render is correct.
     * Rejecting anything but a permutation keeps a future Polarion, which may fill the provider from
     * somewhere else, from having its entries replaced.
     *
     * @param liveOrder   the provider's own list, modified in place
     * @param targetOrder the same entries in the requested order
     * @param <T>         the type of the entries
     * @return what the call did, see {@link Outcome}
     */
    static <T> @NotNull Outcome restore(@NotNull List<T> liveOrder, @NotNull List<? extends T> targetOrder) {
        if (liveOrder.size() != targetOrder.size() || !holdSameEntries(liveOrder, targetOrder)) {
            return Outcome.MISMATCH;
        }
        if (alreadyOrdered(liveOrder, targetOrder)) {
            return Outcome.ALREADY_ORDERED;
        }
        for (int i = 0; i < targetOrder.size(); i++) {
            liveOrder.set(i, targetOrder.get(i));
        }
        return Outcome.APPLIED;
    }

    /** What a {@link #restore(List, List)} call did. */
    enum Outcome {
        /** The order was applied. */
        APPLIED,
        /** Nothing to do: the entries already sat in the requested order. */
        ALREADY_ORDERED,
        /** The two lists do not hold the same entries, so nothing was touched. */
        MISMATCH
    }

    private static boolean alreadyOrdered(@NotNull List<?> liveOrder, @NotNull List<?> targetOrder) {
        for (int i = 0; i < targetOrder.size(); i++) {
            if (liveOrder.get(i) != targetOrder.get(i)) {
                return false;
            }
        }
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
     * Compares by identity rather than by {@code equals}: {@code AdministrationPageExtender} does not
     * implement {@code equals}, and a repeated instance must count as a mismatch.
     */
    private static boolean holdSameEntries(@NotNull List<?> first, @NotNull List<?> second) {
        Set<Object> firstEntries = identitySet(first);
        // The size check also rejects a repeated instance: a set collapses repeats, so equal sets over
        // equal-sized lists would otherwise admit one entry being dropped and another written twice.
        return firstEntries.size() == first.size() && firstEntries.equals(identitySet(second));
    }

    private static @NotNull Set<Object> identitySet(@NotNull List<?> entries) {
        Set<Object> result = Collections.newSetFromMap(new IdentityHashMap<>());
        result.addAll(entries);
        return result;
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
     * Reads the provider's own list of entries. Walks the class hierarchy so a future subclass of the
     * provider is still handled.
     */
    @SuppressWarnings({"unchecked", "java:S3011"})
    static @Nullable List<AdministrationPageExtender> readLiveOrder(@NotNull AdministrationPageExtenderProvider provider) throws ReflectiveOperationException {
        for (Class<?> type = provider.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(EXTENDERS_FIELD);
                field.setAccessible(true);
                Object value = field.get(provider);
                // The field is declared as a list of extenders, and restore() writes into it only entries
                // that readDeclaredOrder() has checked.
                return value instanceof List ? (List<AdministrationPageExtender>) value : null;
            } catch (NoSuchFieldException e) {
                // Keep looking further up the hierarchy.
            }
        }
        return null;
    }

    /**
     * Reads the configuration straight from the HiveMind registry, the only place that still holds the
     * entries in declaration order. A configuration holding anything but extenders is not used.
     */
    @SuppressWarnings("unchecked")
    static @Nullable List<AdministrationPageExtender> readDeclaredOrder(@NotNull IPlatform platform) throws ReflectiveOperationException {
        Method getRegistry = findMethod(platform.getClass(), GET_REGISTRY_METHOD);
        if (getRegistry == null) {
            return null;
        }
        Object registry = getRegistry.invoke(platform);
        if (registry == null) {
            return null;
        }
        Method getConfiguration = findMethod(registry.getClass(), GET_CONFIGURATION_METHOD, String.class);
        if (getConfiguration == null) {
            return null;
        }
        Object configuration = getConfiguration.invoke(registry, CONFIG_ID);
        if (!(configuration instanceof List<?> list) || list.isEmpty()
                || !list.stream().allMatch(AdministrationPageExtender.class::isInstance)) {
            return null;
        }
        // Every element was checked above, so the cast is safe.
        return (List<AdministrationPageExtender>) list;
    }

    static @Nullable Method findMethod(@NotNull Class<?> type, @NotNull String name, @NotNull Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            logger.warn("Administration menu order was not applied: %s has no %s method".formatted(type.getName(), name));
            return null;
        }
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
