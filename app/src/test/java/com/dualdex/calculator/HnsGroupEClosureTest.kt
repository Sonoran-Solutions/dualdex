package com.dualdex.calculator

import com.dualdex.battle.DamageBlockerPresentation
import com.dualdex.pokemon.hns.*
import org.junit.Assert.*
import org.junit.Test

class HnsGroupEClosureTest {
    @Test fun `every unsupported identity is covered and unknown context stays refused with a named reason`() {
        for ((id, _) in HnsAbilityRegistry.pinnedAbilityDomain()) {
            val entry = HnsAbilityRegistry.classify(id)
            if (entry.category == HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT ||
                entry.category == HnsAbilityCategory.UNCLASSIFIED) {
                val disposition = HnsGroupEData.abilityDispositions[id] ?: error("Unassigned ability $id")
                val decision = HnsAbilityContextPolicy.assess(id, null)
                assertEquals(HnsAbilityRequestRelevance.UNKNOWN, decision.relevance)
                for (side in HnsAbilitySide.values()) {
                    val message = DamageBlockerPresentation.Ability(decision.copy(side = side)).headline
                    assertTrue(message, message.contains(entry.titleCaseName))
                    assertTrue(message, message.contains(disposition.reason))
                    assertTrue(message, message.startsWith(if (side == HnsAbilitySide.ATTACKER) "Your " else "Foe's "))
                }
            }
        }
        for (id in 0..900) {
            val entry = HnsItemRegistry.classify(id)
            assertNotEquals("Unclassified item $id", HnsItemCategory.UNCLASSIFIED, entry.category)
            if (entry.category == HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT) {
                val disposition = HnsGroupEData.itemDispositions[id] ?: error("Unassigned item $id")
                val decision = HnsItemContextPolicy.assess(id, null)
                assertEquals(HnsItemRequestRelevance.UNKNOWN, decision.relevance)
                for (side in HnsItemSide.values()) {
                    val message = DamageBlockerPresentation.Item(decision.copy(side = side)).headline
                    assertTrue(message, message.contains(decision.itemName))
                    assertTrue(message, message.contains(disposition.reason))
                    assertTrue(message, message.startsWith(if (side == HnsItemSide.ATTACKER) "Your " else "Foe's "))
                }
            }
        }
    }

    @Test fun `exact Charge handoffs are conditional and cannot be authorized by ability name alone`() {
        for (id in listOf(277, 280)) {
            assertEquals(HnsGroupETier.EXACT, HnsGroupEData.abilityDispositions.getValue(id).tier)
            assertEquals(HnsAbilityCategory.MODELLED_HNS_CONDITIONAL, HnsAbilityRegistry.classify(id).category)
            assertEquals(HnsAbilityRequestRelevance.UNKNOWN, HnsAbilityContextPolicy.assess(id, null).relevance)
        }
    }
}
