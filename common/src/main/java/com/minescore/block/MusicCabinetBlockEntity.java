package com.minescore.block;

import com.minescore.item.CdData;
import com.minescore.item.CdItem;
import com.minescore.item.ScoreItem;
import com.minescore.registry.ModBlockEntities;
import com.minescore.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * What a Music Cabinet holds, each shelf packed from the left with no gaps, and sent to clients so the
 * sleeves can take their records' colours and the pointed-at title can show.
 */
public class MusicCabinetBlockEntity extends BlockEntity {
    private final List<ItemStack> scores = new ArrayList<>();
    private final List<ItemStack> vinyls = new ArrayList<>();

    public MusicCabinetBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MUSIC_CABINET.get(), pos, state);
    }

    /** The shelf an item goes on: true for Scores, false for records, null for anything else, blanks included. */
    public static @Nullable Boolean shelfFor(ItemStack stack) {
        if (stack.is(ModItems.SCORE.get()) && ScoreItem.Data.read(stack) != null) {
            return true;
        }
        if (stack.is(ModItems.ENCODED_CD.get()) && CdData.read(stack) != null) {
            return false;
        }
        return null;
    }

    private List<ItemStack> shelf(boolean score) {
        return score ? scores : vinyls;
    }

    public List<ItemStack> scores() {
        return scores;
    }

    public List<ItemStack> vinyls() {
        return vinyls;
    }

    public boolean isFull(boolean score) {
        return shelf(score).size() >= (score ? MusicCabinetBlock.SCORE_SLOTS : MusicCabinetBlock.VINYL_SLOTS);
    }

    public boolean has(MusicCabinetBlock.Spot spot) {
        return spot.index() < shelf(spot.score()).size();
    }

    public @Nullable ItemStack at(MusicCabinetBlock.Spot spot) {
        return has(spot) ? shelf(spot.score()).get(spot.index()) : null;
    }

    /** Files a Score or record in the first free place from the left. */
    public void add(ItemStack stack) {
        Boolean score = shelfFor(stack);
        if (score == null || isFull(score)) {
            return;
        }
        shelf(score).add(stack);
        sound(score, true);
        changed();
    }

    /** Takes one out; the rest close up to the left. */
    public ItemStack take(MusicCabinetBlock.Spot spot) {
        if (!has(spot)) {
            return ItemStack.EMPTY;
        }
        ItemStack out = shelf(spot.score()).remove(spot.index());
        sound(spot.score(), false);
        changed();
        return out;
    }

    /** Everything in it, emptying it: for when it's broken. */
    public List<ItemStack> empty() {
        List<ItemStack> all = new ArrayList<>(scores);
        all.addAll(vinyls);
        scores.clear();
        vinyls.clear();
        return all;
    }

    private void sound(boolean score, boolean in) {
        if (level != null) {
            level.playSound(null, worldPosition, in ? SoundEvents.CHISELED_BOOKSHELF_INSERT : SoundEvents.CHISELED_BOOKSHELF_PICKUP,
                    SoundSource.BLOCKS, 1, score ? 1 : 1.25f);
        }
    }

    /** The colour of the sleeve in place {@code index}: its record's dye. */
    public int sleeveColor(int index) {
        return index < vinyls.size() && vinyls.get(index).getItem() instanceof CdItem record
                ? record.getColor(vinyls.get(index)) : ModItems.ENCODED_LABEL;
    }

    private void changed() {
        setChanged();
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        BlockState counted = state.setValue(MusicCabinetBlock.SCORES, scores.size()).setValue(MusicCabinetBlock.VINYLS, vinyls.size());
        if (counted != state) {
            level.setBlock(worldPosition, counted, 3);
        }
        level.sendBlockUpdated(worldPosition, counted, counted, 3);
        level.updateNeighbourForOutputSignal(worldPosition, counted.getBlock());
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Scores", write(scores));
        tag.put("Vinyls", write(vinyls));
    }

    private static ListTag write(List<ItemStack> stacks) {
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            list.add(stack.save(new CompoundTag()));
        }
        return list;
    }

    private static void read(ListTag list, List<ItemStack> into, int max) {
        into.clear();
        for (int i = 0; i < list.size() && into.size() < max; i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) {
                into.add(stack);
            }
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        read(tag.getList("Scores", Tag.TAG_COMPOUND), scores, MusicCabinetBlock.SCORE_SLOTS);
        read(tag.getList("Vinyls", Tag.TAG_COMPOUND), vinyls, MusicCabinetBlock.VINYL_SLOTS);
        if (level != null && level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8); // redraw the sleeves in their records' colours
        }
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
