package com.intechcore.polarion.extension.admin_menu_organizer.menu;

/**
 * The two ordering modes of the administration menu. Both off means Polarion's own order, which is what
 * a server that never configured this extension gets.
 *
 * @param foldersAlphabetically    sort the folders by name instead of leaving them where Polarion put them
 * @param entriesInDeclarationOrder order the entries inside a folder as their extensions declare them
 */
public record MenuOrderModes(boolean foldersAlphabetically, boolean entriesInDeclarationOrder) {

    /** Whether the modes ask for Polarion's own order, which means there is nothing to reorder. */
    public boolean polarionsOwnOrder() {
        return !foldersAlphabetically && !entriesInDeclarationOrder;
    }
}
