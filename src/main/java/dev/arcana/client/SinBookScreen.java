package dev.arcana.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** The first bound volume of the Books of Sin; its trial content is added in later design passes. */
public final class SinBookScreen extends Screen {
    public SinBookScreen() { super(Text.literal("Book of Sin")); }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int left = (width - 300) / 2;
        int top = (height - 180) / 2;
        context.fill(0, 0, width, height, 0x9909000D);
        context.fill(left, top, left + 300, top + 180, 0xFF2A1724);
        context.fill(left + 5, top + 5, left + 295, top + 175, 0xFF3D2635);
        context.drawCenteredTextWithShadow(textRenderer, "THE BOOKS OF SIN", width / 2, top + 24, 0xFFE8C7A0);
        context.drawCenteredTextWithShadow(textRenderer, "This volume is sealed.", width / 2, top + 70, 0xFFF0DDB7);
        context.drawCenteredTextWithShadow(textRenderer, "Its power and trial have not yet been revealed.", width / 2, top + 88, 0xFFCBB7A0);
        context.drawCenteredTextWithShadow(textRenderer, "Close the book to return.", width / 2, top + 132, 0xFFCBB7A0);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return false; }
}
