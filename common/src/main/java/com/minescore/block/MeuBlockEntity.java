package com.minescore.block;

import com.minescore.item.CardItem;
import com.minescore.item.PresetChipItem;
import com.minescore.menu.MeuMenu;
import com.minescore.meu.CardSettings;
import com.minescore.meu.CardType;
import com.minescore.meu.Effect;
import com.minescore.meu.EffectChain;
import com.minescore.meu.Preset;
import com.minescore.playback.PlaybackManager;
import com.minescore.registry.ModBlockEntities;
import com.minescore.wiring.SpeakerSystems;
import com.minescore.wiring.WireNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Multi-Effects Unit (SCOPE.md §3.10): six card slots, a Preset Chip slot, a master bypass and the
 * expression pedal. Each card's settings live on the card; the unit remembers which preset it last
 * loaded or saved and whether it's been changed since.
 */
public class MeuBlockEntity extends BlockEntity implements MenuProvider, RedstoneHub {
    private final SimpleContainer items = new SimpleContainer(MeuMenu.CARDS + 1);
    private boolean bypass;
    private boolean redstoneControl = RedstoneHub.controlByDefault();
    /** Switched off by a redstone signal (§6.4), as if on master bypass. */
    private boolean redstoneOff;
    private float expression;
    private String presetName = Preset.BUILT_IN.get(0).name();
    private boolean modified;
    private int builtIn;

