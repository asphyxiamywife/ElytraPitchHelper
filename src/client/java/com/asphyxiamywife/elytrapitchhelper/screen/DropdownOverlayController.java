package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.screen.widget.DropdownButton;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;

final class DropdownOverlayController {
    private final List<DropdownButton<?>> dropdowns = new ArrayList<>();

    void clear() {
        dropdowns.clear();
    }

    void register(DropdownButton<?> dropdown) {
        dropdowns.add(dropdown);
        dropdown.setOnOpen(() -> closeExcept(dropdown));
    }

    void extractOverlays(GuiGraphicsExtractor context, Font font, int mouseX, int mouseY) {
        if (!hasOpenDropdown()) {
            return;
        }

        context.nextStratum();
        for (DropdownButton<?> dropdown : dropdowns) {
            dropdown.extractDropdownOverlay(context, font, mouseX, mouseY);
        }
    }

    boolean handleMouseClicked(MouseButtonEvent event, int screenHeight) {
        for (int i = dropdowns.size() - 1; i >= 0; i--) {
            DropdownButton<?> dropdown = dropdowns.get(i);
            if (dropdown.isOpen()) {
                return dropdown.handleOpenMouseClicked(event, screenHeight);
            }
        }
        return false;
    }

    boolean handleMouseScrolled(double mouseX, double mouseY, double scrollY, int screenHeight) {
        for (int i = dropdowns.size() - 1; i >= 0; i--) {
            DropdownButton<?> dropdown = dropdowns.get(i);
            if (dropdown.isOpen()) {
                return dropdown.handleOpenMouseScrolled(mouseX, mouseY, scrollY, screenHeight);
            }
        }
        return false;
    }

    boolean handleKeyPressed(KeyEvent event) {
        for (int i = dropdowns.size() - 1; i >= 0; i--) {
            DropdownButton<?> dropdown = dropdowns.get(i);
            if (dropdown.handleOpenKeyPressed(event)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasOpenDropdown() {
        for (DropdownButton<?> dropdown : dropdowns) {
            if (dropdown.isOpen()) {
                return true;
            }
        }
        return false;
    }

    private void closeExcept(DropdownButton<?> openDropdown) {
        for (DropdownButton<?> dropdown : dropdowns) {
            if (dropdown != openDropdown) {
                dropdown.close();
            }
        }
    }
}
