package net.rdv88.redos.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class PermissionCache {
    private static boolean hasMainAccess = false;
    private static boolean hasHighTechAccess = false;
    private static boolean isAdmin = false;

    public static void update(boolean main, boolean highTech, boolean admin) {
        hasMainAccess = main;
        hasHighTechAccess = highTech;
        isAdmin = admin;
    }

    public static boolean hasMainAccess() { return hasMainAccess; }
    public static boolean hasHighTechAccess() { return hasHighTechAccess; }
    public static boolean isAdmin() { return isAdmin; }
    
    public static void reset() {
        hasMainAccess = false;
        hasHighTechAccess = false;
        isAdmin = false;
    }
}
