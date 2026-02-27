package net.rdv88.redos.client.gui.screen.handheld;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import java.util.Optional;

public interface HandheldApp {
    interface WidgetAdder {
        <T extends GuiEventListener & Renderable & NarratableEntry> T add(T widget);
    }

    void init(int screenX, int screenY, int width, int height, WidgetAdder adder);
    default void preRender(int mouseX, int mouseY, float delta, int screenX, int screenY, int width, int height) {}
    void render(GuiGraphics g, int mouseX, int mouseY, float delta, int screenX, int screenY, int width, int height);
    void tick();
    
    boolean keyPressed(net.minecraft.client.input.KeyEvent event);
    boolean mouseClicked(double mouseX, double mouseY, int button, int sx, int sy, int w, int h);
    default boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY, int sx, int sy, int w, int h) { return false; }
    default boolean mouseReleased(double mouseX, double mouseY, int button, int sx, int sy, int w, int h) { return false; }
    boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, int sx, int sy, int w, int h);
    
    default boolean isEditMode() { return false; }
    default Optional<GuiEventListener> getInitialFocus() { return Optional.empty(); }
}