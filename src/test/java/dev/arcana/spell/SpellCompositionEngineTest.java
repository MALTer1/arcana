package dev.arcana.spell;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SpellCompositionEngineTest {
    @Test
    void everyRegisteredComponentHasACompositionProperty() {
        ComponentRegistry.bootstrap();
        for (SpellComponent component : ComponentRegistry.all()) {
            assertFalse(SpellCompositionEngine.compose(List.of(component.id())).properties().isEmpty(),
                "Missing semantic properties for " + component.id());
        }
    }

    @Test
    void specificThreeElementReactionReplacesItsPairReactions() {
        List<ElementalReactionEngine.Reaction> reactions =
            ElementalReactionEngine.resolve(List.of("fire", "water", "lightning"));

        assertEquals(List.of("storm_steam"),
            reactions.stream().map(ElementalReactionEngine.Reaction::id).toList());
    }

    @Test
    void independentOverlappingReactionsCanCoexist() {
        List<ElementalReactionEngine.Reaction> reactions =
            ElementalReactionEngine.resolve(List.of("fire", "water", "wind"));

        Set<String> ids = reactions.stream()
            .map(ElementalReactionEngine.Reaction::id)
            .collect(Collectors.toSet());

        assertEquals(Set.of("steam", "wildfire", "mist"), ids);
    }

    @Test
    void distinctComponentsFromTheSameCategoryAreAllowed() {
        ComponentRegistry.bootstrap();
        SpellDraft draft = new SpellDraft();
        assertTrue(draft.add("projectile").success());
        assertTrue(draft.add("chain").success());
        assertTrue(draft.add("fire").success());
        assertTrue(draft.add("wind").success());
        assertTrue(draft.add("binding").success());
        assertTrue(draft.isValid());
    }

    @Test
    void projectileElementsModifyTravelSpeed() {
        SpellCompositionEngine.Plan plan =
            SpellCompositionEngine.compose(List.of("projectile", "lightning"));

        assertEquals(1.35, plan.travelSpeedMultiplier(), 0.0001);
        assertTrue(plan.interactions().contains("Conductive projectile"));
    }

    @Test
    void lightningAndBindingProduceAnInterruption() {
        SpellCompositionEngine.Plan plan =
            SpellCompositionEngine.compose(List.of("projectile", "lightning", "binding"));

        assertTrue(plan.stunTicks() > 0);
        assertTrue(plan.slowTicks() > 0);
        assertTrue(plan.interactions().contains("Electrified binding"));
    }

    @Test
    void wetTargetsStrengthenElectricalInteractions() {
        SpellCompositionEngine.Plan plan = SpellCompositionEngine.compose(
            List.of("lightning", "binding"),
            new SpellCompositionEngine.Context(true, false, false));

        assertEquals(80, plan.stunTicks());
        assertTrue(plan.impactDamageBonus() > 0.0);
        assertTrue(plan.interactions().contains("Wet-target conduction"));
    }

    @Test
    void allSelectedCategoriesCanComposeTogether() {
        SpellCompositionEngine.Plan plan = SpellCompositionEngine.compose(
            List.of("projectile", "chain", "fire", "wind", "multiple_targets", "binding", "healing"));

        assertTrue(plan.interactions().contains("Composite delivery"));
        assertTrue(plan.interactions().contains("Mixed elements"));
        assertTrue(plan.interactions().contains("Layered effects"));
    }
}
