package net.rdv88.redos.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;
import net.rdv88.redos.Redos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModScreenHandlers {
    private static final Logger LOGGER = LoggerFactory.getLogger("redos");

    public static final MenuType<DroneStationScreenHandler> DRONE_STATION_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(Redos.MOD_ID, "drone_station"),
                    new ExtendedScreenHandlerType<>(DroneStationScreenHandler::new, DroneStationScreenHandler.PACKET_CODEC));

    public static void registerScreenHandlers() {
        LOGGER.info("Registering Screen Handlers for " + Redos.MOD_ID);
    }
}