package com.dualdex.calculator.census

import com.dualdex.calculator.CalcCapabilityPolicy
import com.dualdex.calculator.CalcRequestBoundary
import com.dualdex.calculator.CalcRequestOutcome
import com.dualdex.pokemon.DeclaredAbility
import com.dualdex.pokemon.GameDataPack
import com.dualdex.pokemon.GameDataPackRegistry
import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.MoveInfo
import com.dualdex.pokemon.SpeciesInfo
import com.dualdex.pokemon.hns.Hns205ItemCatalogue
import com.dualdex.pokemon.hns.HnsAbilityRegistry
import com.dualdex.pokemon.hns.HnsMoveMechanicsRegistry
import com.dualdex.romhack.RomHackProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The offline H&S 2.0.5 trainer blocker census (issue #84).
 *
 * For every trainer Pokemon the pinned build defines, this drives the **real production
 * policy** - [CalcRequestBoundary] and, through it, [CalcCapabilityPolicy] and the
 * contextual ability / item / field policies - over the pinned, resolved trainer data and
 * records every structured refusal reason. It never calls the JS/native damage engine, never
 * re-implements a capability decision, and never weakens a production blocker: the only
 * thing it adds is the truthfully-observed neutral baseline runtime state described in
 * [HnsCalcCensusBaseline].
 *
 * HOST-ONLY. Nothing in the app calls this; it is measurement infrastructure.
 */
object HnsCalcCensusEngine {

    const val SCHEMA_VERSION: Int = 1

    /** One reference player team lead, loaded from the committed fixture JSON. */
    data class ReferenceLead(
        val teamId: String,
        val species: String,
        val level: Int,
        val ability: String,
        val abilityId: Int,
        val item: String?,
        val itemId: Int,
        val types: List<String>,
        val moves: List<String>,
        val notes: List<String>
    )

    /** One resolved trainer party entry, exactly as the pinned source initializes it. */
    data class TrainerMon(
        val partySlot: Int,
        val speciesId: Int,
        /** The data pack's own display name for [speciesId]. */
        val species: String,
        /** The pinned `SPECIES_*` symbol's species name, kept for provenance only. */
        val speciesRaw: String,
        val level: Int,
        val abilityId: Int,
        val abilityName: String,
        val abilitySource: String,
        val itemId: Int,
        val item: String?,
        val moves: List<String>,
        val movesSource: String,
        val sourceLine: Int
    )

    /** One trainer battle. */
    data class TrainerBattle(
        val key: String,
        val difficulty: String,
        val trainerClass: String,
        val battleType: String,
        val name: String?,
        val sourceLine: Int,
        val party: List<TrainerMon>
    ) {
        val isSingles: Boolean get() = battleType == "TRAINER_BATTLE_TYPE_SINGLES"
        val lead: TrainerMon get() = party.first()
    }

    /** One evaluated policy request, with its outcome. */
    data class RequestRecord(
        val key: String,
        val direction: String,
        val trainerKey: String,
        val trainerSlot: Int,
        val referenceTeam: String,
        val attackerSpeciesId: Int,
        val attackerSpecies: String,
        val attackerAbility: String?,
        val attackerItem: String?,
        val defenderSpeciesId: Int,
        val defenderSpecies: String,
        val defenderAbility: String?,
        val defenderItem: String?,
        val move: String,
        val moveType: String,
        val moveCategory: String,
        val movePower: Int,
        val gameType: String,
        val battlersCount: Int,
        val outcome: HnsCensusOutcome
    )

    /** One move that can never produce a damage display basis, recorded but not a failure. */
    data class ExcludedMove(
        val trainerKey: String,
        val trainerSlot: Int,
        val direction: String,
        val move: String,
        val moveType: String,
        val moveCategory: String,
        val movePower: Int,
        val reason: String
    )

    /**
     * The signed operands an ability-relevance decision actually depends on.
     *
     * `HnsAbilityContextPolicy.contextForRequest` reads exactly the side, the effective move type
     * and category, the attacker's effective types, the defender's battle species, both live HP
     * values, the attacker's status word, and the observed topology. The census baseline pins all
     * of those except the ones listed here, so two census requests with the same key are decided
     * identically for the same ability.
     */
    data class OperandKey(
        val direction: String,
        val attackerSpeciesId: Int,
        val attackerSpecies: String,
        val attackerAbilityId: Int,
        val attackerAbilityName: String?,
        val defenderSpeciesId: Int,
        val defenderSpecies: String,
        val defenderAbilityId: Int,
        val defenderAbilityName: String?,
        val move: String,
        val moveType: String,
        val moveCategory: String,
        val stab: Boolean,
        val gameType: String,
        val battlersCount: Int
    ) {
        /** The part of the key an ability substitution does not change. */
        val operands: String
            get() = "$direction|$attackerSpeciesId|$defenderSpeciesId|$move|$moveType|" +
                "$moveCategory|$stab|$gameType|$battlersCount"
    }

    /** One operand cohort: every census request whose ability decisions are decided together. */
    data class OperandCohort(
        val key: OperandKey,
        /** The exact census request keys in this cohort, so block counts de-duplicate exactly. */
        val requestKeys: Set<String>,
        val battles: Set<String>
    ) {
        val requestCount: Int get() = requestKeys.size
    }

    /**
     * One Random Abilities trial: this ability, on this side, in this operand cohort's context.
     *
     * [blockedByAbility] is true only when the production policy refused because of THIS ability -
     * that is, the ability decision came back RELEVANT and the verdict carries
     * `HNS_ABILITY_EFFECT_NOT_MODELLED`. [abilityRelevance] preserves the full three-valued result
     * (`PROVEN_IRRELEVANT` / `RELEVANT` / `UNKNOWN`) so a contextual ability stays visible instead
     * of being flattened into supported/unsupported.
     */
    data class AbilityTrial(
        val abilityId: Int,
        val abilityName: String,
        val side: String,
        val category: String,
        val blockedByAbility: Boolean,
        val abilityRelevance: String,
        val abilityRule: String?,
        /** Requests in which production policy PROVED this ability cannot change the number. */
        val clearedRequests: Int,
        /** Requests in which production policy blamed this ability, under uniform assignment. */
        val requestBlocks: Int,
        /** Distinct trainer battles in which it blamed this ability. */
        val battleBlocks: Int,
        /** Distinct reviewed context rules that fired in any context for this rank. */
        val rules: List<String>
    ) {
        /**
         * The `(ability, side, category)` ranking key this trial row contributes to.
         *
         * Rows are aggregated by this key so the output carries one line per ability per
         * side/category, with the per-context detail available from the trial table itself.
         */
        val rankKey: String get() = "$abilityId|$side|$category"
    }

    /** Everything one census run produced. */
    data class CensusRun(
        val trainers: List<TrainerBattle>,
        val referenceLeads: List<ReferenceLead>,
        val requests: List<RequestRecord>,
        val excludedMoves: List<ExcludedMove>,
        val abilityDomain: List<Pair<Int, String>>,
        val abilityCohorts: List<OperandCohort>,
        val abilityTrials: List<AbilityTrial>,
        val baselinePositiveControl: BaselinePositiveControl
    )

    data class BaselinePositiveControl(
        val attackerSpecies: String,
        val defenderSpecies: String,
        val move: String,
        val displayTier: String,
        val limitations: List<String>
    )

    // ------------------------------------------------------------------ loading the inputs

    fun loadReferenceLeads(file: File): List<ReferenceLead> {
        val root = JSONObject(file.readText(Charsets.UTF_8))
        val teams = root.getJSONArray("teams")
        val leads = mutableListOf<ReferenceLead>()
        for (index in 0 until teams.length()) {
            val team = teams.getJSONObject(index)
            val lead = team.getJSONObject("lead")
            leads += ReferenceLead(
                teamId = team.getString("id"),
                species = lead.getString("species"),
                level = lead.getInt("level"),
                ability = lead.getString("ability"),
                abilityId = lead.getInt("abilityId"),
                item = if (lead.isNull("item")) null else lead.getString("item"),
                itemId = lead.getInt("itemId"),
                types = lead.getJSONArray("types").toStringList(),
                moves = lead.getJSONArray("moves").toStringList(),
                notes = team.optJSONArray("notes")?.toStringList() ?: emptyList()
            )
        }
        check(leads.isNotEmpty()) { "the reference team fixture declares no team" }
        return leads.sortedBy { it.teamId }
    }

    fun loadTrainerBattles(file: File): List<TrainerBattle> {
        val root = JSONObject(file.readText(Charsets.UTF_8))
        val array = root.getJSONArray("trainers")
        val battles = mutableListOf<TrainerBattle>()
        for (index in 0 until array.length()) {
            val trainer = array.getJSONObject(index)
            val partySize = trainer.getInt("partySize")
            if (partySize == 0) continue
            val partyArray = trainer.getJSONArray("party")
            val party = mutableListOf<TrainerMon>()
            for (slot in 0 until partyArray.length()) {
                val mon = partyArray.getJSONObject(slot)
                party += TrainerMon(
                    partySlot = mon.getInt("partySlot"),
                    speciesId = mon.getInt("speciesId"),
                    species = mon.getString("species"),
                    speciesRaw = mon.getString("speciesRaw"),
                    level = mon.getInt("level"),
                    abilityId = mon.getInt("abilityId"),
                    abilityName = mon.getString("ability"),
                    abilitySource = mon.getString("abilitySource"),
                    itemId = mon.getInt("itemId"),
                    item = if (mon.isNull("item")) null else mon.getString("item"),
                    moves = mon.getJSONArray("moves").toStringList(),
                    movesSource = mon.getString("movesSource"),
                    sourceLine = mon.getInt("sourceLine")
                )
            }
            battles += TrainerBattle(
                key = trainer.getString("key"),
                difficulty = trainer.getString("difficulty"),
                trainerClass = trainer.getString("trainerClass"),
                battleType = trainer.getString("battleType"),
                name = if (trainer.isNull("name")) null else trainer.getString("name"),
                sourceLine = trainer.getInt("sourceLine"),
                party = party
            )
        }
        check(battles.isNotEmpty()) { "the trainer inventory contains no trainer battle" }
        return battles.sortedBy { it.key }
    }

    // ------------------------------------------------------------- input self-verification

    /**
     * Fail closed when the committed inventory and the committed Kotlin data pack disagree.
     *
     * The census is only meaningful if the names it requests are the names the pinned pack
     * resolves, and if the ability identity resolved from the pinned species data is the same
     * identity the shipped ability catalogue knows.
     */
    fun verifyInputs(
        pack: GameDataPack,
        profile: RomHackProfile,
        trainers: List<TrainerBattle>,
        leads: List<ReferenceLead>
    ) {
        check(GameDataPackRegistry.getForProfile(profile) === pack) {
            "the census profile must resolve to the pinned H&S 2.0.5 data pack"
        }
        for (lead in leads) {
            requireNotNull(pack.getSpeciesByName(lead.species)) {
                "reference lead species ${lead.species} is not in the pinned data pack"
            }
            val declared = pack.getDeclaredAbilityForSlot(
                pack.getSpeciesByName(lead.species)!!.id, 0
            )
            check(declared is DeclaredAbility.Declared && declared.abilityId == lead.abilityId) {
                "reference lead ${lead.teamId} declares ability ${lead.abilityId} but the pinned " +
                    "pack declares $declared for slot 0"
            }
            for (move in lead.moves) {
                requireNotNull(pack.getMoveByName(move)) {
                    "reference lead move $move is not in the pinned data pack"
                }
            }
            if (lead.item != null) {
                val catalogue = Hns205ItemCatalogue.get(lead.itemId)
                check(catalogue != null && catalogue.sourceName == lead.item) {
                    "reference lead item ${lead.item} does not match pinned item ID ${lead.itemId}"
                }
            }
        }

        for (trainer in trainers) {
            for (mon in trainer.party) {
                // The species is verified by ID, which is the pinned enum identity the source
                // resolved. Its display NAME is NOT asserted to be name-resolvable: the data
                // pack deliberately refuses a name shared by several forms (for example
                // Castform, Pikachu, Giratina, Arceus) so the calculator cannot compute one
                // form's damage under another form's name. That refusal is a real production
                // outcome the census must measure, so a null name lookup is expected here and
                // the request below carries the pack's species name exactly as the app would.
                val species = requireNotNull(pack.getSpecies(mon.speciesId)) {
                    "trainer ${trainer.key} slot ${mon.partySlot} species ID ${mon.speciesId} " +
                        "is not in the pinned data pack"
                }
                check(species.name == mon.species) {
                    "trainer ${trainer.key} slot ${mon.partySlot} names ${mon.species} but the " +
                        "pinned data pack names ID ${mon.speciesId} ${species.name}"
                }
                // The pinned `.party` source may declare an ability explicitly (229 of 1832
                // entries do), and `CreateNPCTrainerPartyFromTrainer` then asserts it is one of
                // the species' own slots. An entry with no declaration resolves through slot 0.
                // Both shapes are checked against the species' declared slots rather than against
                // slot 0 alone.
                val slots = (0 until DECLARED_ABILITY_SLOTS).map {
                    pack.getDeclaredAbilityForSlot(species.id, it)
                }.filterIsInstance<DeclaredAbility.Declared>().map { it.abilityId }
                when (mon.abilitySource) {
                    "party-entry" -> check(mon.abilityId in slots) {
                        "trainer ${trainer.key} slot ${mon.partySlot} declares ability " +
                            "${mon.abilityId}, which is not one of ${species.name}'s declared " +
                            "slots $slots"
                    }
                    else -> check(slots.firstOrNull() == mon.abilityId) {
                        "trainer ${trainer.key} slot ${mon.partySlot} resolved slot-0 ability " +
                            "${mon.abilityId}, but the pinned pack declares ${slots.firstOrNull()} " +
                            "for slot 0"
                    }
                }
                check(HnsAbilityRegistry.classify(mon.abilityId).abilityId == mon.abilityId) {
                    "trainer ${trainer.key} slot ${mon.partySlot} ability ${mon.abilityId} is " +
                        "unknown to the shipped ability catalogue"
                }
                for (move in mon.moves) {
                    requireNotNull(pack.getMoveByName(move)) {
                        "trainer ${trainer.key} slot ${mon.partySlot} move $move is not in the " +
                            "pinned data pack"
                    }
                }
                if (mon.itemId != 0) {
                    check(Hns205ItemCatalogue.get(mon.itemId) != null) {
                        "trainer ${trainer.key} slot ${mon.partySlot} item ${mon.itemId} is not in " +
                            "the pinned item catalogue"
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------ request build

    private fun movesOf(pack: GameDataPack, names: List<String>): List<MoveInfo> =
        names.map { requireNotNull(pack.getMoveByName(it)) { "move $it is not in the pinned pack" } }

    /**
     * True when a move could ever produce a damage display basis.
     *
     * The pinned record's own `power` is the discriminator: a move with no base power deals no
     * direct damage, so the calculator has nothing to display for it and counting it as a failed
     * damage calculation would be wrong. The category is recorded for the report but is not the
     * discriminator, because H&S can derive the category from the move type.
     */
    private fun isDamaging(move: MoveInfo): Boolean = move.power > 0

    /**
     * True when the pinned move-mechanics audit proves the move's damage is the ordinary
     * fixed-base-power path the calculator reproduces. A move outside that subset is excluded
     * from the damage denominator for the same reason a status move is: no displayed number is
     * being refused, the move's damage shape is simply out of scope.
     */
    private fun isOrdinaryMove(pack: GameDataPack, move: MoveInfo): Boolean =
        !HnsMoveMechanicsRegistry.classify(move.id).requiresBlock

    private fun exclusionReason(pack: GameDataPack, move: MoveInfo): String? = when {
        !isDamaging(move) -> "NON_DAMAGING_MOVE"
        !isOrdinaryMove(pack, move) -> "MOVE_DAMAGE_SHAPE_NOT_IN_ORDINARY_SUBSET"
        else -> null
    }

    private fun outcomeFor(
        profile: RomHackProfile,
        attackerSpecies: String,
        attackerLevel: Int,
        attackerAbilityId: Int,
        attackerAbilityName: String?,
        attackerItemId: Int,
        attackerTypes: List<String>,
        attackerSpeciesId: Int,
        defenderSpecies: String,
        defenderLevel: Int,
        defenderAbilityId: Int,
        defenderAbilityName: String?,
        defenderItemId: Int,
        defenderTypes: List<String>,
        defenderSpeciesId: Int,
        move: String,
        gameType: String,
        battlersCount: Int
    ): HnsCensusOutcome {
        // A species the pack refuses to resolve BY NAME is a real production outcome (the
        // calculator cannot tell one form's damage from another's), not a harness failure: the
        // request carries the name and the policy refuses it with SPECIES_NOT_IN_PINNED_DATA.

        val request = com.dualdex.calculator.DamageCalculationRequest(
            gen = 3,
            typeSystem = HnsCalcCensusBaseline.DATA_PACK_ID,
            attacker = com.dualdex.calculator.CalcPokemonInput(
                species = attackerSpecies,
                level = attackerLevel,
                ability = attackerAbilityName,
                abilityId = attackerAbilityId,
                item = Hns205ItemCatalogue.get(attackerItemId)?.sourceName,
                itemId = attackerItemId,
                itemProvenance = com.dualdex.calculator.CalcItemProvenance.MANUAL,
                origin = com.dualdex.calculator.CalcInputOrigin.LIVE_READ,
                partySlot = 0
            ),
            defender = com.dualdex.calculator.CalcPokemonInput(
                species = defenderSpecies,
                level = defenderLevel,
                ability = defenderAbilityName,
                abilityId = defenderAbilityId,
                item = Hns205ItemCatalogue.get(defenderItemId)?.sourceName,
                itemId = defenderItemId,
                itemProvenance = com.dualdex.calculator.CalcItemProvenance.MANUAL,
                origin = com.dualdex.calculator.CalcInputOrigin.LIVE_READ,
                partySlot = 1
            ),
            move = com.dualdex.calculator.CalcMoveInput(name = move),
            field = HnsCalcCensusBaseline.field(gameType)
        )
        val attackerObservation = HnsCalcCensusBaseline.observation(
            participant = HnsCalcCensusBaseline.Participant.ATTACKER,
            partySlot = 0,
            speciesId = attackerSpeciesId,
            speciesName = attackerSpecies,
            types = attackerTypes,
            abilityId = attackerAbilityId,
            itemId = attackerItemId,
            abilityName = attackerAbilityName,
            item = Hns205ItemCatalogue.get(attackerItemId),
            gBattlersCount = battlersCount
        )
        val defenderObservation = HnsCalcCensusBaseline.observation(
            participant = HnsCalcCensusBaseline.Participant.DEFENDER,
            partySlot = 1,
            speciesId = defenderSpeciesId,
            speciesName = defenderSpecies,
            types = defenderTypes,
            abilityId = defenderAbilityId,
            itemId = defenderItemId,
            abilityName = defenderAbilityName,
            item = Hns205ItemCatalogue.get(defenderItemId),
            gBattlersCount = battlersCount
        )
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = HnsCalcCensusBaseline.trust,
            request = request,
            challengeSettings = HnsCalcCensusBaseline.challengeSettings,
            playerBattlerState = attackerObservation,
            enemyBattlerState = defenderObservation,
            activeBattle = true
        )
        val verdict = when (outcome) {
            is CalcRequestOutcome.Ready -> outcome.verdict
            is CalcRequestOutcome.Refused -> outcome.verdict
        }
        check(verdict.ruleset == HnsCensusDisplayClassifier.REQUIRED_RULESET) {
            "the census must measure the H&S 2.0.5 ruleset, got ${verdict.ruleset}"
        }
        return HnsCensusDisplayClassifier.classify(verdict)
    }

    /**
     * Resolve a species by its pinned ID, verifying the display name the caller expects.
     *
     * Species names are looked up by ID rather than by name because the data pack deliberately
     * refuses names shared by several forms; the census still sends the pack's own display name
     * in the request so the production refusal is measured rather than sidestepped.
     */
    private fun speciesFor(pack: GameDataPack, speciesId: Int, expectedName: String): SpeciesInfo {
        val info = requireNotNull(pack.getSpecies(speciesId)) {
            "species ID $speciesId ($expectedName) is not in the pinned data pack"
        }
        check(info.name == expectedName) {
            "species ID $speciesId is named ${info.name} in the pinned data pack, not $expectedName"
        }
        return info
    }

    private fun typesOf(pack: GameDataPack, species: SpeciesInfo): List<String> =
        listOfNotNull(species.type1.displayName, species.type2?.displayName)
            .also { check(it.isNotEmpty()) { "species ${species.name} has no type" } }

    // ----------------------------------------------------------------------------- the run

    fun run(
        pack: GameDataPack,
        profile: RomHackProfile,
        trainers: List<TrainerBattle>,
        leads: List<ReferenceLead>
    ): CensusRun {
        verifyInputs(pack, profile, trainers, leads)
        val abilityDomain = HnsAbilityRegistry.pinnedAbilityDomain().filter { it.first != 0 }
        check(abilityDomain.size == EXPECTED_ABILITY_DOMAIN) {
            "the pinned ability domain has ${abilityDomain.size} identities, expected " +
                "$EXPECTED_ABILITY_DOMAIN; the ability catalogue drifted"
        }
        check(
            HnsAbilityRegistry.classify(OVERGROW_ID).category ==
                com.dualdex.pokemon.hns.HnsAbilityCategory.MODELLED_HNS_CONDITIONAL
        ) { "the Random Abilities fitness attacker ability must be the pinch ability Overgrow" }
        check(
            HnsAbilityRegistry.classify(KEEN_EYE_ID).category ==
                com.dualdex.pokemon.hns.HnsAbilityCategory.PROVEN_NO_DAMAGE_EFFECT
        ) { "the Random Abilities fitness defender ability must be proven damage-irrelevant" }

        val requests = mutableListOf<RequestRecord>()
        val excluded = mutableListOf<ExcludedMove>()
        for (trainer in trainers) {
            val battlersCount = if (trainer.isSingles) {
                HnsCalcCensusBaseline.SINGLES_BATTLERS_COUNT
            } else {
                HnsCalcCensusBaseline.DOUBLES_BATTLERS_COUNT
            }
            val gameType = if (trainer.isSingles) {
                HnsCalcCensusBaseline.SINGLES_GAME_TYPE
            } else {
                HnsCalcCensusBaseline.DOUBLES_GAME_TYPE
            }
            for (lead in leads) {
                val referenceSpecies = requireNotNull(pack.getSpeciesByName(lead.species))
                val referenceTypes = typesOf(pack, referenceSpecies)
                for (mon in trainer.party) {
                    val trainerSpecies = speciesFor(pack, mon.speciesId, mon.species)
                    val trainerTypes = typesOf(pack, trainerSpecies)
                    // The catalogue's own display name, never the raw `ABILITY_*` symbol: the
                    // census artifact reads like the rest of DualDex and the policy resolves the
                    // identity from the numeric ID either way.
                    val trainerAbilityName = HnsAbilityRegistry
                        .classify(mon.abilityId).titleCaseName.takeIf { it.isNotEmpty() }

                    // Direction 1: reference player lead -> trainer Pokemon.
                    for (move in movesOf(pack, lead.moves)) {
                        val reason = exclusionReason(pack, move)
                        if (reason != null) {
                            excluded += ExcludedMove(
                                trainerKey = trainer.key,
                                trainerSlot = mon.partySlot,
                                direction = DIRECTION_REFERENCE_TO_TRAINER,
                                move = move.name,
                                moveType = move.type.displayName,
                                moveCategory = move.category.displayName,
                                movePower = move.power,
                                reason = reason
                            )
                            continue
                        }
                        requests += RequestRecord(
                            key = requestKey(trainer.key, mon.partySlot, lead.teamId, DIRECTION_REFERENCE_TO_TRAINER, move.name),
                            direction = DIRECTION_REFERENCE_TO_TRAINER,
                            trainerKey = trainer.key,
                            trainerSlot = mon.partySlot,
                            referenceTeam = lead.teamId,
                            attackerSpeciesId = referenceSpecies.id,
                            attackerSpecies = lead.species,
                            attackerAbility = lead.ability,
                            attackerItem = lead.item,
                            defenderSpeciesId = mon.speciesId,
                            defenderSpecies = mon.species,
                            defenderAbility = trainerAbilityName,
                            defenderItem = mon.item,
                            move = move.name,
                            moveType = move.type.displayName,
                            moveCategory = move.category.displayName,
                            movePower = move.power,
                            gameType = gameType,
                            battlersCount = battlersCount,
                            outcome = outcomeFor(
                                profile = profile,
                                attackerSpecies = lead.species,
                                attackerLevel = lead.level,
                                attackerAbilityId = lead.abilityId,
                                attackerAbilityName = lead.ability,
                                attackerItemId = lead.itemId,
                                attackerTypes = referenceTypes,
                                attackerSpeciesId = referenceSpecies.id,
                                defenderSpecies = mon.species,
                                defenderLevel = mon.level,
                                defenderAbilityId = mon.abilityId,
                                defenderAbilityName = trainerAbilityName,
                                defenderItemId = mon.itemId,
                                defenderTypes = trainerTypes,
                                defenderSpeciesId = trainerSpecies.id,
                                move = move.name,
                                gameType = gameType,
                                battlersCount = battlersCount
                            )
                        )
                    }

                    // Direction 2: trainer Pokemon -> reference player lead.
                    for (move in movesOf(pack, mon.moves)) {
                        val reason = exclusionReason(pack, move)
                        if (reason != null) {
                            excluded += ExcludedMove(
                                trainerKey = trainer.key,
                                trainerSlot = mon.partySlot,
                                direction = DIRECTION_TRAINER_TO_REFERENCE,
                                move = move.name,
                                moveType = move.type.displayName,
                                moveCategory = move.category.displayName,
                                movePower = move.power,
                                reason = reason
                            )
                            continue
                        }
                        requests += RequestRecord(
                            key = requestKey(trainer.key, mon.partySlot, lead.teamId, DIRECTION_TRAINER_TO_REFERENCE, move.name),
                            direction = DIRECTION_TRAINER_TO_REFERENCE,
                            trainerKey = trainer.key,
                            trainerSlot = mon.partySlot,
                            referenceTeam = lead.teamId,
                            attackerSpeciesId = mon.speciesId,
                            attackerSpecies = mon.species,
                            attackerAbility = trainerAbilityName,
                            attackerItem = mon.item,
                            defenderSpeciesId = referenceSpecies.id,
                            defenderSpecies = lead.species,
                            defenderAbility = lead.ability,
                            defenderItem = lead.item,
                            move = move.name,
                            moveType = move.type.displayName,
                            moveCategory = move.category.displayName,
                            movePower = move.power,
                            gameType = gameType,
                            battlersCount = battlersCount,
                            outcome = outcomeFor(
                                profile = profile,
                                attackerSpecies = mon.species,
                                attackerLevel = mon.level,
                                attackerAbilityId = mon.abilityId,
                                attackerAbilityName = trainerAbilityName,
                                attackerItemId = mon.itemId,
                                attackerTypes = trainerTypes,
                                attackerSpeciesId = trainerSpecies.id,
                                defenderSpecies = lead.species,
                                defenderLevel = lead.level,
                                defenderAbilityId = lead.abilityId,
                                defenderAbilityName = lead.ability,
                                defenderItemId = lead.itemId,
                                defenderTypes = referenceTypes,
                                defenderSpeciesId = referenceSpecies.id,
                                move = move.name,
                                gameType = gameType,
                                battlersCount = battlersCount
                            )
                        )
                    }
                }
            }
        }

        val cohorts = operandCohorts(pack, requests)
        return CensusRun(
            trainers = trainers,
            referenceLeads = leads,
            requests = requests.sortedBy { it.key },
            excludedMoves = excluded.sortedWith(
                compareBy({ it.trainerKey }, { it.trainerSlot }, { it.direction }, { it.move })
            ),
            abilityDomain = abilityDomain,
            abilityCohorts = cohorts,
            abilityTrials = randomAbilityTrials(pack, profile, abilityDomain, cohorts),
            baselinePositiveControl = baselinePositiveControl(pack, profile)
        )
    }

    /**
     * The census' own positive control: one ordinary singles request on the neutral baseline
     * must reach a displayable tier with no blocker at all.
     *
     * If the baseline could not pass production policy, every census number would be measuring
     * a broken harness rather than the trainer data, so this is asserted instead of assumed.
     */
    fun baselinePositiveControl(pack: GameDataPack, profile: RomHackProfile): BaselinePositiveControl {
        val attacker = requireNotNull(pack.getSpeciesByName("Chikorita"))
        val defender = requireNotNull(pack.getSpeciesByName("Pidgey"))
        val single = outcomeFor(
            profile = profile,
            attackerSpecies = attacker.name,
            attackerLevel = 5,
            attackerAbilityId = 65,
            attackerAbilityName = "Overgrow",
            attackerItemId = 0,
            attackerTypes = typesOf(pack, attacker),
            attackerSpeciesId = attacker.id,
            defenderSpecies = defender.name,
            defenderLevel = 5,
            defenderAbilityId = 51,
            defenderAbilityName = "Keen Eye",
            defenderItemId = 0,
            defenderTypes = typesOf(pack, defender),
            defenderSpeciesId = defender.id,
            move = "Tackle",
            gameType = HnsCalcCensusBaseline.SINGLES_GAME_TYPE,
            battlersCount = HnsCalcCensusBaseline.SINGLES_BATTLERS_COUNT
        )
        return BaselinePositiveControl(
            attackerSpecies = attacker.name,
            defenderSpecies = defender.name,
            move = "Tackle",
            displayTier = single.tier.wireName,
            limitations = single.limitations.map { it.name }
        )
    }

    /**
     * The Random Abilities view.
     *
     * Under Random Abilities any of the pinned build's abilities can be installed on any
     * battler, so the question is not "which abilities do trainers happen to have" but "which
     * abilities does production policy block, on which side, for which move categories, and how
     * many of this census' requests would that affect".
     *
     * Every census request is assigned to an [OperandCohort]: the requests that share the exact
     * operands an ability-relevance decision reads. Each ability of the complete pinned domain is
     * then installed on each side of each cohort (replacing that side's ability and leaving the
     * other side's real ability in place, so a cohort's own ability blocker is never laundered
     * away) and decided by the real production policy. Counts scale by the cohort's own request
     * weight, so no probability weight is invented: under a uniform ability distribution the
     * ranking is exactly a count.
     */
    fun randomAbilityTrials(
        pack: GameDataPack,
        profile: RomHackProfile,
        abilityDomain: List<Pair<Int, String>>,
        cohorts: List<OperandCohort>,
        typesCache: MutableMap<Int, List<String>> = HashMap()
    ): List<AbilityTrial> {
        // Rows are aggregated by (ability, side, category) as they are produced and the affected
        // requests and battles are de-duplicated, so a cohort that appears in several contexts
        // cannot make one battle count twice. Only the ranked rows are retained: materializing
        // every (ability x side x cohort) row would be over a million objects for a report whose
        // useful resolution is the ability / side / category ranking.
        class Acc(var name: String, var relevance: String) {
            /** Cohorts are disjoint, so summing their weights counts every request exactly once. */
            var requests = 0
            var cleared = 0

            /** A trainer battle can appear in several cohorts, so battles are de-duplicated. */
            val battleKeys = LinkedHashSet<String>()

            /** Distinct blocking contexts, which is what separates a global blocker from a contextual one. */
            val rules = LinkedHashSet<String>()
        }
        val accs = LinkedHashMap<String, Acc>()
        for (cohort in cohorts) {
            val key = cohort.key
            val attackerSpecies = speciesFor(pack, key.attackerSpeciesId, key.attackerSpecies)
            val defenderSpecies = speciesFor(pack, key.defenderSpeciesId, key.defenderSpecies)
            val attackerTypes = typesCache.getOrPut(key.attackerSpeciesId) {
                typesOf(pack, attackerSpecies)
            }
            val defenderTypes = typesCache.getOrPut(key.defenderSpeciesId) {
                typesOf(pack, defenderSpecies)
            }
            val move = requireNotNull(pack.getMoveByName(key.move)) {
                "move ${key.move} is not in the pinned data pack"
            }
            for ((abilityId, abilityName) in abilityDomain) {
                for (side in listOf(SIDE_ATTACKER, SIDE_DEFENDER)) {
                    val attackerAbilityId =
                        if (side == SIDE_ATTACKER) abilityId else key.attackerAbilityId
                    val attackerAbilityName =
                        if (side == SIDE_ATTACKER) abilityName else key.attackerAbilityName
                    val defenderAbilityId =
                        if (side == SIDE_DEFENDER) abilityId else key.defenderAbilityId
                    val defenderAbilityName =
                        if (side == SIDE_DEFENDER) abilityName else key.defenderAbilityName
                    val outcome = outcomeFor(
                        profile = profile,
                        attackerSpecies = key.attackerSpecies,
                        attackerLevel = ABILITY_TRIAL_LEVEL,
                        attackerAbilityId = attackerAbilityId,
                        attackerAbilityName = attackerAbilityName,
                        attackerItemId = 0,
                        attackerTypes = attackerTypes,
                        attackerSpeciesId = attackerSpecies.id,
                        defenderSpecies = key.defenderSpecies,
                        defenderLevel = ABILITY_TRIAL_LEVEL,
                        defenderAbilityId = defenderAbilityId,
                        defenderAbilityName = defenderAbilityName,
                        defenderItemId = 0,
                        defenderTypes = defenderTypes,
                        defenderSpeciesId = defenderSpecies.id,
                        move = key.move,
                        gameType = key.gameType,
                        battlersCount = key.battlersCount
                    )
                    val decision = outcome.abilityDecisions.firstOrNull { it.side.name == side }
                    val relevance = decision?.relevance?.name
                        ?: com.dualdex.calculator.HnsAbilityRequestRelevance.UNKNOWN.name
                    // The tested ability blocks this request only when production policy actually
                    // blamed the ability and its own relevance result is not a proven clearance.
                    // An `UNKNOWN` result blocks (required evidence is missing) exactly as it does
                    // in production; a `PROVEN_IRRELEVANT` result never blocks even when the OTHER
                    // side's real ability does.
                    val blocksThisRequest = outcome.limitations.contains(
                        com.dualdex.calculator.CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED
                    ) && decision?.relevance !=
                        com.dualdex.calculator.HnsAbilityRequestRelevance.PROVEN_IRRELEVANT
                    val rankKey = "$abilityId|${side.lowercase()}|${move.category.displayName}"
                    val acc = accs.getOrPut(rankKey) { Acc(abilityName, relevance) }
                    if (blocksThisRequest) {
                        acc.requests += cohort.requestCount
                        acc.battleKeys += cohort.battles
                        acc.rules += (decision?.rule ?: "unreviewed_context")
                    }
                    if (decision?.relevance ==
                        com.dualdex.calculator.HnsAbilityRequestRelevance.PROVEN_IRRELEVANT
                    ) {
                        acc.cleared += cohort.requestCount
                        acc.rules += (decision.rule ?: "unreviewed_context")
                    }
                    if (relevanceRank(relevance) > relevanceRank(acc.relevance)) {
                        acc.relevance = relevance
                    }
                }
            }
        }
        return accs.entries
            .map { (rankKey, acc) ->
                val parts = rankKey.split("|")
                AbilityTrial(
                    abilityId = parts[0].toInt(),
                    abilityName = acc.name,
                    side = parts[1],
                    category = parts[2],
                    blockedByAbility = acc.requests > 0,
                    abilityRelevance = acc.relevance,
                    abilityRule = acc.rules.sorted().joinToString(",").ifEmpty { null },
                    clearedRequests = acc.cleared,
                    requestBlocks = acc.requests,
                    battleBlocks = acc.battleKeys.size,
                    rules = acc.rules.sorted()
                )
            }
            .sortedWith(compareBy({ it.abilityId }, { it.side }, { it.category }))
    }

    /**
     * The `RELEVANT` > `UNKNOWN` > `PROVEN_IRRELEVANT` order a rank merges its contexts with: a
     * rank that blocks in any context must never be reported as cleared.
     */
    private fun relevanceRank(relevance: String): Int = when (relevance) {
        "RELEVANT" -> 2
        "UNKNOWN" -> 1
        else -> 0
    }


    /**
     * Group the census' eligible requests into the operand cohorts the ability decision reads.
     *
     * A cohort is identified by the decision operands, so every request in it is decided
     * identically for the same ability; the cohort's weight is its own request count and the
     * distinct trainer battles it covers.
     */
    private class CohortAcc(
        val key: OperandKey,
        val requestKeys: LinkedHashSet<String>,
        val battles: LinkedHashSet<String>
    )

    fun operandCohorts(pack: GameDataPack, requests: List<RequestRecord>): List<OperandCohort> {
        val byKey = LinkedHashMap<String, CohortAcc>()
        val typeCache = HashMap<String, List<String>>()
        for (record in requests) {
            val attackerTypes = typeCache.getOrPut(record.attackerSpecies) {
                typesOf(pack, speciesFor(pack, record.attackerSpeciesId, record.attackerSpecies))
            }
            val key = OperandKey(
                direction = record.direction,
                attackerSpeciesId = record.attackerSpeciesId,
                attackerSpecies = record.attackerSpecies,
                attackerAbilityId = abilityIdOf(pack, record.attackerSpeciesId, record.attackerAbility),
                attackerAbilityName = record.attackerAbility,
                defenderSpeciesId = record.defenderSpeciesId,
                defenderSpecies = record.defenderSpecies,
                defenderAbilityId = abilityIdOf(pack, record.defenderSpeciesId, record.defenderAbility),
                defenderAbilityName = record.defenderAbility,
                move = record.move,
                moveType = record.moveType,
                moveCategory = record.moveCategory,
                stab = record.moveType in attackerTypes,
                gameType = record.gameType,
                battlersCount = record.battlersCount
            )
            val signature = key.operands + "|" + key.attackerAbilityId + "|" + key.defenderAbilityId
            val existing = byKey[signature]
            if (existing == null) {
                byKey[signature] = CohortAcc(key, linkedSetOf(record.key), linkedSetOf(record.trainerKey))
            } else {
                existing.requestKeys += record.key
                existing.battles += record.trainerKey
            }
        }
        return byKey.values
            .map { OperandCohort(it.key, it.requestKeys, it.battles) }
            .sortedWith(compareBy({ it.key.operands }, { it.key.attackerAbilityId }, { it.key.defenderAbilityId }))
    }

    /** The pinned ability ID for a participant, falling back to slot 0 of its species. */
    private fun abilityIdOf(pack: GameDataPack, speciesId: Int, ability: String?): Int {
        val info = requireNotNull(pack.getSpecies(speciesId)) {
            "species ID $speciesId is not in the pinned data pack"
        }
        if (ability != null) {
            val entry = HnsAbilityRegistry.classify(ability)
            if (entry.abilityId != null) return entry.abilityId
        }
        val declared = pack.getDeclaredAbilityForSlot(info.id, 0)
        return (declared as? DeclaredAbility.Declared)?.abilityId ?: 0
    }

    /** The level the Random Abilities trials use: a full-HP, level-50 ordinary battle. */
    const val ABILITY_TRIAL_LEVEL: Int = 50


    internal const val DIRECTION_REFERENCE_TO_TRAINER = "reference-to-trainer"
    internal const val DIRECTION_TRAINER_TO_REFERENCE = "trainer-to-reference"
    internal const val SIDE_ATTACKER = "ATTACKER"
    internal const val SIDE_DEFENDER = "DEFENDER"

    /**
     * The fitness ability used as the "not the trial" side of the Random Abilities trials.
     *
     * Overgrow (65) and Keen Eye (51) are both classified `PROVEN_NO_DAMAGE_EFFECT` by the
     * shipped ability registry, so a trial's block can only come from the ability under test.
     * The engine asserts that classification in [randomAbilityTrials]'s callers.
     */
    internal const val OVERGROW_ID = 65
    internal const val KEEN_EYE_ID = 51

    /**
     * The pinned `enum Ability` identity count the census expects, excluding the `ABILITY_NONE`
     * sentinel. Asserted so an ability-catalogue regression cannot silently shrink the Random
     * Abilities view.
     */
    const val EXPECTED_ABILITY_DOMAIN: Int = 310

    /** `NUM_ABILITY_SLOTS` from the pinned `include/constants/pokemon.h`. */
    const val DECLARED_ABILITY_SLOTS: Int = 3

    private fun requestKey(
        trainerKey: String,
        partySlot: Int,
        referenceTeam: String,
        direction: String,
        move: String
    ): String = "$trainerKey#$partySlot|$referenceTeam|$direction|$move"

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }

    /**
     * The production capability policy every request above is decided by, named here so the
     * census' own documentation and tests can assert the entry point it claims to use.
     */
    const val POLICY_ENTRY_POINT: String = "CalcCapabilityPolicy.evaluate"

    /**
     * The committed pinned trainer source this census reads.
     *
     * Pinned upstream GITIGNORES the `trainers_hns.h` it compiles (`src/data.c` includes it under
     * `IS_HNS`), so the committed `.party` file `trainerproc` generates that header from is the
     * authoritative input a checkout actually carries.
     */
    const val TRAINER_SOURCE: String = "src/data/trainers_hns.party"
}