    public MeuBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MULTI_EFFECTS_UNIT.get(), pos, state);
        items.addListener(container -> changed());
    }

    public SimpleContainer items() {
        return items;
    }

    public boolean bypass() {
        return bypass;
    }

    /** Passing the sound straight through: on master bypass, or switched off by redstone. */
    public boolean disabled() {
        return bypass || redstoneOff;
    }

    @Override
    public boolean redstoneControl() {
        return redstoneControl;
    }

    @Override
    public void setRedstoneControl(boolean on) {
        redstoneControl = on;
        readRedstone();
        changed();
    }

    @Override
    public void readRedstone() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean off = redstoneControl && level.hasNeighborSignal(worldPosition) && RedstoneHub.feedsSpeakers(level, worldPosition);
        if (off != redstoneOff) {
            redstoneOff = off;
            changed();
        }
    }

    /** The number of cards in it that aren't bypassed, 0–6 (every face). */
    @Override
    public int comparatorSignal(Direction face) {
        int active = 0;
        for (int i = 0; i < MeuMenu.CARDS; i++) {
            CardSettings card = card(i);
            if (card != null && !card.bypass()) {
                active++;
            }
        }
        return active;
    }

    public float expression() {
        return expression;
    }

    /** The preset's name as shown: with a star once anything's been changed since it was loaded or saved. */
    public String presetLabel() {
        return presetName + (modified ? "*" : "");
    }

    public int builtIn() {
        return builtIn;
    }

    public @Nullable CardSettings card(int slot) {
        ItemStack stack = items.getItem(slot);
        return stack.getItem() instanceof CardItem card ? CardSettings.read(stack, card.type()) : null;
    }

    /** What the unit does to the sound right now, in slot order. */
    public EffectChain chain() {
        List<CardSettings> cards = new ArrayList<>();
        for (int i = 0; i < MeuMenu.CARDS; i++) {
            CardSettings card = card(i);
            if (card != null) {
                cards.add(card);
            }
        }
        return new EffectChain(cards, disabled(), expression);
    }

    // --- changes from the screen (MeuPackets) ---------------------------------------------------

    private void setCard(int slot, CardSettings settings) {
        ItemStack stack = items.getItem(slot);
        if (stack.getItem() instanceof CardItem) {
            settings.write(stack);
            modified = true;
            items.setChanged();
        }
    }

    public void handle(MeuPackets.Set packet, ServerPlayer player) {
        int slot = packet.slot();
        CardSettings card = slot >= 0 && slot < MeuMenu.CARDS ? card(slot) : null;
        switch (packet.action()) {
            case EFFECT -> {
                if (card != null) {
                    List<Effect> choices = Effect.of(card.type());
                    setCard(slot, card.withEffect(choices.get(Math.floorMod(packet.i(), choices.size()))));
                }
            }
            case KNOB -> {
                if (card != null && Float.isFinite(packet.f())) {
                    setCard(slot, card.withValue(packet.i(), packet.f()));
                }
            }
            case BYPASS -> {
                if (card != null) {
                    setCard(slot, card.withBypass(!card.bypass()));
                }
            }
            case RESET_CARD -> {
                if (card != null) {
                    setCard(slot, CardSettings.of(card.effect()).withBypass(card.bypass()));
                }
            }
            case REDSTONE -> setRedstoneControl(!redstoneControl);
            case MASTER -> {
                bypass = !bypass;
                changed();
            }
            case EXPRESSION -> {
                if (Float.isFinite(packet.f())) {
                    expression = Mth.clamp(packet.f(), 0, 1);
                    changed();
                }
            }
            case LOAD_BUILT_IN -> {
                builtIn = Math.floorMod(packet.i(), Preset.BUILT_IN.size());
                apply(Preset.BUILT_IN.get(builtIn), player);
            }
            case SAVE_CHIP -> {
                ItemStack chip = items.getItem(MeuMenu.CHIP);
                if (chip.getItem() instanceof PresetChipItem) {
                    String name = packet.text().isBlank() ? presetName : packet.text();
                    Preset preset = new Preset(name, chain().cards());
                    preset.write(chip);
                    presetName = preset.name();
                    modified = false;
                    items.setChanged();
                    player.displayClientMessage(Component.translatable("msg.minescore.meu.saved", preset.name()), true);
                }
            }
            case LOAD_CHIP -> {
                Preset preset = Preset.read(items.getItem(MeuMenu.CHIP));
                if (preset != null) {
                    apply(preset, player);
                } else {
                    player.displayClientMessage(Component.translatable("msg.minescore.meu.blank_chip"), true);
                }
            }
        }
    }

    /**
     * Loads a preset onto the cards in the unit: each card it uses takes the preset's settings and moves into
     * the preset's order; cards it doesn't use are bypassed. Cards it needs that aren't in the unit are named.
     */
    private void apply(Preset preset, ServerPlayer player) {
        List<ItemStack> ordered = new ArrayList<>();
        List<ItemStack> rest = new ArrayList<>();
        for (int i = 0; i < MeuMenu.CARDS; i++) {
            ItemStack stack = items.getItem(i).copy();
            if (stack.getItem() instanceof CardItem cardItem) {
                CardSettings wanted = preset.card(cardItem.type());
                if (wanted != null) {
                    wanted.write(stack);
                } else {
                    CardSettings.read(stack, cardItem.type()).withBypass(true).write(stack);
                    rest.add(stack);
                    continue;
                }
            }
            if (!stack.isEmpty()) {
                ordered.add(stack);
            }
        }
        ordered.sort((a, b) -> Integer.compare(indexIn(preset, a), indexIn(preset, b)));
        ordered.addAll(rest);
        for (int i = 0; i < MeuMenu.CARDS; i++) {
            items.setItem(i, i < ordered.size() ? ordered.get(i) : ItemStack.EMPTY);
        }
        presetName = preset.name();
        modified = false;
        List<Component> missing = new ArrayList<>();
        for (CardSettings card : preset.cards()) {
            boolean present = ordered.stream().anyMatch(s -> ((CardItem) s.getItem()).type() == card.type());
            if (!present) {
                missing.add(Component.translatable("item.minescore." + card.type().itemName()));
            }
        }
        if (!missing.isEmpty()) {
            Component list = missing.stream().reduce((a, b) -> Component.empty().append(a).append(", ").append(b)).orElse(Component.empty());
            player.displayClientMessage(Component.translatable("msg.minescore.meu.missing", list).withStyle(ChatFormatting.GOLD), false);
        }
        changed();
    }

    private static int indexIn(Preset preset, ItemStack stack) {
        CardType type = ((CardItem) stack.getItem()).type();
        for (int i = 0; i < preset.cards().size(); i++) {
            if (preset.cards().get(i).type() == type) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    /** Saves, tells clients, and passes the new effects to whatever is playing through the unit. */
    private void changed() {
        setChanged();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        BlockPos source = SpeakerSystems.sourceOf(WireNetwork.get(serverLevel)::endsAt, worldPosition);
        if (source != null && level.getBlockEntity(source) instanceof MixerBlockEntity mixer) {
            mixer.onSpeed(chain().speed()); // a new speed moves the song's timeline
        }
        PlaybackManager.get(serverLevel).refresh();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.minescore.multi_effects_unit");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MeuMenu(id, inventory, items, worldPosition);
    }

    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < items.getContainerSize(); i++) {
            all.add(items.getItem(i));
        }
        return all;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        NonNullList<ItemStack> list = NonNullList.withSize(items.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < list.size(); i++) {
            list.set(i, items.getItem(i));
        }
        ContainerHelper.saveAllItems(tag, list, true);
        tag.putBoolean("Bypass", bypass);
        tag.putBoolean("RedstoneControl", redstoneControl);
        tag.putBoolean("RedstoneOff", redstoneOff);
        tag.putFloat("Expression", expression);
        tag.putString("Preset", presetName);
        tag.putBoolean("Modified", modified);
        tag.putInt("BuiltIn", builtIn);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        NonNullList<ItemStack> list = NonNullList.withSize(items.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list);
        for (int i = 0; i < list.size(); i++) {
            items.setItem(i, list.get(i));
        }
        bypass = tag.getBoolean("Bypass");
        if (tag.contains("RedstoneControl")) {
            redstoneControl = tag.getBoolean("RedstoneControl");
        }
        redstoneOff = tag.getBoolean("RedstoneOff");
        expression = tag.getFloat("Expression");
        presetName = tag.contains("Preset") ? tag.getString("Preset") : Preset.BUILT_IN.get(0).name();
        modified = tag.getBoolean("Modified");
        builtIn = tag.getInt("BuiltIn");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
