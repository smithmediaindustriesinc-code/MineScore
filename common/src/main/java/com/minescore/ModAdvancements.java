package com.minescore;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * MineScore's advancements (SCOPE.md §14) all use one criterion, {@code minescore:event}, with the name of
 * what happened: {@code {"trigger": "minescore:event", "conditions": {"event": "fresh_cut"}}}. The code
 * that does the thing calls {@link #award}, or {@link #awardNear} for things that happen in the world
 * rather than to one player (a speaker shorting is earned by everyone who heard it).
 */
public final class ModAdvancements {
    public static final Trigger EVENT = new Trigger();
    /** How close a player must be to earn something that happened in the world. */
    public static final double NEAR = 32;

    private ModAdvancements() {}

    public static void register() {
        MineScore.platform().registerTrigger(EVENT);
    }

    public static void award(Player player, String event) {
        if (player instanceof ServerPlayer serverPlayer) {
            EVENT.trigger(serverPlayer, event);
        }
    }

    public static void awardNear(Level level, BlockPos pos, String event) {
        if (level instanceof ServerLevel serverLevel) {
            for (ServerPlayer player : serverLevel.players()) {
                if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < NEAR * NEAR) {
                    EVENT.trigger(player, event);
                }
            }
        }
    }

    public static final class Trigger extends SimpleCriterionTrigger<Trigger.Instance> {
        private static final ResourceLocation ID = MineScore.id("event");

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        protected Instance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
            return new Instance(player, GsonHelper.getAsString(json, "event"));
        }

        public void trigger(ServerPlayer player, String event) {
            trigger(player, instance -> instance.event.equals(event));
        }

        public static final class Instance extends AbstractCriterionTriggerInstance {
            private final String event;

            Instance(ContextAwarePredicate player, String event) {
                super(ID, player);
                this.event = event;
            }

            @Override
            public JsonObject serializeToJson(SerializationContext context) {
                JsonObject json = super.serializeToJson(context);
                json.addProperty("event", event);
                return json;
            }
        }
    }
}
