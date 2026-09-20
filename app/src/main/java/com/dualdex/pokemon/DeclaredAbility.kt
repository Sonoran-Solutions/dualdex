package com.dualdex.pokemon

/**
 * The ability the pinned source data declares for one ability slot, for one exact species/form.
 *
 * This is an identity, not an effective battle state. A declared slot entry says what the
 * build's static data tables hold; it does not say what ability a specific Pokemon will
 * actually use in battle. DualDex does not read the runtime settings (such as Heart &
 * Soul 2.0.5's challenge-mode ability overrides) that can change that, so this value is
 * never a substitute for observed live battle data.
 */
sealed class DeclaredAbility {

    /** The source data declares this exact ability ID for the slot. */
    data class Declared(
        /** The numeric ability ID in the pinned build's ability enum. */
        val abilityId: Int,
        /** The display name as written in the build's ability table. */
        val name: String
    ) : DeclaredAbility()

    /**
     * The source data stores an explicit "no ability" sentinel in this slot (slot 2 of
     * species with no hidden ability, etc.). The empty slot is part of the pinned table:
     * filtering it out would shift later slots and misreport which ability belongs to
     * which slot number.
     */
    object EmptySlot : DeclaredAbility()

    /**
     * No source-backed declaration exists for this lookup. Unknown species IDs,
     * out-of-range slot numbers, and data packs without ability declarations all
     * land here. This is an explicit absence, never a substitute value.
     */
    object Absent : DeclaredAbility()
}
