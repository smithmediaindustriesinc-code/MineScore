package com.minescore.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minescore.MineScore;
import com.minescore.client.UiTheme;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * The MineScore Manual (SCOPE.md §11): an open book, two pages at a time. The first spread is the
 * introduction and the contents; each chapter starts on a fresh page and flows on as far as it needs.
 * The text is {@code assets/minescore/minescore/manual/<language>.json} (English if the game's language has
 * none), so a resource pack can translate or replace it: a title, an intro, and chapters, each with a
 * title, an item for its icon and paragraphs, where a paragraph starting "# " is a heading.
 */
public class ManualScreen extends Screen {
    private static final ResourceLocation TEXTURE = MineScore.id("textures/gui/manual.png");
    private static final int W = 256, H = 180;
    private static final int PAGE_W = 104, PAGE_TOP = 16, PAGE_H = 142, LINE = 9, GAP = 4;
    private static final int[] PAGE_X = {16, 137};
    private static final int ARROW_W = 18, ARROW_H = 10, ARROW_Y = H - 16, PREV_X = 14, NEXT_X = W - 14 - ARROW_W;
    private static final int CONTENTS_ROW = 15;

    private record Line(FormattedCharSequence text, int y, boolean heading) {}

    /** One page: its lines, and what it opens with: a chapter's title, the book's (-1), the contents (-2) or nothing (-3). */
    private record Page(List<Line> lines, int chapter) {}

    private record Chapter(Component title, ItemStack icon, List<String> text) {}

    private final List<Page> pages = new ArrayList<>();
    private final List<Chapter> chapters = new ArrayList<>();
    private final List<Integer> chapterStart = new ArrayList<>();
    private Component bookTitle = Component.translatable("item.minescore.manual");
    private int spread;
    private int left, top;

    public ManualScreen() {
        super(Component.translatable("item.minescore.manual"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        if (pages.isEmpty()) {
            build(read());
        }
    }

    private JsonObject read() {
        String language = minecraft.getLanguageManager().getSelected();
        for (String code : new String[] {language, "en_us"}) {
            Optional<Resource> resource = minecraft.getResourceManager().getResource(MineScore.id("minescore/manual/" + code + ".json"));
            if (resource.isPresent()) {
                try (Reader reader = resource.get().openAsReader()) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                } catch (Exception e) {
                    MineScore.LOGGER.warn("Couldn't read the manual for {}", code, e);
                }
            }
        }
        return new JsonObject();
    }

    private void build(JsonObject json) {
        if (json.has("title")) {
            bookTitle = Component.literal(json.get("title").getAsString());
        }
        for (JsonElement element : json.has("chapters") ? json.getAsJsonArray("chapters") : new JsonArray()) {
            JsonObject c = element.getAsJsonObject();
            ResourceLocation icon = ResourceLocation.tryParse(c.has("icon") ? c.get("icon").getAsString() : "minescore:manual");
            chapters.add(new Chapter(Component.literal(c.get("title").getAsString()),
                    new ItemStack(BuiltInRegistries.ITEM.get(icon)), strings(c.getAsJsonArray("text"))));
        }
        // the first spread: the introduction, then the contents
        flow(strings(json.getAsJsonArray("intro")), -1, bookTitle);
        if (pages.size() % 2 == 0) {
            pages.add(new Page(List.of(), -3)); // a long intro: keep the contents on a right-hand page
        }
        pages.add(new Page(List.of(), -2));
        for (int i = 0; i < chapters.size(); i++) {
            chapterStart.add(pages.size());
            flow(chapters.get(i).text(), i, chapters.get(i).title());
        }
    }

    private static List<String> strings(JsonArray array) {
        List<String> out = new ArrayList<>();
        if (array != null) {
            array.forEach(e -> out.add(e.getAsString()));
        }
        return out;
    }

    /** Lays paragraphs onto as many pages as they need, the first opening with {@code title}. */
    private void flow(List<String> paragraphs, int chapter, Component title) {
        int first = pages.size();
        List<Line> lines = new ArrayList<>();
        int y = 20; // under the title
        for (String paragraph : paragraphs) {
            boolean heading = paragraph.startsWith("# ");
            Component text = heading ? Component.literal(paragraph.substring(2)).withStyle(ChatFormatting.BOLD)
                    : Component.literal(paragraph);
            List<FormattedCharSequence> wrapped = font.split(text, PAGE_W);
            if (heading && y > 0) {
                y += 2;
            }
            if (!heading && y + LINE > PAGE_H) {
                y = PAGE_H; // start the paragraph on the next page
            }
            if (heading && y + LINE * 2 > PAGE_H) { // never leave a heading alone at the foot of a page
                pages.add(new Page(lines, pages.size() == first ? chapter : -3));
                lines = new ArrayList<>();
                y = 0;
            }
            for (FormattedCharSequence seq : wrapped) {
                if (y + LINE > PAGE_H) {
                    pages.add(new Page(lines, pages.size() == first ? chapter : -3));
                    lines = new ArrayList<>();
                    y = 0;
                }
                lines.add(new Line(seq, y, heading));
                y += LINE;
            }
            y += heading ? 1 : GAP;
        }
        pages.add(new Page(lines, pages.size() == first ? chapter : -3));
    }

    private int contentsPage() {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).chapter() == -2) {
                return i;
            }
        }
        return 1;
    }

    private int spreads() {
        return (pages.size() + 1) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.blit(TEXTURE, left, top, 0, 0, W, H, 256, 256);
        for (int side = 0; side < 2; side++) {
            int index = spread * 2 + side;
            if (index < pages.size()) {
                drawPage(g, pages.get(index), index, left + PAGE_X[side], top + PAGE_TOP, mouseX, mouseY);
            }
        }
        if (spread > 0) {
            arrow(g, PREV_X, 196, mouseX, mouseY);
        }
        if (spread < spreads() - 1) {
            arrow(g, NEXT_X, 184, mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void arrow(GuiGraphics g, int x, int v, int mouseX, int mouseY) {
        boolean hover = over(mouseX, mouseY, left + x, top + ARROW_Y, ARROW_W, ARROW_H);
        g.blit(TEXTURE, left + x, top + ARROW_Y, hover ? 20 : 0, v, ARROW_W, ARROW_H, 256, 256);
    }

    private void drawPage(GuiGraphics g, Page page, int index, int x, int y, int mouseX, int mouseY) {
        int ink = UiTheme.c("book_ink"), heading = UiTheme.c("book_heading");
        if (page.chapter() == -2) { // the contents
            g.drawString(font, Component.translatable("gui.minescore.manual.contents").withStyle(ChatFormatting.BOLD), x, y, heading, false);
            for (int i = 0; i < chapters.size(); i++) {
                int ry = y + 16 + i * CONTENTS_ROW;
                boolean hover = over(mouseX, mouseY, x - 2, ry - 2, PAGE_W + 4, CONTENTS_ROW);
                g.pose().pushPose();
                g.pose().translate(x, ry - 2, 0);
                g.pose().scale(0.75f, 0.75f, 1);
                g.renderItem(chapters.get(i).icon(), 0, 0);
                g.pose().popPose();
                g.drawString(font, chapters.get(i).title(), x + 16, ry + 1,
                        hover ? UiTheme.c("book_link_hover") : UiTheme.c("book_link"), false);
            }
            return;
        }
        if (page.chapter() != -3) {
            Component title = page.chapter() >= 0 ? chapters.get(page.chapter()).title() : bookTitle;
            int tx = x;
            if (page.chapter() >= 0) {
                g.pose().pushPose();
                g.pose().translate(x, y - 3, 0);
                g.pose().scale(0.75f, 0.75f, 1);
                g.renderItem(chapters.get(page.chapter()).icon(), 0, 0);
                g.pose().popPose();
                tx += 15;
            }
            g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), tx, y, heading, false);
            g.fill(x, y + 11, x + PAGE_W, y + 12, (heading & 0x00FFFFFF) | 0x60000000);
        }
        for (Line line : page.lines()) {
            g.drawString(font, line.text(), x, y + line.y(), line.heading() ? heading : ink, false);
        }
        String number = String.valueOf(index + 1);
        g.drawString(font, number, x + PAGE_W / 2 - font.width(number) / 2, top + H - 14, UiTheme.c("book_dim"), false);
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (spread > 0 && over(mouseX, mouseY, left + PREV_X, top + ARROW_Y, ARROW_W, ARROW_H)) {
                turn(-1);
                return true;
            }
            if (spread < spreads() - 1 && over(mouseX, mouseY, left + NEXT_X, top + ARROW_Y, ARROW_W, ARROW_H)) {
                turn(1);
                return true;
            }
            if (spread == contentsPage() / 2) {
                int x = left + PAGE_X[1], y = top + PAGE_TOP;
                for (int i = 0; i < chapters.size(); i++) {
                    if (over(mouseX, mouseY, x - 2, y + 16 + i * CONTENTS_ROW - 2, PAGE_W + 4, CONTENTS_ROW)) {
                        spread = chapterStart.get(i) / 2;
                        click();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        turn(delta > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP -> turn(-1);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN -> turn(1);
            case GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_HOME -> {
                if (spread != contentsPage() / 2) {
                    spread = contentsPage() / 2;
                    click();
                }
            }
            default -> {
                return super.keyPressed(key, scanCode, modifiers);
            }
        }
        return true;
    }

    private void turn(int by) {
        int next = Math.max(0, Math.min(spreads() - 1, spread + by));
        if (next != spread) {
            spread = next;
            click();
        }
    }

    private void click() {
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
