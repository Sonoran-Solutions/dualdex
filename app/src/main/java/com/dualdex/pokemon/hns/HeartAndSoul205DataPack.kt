package com.dualdex.pokemon.hns

import com.dualdex.pokemon.DeclaredAbility
import com.dualdex.pokemon.GameDataPack
import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.MoveInfo
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.SpeciesInfo
import com.dualdex.pokemon.TypeChart

/**
 * Exact, version-pinned GameDataPack for Pokemon Heart & Soul 2.0.5.
 *
 * Provenance:
 *   Repository: PokemonHnS-Development/pokehns-expansion
 *   Tag: Release-v2.0.5
 *   Commit: 1f42b74dff0e9fe942419845d040663dd829a973
 *   Extraction: Source / Preprocessor (arm-none-eabi-cpp) against build configuration
 *   ROM dependency: NONE (Zero commercial ROM or byte-scanning dependency)
 *
 * Total Species: 1427
 * Total Moves: 934
 * Total Abilities: 310
 * Species with declared ability slots: 1427
 *
 * Runtime-ability boundary (identity only, not effective battle state):
 *   GetAbilityBySpecies [src/pokemon.c:5548] overrides declared slot 0 with
 *   sLegendaryCustomAbilities [src/pokemon.c:5535] whenever the challenge
 *   setting tx_Mode_Legendary_Abilities is ON (default ON, [src/new_game.c:147],
 *   challenge menu item LEGEN. ABILITIES [src/challenge_menu.c:462], TAB_MODE is
 *   always unlocked [src/challenge_menu.c:183]); tx_Random_Abilities rerolls
 *   abilities entirely [src/pokemon.c:5585]. DualDex reads neither setting, so
 *   these declarations are not evidence of a live Pokemon's ability.
 *
 * DO NOT EDIT DIRECTLY. Regenerate using:
 *   python3 tools/hns-data-pack/generate_hns_data_pack.py
 */
object HeartAndSoul205DataPack : GameDataPack {
    override val id: String = "hns_2_0_5"
    override val generation: Int = 8
    override val hasFairyType: Boolean = true
    override val hasStellarType: Boolean = true
    override val hasPhysicalSpecialSplit: Boolean = true
    override val allowGlobalFallback: Boolean = false

    private val speciesMap = HashMap<Int, SpeciesInfo>()
    private val movesMap = HashMap<Int, MoveInfo>()

    /** The build's own slot layout: 2 normal slots + 1 hidden slot. */
    private const val ABILITY_SLOT_COUNT = 3

    /** Ability ID -> display name, for the catalogue lookup. */
    private val abilityNameMap = HashMap<Int, String>()

    /** Species/form ID -> declared ability ID per slot (null = ABILITY_NONE sentinel). */
    private val speciesAbilityMap = HashMap<Int, Array<Int?>>()

    init {
        registerSpeciesChunk1()
        registerSpeciesChunk2()
        registerSpeciesChunk3()
        registerSpeciesChunk4()
        registerSpeciesChunk5()
        registerSpeciesChunk6()
        registerSpeciesChunk7()
        registerSpeciesChunk8()
        registerMoveChunk1()
        registerMoveChunk2()
        registerMoveChunk3()
        registerMoveChunk4()
        registerMoveChunk5()
        registerAbilityChunk1()
        registerAbilityChunk2()
        registerSpeciesAbilityChunk1()
        registerSpeciesAbilityChunk2()
        registerSpeciesAbilityChunk3()
        registerSpeciesAbilityChunk4()
    }

    override fun getSpecies(id: Int): SpeciesInfo? = speciesMap[id]
    override fun getMove(id: Int): MoveInfo? = movesMap[id]
    override fun getEffectiveness(attackType: PokemonType, defType: PokemonType): Double {
        return TypeChart.getEffectiveness(attackType, defType, steelResistsGhostDark = false).toDouble()
    }

    override fun isSpeciesAuthoritative(id: Int): Boolean = speciesMap.containsKey(id)
    override fun isMoveAuthoritative(id: Int): Boolean = movesMap.containsKey(id)

    /**
     * The ability identity catalogue for this exact build.
     *
     * Maps this build's own numeric ability IDs (the `enum Ability` values of
     * include/constants/abilities.h) to display names from the build's
     * gAbilitiesInfo table. Slot declarations reference these IDs.
     *
     * This is identity data only: it proves that ability ID N exists in this build
     * and what the build calls it. It does NOT model any ability's battle effect,
     * and it is never evidence that the damage engine implements the ability.
     */
    fun getAbility(id: Int): DeclaredAbility =
        if (id <= 0) DeclaredAbility.Absent else abilityNameMap[id]
            ?.let { DeclaredAbility.Declared(id, it) } ?: DeclaredAbility.Absent

    /**
     * The ability the pinned build's static data declares for one ability slot of
     * one exact species/form ID.
     *
     * Slots are positional: slot 0 and 1 are the normal slots, slot 2 is the hidden
     * slot (NUM_ABILITY_SLOTS = 2 + 1, include/constants/pokemon.h:393). A declared
     * ABILITY_NONE sentinel is reported as [DeclaredAbility.EmptySlot] and the slot
     * position is always preserved, so a slot number never shifts.
     *
     * This is a declaration lookup, not a battle read: it is not proof of a live
     * Pokemon's current ability, because the build's own challenge settings (see the
     * file header) can change which ability actually applies. Unknown species, out of
     * range slots, and packs without ability declarations return
     * [DeclaredAbility.Absent] - never a substitute.
     */
    override fun getDeclaredAbilityForSlot(id: Int, slot: Int): DeclaredAbility {
        if (slot < 0 || slot >= ABILITY_SLOT_COUNT) return DeclaredAbility.Absent
        val slots = speciesAbilityMap[id] ?: return DeclaredAbility.Absent
        return when (val abilityId = slots[slot]) {
            null -> DeclaredAbility.EmptySlot
            else -> abilityNameMap[abilityId]?.let { DeclaredAbility.Declared(abilityId, it) }
                ?: DeclaredAbility.Absent
        }
    }

    /**
     * Authoritative name -> entry lookups for this exact build.
     *
     * The damage-calculator bridge selects content BY NAME, so a caller must be able to
     * prove that a name belongs to this build before the request is sent. Resolving
     * against a shared later-generation dex instead would silently compute a number from
     * another game's base stats, typing or base power.
     *
     * Matching is case-insensitive and whitespace-trimmed. Generated from the pinned
     * upstream source; returns null rather than a substitute entry.
     */
    override fun getSpeciesByName(name: String): SpeciesInfo? =
        speciesByName(name.trim().lowercase())

    override fun getMoveByName(name: String): MoveInfo? =
        movesByName(name.trim().lowercase())

    private fun speciesByName(key: String): SpeciesInfo? = when (key) {
        "abomasnow" -> speciesMap[460]
        "abra" -> speciesMap[63]
        "absol" -> speciesMap[359]
        "accelgor" -> speciesMap[617]
        "aerodactyl" -> speciesMap[142]
        "aggron" -> speciesMap[306]
        "aipom" -> speciesMap[190]
        "alakazam" -> speciesMap[65]
        "alcremie" -> speciesMap[869]
        "alomomola" -> speciesMap[594]
        "altaria" -> speciesMap[334]
        "amaura" -> speciesMap[698]
        "ambipom" -> speciesMap[424]
        "amoonguss" -> speciesMap[591]
        "ampharos" -> speciesMap[181]
        "annihilape" -> speciesMap[1370]
        "anorith" -> speciesMap[347]
        "appletun" -> speciesMap[842]
        "applin" -> speciesMap[840]
        "araquanid" -> speciesMap[752]
        "arbok" -> speciesMap[24]
        "arboliva" -> speciesMap[1315]
        "arcanine" -> speciesMap[59]
        "arcanine-h" -> speciesMap[994]
        "archaludon" -> speciesMap[1425]
        "archen" -> speciesMap[566]
        "archeops" -> speciesMap[567]
        "arctibax" -> speciesMap[1389]
        "arctovish" -> speciesMap[883]
        "arctozolt" -> speciesMap[881]
        "ariados" -> speciesMap[168]
        "armaldo" -> speciesMap[348]
        "armarouge" -> speciesMap[1324]
        "aromatisse" -> speciesMap[683]
        "aron" -> speciesMap[304]
        "arrokuda" -> speciesMap[846]
        "articuno" -> speciesMap[144]
        "articuno-g" -> speciesMap[982]
        "audino" -> speciesMap[531]
        "aurorus" -> speciesMap[699]
        "avalugg" -> speciesMap[713]
        "avalugg-h" -> speciesMap[1007]
        "axew" -> speciesMap[610]
        "azelf" -> speciesMap[482]
        "azumarill" -> speciesMap[184]
        "azurill" -> speciesMap[298]
        "bagon" -> speciesMap[371]
        "baltoy" -> speciesMap[343]
        "banette" -> speciesMap[354]
        "barbaracle" -> speciesMap[689]
        "barboach" -> speciesMap[339]
        "barraskewda" -> speciesMap[847]
        "basculin" -> speciesMap[550]
        "bastiodon" -> speciesMap[411]
        "baxcalibur" -> speciesMap[1390]
        "bayleef" -> speciesMap[153]
        "beartic" -> speciesMap[614]
        "beautifly" -> speciesMap[267]
        "beedrill" -> speciesMap[15]
        "beheeyem" -> speciesMap[606]
        "beldum" -> speciesMap[374]
        "bellibolt" -> speciesMap[1327]
        "bellossom" -> speciesMap[182]
        "bellsprout" -> speciesMap[69]
        "bergmite" -> speciesMap[712]
        "bewear" -> speciesMap[760]
        "bibarel" -> speciesMap[400]
        "bidoof" -> speciesMap[399]
        "binacle" -> speciesMap[688]
        "bisharp" -> speciesMap[625]
        "blacephalon" -> speciesMap[806]
        "blastoise" -> speciesMap[9]
        "blaziken" -> speciesMap[257]
        "blipbug" -> speciesMap[824]
        "blissey" -> speciesMap[242]
        "blitzle" -> speciesMap[522]
        "boldore" -> speciesMap[525]
        "boltund" -> speciesMap[836]
        "bombirdier" -> speciesMap[1350]
        "bonsly" -> speciesMap[438]
        "bouffalant" -> speciesMap[626]
        "bounsweet" -> speciesMap[761]
        "braixen" -> speciesMap[654]
        "brambleghast" -> speciesMap[1335]
        "bramblin" -> speciesMap[1334]
        "braviary" -> speciesMap[628]
        "braviary-h" -> speciesMap[1004]
        "breloom" -> speciesMap[286]
        "brionne" -> speciesMap[729]
        "bronzong" -> speciesMap[437]
        "bronzor" -> speciesMap[436]
        "brute bonnet" -> speciesMap[1378]
        "bruxish" -> speciesMap[779]
        "budew" -> speciesMap[406]
        "buizel" -> speciesMap[418]
        "bulbasaur" -> speciesMap[1]
        "buneary" -> speciesMap[427]
        "bunnelby" -> speciesMap[659]
        "burmy" -> speciesMap[412]
        "butterfree" -> speciesMap[12]
        "buzzwole" -> speciesMap[794]
        "cacnea" -> speciesMap[331]
        "cacturne" -> speciesMap[332]
        "calyrex" -> speciesMap[898]
        "camerupt" -> speciesMap[323]
        "capsakid" -> speciesMap[1339]
        "carbink" -> speciesMap[703]
        "carkol" -> speciesMap[838]
        "carnivine" -> speciesMap[455]
        "carracosta" -> speciesMap[565]
        "carvanha" -> speciesMap[318]
        "cascoon" -> speciesMap[268]
        "caterpie" -> speciesMap[10]
        "celebi" -> speciesMap[251]
        "celesteela" -> speciesMap[797]
        "centiskorch" -> speciesMap[851]
        "ceruledge" -> speciesMap[1325]
        "cetitan" -> speciesMap[1364]
        "cetoddle" -> speciesMap[1363]
        "chandelure" -> speciesMap[609]
        "chansey" -> speciesMap[113]
        "charcadet" -> speciesMap[1323]
        "charizard" -> speciesMap[6]
        "charjabug" -> speciesMap[737]
        "charmander" -> speciesMap[4]
        "charmeleon" -> speciesMap[5]
        "chatot" -> speciesMap[441]
        "cherrim" -> speciesMap[421]
        "cherubi" -> speciesMap[420]
        "chesnaught" -> speciesMap[652]
        "chespin" -> speciesMap[650]
        "chewtle" -> speciesMap[833]
        "chi-yu" -> speciesMap[1397]
        "chien-pao" -> speciesMap[1395]
        "chikorita" -> speciesMap[152]
        "chimchar" -> speciesMap[390]
        "chimecho" -> speciesMap[358]
        "chinchou" -> speciesMap[170]
        "chingling" -> speciesMap[433]
        "cinccino" -> speciesMap[573]
        "cinderace" -> speciesMap[815]
        "clamperl" -> speciesMap[366]
        "clauncher" -> speciesMap[692]
        "clawitzer" -> speciesMap[693]
        "claydol" -> speciesMap[344]
        "clefable" -> speciesMap[36]
        "clefairy" -> speciesMap[35]
        "cleffa" -> speciesMap[173]
        "clobbopus" -> speciesMap[852]
        "clodsire" -> speciesMap[1371]
        "cloyster" -> speciesMap[91]
        "coalossal" -> speciesMap[839]
        "cobalion" -> speciesMap[638]
        "cofagrigus" -> speciesMap[563]
        "combee" -> speciesMap[415]
        "combusken" -> speciesMap[256]
        "comfey" -> speciesMap[764]
        "conkeldurr" -> speciesMap[534]
        "copperajah" -> speciesMap[879]
        "corphish" -> speciesMap[341]
        "corsola" -> speciesMap[222]
        "corsola-g" -> speciesMap[986]
        "corviknight" -> speciesMap[823]
        "corvisquire" -> speciesMap[822]
        "cosmoem" -> speciesMap[790]
        "cosmog" -> speciesMap[789]
        "cottonee" -> speciesMap[546]
        "crabominable" -> speciesMap[740]
        "crabrawler" -> speciesMap[739]
        "cradily" -> speciesMap[346]
        "cramorant" -> speciesMap[845]
        "cranidos" -> speciesMap[408]
        "crawdaunt" -> speciesMap[342]
        "cresselia" -> speciesMap[488]
        "croagunk" -> speciesMap[453]
        "crobat" -> speciesMap[169]
        "crocalor" -> speciesMap[1293]
        "croconaw" -> speciesMap[159]
        "crustle" -> speciesMap[558]
        "cryogonal" -> speciesMap[615]
        "cubchoo" -> speciesMap[613]
        "cubone" -> speciesMap[104]
        "cufant" -> speciesMap[878]
        "cursola" -> speciesMap[864]
        "cutiefly" -> speciesMap[742]
        "cyclizar" -> speciesMap[1356]
        "cyndaquil" -> speciesMap[155]
        "dachsbun" -> speciesMap[1312]
        "darkrai" -> speciesMap[491]
        "dartrix" -> speciesMap[723]
        "darumaka" -> speciesMap[554]
        "darumaka-g" -> speciesMap[989]
        "decidueye" -> speciesMap[724]
        "decidueye-h" -> speciesMap[1008]
        "dedenne" -> speciesMap[702]
        "deerling" -> speciesMap[585]
        "deino" -> speciesMap[633]
        "delcatty" -> speciesMap[301]
        "delibird" -> speciesMap[225]
        "delphox" -> speciesMap[655]
        "dewgong" -> speciesMap[87]
        "dewott" -> speciesMap[502]
        "dewpider" -> speciesMap[751]
        "dhelmise" -> speciesMap[781]
        "diancie" -> speciesMap[719]
        "diggersby" -> speciesMap[660]
        "diglett" -> speciesMap[50]
        "diglett-a" -> speciesMap[963]
        "dipplin" -> speciesMap[1408]
        "ditto" -> speciesMap[132]
        "dodrio" -> speciesMap[85]
        "doduo" -> speciesMap[84]
        "dolliv" -> speciesMap[1314]
        "dondozo" -> speciesMap[1366]
        "donphan" -> speciesMap[232]
        "dottler" -> speciesMap[825]
        "doublade" -> speciesMap[680]
        "dracovish" -> speciesMap[882]
        "dracozolt" -> speciesMap[880]
        "dragalge" -> speciesMap[691]
        "dragapult" -> speciesMap[887]
        "dragonair" -> speciesMap[148]
        "dragonite" -> speciesMap[149]
        "drakloak" -> speciesMap[886]
        "drampa" -> speciesMap[780]
        "drapion" -> speciesMap[452]
        "dratini" -> speciesMap[147]
        "drednaw" -> speciesMap[834]
        "dreepy" -> speciesMap[885]
        "drifblim" -> speciesMap[426]
        "drifloon" -> speciesMap[425]
        "drilbur" -> speciesMap[529]
        "drizzile" -> speciesMap[817]
        "drowzee" -> speciesMap[96]
        "druddigon" -> speciesMap[621]
        "dubwool" -> speciesMap[832]
        "ducklett" -> speciesMap[580]
        "dudunsparce" -> speciesMap[1373]
        "dugtrio" -> speciesMap[51]
        "dugtrio-a" -> speciesMap[964]
        "dunsparce" -> speciesMap[206]
        "duosion" -> speciesMap[578]
        "duraludon" -> speciesMap[884]
        "durant" -> speciesMap[632]
        "dusclops" -> speciesMap[356]
        "dusknoir" -> speciesMap[477]
        "duskull" -> speciesMap[355]
        "dustox" -> speciesMap[269]
        "dwebble" -> speciesMap[557]
        "eelektrik" -> speciesMap[603]
        "eelektross" -> speciesMap[604]
        "ekans" -> speciesMap[23]
        "eldegoss" -> speciesMap[830]
        "electabuzz" -> speciesMap[125]
        "electivire" -> speciesMap[466]
        "electrike" -> speciesMap[309]
        "electrode" -> speciesMap[101]
        "electrode-h" -> speciesMap[996]
        "elekid" -> speciesMap[239]
        "elgyem" -> speciesMap[605]
        "emboar" -> speciesMap[500]
        "emolga" -> speciesMap[587]
        "empoleon" -> speciesMap[395]
        "entei" -> speciesMap[244]
        "escavalier" -> speciesMap[589]
        "espathra" -> speciesMap[1344]
        "espeon" -> speciesMap[196]
        "espurr" -> speciesMap[677]
        "excadrill" -> speciesMap[530]
        "exeggcute" -> speciesMap[102]
        "exeggutor" -> speciesMap[103]
        "exeggutor-a" -> speciesMap[972]
        "exploud" -> speciesMap[295]
        "falinks" -> speciesMap[870]
        "farfetch'd" -> speciesMap[83]
        "farfetch'd-g" -> speciesMap[979]
        "farigiraf" -> speciesMap[1372]
        "fearow" -> speciesMap[22]
        "feebas" -> speciesMap[349]
        "fennekin" -> speciesMap[653]
        "feraligatr" -> speciesMap[160]
        "ferroseed" -> speciesMap[597]
        "ferrothorn" -> speciesMap[598]
        "fezandipiti" -> speciesMap[1415]
        "fidough" -> speciesMap[1311]
        "finizen" -> speciesMap[1351]
        "finneon" -> speciesMap[456]
        "flaaffy" -> speciesMap[180]
        "flabébé" -> speciesMap[669]
        "flamigo" -> speciesMap[1362]
        "flapple" -> speciesMap[841]
        "flareon" -> speciesMap[136]
        "fletchinder" -> speciesMap[662]
        "fletchling" -> speciesMap[661]
        "flittle" -> speciesMap[1343]
        "floatzel" -> speciesMap[419]
        "floragato" -> speciesMap[1290]
        "florges" -> speciesMap[671]
        "flutter mane" -> speciesMap[1379]
        "flygon" -> speciesMap[330]
        "fomantis" -> speciesMap[753]
        "foongus" -> speciesMap[590]
        "forretress" -> speciesMap[205]
        "fraxure" -> speciesMap[611]
        "frigibax" -> speciesMap[1388]
        "frillish" -> speciesMap[592]
        "froakie" -> speciesMap[656]
        "frogadier" -> speciesMap[657]
        "froslass" -> speciesMap[478]
        "frosmoth" -> speciesMap[873]
        "fuecoco" -> speciesMap[1292]
        "furfrou" -> speciesMap[676]
        "furret" -> speciesMap[162]
        "gabite" -> speciesMap[444]
        "gallade" -> speciesMap[475]
        "galvantula" -> speciesMap[596]
        "garbodor" -> speciesMap[569]
        "garchomp" -> speciesMap[445]
        "gardevoir" -> speciesMap[282]
        "garganacl" -> speciesMap[1322]
        "gastly" -> speciesMap[92]
        "gastrodon" -> speciesMap[423]
        "genesect" -> speciesMap[649]
        "gengar" -> speciesMap[94]
        "geodude" -> speciesMap[74]
        "geodude-a" -> speciesMap[967]
        "gholdengo" -> speciesMap[1393]
        "gible" -> speciesMap[443]
        "gigalith" -> speciesMap[526]
        "girafarig" -> speciesMap[203]
        "glaceon" -> speciesMap[471]
        "glalie" -> speciesMap[362]
        "glameow" -> speciesMap[431]
        "glastrier" -> speciesMap[896]
        "gligar" -> speciesMap[207]
        "glimmet" -> speciesMap[1358]
        "glimmora" -> speciesMap[1359]
        "gliscor" -> speciesMap[472]
        "gloom" -> speciesMap[44]
        "gogoat" -> speciesMap[673]
        "golbat" -> speciesMap[42]
        "goldeen" -> speciesMap[118]
        "golduck" -> speciesMap[55]
        "golem" -> speciesMap[76]
        "golem-a" -> speciesMap[969]
        "golett" -> speciesMap[622]
        "golisopod" -> speciesMap[768]
        "golurk" -> speciesMap[623]
        "goodra" -> speciesMap[706]
        "goodra-h" -> speciesMap[1006]
        "goomy" -> speciesMap[704]
        "gorebyss" -> speciesMap[368]
        "gossifleur" -> speciesMap[829]
        "gothita" -> speciesMap[574]
        "gothitelle" -> speciesMap[576]
        "gothorita" -> speciesMap[575]
        "gouging fire" -> speciesMap[1427]
        "grafaiai" -> speciesMap[1333]
        "granbull" -> speciesMap[210]
        "grapploct" -> speciesMap[853]
        "graveler" -> speciesMap[75]
        "graveler-a" -> speciesMap[968]
        "great tusk" -> speciesMap[1376]
        "greavard" -> speciesMap[1360]
        "greedent" -> speciesMap[820]
        "grimer" -> speciesMap[88]
        "grimer-a" -> speciesMap[970]
        "grimmsnarl" -> speciesMap[861]
        "grookey" -> speciesMap[810]
        "grotle" -> speciesMap[388]
        "groudon" -> speciesMap[383]
        "grovyle" -> speciesMap[253]
        "growlithe" -> speciesMap[58]
        "growlithe-h" -> speciesMap[993]
        "grubbin" -> speciesMap[736]
        "grumpig" -> speciesMap[326]
        "gulpin" -> speciesMap[316]
        "gumshoos" -> speciesMap[735]
        "gurdurr" -> speciesMap[533]
        "guzzlord" -> speciesMap[799]
        "gyarados" -> speciesMap[130]
        "hakamo-o" -> speciesMap[783]
        "happiny" -> speciesMap[440]
        "hariyama" -> speciesMap[297]
        "hatenna" -> speciesMap[856]
        "hatterene" -> speciesMap[858]
        "hattrem" -> speciesMap[857]
        "haunter" -> speciesMap[93]
        "hawlucha" -> speciesMap[701]
        "haxorus" -> speciesMap[612]
        "heatmor" -> speciesMap[631]
        "heatran" -> speciesMap[485]
        "heliolisk" -> speciesMap[695]
        "helioptile" -> speciesMap[694]
        "heracross" -> speciesMap[214]
        "herdier" -> speciesMap[507]
        "hippopotas" -> speciesMap[449]
        "hippowdon" -> speciesMap[450]
        "hitmonchan" -> speciesMap[107]
        "hitmonlee" -> speciesMap[106]
        "hitmontop" -> speciesMap[237]
        "ho-oh" -> speciesMap[250]
        "honchkrow" -> speciesMap[430]
        "honedge" -> speciesMap[679]
        "hoothoot" -> speciesMap[163]
        "hoppip" -> speciesMap[187]
        "horsea" -> speciesMap[116]
        "houndoom" -> speciesMap[229]
        "houndour" -> speciesMap[228]
        "houndstone" -> speciesMap[1361]
        "huntail" -> speciesMap[367]
        "hydrapple" -> speciesMap[1426]
        "hydreigon" -> speciesMap[635]
        "hypno" -> speciesMap[97]
        "igglybuff" -> speciesMap[174]
        "illumise" -> speciesMap[314]
        "impidimp" -> speciesMap[859]
        "incineroar" -> speciesMap[727]
        "infernape" -> speciesMap[392]
        "inkay" -> speciesMap[686]
        "inteleon" -> speciesMap[818]
        "iron boulder" -> speciesMap[1429]
        "iron bundle" -> speciesMap[1383]
        "iron crown" -> speciesMap[1430]
        "iron hands" -> speciesMap[1384]
        "iron jugulis" -> speciesMap[1385]
        "iron leaves" -> speciesMap[1407]
        "iron moth" -> speciesMap[1386]
        "iron thorns" -> speciesMap[1387]
        "iron treads" -> speciesMap[1382]
        "iron valiant" -> speciesMap[1399]
        "ivysaur" -> speciesMap[2]
        "jangmo-o" -> speciesMap[782]
        "jellicent" -> speciesMap[593]
        "jigglypuff" -> speciesMap[39]
        "jirachi" -> speciesMap[385]
        "jolteon" -> speciesMap[135]
        "joltik" -> speciesMap[595]
        "jumpluff" -> speciesMap[189]
        "jynx" -> speciesMap[124]
        "kabuto" -> speciesMap[140]
        "kabutops" -> speciesMap[141]
        "kadabra" -> speciesMap[64]
        "kakuna" -> speciesMap[14]
        "kangaskhan" -> speciesMap[115]
        "karrablast" -> speciesMap[588]
        "kartana" -> speciesMap[798]
        "kecleon" -> speciesMap[352]
        "keldeo" -> speciesMap[647]
        "kilowattrel" -> speciesMap[1329]
        "kingambit" -> speciesMap[1375]
        "kingdra" -> speciesMap[230]
        "kingler" -> speciesMap[99]
        "kirlia" -> speciesMap[281]
        "klang" -> speciesMap[600]
        "klawf" -> speciesMap[1338]
        "kleavor" -> speciesMap[900]
        "klefki" -> speciesMap[707]
        "klink" -> speciesMap[599]
        "klinklang" -> speciesMap[601]
        "koffing" -> speciesMap[109]
        "komala" -> speciesMap[775]
        "kommo-o" -> speciesMap[784]
        "koraidon" -> speciesMap[1400]
        "krabby" -> speciesMap[98]
        "kricketot" -> speciesMap[401]
        "kricketune" -> speciesMap[402]
        "krokorok" -> speciesMap[552]
        "krookodile" -> speciesMap[553]
        "kubfu" -> speciesMap[891]
        "kyogre" -> speciesMap[382]
        "kyurem" -> speciesMap[646]
        "lairon" -> speciesMap[305]
        "lampent" -> speciesMap[608]
        "lanturn" -> speciesMap[171]
        "lapras" -> speciesMap[131]
        "larvesta" -> speciesMap[636]
        "larvitar" -> speciesMap[246]
        "latias" -> speciesMap[380]
        "latios" -> speciesMap[381]
        "leafeon" -> speciesMap[470]
        "leavanny" -> speciesMap[542]
        "lechonk" -> speciesMap[1298]
        "ledian" -> speciesMap[166]
        "ledyba" -> speciesMap[165]
        "lickilicky" -> speciesMap[463]
        "lickitung" -> speciesMap[108]
        "liepard" -> speciesMap[510]
        "lileep" -> speciesMap[345]
        "lilligant" -> speciesMap[549]
        "lilligant-h" -> speciesMap[1001]
        "lillipup" -> speciesMap[506]
        "linoone" -> speciesMap[264]
        "linoone-g" -> speciesMap[988]
        "litleo" -> speciesMap[667]
        "litten" -> speciesMap[725]
        "litwick" -> speciesMap[607]
        "lokix" -> speciesMap[1304]
        "lombre" -> speciesMap[271]
        "lopunny" -> speciesMap[428]
        "lotad" -> speciesMap[270]
        "loudred" -> speciesMap[294]
        "lucario" -> speciesMap[448]
        "ludicolo" -> speciesMap[272]
        "lugia" -> speciesMap[249]
        "lumineon" -> speciesMap[457]
        "lunala" -> speciesMap[792]
        "lunatone" -> speciesMap[337]
        "lurantis" -> speciesMap[754]
        "luvdisc" -> speciesMap[370]
        "luxio" -> speciesMap[404]
        "luxray" -> speciesMap[405]
        "mabosstiff" -> speciesMap[1331]
        "machamp" -> speciesMap[68]
        "machoke" -> speciesMap[67]
        "machop" -> speciesMap[66]
        "magby" -> speciesMap[240]
        "magcargo" -> speciesMap[219]
        "magearna" -> speciesMap[801]
        "magikarp" -> speciesMap[129]
        "magmar" -> speciesMap[126]
        "magmortar" -> speciesMap[467]
        "magnemite" -> speciesMap[81]
        "magneton" -> speciesMap[82]
        "magnezone" -> speciesMap[462]
        "makuhita" -> speciesMap[296]
        "malamar" -> speciesMap[687]
        "mamoswine" -> speciesMap[473]
        "manaphy" -> speciesMap[490]
        "mandibuzz" -> speciesMap[630]
        "manectric" -> speciesMap[310]
        "mankey" -> speciesMap[56]
        "mantine" -> speciesMap[226]
        "mantyke" -> speciesMap[458]
        "maractus" -> speciesMap[556]
        "mareanie" -> speciesMap[747]
        "mareep" -> speciesMap[179]
        "marill" -> speciesMap[183]
        "marowak" -> speciesMap[105]
        "marowak-a" -> speciesMap[973]
        "marshadow" -> speciesMap[802]
        "marshtomp" -> speciesMap[259]
        "maschiff" -> speciesMap[1330]
        "masquerain" -> speciesMap[284]
        "maushold" -> speciesMap[1309]
        "mawile" -> speciesMap[303]
        "medicham" -> speciesMap[308]
        "meditite" -> speciesMap[307]
        "meganium" -> speciesMap[154]
        "melmetal" -> speciesMap[809]
        "meltan" -> speciesMap[808]
        "meowscarada" -> speciesMap[1291]
        "meowstic" -> speciesMap[678]
        "meowth" -> speciesMap[52]
        "meowth-a" -> speciesMap[965]
        "meowth-g" -> speciesMap[974]
        "mesprit" -> speciesMap[481]
        "metagross" -> speciesMap[376]
        "metang" -> speciesMap[375]
        "metapod" -> speciesMap[11]
        "mew" -> speciesMap[151]
        "mewtwo" -> speciesMap[150]
        "mienfoo" -> speciesMap[619]
        "mienshao" -> speciesMap[620]
        "mightyena" -> speciesMap[262]
        "milcery" -> speciesMap[868]
        "milotic" -> speciesMap[350]
        "miltank" -> speciesMap[241]
        "mime jr." -> speciesMap[439]
        "mimikyu" -> speciesMap[778]
        "minccino" -> speciesMap[572]
        "minun" -> speciesMap[312]
        "miraidon" -> speciesMap[1401]
        "misdreavus" -> speciesMap[200]
        "mismagius" -> speciesMap[429]
        "moltres" -> speciesMap[146]
        "moltres-g" -> speciesMap[984]
        "monferno" -> speciesMap[391]
        "morelull" -> speciesMap[755]
        "morgrem" -> speciesMap[860]
        "morpeko" -> speciesMap[877]
        "mothim" -> speciesMap[414]
        "mr. mime" -> speciesMap[122]
        "mr. mime-g" -> speciesMap[981]
        "mr. rime" -> speciesMap[866]
        "mudbray" -> speciesMap[749]
        "mudkip" -> speciesMap[258]
        "mudsdale" -> speciesMap[750]
        "muk" -> speciesMap[89]
        "muk-a" -> speciesMap[971]
        "munchlax" -> speciesMap[446]
        "munkidori" -> speciesMap[1414]
        "munna" -> speciesMap[517]
        "murkrow" -> speciesMap[198]
        "musharna" -> speciesMap[518]
        "nacli" -> speciesMap[1320]
        "naclstack" -> speciesMap[1321]
        "naganadel" -> speciesMap[804]
        "natu" -> speciesMap[177]
        "necrozma" -> speciesMap[800]
        "nickit" -> speciesMap[827]
        "nidoking" -> speciesMap[34]
        "nidoqueen" -> speciesMap[31]
        "nidoran♀" -> speciesMap[29]
        "nidoran♂" -> speciesMap[32]
        "nidorina" -> speciesMap[30]
        "nidorino" -> speciesMap[33]
        "nihilego" -> speciesMap[793]
        "nincada" -> speciesMap[290]
        "ninetales" -> speciesMap[38]
        "ninetales-a" -> speciesMap[962]
        "ninjask" -> speciesMap[291]
        "noctowl" -> speciesMap[164]
        "noibat" -> speciesMap[714]
        "noivern" -> speciesMap[715]
        "nosepass" -> speciesMap[299]
        "numel" -> speciesMap[322]
        "nuzleaf" -> speciesMap[274]
        "nymble" -> speciesMap[1303]
        "obstagoon" -> speciesMap[862]
        "octillery" -> speciesMap[224]
        "oddish" -> speciesMap[43]
        "okidogi" -> speciesMap[1413]
        "omanyte" -> speciesMap[138]
        "omastar" -> speciesMap[139]
        "onix" -> speciesMap[95]
        "oranguru" -> speciesMap[765]
        "orbeetle" -> speciesMap[826]
        "orthworm" -> speciesMap[1357]
        "oshawott" -> speciesMap[501]
        "overqwil" -> speciesMap[904]
        "pachirisu" -> speciesMap[417]
        "palossand" -> speciesMap[770]
        "palpitoad" -> speciesMap[536]
        "pancham" -> speciesMap[674]
        "pangoro" -> speciesMap[675]
        "panpour" -> speciesMap[515]
        "pansage" -> speciesMap[511]
        "pansear" -> speciesMap[513]
        "paras" -> speciesMap[46]
        "parasect" -> speciesMap[47]
        "passimian" -> speciesMap[766]
        "patrat" -> speciesMap[504]
        "pawmi" -> speciesMap[1305]
        "pawmo" -> speciesMap[1306]
        "pawmot" -> speciesMap[1307]
        "pawniard" -> speciesMap[624]
        "pecharunt" -> speciesMap[1434]
        "pelipper" -> speciesMap[279]
        "perrserker" -> speciesMap[863]
        "persian" -> speciesMap[53]
        "persian-a" -> speciesMap[966]
        "petilil" -> speciesMap[548]
        "phanpy" -> speciesMap[231]
        "phantump" -> speciesMap[708]
        "pheromosa" -> speciesMap[795]
        "phione" -> speciesMap[489]
        "pichu" -> speciesMap[172]
        "pidgeot" -> speciesMap[18]
        "pidgeotto" -> speciesMap[17]
        "pidgey" -> speciesMap[16]
        "pidove" -> speciesMap[519]
        "pignite" -> speciesMap[499]
        "pikipek" -> speciesMap[731]
        "piloswine" -> speciesMap[221]
        "pincurchin" -> speciesMap[871]
        "pineco" -> speciesMap[204]
        "pinsir" -> speciesMap[127]
        "piplup" -> speciesMap[393]
        "plusle" -> speciesMap[311]
        "poipole" -> speciesMap[803]
        "politoed" -> speciesMap[186]
        "poliwag" -> speciesMap[60]
        "poliwhirl" -> speciesMap[61]
        "poliwrath" -> speciesMap[62]
        "poltchageist" -> speciesMap[1409]
        "polteageist" -> speciesMap[855]
        "ponyta" -> speciesMap[77]
        "ponyta-g" -> speciesMap[975]
        "poochyena" -> speciesMap[261]
        "popplio" -> speciesMap[728]
        "porygon" -> speciesMap[137]
        "porygon-z" -> speciesMap[474]
        "porygon2" -> speciesMap[233]
        "primarina" -> speciesMap[730]
        "primeape" -> speciesMap[57]
        "prinplup" -> speciesMap[394]
        "probopass" -> speciesMap[476]
        "psyduck" -> speciesMap[54]
        "pupitar" -> speciesMap[247]
        "purrloin" -> speciesMap[509]
        "purugly" -> speciesMap[432]
        "pyroar" -> speciesMap[668]
        "pyukumuku" -> speciesMap[771]
        "quagsire" -> speciesMap[195]
        "quaquaval" -> speciesMap[1297]
        "quaxly" -> speciesMap[1295]
        "quaxwell" -> speciesMap[1296]
        "quilava" -> speciesMap[156]
        "quilladin" -> speciesMap[651]
        "qwilfish" -> speciesMap[211]
        "qwilfish-h" -> speciesMap[998]
        "raboot" -> speciesMap[814]
        "rabsca" -> speciesMap[1342]
        "raging bolt" -> speciesMap[1428]
        "raichu" -> speciesMap[26]
        "raichu-a" -> speciesMap[958]
        "raikou" -> speciesMap[243]
        "ralts" -> speciesMap[280]
        "rampardos" -> speciesMap[409]
        "rapidash" -> speciesMap[78]
        "rapidash-g" -> speciesMap[976]
        "raticate" -> speciesMap[20]
        "raticate-a" -> speciesMap[957]
        "rattata" -> speciesMap[19]
        "rattata-a" -> speciesMap[956]
        "rayquaza" -> speciesMap[384]
        "regice" -> speciesMap[378]
        "regidrago" -> speciesMap[895]
        "regieleki" -> speciesMap[894]
        "regigigas" -> speciesMap[486]
        "regirock" -> speciesMap[377]
        "registeel" -> speciesMap[379]
        "relicanth" -> speciesMap[369]
        "rellor" -> speciesMap[1341]
        "remoraid" -> speciesMap[223]
        "reshiram" -> speciesMap[643]
        "reuniclus" -> speciesMap[579]
        "revavroom" -> speciesMap[1355]
        "rhydon" -> speciesMap[112]
        "rhyhorn" -> speciesMap[111]
        "rhyperior" -> speciesMap[464]
        "ribombee" -> speciesMap[743]
        "rillaboom" -> speciesMap[812]
        "riolu" -> speciesMap[447]
        "roaring moon" -> speciesMap[1398]
        "rockruff" -> speciesMap[744]
        "roggenrola" -> speciesMap[524]
        "rolycoly" -> speciesMap[837]
        "rookidee" -> speciesMap[821]
        "roselia" -> speciesMap[315]
        "roserade" -> speciesMap[407]
        "rowlet" -> speciesMap[722]
        "rufflet" -> speciesMap[627]
        "runerigus" -> speciesMap[867]
        "sableye" -> speciesMap[302]
        "salamence" -> speciesMap[373]
        "salandit" -> speciesMap[757]
        "salazzle" -> speciesMap[758]
        "samurott" -> speciesMap[503]
        "samurott-h" -> speciesMap[1000]
        "sandaconda" -> speciesMap[844]
        "sandile" -> speciesMap[551]
        "sandshrew" -> speciesMap[27]
        "sandshrew-a" -> speciesMap[959]
        "sandslash" -> speciesMap[28]
        "sandslash-a" -> speciesMap[960]
        "sandy shocks" -> speciesMap[1381]
        "sandygast" -> speciesMap[769]
        "sawk" -> speciesMap[539]
        "sawsbuck" -> speciesMap[586]
        "scatterbug" -> speciesMap[664]
        "sceptile" -> speciesMap[254]
        "scizor" -> speciesMap[212]
        "scolipede" -> speciesMap[545]
        "scorbunny" -> speciesMap[813]
        "scovillain" -> speciesMap[1340]
        "scrafty" -> speciesMap[560]
        "scraggy" -> speciesMap[559]
        "scream tail" -> speciesMap[1377]
        "scyther" -> speciesMap[123]
        "seadra" -> speciesMap[117]
        "seaking" -> speciesMap[119]
        "sealeo" -> speciesMap[364]
        "seedot" -> speciesMap[273]
        "seel" -> speciesMap[86]
        "seismitoad" -> speciesMap[537]
        "sentret" -> speciesMap[161]
        "serperior" -> speciesMap[497]
        "servine" -> speciesMap[496]
        "seviper" -> speciesMap[336]
        "sewaddle" -> speciesMap[540]
        "sharpedo" -> speciesMap[319]
        "shedinja" -> speciesMap[292]
        "shelgon" -> speciesMap[372]
        "shellder" -> speciesMap[90]
        "shellos" -> speciesMap[422]
        "shelmet" -> speciesMap[616]
        "shieldon" -> speciesMap[410]
        "shiftry" -> speciesMap[275]
        "shiinotic" -> speciesMap[756]
        "shinx" -> speciesMap[403]
        "shroodle" -> speciesMap[1332]
        "shroomish" -> speciesMap[285]
        "shuckle" -> speciesMap[213]
        "shuppet" -> speciesMap[353]
        "sigilyph" -> speciesMap[561]
        "silcoon" -> speciesMap[266]
        "silicobra" -> speciesMap[843]
        "simipour" -> speciesMap[516]
        "simisage" -> speciesMap[512]
        "simisear" -> speciesMap[514]
        "sinistcha" -> speciesMap[1411]
        "sinistea" -> speciesMap[854]
        "sirfetch'd" -> speciesMap[865]
        "sizzlipede" -> speciesMap[850]
        "skarmory" -> speciesMap[227]
        "skeledirge" -> speciesMap[1294]
        "skiddo" -> speciesMap[672]
        "skiploom" -> speciesMap[188]
        "skitty" -> speciesMap[300]
        "skorupi" -> speciesMap[451]
        "skrelp" -> speciesMap[690]
        "skuntank" -> speciesMap[435]
        "skwovet" -> speciesMap[819]
        "slaking" -> speciesMap[289]
        "slakoth" -> speciesMap[287]
        "sliggoo" -> speciesMap[705]
        "sliggoo-h" -> speciesMap[1005]
        "slither wing" -> speciesMap[1380]
        "slowbro" -> speciesMap[80]
        "slowbro-g" -> speciesMap[978]
        "slowking" -> speciesMap[199]
        "slowking-g" -> speciesMap[985]
        "slowpoke" -> speciesMap[79]
        "slowpoke-g" -> speciesMap[977]
        "slugma" -> speciesMap[218]
        "slurpuff" -> speciesMap[685]
        "smeargle" -> speciesMap[235]
        "smoliv" -> speciesMap[1313]
        "smoochum" -> speciesMap[238]
        "sneasel" -> speciesMap[215]
        "sneasel-h" -> speciesMap[999]
        "sneasler" -> speciesMap[903]
        "snivy" -> speciesMap[495]
        "snom" -> speciesMap[872]
        "snorlax" -> speciesMap[143]
        "snorunt" -> speciesMap[361]
        "snover" -> speciesMap[459]
        "snubbull" -> speciesMap[209]
        "sobble" -> speciesMap[816]
        "solgaleo" -> speciesMap[791]
        "solosis" -> speciesMap[577]
        "solrock" -> speciesMap[338]
        "spearow" -> speciesMap[21]
        "spectrier" -> speciesMap[897]
        "spewpa" -> speciesMap[665]
        "spheal" -> speciesMap[363]
        "spidops" -> speciesMap[1302]
        "spinarak" -> speciesMap[167]
        "spinda" -> speciesMap[327]
        "spiritomb" -> speciesMap[442]
        "spoink" -> speciesMap[325]
        "sprigatito" -> speciesMap[1289]
        "spritzee" -> speciesMap[682]
        "squawkabilly" -> speciesMap[1316]
        "squirtle" -> speciesMap[7]
        "stakataka" -> speciesMap[805]
        "stantler" -> speciesMap[234]
        "staraptor" -> speciesMap[398]
        "staravia" -> speciesMap[397]
        "starly" -> speciesMap[396]
        "starmie" -> speciesMap[121]
        "staryu" -> speciesMap[120]
        "steelix" -> speciesMap[208]
        "steenee" -> speciesMap[762]
        "stonjourner" -> speciesMap[874]
        "stoutland" -> speciesMap[508]
        "stufful" -> speciesMap[759]
        "stunfisk" -> speciesMap[618]
        "stunfisk-g" -> speciesMap[992]
        "stunky" -> speciesMap[434]
        "sudowoodo" -> speciesMap[185]
        "suicune" -> speciesMap[245]
        "sunflora" -> speciesMap[192]
        "sunkern" -> speciesMap[191]
        "surskit" -> speciesMap[283]
        "swablu" -> speciesMap[333]
        "swadloon" -> speciesMap[541]
        "swalot" -> speciesMap[317]
        "swampert" -> speciesMap[260]
        "swanna" -> speciesMap[581]
        "swellow" -> speciesMap[277]
        "swinub" -> speciesMap[220]
        "swirlix" -> speciesMap[684]
        "swoobat" -> speciesMap[528]
        "sylveon" -> speciesMap[700]
        "tadbulb" -> speciesMap[1326]
        "taillow" -> speciesMap[276]
        "talonflame" -> speciesMap[663]
        "tandemaus" -> speciesMap[1308]
        "tangela" -> speciesMap[114]
        "tangrowth" -> speciesMap[465]
        "tapu bulu" -> speciesMap[787]
        "tapu fini" -> speciesMap[788]
        "tapu koko" -> speciesMap[785]
        "tapu lele" -> speciesMap[786]
        "tarountula" -> speciesMap[1301]
        "tatsugiri" -> speciesMap[1367]
        "tauros" -> speciesMap[128]
        "teddiursa" -> speciesMap[216]
        "tentacool" -> speciesMap[72]
        "tentacruel" -> speciesMap[73]
        "tepig" -> speciesMap[498]
        "terrakion" -> speciesMap[639]
        "thievul" -> speciesMap[828]
        "throh" -> speciesMap[538]
        "thwackey" -> speciesMap[811]
        "timburr" -> speciesMap[532]
        "ting-lu" -> speciesMap[1396]
        "tinkatink" -> speciesMap[1345]
        "tinkaton" -> speciesMap[1347]
        "tinkatuff" -> speciesMap[1346]
        "tirtouga" -> speciesMap[564]
        "toedscool" -> speciesMap[1336]
        "toedscruel" -> speciesMap[1337]
        "togedemaru" -> speciesMap[777]
        "togekiss" -> speciesMap[468]
        "togepi" -> speciesMap[175]
        "togetic" -> speciesMap[176]
        "torchic" -> speciesMap[255]
        "torkoal" -> speciesMap[324]
        "torracat" -> speciesMap[726]
        "torterra" -> speciesMap[389]
        "totodile" -> speciesMap[158]
        "toucannon" -> speciesMap[733]
        "toxapex" -> speciesMap[748]
        "toxel" -> speciesMap[848]
        "toxicroak" -> speciesMap[454]
        "toxtricity" -> speciesMap[849]
        "tranquill" -> speciesMap[520]
        "trapinch" -> speciesMap[328]
        "treecko" -> speciesMap[252]
        "trevenant" -> speciesMap[709]
        "tropius" -> speciesMap[357]
        "trubbish" -> speciesMap[568]
        "trumbeak" -> speciesMap[732]
        "tsareena" -> speciesMap[763]
        "turtonator" -> speciesMap[776]
        "turtwig" -> speciesMap[387]
        "tympole" -> speciesMap[535]
        "tynamo" -> speciesMap[602]
        "type: null" -> speciesMap[772]
        "typhlosion" -> speciesMap[157]
        "typhlosion-h" -> speciesMap[997]
        "tyranitar" -> speciesMap[248]
        "tyrantrum" -> speciesMap[697]
        "tyrogue" -> speciesMap[236]
        "tyrunt" -> speciesMap[696]
        "umbreon" -> speciesMap[197]
        "unfezant" -> speciesMap[521]
        "unown" -> speciesMap[201]
        "ursaring" -> speciesMap[217]
        "uxie" -> speciesMap[480]
        "vanillish" -> speciesMap[583]
        "vanillite" -> speciesMap[582]
        "vanilluxe" -> speciesMap[584]
        "vaporeon" -> speciesMap[134]
        "varoom" -> speciesMap[1354]
        "veluza" -> speciesMap[1365]
        "venipede" -> speciesMap[543]
        "venomoth" -> speciesMap[49]
        "venonat" -> speciesMap[48]
        "venusaur" -> speciesMap[3]
        "vespiquen" -> speciesMap[416]
        "vibrava" -> speciesMap[329]
        "victini" -> speciesMap[494]
        "victreebel" -> speciesMap[71]
        "vigoroth" -> speciesMap[288]
        "vikavolt" -> speciesMap[738]
        "vileplume" -> speciesMap[45]
        "virizion" -> speciesMap[640]
        "vivillon" -> speciesMap[666]
        "volbeat" -> speciesMap[313]
        "volcanion" -> speciesMap[721]
        "volcarona" -> speciesMap[637]
        "voltorb" -> speciesMap[100]
        "voltorb-h" -> speciesMap[995]
        "vullaby" -> speciesMap[629]
        "vulpix" -> speciesMap[37]
        "vulpix-a" -> speciesMap[961]
        "wailmer" -> speciesMap[320]
        "wailord" -> speciesMap[321]
        "walking wake" -> speciesMap[1406]
        "walrein" -> speciesMap[365]
        "wartortle" -> speciesMap[8]
        "watchog" -> speciesMap[505]
        "wattrel" -> speciesMap[1328]
        "weavile" -> speciesMap[461]
        "weedle" -> speciesMap[13]
        "weepinbell" -> speciesMap[70]
        "weezing" -> speciesMap[110]
        "weezing-g" -> speciesMap[980]
        "whimsicott" -> speciesMap[547]
        "whirlipede" -> speciesMap[544]
        "whiscash" -> speciesMap[340]
        "whismur" -> speciesMap[293]
        "wigglytuff" -> speciesMap[40]
        "wiglett" -> speciesMap[1348]
        "wimpod" -> speciesMap[767]
        "wingull" -> speciesMap[278]
        "wo-chien" -> speciesMap[1394]
        "wobbuffet" -> speciesMap[202]
        "woobat" -> speciesMap[527]
        "wooloo" -> speciesMap[831]
        "wooper" -> speciesMap[194]
        "wooper-p" -> speciesMap[1405]
        "wugtrio" -> speciesMap[1349]
        "wurmple" -> speciesMap[265]
        "wynaut" -> speciesMap[360]
        "wyrdeer" -> speciesMap[899]
        "xatu" -> speciesMap[178]
        "xerneas" -> speciesMap[716]
        "xurkitree" -> speciesMap[796]
        "yamask" -> speciesMap[562]
        "yamask-g" -> speciesMap[991]
        "yamper" -> speciesMap[835]
        "yanma" -> speciesMap[193]
        "yanmega" -> speciesMap[469]
        "yungoos" -> speciesMap[734]
        "yveltal" -> speciesMap[717]
        "zangoose" -> speciesMap[335]
        "zapdos" -> speciesMap[145]
        "zapdos-g" -> speciesMap[983]
        "zarude" -> speciesMap[893]
        "zebstrika" -> speciesMap[523]
        "zekrom" -> speciesMap[644]
        "zeraora" -> speciesMap[807]
        "zigzagoon" -> speciesMap[263]
        "zigzagoon-g" -> speciesMap[987]
        "zoroark" -> speciesMap[571]
        "zoroark-h" -> speciesMap[1003]
        "zorua" -> speciesMap[570]
        "zorua-h" -> speciesMap[1002]
        "zubat" -> speciesMap[41]
        "zweilous" -> speciesMap[634]
        else -> null
    }

    /**
     * Names that map to more than one form with DIFFERENT types or base stats.
     *
     * A name-only calculation request cannot say which form is meant, so these names are
     * deliberately absent from [speciesByName] and resolve to null. That is what makes
     * the calculator boundary refuse them instead of computing one form's damage for
     * another form's species name.
     */
    val multiFormNames: Set<String> = setOf(
        "aegislash", // ids 681, 1156
        "arceus", // ids 493, 1073, 1074, 1075, 1076, 1077, 1078, 1079, 1080, 1081, 1082, 1083, 1084, 1085, 1086, 1087, 1088, 1089
        "basculegion", // ids 902, 1234
        "castform", // ids 351, 1051, 1052, 1053
        "darmanitan", // ids 555, 1092
        "darmanitan-g", // ids 990, 1093
        "deoxys", // ids 386, 1054, 1055, 1056
        "dialga", // ids 483, 1069
        "eevee", // ids 133, 1488
        "eiscue", // ids 875, 1224
        "enamorus", // ids 905, 1103
        "eternatus", // ids 890, 1229
        "floette", // ids 670, 1137, 1138, 1139, 1140, 1141
        "gimmighoul", // ids 1391, 1392
        "giratina", // ids 487, 1071
        "gourgeist", // ids 711, 1160, 1161, 1162
        "greninja", // ids 658, 1112, 1113
        "hoopa", // ids 720, 1168
        "indeedee", // ids 876, 1225
        "landorus", // ids 645, 1102
        "lycanroc", // ids 745, 1173, 1174
        "meloetta", // ids 648, 1107
        "minior", // ids 774, 1193, 1194, 1195, 1196, 1197, 1198, 1199, 1200, 1201, 1202, 1203, 1204, 1205
        "ogerpon", // ids 1416, 1417, 1418, 1419
        "oinkologne", // ids 1299, 1300
        "oricorio", // ids 741, 1169, 1170, 1171
        "palafin", // ids 1352, 1353
        "palkia", // ids 484, 1070
        "pikachu", // ids 25, 1009, 1010, 1011, 1012, 1013, 1014, 1015, 1016, 1017, 1018, 1019, 1020, 1021, 1022, 1487
        "pumpkaboo", // ids 710, 1157, 1158, 1159
        "rotom", // ids 479, 1064, 1065, 1066, 1067, 1068
        "shaymin", // ids 492, 1072
        "silvally", // ids 773, 1176, 1177, 1178, 1179, 1180, 1181, 1182, 1183, 1184, 1185, 1186, 1187, 1188, 1189, 1190, 1191, 1192
        "tauros-p", // ids 1402, 1403, 1404
        "terapagos", // ids 1431, 1432, 1433
        "thundurus", // ids 642, 1101
        "tornadus", // ids 641, 1100
        "ursaluna", // ids 901, 1424
        "urshifu", // ids 892, 1230
        "wishiwashi", // ids 746, 1175
        "wormadam", // ids 413, 1059, 1060
        "zacian", // ids 888, 1227
        "zamazenta", // ids 889, 1228
        "zygarde", // ids 718, 1164, 1165, 1166, 1167
    )

    private fun movesByName(key: String): MoveInfo? = when (key) {
        "10,000,000 volt thunderbolt" -> movesMap[867]
        "absorb" -> movesMap[71]
        "accelerock" -> movesMap[663]
        "acid" -> movesMap[51]
        "acid armor" -> movesMap[151]
        "acid downpour" -> movesMap[851]
        "acid spray" -> movesMap[491]
        "acrobatics" -> movesMap[512]
        "acupressure" -> movesMap[367]
        "aerial ace" -> movesMap[332]
        "aeroblast" -> movesMap[177]
        "after you" -> movesMap[495]
        "agility" -> movesMap[97]
        "air cutter" -> movesMap[314]
        "air slash" -> movesMap[403]
        "all-out pummeling" -> movesMap[849]
        "alluring voice" -> movesMap[842]
        "ally switch" -> movesMap[502]
        "amnesia" -> movesMap[133]
        "anchor shot" -> movesMap[640]
        "ancient power" -> movesMap[246]
        "apple acid" -> movesMap[715]
        "aqua cutter" -> movesMap[821]
        "aqua jet" -> movesMap[453]
        "aqua ring" -> movesMap[392]
        "aqua step" -> movesMap[800]
        "aqua tail" -> movesMap[401]
        "arm thrust" -> movesMap[292]
        "armor cannon" -> movesMap[816]
        "aromatherapy" -> movesMap[312]
        "aromatic mist" -> movesMap[597]
        "assist" -> movesMap[274]
        "assurance" -> movesMap[372]
        "astonish" -> movesMap[310]
        "astral barrage" -> movesMap[753]
        "attack order" -> movesMap[454]
        "attract" -> movesMap[213]
        "aura sphere" -> movesMap[396]
        "aura wheel" -> movesMap[711]
        "aurora beam" -> movesMap[62]
        "aurora veil" -> movesMap[657]
        "autotomize" -> movesMap[475]
        "avalanche" -> movesMap[419]
        "axe kick" -> movesMap[781]
        "baby-doll eyes" -> movesMap[608]
        "baddy bad" -> movesMap[684]
        "baneful bunker" -> movesMap[624]
        "barb barrage" -> movesMap[767]
        "barrage" -> movesMap[140]
        "barrier" -> movesMap[112]
        "baton pass" -> movesMap[226]
        "beak blast" -> movesMap[653]
        "beat up" -> movesMap[251]
        "behemoth bash" -> movesMap[710]
        "behemoth blade" -> movesMap[709]
        "belch" -> movesMap[562]
        "belly drum" -> movesMap[187]
        "bestow" -> movesMap[516]
        "bide" -> movesMap[117]
        "bind" -> movesMap[20]
        "bite" -> movesMap[44]
        "bitter blade" -> movesMap[817]
        "bitter malice" -> movesMap[769]
        "black hole eclipse" -> movesMap[864]
        "blast burn" -> movesMap[307]
        "blaze kick" -> movesMap[299]
        "blazing torque" -> movesMap[822]
        "bleakwind storm" -> movesMap[774]
        "blizzard" -> movesMap[59]
        "block" -> movesMap[335]
        "blood moon" -> movesMap[829]
        "bloom doom" -> movesMap[859]
        "blue flare" -> movesMap[551]
        "body press" -> movesMap[704]
        "body slam" -> movesMap[34]
        "bolt beak" -> movesMap[700]
        "bolt strike" -> movesMap[550]
        "bone club" -> movesMap[125]
        "bone rush" -> movesMap[198]
        "bonemerang" -> movesMap[155]
        "boomburst" -> movesMap[586]
        "bounce" -> movesMap[340]
        "bouncy bubble" -> movesMap[680]
        "branch poke" -> movesMap[713]
        "brave bird" -> movesMap[413]
        "breaking swipe" -> movesMap[712]
        "breakneck blitz" -> movesMap[848]
        "brick break" -> movesMap[280]
        "brine" -> movesMap[362]
        "brutal swing" -> movesMap[656]
        "bubble" -> movesMap[145]
        "bubble beam" -> movesMap[61]
        "bug bite" -> movesMap[450]
        "bug buzz" -> movesMap[405]
        "bulk up" -> movesMap[339]
        "bulldoze" -> movesMap[523]
        "bullet punch" -> movesMap[418]
        "bullet seed" -> movesMap[331]
        "burn up" -> movesMap[645]
        "burning bulwark" -> movesMap[836]
        "burning jealousy" -> movesMap[735]
        "buzzy buzz" -> movesMap[681]
        "calm mind" -> movesMap[347]
        "camouflage" -> movesMap[293]
        "captivate" -> movesMap[445]
        "catastropika" -> movesMap[866]
        "ceaseless edge" -> movesMap[773]
        "celebrate" -> movesMap[606]
        "charge" -> movesMap[268]
        "charge beam" -> movesMap[451]
        "charm" -> movesMap[204]
        "chatter" -> movesMap[448]
        "chilling water" -> movesMap[812]
        "chilly reception" -> movesMap[807]
        "chip away" -> movesMap[498]
        "chloroblast" -> movesMap[763]
        "circle throw" -> movesMap[509]
        "clamp" -> movesMap[128]
        "clanging scales" -> movesMap[654]
        "clangorous soul" -> movesMap[703]
        "clangorous soulblaze" -> movesMap[877]
        "clear smog" -> movesMap[499]
        "close combat" -> movesMap[370]
        "coaching" -> movesMap[739]
        "coil" -> movesMap[489]
        "collision course" -> movesMap[804]
        "combat torque" -> movesMap[825]
        "comet punch" -> movesMap[4]
        "comeuppance" -> movesMap[820]
        "confide" -> movesMap[590]
        "confuse ray" -> movesMap[109]
        "confusion" -> movesMap[93]
        "constrict" -> movesMap[132]
        "continental crush" -> movesMap[853]
        "conversion" -> movesMap[160]
        "conversion 2" -> movesMap[176]
        "copycat" -> movesMap[383]
        "core enforcer" -> movesMap[650]
        "corkscrew crash" -> movesMap[856]
        "corrosive gas" -> movesMap[738]
        "cosmic power" -> movesMap[322]
        "cotton guard" -> movesMap[538]
        "cotton spore" -> movesMap[178]
        "counter" -> movesMap[68]
        "court change" -> movesMap[702]
        "covet" -> movesMap[343]
        "crabhammer" -> movesMap[152]
        "crafty shield" -> movesMap[578]
        "cross chop" -> movesMap[238]
        "cross poison" -> movesMap[440]
        "crunch" -> movesMap[242]
        "crush claw" -> movesMap[306]
        "crush grip" -> movesMap[462]
        "curse" -> movesMap[174]
        "cut" -> movesMap[15]
        "dark pulse" -> movesMap[399]
        "dark void" -> movesMap[464]
        "darkest lariat" -> movesMap[626]
        "dazzling gleam" -> movesMap[605]
        "decorate" -> movesMap[705]
        "defend order" -> movesMap[455]
        "defense curl" -> movesMap[111]
        "defog" -> movesMap[432]
        "destiny bond" -> movesMap[194]
        "detect" -> movesMap[197]
        "devastating drake" -> movesMap[863]
        "diamond storm" -> movesMap[591]
        "dig" -> movesMap[91]
        "dire claw" -> movesMap[755]
        "disable" -> movesMap[50]
        "disarming voice" -> movesMap[574]
        "discharge" -> movesMap[435]
        "dive" -> movesMap[291]
        "dizzy punch" -> movesMap[146]
        "doodle" -> movesMap[795]
        "doom desire" -> movesMap[353]
        "double hit" -> movesMap[458]
        "double iron bash" -> movesMap[689]
        "double kick" -> movesMap[24]
        "double shock" -> movesMap[818]
        "double slap" -> movesMap[3]
        "double team" -> movesMap[104]
        "double-edge" -> movesMap[38]
        "draco meteor" -> movesMap[434]
        "dragon ascent" -> movesMap[620]
        "dragon breath" -> movesMap[225]
        "dragon cheer" -> movesMap[841]
        "dragon claw" -> movesMap[337]
        "dragon dance" -> movesMap[349]
        "dragon darts" -> movesMap[697]
        "dragon energy" -> movesMap[748]
        "dragon hammer" -> movesMap[655]
        "dragon pulse" -> movesMap[406]
        "dragon rage" -> movesMap[82]
        "dragon rush" -> movesMap[407]
        "dragon tail" -> movesMap[525]
        "drain punch" -> movesMap[409]
        "draining kiss" -> movesMap[577]
        "dream eater" -> movesMap[138]
        "drill peck" -> movesMap[65]
        "drill run" -> movesMap[529]
        "drum beating" -> movesMap[706]
        "dual chop" -> movesMap[530]
        "dual wingbeat" -> movesMap[742]
        "dynamax cannon" -> movesMap[690]
        "dynamic punch" -> movesMap[223]
        "earth power" -> movesMap[414]
        "earthquake" -> movesMap[89]
        "echoed voice" -> movesMap[497]
        "eerie impulse" -> movesMap[598]
        "eerie spell" -> movesMap[754]
        "egg bomb" -> movesMap[121]
        "electric terrain" -> movesMap[604]
        "electrify" -> movesMap[582]
        "electro ball" -> movesMap[486]
        "electro drift" -> movesMap[805]
        "electro shot" -> movesMap[833]
        "electroweb" -> movesMap[527]
        "embargo" -> movesMap[373]
        "ember" -> movesMap[52]
        "encore" -> movesMap[227]
        "endeavor" -> movesMap[283]
        "endure" -> movesMap[203]
        "energy ball" -> movesMap[412]
        "entrainment" -> movesMap[494]
        "eruption" -> movesMap[284]
        "esper wing" -> movesMap[768]
        "eternabeam" -> movesMap[723]
        "expanding force" -> movesMap[725]
        "explosion" -> movesMap[153]
        "extrasensory" -> movesMap[326]
        "extreme evoboost" -> movesMap[869]
        "extreme speed" -> movesMap[245]
        "facade" -> movesMap[263]
        "fairy lock" -> movesMap[587]
        "fairy wind" -> movesMap[584]
        "fake out" -> movesMap[252]
        "fake tears" -> movesMap[313]
        "false surrender" -> movesMap[721]
        "false swipe" -> movesMap[206]
        "feather dance" -> movesMap[297]
        "feint" -> movesMap[364]
        "feint attack" -> movesMap[185]
        "fell stinger" -> movesMap[565]
        "fickle beam" -> movesMap[835]
        "fiery dance" -> movesMap[552]
        "fiery wrath" -> movesMap[750]
        "fillet away" -> movesMap[796]
        "final gambit" -> movesMap[515]
        "fire blast" -> movesMap[126]
        "fire fang" -> movesMap[424]
        "fire lash" -> movesMap[643]
        "fire pledge" -> movesMap[519]
        "fire punch" -> movesMap[7]
        "fire spin" -> movesMap[83]
        "first impression" -> movesMap[623]
        "fishious rend" -> movesMap[701]
        "fissure" -> movesMap[90]
        "flail" -> movesMap[175]
        "flame burst" -> movesMap[481]
        "flame charge" -> movesMap[488]
        "flame wheel" -> movesMap[172]
        "flamethrower" -> movesMap[53]
        "flare blitz" -> movesMap[394]
        "flash" -> movesMap[148]
        "flash cannon" -> movesMap[430]
        "flatter" -> movesMap[260]
        "fleur cannon" -> movesMap[659]
        "fling" -> movesMap[374]
        "flip turn" -> movesMap[740]
        "floaty fall" -> movesMap[678]
        "floral healing" -> movesMap[629]
        "flower shield" -> movesMap[579]
        "flower trick" -> movesMap[798]
        "fly" -> movesMap[19]
        "flying press" -> movesMap[560]
        "focus blast" -> movesMap[411]
        "focus energy" -> movesMap[116]
        "focus punch" -> movesMap[264]
        "follow me" -> movesMap[266]
        "force palm" -> movesMap[395]
        "foresight" -> movesMap[193]
        "forest's curse" -> movesMap[571]
        "foul play" -> movesMap[492]
        "freeze shock" -> movesMap[553]
        "freeze-dry" -> movesMap[573]
        "freezing glare" -> movesMap[749]
        "freezy frost" -> movesMap[686]
        "frenzy plant" -> movesMap[338]
        "frost breath" -> movesMap[524]
        "frustration" -> movesMap[218]
        "fury attack" -> movesMap[31]
        "fury cutter" -> movesMap[210]
        "fury swipes" -> movesMap[154]
        "fusion bolt" -> movesMap[559]
        "fusion flare" -> movesMap[558]
        "future sight" -> movesMap[248]
        "g-max befuddle" -> movesMap[905]
        "g-max cannonade" -> movesMap[904]
        "g-max centiferno" -> movesMap[927]
        "g-max chi strike" -> movesMap[908]
        "g-max cuddle" -> movesMap[912]
        "g-max depletion" -> movesMap[932]
        "g-max drum solo" -> movesMap[916]
        "g-max finale" -> movesMap[930]
        "g-max fireball" -> movesMap[917]
        "g-max foam burst" -> movesMap[910]
        "g-max gold rush" -> movesMap[907]
        "g-max gravitas" -> movesMap[920]
        "g-max hydrosnipe" -> movesMap[918]
        "g-max malodor" -> movesMap[914]
        "g-max meltdown" -> movesMap[915]
        "g-max one blow" -> movesMap[933]
        "g-max rapid flow" -> movesMap[934]
        "g-max replenish" -> movesMap[913]
        "g-max resonance" -> movesMap[911]
        "g-max sandblast" -> movesMap[925]
        "g-max smite" -> movesMap[928]
        "g-max snooze" -> movesMap[929]
        "g-max steelsurge" -> movesMap[931]
        "g-max stonesurge" -> movesMap[921]
        "g-max stun shock" -> movesMap[926]
        "g-max sweetness" -> movesMap[924]
        "g-max tartness" -> movesMap[923]
        "g-max terror" -> movesMap[909]
        "g-max vine lash" -> movesMap[902]
        "g-max volcalith" -> movesMap[922]
        "g-max volt crash" -> movesMap[906]
        "g-max wildfire" -> movesMap[903]
        "g-max wind rage" -> movesMap[919]
        "gastro acid" -> movesMap[380]
        "gear grind" -> movesMap[544]
        "gear up" -> movesMap[637]
        "genesis supernova" -> movesMap[871]
        "geomancy" -> movesMap[601]
        "giga drain" -> movesMap[202]
        "giga impact" -> movesMap[416]
        "gigaton hammer" -> movesMap[819]
        "gigavolt havoc" -> movesMap[860]
        "glacial lance" -> movesMap[752]
        "glaciate" -> movesMap[549]
        "glaive rush" -> movesMap[790]
        "glare" -> movesMap[137]
        "glitzy glow" -> movesMap[683]
        "grass knot" -> movesMap[447]
        "grass pledge" -> movesMap[520]
        "grass whistle" -> movesMap[320]
        "grassy glide" -> movesMap[731]
        "grassy terrain" -> movesMap[580]
        "grav apple" -> movesMap[716]
        "gravity" -> movesMap[356]
        "growl" -> movesMap[45]
        "growth" -> movesMap[74]
        "grudge" -> movesMap[288]
        "guard split" -> movesMap[470]
        "guard swap" -> movesMap[385]
        "guardian of alola" -> movesMap[878]
        "guillotine" -> movesMap[12]
        "gunk shot" -> movesMap[441]
        "gust" -> movesMap[16]
        "gyro ball" -> movesMap[360]
        "hail" -> movesMap[258]
        "hammer arm" -> movesMap[359]
        "happy hour" -> movesMap[603]
        "hard press" -> movesMap[840]
        "harden" -> movesMap[106]
        "haze" -> movesMap[114]
        "head charge" -> movesMap[543]
        "head smash" -> movesMap[457]
        "headbutt" -> movesMap[29]
        "headlong rush" -> movesMap[766]
        "heal bell" -> movesMap[215]
        "heal block" -> movesMap[377]
        "heal order" -> movesMap[456]
        "heal pulse" -> movesMap[505]
        "healing wish" -> movesMap[361]
        "heart stamp" -> movesMap[531]
        "heart swap" -> movesMap[391]
        "heat crash" -> movesMap[535]
        "heat wave" -> movesMap[257]
        "heavy slam" -> movesMap[484]
        "helping hand" -> movesMap[270]
        "hex" -> movesMap[506]
        "hidden power" -> movesMap[237]
        "high horsepower" -> movesMap[630]
        "high jump kick" -> movesMap[136]
        "hold back" -> movesMap[610]
        "hold hands" -> movesMap[607]
        "hone claws" -> movesMap[468]
        "horn attack" -> movesMap[30]
        "horn drill" -> movesMap[32]
        "horn leech" -> movesMap[532]
        "howl" -> movesMap[336]
        "hurricane" -> movesMap[542]
        "hydro cannon" -> movesMap[308]
        "hydro pump" -> movesMap[56]
        "hydro steam" -> movesMap[828]
        "hydro vortex" -> movesMap[858]
        "hyper beam" -> movesMap[63]
        "hyper drill" -> movesMap[813]
        "hyper fang" -> movesMap[158]
        "hyper voice" -> movesMap[304]
        "hyperspace fury" -> movesMap[621]
        "hyperspace hole" -> movesMap[593]
        "hypnosis" -> movesMap[95]
        "ice ball" -> movesMap[301]
        "ice beam" -> movesMap[58]
        "ice burn" -> movesMap[554]
        "ice fang" -> movesMap[423]
        "ice hammer" -> movesMap[628]
        "ice punch" -> movesMap[8]
        "ice shard" -> movesMap[420]
        "ice spinner" -> movesMap[789]
        "icicle crash" -> movesMap[556]
        "icicle spear" -> movesMap[333]
        "icy wind" -> movesMap[196]
        "imprison" -> movesMap[286]
        "incinerate" -> movesMap[510]
        "infernal parade" -> movesMap[772]
        "inferno" -> movesMap[517]
        "inferno overdrive" -> movesMap[857]
        "infestation" -> movesMap[611]
        "ingrain" -> movesMap[275]
        "instruct" -> movesMap[652]
        "ion deluge" -> movesMap[569]
        "iron defense" -> movesMap[334]
        "iron head" -> movesMap[442]
        "iron tail" -> movesMap[231]
        "ivy cudgel" -> movesMap[832]
        "jaw lock" -> movesMap[692]
        "jet punch" -> movesMap[785]
        "judgment" -> movesMap[449]
        "jump kick" -> movesMap[26]
        "jungle healing" -> movesMap[744]
        "karate chop" -> movesMap[2]
        "kinesis" -> movesMap[134]
        "king's shield" -> movesMap[588]
        "knock off" -> movesMap[282]
        "kowtow cleave" -> movesMap[797]
        "land's wrath" -> movesMap[616]
        "laser focus" -> movesMap[636]
        "lash out" -> movesMap[736]
        "last resort" -> movesMap[387]
        "last respects" -> movesMap[782]
        "lava plume" -> movesMap[436]
        "leaf blade" -> movesMap[348]
        "leaf storm" -> movesMap[437]
        "leaf tornado" -> movesMap[536]
        "leafage" -> movesMap[633]
        "leech life" -> movesMap[141]
        "leech seed" -> movesMap[73]
        "leer" -> movesMap[43]
        "let's snuggle forever" -> movesMap[876]
        "lick" -> movesMap[122]
        "life dew" -> movesMap[719]
        "light of ruin" -> movesMap[617]
        "light screen" -> movesMap[113]
        "light that burns the sky" -> movesMap[881]
        "liquidation" -> movesMap[664]
        "lock-on" -> movesMap[199]
        "lovely kiss" -> movesMap[142]
        "low kick" -> movesMap[67]
        "low sweep" -> movesMap[490]
        "lucky chant" -> movesMap[381]
        "lumina crash" -> movesMap[783]
        "lunar blessing" -> movesMap[777]
        "lunar dance" -> movesMap[461]
        "lunge" -> movesMap[642]
        "luster purge" -> movesMap[295]
        "mach punch" -> movesMap[183]
        "magic coat" -> movesMap[277]
        "magic powder" -> movesMap[696]
        "magic room" -> movesMap[478]
        "magical leaf" -> movesMap[345]
        "magical torque" -> movesMap[826]
        "magma storm" -> movesMap[463]
        "magnet bomb" -> movesMap[443]
        "magnet rise" -> movesMap[393]
        "magnetic flux" -> movesMap[602]
        "magnitude" -> movesMap[222]
        "make it rain" -> movesMap[802]
        "malicious moonsault" -> movesMap[873]
        "malignant chain" -> movesMap[847]
        "mat block" -> movesMap[561]
        "matcha gotcha" -> movesMap[830]
        "max airstream" -> movesMap[886]
        "max darkness" -> movesMap[900]
        "max flare" -> movesMap[893]
        "max flutterby" -> movesMap[890]
        "max geyser" -> movesMap[894]
        "max guard" -> movesMap[883]
        "max hailstorm" -> movesMap[898]
        "max knuckle" -> movesMap[885]
        "max lightning" -> movesMap[896]
        "max mindstorm" -> movesMap[897]
        "max ooze" -> movesMap[887]
        "max overgrowth" -> movesMap[895]
        "max phantasm" -> movesMap[891]
        "max quake" -> movesMap[888]
        "max rockfall" -> movesMap[889]
        "max starfall" -> movesMap[901]
        "max steelspike" -> movesMap[892]
        "max strike" -> movesMap[884]
        "max wyrmwind" -> movesMap[899]
        "me first" -> movesMap[382]
        "mean look" -> movesMap[212]
        "meditate" -> movesMap[96]
        "mega drain" -> movesMap[72]
        "mega kick" -> movesMap[25]
        "mega punch" -> movesMap[5]
        "megahorn" -> movesMap[224]
        "memento" -> movesMap[262]
        "menacing moonraze maelstrom" -> movesMap[880]
        "metal burst" -> movesMap[368]
        "metal claw" -> movesMap[232]
        "metal sound" -> movesMap[319]
        "meteor assault" -> movesMap[722]
        "meteor beam" -> movesMap[728]
        "meteor mash" -> movesMap[309]
        "metronome" -> movesMap[118]
        "mighty cleave" -> movesMap[838]
        "milk drink" -> movesMap[208]
        "mimic" -> movesMap[102]
        "mind blown" -> movesMap[673]
        "mind reader" -> movesMap[170]
        "minimize" -> movesMap[107]
        "miracle eye" -> movesMap[357]
        "mirror coat" -> movesMap[243]
        "mirror move" -> movesMap[119]
        "mirror shot" -> movesMap[429]
        "mist" -> movesMap[54]
        "mist ball" -> movesMap[296]
        "misty explosion" -> movesMap[730]
        "misty terrain" -> movesMap[581]
        "moonblast" -> movesMap[585]
        "moongeist beam" -> movesMap[668]
        "moonlight" -> movesMap[236]
        "morning sun" -> movesMap[234]
        "mortal spin" -> movesMap[794]
        "mountain gale" -> movesMap[764]
        "mud bomb" -> movesMap[426]
        "mud shot" -> movesMap[341]
        "mud sport" -> movesMap[300]
        "mud-slap" -> movesMap[189]
        "muddy water" -> movesMap[330]
        "multi-attack" -> movesMap[672]
        "mystical fire" -> movesMap[595]
        "mystical power" -> movesMap[760]
        "nasty plot" -> movesMap[417]
        "natural gift" -> movesMap[363]
        "nature power" -> movesMap[267]
        "nature's madness" -> movesMap[671]
        "needle arm" -> movesMap[302]
        "never-ending nightmare" -> movesMap[855]
        "night daze" -> movesMap[539]
        "night shade" -> movesMap[101]
        "night slash" -> movesMap[400]
        "nightmare" -> movesMap[171]
        "no retreat" -> movesMap[694]
        "noble roar" -> movesMap[568]
        "noxious torque" -> movesMap[824]
        "nuzzle" -> movesMap[609]
        "oblivion wing" -> movesMap[613]
        "obstruct" -> movesMap[720]
        "oceanic operetta" -> movesMap[874]
        "octazooka" -> movesMap[190]
        "octolock" -> movesMap[699]
        "odor sleuth" -> movesMap[316]
        "ominous wind" -> movesMap[466]
        "order up" -> movesMap[784]
        "origin pulse" -> movesMap[618]
        "outrage" -> movesMap[200]
        "overdrive" -> movesMap[714]
        "overheat" -> movesMap[315]
        "pain split" -> movesMap[220]
        "parabolic charge" -> movesMap[570]
        "parting shot" -> movesMap[575]
        "pay day" -> movesMap[6]
        "payback" -> movesMap[371]
        "peck" -> movesMap[64]
        "perish song" -> movesMap[195]
        "petal blizzard" -> movesMap[572]
        "petal dance" -> movesMap[80]
        "phantom force" -> movesMap[566]
        "photon geyser" -> movesMap[675]
        "pika papow" -> movesMap[679]
        "pin missile" -> movesMap[42]
        "plasma fists" -> movesMap[674]
        "play nice" -> movesMap[589]
        "play rough" -> movesMap[583]
        "pluck" -> movesMap[365]
        "poison fang" -> movesMap[305]
        "poison gas" -> movesMap[139]
        "poison jab" -> movesMap[398]
        "poison powder" -> movesMap[77]
        "poison sting" -> movesMap[40]
        "poison tail" -> movesMap[342]
        "pollen puff" -> movesMap[639]
        "poltergeist" -> movesMap[737]
        "population bomb" -> movesMap[788]
        "pounce" -> movesMap[810]
        "pound" -> movesMap[1]
        "powder" -> movesMap[600]
        "powder snow" -> movesMap[181]
        "power gem" -> movesMap[408]
        "power shift" -> movesMap[757]
        "power split" -> movesMap[471]
        "power swap" -> movesMap[384]
        "power trick" -> movesMap[379]
        "power trip" -> movesMap[644]
        "power whip" -> movesMap[438]
        "power-up punch" -> movesMap[612]
        "precipice blades" -> movesMap[619]
        "present" -> movesMap[217]
        "prismatic laser" -> movesMap[665]
        "protect" -> movesMap[182]
        "psybeam" -> movesMap[60]
        "psyblade" -> movesMap[827]
        "psych up" -> movesMap[244]
        "psychic" -> movesMap[94]
        "psychic fangs" -> movesMap[660]
        "psychic noise" -> movesMap[845]
        "psychic terrain" -> movesMap[641]
        "psycho boost" -> movesMap[354]
        "psycho cut" -> movesMap[427]
        "psycho shift" -> movesMap[375]
        "psyshield bash" -> movesMap[756]
        "psyshock" -> movesMap[473]
        "psystrike" -> movesMap[540]
        "psywave" -> movesMap[149]
        "pulverizing pancake" -> movesMap[870]
        "punishment" -> movesMap[386]
        "purify" -> movesMap[648]
        "pursuit" -> movesMap[228]
        "pyro ball" -> movesMap[708]
        "quash" -> movesMap[511]
        "quick attack" -> movesMap[98]
        "quick guard" -> movesMap[501]
        "quiver dance" -> movesMap[483]
        "rage" -> movesMap[99]
        "rage fist" -> movesMap[815]
        "rage powder" -> movesMap[476]
        "raging bull" -> movesMap[801]
        "raging fury" -> movesMap[761]
        "rain dance" -> movesMap[240]
        "rapid spin" -> movesMap[229]
        "razor leaf" -> movesMap[75]
        "razor shell" -> movesMap[534]
        "razor wind" -> movesMap[13]
        "recover" -> movesMap[105]
        "recycle" -> movesMap[278]
        "reflect" -> movesMap[115]
        "reflect type" -> movesMap[513]
        "refresh" -> movesMap[287]
        "relic song" -> movesMap[547]
        "rest" -> movesMap[156]
        "retaliate" -> movesMap[514]
        "return" -> movesMap[216]
        "revelation dance" -> movesMap[649]
        "revenge" -> movesMap[279]
        "reversal" -> movesMap[179]
        "revival blessing" -> movesMap[791]
        "rising voltage" -> movesMap[732]
        "roar" -> movesMap[46]
        "roar of time" -> movesMap[459]
        "rock blast" -> movesMap[350]
        "rock climb" -> movesMap[431]
        "rock polish" -> movesMap[397]
        "rock slide" -> movesMap[157]
        "rock smash" -> movesMap[249]
        "rock throw" -> movesMap[88]
        "rock tomb" -> movesMap[317]
        "rock wrecker" -> movesMap[439]
        "role play" -> movesMap[272]
        "rolling kick" -> movesMap[27]
        "rollout" -> movesMap[205]
        "roost" -> movesMap[355]
        "rototiller" -> movesMap[563]
        "round" -> movesMap[496]
        "ruination" -> movesMap[803]
        "sacred fire" -> movesMap[221]
        "sacred sword" -> movesMap[533]
        "safeguard" -> movesMap[219]
        "salt cure" -> movesMap[792]
        "sand attack" -> movesMap[28]
        "sand tomb" -> movesMap[328]
        "sandsear storm" -> movesMap[776]
        "sandstorm" -> movesMap[201]
        "sappy seed" -> movesMap[685]
        "savage spin-out" -> movesMap[854]
        "scald" -> movesMap[503]
        "scale shot" -> movesMap[727]
        "scary face" -> movesMap[184]
        "scorching sands" -> movesMap[743]
        "scratch" -> movesMap[10]
        "screech" -> movesMap[103]
        "searing shot" -> movesMap[545]
        "searing sunraze smash" -> movesMap[879]
        "secret power" -> movesMap[290]
        "secret sword" -> movesMap[548]
        "seed bomb" -> movesMap[402]
        "seed flare" -> movesMap[465]
        "seismic toss" -> movesMap[69]
        "self-destruct" -> movesMap[120]
        "shadow ball" -> movesMap[247]
        "shadow bone" -> movesMap[662]
        "shadow claw" -> movesMap[421]
        "shadow force" -> movesMap[467]
        "shadow punch" -> movesMap[325]
        "shadow sneak" -> movesMap[425]
        "sharpen" -> movesMap[159]
        "shattered psyche" -> movesMap[861]
        "shed tail" -> movesMap[806]
        "sheer cold" -> movesMap[329]
        "shell side arm" -> movesMap[729]
        "shell smash" -> movesMap[504]
        "shell trap" -> movesMap[658]
        "shelter" -> movesMap[770]
        "shift gear" -> movesMap[508]
        "shock wave" -> movesMap[351]
        "shore up" -> movesMap[622]
        "signal beam" -> movesMap[324]
        "silk trap" -> movesMap[780]
        "silver wind" -> movesMap[318]
        "simple beam" -> movesMap[493]
        "sing" -> movesMap[47]
        "sinister arrow raid" -> movesMap[872]
        "sizzly slide" -> movesMap[682]
        "sketch" -> movesMap[166]
        "skill swap" -> movesMap[285]
        "skitter smack" -> movesMap[734]
        "skull bash" -> movesMap[130]
        "sky attack" -> movesMap[143]
        "sky drop" -> movesMap[507]
        "sky uppercut" -> movesMap[327]
        "slack off" -> movesMap[303]
        "slam" -> movesMap[21]
        "slash" -> movesMap[163]
        "sleep powder" -> movesMap[79]
        "sleep talk" -> movesMap[214]
        "sludge" -> movesMap[124]
        "sludge bomb" -> movesMap[188]
        "sludge wave" -> movesMap[482]
        "smack down" -> movesMap[479]
        "smart strike" -> movesMap[647]
        "smelling salts" -> movesMap[265]
        "smog" -> movesMap[123]
        "smokescreen" -> movesMap[108]
        "snap trap" -> movesMap[707]
        "snarl" -> movesMap[555]
        "snatch" -> movesMap[289]
        "snipe shot" -> movesMap[691]
        "snore" -> movesMap[173]
        "snowscape" -> movesMap[809]
        "soak" -> movesMap[487]
        "soft-boiled" -> movesMap[135]
        "solar beam" -> movesMap[76]
        "solar blade" -> movesMap[632]
        "sonic boom" -> movesMap[49]
        "soul-stealing 7-star strike" -> movesMap[882]
        "spacial rend" -> movesMap[460]
        "spark" -> movesMap[209]
        "sparkling aria" -> movesMap[627]
        "sparkly swirl" -> movesMap[687]
        "spectral thief" -> movesMap[666]
        "speed swap" -> movesMap[646]
        "spicy extract" -> movesMap[786]
        "spider web" -> movesMap[169]
        "spike cannon" -> movesMap[131]
        "spikes" -> movesMap[191]
        "spiky shield" -> movesMap[596]
        "spin out" -> movesMap[787]
        "spirit break" -> movesMap[717]
        "spirit shackle" -> movesMap[625]
        "spit up" -> movesMap[255]
        "spite" -> movesMap[180]
        "splash" -> movesMap[150]
        "splintered stormshards" -> movesMap[875]
        "splishy splash" -> movesMap[677]
        "spore" -> movesMap[147]
        "spotlight" -> movesMap[634]
        "springtide storm" -> movesMap[759]
        "stealth rock" -> movesMap[446]
        "steam eruption" -> movesMap[592]
        "steamroller" -> movesMap[537]
        "steel beam" -> movesMap[724]
        "steel roller" -> movesMap[726]
        "steel wing" -> movesMap[211]
        "sticky web" -> movesMap[564]
        "stockpile" -> movesMap[254]
        "stoked sparksurfer" -> movesMap[868]
        "stomp" -> movesMap[23]
        "stomping tantrum" -> movesMap[661]
        "stone axe" -> movesMap[758]
        "stone edge" -> movesMap[444]
        "stored power" -> movesMap[500]
        "storm throw" -> movesMap[480]
        "strange steam" -> movesMap[718]
        "strength" -> movesMap[70]
        "strength sap" -> movesMap[631]
        "string shot" -> movesMap[81]
        "struggle" -> movesMap[165]
        "struggle bug" -> movesMap[522]
        "stuff cheeks" -> movesMap[693]
        "stun spore" -> movesMap[78]
        "submission" -> movesMap[66]
        "substitute" -> movesMap[164]
        "subzero slammer" -> movesMap[862]
        "sucker punch" -> movesMap[389]
        "sunny day" -> movesMap[241]
        "sunsteel strike" -> movesMap[667]
        "super fang" -> movesMap[162]
        "supercell slam" -> movesMap[844]
        "superpower" -> movesMap[276]
        "supersonic" -> movesMap[48]
        "supersonic skystrike" -> movesMap[850]
        "surf" -> movesMap[57]
        "surging strikes" -> movesMap[746]
        "swagger" -> movesMap[207]
        "swallow" -> movesMap[256]
        "sweet kiss" -> movesMap[186]
        "sweet scent" -> movesMap[230]
        "swift" -> movesMap[129]
        "switcheroo" -> movesMap[415]
        "swords dance" -> movesMap[14]
        "synchronoise" -> movesMap[485]
        "synthesis" -> movesMap[235]
        "syrup bomb" -> movesMap[831]
        "tachyon cutter" -> movesMap[839]
        "tackle" -> movesMap[33]
        "tail glow" -> movesMap[294]
        "tail slap" -> movesMap[541]
        "tail whip" -> movesMap[39]
        "tailwind" -> movesMap[366]
        "take down" -> movesMap[36]
        "take heart" -> movesMap[778]
        "tar shot" -> movesMap[695]
        "taunt" -> movesMap[269]
        "tearful look" -> movesMap[669]
        "teatime" -> movesMap[698]
        "techno blast" -> movesMap[546]
        "tectonic rage" -> movesMap[852]
        "teeter dance" -> movesMap[298]
        "telekinesis" -> movesMap[477]
        "teleport" -> movesMap[100]
        "temper flare" -> movesMap[843]
        "tera blast" -> movesMap[779]
        "tera starstorm" -> movesMap[834]
        "terrain pulse" -> movesMap[733]
        "thief" -> movesMap[168]
        "thousand arrows" -> movesMap[614]
        "thousand waves" -> movesMap[615]
        "thrash" -> movesMap[37]
        "throat chop" -> movesMap[638]
        "thunder" -> movesMap[87]
        "thunder cage" -> movesMap[747]
        "thunder fang" -> movesMap[422]
        "thunder punch" -> movesMap[9]
        "thunder shock" -> movesMap[84]
        "thunder wave" -> movesMap[86]
        "thunderbolt" -> movesMap[85]
        "thunderclap" -> movesMap[837]
        "thunderous kick" -> movesMap[751]
        "tickle" -> movesMap[321]
        "tidy up" -> movesMap[808]
        "topsy-turvy" -> movesMap[576]
        "torch song" -> movesMap[799]
        "torment" -> movesMap[259]
        "toxic" -> movesMap[92]
        "toxic spikes" -> movesMap[390]
        "toxic thread" -> movesMap[635]
        "trailblaze" -> movesMap[811]
        "transform" -> movesMap[144]
        "tri attack" -> movesMap[161]
        "trick" -> movesMap[271]
        "trick room" -> movesMap[433]
        "trick-or-treat" -> movesMap[567]
        "triple arrows" -> movesMap[771]
        "triple axel" -> movesMap[741]
        "triple dive" -> movesMap[793]
        "triple kick" -> movesMap[167]
        "trop kick" -> movesMap[651]
        "trump card" -> movesMap[376]
        "twin beam" -> movesMap[814]
        "twineedle" -> movesMap[41]
        "twinkle tackle" -> movesMap[865]
        "twister" -> movesMap[239]
        "u-turn" -> movesMap[369]
        "upper hand" -> movesMap[846]
        "uproar" -> movesMap[253]
        "v-create" -> movesMap[557]
        "vacuum wave" -> movesMap[410]
        "veevee volley" -> movesMap[688]
        "venom drench" -> movesMap[599]
        "venoshock" -> movesMap[474]
        "victory dance" -> movesMap[765]
        "vine whip" -> movesMap[22]
        "vise grip" -> movesMap[11]
        "vital throw" -> movesMap[233]
        "volt switch" -> movesMap[521]
        "volt tackle" -> movesMap[344]
        "wake-up slap" -> movesMap[358]
        "water gun" -> movesMap[55]
        "water pledge" -> movesMap[518]
        "water pulse" -> movesMap[352]
        "water shuriken" -> movesMap[594]
        "water sport" -> movesMap[346]
        "water spout" -> movesMap[323]
        "waterfall" -> movesMap[127]
        "wave crash" -> movesMap[762]
        "weather ball" -> movesMap[311]
        "whirlpool" -> movesMap[250]
        "whirlwind" -> movesMap[18]
        "wicked blow" -> movesMap[745]
        "wicked torque" -> movesMap[823]
        "wide guard" -> movesMap[469]
        "wild charge" -> movesMap[528]
        "wildbolt storm" -> movesMap[775]
        "will-o-wisp" -> movesMap[261]
        "wing attack" -> movesMap[17]
        "wish" -> movesMap[273]
        "withdraw" -> movesMap[110]
        "wonder room" -> movesMap[472]
        "wood hammer" -> movesMap[452]
        "work up" -> movesMap[526]
        "worry seed" -> movesMap[388]
        "wrap" -> movesMap[35]
        "wring out" -> movesMap[378]
        "x-scissor" -> movesMap[404]
        "yawn" -> movesMap[281]
        "zap cannon" -> movesMap[192]
        "zen headbutt" -> movesMap[428]
        "zing zap" -> movesMap[670]
        "zippy zap" -> movesMap[676]
        else -> null
    }

    private fun registerSpecies(id: Int, name: String, t1: PokemonType, t2: PokemonType?,
                                hp: Int, atk: Int, def: Int, spa: Int, spd: Int, spe: Int) {
        speciesMap[id] = SpeciesInfo(id, name, t1, t2, hp, atk, def, spa, spd, spe)
    }

    private fun registerMove(id: Int, name: String, type: PokemonType, category: MoveCategory, power: Int, acc: Int, pp: Int) {
        movesMap[id] = MoveInfo(id, name, type, category, power, acc, pp)
    }

    private fun registerSpeciesChunk1() {
        registerSpecies(1, "Bulbasaur", PokemonType.GRASS, PokemonType.POISON, 45, 49, 49, 65, 65, 45)
        registerSpecies(2, "Ivysaur", PokemonType.GRASS, PokemonType.POISON, 60, 62, 63, 80, 80, 60)
        registerSpecies(3, "Venusaur", PokemonType.GRASS, PokemonType.POISON, 80, 82, 83, 100, 100, 80)
        registerSpecies(4, "Charmander", PokemonType.FIRE, null, 39, 52, 43, 60, 50, 65)
        registerSpecies(5, "Charmeleon", PokemonType.FIRE, null, 58, 64, 58, 80, 65, 80)
        registerSpecies(6, "Charizard", PokemonType.FIRE, PokemonType.FLYING, 78, 84, 78, 109, 85, 100)
        registerSpecies(7, "Squirtle", PokemonType.WATER, null, 44, 48, 65, 50, 64, 43)
        registerSpecies(8, "Wartortle", PokemonType.WATER, null, 59, 63, 80, 65, 80, 58)
        registerSpecies(9, "Blastoise", PokemonType.WATER, null, 79, 83, 100, 85, 105, 78)
        registerSpecies(10, "Caterpie", PokemonType.BUG, null, 45, 30, 35, 20, 20, 45)
        registerSpecies(11, "Metapod", PokemonType.BUG, null, 50, 20, 55, 25, 25, 30)
        registerSpecies(12, "Butterfree", PokemonType.BUG, PokemonType.FLYING, 60, 45, 50, 90, 80, 70)
        registerSpecies(13, "Weedle", PokemonType.BUG, PokemonType.POISON, 40, 35, 30, 20, 20, 50)
        registerSpecies(14, "Kakuna", PokemonType.BUG, PokemonType.POISON, 45, 25, 50, 25, 25, 35)
        registerSpecies(15, "Beedrill", PokemonType.BUG, PokemonType.POISON, 65, 90, 40, 45, 80, 75)
        registerSpecies(16, "Pidgey", PokemonType.NORMAL, PokemonType.FLYING, 40, 45, 40, 35, 35, 56)
        registerSpecies(17, "Pidgeotto", PokemonType.NORMAL, PokemonType.FLYING, 63, 60, 55, 50, 50, 71)
        registerSpecies(18, "Pidgeot", PokemonType.NORMAL, PokemonType.FLYING, 83, 80, 75, 70, 70, 101)
        registerSpecies(19, "Rattata", PokemonType.NORMAL, null, 30, 56, 35, 25, 35, 72)
        registerSpecies(20, "Raticate", PokemonType.NORMAL, null, 55, 81, 60, 50, 70, 97)
        registerSpecies(21, "Spearow", PokemonType.NORMAL, PokemonType.FLYING, 40, 60, 30, 31, 31, 70)
        registerSpecies(22, "Fearow", PokemonType.NORMAL, PokemonType.FLYING, 65, 90, 65, 61, 61, 100)
        registerSpecies(23, "Ekans", PokemonType.POISON, null, 35, 60, 44, 40, 54, 55)
        registerSpecies(24, "Arbok", PokemonType.POISON, null, 60, 95, 69, 65, 79, 80)
        registerSpecies(25, "Pikachu", PokemonType.ELECTRIC, null, 35, 55, 40, 50, 50, 90)
        registerSpecies(26, "Raichu", PokemonType.ELECTRIC, null, 60, 90, 55, 90, 80, 110)
        registerSpecies(27, "Sandshrew", PokemonType.GROUND, null, 50, 75, 85, 20, 30, 40)
        registerSpecies(28, "Sandslash", PokemonType.GROUND, null, 75, 100, 110, 45, 55, 65)
        registerSpecies(29, "Nidoran♀", PokemonType.POISON, null, 55, 47, 52, 40, 40, 41)
        registerSpecies(30, "Nidorina", PokemonType.POISON, null, 70, 62, 67, 55, 55, 56)
        registerSpecies(31, "Nidoqueen", PokemonType.POISON, PokemonType.GROUND, 90, 92, 87, 75, 85, 76)
        registerSpecies(32, "Nidoran♂", PokemonType.POISON, null, 46, 57, 40, 40, 40, 50)
        registerSpecies(33, "Nidorino", PokemonType.POISON, null, 61, 72, 57, 55, 55, 65)
        registerSpecies(34, "Nidoking", PokemonType.POISON, PokemonType.GROUND, 81, 102, 77, 85, 75, 85)
        registerSpecies(35, "Clefairy", PokemonType.FAIRY, null, 70, 45, 48, 60, 65, 35)
        registerSpecies(36, "Clefable", PokemonType.FAIRY, null, 95, 70, 73, 95, 90, 60)
        registerSpecies(37, "Vulpix", PokemonType.FIRE, null, 38, 41, 40, 50, 65, 65)
        registerSpecies(38, "Ninetales", PokemonType.FIRE, null, 73, 76, 75, 81, 100, 100)
        registerSpecies(39, "Jigglypuff", PokemonType.NORMAL, PokemonType.FAIRY, 115, 45, 20, 45, 25, 20)
        registerSpecies(40, "Wigglytuff", PokemonType.NORMAL, PokemonType.FAIRY, 140, 70, 45, 85, 50, 45)
        registerSpecies(41, "Zubat", PokemonType.POISON, PokemonType.FLYING, 40, 45, 35, 30, 40, 55)
        registerSpecies(42, "Golbat", PokemonType.POISON, PokemonType.FLYING, 75, 80, 70, 65, 75, 90)
        registerSpecies(43, "Oddish", PokemonType.GRASS, PokemonType.POISON, 45, 50, 55, 75, 65, 30)
        registerSpecies(44, "Gloom", PokemonType.GRASS, PokemonType.POISON, 60, 65, 70, 85, 75, 40)
        registerSpecies(45, "Vileplume", PokemonType.GRASS, PokemonType.POISON, 75, 80, 85, 110, 90, 50)
        registerSpecies(46, "Paras", PokemonType.BUG, PokemonType.GRASS, 35, 70, 55, 45, 55, 25)
        registerSpecies(47, "Parasect", PokemonType.BUG, PokemonType.GRASS, 60, 95, 80, 60, 80, 30)
        registerSpecies(48, "Venonat", PokemonType.BUG, PokemonType.POISON, 60, 55, 50, 40, 55, 45)
        registerSpecies(49, "Venomoth", PokemonType.BUG, PokemonType.POISON, 70, 65, 60, 90, 75, 90)
        registerSpecies(50, "Diglett", PokemonType.GROUND, null, 10, 55, 25, 35, 45, 95)
        registerSpecies(51, "Dugtrio", PokemonType.GROUND, null, 35, 100, 50, 50, 70, 120)
        registerSpecies(52, "Meowth", PokemonType.NORMAL, null, 40, 45, 35, 40, 40, 90)
        registerSpecies(53, "Persian", PokemonType.NORMAL, null, 65, 70, 60, 65, 65, 115)
        registerSpecies(54, "Psyduck", PokemonType.WATER, null, 50, 52, 48, 65, 50, 55)
        registerSpecies(55, "Golduck", PokemonType.WATER, null, 80, 82, 78, 95, 80, 85)
        registerSpecies(56, "Mankey", PokemonType.FIGHTING, null, 40, 80, 35, 35, 45, 70)
        registerSpecies(57, "Primeape", PokemonType.FIGHTING, null, 65, 105, 60, 60, 70, 95)
        registerSpecies(58, "Growlithe", PokemonType.FIRE, null, 55, 70, 45, 70, 50, 60)
        registerSpecies(59, "Arcanine", PokemonType.FIRE, null, 90, 110, 80, 100, 80, 95)
        registerSpecies(60, "Poliwag", PokemonType.WATER, null, 40, 50, 40, 40, 40, 90)
        registerSpecies(61, "Poliwhirl", PokemonType.WATER, null, 65, 65, 65, 50, 50, 90)
        registerSpecies(62, "Poliwrath", PokemonType.WATER, PokemonType.FIGHTING, 90, 95, 95, 70, 90, 70)
        registerSpecies(63, "Abra", PokemonType.PSYCHIC, null, 25, 20, 15, 105, 55, 90)
        registerSpecies(64, "Kadabra", PokemonType.PSYCHIC, null, 40, 35, 30, 120, 70, 105)
        registerSpecies(65, "Alakazam", PokemonType.PSYCHIC, null, 55, 50, 45, 135, 95, 120)
        registerSpecies(66, "Machop", PokemonType.FIGHTING, null, 70, 80, 50, 35, 35, 35)
        registerSpecies(67, "Machoke", PokemonType.FIGHTING, null, 80, 100, 70, 50, 60, 45)
        registerSpecies(68, "Machamp", PokemonType.FIGHTING, null, 90, 130, 80, 65, 85, 55)
        registerSpecies(69, "Bellsprout", PokemonType.GRASS, PokemonType.POISON, 50, 75, 35, 70, 30, 40)
        registerSpecies(70, "Weepinbell", PokemonType.GRASS, PokemonType.POISON, 65, 90, 50, 85, 45, 55)
        registerSpecies(71, "Victreebel", PokemonType.GRASS, PokemonType.POISON, 80, 105, 65, 100, 70, 70)
        registerSpecies(72, "Tentacool", PokemonType.WATER, PokemonType.POISON, 40, 40, 35, 50, 100, 70)
        registerSpecies(73, "Tentacruel", PokemonType.WATER, PokemonType.POISON, 80, 70, 65, 80, 120, 100)
        registerSpecies(74, "Geodude", PokemonType.ROCK, PokemonType.GROUND, 40, 80, 100, 30, 30, 20)
        registerSpecies(75, "Graveler", PokemonType.ROCK, PokemonType.GROUND, 55, 95, 115, 45, 45, 35)
        registerSpecies(76, "Golem", PokemonType.ROCK, PokemonType.GROUND, 80, 120, 130, 55, 65, 45)
        registerSpecies(77, "Ponyta", PokemonType.FIRE, null, 50, 85, 55, 65, 65, 90)
        registerSpecies(78, "Rapidash", PokemonType.FIRE, null, 65, 100, 70, 80, 80, 105)
        registerSpecies(79, "Slowpoke", PokemonType.WATER, PokemonType.PSYCHIC, 90, 65, 65, 40, 40, 15)
        registerSpecies(80, "Slowbro", PokemonType.WATER, PokemonType.PSYCHIC, 95, 75, 110, 100, 80, 30)
        registerSpecies(81, "Magnemite", PokemonType.ELECTRIC, PokemonType.STEEL, 25, 35, 70, 95, 55, 45)
        registerSpecies(82, "Magneton", PokemonType.ELECTRIC, PokemonType.STEEL, 50, 60, 95, 120, 70, 70)
        registerSpecies(83, "Farfetch'd", PokemonType.NORMAL, PokemonType.FLYING, 52, 90, 55, 58, 62, 60)
        registerSpecies(84, "Doduo", PokemonType.NORMAL, PokemonType.FLYING, 35, 85, 45, 35, 35, 75)
        registerSpecies(85, "Dodrio", PokemonType.NORMAL, PokemonType.FLYING, 60, 110, 70, 60, 60, 110)
        registerSpecies(86, "Seel", PokemonType.WATER, null, 65, 45, 55, 45, 70, 45)
        registerSpecies(87, "Dewgong", PokemonType.WATER, PokemonType.ICE, 90, 70, 80, 70, 95, 70)
        registerSpecies(88, "Grimer", PokemonType.POISON, null, 80, 80, 50, 40, 50, 25)
        registerSpecies(89, "Muk", PokemonType.POISON, null, 105, 105, 75, 65, 100, 50)
        registerSpecies(90, "Shellder", PokemonType.WATER, null, 30, 65, 100, 45, 25, 40)
        registerSpecies(91, "Cloyster", PokemonType.WATER, PokemonType.ICE, 50, 95, 180, 85, 45, 70)
        registerSpecies(92, "Gastly", PokemonType.GHOST, PokemonType.POISON, 30, 35, 30, 100, 35, 80)
        registerSpecies(93, "Haunter", PokemonType.GHOST, PokemonType.POISON, 45, 50, 45, 115, 55, 95)
        registerSpecies(94, "Gengar", PokemonType.GHOST, PokemonType.POISON, 60, 65, 60, 130, 75, 110)
        registerSpecies(95, "Onix", PokemonType.ROCK, PokemonType.GROUND, 35, 45, 160, 30, 45, 70)
        registerSpecies(96, "Drowzee", PokemonType.PSYCHIC, null, 60, 48, 45, 43, 90, 42)
        registerSpecies(97, "Hypno", PokemonType.PSYCHIC, null, 85, 73, 70, 73, 115, 67)
        registerSpecies(98, "Krabby", PokemonType.WATER, null, 30, 105, 90, 25, 25, 50)
        registerSpecies(99, "Kingler", PokemonType.WATER, null, 55, 130, 115, 50, 50, 75)
        registerSpecies(100, "Voltorb", PokemonType.ELECTRIC, null, 40, 30, 50, 55, 55, 100)
        registerSpecies(101, "Electrode", PokemonType.ELECTRIC, null, 60, 50, 70, 80, 80, 150)
        registerSpecies(102, "Exeggcute", PokemonType.GRASS, PokemonType.PSYCHIC, 60, 40, 80, 60, 45, 40)
        registerSpecies(103, "Exeggutor", PokemonType.GRASS, PokemonType.PSYCHIC, 95, 95, 85, 125, 75, 55)
        registerSpecies(104, "Cubone", PokemonType.GROUND, null, 50, 50, 95, 40, 50, 35)
        registerSpecies(105, "Marowak", PokemonType.GROUND, null, 60, 80, 110, 50, 80, 45)
        registerSpecies(106, "Hitmonlee", PokemonType.FIGHTING, null, 50, 120, 53, 35, 110, 87)
        registerSpecies(107, "Hitmonchan", PokemonType.FIGHTING, null, 50, 105, 79, 35, 110, 76)
        registerSpecies(108, "Lickitung", PokemonType.NORMAL, null, 90, 55, 75, 60, 75, 30)
        registerSpecies(109, "Koffing", PokemonType.POISON, null, 40, 65, 95, 60, 45, 35)
        registerSpecies(110, "Weezing", PokemonType.POISON, null, 65, 90, 120, 85, 70, 60)
        registerSpecies(111, "Rhyhorn", PokemonType.GROUND, PokemonType.ROCK, 80, 85, 95, 30, 30, 25)
        registerSpecies(112, "Rhydon", PokemonType.GROUND, PokemonType.ROCK, 105, 130, 120, 45, 45, 40)
        registerSpecies(113, "Chansey", PokemonType.NORMAL, null, 250, 5, 5, 35, 105, 50)
        registerSpecies(114, "Tangela", PokemonType.GRASS, null, 65, 55, 115, 100, 40, 60)
        registerSpecies(115, "Kangaskhan", PokemonType.NORMAL, null, 105, 95, 80, 40, 80, 90)
        registerSpecies(116, "Horsea", PokemonType.WATER, null, 30, 40, 70, 70, 25, 60)
        registerSpecies(117, "Seadra", PokemonType.WATER, null, 55, 65, 95, 95, 45, 85)
        registerSpecies(118, "Goldeen", PokemonType.WATER, null, 45, 67, 60, 35, 50, 63)
        registerSpecies(119, "Seaking", PokemonType.WATER, null, 80, 92, 65, 65, 80, 68)
        registerSpecies(120, "Staryu", PokemonType.WATER, null, 30, 45, 55, 70, 55, 85)
        registerSpecies(121, "Starmie", PokemonType.WATER, PokemonType.PSYCHIC, 60, 75, 85, 100, 85, 115)
        registerSpecies(122, "Mr. Mime", PokemonType.PSYCHIC, PokemonType.FAIRY, 40, 45, 65, 100, 120, 90)
        registerSpecies(123, "Scyther", PokemonType.BUG, PokemonType.FLYING, 70, 110, 80, 55, 80, 105)
        registerSpecies(124, "Jynx", PokemonType.ICE, PokemonType.PSYCHIC, 65, 50, 35, 115, 95, 95)
        registerSpecies(125, "Electabuzz", PokemonType.ELECTRIC, null, 65, 83, 57, 95, 85, 105)
        registerSpecies(126, "Magmar", PokemonType.FIRE, null, 65, 95, 57, 100, 85, 93)
        registerSpecies(127, "Pinsir", PokemonType.BUG, null, 65, 125, 100, 55, 70, 85)
        registerSpecies(128, "Tauros", PokemonType.NORMAL, null, 75, 100, 95, 40, 70, 110)
        registerSpecies(129, "Magikarp", PokemonType.WATER, null, 20, 10, 55, 15, 20, 80)
        registerSpecies(130, "Gyarados", PokemonType.WATER, PokemonType.FLYING, 95, 125, 79, 60, 100, 81)
        registerSpecies(131, "Lapras", PokemonType.WATER, PokemonType.ICE, 130, 85, 80, 85, 95, 60)
        registerSpecies(132, "Ditto", PokemonType.NORMAL, null, 48, 48, 48, 48, 48, 48)
        registerSpecies(133, "Eevee", PokemonType.NORMAL, null, 55, 55, 50, 45, 65, 55)
        registerSpecies(134, "Vaporeon", PokemonType.WATER, null, 130, 65, 60, 110, 95, 65)
        registerSpecies(135, "Jolteon", PokemonType.ELECTRIC, null, 65, 65, 60, 110, 95, 130)
        registerSpecies(136, "Flareon", PokemonType.FIRE, null, 65, 130, 60, 95, 110, 65)
        registerSpecies(137, "Porygon", PokemonType.NORMAL, null, 65, 60, 70, 85, 75, 40)
        registerSpecies(138, "Omanyte", PokemonType.ROCK, PokemonType.WATER, 35, 40, 100, 90, 55, 35)
        registerSpecies(139, "Omastar", PokemonType.ROCK, PokemonType.WATER, 70, 60, 125, 115, 70, 55)
        registerSpecies(140, "Kabuto", PokemonType.ROCK, PokemonType.WATER, 30, 80, 90, 55, 45, 55)
        registerSpecies(141, "Kabutops", PokemonType.ROCK, PokemonType.WATER, 60, 115, 105, 65, 70, 80)
        registerSpecies(142, "Aerodactyl", PokemonType.ROCK, PokemonType.FLYING, 80, 105, 65, 60, 75, 130)
        registerSpecies(143, "Snorlax", PokemonType.NORMAL, null, 160, 110, 65, 65, 110, 30)
        registerSpecies(144, "Articuno", PokemonType.ICE, PokemonType.FLYING, 90, 85, 100, 95, 125, 85)
        registerSpecies(145, "Zapdos", PokemonType.ELECTRIC, PokemonType.FLYING, 90, 90, 85, 125, 90, 100)
        registerSpecies(146, "Moltres", PokemonType.FIRE, PokemonType.FLYING, 90, 100, 90, 125, 85, 90)
        registerSpecies(147, "Dratini", PokemonType.DRAGON, null, 41, 64, 45, 50, 50, 50)
        registerSpecies(148, "Dragonair", PokemonType.DRAGON, null, 61, 84, 65, 70, 70, 70)
        registerSpecies(149, "Dragonite", PokemonType.DRAGON, PokemonType.FLYING, 91, 134, 95, 100, 100, 80)
        registerSpecies(150, "Mewtwo", PokemonType.PSYCHIC, null, 106, 110, 90, 154, 90, 130)
        registerSpecies(151, "Mew", PokemonType.PSYCHIC, null, 100, 100, 100, 100, 100, 100)
        registerSpecies(152, "Chikorita", PokemonType.GRASS, null, 45, 49, 65, 49, 65, 45)
        registerSpecies(153, "Bayleef", PokemonType.GRASS, null, 60, 62, 80, 63, 80, 60)
        registerSpecies(154, "Meganium", PokemonType.GRASS, null, 80, 82, 100, 83, 100, 80)
        registerSpecies(155, "Cyndaquil", PokemonType.FIRE, null, 39, 52, 43, 60, 50, 65)
        registerSpecies(156, "Quilava", PokemonType.FIRE, null, 58, 64, 58, 80, 65, 80)
        registerSpecies(157, "Typhlosion", PokemonType.FIRE, null, 78, 84, 78, 109, 85, 100)
        registerSpecies(158, "Totodile", PokemonType.WATER, null, 50, 65, 64, 44, 48, 43)
        registerSpecies(159, "Croconaw", PokemonType.WATER, null, 65, 80, 80, 59, 63, 58)
        registerSpecies(160, "Feraligatr", PokemonType.WATER, null, 85, 105, 100, 79, 83, 78)
        registerSpecies(161, "Sentret", PokemonType.NORMAL, null, 35, 46, 34, 35, 45, 20)
        registerSpecies(162, "Furret", PokemonType.NORMAL, null, 85, 76, 64, 45, 55, 90)
        registerSpecies(163, "Hoothoot", PokemonType.NORMAL, PokemonType.FLYING, 60, 30, 30, 36, 56, 50)
        registerSpecies(164, "Noctowl", PokemonType.NORMAL, PokemonType.FLYING, 100, 50, 50, 86, 96, 70)
        registerSpecies(165, "Ledyba", PokemonType.BUG, PokemonType.FLYING, 40, 20, 30, 40, 80, 55)
        registerSpecies(166, "Ledian", PokemonType.BUG, PokemonType.FLYING, 55, 35, 50, 55, 110, 85)
        registerSpecies(167, "Spinarak", PokemonType.BUG, PokemonType.POISON, 40, 60, 40, 40, 40, 30)
        registerSpecies(168, "Ariados", PokemonType.BUG, PokemonType.POISON, 70, 90, 70, 60, 70, 40)
        registerSpecies(169, "Crobat", PokemonType.POISON, PokemonType.FLYING, 85, 90, 80, 70, 80, 130)
        registerSpecies(170, "Chinchou", PokemonType.WATER, PokemonType.ELECTRIC, 75, 38, 38, 56, 56, 67)
        registerSpecies(171, "Lanturn", PokemonType.WATER, PokemonType.ELECTRIC, 125, 58, 58, 76, 76, 67)
        registerSpecies(172, "Pichu", PokemonType.ELECTRIC, null, 20, 40, 15, 35, 35, 60)
        registerSpecies(173, "Cleffa", PokemonType.FAIRY, null, 50, 25, 28, 45, 55, 15)
        registerSpecies(174, "Igglybuff", PokemonType.NORMAL, PokemonType.FAIRY, 90, 30, 15, 40, 20, 15)
        registerSpecies(175, "Togepi", PokemonType.FAIRY, null, 35, 20, 65, 40, 65, 20)
        registerSpecies(176, "Togetic", PokemonType.FAIRY, PokemonType.FLYING, 55, 40, 85, 80, 105, 40)
        registerSpecies(177, "Natu", PokemonType.PSYCHIC, PokemonType.FLYING, 40, 50, 45, 70, 45, 70)
        registerSpecies(178, "Xatu", PokemonType.PSYCHIC, PokemonType.FLYING, 65, 75, 70, 95, 70, 95)
        registerSpecies(179, "Mareep", PokemonType.ELECTRIC, null, 55, 40, 40, 65, 45, 35)
        registerSpecies(180, "Flaaffy", PokemonType.ELECTRIC, null, 70, 55, 55, 80, 60, 45)
        registerSpecies(181, "Ampharos", PokemonType.ELECTRIC, null, 90, 75, 85, 115, 90, 55)
        registerSpecies(182, "Bellossom", PokemonType.GRASS, null, 75, 80, 95, 90, 100, 50)
        registerSpecies(183, "Marill", PokemonType.WATER, PokemonType.FAIRY, 70, 20, 50, 20, 50, 40)
        registerSpecies(184, "Azumarill", PokemonType.WATER, PokemonType.FAIRY, 100, 50, 80, 60, 80, 50)
        registerSpecies(185, "Sudowoodo", PokemonType.ROCK, null, 70, 100, 115, 30, 65, 30)
        registerSpecies(186, "Politoed", PokemonType.WATER, null, 90, 75, 75, 90, 100, 70)
        registerSpecies(187, "Hoppip", PokemonType.GRASS, PokemonType.FLYING, 35, 35, 40, 35, 55, 50)
        registerSpecies(188, "Skiploom", PokemonType.GRASS, PokemonType.FLYING, 55, 45, 50, 45, 65, 80)
        registerSpecies(189, "Jumpluff", PokemonType.GRASS, PokemonType.FLYING, 75, 55, 70, 55, 95, 110)
        registerSpecies(190, "Aipom", PokemonType.NORMAL, null, 55, 70, 55, 40, 55, 85)
        registerSpecies(191, "Sunkern", PokemonType.GRASS, null, 30, 30, 30, 30, 30, 30)
        registerSpecies(192, "Sunflora", PokemonType.GRASS, null, 75, 75, 55, 105, 85, 30)
        registerSpecies(193, "Yanma", PokemonType.BUG, PokemonType.FLYING, 65, 65, 45, 75, 45, 95)
        registerSpecies(194, "Wooper", PokemonType.WATER, PokemonType.GROUND, 55, 45, 45, 25, 25, 15)
        registerSpecies(195, "Quagsire", PokemonType.WATER, PokemonType.GROUND, 95, 85, 85, 65, 65, 35)
        registerSpecies(196, "Espeon", PokemonType.PSYCHIC, null, 65, 65, 60, 130, 95, 110)
        registerSpecies(197, "Umbreon", PokemonType.DARK, null, 95, 65, 110, 60, 130, 65)
        registerSpecies(198, "Murkrow", PokemonType.DARK, PokemonType.FLYING, 60, 85, 42, 85, 42, 91)
        registerSpecies(199, "Slowking", PokemonType.WATER, PokemonType.PSYCHIC, 95, 75, 80, 100, 110, 30)
        registerSpecies(200, "Misdreavus", PokemonType.GHOST, null, 60, 60, 60, 85, 85, 85)
    }

    private fun registerSpeciesChunk2() {
        registerSpecies(201, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(202, "Wobbuffet", PokemonType.PSYCHIC, null, 190, 33, 58, 33, 58, 33)
        registerSpecies(203, "Girafarig", PokemonType.NORMAL, PokemonType.PSYCHIC, 70, 80, 65, 90, 65, 85)
        registerSpecies(204, "Pineco", PokemonType.BUG, null, 50, 65, 90, 35, 35, 15)
        registerSpecies(205, "Forretress", PokemonType.BUG, PokemonType.STEEL, 75, 90, 140, 60, 60, 40)
        registerSpecies(206, "Dunsparce", PokemonType.NORMAL, null, 100, 70, 70, 65, 65, 45)
        registerSpecies(207, "Gligar", PokemonType.GROUND, PokemonType.FLYING, 65, 75, 105, 35, 65, 85)
        registerSpecies(208, "Steelix", PokemonType.STEEL, PokemonType.GROUND, 75, 85, 200, 55, 65, 30)
        registerSpecies(209, "Snubbull", PokemonType.FAIRY, null, 60, 80, 50, 40, 40, 30)
        registerSpecies(210, "Granbull", PokemonType.FAIRY, null, 90, 120, 75, 60, 60, 45)
        registerSpecies(211, "Qwilfish", PokemonType.WATER, PokemonType.POISON, 65, 95, 85, 55, 55, 85)
        registerSpecies(212, "Scizor", PokemonType.BUG, PokemonType.STEEL, 70, 130, 100, 55, 80, 65)
        registerSpecies(213, "Shuckle", PokemonType.BUG, PokemonType.ROCK, 20, 10, 230, 10, 230, 5)
        registerSpecies(214, "Heracross", PokemonType.BUG, PokemonType.FIGHTING, 80, 125, 75, 40, 95, 85)
        registerSpecies(215, "Sneasel", PokemonType.DARK, PokemonType.ICE, 55, 95, 55, 35, 75, 115)
        registerSpecies(216, "Teddiursa", PokemonType.NORMAL, null, 60, 80, 50, 50, 50, 40)
        registerSpecies(217, "Ursaring", PokemonType.NORMAL, null, 90, 130, 75, 75, 75, 55)
        registerSpecies(218, "Slugma", PokemonType.FIRE, null, 40, 40, 40, 70, 40, 20)
        registerSpecies(219, "Magcargo", PokemonType.FIRE, PokemonType.ROCK, 60, 50, 120, 90, 80, 30)
        registerSpecies(220, "Swinub", PokemonType.ICE, PokemonType.GROUND, 50, 50, 40, 30, 30, 50)
        registerSpecies(221, "Piloswine", PokemonType.ICE, PokemonType.GROUND, 100, 100, 80, 60, 60, 50)
        registerSpecies(222, "Corsola", PokemonType.WATER, PokemonType.ROCK, 65, 55, 95, 65, 95, 35)
        registerSpecies(223, "Remoraid", PokemonType.WATER, null, 35, 65, 35, 65, 35, 65)
        registerSpecies(224, "Octillery", PokemonType.WATER, null, 75, 105, 75, 105, 75, 45)
        registerSpecies(225, "Delibird", PokemonType.ICE, PokemonType.FLYING, 45, 55, 45, 65, 45, 75)
        registerSpecies(226, "Mantine", PokemonType.WATER, PokemonType.FLYING, 85, 40, 70, 80, 140, 70)
        registerSpecies(227, "Skarmory", PokemonType.STEEL, PokemonType.FLYING, 65, 80, 140, 40, 70, 70)
        registerSpecies(228, "Houndour", PokemonType.DARK, PokemonType.FIRE, 45, 60, 30, 80, 50, 65)
        registerSpecies(229, "Houndoom", PokemonType.DARK, PokemonType.FIRE, 75, 90, 50, 110, 80, 95)
        registerSpecies(230, "Kingdra", PokemonType.WATER, PokemonType.DRAGON, 75, 95, 95, 95, 95, 85)
        registerSpecies(231, "Phanpy", PokemonType.GROUND, null, 90, 60, 60, 40, 40, 40)
        registerSpecies(232, "Donphan", PokemonType.GROUND, null, 90, 120, 120, 60, 60, 50)
        registerSpecies(233, "Porygon2", PokemonType.NORMAL, null, 85, 80, 90, 105, 95, 60)
        registerSpecies(234, "Stantler", PokemonType.NORMAL, null, 73, 95, 62, 85, 65, 85)
        registerSpecies(235, "Smeargle", PokemonType.NORMAL, null, 55, 20, 35, 20, 45, 75)
        registerSpecies(236, "Tyrogue", PokemonType.FIGHTING, null, 35, 35, 35, 35, 35, 35)
        registerSpecies(237, "Hitmontop", PokemonType.FIGHTING, null, 50, 95, 95, 35, 110, 70)
        registerSpecies(238, "Smoochum", PokemonType.ICE, PokemonType.PSYCHIC, 45, 30, 15, 85, 65, 65)
        registerSpecies(239, "Elekid", PokemonType.ELECTRIC, null, 45, 63, 37, 65, 55, 95)
        registerSpecies(240, "Magby", PokemonType.FIRE, null, 45, 75, 37, 70, 55, 83)
        registerSpecies(241, "Miltank", PokemonType.NORMAL, null, 95, 80, 105, 40, 70, 100)
        registerSpecies(242, "Blissey", PokemonType.NORMAL, null, 255, 10, 10, 75, 135, 55)
        registerSpecies(243, "Raikou", PokemonType.ELECTRIC, null, 90, 85, 75, 115, 100, 115)
        registerSpecies(244, "Entei", PokemonType.FIRE, null, 115, 115, 85, 90, 75, 100)
        registerSpecies(245, "Suicune", PokemonType.WATER, null, 100, 75, 115, 90, 115, 85)
        registerSpecies(246, "Larvitar", PokemonType.ROCK, PokemonType.GROUND, 50, 64, 50, 45, 50, 41)
        registerSpecies(247, "Pupitar", PokemonType.ROCK, PokemonType.GROUND, 70, 84, 70, 65, 70, 51)
        registerSpecies(248, "Tyranitar", PokemonType.ROCK, PokemonType.DARK, 100, 134, 110, 95, 100, 61)
        registerSpecies(249, "Lugia", PokemonType.PSYCHIC, PokemonType.FLYING, 106, 90, 130, 90, 154, 110)
        registerSpecies(250, "Ho-Oh", PokemonType.FIRE, PokemonType.FLYING, 106, 130, 90, 110, 154, 90)
        registerSpecies(251, "Celebi", PokemonType.PSYCHIC, PokemonType.GRASS, 100, 100, 100, 100, 100, 100)
        registerSpecies(252, "Treecko", PokemonType.GRASS, null, 40, 45, 35, 65, 55, 70)
        registerSpecies(253, "Grovyle", PokemonType.GRASS, null, 50, 65, 45, 85, 65, 95)
        registerSpecies(254, "Sceptile", PokemonType.GRASS, null, 70, 85, 65, 105, 85, 120)
        registerSpecies(255, "Torchic", PokemonType.FIRE, null, 45, 60, 40, 70, 50, 45)
        registerSpecies(256, "Combusken", PokemonType.FIRE, PokemonType.FIGHTING, 60, 85, 60, 85, 60, 55)
        registerSpecies(257, "Blaziken", PokemonType.FIRE, PokemonType.FIGHTING, 80, 120, 70, 110, 70, 80)
        registerSpecies(258, "Mudkip", PokemonType.WATER, null, 50, 70, 50, 50, 50, 40)
        registerSpecies(259, "Marshtomp", PokemonType.WATER, PokemonType.GROUND, 70, 85, 70, 60, 70, 50)
        registerSpecies(260, "Swampert", PokemonType.WATER, PokemonType.GROUND, 100, 110, 90, 85, 90, 60)
        registerSpecies(261, "Poochyena", PokemonType.DARK, null, 35, 55, 35, 30, 30, 35)
        registerSpecies(262, "Mightyena", PokemonType.DARK, null, 70, 90, 70, 60, 60, 70)
        registerSpecies(263, "Zigzagoon", PokemonType.NORMAL, null, 38, 30, 41, 30, 41, 60)
        registerSpecies(264, "Linoone", PokemonType.NORMAL, null, 78, 70, 61, 50, 61, 100)
        registerSpecies(265, "Wurmple", PokemonType.BUG, null, 45, 45, 35, 20, 30, 20)
        registerSpecies(266, "Silcoon", PokemonType.BUG, null, 50, 35, 55, 25, 25, 15)
        registerSpecies(267, "Beautifly", PokemonType.BUG, PokemonType.FLYING, 60, 70, 50, 100, 50, 65)
        registerSpecies(268, "Cascoon", PokemonType.BUG, null, 50, 35, 55, 25, 25, 15)
        registerSpecies(269, "Dustox", PokemonType.BUG, PokemonType.POISON, 60, 50, 70, 50, 90, 65)
        registerSpecies(270, "Lotad", PokemonType.WATER, PokemonType.GRASS, 40, 30, 30, 40, 50, 30)
        registerSpecies(271, "Lombre", PokemonType.WATER, PokemonType.GRASS, 60, 50, 50, 60, 70, 50)
        registerSpecies(272, "Ludicolo", PokemonType.WATER, PokemonType.GRASS, 80, 70, 70, 90, 100, 70)
        registerSpecies(273, "Seedot", PokemonType.GRASS, null, 40, 40, 50, 30, 30, 30)
        registerSpecies(274, "Nuzleaf", PokemonType.GRASS, PokemonType.DARK, 70, 70, 40, 60, 40, 60)
        registerSpecies(275, "Shiftry", PokemonType.GRASS, PokemonType.DARK, 90, 100, 60, 90, 60, 80)
        registerSpecies(276, "Taillow", PokemonType.NORMAL, PokemonType.FLYING, 40, 55, 30, 30, 30, 85)
        registerSpecies(277, "Swellow", PokemonType.NORMAL, PokemonType.FLYING, 60, 85, 60, 75, 50, 125)
        registerSpecies(278, "Wingull", PokemonType.WATER, PokemonType.FLYING, 40, 30, 30, 55, 30, 85)
        registerSpecies(279, "Pelipper", PokemonType.WATER, PokemonType.FLYING, 60, 50, 100, 95, 70, 65)
        registerSpecies(280, "Ralts", PokemonType.PSYCHIC, PokemonType.FAIRY, 28, 25, 25, 45, 35, 40)
        registerSpecies(281, "Kirlia", PokemonType.PSYCHIC, PokemonType.FAIRY, 38, 35, 35, 65, 55, 50)
        registerSpecies(282, "Gardevoir", PokemonType.PSYCHIC, PokemonType.FAIRY, 68, 65, 65, 125, 115, 80)
        registerSpecies(283, "Surskit", PokemonType.BUG, PokemonType.WATER, 40, 30, 32, 50, 52, 65)
        registerSpecies(284, "Masquerain", PokemonType.BUG, PokemonType.FLYING, 70, 60, 62, 100, 82, 80)
        registerSpecies(285, "Shroomish", PokemonType.GRASS, null, 60, 40, 60, 40, 60, 35)
        registerSpecies(286, "Breloom", PokemonType.GRASS, PokemonType.FIGHTING, 60, 130, 80, 60, 60, 70)
        registerSpecies(287, "Slakoth", PokemonType.NORMAL, null, 60, 60, 60, 35, 35, 30)
        registerSpecies(288, "Vigoroth", PokemonType.NORMAL, null, 80, 80, 80, 55, 55, 90)
        registerSpecies(289, "Slaking", PokemonType.NORMAL, null, 150, 160, 100, 95, 65, 100)
        registerSpecies(290, "Nincada", PokemonType.BUG, PokemonType.GROUND, 31, 45, 90, 30, 30, 40)
        registerSpecies(291, "Ninjask", PokemonType.BUG, PokemonType.FLYING, 61, 90, 45, 50, 50, 160)
        registerSpecies(292, "Shedinja", PokemonType.BUG, PokemonType.GHOST, 1, 90, 45, 30, 30, 40)
        registerSpecies(293, "Whismur", PokemonType.NORMAL, null, 64, 51, 23, 51, 23, 28)
        registerSpecies(294, "Loudred", PokemonType.NORMAL, null, 84, 71, 43, 71, 43, 48)
        registerSpecies(295, "Exploud", PokemonType.NORMAL, null, 104, 91, 63, 91, 73, 68)
        registerSpecies(296, "Makuhita", PokemonType.FIGHTING, null, 72, 60, 30, 20, 30, 25)
        registerSpecies(297, "Hariyama", PokemonType.FIGHTING, null, 144, 120, 60, 40, 60, 50)
        registerSpecies(298, "Azurill", PokemonType.NORMAL, PokemonType.FAIRY, 50, 20, 40, 20, 40, 20)
        registerSpecies(299, "Nosepass", PokemonType.ROCK, null, 30, 45, 135, 45, 90, 30)
        registerSpecies(300, "Skitty", PokemonType.NORMAL, null, 50, 45, 45, 35, 35, 50)
        registerSpecies(301, "Delcatty", PokemonType.NORMAL, null, 70, 65, 65, 55, 55, 90)
        registerSpecies(302, "Sableye", PokemonType.DARK, PokemonType.GHOST, 50, 75, 75, 65, 65, 50)
        registerSpecies(303, "Mawile", PokemonType.STEEL, PokemonType.FAIRY, 50, 85, 85, 55, 55, 50)
        registerSpecies(304, "Aron", PokemonType.STEEL, PokemonType.ROCK, 50, 70, 100, 40, 40, 30)
        registerSpecies(305, "Lairon", PokemonType.STEEL, PokemonType.ROCK, 60, 90, 140, 50, 50, 40)
        registerSpecies(306, "Aggron", PokemonType.STEEL, PokemonType.ROCK, 70, 110, 180, 60, 60, 50)
        registerSpecies(307, "Meditite", PokemonType.FIGHTING, PokemonType.PSYCHIC, 30, 40, 55, 40, 55, 60)
        registerSpecies(308, "Medicham", PokemonType.FIGHTING, PokemonType.PSYCHIC, 60, 60, 75, 60, 75, 80)
        registerSpecies(309, "Electrike", PokemonType.ELECTRIC, null, 40, 45, 40, 65, 40, 65)
        registerSpecies(310, "Manectric", PokemonType.ELECTRIC, null, 70, 75, 60, 105, 60, 105)
        registerSpecies(311, "Plusle", PokemonType.ELECTRIC, null, 60, 50, 40, 85, 75, 95)
        registerSpecies(312, "Minun", PokemonType.ELECTRIC, null, 60, 40, 50, 75, 85, 95)
        registerSpecies(313, "Volbeat", PokemonType.BUG, null, 65, 73, 75, 47, 85, 85)
        registerSpecies(314, "Illumise", PokemonType.BUG, null, 65, 47, 75, 73, 85, 85)
        registerSpecies(315, "Roselia", PokemonType.GRASS, PokemonType.POISON, 50, 60, 45, 100, 80, 65)
        registerSpecies(316, "Gulpin", PokemonType.POISON, null, 70, 43, 53, 43, 53, 40)
        registerSpecies(317, "Swalot", PokemonType.POISON, null, 100, 73, 83, 73, 83, 55)
        registerSpecies(318, "Carvanha", PokemonType.WATER, PokemonType.DARK, 45, 90, 20, 65, 20, 65)
        registerSpecies(319, "Sharpedo", PokemonType.WATER, PokemonType.DARK, 70, 120, 40, 95, 40, 95)
        registerSpecies(320, "Wailmer", PokemonType.WATER, null, 130, 70, 35, 70, 35, 60)
        registerSpecies(321, "Wailord", PokemonType.WATER, null, 170, 90, 45, 90, 45, 60)
        registerSpecies(322, "Numel", PokemonType.FIRE, PokemonType.GROUND, 60, 60, 40, 65, 45, 35)
        registerSpecies(323, "Camerupt", PokemonType.FIRE, PokemonType.GROUND, 70, 100, 70, 105, 75, 40)
        registerSpecies(324, "Torkoal", PokemonType.FIRE, null, 70, 85, 140, 85, 70, 20)
        registerSpecies(325, "Spoink", PokemonType.PSYCHIC, null, 60, 25, 35, 70, 80, 60)
        registerSpecies(326, "Grumpig", PokemonType.PSYCHIC, null, 80, 45, 65, 90, 110, 80)
        registerSpecies(327, "Spinda", PokemonType.NORMAL, null, 60, 60, 60, 60, 60, 60)
        registerSpecies(328, "Trapinch", PokemonType.GROUND, null, 45, 100, 45, 45, 45, 10)
        registerSpecies(329, "Vibrava", PokemonType.GROUND, PokemonType.DRAGON, 50, 70, 50, 50, 50, 70)
        registerSpecies(330, "Flygon", PokemonType.GROUND, PokemonType.DRAGON, 80, 100, 80, 80, 80, 100)
        registerSpecies(331, "Cacnea", PokemonType.GRASS, null, 50, 85, 40, 85, 40, 35)
        registerSpecies(332, "Cacturne", PokemonType.GRASS, PokemonType.DARK, 70, 115, 60, 115, 60, 55)
        registerSpecies(333, "Swablu", PokemonType.NORMAL, PokemonType.FLYING, 45, 40, 60, 40, 75, 50)
        registerSpecies(334, "Altaria", PokemonType.DRAGON, PokemonType.FLYING, 75, 70, 90, 70, 105, 80)
        registerSpecies(335, "Zangoose", PokemonType.NORMAL, null, 73, 115, 60, 60, 60, 90)
        registerSpecies(336, "Seviper", PokemonType.POISON, null, 73, 100, 60, 100, 60, 65)
        registerSpecies(337, "Lunatone", PokemonType.ROCK, PokemonType.PSYCHIC, 90, 55, 65, 95, 85, 70)
        registerSpecies(338, "Solrock", PokemonType.ROCK, PokemonType.PSYCHIC, 90, 95, 85, 55, 65, 70)
        registerSpecies(339, "Barboach", PokemonType.WATER, PokemonType.GROUND, 50, 48, 43, 46, 41, 60)
        registerSpecies(340, "Whiscash", PokemonType.WATER, PokemonType.GROUND, 110, 78, 73, 76, 71, 60)
        registerSpecies(341, "Corphish", PokemonType.WATER, null, 43, 80, 65, 50, 35, 35)
        registerSpecies(342, "Crawdaunt", PokemonType.WATER, PokemonType.DARK, 63, 120, 85, 90, 55, 55)
        registerSpecies(343, "Baltoy", PokemonType.GROUND, PokemonType.PSYCHIC, 40, 40, 55, 40, 70, 55)
        registerSpecies(344, "Claydol", PokemonType.GROUND, PokemonType.PSYCHIC, 60, 70, 105, 70, 120, 75)
        registerSpecies(345, "Lileep", PokemonType.ROCK, PokemonType.GRASS, 66, 41, 77, 61, 87, 23)
        registerSpecies(346, "Cradily", PokemonType.ROCK, PokemonType.GRASS, 86, 81, 97, 81, 107, 43)
        registerSpecies(347, "Anorith", PokemonType.ROCK, PokemonType.BUG, 45, 95, 50, 40, 50, 75)
        registerSpecies(348, "Armaldo", PokemonType.ROCK, PokemonType.BUG, 75, 125, 100, 70, 80, 45)
        registerSpecies(349, "Feebas", PokemonType.WATER, null, 20, 15, 20, 10, 55, 80)
        registerSpecies(350, "Milotic", PokemonType.WATER, null, 95, 60, 79, 100, 125, 81)
        registerSpecies(351, "Castform", PokemonType.NORMAL, null, 70, 70, 70, 70, 70, 70)
        registerSpecies(352, "Kecleon", PokemonType.NORMAL, null, 60, 90, 70, 60, 120, 40)
        registerSpecies(353, "Shuppet", PokemonType.GHOST, null, 44, 75, 35, 63, 33, 45)
        registerSpecies(354, "Banette", PokemonType.GHOST, null, 64, 115, 65, 83, 63, 65)
        registerSpecies(355, "Duskull", PokemonType.GHOST, null, 20, 40, 90, 30, 90, 25)
        registerSpecies(356, "Dusclops", PokemonType.GHOST, null, 40, 70, 130, 60, 130, 25)
        registerSpecies(357, "Tropius", PokemonType.GRASS, PokemonType.FLYING, 99, 68, 83, 72, 87, 51)
        registerSpecies(358, "Chimecho", PokemonType.PSYCHIC, null, 75, 50, 80, 95, 90, 65)
        registerSpecies(359, "Absol", PokemonType.DARK, null, 65, 130, 60, 75, 60, 75)
        registerSpecies(360, "Wynaut", PokemonType.PSYCHIC, null, 95, 23, 48, 23, 48, 23)
        registerSpecies(361, "Snorunt", PokemonType.ICE, null, 50, 50, 50, 50, 50, 50)
        registerSpecies(362, "Glalie", PokemonType.ICE, null, 80, 80, 80, 80, 80, 80)
        registerSpecies(363, "Spheal", PokemonType.ICE, PokemonType.WATER, 70, 40, 50, 55, 50, 25)
        registerSpecies(364, "Sealeo", PokemonType.ICE, PokemonType.WATER, 90, 60, 70, 75, 70, 45)
        registerSpecies(365, "Walrein", PokemonType.ICE, PokemonType.WATER, 110, 80, 90, 95, 90, 65)
        registerSpecies(366, "Clamperl", PokemonType.WATER, null, 35, 64, 85, 74, 55, 32)
        registerSpecies(367, "Huntail", PokemonType.WATER, null, 55, 104, 105, 94, 75, 52)
        registerSpecies(368, "Gorebyss", PokemonType.WATER, null, 55, 84, 105, 114, 75, 52)
        registerSpecies(369, "Relicanth", PokemonType.WATER, PokemonType.ROCK, 100, 90, 130, 45, 65, 55)
        registerSpecies(370, "Luvdisc", PokemonType.WATER, null, 43, 30, 55, 40, 65, 97)
        registerSpecies(371, "Bagon", PokemonType.DRAGON, null, 45, 75, 60, 40, 30, 50)
        registerSpecies(372, "Shelgon", PokemonType.DRAGON, null, 65, 95, 100, 60, 50, 50)
        registerSpecies(373, "Salamence", PokemonType.DRAGON, PokemonType.FLYING, 95, 135, 80, 110, 80, 100)
        registerSpecies(374, "Beldum", PokemonType.STEEL, PokemonType.PSYCHIC, 40, 55, 80, 35, 60, 30)
        registerSpecies(375, "Metang", PokemonType.STEEL, PokemonType.PSYCHIC, 60, 75, 100, 55, 80, 50)
        registerSpecies(376, "Metagross", PokemonType.STEEL, PokemonType.PSYCHIC, 80, 135, 130, 95, 90, 70)
        registerSpecies(377, "Regirock", PokemonType.ROCK, null, 80, 100, 200, 50, 100, 50)
        registerSpecies(378, "Regice", PokemonType.ICE, null, 80, 50, 100, 100, 200, 50)
        registerSpecies(379, "Registeel", PokemonType.STEEL, null, 80, 75, 150, 75, 150, 50)
        registerSpecies(380, "Latias", PokemonType.DRAGON, PokemonType.PSYCHIC, 80, 80, 90, 110, 130, 110)
        registerSpecies(381, "Latios", PokemonType.DRAGON, PokemonType.PSYCHIC, 80, 90, 80, 130, 110, 110)
        registerSpecies(382, "Kyogre", PokemonType.WATER, null, 100, 100, 90, 150, 140, 90)
        registerSpecies(383, "Groudon", PokemonType.GROUND, null, 100, 150, 140, 100, 90, 90)
        registerSpecies(384, "Rayquaza", PokemonType.DRAGON, PokemonType.FLYING, 105, 150, 90, 150, 90, 95)
        registerSpecies(385, "Jirachi", PokemonType.STEEL, PokemonType.PSYCHIC, 100, 100, 100, 100, 100, 100)
        registerSpecies(386, "Deoxys", PokemonType.PSYCHIC, null, 50, 150, 50, 150, 50, 150)
        registerSpecies(387, "Turtwig", PokemonType.GRASS, null, 55, 68, 64, 45, 55, 31)
        registerSpecies(388, "Grotle", PokemonType.GRASS, null, 75, 89, 85, 55, 65, 36)
        registerSpecies(389, "Torterra", PokemonType.GRASS, PokemonType.GROUND, 95, 109, 105, 75, 85, 56)
        registerSpecies(390, "Chimchar", PokemonType.FIRE, null, 44, 58, 44, 58, 44, 61)
        registerSpecies(391, "Monferno", PokemonType.FIRE, PokemonType.FIGHTING, 64, 78, 52, 78, 52, 81)
        registerSpecies(392, "Infernape", PokemonType.FIRE, PokemonType.FIGHTING, 76, 104, 71, 104, 71, 108)
        registerSpecies(393, "Piplup", PokemonType.WATER, null, 53, 51, 53, 61, 56, 40)
        registerSpecies(394, "Prinplup", PokemonType.WATER, null, 64, 66, 68, 81, 76, 50)
        registerSpecies(395, "Empoleon", PokemonType.WATER, PokemonType.STEEL, 84, 86, 88, 111, 101, 60)
        registerSpecies(396, "Starly", PokemonType.NORMAL, PokemonType.FLYING, 40, 55, 30, 30, 30, 60)
        registerSpecies(397, "Staravia", PokemonType.NORMAL, PokemonType.FLYING, 55, 75, 50, 40, 40, 80)
        registerSpecies(398, "Staraptor", PokemonType.NORMAL, PokemonType.FLYING, 85, 120, 70, 50, 60, 100)
        registerSpecies(399, "Bidoof", PokemonType.NORMAL, null, 59, 45, 40, 35, 40, 31)
        registerSpecies(400, "Bibarel", PokemonType.NORMAL, PokemonType.WATER, 79, 85, 60, 55, 60, 71)
    }

    private fun registerSpeciesChunk3() {
        registerSpecies(401, "Kricketot", PokemonType.BUG, null, 37, 25, 41, 25, 41, 25)
        registerSpecies(402, "Kricketune", PokemonType.BUG, null, 77, 85, 51, 55, 51, 65)
        registerSpecies(403, "Shinx", PokemonType.ELECTRIC, null, 45, 65, 34, 40, 34, 45)
        registerSpecies(404, "Luxio", PokemonType.ELECTRIC, null, 60, 85, 49, 60, 49, 60)
        registerSpecies(405, "Luxray", PokemonType.ELECTRIC, null, 80, 120, 79, 95, 79, 70)
        registerSpecies(406, "Budew", PokemonType.GRASS, PokemonType.POISON, 40, 30, 35, 50, 70, 55)
        registerSpecies(407, "Roserade", PokemonType.GRASS, PokemonType.POISON, 60, 70, 65, 125, 105, 90)
        registerSpecies(408, "Cranidos", PokemonType.ROCK, null, 67, 125, 40, 30, 30, 58)
        registerSpecies(409, "Rampardos", PokemonType.ROCK, null, 97, 165, 60, 65, 50, 58)
        registerSpecies(410, "Shieldon", PokemonType.ROCK, PokemonType.STEEL, 30, 42, 118, 42, 88, 30)
        registerSpecies(411, "Bastiodon", PokemonType.ROCK, PokemonType.STEEL, 60, 52, 168, 47, 138, 30)
        registerSpecies(412, "Burmy", PokemonType.BUG, null, 40, 29, 45, 29, 45, 36)
        registerSpecies(413, "Wormadam", PokemonType.BUG, PokemonType.GRASS, 60, 59, 85, 79, 105, 36)
        registerSpecies(414, "Mothim", PokemonType.BUG, PokemonType.FLYING, 70, 94, 50, 94, 50, 66)
        registerSpecies(415, "Combee", PokemonType.BUG, PokemonType.FLYING, 30, 30, 42, 30, 42, 70)
        registerSpecies(416, "Vespiquen", PokemonType.BUG, PokemonType.FLYING, 70, 80, 102, 80, 102, 40)
        registerSpecies(417, "Pachirisu", PokemonType.ELECTRIC, null, 60, 45, 70, 45, 90, 95)
        registerSpecies(418, "Buizel", PokemonType.WATER, null, 55, 65, 35, 60, 30, 85)
        registerSpecies(419, "Floatzel", PokemonType.WATER, null, 85, 105, 55, 85, 50, 115)
        registerSpecies(420, "Cherubi", PokemonType.GRASS, null, 45, 35, 45, 62, 53, 35)
        registerSpecies(421, "Cherrim", PokemonType.GRASS, null, 70, 60, 70, 87, 78, 85)
        registerSpecies(422, "Shellos", PokemonType.WATER, null, 76, 48, 48, 57, 62, 34)
        registerSpecies(423, "Gastrodon", PokemonType.WATER, PokemonType.GROUND, 111, 83, 68, 92, 82, 39)
        registerSpecies(424, "Ambipom", PokemonType.NORMAL, null, 75, 100, 66, 60, 66, 115)
        registerSpecies(425, "Drifloon", PokemonType.GHOST, PokemonType.FLYING, 90, 50, 34, 60, 44, 70)
        registerSpecies(426, "Drifblim", PokemonType.GHOST, PokemonType.FLYING, 150, 80, 44, 90, 54, 80)
        registerSpecies(427, "Buneary", PokemonType.NORMAL, null, 55, 66, 44, 44, 56, 85)
        registerSpecies(428, "Lopunny", PokemonType.NORMAL, null, 65, 76, 84, 54, 96, 105)
        registerSpecies(429, "Mismagius", PokemonType.GHOST, null, 60, 60, 60, 105, 105, 105)
        registerSpecies(430, "Honchkrow", PokemonType.DARK, PokemonType.FLYING, 100, 125, 52, 105, 52, 71)
        registerSpecies(431, "Glameow", PokemonType.NORMAL, null, 49, 55, 42, 42, 37, 85)
        registerSpecies(432, "Purugly", PokemonType.NORMAL, null, 71, 82, 64, 64, 59, 112)
        registerSpecies(433, "Chingling", PokemonType.PSYCHIC, null, 45, 30, 50, 65, 50, 45)
        registerSpecies(434, "Stunky", PokemonType.POISON, PokemonType.DARK, 63, 63, 47, 41, 41, 74)
        registerSpecies(435, "Skuntank", PokemonType.POISON, PokemonType.DARK, 103, 93, 67, 71, 61, 84)
        registerSpecies(436, "Bronzor", PokemonType.STEEL, PokemonType.PSYCHIC, 57, 24, 86, 24, 86, 23)
        registerSpecies(437, "Bronzong", PokemonType.STEEL, PokemonType.PSYCHIC, 67, 89, 116, 79, 116, 33)
        registerSpecies(438, "Bonsly", PokemonType.ROCK, null, 50, 80, 95, 10, 45, 10)
        registerSpecies(439, "Mime Jr.", PokemonType.PSYCHIC, PokemonType.FAIRY, 20, 25, 45, 70, 90, 60)
        registerSpecies(440, "Happiny", PokemonType.NORMAL, null, 100, 5, 5, 15, 65, 30)
        registerSpecies(441, "Chatot", PokemonType.NORMAL, PokemonType.FLYING, 76, 65, 45, 92, 42, 91)
        registerSpecies(442, "Spiritomb", PokemonType.GHOST, PokemonType.DARK, 50, 92, 108, 92, 108, 35)
        registerSpecies(443, "Gible", PokemonType.DRAGON, PokemonType.GROUND, 58, 70, 45, 40, 45, 42)
        registerSpecies(444, "Gabite", PokemonType.DRAGON, PokemonType.GROUND, 68, 90, 65, 50, 55, 82)
        registerSpecies(445, "Garchomp", PokemonType.DRAGON, PokemonType.GROUND, 108, 130, 95, 80, 85, 102)
        registerSpecies(446, "Munchlax", PokemonType.NORMAL, null, 135, 85, 40, 40, 85, 5)
        registerSpecies(447, "Riolu", PokemonType.FIGHTING, null, 40, 70, 40, 35, 40, 60)
        registerSpecies(448, "Lucario", PokemonType.FIGHTING, PokemonType.STEEL, 70, 110, 70, 115, 70, 90)
        registerSpecies(449, "Hippopotas", PokemonType.GROUND, null, 68, 72, 78, 38, 42, 32)
        registerSpecies(450, "Hippowdon", PokemonType.GROUND, null, 108, 112, 118, 68, 72, 47)
        registerSpecies(451, "Skorupi", PokemonType.POISON, PokemonType.BUG, 40, 50, 90, 30, 55, 65)
        registerSpecies(452, "Drapion", PokemonType.POISON, PokemonType.DARK, 70, 90, 110, 60, 75, 95)
        registerSpecies(453, "Croagunk", PokemonType.POISON, PokemonType.FIGHTING, 48, 61, 40, 61, 40, 50)
        registerSpecies(454, "Toxicroak", PokemonType.POISON, PokemonType.FIGHTING, 83, 106, 65, 86, 65, 85)
        registerSpecies(455, "Carnivine", PokemonType.GRASS, null, 74, 100, 72, 90, 72, 46)
        registerSpecies(456, "Finneon", PokemonType.WATER, null, 49, 49, 56, 49, 61, 66)
        registerSpecies(457, "Lumineon", PokemonType.WATER, null, 69, 69, 76, 69, 86, 91)
        registerSpecies(458, "Mantyke", PokemonType.WATER, PokemonType.FLYING, 45, 20, 50, 60, 120, 50)
        registerSpecies(459, "Snover", PokemonType.GRASS, PokemonType.ICE, 60, 62, 50, 62, 60, 40)
        registerSpecies(460, "Abomasnow", PokemonType.GRASS, PokemonType.ICE, 90, 92, 75, 92, 85, 60)
        registerSpecies(461, "Weavile", PokemonType.DARK, PokemonType.ICE, 70, 120, 65, 45, 85, 125)
        registerSpecies(462, "Magnezone", PokemonType.ELECTRIC, PokemonType.STEEL, 70, 70, 115, 130, 90, 60)
        registerSpecies(463, "Lickilicky", PokemonType.NORMAL, null, 110, 85, 95, 80, 95, 50)
        registerSpecies(464, "Rhyperior", PokemonType.GROUND, PokemonType.ROCK, 115, 140, 130, 55, 55, 40)
        registerSpecies(465, "Tangrowth", PokemonType.GRASS, null, 100, 100, 125, 110, 50, 50)
        registerSpecies(466, "Electivire", PokemonType.ELECTRIC, null, 75, 123, 67, 95, 85, 95)
        registerSpecies(467, "Magmortar", PokemonType.FIRE, null, 75, 95, 67, 125, 95, 83)
        registerSpecies(468, "Togekiss", PokemonType.FAIRY, PokemonType.FLYING, 85, 50, 95, 120, 115, 80)
        registerSpecies(469, "Yanmega", PokemonType.BUG, PokemonType.FLYING, 86, 76, 86, 116, 56, 95)
        registerSpecies(470, "Leafeon", PokemonType.GRASS, null, 65, 110, 130, 60, 65, 95)
        registerSpecies(471, "Glaceon", PokemonType.ICE, null, 65, 60, 110, 130, 95, 65)
        registerSpecies(472, "Gliscor", PokemonType.GROUND, PokemonType.FLYING, 75, 95, 125, 45, 75, 95)
        registerSpecies(473, "Mamoswine", PokemonType.ICE, PokemonType.GROUND, 110, 130, 80, 70, 60, 80)
        registerSpecies(474, "Porygon-Z", PokemonType.NORMAL, null, 85, 80, 70, 135, 75, 90)
        registerSpecies(475, "Gallade", PokemonType.PSYCHIC, PokemonType.FIGHTING, 68, 125, 65, 65, 115, 80)
        registerSpecies(476, "Probopass", PokemonType.ROCK, PokemonType.STEEL, 60, 55, 145, 75, 150, 40)
        registerSpecies(477, "Dusknoir", PokemonType.GHOST, null, 45, 100, 135, 65, 135, 45)
        registerSpecies(478, "Froslass", PokemonType.ICE, PokemonType.GHOST, 70, 80, 70, 80, 70, 110)
        registerSpecies(479, "Rotom", PokemonType.ELECTRIC, PokemonType.GHOST, 50, 50, 77, 95, 77, 91)
        registerSpecies(480, "Uxie", PokemonType.PSYCHIC, null, 75, 75, 130, 75, 130, 95)
        registerSpecies(481, "Mesprit", PokemonType.PSYCHIC, null, 80, 105, 105, 105, 105, 80)
        registerSpecies(482, "Azelf", PokemonType.PSYCHIC, null, 75, 125, 70, 125, 70, 115)
        registerSpecies(483, "Dialga", PokemonType.STEEL, PokemonType.DRAGON, 100, 120, 120, 150, 100, 90)
        registerSpecies(484, "Palkia", PokemonType.WATER, PokemonType.DRAGON, 90, 120, 100, 150, 120, 100)
        registerSpecies(485, "Heatran", PokemonType.FIRE, PokemonType.STEEL, 91, 90, 106, 130, 106, 77)
        registerSpecies(486, "Regigigas", PokemonType.NORMAL, null, 110, 160, 110, 80, 110, 100)
        registerSpecies(487, "Giratina", PokemonType.GHOST, PokemonType.DRAGON, 150, 100, 120, 100, 120, 90)
        registerSpecies(488, "Cresselia", PokemonType.PSYCHIC, null, 120, 70, 110, 75, 120, 85)
        registerSpecies(489, "Phione", PokemonType.WATER, null, 80, 80, 80, 80, 80, 80)
        registerSpecies(490, "Manaphy", PokemonType.WATER, null, 100, 100, 100, 100, 100, 100)
        registerSpecies(491, "Darkrai", PokemonType.DARK, null, 70, 90, 90, 135, 90, 125)
        registerSpecies(492, "Shaymin", PokemonType.GRASS, null, 100, 100, 100, 100, 100, 100)
        registerSpecies(493, "Arceus", PokemonType.NORMAL, null, 120, 120, 120, 120, 120, 120)
        registerSpecies(494, "Victini", PokemonType.PSYCHIC, PokemonType.FIRE, 100, 100, 100, 100, 100, 100)
        registerSpecies(495, "Snivy", PokemonType.GRASS, null, 45, 45, 55, 45, 55, 63)
        registerSpecies(496, "Servine", PokemonType.GRASS, null, 60, 60, 75, 60, 75, 83)
        registerSpecies(497, "Serperior", PokemonType.GRASS, null, 75, 75, 95, 75, 95, 113)
        registerSpecies(498, "Tepig", PokemonType.FIRE, null, 65, 63, 45, 45, 45, 45)
        registerSpecies(499, "Pignite", PokemonType.FIRE, PokemonType.FIGHTING, 90, 93, 55, 70, 55, 55)
        registerSpecies(500, "Emboar", PokemonType.FIRE, PokemonType.FIGHTING, 110, 123, 65, 100, 65, 65)
        registerSpecies(501, "Oshawott", PokemonType.WATER, null, 55, 55, 45, 63, 45, 45)
        registerSpecies(502, "Dewott", PokemonType.WATER, null, 75, 75, 60, 83, 60, 60)
        registerSpecies(503, "Samurott", PokemonType.WATER, null, 95, 100, 85, 108, 70, 70)
        registerSpecies(504, "Patrat", PokemonType.NORMAL, null, 45, 55, 39, 35, 39, 42)
        registerSpecies(505, "Watchog", PokemonType.NORMAL, null, 60, 85, 69, 60, 69, 77)
        registerSpecies(506, "Lillipup", PokemonType.NORMAL, null, 45, 60, 45, 25, 45, 55)
        registerSpecies(507, "Herdier", PokemonType.NORMAL, null, 65, 80, 65, 35, 65, 60)
        registerSpecies(508, "Stoutland", PokemonType.NORMAL, null, 85, 110, 90, 45, 90, 80)
        registerSpecies(509, "Purrloin", PokemonType.DARK, null, 41, 50, 37, 50, 37, 66)
        registerSpecies(510, "Liepard", PokemonType.DARK, null, 64, 88, 50, 88, 50, 106)
        registerSpecies(511, "Pansage", PokemonType.GRASS, null, 50, 53, 48, 53, 48, 64)
        registerSpecies(512, "Simisage", PokemonType.GRASS, null, 75, 98, 63, 98, 63, 101)
        registerSpecies(513, "Pansear", PokemonType.FIRE, null, 50, 53, 48, 53, 48, 64)
        registerSpecies(514, "Simisear", PokemonType.FIRE, null, 75, 98, 63, 98, 63, 101)
        registerSpecies(515, "Panpour", PokemonType.WATER, null, 50, 53, 48, 53, 48, 64)
        registerSpecies(516, "Simipour", PokemonType.WATER, null, 75, 98, 63, 98, 63, 101)
        registerSpecies(517, "Munna", PokemonType.PSYCHIC, null, 76, 25, 45, 67, 55, 24)
        registerSpecies(518, "Musharna", PokemonType.PSYCHIC, null, 116, 55, 85, 107, 95, 29)
        registerSpecies(519, "Pidove", PokemonType.NORMAL, PokemonType.FLYING, 50, 55, 50, 36, 30, 43)
        registerSpecies(520, "Tranquill", PokemonType.NORMAL, PokemonType.FLYING, 62, 77, 62, 50, 42, 65)
        registerSpecies(521, "Unfezant", PokemonType.NORMAL, PokemonType.FLYING, 80, 115, 80, 65, 55, 93)
        registerSpecies(522, "Blitzle", PokemonType.ELECTRIC, null, 45, 60, 32, 50, 32, 76)
        registerSpecies(523, "Zebstrika", PokemonType.ELECTRIC, null, 75, 100, 63, 80, 63, 116)
        registerSpecies(524, "Roggenrola", PokemonType.ROCK, null, 55, 75, 85, 25, 25, 15)
        registerSpecies(525, "Boldore", PokemonType.ROCK, null, 70, 105, 105, 50, 40, 20)
        registerSpecies(526, "Gigalith", PokemonType.ROCK, null, 85, 135, 130, 60, 80, 25)
        registerSpecies(527, "Woobat", PokemonType.PSYCHIC, PokemonType.FLYING, 65, 45, 43, 55, 43, 72)
        registerSpecies(528, "Swoobat", PokemonType.PSYCHIC, PokemonType.FLYING, 67, 57, 55, 77, 55, 114)
        registerSpecies(529, "Drilbur", PokemonType.GROUND, null, 60, 85, 40, 30, 45, 68)
        registerSpecies(530, "Excadrill", PokemonType.GROUND, PokemonType.STEEL, 110, 135, 60, 50, 65, 88)
        registerSpecies(531, "Audino", PokemonType.NORMAL, null, 103, 60, 86, 60, 86, 50)
        registerSpecies(532, "Timburr", PokemonType.FIGHTING, null, 75, 80, 55, 25, 35, 35)
        registerSpecies(533, "Gurdurr", PokemonType.FIGHTING, null, 85, 105, 85, 40, 50, 40)
        registerSpecies(534, "Conkeldurr", PokemonType.FIGHTING, null, 105, 140, 95, 55, 65, 45)
        registerSpecies(535, "Tympole", PokemonType.WATER, null, 50, 50, 40, 50, 40, 64)
        registerSpecies(536, "Palpitoad", PokemonType.WATER, PokemonType.GROUND, 75, 65, 55, 65, 55, 69)
        registerSpecies(537, "Seismitoad", PokemonType.WATER, PokemonType.GROUND, 105, 95, 75, 85, 75, 74)
        registerSpecies(538, "Throh", PokemonType.FIGHTING, null, 120, 100, 85, 30, 85, 45)
        registerSpecies(539, "Sawk", PokemonType.FIGHTING, null, 75, 125, 75, 30, 75, 85)
        registerSpecies(540, "Sewaddle", PokemonType.BUG, PokemonType.GRASS, 45, 53, 70, 40, 60, 42)
        registerSpecies(541, "Swadloon", PokemonType.BUG, PokemonType.GRASS, 55, 63, 90, 50, 80, 42)
        registerSpecies(542, "Leavanny", PokemonType.BUG, PokemonType.GRASS, 75, 103, 80, 70, 80, 92)
        registerSpecies(543, "Venipede", PokemonType.BUG, PokemonType.POISON, 30, 45, 59, 30, 39, 57)
        registerSpecies(544, "Whirlipede", PokemonType.BUG, PokemonType.POISON, 40, 55, 99, 40, 79, 47)
        registerSpecies(545, "Scolipede", PokemonType.BUG, PokemonType.POISON, 60, 100, 89, 55, 69, 112)
        registerSpecies(546, "Cottonee", PokemonType.GRASS, PokemonType.FAIRY, 40, 27, 60, 37, 50, 66)
        registerSpecies(547, "Whimsicott", PokemonType.GRASS, PokemonType.FAIRY, 60, 67, 85, 77, 75, 116)
        registerSpecies(548, "Petilil", PokemonType.GRASS, null, 45, 35, 50, 70, 50, 30)
        registerSpecies(549, "Lilligant", PokemonType.GRASS, null, 70, 60, 75, 110, 75, 90)
        registerSpecies(550, "Basculin", PokemonType.WATER, null, 70, 92, 65, 80, 55, 98)
        registerSpecies(551, "Sandile", PokemonType.GROUND, PokemonType.DARK, 50, 72, 35, 35, 35, 65)
        registerSpecies(552, "Krokorok", PokemonType.GROUND, PokemonType.DARK, 60, 82, 45, 45, 45, 74)
        registerSpecies(553, "Krookodile", PokemonType.GROUND, PokemonType.DARK, 95, 117, 80, 65, 70, 92)
        registerSpecies(554, "Darumaka", PokemonType.FIRE, null, 70, 90, 45, 15, 45, 50)
        registerSpecies(555, "Darmanitan", PokemonType.FIRE, null, 105, 140, 55, 30, 55, 95)
        registerSpecies(556, "Maractus", PokemonType.GRASS, null, 75, 86, 67, 106, 67, 60)
        registerSpecies(557, "Dwebble", PokemonType.BUG, PokemonType.ROCK, 50, 65, 85, 35, 35, 55)
        registerSpecies(558, "Crustle", PokemonType.BUG, PokemonType.ROCK, 70, 105, 125, 65, 75, 45)
        registerSpecies(559, "Scraggy", PokemonType.DARK, PokemonType.FIGHTING, 50, 75, 70, 35, 70, 48)
        registerSpecies(560, "Scrafty", PokemonType.DARK, PokemonType.FIGHTING, 65, 90, 115, 45, 115, 58)
        registerSpecies(561, "Sigilyph", PokemonType.PSYCHIC, PokemonType.FLYING, 72, 58, 80, 103, 80, 97)
        registerSpecies(562, "Yamask", PokemonType.GHOST, null, 38, 30, 85, 55, 65, 30)
        registerSpecies(563, "Cofagrigus", PokemonType.GHOST, null, 58, 50, 145, 95, 105, 30)
        registerSpecies(564, "Tirtouga", PokemonType.WATER, PokemonType.ROCK, 54, 78, 103, 53, 45, 22)
        registerSpecies(565, "Carracosta", PokemonType.WATER, PokemonType.ROCK, 74, 108, 133, 83, 65, 32)
        registerSpecies(566, "Archen", PokemonType.ROCK, PokemonType.FLYING, 55, 112, 45, 74, 45, 70)
        registerSpecies(567, "Archeops", PokemonType.ROCK, PokemonType.FLYING, 75, 140, 65, 112, 65, 110)
        registerSpecies(568, "Trubbish", PokemonType.POISON, null, 50, 50, 62, 40, 62, 65)
        registerSpecies(569, "Garbodor", PokemonType.POISON, null, 80, 95, 82, 60, 82, 75)
        registerSpecies(570, "Zorua", PokemonType.DARK, null, 40, 65, 40, 80, 40, 65)
        registerSpecies(571, "Zoroark", PokemonType.DARK, null, 60, 105, 60, 120, 60, 105)
        registerSpecies(572, "Minccino", PokemonType.NORMAL, null, 55, 50, 40, 40, 40, 75)
        registerSpecies(573, "Cinccino", PokemonType.NORMAL, null, 75, 95, 60, 65, 60, 115)
        registerSpecies(574, "Gothita", PokemonType.PSYCHIC, null, 45, 30, 50, 55, 65, 45)
        registerSpecies(575, "Gothorita", PokemonType.PSYCHIC, null, 60, 45, 70, 75, 85, 55)
        registerSpecies(576, "Gothitelle", PokemonType.PSYCHIC, null, 70, 55, 95, 95, 110, 65)
        registerSpecies(577, "Solosis", PokemonType.PSYCHIC, null, 45, 30, 40, 105, 50, 20)
        registerSpecies(578, "Duosion", PokemonType.PSYCHIC, null, 65, 40, 50, 125, 60, 30)
        registerSpecies(579, "Reuniclus", PokemonType.PSYCHIC, null, 110, 65, 75, 125, 85, 30)
        registerSpecies(580, "Ducklett", PokemonType.WATER, PokemonType.FLYING, 62, 44, 50, 44, 50, 55)
        registerSpecies(581, "Swanna", PokemonType.WATER, PokemonType.FLYING, 75, 87, 63, 87, 63, 98)
        registerSpecies(582, "Vanillite", PokemonType.ICE, null, 36, 50, 50, 65, 60, 44)
        registerSpecies(583, "Vanillish", PokemonType.ICE, null, 51, 65, 65, 80, 75, 59)
        registerSpecies(584, "Vanilluxe", PokemonType.ICE, null, 71, 95, 85, 110, 95, 79)
        registerSpecies(585, "Deerling", PokemonType.NORMAL, PokemonType.GRASS, 60, 60, 50, 40, 50, 75)
        registerSpecies(586, "Sawsbuck", PokemonType.NORMAL, PokemonType.GRASS, 80, 100, 70, 60, 70, 95)
        registerSpecies(587, "Emolga", PokemonType.ELECTRIC, PokemonType.FLYING, 55, 75, 60, 75, 60, 103)
        registerSpecies(588, "Karrablast", PokemonType.BUG, null, 50, 75, 45, 40, 45, 60)
        registerSpecies(589, "Escavalier", PokemonType.BUG, PokemonType.STEEL, 70, 135, 105, 60, 105, 20)
        registerSpecies(590, "Foongus", PokemonType.GRASS, PokemonType.POISON, 69, 55, 45, 55, 55, 15)
        registerSpecies(591, "Amoonguss", PokemonType.GRASS, PokemonType.POISON, 114, 85, 70, 85, 80, 30)
        registerSpecies(592, "Frillish", PokemonType.WATER, PokemonType.GHOST, 55, 40, 50, 65, 85, 40)
        registerSpecies(593, "Jellicent", PokemonType.WATER, PokemonType.GHOST, 100, 60, 70, 85, 105, 60)
        registerSpecies(594, "Alomomola", PokemonType.WATER, null, 165, 75, 80, 40, 45, 65)
        registerSpecies(595, "Joltik", PokemonType.BUG, PokemonType.ELECTRIC, 50, 47, 50, 57, 50, 65)
        registerSpecies(596, "Galvantula", PokemonType.BUG, PokemonType.ELECTRIC, 70, 77, 60, 97, 60, 108)
        registerSpecies(597, "Ferroseed", PokemonType.GRASS, PokemonType.STEEL, 44, 50, 91, 24, 86, 10)
        registerSpecies(598, "Ferrothorn", PokemonType.GRASS, PokemonType.STEEL, 74, 94, 131, 54, 116, 20)
        registerSpecies(599, "Klink", PokemonType.STEEL, null, 40, 55, 70, 45, 60, 30)
        registerSpecies(600, "Klang", PokemonType.STEEL, null, 60, 80, 95, 70, 85, 50)
    }

    private fun registerSpeciesChunk4() {
        registerSpecies(601, "Klinklang", PokemonType.STEEL, null, 60, 100, 115, 70, 85, 90)
        registerSpecies(602, "Tynamo", PokemonType.ELECTRIC, null, 35, 55, 40, 45, 40, 60)
        registerSpecies(603, "Eelektrik", PokemonType.ELECTRIC, null, 65, 85, 70, 75, 70, 40)
        registerSpecies(604, "Eelektross", PokemonType.ELECTRIC, null, 85, 115, 80, 105, 80, 50)
        registerSpecies(605, "Elgyem", PokemonType.PSYCHIC, null, 55, 55, 55, 85, 55, 30)
        registerSpecies(606, "Beheeyem", PokemonType.PSYCHIC, null, 75, 75, 75, 125, 95, 40)
        registerSpecies(607, "Litwick", PokemonType.GHOST, PokemonType.FIRE, 50, 30, 55, 65, 55, 20)
        registerSpecies(608, "Lampent", PokemonType.GHOST, PokemonType.FIRE, 60, 40, 60, 95, 60, 55)
        registerSpecies(609, "Chandelure", PokemonType.GHOST, PokemonType.FIRE, 60, 55, 90, 145, 90, 80)
        registerSpecies(610, "Axew", PokemonType.DRAGON, null, 46, 87, 60, 30, 40, 57)
        registerSpecies(611, "Fraxure", PokemonType.DRAGON, null, 66, 117, 70, 40, 50, 67)
        registerSpecies(612, "Haxorus", PokemonType.DRAGON, null, 76, 147, 90, 60, 70, 97)
        registerSpecies(613, "Cubchoo", PokemonType.ICE, null, 55, 70, 40, 60, 40, 40)
        registerSpecies(614, "Beartic", PokemonType.ICE, null, 95, 130, 80, 70, 80, 50)
        registerSpecies(615, "Cryogonal", PokemonType.ICE, null, 80, 50, 50, 95, 135, 105)
        registerSpecies(616, "Shelmet", PokemonType.BUG, null, 50, 40, 85, 40, 65, 25)
        registerSpecies(617, "Accelgor", PokemonType.BUG, null, 80, 70, 40, 100, 60, 145)
        registerSpecies(618, "Stunfisk", PokemonType.GROUND, PokemonType.ELECTRIC, 109, 66, 84, 81, 99, 32)
        registerSpecies(619, "Mienfoo", PokemonType.FIGHTING, null, 45, 85, 50, 55, 50, 65)
        registerSpecies(620, "Mienshao", PokemonType.FIGHTING, null, 65, 125, 60, 95, 60, 105)
        registerSpecies(621, "Druddigon", PokemonType.DRAGON, null, 77, 120, 90, 60, 90, 48)
        registerSpecies(622, "Golett", PokemonType.GROUND, PokemonType.GHOST, 59, 74, 50, 35, 50, 35)
        registerSpecies(623, "Golurk", PokemonType.GROUND, PokemonType.GHOST, 89, 124, 80, 55, 80, 55)
        registerSpecies(624, "Pawniard", PokemonType.DARK, PokemonType.STEEL, 45, 85, 70, 40, 40, 60)
        registerSpecies(625, "Bisharp", PokemonType.DARK, PokemonType.STEEL, 65, 125, 100, 60, 70, 70)
        registerSpecies(626, "Bouffalant", PokemonType.NORMAL, null, 95, 110, 95, 40, 95, 55)
        registerSpecies(627, "Rufflet", PokemonType.NORMAL, PokemonType.FLYING, 70, 83, 50, 37, 50, 60)
        registerSpecies(628, "Braviary", PokemonType.NORMAL, PokemonType.FLYING, 100, 123, 75, 57, 75, 80)
        registerSpecies(629, "Vullaby", PokemonType.DARK, PokemonType.FLYING, 70, 55, 75, 45, 65, 60)
        registerSpecies(630, "Mandibuzz", PokemonType.DARK, PokemonType.FLYING, 110, 65, 105, 55, 95, 80)
        registerSpecies(631, "Heatmor", PokemonType.FIRE, null, 85, 97, 66, 105, 66, 65)
        registerSpecies(632, "Durant", PokemonType.BUG, PokemonType.STEEL, 58, 109, 112, 48, 48, 109)
        registerSpecies(633, "Deino", PokemonType.DARK, PokemonType.DRAGON, 52, 65, 50, 45, 50, 38)
        registerSpecies(634, "Zweilous", PokemonType.DARK, PokemonType.DRAGON, 72, 85, 70, 65, 70, 58)
        registerSpecies(635, "Hydreigon", PokemonType.DARK, PokemonType.DRAGON, 92, 105, 90, 125, 90, 98)
        registerSpecies(636, "Larvesta", PokemonType.BUG, PokemonType.FIRE, 55, 85, 55, 50, 55, 60)
        registerSpecies(637, "Volcarona", PokemonType.BUG, PokemonType.FIRE, 85, 60, 65, 135, 105, 100)
        registerSpecies(638, "Cobalion", PokemonType.STEEL, PokemonType.FIGHTING, 91, 90, 129, 90, 72, 108)
        registerSpecies(639, "Terrakion", PokemonType.ROCK, PokemonType.FIGHTING, 91, 129, 90, 72, 90, 108)
        registerSpecies(640, "Virizion", PokemonType.GRASS, PokemonType.FIGHTING, 91, 90, 72, 90, 129, 108)
        registerSpecies(641, "Tornadus", PokemonType.FLYING, null, 79, 115, 70, 125, 80, 111)
        registerSpecies(642, "Thundurus", PokemonType.ELECTRIC, PokemonType.FLYING, 79, 115, 70, 125, 80, 111)
        registerSpecies(643, "Reshiram", PokemonType.DRAGON, PokemonType.FIRE, 100, 120, 100, 150, 120, 90)
        registerSpecies(644, "Zekrom", PokemonType.DRAGON, PokemonType.ELECTRIC, 100, 150, 120, 120, 100, 90)
        registerSpecies(645, "Landorus", PokemonType.GROUND, PokemonType.FLYING, 89, 125, 90, 115, 80, 101)
        registerSpecies(646, "Kyurem", PokemonType.DRAGON, PokemonType.ICE, 125, 130, 90, 130, 90, 95)
        registerSpecies(647, "Keldeo", PokemonType.WATER, PokemonType.FIGHTING, 91, 72, 90, 129, 90, 108)
        registerSpecies(648, "Meloetta", PokemonType.NORMAL, PokemonType.PSYCHIC, 100, 77, 77, 128, 128, 90)
        registerSpecies(649, "Genesect", PokemonType.BUG, PokemonType.STEEL, 71, 120, 95, 120, 95, 99)
        registerSpecies(650, "Chespin", PokemonType.GRASS, null, 56, 61, 65, 48, 45, 38)
        registerSpecies(651, "Quilladin", PokemonType.GRASS, null, 61, 78, 95, 56, 58, 57)
        registerSpecies(652, "Chesnaught", PokemonType.GRASS, PokemonType.FIGHTING, 88, 107, 122, 74, 75, 64)
        registerSpecies(653, "Fennekin", PokemonType.FIRE, null, 40, 45, 40, 62, 60, 60)
        registerSpecies(654, "Braixen", PokemonType.FIRE, null, 59, 59, 58, 90, 70, 73)
        registerSpecies(655, "Delphox", PokemonType.FIRE, PokemonType.PSYCHIC, 75, 69, 72, 114, 100, 104)
        registerSpecies(656, "Froakie", PokemonType.WATER, null, 41, 56, 40, 62, 44, 71)
        registerSpecies(657, "Frogadier", PokemonType.WATER, null, 54, 63, 52, 83, 56, 97)
        registerSpecies(658, "Greninja", PokemonType.WATER, PokemonType.DARK, 72, 95, 67, 103, 71, 122)
        registerSpecies(659, "Bunnelby", PokemonType.NORMAL, null, 38, 36, 38, 32, 36, 57)
        registerSpecies(660, "Diggersby", PokemonType.NORMAL, PokemonType.GROUND, 85, 56, 77, 50, 77, 78)
        registerSpecies(661, "Fletchling", PokemonType.NORMAL, PokemonType.FLYING, 45, 50, 43, 40, 38, 62)
        registerSpecies(662, "Fletchinder", PokemonType.FIRE, PokemonType.FLYING, 62, 73, 55, 56, 52, 84)
        registerSpecies(663, "Talonflame", PokemonType.FIRE, PokemonType.FLYING, 78, 81, 71, 74, 69, 126)
        registerSpecies(664, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(665, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
        registerSpecies(666, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(667, "Litleo", PokemonType.FIRE, PokemonType.NORMAL, 62, 50, 58, 73, 54, 72)
        registerSpecies(668, "Pyroar", PokemonType.FIRE, PokemonType.NORMAL, 86, 68, 72, 109, 66, 106)
        registerSpecies(669, "Flabébé", PokemonType.FAIRY, null, 44, 38, 39, 61, 79, 42)
        registerSpecies(670, "Floette", PokemonType.FAIRY, null, 54, 45, 47, 75, 98, 52)
        registerSpecies(671, "Florges", PokemonType.FAIRY, null, 78, 65, 68, 112, 154, 75)
        registerSpecies(672, "Skiddo", PokemonType.GRASS, null, 66, 65, 48, 62, 57, 52)
        registerSpecies(673, "Gogoat", PokemonType.GRASS, null, 123, 100, 62, 97, 81, 68)
        registerSpecies(674, "Pancham", PokemonType.FIGHTING, null, 67, 82, 62, 46, 48, 43)
        registerSpecies(675, "Pangoro", PokemonType.FIGHTING, PokemonType.DARK, 95, 124, 78, 69, 71, 58)
        registerSpecies(676, "Furfrou", PokemonType.NORMAL, null, 75, 80, 60, 65, 90, 102)
        registerSpecies(677, "Espurr", PokemonType.PSYCHIC, null, 62, 48, 54, 63, 60, 68)
        registerSpecies(678, "Meowstic", PokemonType.PSYCHIC, null, 74, 48, 76, 83, 81, 104)
        registerSpecies(679, "Honedge", PokemonType.STEEL, PokemonType.GHOST, 45, 80, 100, 35, 37, 28)
        registerSpecies(680, "Doublade", PokemonType.STEEL, PokemonType.GHOST, 59, 110, 150, 45, 49, 35)
        registerSpecies(681, "Aegislash", PokemonType.STEEL, PokemonType.GHOST, 60, 50, 140, 50, 140, 60)
        registerSpecies(682, "Spritzee", PokemonType.FAIRY, null, 78, 52, 60, 63, 65, 23)
        registerSpecies(683, "Aromatisse", PokemonType.FAIRY, null, 101, 72, 72, 99, 89, 29)
        registerSpecies(684, "Swirlix", PokemonType.FAIRY, null, 62, 48, 66, 59, 57, 49)
        registerSpecies(685, "Slurpuff", PokemonType.FAIRY, null, 82, 80, 86, 85, 75, 72)
        registerSpecies(686, "Inkay", PokemonType.DARK, PokemonType.PSYCHIC, 53, 54, 53, 37, 46, 45)
        registerSpecies(687, "Malamar", PokemonType.DARK, PokemonType.PSYCHIC, 86, 92, 88, 68, 75, 73)
        registerSpecies(688, "Binacle", PokemonType.ROCK, PokemonType.WATER, 42, 52, 67, 39, 56, 50)
        registerSpecies(689, "Barbaracle", PokemonType.ROCK, PokemonType.WATER, 72, 105, 115, 54, 86, 68)
        registerSpecies(690, "Skrelp", PokemonType.POISON, PokemonType.WATER, 50, 60, 60, 60, 60, 30)
        registerSpecies(691, "Dragalge", PokemonType.POISON, PokemonType.DRAGON, 65, 75, 90, 97, 123, 44)
        registerSpecies(692, "Clauncher", PokemonType.WATER, null, 50, 53, 62, 58, 63, 44)
        registerSpecies(693, "Clawitzer", PokemonType.WATER, null, 71, 73, 88, 120, 89, 59)
        registerSpecies(694, "Helioptile", PokemonType.ELECTRIC, PokemonType.NORMAL, 44, 38, 33, 61, 43, 70)
        registerSpecies(695, "Heliolisk", PokemonType.ELECTRIC, PokemonType.NORMAL, 62, 55, 52, 109, 94, 109)
        registerSpecies(696, "Tyrunt", PokemonType.ROCK, PokemonType.DRAGON, 58, 89, 77, 45, 45, 48)
        registerSpecies(697, "Tyrantrum", PokemonType.ROCK, PokemonType.DRAGON, 82, 121, 119, 69, 59, 71)
        registerSpecies(698, "Amaura", PokemonType.ROCK, PokemonType.ICE, 77, 59, 50, 67, 63, 46)
        registerSpecies(699, "Aurorus", PokemonType.ROCK, PokemonType.ICE, 123, 77, 72, 99, 92, 58)
        registerSpecies(700, "Sylveon", PokemonType.FAIRY, null, 95, 65, 65, 110, 130, 60)
        registerSpecies(701, "Hawlucha", PokemonType.FIGHTING, PokemonType.FLYING, 78, 92, 75, 74, 63, 118)
        registerSpecies(702, "Dedenne", PokemonType.ELECTRIC, PokemonType.FAIRY, 67, 58, 57, 81, 67, 101)
        registerSpecies(703, "Carbink", PokemonType.ROCK, PokemonType.FAIRY, 50, 50, 150, 50, 150, 50)
        registerSpecies(704, "Goomy", PokemonType.DRAGON, null, 45, 50, 35, 55, 75, 40)
        registerSpecies(705, "Sliggoo", PokemonType.DRAGON, null, 68, 75, 53, 83, 113, 60)
        registerSpecies(706, "Goodra", PokemonType.DRAGON, null, 90, 100, 70, 110, 150, 80)
        registerSpecies(707, "Klefki", PokemonType.STEEL, PokemonType.FAIRY, 57, 80, 91, 80, 87, 75)
        registerSpecies(708, "Phantump", PokemonType.GHOST, PokemonType.GRASS, 43, 70, 48, 50, 60, 38)
        registerSpecies(709, "Trevenant", PokemonType.GHOST, PokemonType.GRASS, 85, 110, 76, 65, 82, 56)
        registerSpecies(710, "Pumpkaboo", PokemonType.GHOST, PokemonType.GRASS, 49, 66, 70, 44, 55, 51)
        registerSpecies(711, "Gourgeist", PokemonType.GHOST, PokemonType.GRASS, 65, 90, 122, 58, 75, 84)
        registerSpecies(712, "Bergmite", PokemonType.ICE, null, 55, 69, 85, 32, 35, 28)
        registerSpecies(713, "Avalugg", PokemonType.ICE, null, 95, 117, 184, 44, 46, 28)
        registerSpecies(714, "Noibat", PokemonType.FLYING, PokemonType.DRAGON, 40, 30, 35, 45, 40, 55)
        registerSpecies(715, "Noivern", PokemonType.FLYING, PokemonType.DRAGON, 85, 70, 80, 97, 80, 123)
        registerSpecies(716, "Xerneas", PokemonType.FAIRY, null, 126, 131, 95, 131, 98, 99)
        registerSpecies(717, "Yveltal", PokemonType.DARK, PokemonType.FLYING, 126, 131, 95, 131, 98, 99)
        registerSpecies(718, "Zygarde", PokemonType.DRAGON, PokemonType.GROUND, 108, 100, 121, 81, 95, 95)
        registerSpecies(719, "Diancie", PokemonType.ROCK, PokemonType.FAIRY, 50, 100, 150, 100, 150, 50)
        registerSpecies(720, "Hoopa", PokemonType.PSYCHIC, PokemonType.GHOST, 80, 110, 60, 150, 130, 70)
        registerSpecies(721, "Volcanion", PokemonType.FIRE, PokemonType.WATER, 80, 110, 120, 130, 90, 70)
        registerSpecies(722, "Rowlet", PokemonType.GRASS, PokemonType.FLYING, 68, 55, 55, 50, 50, 42)
        registerSpecies(723, "Dartrix", PokemonType.GRASS, PokemonType.FLYING, 78, 75, 75, 70, 70, 52)
        registerSpecies(724, "Decidueye", PokemonType.GRASS, PokemonType.GHOST, 78, 107, 75, 100, 100, 70)
        registerSpecies(725, "Litten", PokemonType.FIRE, null, 45, 65, 40, 60, 40, 70)
        registerSpecies(726, "Torracat", PokemonType.FIRE, null, 65, 85, 50, 80, 50, 90)
        registerSpecies(727, "Incineroar", PokemonType.FIRE, PokemonType.DARK, 95, 115, 90, 80, 90, 60)
        registerSpecies(728, "Popplio", PokemonType.WATER, null, 50, 54, 54, 66, 56, 40)
        registerSpecies(729, "Brionne", PokemonType.WATER, null, 60, 69, 69, 91, 81, 50)
        registerSpecies(730, "Primarina", PokemonType.WATER, PokemonType.FAIRY, 80, 74, 74, 126, 116, 60)
        registerSpecies(731, "Pikipek", PokemonType.NORMAL, PokemonType.FLYING, 35, 75, 30, 30, 30, 65)
        registerSpecies(732, "Trumbeak", PokemonType.NORMAL, PokemonType.FLYING, 55, 85, 50, 40, 50, 75)
        registerSpecies(733, "Toucannon", PokemonType.NORMAL, PokemonType.FLYING, 80, 120, 75, 75, 75, 60)
        registerSpecies(734, "Yungoos", PokemonType.NORMAL, null, 48, 70, 30, 30, 30, 45)
        registerSpecies(735, "Gumshoos", PokemonType.NORMAL, null, 88, 110, 60, 55, 60, 45)
        registerSpecies(736, "Grubbin", PokemonType.BUG, null, 47, 62, 45, 55, 45, 46)
        registerSpecies(737, "Charjabug", PokemonType.BUG, PokemonType.ELECTRIC, 57, 82, 95, 55, 75, 36)
        registerSpecies(738, "Vikavolt", PokemonType.BUG, PokemonType.ELECTRIC, 77, 70, 90, 145, 75, 43)
        registerSpecies(739, "Crabrawler", PokemonType.FIGHTING, null, 47, 82, 57, 42, 47, 63)
        registerSpecies(740, "Crabominable", PokemonType.FIGHTING, PokemonType.ICE, 97, 132, 77, 62, 67, 43)
        registerSpecies(741, "Oricorio", PokemonType.FIRE, PokemonType.FLYING, 75, 70, 70, 98, 70, 93)
        registerSpecies(742, "Cutiefly", PokemonType.BUG, PokemonType.FAIRY, 40, 45, 40, 55, 40, 84)
        registerSpecies(743, "Ribombee", PokemonType.BUG, PokemonType.FAIRY, 60, 55, 60, 95, 70, 124)
        registerSpecies(744, "Rockruff", PokemonType.ROCK, null, 45, 65, 40, 30, 40, 60)
        registerSpecies(745, "Lycanroc", PokemonType.ROCK, null, 75, 115, 65, 55, 65, 112)
        registerSpecies(746, "Wishiwashi", PokemonType.WATER, null, 45, 20, 20, 25, 25, 40)
        registerSpecies(747, "Mareanie", PokemonType.POISON, PokemonType.WATER, 50, 53, 62, 43, 52, 45)
        registerSpecies(748, "Toxapex", PokemonType.POISON, PokemonType.WATER, 50, 63, 152, 53, 142, 35)
        registerSpecies(749, "Mudbray", PokemonType.GROUND, null, 70, 100, 70, 45, 55, 45)
        registerSpecies(750, "Mudsdale", PokemonType.GROUND, null, 100, 125, 100, 55, 85, 35)
        registerSpecies(751, "Dewpider", PokemonType.WATER, PokemonType.BUG, 38, 40, 52, 40, 72, 27)
        registerSpecies(752, "Araquanid", PokemonType.WATER, PokemonType.BUG, 68, 70, 92, 50, 132, 42)
        registerSpecies(753, "Fomantis", PokemonType.GRASS, null, 40, 55, 35, 50, 35, 35)
        registerSpecies(754, "Lurantis", PokemonType.GRASS, null, 70, 105, 90, 80, 90, 45)
        registerSpecies(755, "Morelull", PokemonType.GRASS, PokemonType.FAIRY, 40, 35, 55, 65, 75, 15)
        registerSpecies(756, "Shiinotic", PokemonType.GRASS, PokemonType.FAIRY, 60, 45, 80, 90, 100, 30)
        registerSpecies(757, "Salandit", PokemonType.POISON, PokemonType.FIRE, 48, 44, 40, 71, 40, 77)
        registerSpecies(758, "Salazzle", PokemonType.POISON, PokemonType.FIRE, 68, 64, 60, 111, 60, 117)
        registerSpecies(759, "Stufful", PokemonType.NORMAL, PokemonType.FIGHTING, 70, 75, 50, 45, 50, 50)
        registerSpecies(760, "Bewear", PokemonType.NORMAL, PokemonType.FIGHTING, 120, 125, 80, 55, 60, 60)
        registerSpecies(761, "Bounsweet", PokemonType.GRASS, null, 42, 30, 38, 30, 38, 32)
        registerSpecies(762, "Steenee", PokemonType.GRASS, null, 52, 40, 48, 40, 48, 62)
        registerSpecies(763, "Tsareena", PokemonType.GRASS, null, 72, 120, 98, 50, 98, 72)
        registerSpecies(764, "Comfey", PokemonType.FAIRY, null, 51, 52, 90, 82, 110, 100)
        registerSpecies(765, "Oranguru", PokemonType.NORMAL, PokemonType.PSYCHIC, 90, 60, 80, 90, 110, 60)
        registerSpecies(766, "Passimian", PokemonType.FIGHTING, null, 100, 120, 90, 40, 60, 80)
        registerSpecies(767, "Wimpod", PokemonType.BUG, PokemonType.WATER, 25, 35, 40, 20, 30, 80)
        registerSpecies(768, "Golisopod", PokemonType.BUG, PokemonType.WATER, 75, 125, 140, 60, 90, 40)
        registerSpecies(769, "Sandygast", PokemonType.GHOST, PokemonType.GROUND, 55, 55, 80, 70, 45, 15)
        registerSpecies(770, "Palossand", PokemonType.GHOST, PokemonType.GROUND, 85, 75, 110, 100, 75, 35)
        registerSpecies(771, "Pyukumuku", PokemonType.WATER, null, 55, 60, 130, 30, 130, 5)
        registerSpecies(772, "Type: Null", PokemonType.NORMAL, null, 95, 95, 95, 95, 95, 59)
        registerSpecies(773, "Silvally", PokemonType.NORMAL, null, 95, 95, 95, 95, 95, 95)
        registerSpecies(774, "Minior", PokemonType.ROCK, PokemonType.FLYING, 60, 60, 100, 60, 100, 60)
        registerSpecies(775, "Komala", PokemonType.NORMAL, null, 65, 115, 65, 75, 95, 65)
        registerSpecies(776, "Turtonator", PokemonType.FIRE, PokemonType.DRAGON, 60, 78, 135, 91, 85, 36)
        registerSpecies(777, "Togedemaru", PokemonType.ELECTRIC, PokemonType.STEEL, 65, 98, 63, 40, 73, 96)
        registerSpecies(778, "Mimikyu", PokemonType.GHOST, PokemonType.FAIRY, 55, 90, 80, 50, 105, 96)
        registerSpecies(779, "Bruxish", PokemonType.WATER, PokemonType.PSYCHIC, 68, 105, 70, 70, 70, 92)
        registerSpecies(780, "Drampa", PokemonType.NORMAL, PokemonType.DRAGON, 78, 60, 85, 135, 91, 36)
        registerSpecies(781, "Dhelmise", PokemonType.GHOST, PokemonType.GRASS, 70, 131, 100, 86, 90, 40)
        registerSpecies(782, "Jangmo-o", PokemonType.DRAGON, null, 45, 55, 65, 45, 45, 45)
        registerSpecies(783, "Hakamo-o", PokemonType.DRAGON, PokemonType.FIGHTING, 55, 75, 90, 65, 70, 65)
        registerSpecies(784, "Kommo-o", PokemonType.DRAGON, PokemonType.FIGHTING, 75, 110, 125, 100, 105, 85)
        registerSpecies(785, "Tapu Koko", PokemonType.ELECTRIC, PokemonType.FAIRY, 70, 115, 85, 95, 75, 130)
        registerSpecies(786, "Tapu Lele", PokemonType.PSYCHIC, PokemonType.FAIRY, 70, 85, 75, 130, 115, 95)
        registerSpecies(787, "Tapu Bulu", PokemonType.GRASS, PokemonType.FAIRY, 70, 130, 115, 85, 95, 75)
        registerSpecies(788, "Tapu Fini", PokemonType.WATER, PokemonType.FAIRY, 70, 75, 115, 95, 130, 85)
        registerSpecies(789, "Cosmog", PokemonType.PSYCHIC, null, 43, 29, 31, 29, 31, 37)
        registerSpecies(790, "Cosmoem", PokemonType.PSYCHIC, null, 43, 29, 131, 29, 131, 37)
        registerSpecies(791, "Solgaleo", PokemonType.PSYCHIC, PokemonType.STEEL, 137, 137, 107, 113, 89, 97)
        registerSpecies(792, "Lunala", PokemonType.PSYCHIC, PokemonType.GHOST, 137, 113, 89, 137, 107, 97)
        registerSpecies(793, "Nihilego", PokemonType.ROCK, PokemonType.POISON, 109, 53, 47, 127, 131, 103)
        registerSpecies(794, "Buzzwole", PokemonType.BUG, PokemonType.FIGHTING, 107, 139, 139, 53, 53, 79)
        registerSpecies(795, "Pheromosa", PokemonType.BUG, PokemonType.FIGHTING, 71, 137, 37, 137, 37, 151)
        registerSpecies(796, "Xurkitree", PokemonType.ELECTRIC, null, 83, 89, 71, 173, 71, 83)
        registerSpecies(797, "Celesteela", PokemonType.STEEL, PokemonType.FLYING, 97, 101, 103, 107, 101, 61)
        registerSpecies(798, "Kartana", PokemonType.GRASS, PokemonType.STEEL, 59, 181, 131, 59, 31, 109)
        registerSpecies(799, "Guzzlord", PokemonType.DARK, PokemonType.DRAGON, 223, 101, 53, 97, 53, 43)
        registerSpecies(800, "Necrozma", PokemonType.PSYCHIC, null, 97, 107, 101, 127, 89, 79)
    }

    private fun registerSpeciesChunk5() {
        registerSpecies(801, "Magearna", PokemonType.STEEL, PokemonType.FAIRY, 80, 95, 115, 130, 115, 65)
        registerSpecies(802, "Marshadow", PokemonType.FIGHTING, PokemonType.GHOST, 90, 125, 80, 90, 90, 125)
        registerSpecies(803, "Poipole", PokemonType.POISON, null, 67, 73, 67, 73, 67, 73)
        registerSpecies(804, "Naganadel", PokemonType.POISON, PokemonType.DRAGON, 73, 73, 73, 127, 73, 121)
        registerSpecies(805, "Stakataka", PokemonType.ROCK, PokemonType.STEEL, 61, 131, 211, 53, 101, 13)
        registerSpecies(806, "Blacephalon", PokemonType.FIRE, PokemonType.GHOST, 53, 127, 53, 151, 79, 107)
        registerSpecies(807, "Zeraora", PokemonType.ELECTRIC, null, 88, 112, 75, 102, 80, 143)
        registerSpecies(808, "Meltan", PokemonType.STEEL, null, 46, 65, 65, 55, 35, 34)
        registerSpecies(809, "Melmetal", PokemonType.STEEL, null, 135, 143, 143, 80, 65, 34)
        registerSpecies(810, "Grookey", PokemonType.GRASS, null, 50, 65, 50, 40, 40, 65)
        registerSpecies(811, "Thwackey", PokemonType.GRASS, null, 70, 85, 70, 55, 60, 80)
        registerSpecies(812, "Rillaboom", PokemonType.GRASS, null, 100, 125, 90, 60, 70, 85)
        registerSpecies(813, "Scorbunny", PokemonType.FIRE, null, 50, 71, 40, 40, 40, 69)
        registerSpecies(814, "Raboot", PokemonType.FIRE, null, 65, 86, 60, 55, 60, 94)
        registerSpecies(815, "Cinderace", PokemonType.FIRE, null, 80, 116, 75, 65, 75, 119)
        registerSpecies(816, "Sobble", PokemonType.WATER, null, 50, 40, 40, 70, 40, 70)
        registerSpecies(817, "Drizzile", PokemonType.WATER, null, 65, 60, 55, 95, 55, 90)
        registerSpecies(818, "Inteleon", PokemonType.WATER, null, 70, 85, 65, 125, 65, 120)
        registerSpecies(819, "Skwovet", PokemonType.NORMAL, null, 70, 55, 55, 35, 35, 25)
        registerSpecies(820, "Greedent", PokemonType.NORMAL, null, 120, 95, 95, 55, 75, 20)
        registerSpecies(821, "Rookidee", PokemonType.FLYING, null, 38, 47, 35, 33, 35, 57)
        registerSpecies(822, "Corvisquire", PokemonType.FLYING, null, 68, 67, 55, 43, 55, 77)
        registerSpecies(823, "Corviknight", PokemonType.FLYING, PokemonType.STEEL, 98, 87, 105, 53, 85, 67)
        registerSpecies(824, "Blipbug", PokemonType.BUG, null, 25, 20, 20, 25, 45, 45)
        registerSpecies(825, "Dottler", PokemonType.BUG, PokemonType.PSYCHIC, 50, 35, 80, 50, 90, 30)
        registerSpecies(826, "Orbeetle", PokemonType.BUG, PokemonType.PSYCHIC, 60, 45, 110, 80, 120, 90)
        registerSpecies(827, "Nickit", PokemonType.DARK, null, 40, 28, 28, 47, 52, 50)
        registerSpecies(828, "Thievul", PokemonType.DARK, null, 70, 58, 58, 87, 92, 90)
        registerSpecies(829, "Gossifleur", PokemonType.GRASS, null, 40, 40, 60, 40, 60, 10)
        registerSpecies(830, "Eldegoss", PokemonType.GRASS, null, 60, 50, 90, 80, 120, 60)
        registerSpecies(831, "Wooloo", PokemonType.NORMAL, null, 42, 40, 55, 40, 45, 48)
        registerSpecies(832, "Dubwool", PokemonType.NORMAL, null, 72, 80, 100, 60, 90, 88)
        registerSpecies(833, "Chewtle", PokemonType.WATER, null, 50, 64, 50, 38, 38, 44)
        registerSpecies(834, "Drednaw", PokemonType.WATER, PokemonType.ROCK, 90, 115, 90, 48, 68, 74)
        registerSpecies(835, "Yamper", PokemonType.ELECTRIC, null, 59, 45, 50, 40, 50, 26)
        registerSpecies(836, "Boltund", PokemonType.ELECTRIC, null, 69, 90, 60, 90, 60, 121)
        registerSpecies(837, "Rolycoly", PokemonType.ROCK, null, 30, 40, 50, 40, 50, 30)
        registerSpecies(838, "Carkol", PokemonType.ROCK, PokemonType.FIRE, 80, 60, 90, 60, 70, 50)
        registerSpecies(839, "Coalossal", PokemonType.ROCK, PokemonType.FIRE, 110, 80, 120, 80, 90, 30)
        registerSpecies(840, "Applin", PokemonType.GRASS, PokemonType.DRAGON, 40, 40, 80, 40, 40, 20)
        registerSpecies(841, "Flapple", PokemonType.GRASS, PokemonType.DRAGON, 70, 110, 80, 95, 60, 70)
        registerSpecies(842, "Appletun", PokemonType.GRASS, PokemonType.DRAGON, 110, 85, 80, 100, 80, 30)
        registerSpecies(843, "Silicobra", PokemonType.GROUND, null, 52, 57, 75, 35, 50, 46)
        registerSpecies(844, "Sandaconda", PokemonType.GROUND, null, 72, 107, 125, 65, 70, 71)
        registerSpecies(845, "Cramorant", PokemonType.FLYING, PokemonType.WATER, 70, 85, 55, 85, 95, 85)
        registerSpecies(846, "Arrokuda", PokemonType.WATER, null, 41, 63, 40, 40, 30, 66)
        registerSpecies(847, "Barraskewda", PokemonType.WATER, null, 61, 123, 60, 60, 50, 136)
        registerSpecies(848, "Toxel", PokemonType.ELECTRIC, PokemonType.POISON, 40, 38, 35, 54, 35, 40)
        registerSpecies(849, "Toxtricity", PokemonType.ELECTRIC, PokemonType.POISON, 75, 98, 70, 114, 70, 75)
        registerSpecies(850, "Sizzlipede", PokemonType.FIRE, PokemonType.BUG, 50, 65, 45, 50, 50, 45)
        registerSpecies(851, "Centiskorch", PokemonType.FIRE, PokemonType.BUG, 100, 115, 65, 90, 90, 65)
        registerSpecies(852, "Clobbopus", PokemonType.FIGHTING, null, 50, 68, 60, 50, 50, 32)
        registerSpecies(853, "Grapploct", PokemonType.FIGHTING, null, 80, 118, 90, 70, 80, 42)
        registerSpecies(854, "Sinistea", PokemonType.GHOST, null, 40, 45, 45, 74, 54, 50)
        registerSpecies(855, "Polteageist", PokemonType.GHOST, null, 60, 65, 65, 134, 114, 70)
        registerSpecies(856, "Hatenna", PokemonType.PSYCHIC, null, 42, 30, 45, 56, 53, 39)
        registerSpecies(857, "Hattrem", PokemonType.PSYCHIC, null, 57, 40, 65, 86, 73, 49)
        registerSpecies(858, "Hatterene", PokemonType.PSYCHIC, PokemonType.FAIRY, 57, 90, 95, 136, 103, 29)
        registerSpecies(859, "Impidimp", PokemonType.DARK, PokemonType.FAIRY, 45, 45, 30, 55, 40, 50)
        registerSpecies(860, "Morgrem", PokemonType.DARK, PokemonType.FAIRY, 65, 60, 45, 75, 55, 70)
        registerSpecies(861, "Grimmsnarl", PokemonType.DARK, PokemonType.FAIRY, 95, 120, 65, 95, 75, 60)
        registerSpecies(862, "Obstagoon", PokemonType.DARK, PokemonType.NORMAL, 93, 90, 101, 60, 81, 95)
        registerSpecies(863, "Perrserker", PokemonType.STEEL, null, 70, 110, 100, 50, 60, 50)
        registerSpecies(864, "Cursola", PokemonType.GHOST, null, 60, 95, 50, 145, 130, 30)
        registerSpecies(865, "Sirfetch'd", PokemonType.FIGHTING, null, 62, 135, 95, 68, 82, 65)
        registerSpecies(866, "Mr. Rime", PokemonType.ICE, PokemonType.PSYCHIC, 80, 85, 75, 110, 100, 70)
        registerSpecies(867, "Runerigus", PokemonType.GROUND, PokemonType.GHOST, 58, 95, 145, 50, 105, 30)
        registerSpecies(868, "Milcery", PokemonType.FAIRY, null, 45, 40, 40, 50, 61, 34)
        registerSpecies(869, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(870, "Falinks", PokemonType.FIGHTING, null, 65, 100, 100, 70, 60, 75)
        registerSpecies(871, "Pincurchin", PokemonType.ELECTRIC, null, 48, 101, 95, 91, 85, 15)
        registerSpecies(872, "Snom", PokemonType.ICE, PokemonType.BUG, 30, 25, 35, 45, 30, 20)
        registerSpecies(873, "Frosmoth", PokemonType.ICE, PokemonType.BUG, 70, 65, 60, 125, 90, 65)
        registerSpecies(874, "Stonjourner", PokemonType.ROCK, null, 100, 125, 135, 20, 20, 70)
        registerSpecies(875, "Eiscue", PokemonType.ICE, null, 75, 80, 110, 65, 90, 50)
        registerSpecies(876, "Indeedee", PokemonType.PSYCHIC, PokemonType.NORMAL, 60, 65, 55, 105, 95, 95)
        registerSpecies(877, "Morpeko", PokemonType.ELECTRIC, PokemonType.DARK, 58, 95, 58, 70, 58, 97)
        registerSpecies(878, "Cufant", PokemonType.STEEL, null, 72, 80, 49, 40, 49, 40)
        registerSpecies(879, "Copperajah", PokemonType.STEEL, null, 122, 130, 69, 80, 69, 30)
        registerSpecies(880, "Dracozolt", PokemonType.ELECTRIC, PokemonType.DRAGON, 90, 100, 90, 80, 70, 75)
        registerSpecies(881, "Arctozolt", PokemonType.ELECTRIC, PokemonType.ICE, 90, 100, 90, 90, 80, 55)
        registerSpecies(882, "Dracovish", PokemonType.WATER, PokemonType.DRAGON, 90, 90, 100, 70, 80, 75)
        registerSpecies(883, "Arctovish", PokemonType.WATER, PokemonType.ICE, 90, 90, 100, 80, 90, 55)
        registerSpecies(884, "Duraludon", PokemonType.STEEL, PokemonType.DRAGON, 70, 95, 115, 120, 50, 85)
        registerSpecies(885, "Dreepy", PokemonType.DRAGON, PokemonType.GHOST, 28, 60, 30, 40, 30, 82)
        registerSpecies(886, "Drakloak", PokemonType.DRAGON, PokemonType.GHOST, 68, 80, 50, 60, 50, 102)
        registerSpecies(887, "Dragapult", PokemonType.DRAGON, PokemonType.GHOST, 88, 120, 75, 100, 75, 142)
        registerSpecies(888, "Zacian", PokemonType.FAIRY, null, 92, 120, 115, 80, 115, 138)
        registerSpecies(889, "Zamazenta", PokemonType.FIGHTING, null, 92, 120, 115, 80, 115, 138)
        registerSpecies(890, "Eternatus", PokemonType.POISON, PokemonType.DRAGON, 140, 85, 95, 145, 95, 130)
        registerSpecies(891, "Kubfu", PokemonType.FIGHTING, null, 60, 90, 60, 53, 50, 72)
        registerSpecies(892, "Urshifu", PokemonType.FIGHTING, PokemonType.DARK, 100, 130, 100, 63, 60, 97)
        registerSpecies(893, "Zarude", PokemonType.DARK, PokemonType.GRASS, 105, 120, 105, 70, 95, 105)
        registerSpecies(894, "Regieleki", PokemonType.ELECTRIC, null, 80, 100, 50, 100, 50, 200)
        registerSpecies(895, "Regidrago", PokemonType.DRAGON, null, 200, 100, 50, 100, 50, 80)
        registerSpecies(896, "Glastrier", PokemonType.ICE, null, 100, 145, 130, 65, 110, 30)
        registerSpecies(897, "Spectrier", PokemonType.GHOST, null, 100, 65, 60, 145, 80, 130)
        registerSpecies(898, "Calyrex", PokemonType.PSYCHIC, PokemonType.GRASS, 100, 80, 80, 80, 80, 80)
        registerSpecies(899, "Wyrdeer", PokemonType.NORMAL, PokemonType.PSYCHIC, 103, 105, 72, 105, 75, 65)
        registerSpecies(900, "Kleavor", PokemonType.BUG, PokemonType.ROCK, 70, 135, 95, 45, 70, 85)
        registerSpecies(901, "Ursaluna", PokemonType.GROUND, PokemonType.NORMAL, 130, 140, 105, 45, 80, 50)
        registerSpecies(902, "Basculegion", PokemonType.WATER, PokemonType.GHOST, 120, 112, 65, 80, 75, 78)
        registerSpecies(903, "Sneasler", PokemonType.FIGHTING, PokemonType.POISON, 80, 130, 60, 40, 80, 120)
        registerSpecies(904, "Overqwil", PokemonType.DARK, PokemonType.POISON, 85, 115, 95, 65, 65, 85)
        registerSpecies(905, "Enamorus", PokemonType.FAIRY, PokemonType.FLYING, 74, 115, 70, 135, 80, 106)
        registerSpecies(956, "Rattata-A", PokemonType.DARK, PokemonType.NORMAL, 30, 56, 35, 25, 35, 72)
        registerSpecies(957, "Raticate-A", PokemonType.DARK, PokemonType.NORMAL, 75, 71, 70, 40, 80, 77)
        registerSpecies(958, "Raichu-A", PokemonType.ELECTRIC, PokemonType.PSYCHIC, 60, 85, 50, 95, 85, 110)
        registerSpecies(959, "Sandshrew-A", PokemonType.ICE, PokemonType.STEEL, 50, 75, 90, 10, 35, 40)
        registerSpecies(960, "Sandslash-A", PokemonType.ICE, PokemonType.STEEL, 75, 100, 120, 25, 65, 65)
        registerSpecies(961, "Vulpix-A", PokemonType.ICE, null, 38, 41, 40, 50, 65, 65)
        registerSpecies(962, "Ninetales-A", PokemonType.ICE, PokemonType.FAIRY, 73, 67, 75, 81, 100, 109)
        registerSpecies(963, "Diglett-A", PokemonType.GROUND, PokemonType.STEEL, 10, 55, 30, 35, 45, 90)
        registerSpecies(964, "Dugtrio-A", PokemonType.GROUND, PokemonType.STEEL, 35, 100, 60, 50, 70, 110)
        registerSpecies(965, "Meowth-A", PokemonType.DARK, null, 40, 35, 35, 50, 40, 90)
        registerSpecies(966, "Persian-A", PokemonType.DARK, null, 65, 60, 60, 75, 65, 115)
        registerSpecies(967, "Geodude-A", PokemonType.ROCK, PokemonType.ELECTRIC, 40, 80, 100, 30, 30, 20)
        registerSpecies(968, "Graveler-A", PokemonType.ROCK, PokemonType.ELECTRIC, 55, 95, 115, 45, 45, 35)
        registerSpecies(969, "Golem-A", PokemonType.ROCK, PokemonType.ELECTRIC, 80, 120, 130, 55, 65, 45)
        registerSpecies(970, "Grimer-A", PokemonType.POISON, PokemonType.DARK, 80, 80, 50, 40, 50, 25)
        registerSpecies(971, "Muk-A", PokemonType.POISON, PokemonType.DARK, 105, 105, 75, 65, 100, 50)
        registerSpecies(972, "Exeggutor-A", PokemonType.GRASS, PokemonType.DRAGON, 95, 105, 85, 125, 75, 45)
        registerSpecies(973, "Marowak-A", PokemonType.FIRE, PokemonType.GHOST, 60, 80, 110, 50, 80, 45)
        registerSpecies(974, "Meowth-G", PokemonType.STEEL, null, 50, 65, 55, 40, 40, 40)
        registerSpecies(975, "Ponyta-G", PokemonType.PSYCHIC, null, 50, 85, 55, 65, 65, 90)
        registerSpecies(976, "Rapidash-G", PokemonType.PSYCHIC, PokemonType.FAIRY, 65, 100, 70, 80, 80, 105)
        registerSpecies(977, "Slowpoke-G", PokemonType.PSYCHIC, null, 90, 65, 65, 40, 40, 15)
        registerSpecies(978, "Slowbro-G", PokemonType.POISON, PokemonType.PSYCHIC, 95, 100, 95, 100, 70, 30)
        registerSpecies(979, "Farfetch'd-G", PokemonType.FIGHTING, null, 52, 95, 55, 58, 62, 55)
        registerSpecies(980, "Weezing-G", PokemonType.POISON, PokemonType.FAIRY, 65, 90, 120, 85, 70, 60)
        registerSpecies(981, "Mr. Mime-G", PokemonType.ICE, PokemonType.PSYCHIC, 50, 65, 65, 90, 90, 100)
        registerSpecies(982, "Articuno-G", PokemonType.PSYCHIC, PokemonType.FLYING, 90, 85, 85, 125, 100, 95)
        registerSpecies(983, "Zapdos-G", PokemonType.FIGHTING, PokemonType.FLYING, 90, 125, 90, 85, 90, 100)
        registerSpecies(984, "Moltres-G", PokemonType.DARK, PokemonType.FLYING, 90, 85, 90, 100, 125, 90)
        registerSpecies(985, "Slowking-G", PokemonType.POISON, PokemonType.PSYCHIC, 95, 65, 80, 110, 110, 30)
        registerSpecies(986, "Corsola-G", PokemonType.GHOST, null, 60, 55, 100, 65, 100, 30)
        registerSpecies(987, "Zigzagoon-G", PokemonType.DARK, PokemonType.NORMAL, 38, 30, 41, 30, 41, 60)
        registerSpecies(988, "Linoone-G", PokemonType.DARK, PokemonType.NORMAL, 78, 70, 61, 50, 61, 100)
        registerSpecies(989, "Darumaka-G", PokemonType.ICE, null, 70, 90, 45, 15, 45, 50)
        registerSpecies(990, "Darmanitan-G", PokemonType.ICE, null, 105, 140, 55, 30, 55, 95)
        registerSpecies(991, "Yamask-G", PokemonType.GROUND, PokemonType.GHOST, 38, 55, 85, 30, 65, 30)
        registerSpecies(992, "Stunfisk-G", PokemonType.GROUND, PokemonType.STEEL, 109, 81, 99, 66, 84, 32)
        registerSpecies(993, "Growlithe-H", PokemonType.FIRE, PokemonType.ROCK, 60, 75, 45, 65, 50, 55)
        registerSpecies(994, "Arcanine-H", PokemonType.FIRE, PokemonType.ROCK, 95, 115, 80, 95, 80, 90)
        registerSpecies(995, "Voltorb-H", PokemonType.ELECTRIC, PokemonType.GRASS, 40, 30, 50, 55, 55, 100)
        registerSpecies(996, "Electrode-H", PokemonType.ELECTRIC, PokemonType.GRASS, 60, 50, 70, 80, 80, 150)
        registerSpecies(997, "Typhlosion-H", PokemonType.FIRE, PokemonType.GHOST, 73, 84, 78, 119, 85, 95)
        registerSpecies(998, "Qwilfish-H", PokemonType.DARK, PokemonType.POISON, 65, 95, 85, 55, 55, 85)
        registerSpecies(999, "Sneasel-H", PokemonType.FIGHTING, PokemonType.POISON, 55, 95, 55, 35, 75, 115)
        registerSpecies(1000, "Samurott-H", PokemonType.WATER, PokemonType.DARK, 90, 108, 80, 100, 65, 85)
        registerSpecies(1001, "Lilligant-H", PokemonType.GRASS, PokemonType.FIGHTING, 70, 105, 75, 50, 75, 105)
        registerSpecies(1002, "Zorua-H", PokemonType.NORMAL, PokemonType.GHOST, 35, 60, 40, 85, 40, 70)
        registerSpecies(1003, "Zoroark-H", PokemonType.NORMAL, PokemonType.GHOST, 55, 100, 60, 125, 60, 110)
        registerSpecies(1004, "Braviary-H", PokemonType.PSYCHIC, PokemonType.FLYING, 110, 83, 70, 112, 70, 65)
        registerSpecies(1005, "Sliggoo-H", PokemonType.DRAGON, PokemonType.STEEL, 58, 75, 83, 83, 113, 40)
        registerSpecies(1006, "Goodra-H", PokemonType.DRAGON, PokemonType.STEEL, 80, 100, 100, 110, 150, 60)
        registerSpecies(1007, "Avalugg-H", PokemonType.ICE, PokemonType.ROCK, 95, 127, 184, 34, 36, 38)
        registerSpecies(1008, "Decidueye-H", PokemonType.GRASS, PokemonType.FIGHTING, 88, 112, 80, 95, 95, 60)
        registerSpecies(1009, "Pikachu", PokemonType.ELECTRIC, null, 35, 55, 40, 50, 50, 90)
        registerSpecies(1010, "Pikachu", PokemonType.ELECTRIC, null, 35, 55, 40, 50, 50, 90)
        registerSpecies(1011, "Pikachu", PokemonType.ELECTRIC, null, 35, 55, 40, 50, 50, 90)
        registerSpecies(1012, "Pikachu", PokemonType.ELECTRIC, null, 35, 55, 40, 50, 50, 90)
        registerSpecies(1013, "Pikachu", PokemonType.ELECTRIC, null, 35, 55, 40, 50, 50, 90)
        registerSpecies(1014, "Pikachu", PokemonType.ELECTRIC, null, 35, 55, 40, 50, 50, 90)
        registerSpecies(1015, "Pikachu", PokemonType.ELECTRIC, null, 35, 55, 40, 50, 50, 90)
        registerSpecies(1016, "Pikachu", PokemonType.ELECTRIC, null, 35, 55, 40, 50, 50, 90)
        registerSpecies(1017, "Pikachu", PokemonType.ELECTRIC, null, 35, 55, 40, 50, 50, 90)
        registerSpecies(1018, "Pikachu", PokemonType.ELECTRIC, null, 35, 55, 40, 50, 50, 90)
        registerSpecies(1019, "Pikachu", PokemonType.ELECTRIC, null, 35, 55, 40, 50, 50, 90)
        registerSpecies(1020, "Pikachu", PokemonType.ELECTRIC, null, 35, 55, 40, 50, 50, 90)
        registerSpecies(1021, "Pikachu", PokemonType.ELECTRIC, null, 35, 55, 40, 50, 50, 90)
        registerSpecies(1022, "Pikachu", PokemonType.ELECTRIC, null, 35, 55, 40, 50, 50, 90)
        registerSpecies(1023, "Pichu", PokemonType.ELECTRIC, null, 20, 40, 15, 35, 35, 60)
        registerSpecies(1024, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1025, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1026, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1027, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1028, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1029, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1030, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1031, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1032, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1033, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1034, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1035, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1036, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1037, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1038, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1039, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1040, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1041, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1042, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1043, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1044, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1045, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1046, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1047, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1048, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1049, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
        registerSpecies(1050, "Unown", PokemonType.PSYCHIC, null, 48, 72, 48, 72, 48, 48)
    }

    private fun registerSpeciesChunk6() {
        registerSpecies(1051, "Castform", PokemonType.FIRE, null, 70, 70, 70, 70, 70, 70)
        registerSpecies(1052, "Castform", PokemonType.WATER, null, 70, 70, 70, 70, 70, 70)
        registerSpecies(1053, "Castform", PokemonType.ICE, null, 70, 70, 70, 70, 70, 70)
        registerSpecies(1054, "Deoxys", PokemonType.PSYCHIC, null, 50, 180, 20, 180, 20, 150)
        registerSpecies(1055, "Deoxys", PokemonType.PSYCHIC, null, 50, 70, 160, 70, 160, 90)
        registerSpecies(1056, "Deoxys", PokemonType.PSYCHIC, null, 50, 95, 90, 95, 90, 180)
        registerSpecies(1057, "Burmy", PokemonType.BUG, null, 40, 29, 45, 29, 45, 36)
        registerSpecies(1058, "Burmy", PokemonType.BUG, null, 40, 29, 45, 29, 45, 36)
        registerSpecies(1059, "Wormadam", PokemonType.BUG, PokemonType.GROUND, 60, 79, 105, 59, 85, 36)
        registerSpecies(1060, "Wormadam", PokemonType.BUG, PokemonType.STEEL, 60, 69, 95, 69, 95, 36)
        registerSpecies(1061, "Cherrim", PokemonType.GRASS, null, 70, 60, 70, 87, 78, 85)
        registerSpecies(1062, "Shellos", PokemonType.WATER, null, 76, 48, 48, 57, 62, 34)
        registerSpecies(1063, "Gastrodon", PokemonType.WATER, PokemonType.GROUND, 111, 83, 68, 92, 82, 39)
        registerSpecies(1064, "Rotom", PokemonType.ELECTRIC, PokemonType.FIRE, 50, 65, 107, 105, 107, 86)
        registerSpecies(1065, "Rotom", PokemonType.ELECTRIC, PokemonType.WATER, 50, 65, 107, 105, 107, 86)
        registerSpecies(1066, "Rotom", PokemonType.ELECTRIC, PokemonType.ICE, 50, 65, 107, 105, 107, 86)
        registerSpecies(1067, "Rotom", PokemonType.ELECTRIC, PokemonType.FLYING, 50, 65, 107, 105, 107, 86)
        registerSpecies(1068, "Rotom", PokemonType.ELECTRIC, PokemonType.GRASS, 50, 65, 107, 105, 107, 86)
        registerSpecies(1069, "Dialga", PokemonType.STEEL, PokemonType.DRAGON, 100, 100, 120, 150, 120, 90)
        registerSpecies(1070, "Palkia", PokemonType.WATER, PokemonType.DRAGON, 90, 100, 100, 150, 120, 120)
        registerSpecies(1071, "Giratina", PokemonType.GHOST, PokemonType.DRAGON, 150, 120, 100, 120, 100, 90)
        registerSpecies(1072, "Shaymin", PokemonType.GRASS, PokemonType.FLYING, 100, 103, 75, 120, 75, 127)
        registerSpecies(1073, "Arceus", PokemonType.FIGHTING, null, 120, 120, 120, 120, 120, 120)
        registerSpecies(1074, "Arceus", PokemonType.FLYING, null, 120, 120, 120, 120, 120, 120)
        registerSpecies(1075, "Arceus", PokemonType.POISON, null, 120, 120, 120, 120, 120, 120)
        registerSpecies(1076, "Arceus", PokemonType.GROUND, null, 120, 120, 120, 120, 120, 120)
        registerSpecies(1077, "Arceus", PokemonType.ROCK, null, 120, 120, 120, 120, 120, 120)
        registerSpecies(1078, "Arceus", PokemonType.BUG, null, 120, 120, 120, 120, 120, 120)
        registerSpecies(1079, "Arceus", PokemonType.GHOST, null, 120, 120, 120, 120, 120, 120)
        registerSpecies(1080, "Arceus", PokemonType.STEEL, null, 120, 120, 120, 120, 120, 120)
        registerSpecies(1081, "Arceus", PokemonType.FIRE, null, 120, 120, 120, 120, 120, 120)
        registerSpecies(1082, "Arceus", PokemonType.WATER, null, 120, 120, 120, 120, 120, 120)
        registerSpecies(1083, "Arceus", PokemonType.GRASS, null, 120, 120, 120, 120, 120, 120)
        registerSpecies(1084, "Arceus", PokemonType.ELECTRIC, null, 120, 120, 120, 120, 120, 120)
        registerSpecies(1085, "Arceus", PokemonType.PSYCHIC, null, 120, 120, 120, 120, 120, 120)
        registerSpecies(1086, "Arceus", PokemonType.ICE, null, 120, 120, 120, 120, 120, 120)
        registerSpecies(1087, "Arceus", PokemonType.DRAGON, null, 120, 120, 120, 120, 120, 120)
        registerSpecies(1088, "Arceus", PokemonType.DARK, null, 120, 120, 120, 120, 120, 120)
        registerSpecies(1089, "Arceus", PokemonType.FAIRY, null, 120, 120, 120, 120, 120, 120)
        registerSpecies(1090, "Basculin", PokemonType.WATER, null, 70, 92, 65, 80, 55, 98)
        registerSpecies(1091, "Basculin", PokemonType.WATER, null, 70, 92, 65, 80, 55, 98)
        registerSpecies(1092, "Darmanitan", PokemonType.FIRE, PokemonType.PSYCHIC, 105, 30, 105, 140, 105, 55)
        registerSpecies(1093, "Darmanitan-G", PokemonType.ICE, PokemonType.FIRE, 105, 160, 55, 30, 55, 135)
        registerSpecies(1094, "Deerling", PokemonType.NORMAL, PokemonType.GRASS, 60, 60, 50, 40, 50, 75)
        registerSpecies(1095, "Deerling", PokemonType.NORMAL, PokemonType.GRASS, 60, 60, 50, 40, 50, 75)
        registerSpecies(1096, "Deerling", PokemonType.NORMAL, PokemonType.GRASS, 60, 60, 50, 40, 50, 75)
        registerSpecies(1097, "Sawsbuck", PokemonType.NORMAL, PokemonType.GRASS, 80, 100, 70, 60, 70, 95)
        registerSpecies(1098, "Sawsbuck", PokemonType.NORMAL, PokemonType.GRASS, 80, 100, 70, 60, 70, 95)
        registerSpecies(1099, "Sawsbuck", PokemonType.NORMAL, PokemonType.GRASS, 80, 100, 70, 60, 70, 95)
        registerSpecies(1100, "Tornadus", PokemonType.FLYING, null, 79, 100, 80, 110, 90, 121)
        registerSpecies(1101, "Thundurus", PokemonType.ELECTRIC, PokemonType.FLYING, 79, 105, 70, 145, 80, 101)
        registerSpecies(1102, "Landorus", PokemonType.GROUND, PokemonType.FLYING, 89, 145, 90, 105, 80, 91)
        registerSpecies(1103, "Enamorus", PokemonType.FAIRY, PokemonType.FLYING, 74, 115, 110, 135, 100, 46)
        registerSpecies(1106, "Keldeo", PokemonType.WATER, PokemonType.FIGHTING, 91, 72, 90, 129, 90, 108)
        registerSpecies(1107, "Meloetta", PokemonType.NORMAL, PokemonType.FIGHTING, 100, 128, 90, 77, 77, 128)
        registerSpecies(1108, "Genesect", PokemonType.BUG, PokemonType.STEEL, 71, 120, 95, 120, 95, 99)
        registerSpecies(1109, "Genesect", PokemonType.BUG, PokemonType.STEEL, 71, 120, 95, 120, 95, 99)
        registerSpecies(1110, "Genesect", PokemonType.BUG, PokemonType.STEEL, 71, 120, 95, 120, 95, 99)
        registerSpecies(1111, "Genesect", PokemonType.BUG, PokemonType.STEEL, 71, 120, 95, 120, 95, 99)
        registerSpecies(1112, "Greninja", PokemonType.WATER, PokemonType.DARK, 72, 95, 67, 103, 71, 122)
        registerSpecies(1113, "Greninja", PokemonType.WATER, PokemonType.DARK, 72, 145, 67, 153, 71, 132)
        registerSpecies(1114, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(1115, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(1116, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(1117, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(1118, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(1119, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(1120, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(1121, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(1122, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(1123, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(1124, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(1125, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(1126, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(1127, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(1128, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(1129, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(1130, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(1131, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(1132, "Vivillon", PokemonType.BUG, PokemonType.FLYING, 80, 52, 50, 90, 50, 89)
        registerSpecies(1133, "Flabébé", PokemonType.FAIRY, null, 44, 38, 39, 61, 79, 42)
        registerSpecies(1134, "Flabébé", PokemonType.FAIRY, null, 44, 38, 39, 61, 79, 42)
        registerSpecies(1135, "Flabébé", PokemonType.FAIRY, null, 44, 38, 39, 61, 79, 42)
        registerSpecies(1136, "Flabébé", PokemonType.FAIRY, null, 44, 38, 39, 61, 79, 42)
        registerSpecies(1137, "Floette", PokemonType.FAIRY, null, 54, 45, 47, 75, 98, 52)
        registerSpecies(1138, "Floette", PokemonType.FAIRY, null, 54, 45, 47, 75, 98, 52)
        registerSpecies(1139, "Floette", PokemonType.FAIRY, null, 54, 45, 47, 75, 98, 52)
        registerSpecies(1140, "Floette", PokemonType.FAIRY, null, 54, 45, 47, 75, 98, 52)
        registerSpecies(1141, "Floette", PokemonType.FAIRY, null, 74, 65, 67, 125, 128, 92)
        registerSpecies(1142, "Florges", PokemonType.FAIRY, null, 78, 65, 68, 112, 154, 75)
        registerSpecies(1143, "Florges", PokemonType.FAIRY, null, 78, 65, 68, 112, 154, 75)
        registerSpecies(1144, "Florges", PokemonType.FAIRY, null, 78, 65, 68, 112, 154, 75)
        registerSpecies(1145, "Florges", PokemonType.FAIRY, null, 78, 65, 68, 112, 154, 75)
        registerSpecies(1146, "Furfrou", PokemonType.NORMAL, null, 75, 80, 60, 65, 90, 102)
        registerSpecies(1147, "Furfrou", PokemonType.NORMAL, null, 75, 80, 60, 65, 90, 102)
        registerSpecies(1148, "Furfrou", PokemonType.NORMAL, null, 75, 80, 60, 65, 90, 102)
        registerSpecies(1149, "Furfrou", PokemonType.NORMAL, null, 75, 80, 60, 65, 90, 102)
        registerSpecies(1150, "Furfrou", PokemonType.NORMAL, null, 75, 80, 60, 65, 90, 102)
        registerSpecies(1151, "Furfrou", PokemonType.NORMAL, null, 75, 80, 60, 65, 90, 102)
        registerSpecies(1152, "Furfrou", PokemonType.NORMAL, null, 75, 80, 60, 65, 90, 102)
        registerSpecies(1153, "Furfrou", PokemonType.NORMAL, null, 75, 80, 60, 65, 90, 102)
        registerSpecies(1154, "Furfrou", PokemonType.NORMAL, null, 75, 80, 60, 65, 90, 102)
        registerSpecies(1155, "Meowstic", PokemonType.PSYCHIC, null, 74, 48, 76, 83, 81, 104)
        registerSpecies(1156, "Aegislash", PokemonType.STEEL, PokemonType.GHOST, 60, 140, 50, 140, 50, 60)
        registerSpecies(1157, "Pumpkaboo", PokemonType.GHOST, PokemonType.GRASS, 44, 66, 70, 44, 55, 56)
        registerSpecies(1158, "Pumpkaboo", PokemonType.GHOST, PokemonType.GRASS, 54, 66, 70, 44, 55, 46)
        registerSpecies(1159, "Pumpkaboo", PokemonType.GHOST, PokemonType.GRASS, 59, 66, 70, 44, 55, 41)
        registerSpecies(1160, "Gourgeist", PokemonType.GHOST, PokemonType.GRASS, 55, 85, 122, 58, 75, 99)
        registerSpecies(1161, "Gourgeist", PokemonType.GHOST, PokemonType.GRASS, 75, 95, 122, 58, 75, 69)
        registerSpecies(1162, "Gourgeist", PokemonType.GHOST, PokemonType.GRASS, 85, 100, 122, 58, 75, 54)
        registerSpecies(1163, "Xerneas", PokemonType.FAIRY, null, 126, 131, 95, 131, 98, 99)
        registerSpecies(1164, "Zygarde", PokemonType.DRAGON, PokemonType.GROUND, 54, 100, 71, 61, 85, 115)
        registerSpecies(1165, "Zygarde", PokemonType.DRAGON, PokemonType.GROUND, 54, 100, 71, 61, 85, 115)
        registerSpecies(1166, "Zygarde", PokemonType.DRAGON, PokemonType.GROUND, 108, 100, 121, 81, 95, 95)
        registerSpecies(1167, "Zygarde", PokemonType.DRAGON, PokemonType.GROUND, 216, 100, 121, 91, 95, 85)
        registerSpecies(1168, "Hoopa", PokemonType.PSYCHIC, PokemonType.DARK, 80, 160, 60, 170, 130, 80)
        registerSpecies(1169, "Oricorio", PokemonType.ELECTRIC, PokemonType.FLYING, 75, 70, 70, 98, 70, 93)
        registerSpecies(1170, "Oricorio", PokemonType.PSYCHIC, PokemonType.FLYING, 75, 70, 70, 98, 70, 93)
        registerSpecies(1171, "Oricorio", PokemonType.GHOST, PokemonType.FLYING, 75, 70, 70, 98, 70, 93)
        registerSpecies(1172, "Rockruff", PokemonType.ROCK, null, 45, 65, 40, 30, 40, 60)
        registerSpecies(1173, "Lycanroc", PokemonType.ROCK, null, 85, 115, 75, 55, 75, 82)
        registerSpecies(1174, "Lycanroc", PokemonType.ROCK, null, 75, 117, 65, 55, 65, 110)
        registerSpecies(1175, "Wishiwashi", PokemonType.WATER, null, 45, 140, 130, 140, 135, 30)
        registerSpecies(1176, "Silvally", PokemonType.FIGHTING, null, 95, 95, 95, 95, 95, 95)
        registerSpecies(1177, "Silvally", PokemonType.FLYING, null, 95, 95, 95, 95, 95, 95)
        registerSpecies(1178, "Silvally", PokemonType.POISON, null, 95, 95, 95, 95, 95, 95)
        registerSpecies(1179, "Silvally", PokemonType.GROUND, null, 95, 95, 95, 95, 95, 95)
        registerSpecies(1180, "Silvally", PokemonType.ROCK, null, 95, 95, 95, 95, 95, 95)
        registerSpecies(1181, "Silvally", PokemonType.BUG, null, 95, 95, 95, 95, 95, 95)
        registerSpecies(1182, "Silvally", PokemonType.GHOST, null, 95, 95, 95, 95, 95, 95)
        registerSpecies(1183, "Silvally", PokemonType.STEEL, null, 95, 95, 95, 95, 95, 95)
        registerSpecies(1184, "Silvally", PokemonType.FIRE, null, 95, 95, 95, 95, 95, 95)
        registerSpecies(1185, "Silvally", PokemonType.WATER, null, 95, 95, 95, 95, 95, 95)
        registerSpecies(1186, "Silvally", PokemonType.GRASS, null, 95, 95, 95, 95, 95, 95)
        registerSpecies(1187, "Silvally", PokemonType.ELECTRIC, null, 95, 95, 95, 95, 95, 95)
        registerSpecies(1188, "Silvally", PokemonType.PSYCHIC, null, 95, 95, 95, 95, 95, 95)
        registerSpecies(1189, "Silvally", PokemonType.ICE, null, 95, 95, 95, 95, 95, 95)
        registerSpecies(1190, "Silvally", PokemonType.DRAGON, null, 95, 95, 95, 95, 95, 95)
        registerSpecies(1191, "Silvally", PokemonType.DARK, null, 95, 95, 95, 95, 95, 95)
        registerSpecies(1192, "Silvally", PokemonType.FAIRY, null, 95, 95, 95, 95, 95, 95)
        registerSpecies(1193, "Minior", PokemonType.ROCK, PokemonType.FLYING, 60, 60, 100, 60, 100, 60)
        registerSpecies(1194, "Minior", PokemonType.ROCK, PokemonType.FLYING, 60, 60, 100, 60, 100, 60)
        registerSpecies(1195, "Minior", PokemonType.ROCK, PokemonType.FLYING, 60, 60, 100, 60, 100, 60)
        registerSpecies(1196, "Minior", PokemonType.ROCK, PokemonType.FLYING, 60, 60, 100, 60, 100, 60)
        registerSpecies(1197, "Minior", PokemonType.ROCK, PokemonType.FLYING, 60, 60, 100, 60, 100, 60)
        registerSpecies(1198, "Minior", PokemonType.ROCK, PokemonType.FLYING, 60, 60, 100, 60, 100, 60)
        registerSpecies(1199, "Minior", PokemonType.ROCK, PokemonType.FLYING, 60, 100, 60, 100, 60, 120)
        registerSpecies(1200, "Minior", PokemonType.ROCK, PokemonType.FLYING, 60, 100, 60, 100, 60, 120)
        registerSpecies(1201, "Minior", PokemonType.ROCK, PokemonType.FLYING, 60, 100, 60, 100, 60, 120)
        registerSpecies(1202, "Minior", PokemonType.ROCK, PokemonType.FLYING, 60, 100, 60, 100, 60, 120)
        registerSpecies(1203, "Minior", PokemonType.ROCK, PokemonType.FLYING, 60, 100, 60, 100, 60, 120)
        registerSpecies(1204, "Minior", PokemonType.ROCK, PokemonType.FLYING, 60, 100, 60, 100, 60, 120)
        registerSpecies(1205, "Minior", PokemonType.ROCK, PokemonType.FLYING, 60, 100, 60, 100, 60, 120)
        registerSpecies(1206, "Mimikyu", PokemonType.GHOST, PokemonType.FAIRY, 55, 90, 80, 50, 105, 96)
        registerSpecies(1210, "Magearna", PokemonType.STEEL, PokemonType.FAIRY, 80, 95, 115, 130, 115, 65)
        registerSpecies(1211, "Cramorant", PokemonType.FLYING, PokemonType.WATER, 70, 85, 55, 85, 95, 85)
        registerSpecies(1212, "Cramorant", PokemonType.FLYING, PokemonType.WATER, 70, 85, 55, 85, 95, 85)
        registerSpecies(1213, "Toxtricity", PokemonType.ELECTRIC, PokemonType.POISON, 75, 98, 70, 114, 70, 75)
        registerSpecies(1214, "Sinistea", PokemonType.GHOST, null, 40, 45, 45, 74, 54, 50)
        registerSpecies(1215, "Polteageist", PokemonType.GHOST, null, 60, 65, 65, 134, 114, 70)
        registerSpecies(1216, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1217, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1218, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1219, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1220, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1221, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1222, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1223, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1224, "Eiscue", PokemonType.ICE, null, 75, 80, 70, 65, 50, 130)
        registerSpecies(1225, "Indeedee", PokemonType.PSYCHIC, PokemonType.NORMAL, 70, 55, 65, 95, 105, 85)
        registerSpecies(1226, "Morpeko", PokemonType.ELECTRIC, PokemonType.DARK, 58, 95, 58, 70, 58, 97)
        registerSpecies(1227, "Zacian", PokemonType.FAIRY, PokemonType.STEEL, 92, 150, 115, 80, 115, 148)
        registerSpecies(1228, "Zamazenta", PokemonType.FIGHTING, PokemonType.STEEL, 92, 120, 140, 80, 140, 128)
        registerSpecies(1229, "Eternatus", PokemonType.POISON, PokemonType.DRAGON, 255, 115, 250, 125, 250, 130)
        registerSpecies(1230, "Urshifu", PokemonType.FIGHTING, PokemonType.WATER, 100, 130, 100, 63, 60, 97)
        registerSpecies(1231, "Zarude", PokemonType.DARK, PokemonType.GRASS, 105, 120, 105, 70, 95, 105)
        registerSpecies(1234, "Basculegion", PokemonType.WATER, PokemonType.GHOST, 120, 92, 65, 100, 75, 78)
        registerSpecies(1235, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1236, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1237, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1238, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1239, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1240, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1241, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1242, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1243, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1244, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1245, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1246, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1247, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1248, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1249, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1250, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1251, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1252, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1253, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1254, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1255, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1256, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1257, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
    }

    private fun registerSpeciesChunk7() {
        registerSpecies(1258, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1259, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1260, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1261, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1262, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1263, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1264, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1265, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1266, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1267, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1268, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1269, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1270, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1271, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1272, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1273, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1274, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1275, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1276, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1277, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1278, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1279, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1280, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1281, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1282, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1283, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1284, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1285, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1286, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1287, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1288, "Alcremie", PokemonType.FAIRY, null, 65, 60, 75, 110, 121, 64)
        registerSpecies(1289, "Sprigatito", PokemonType.GRASS, null, 40, 61, 54, 45, 45, 65)
        registerSpecies(1290, "Floragato", PokemonType.GRASS, null, 61, 80, 63, 60, 63, 83)
        registerSpecies(1291, "Meowscarada", PokemonType.GRASS, PokemonType.DARK, 76, 110, 70, 81, 70, 123)
        registerSpecies(1292, "Fuecoco", PokemonType.FIRE, null, 67, 45, 59, 63, 40, 36)
        registerSpecies(1293, "Crocalor", PokemonType.FIRE, null, 81, 55, 78, 90, 58, 49)
        registerSpecies(1294, "Skeledirge", PokemonType.FIRE, PokemonType.GHOST, 104, 75, 100, 110, 75, 66)
        registerSpecies(1295, "Quaxly", PokemonType.WATER, null, 55, 65, 45, 50, 45, 50)
        registerSpecies(1296, "Quaxwell", PokemonType.WATER, null, 70, 85, 65, 65, 60, 65)
        registerSpecies(1297, "Quaquaval", PokemonType.WATER, PokemonType.FIGHTING, 85, 120, 80, 85, 75, 85)
        registerSpecies(1298, "Lechonk", PokemonType.NORMAL, null, 54, 45, 40, 35, 45, 35)
        registerSpecies(1299, "Oinkologne", PokemonType.NORMAL, null, 110, 100, 75, 59, 80, 65)
        registerSpecies(1300, "Oinkologne", PokemonType.NORMAL, null, 115, 90, 70, 59, 90, 65)
        registerSpecies(1301, "Tarountula", PokemonType.BUG, null, 35, 41, 45, 29, 40, 20)
        registerSpecies(1302, "Spidops", PokemonType.BUG, null, 60, 79, 92, 52, 86, 35)
        registerSpecies(1303, "Nymble", PokemonType.BUG, null, 33, 46, 40, 21, 25, 45)
        registerSpecies(1304, "Lokix", PokemonType.BUG, PokemonType.DARK, 71, 102, 78, 52, 55, 92)
        registerSpecies(1305, "Pawmi", PokemonType.ELECTRIC, null, 45, 50, 20, 40, 25, 60)
        registerSpecies(1306, "Pawmo", PokemonType.ELECTRIC, PokemonType.FIGHTING, 60, 75, 40, 50, 40, 85)
        registerSpecies(1307, "Pawmot", PokemonType.ELECTRIC, PokemonType.FIGHTING, 70, 115, 70, 70, 60, 105)
        registerSpecies(1308, "Tandemaus", PokemonType.NORMAL, null, 50, 50, 45, 40, 45, 75)
        registerSpecies(1309, "Maushold", PokemonType.NORMAL, null, 74, 75, 70, 65, 75, 111)
        registerSpecies(1310, "Maushold", PokemonType.NORMAL, null, 74, 75, 70, 65, 75, 111)
        registerSpecies(1311, "Fidough", PokemonType.FAIRY, null, 37, 55, 70, 30, 55, 65)
        registerSpecies(1312, "Dachsbun", PokemonType.FAIRY, null, 57, 80, 115, 50, 80, 95)
        registerSpecies(1313, "Smoliv", PokemonType.GRASS, PokemonType.NORMAL, 41, 35, 45, 58, 51, 30)
        registerSpecies(1314, "Dolliv", PokemonType.GRASS, PokemonType.NORMAL, 52, 53, 60, 78, 78, 33)
        registerSpecies(1315, "Arboliva", PokemonType.GRASS, PokemonType.NORMAL, 78, 69, 90, 125, 109, 39)
        registerSpecies(1316, "Squawkabilly", PokemonType.NORMAL, PokemonType.FLYING, 82, 96, 51, 45, 51, 92)
        registerSpecies(1317, "Squawkabilly", PokemonType.NORMAL, PokemonType.FLYING, 82, 96, 51, 45, 51, 92)
        registerSpecies(1318, "Squawkabilly", PokemonType.NORMAL, PokemonType.FLYING, 82, 96, 51, 45, 51, 92)
        registerSpecies(1319, "Squawkabilly", PokemonType.NORMAL, PokemonType.FLYING, 82, 96, 51, 45, 51, 92)
        registerSpecies(1320, "Nacli", PokemonType.ROCK, null, 55, 55, 75, 35, 35, 25)
        registerSpecies(1321, "Naclstack", PokemonType.ROCK, null, 60, 60, 100, 35, 65, 35)
        registerSpecies(1322, "Garganacl", PokemonType.ROCK, null, 100, 100, 130, 45, 90, 35)
        registerSpecies(1323, "Charcadet", PokemonType.FIRE, null, 40, 50, 40, 50, 40, 35)
        registerSpecies(1324, "Armarouge", PokemonType.FIRE, PokemonType.PSYCHIC, 85, 60, 100, 125, 80, 75)
        registerSpecies(1325, "Ceruledge", PokemonType.FIRE, PokemonType.GHOST, 75, 125, 80, 60, 100, 85)
        registerSpecies(1326, "Tadbulb", PokemonType.ELECTRIC, null, 61, 31, 41, 59, 35, 45)
        registerSpecies(1327, "Bellibolt", PokemonType.ELECTRIC, null, 109, 64, 91, 103, 83, 45)
        registerSpecies(1328, "Wattrel", PokemonType.ELECTRIC, PokemonType.FLYING, 40, 40, 35, 55, 40, 70)
        registerSpecies(1329, "Kilowattrel", PokemonType.ELECTRIC, PokemonType.FLYING, 70, 70, 60, 105, 60, 125)
        registerSpecies(1330, "Maschiff", PokemonType.DARK, null, 60, 78, 60, 40, 51, 51)
        registerSpecies(1331, "Mabosstiff", PokemonType.DARK, null, 80, 120, 90, 60, 70, 85)
        registerSpecies(1332, "Shroodle", PokemonType.POISON, PokemonType.NORMAL, 40, 65, 35, 40, 35, 75)
        registerSpecies(1333, "Grafaiai", PokemonType.POISON, PokemonType.NORMAL, 63, 95, 65, 80, 72, 110)
        registerSpecies(1334, "Bramblin", PokemonType.GRASS, PokemonType.GHOST, 40, 65, 30, 45, 35, 60)
        registerSpecies(1335, "Brambleghast", PokemonType.GRASS, PokemonType.GHOST, 55, 115, 70, 80, 70, 90)
        registerSpecies(1336, "Toedscool", PokemonType.GROUND, PokemonType.GRASS, 40, 40, 35, 50, 100, 70)
        registerSpecies(1337, "Toedscruel", PokemonType.GROUND, PokemonType.GRASS, 80, 70, 65, 80, 120, 100)
        registerSpecies(1338, "Klawf", PokemonType.ROCK, null, 70, 100, 115, 35, 55, 75)
        registerSpecies(1339, "Capsakid", PokemonType.GRASS, null, 50, 62, 40, 62, 40, 50)
        registerSpecies(1340, "Scovillain", PokemonType.GRASS, PokemonType.FIRE, 65, 108, 65, 108, 65, 75)
        registerSpecies(1341, "Rellor", PokemonType.BUG, null, 41, 50, 60, 31, 58, 30)
        registerSpecies(1342, "Rabsca", PokemonType.BUG, PokemonType.PSYCHIC, 75, 50, 85, 115, 100, 45)
        registerSpecies(1343, "Flittle", PokemonType.PSYCHIC, null, 30, 35, 30, 55, 30, 75)
        registerSpecies(1344, "Espathra", PokemonType.PSYCHIC, null, 95, 60, 60, 101, 60, 105)
        registerSpecies(1345, "Tinkatink", PokemonType.FAIRY, PokemonType.STEEL, 50, 45, 45, 35, 64, 58)
        registerSpecies(1346, "Tinkatuff", PokemonType.FAIRY, PokemonType.STEEL, 65, 55, 55, 45, 82, 78)
        registerSpecies(1347, "Tinkaton", PokemonType.FAIRY, PokemonType.STEEL, 85, 75, 77, 70, 105, 94)
        registerSpecies(1348, "Wiglett", PokemonType.WATER, null, 10, 55, 25, 35, 25, 95)
        registerSpecies(1349, "Wugtrio", PokemonType.WATER, null, 35, 100, 50, 50, 70, 120)
        registerSpecies(1350, "Bombirdier", PokemonType.FLYING, PokemonType.DARK, 70, 103, 85, 60, 85, 82)
        registerSpecies(1351, "Finizen", PokemonType.WATER, null, 70, 45, 40, 45, 40, 75)
        registerSpecies(1352, "Palafin", PokemonType.WATER, null, 100, 70, 72, 53, 62, 100)
        registerSpecies(1353, "Palafin", PokemonType.WATER, null, 100, 160, 97, 106, 87, 100)
        registerSpecies(1354, "Varoom", PokemonType.STEEL, PokemonType.POISON, 45, 70, 63, 30, 45, 47)
        registerSpecies(1355, "Revavroom", PokemonType.STEEL, PokemonType.POISON, 80, 119, 90, 54, 67, 90)
        registerSpecies(1356, "Cyclizar", PokemonType.DRAGON, PokemonType.NORMAL, 70, 95, 65, 85, 65, 121)
        registerSpecies(1357, "Orthworm", PokemonType.STEEL, null, 70, 85, 145, 60, 55, 65)
        registerSpecies(1358, "Glimmet", PokemonType.ROCK, PokemonType.POISON, 48, 35, 42, 105, 60, 60)
        registerSpecies(1359, "Glimmora", PokemonType.ROCK, PokemonType.POISON, 83, 55, 90, 130, 81, 86)
        registerSpecies(1360, "Greavard", PokemonType.GHOST, null, 50, 61, 60, 30, 55, 34)
        registerSpecies(1361, "Houndstone", PokemonType.GHOST, null, 72, 101, 100, 50, 97, 68)
        registerSpecies(1362, "Flamigo", PokemonType.FLYING, PokemonType.FIGHTING, 82, 115, 74, 75, 64, 90)
        registerSpecies(1363, "Cetoddle", PokemonType.ICE, null, 108, 68, 45, 30, 40, 43)
        registerSpecies(1364, "Cetitan", PokemonType.ICE, null, 170, 113, 65, 45, 55, 73)
        registerSpecies(1365, "Veluza", PokemonType.WATER, PokemonType.PSYCHIC, 90, 102, 73, 78, 65, 70)
        registerSpecies(1366, "Dondozo", PokemonType.WATER, null, 150, 100, 115, 65, 65, 35)
        registerSpecies(1367, "Tatsugiri", PokemonType.DRAGON, PokemonType.WATER, 68, 50, 60, 120, 95, 82)
        registerSpecies(1368, "Tatsugiri", PokemonType.DRAGON, PokemonType.WATER, 68, 50, 60, 120, 95, 82)
        registerSpecies(1369, "Tatsugiri", PokemonType.DRAGON, PokemonType.WATER, 68, 50, 60, 120, 95, 82)
        registerSpecies(1370, "Annihilape", PokemonType.FIGHTING, PokemonType.GHOST, 110, 115, 80, 50, 90, 90)
        registerSpecies(1371, "Clodsire", PokemonType.POISON, PokemonType.GROUND, 130, 75, 60, 45, 100, 20)
        registerSpecies(1372, "Farigiraf", PokemonType.NORMAL, PokemonType.PSYCHIC, 120, 90, 70, 110, 70, 60)
        registerSpecies(1373, "Dudunsparce", PokemonType.NORMAL, null, 125, 100, 80, 85, 75, 55)
        registerSpecies(1374, "Dudunsparce", PokemonType.NORMAL, null, 125, 100, 80, 85, 75, 55)
        registerSpecies(1375, "Kingambit", PokemonType.DARK, PokemonType.STEEL, 100, 135, 120, 60, 85, 50)
        registerSpecies(1376, "Great Tusk", PokemonType.GROUND, PokemonType.FIGHTING, 115, 131, 131, 53, 53, 87)
        registerSpecies(1377, "Scream Tail", PokemonType.FAIRY, PokemonType.PSYCHIC, 115, 65, 99, 65, 115, 111)
        registerSpecies(1378, "Brute Bonnet", PokemonType.GRASS, PokemonType.DARK, 111, 127, 99, 79, 99, 55)
        registerSpecies(1379, "Flutter Mane", PokemonType.GHOST, PokemonType.FAIRY, 55, 55, 55, 135, 135, 135)
        registerSpecies(1380, "Slither Wing", PokemonType.BUG, PokemonType.FIGHTING, 85, 135, 79, 85, 105, 81)
        registerSpecies(1381, "Sandy Shocks", PokemonType.ELECTRIC, PokemonType.GROUND, 85, 81, 97, 121, 85, 101)
        registerSpecies(1382, "Iron Treads", PokemonType.GROUND, PokemonType.STEEL, 90, 112, 120, 72, 70, 106)
        registerSpecies(1383, "Iron Bundle", PokemonType.ICE, PokemonType.WATER, 56, 80, 114, 124, 60, 136)
        registerSpecies(1384, "Iron Hands", PokemonType.FIGHTING, PokemonType.ELECTRIC, 154, 140, 108, 50, 68, 50)
        registerSpecies(1385, "Iron Jugulis", PokemonType.DARK, PokemonType.FLYING, 94, 80, 86, 122, 80, 108)
        registerSpecies(1386, "Iron Moth", PokemonType.FIRE, PokemonType.POISON, 80, 70, 60, 140, 110, 110)
        registerSpecies(1387, "Iron Thorns", PokemonType.ROCK, PokemonType.ELECTRIC, 100, 134, 110, 70, 84, 72)
        registerSpecies(1388, "Frigibax", PokemonType.DRAGON, PokemonType.ICE, 65, 75, 45, 35, 45, 55)
        registerSpecies(1389, "Arctibax", PokemonType.DRAGON, PokemonType.ICE, 90, 95, 66, 45, 65, 62)
        registerSpecies(1390, "Baxcalibur", PokemonType.DRAGON, PokemonType.ICE, 115, 145, 92, 75, 86, 87)
        registerSpecies(1391, "Gimmighoul", PokemonType.GHOST, null, 45, 30, 70, 75, 70, 10)
        registerSpecies(1392, "Gimmighoul", PokemonType.GHOST, null, 45, 30, 25, 75, 45, 80)
        registerSpecies(1393, "Gholdengo", PokemonType.STEEL, PokemonType.GHOST, 87, 60, 95, 133, 91, 84)
        registerSpecies(1394, "Wo-Chien", PokemonType.DARK, PokemonType.GRASS, 85, 85, 100, 95, 135, 70)
        registerSpecies(1395, "Chien-Pao", PokemonType.DARK, PokemonType.ICE, 80, 120, 80, 90, 65, 135)
        registerSpecies(1396, "Ting-Lu", PokemonType.DARK, PokemonType.GROUND, 155, 110, 125, 55, 80, 45)
        registerSpecies(1397, "Chi-Yu", PokemonType.DARK, PokemonType.FIRE, 55, 80, 80, 135, 120, 100)
        registerSpecies(1398, "Roaring Moon", PokemonType.DRAGON, PokemonType.DARK, 105, 139, 71, 55, 101, 119)
        registerSpecies(1399, "Iron Valiant", PokemonType.FAIRY, PokemonType.FIGHTING, 74, 130, 90, 120, 60, 116)
        registerSpecies(1400, "Koraidon", PokemonType.FIGHTING, PokemonType.DRAGON, 100, 135, 115, 85, 100, 135)
        registerSpecies(1401, "Miraidon", PokemonType.ELECTRIC, PokemonType.DRAGON, 100, 85, 100, 135, 115, 135)
        registerSpecies(1402, "Tauros-P", PokemonType.FIGHTING, null, 75, 110, 105, 30, 70, 100)
        registerSpecies(1403, "Tauros-P", PokemonType.FIGHTING, PokemonType.FIRE, 75, 110, 105, 30, 70, 100)
        registerSpecies(1404, "Tauros-P", PokemonType.FIGHTING, PokemonType.WATER, 75, 110, 105, 30, 70, 100)
        registerSpecies(1405, "Wooper-P", PokemonType.POISON, PokemonType.GROUND, 55, 45, 45, 25, 25, 15)
        registerSpecies(1406, "Walking Wake", PokemonType.WATER, PokemonType.DRAGON, 99, 83, 91, 125, 83, 109)
        registerSpecies(1407, "Iron Leaves", PokemonType.GRASS, PokemonType.PSYCHIC, 90, 130, 88, 70, 108, 104)
        registerSpecies(1408, "Dipplin", PokemonType.GRASS, PokemonType.DRAGON, 80, 80, 110, 95, 80, 40)
        registerSpecies(1409, "Poltchageist", PokemonType.GRASS, PokemonType.GHOST, 40, 45, 45, 74, 54, 50)
        registerSpecies(1410, "Poltchageist", PokemonType.GRASS, PokemonType.GHOST, 40, 45, 45, 74, 54, 50)
        registerSpecies(1411, "Sinistcha", PokemonType.GRASS, PokemonType.GHOST, 71, 60, 106, 121, 80, 70)
        registerSpecies(1412, "Sinistcha", PokemonType.GRASS, PokemonType.GHOST, 71, 60, 106, 121, 80, 70)
        registerSpecies(1413, "Okidogi", PokemonType.POISON, PokemonType.FIGHTING, 88, 128, 115, 58, 86, 80)
        registerSpecies(1414, "Munkidori", PokemonType.POISON, PokemonType.PSYCHIC, 88, 75, 66, 130, 90, 106)
        registerSpecies(1415, "Fezandipiti", PokemonType.POISON, PokemonType.FAIRY, 88, 91, 82, 70, 125, 99)
        registerSpecies(1416, "Ogerpon", PokemonType.GRASS, null, 80, 120, 84, 60, 96, 110)
        registerSpecies(1417, "Ogerpon", PokemonType.GRASS, PokemonType.WATER, 80, 120, 84, 60, 96, 110)
        registerSpecies(1418, "Ogerpon", PokemonType.GRASS, PokemonType.FIRE, 80, 120, 84, 60, 96, 110)
        registerSpecies(1419, "Ogerpon", PokemonType.GRASS, PokemonType.ROCK, 80, 120, 84, 60, 96, 110)
        registerSpecies(1424, "Ursaluna", PokemonType.GROUND, PokemonType.NORMAL, 113, 70, 120, 135, 65, 52)
        registerSpecies(1425, "Archaludon", PokemonType.STEEL, PokemonType.DRAGON, 90, 105, 130, 125, 65, 85)
        registerSpecies(1426, "Hydrapple", PokemonType.GRASS, PokemonType.DRAGON, 106, 80, 110, 120, 80, 44)
        registerSpecies(1427, "Gouging Fire", PokemonType.FIRE, PokemonType.DRAGON, 105, 115, 121, 65, 93, 91)
        registerSpecies(1428, "Raging Bolt", PokemonType.ELECTRIC, PokemonType.DRAGON, 125, 73, 91, 137, 89, 75)
        registerSpecies(1429, "Iron Boulder", PokemonType.ROCK, PokemonType.PSYCHIC, 90, 120, 80, 68, 108, 124)
        registerSpecies(1430, "Iron Crown", PokemonType.STEEL, PokemonType.PSYCHIC, 90, 72, 100, 122, 108, 98)
        registerSpecies(1431, "Terapagos", PokemonType.NORMAL, null, 90, 65, 85, 65, 85, 60)
        registerSpecies(1432, "Terapagos", PokemonType.NORMAL, null, 95, 95, 110, 105, 110, 85)
        registerSpecies(1433, "Terapagos", PokemonType.NORMAL, null, 160, 105, 110, 130, 110, 85)
        registerSpecies(1434, "Pecharunt", PokemonType.POISON, PokemonType.GHOST, 88, 88, 160, 88, 88, 88)
        registerSpecies(1436, "Mothim", PokemonType.BUG, PokemonType.FLYING, 70, 94, 50, 94, 50, 66)
        registerSpecies(1437, "Mothim", PokemonType.BUG, PokemonType.FLYING, 70, 94, 50, 94, 50, 66)
        registerSpecies(1438, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(1439, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(1440, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(1441, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(1442, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(1443, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(1444, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(1445, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(1446, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(1447, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(1448, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(1449, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(1450, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(1451, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(1452, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(1453, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(1454, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(1455, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(1456, "Scatterbug", PokemonType.BUG, null, 38, 35, 40, 27, 25, 35)
        registerSpecies(1457, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
        registerSpecies(1458, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
        registerSpecies(1459, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
        registerSpecies(1460, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
        registerSpecies(1461, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
        registerSpecies(1462, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
    }

    private fun registerSpeciesChunk8() {
        registerSpecies(1463, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
        registerSpecies(1464, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
        registerSpecies(1465, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
        registerSpecies(1466, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
        registerSpecies(1467, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
        registerSpecies(1468, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
        registerSpecies(1469, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
        registerSpecies(1470, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
        registerSpecies(1471, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
        registerSpecies(1472, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
        registerSpecies(1473, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
        registerSpecies(1474, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
        registerSpecies(1475, "Spewpa", PokemonType.BUG, null, 45, 22, 60, 27, 30, 29)
        registerSpecies(1476, "Raticate-A", PokemonType.DARK, PokemonType.NORMAL, 75, 71, 70, 40, 80, 77)
        registerSpecies(1477, "Gumshoos", PokemonType.NORMAL, null, 88, 110, 60, 55, 60, 45)
        registerSpecies(1478, "Vikavolt", PokemonType.BUG, PokemonType.ELECTRIC, 77, 70, 90, 145, 75, 43)
        registerSpecies(1479, "Lurantis", PokemonType.GRASS, null, 70, 105, 90, 80, 90, 45)
        registerSpecies(1480, "Salazzle", PokemonType.POISON, PokemonType.FIRE, 68, 64, 60, 111, 60, 117)
        registerSpecies(1481, "Mimikyu", PokemonType.GHOST, PokemonType.FAIRY, 55, 90, 80, 50, 105, 96)
        registerSpecies(1482, "Kommo-o", PokemonType.DRAGON, PokemonType.FIGHTING, 75, 110, 125, 100, 105, 85)
        registerSpecies(1483, "Marowak-A", PokemonType.FIRE, PokemonType.GHOST, 60, 80, 110, 50, 80, 45)
        registerSpecies(1484, "Ribombee", PokemonType.BUG, PokemonType.FAIRY, 60, 55, 60, 95, 70, 124)
        registerSpecies(1485, "Araquanid", PokemonType.WATER, PokemonType.BUG, 68, 70, 92, 50, 132, 42)
        registerSpecies(1486, "Togedemaru", PokemonType.ELECTRIC, PokemonType.STEEL, 65, 98, 63, 40, 73, 96)
        registerSpecies(1487, "Pikachu", PokemonType.ELECTRIC, null, 45, 80, 50, 75, 60, 120)
        registerSpecies(1488, "Eevee", PokemonType.NORMAL, null, 65, 75, 70, 65, 85, 75)
        registerSpecies(1523, "Mimikyu", PokemonType.GHOST, PokemonType.FAIRY, 55, 90, 80, 50, 105, 96)
    }

    private fun registerAbilityChunk1() {
        abilityNameMap[1] = "STENCH"
        abilityNameMap[2] = "DRIZZLE"
        abilityNameMap[3] = "SPEED BOOST"
        abilityNameMap[4] = "BATTLE ARMOR"
        abilityNameMap[5] = "STURDY"
        abilityNameMap[6] = "DAMP"
        abilityNameMap[7] = "LIMBER"
        abilityNameMap[8] = "SAND VEIL"
        abilityNameMap[9] = "STATIC"
        abilityNameMap[10] = "VOLT ABSORB"
        abilityNameMap[11] = "WATER ABSORB"
        abilityNameMap[12] = "OBLIVIOUS"
        abilityNameMap[13] = "CLOUD NINE"
        abilityNameMap[14] = "COMPOUND EYES"
        abilityNameMap[15] = "INSOMNIA"
        abilityNameMap[16] = "COLOR CHANGE"
        abilityNameMap[17] = "IMMUNITY"
        abilityNameMap[18] = "FLASH FIRE"
        abilityNameMap[19] = "SHIELD DUST"
        abilityNameMap[20] = "OWN TEMPO"
        abilityNameMap[21] = "SUCTION CUPS"
        abilityNameMap[22] = "INTIMIDATE"
        abilityNameMap[23] = "SHADOW TAG"
        abilityNameMap[24] = "ROUGH SKIN"
        abilityNameMap[25] = "WONDER GUARD"
        abilityNameMap[26] = "LEVITATE"
        abilityNameMap[27] = "EFFECT SPORE"
        abilityNameMap[28] = "SYNCHRONIZE"
        abilityNameMap[29] = "CLEAR BODY"
        abilityNameMap[30] = "NATURAL CURE"
        abilityNameMap[31] = "LIGHTNING ROD"
        abilityNameMap[32] = "SERENE GRACE"
        abilityNameMap[33] = "SWIFT SWIM"
        abilityNameMap[34] = "CHLOROPHYLL"
        abilityNameMap[35] = "ILLUMINATE"
        abilityNameMap[36] = "TRACE"
        abilityNameMap[37] = "HUGE POWER"
        abilityNameMap[38] = "POISON POINT"
        abilityNameMap[39] = "INNER FOCUS"
        abilityNameMap[40] = "MAGMA ARMOR"
        abilityNameMap[41] = "WATER VEIL"
        abilityNameMap[42] = "MAGNET PULL"
        abilityNameMap[43] = "SOUNDPROOF"
        abilityNameMap[44] = "RAIN DISH"
        abilityNameMap[45] = "SAND STREAM"
        abilityNameMap[46] = "PRESSURE"
        abilityNameMap[47] = "THICK FAT"
        abilityNameMap[48] = "EARLY BIRD"
        abilityNameMap[49] = "FLAME BODY"
        abilityNameMap[50] = "RUN AWAY"
        abilityNameMap[51] = "KEEN EYE"
        abilityNameMap[52] = "HYPER CUTTER"
        abilityNameMap[53] = "PICKUP"
        abilityNameMap[54] = "TRUANT"
        abilityNameMap[55] = "HUSTLE"
        abilityNameMap[56] = "CUTE CHARM"
        abilityNameMap[57] = "PLUS"
        abilityNameMap[58] = "MINUS"
        abilityNameMap[59] = "FORECAST"
        abilityNameMap[60] = "STICKY HOLD"
        abilityNameMap[61] = "SHED SKIN"
        abilityNameMap[62] = "GUTS"
        abilityNameMap[63] = "MARVEL SCALE"
        abilityNameMap[64] = "LIQUID OOZE"
        abilityNameMap[65] = "OVERGROW"
        abilityNameMap[66] = "BLAZE"
        abilityNameMap[67] = "TORRENT"
        abilityNameMap[68] = "SWARM"
        abilityNameMap[69] = "ROCK HEAD"
        abilityNameMap[70] = "DROUGHT"
        abilityNameMap[71] = "ARENA TRAP"
        abilityNameMap[72] = "VITAL SPIRIT"
        abilityNameMap[73] = "WHITE SMOKE"
        abilityNameMap[74] = "PURE POWER"
        abilityNameMap[75] = "SHELL ARMOR"
        abilityNameMap[76] = "AIR LOCK"
        abilityNameMap[77] = "TANGLED FEET"
        abilityNameMap[78] = "MOTOR DRIVE"
        abilityNameMap[79] = "RIVALRY"
        abilityNameMap[80] = "STEADFAST"
        abilityNameMap[81] = "SNOW CLOAK"
        abilityNameMap[82] = "GLUTTONY"
        abilityNameMap[83] = "ANGER POINT"
        abilityNameMap[84] = "UNBURDEN"
        abilityNameMap[85] = "HEATPROOF"
        abilityNameMap[86] = "SIMPLE"
        abilityNameMap[87] = "DRY SKIN"
        abilityNameMap[88] = "DOWNLOAD"
        abilityNameMap[89] = "IRON FIST"
        abilityNameMap[90] = "POISON HEAL"
        abilityNameMap[91] = "ADAPTABILITY"
        abilityNameMap[92] = "SKILL LINK"
        abilityNameMap[93] = "HYDRATION"
        abilityNameMap[94] = "SOLAR POWER"
        abilityNameMap[95] = "QUICK FEET"
        abilityNameMap[96] = "NORMALIZE"
        abilityNameMap[97] = "SNIPER"
        abilityNameMap[98] = "MAGIC GUARD"
        abilityNameMap[99] = "NO GUARD"
        abilityNameMap[100] = "STALL"
        abilityNameMap[101] = "TECHNICIAN"
        abilityNameMap[102] = "LEAF GUARD"
        abilityNameMap[103] = "KLUTZ"
        abilityNameMap[104] = "MOLD BREAKER"
        abilityNameMap[105] = "SUPER LUCK"
        abilityNameMap[106] = "AFTERMATH"
        abilityNameMap[107] = "ANTICIPATION"
        abilityNameMap[108] = "FOREWARN"
        abilityNameMap[109] = "UNAWARE"
        abilityNameMap[110] = "TINTED LENS"
        abilityNameMap[111] = "FILTER"
        abilityNameMap[112] = "SLOW START"
        abilityNameMap[113] = "SCRAPPY"
        abilityNameMap[114] = "STORM DRAIN"
        abilityNameMap[115] = "ICE BODY"
        abilityNameMap[116] = "SOLID ROCK"
        abilityNameMap[117] = "SNOW WARNING"
        abilityNameMap[118] = "HONEY GATHER"
        abilityNameMap[119] = "FRISK"
        abilityNameMap[120] = "RECKLESS"
        abilityNameMap[121] = "MULTITYPE"
        abilityNameMap[122] = "FLOWER GIFT"
        abilityNameMap[123] = "BAD DREAMS"
        abilityNameMap[124] = "PICKPOCKET"
        abilityNameMap[125] = "SHEER FORCE"
        abilityNameMap[126] = "CONTRARY"
        abilityNameMap[127] = "UNNERVE"
        abilityNameMap[128] = "DEFIANT"
        abilityNameMap[129] = "DEFEATIST"
        abilityNameMap[130] = "CURSED BODY"
        abilityNameMap[131] = "HEALER"
        abilityNameMap[132] = "FRIEND GUARD"
        abilityNameMap[133] = "WEAK ARMOR"
        abilityNameMap[134] = "HEAVY METAL"
        abilityNameMap[135] = "LIGHT METAL"
        abilityNameMap[136] = "MULTISCALE"
        abilityNameMap[137] = "TOXIC BOOST"
        abilityNameMap[138] = "FLARE BOOST"
        abilityNameMap[139] = "HARVEST"
        abilityNameMap[140] = "TELEPATHY"
        abilityNameMap[141] = "MOODY"
        abilityNameMap[142] = "OVERCOAT"
        abilityNameMap[143] = "POISON TOUCH"
        abilityNameMap[144] = "REGENERATOR"
        abilityNameMap[145] = "BIG PECKS"
        abilityNameMap[146] = "SAND RUSH"
        abilityNameMap[147] = "WONDER SKIN"
        abilityNameMap[148] = "ANALYTIC"
        abilityNameMap[149] = "ILLUSION"
        abilityNameMap[150] = "IMPOSTER"
        abilityNameMap[151] = "INFILTRATOR"
        abilityNameMap[152] = "MUMMY"
        abilityNameMap[153] = "MOXIE"
        abilityNameMap[154] = "JUSTIFIED"
        abilityNameMap[155] = "RATTLED"
        abilityNameMap[156] = "MAGIC BOUNCE"
        abilityNameMap[157] = "SAP SIPPER"
        abilityNameMap[158] = "PRANKSTER"
        abilityNameMap[159] = "SAND FORCE"
        abilityNameMap[160] = "IRON BARBS"
        abilityNameMap[161] = "ZEN MODE"
        abilityNameMap[162] = "VICTORY STAR"
        abilityNameMap[163] = "TURBOBLAZE"
        abilityNameMap[164] = "TERAVOLT"
        abilityNameMap[165] = "AROMA VEIL"
        abilityNameMap[166] = "FLOWER VEIL"
        abilityNameMap[167] = "CHEEK POUCH"
        abilityNameMap[168] = "PROTEAN"
        abilityNameMap[169] = "FUR COAT"
        abilityNameMap[170] = "MAGICIAN"
        abilityNameMap[171] = "BULLETPROOF"
        abilityNameMap[172] = "COMPETITIVE"
        abilityNameMap[173] = "STRONG JAW"
        abilityNameMap[174] = "REFRIGERATE"
        abilityNameMap[175] = "SWEET VEIL"
        abilityNameMap[176] = "STANCE CHANGE"
        abilityNameMap[177] = "GALE WINGS"
        abilityNameMap[178] = "MEGA LAUNCHER"
        abilityNameMap[179] = "GRASS PELT"
        abilityNameMap[180] = "SYMBIOSIS"
        abilityNameMap[181] = "TOUGH CLAWS"
        abilityNameMap[182] = "PIXILATE"
        abilityNameMap[183] = "GOOEY"
        abilityNameMap[184] = "AERILATE"
        abilityNameMap[185] = "PARENTAL BOND"
        abilityNameMap[186] = "DARK AURA"
        abilityNameMap[187] = "FAIRY AURA"
        abilityNameMap[188] = "AURA BREAK"
        abilityNameMap[189] = "PRIMORDIAL SEA"
        abilityNameMap[190] = "DESOLATE LAND"
        abilityNameMap[191] = "DELTA STREAM"
        abilityNameMap[192] = "STAMINA"
        abilityNameMap[193] = "WIMP OUT"
        abilityNameMap[194] = "EMERGENCY EXIT"
        abilityNameMap[195] = "WATER COMPACTION"
        abilityNameMap[196] = "MERCILESS"
        abilityNameMap[197] = "SHIELDS DOWN"
        abilityNameMap[198] = "STAKEOUT"
        abilityNameMap[199] = "WATER BUBBLE"
        abilityNameMap[200] = "STEELWORKER"
    }

    private fun registerAbilityChunk2() {
        abilityNameMap[201] = "BERSERK"
        abilityNameMap[202] = "SLUSH RUSH"
        abilityNameMap[203] = "LONG REACH"
        abilityNameMap[204] = "LIQUID VOICE"
        abilityNameMap[205] = "TRIAGE"
        abilityNameMap[206] = "GALVANIZE"
        abilityNameMap[207] = "SURGE SURFER"
        abilityNameMap[208] = "SCHOOLING"
        abilityNameMap[209] = "DISGUISE"
        abilityNameMap[210] = "BATTLE BOND"
        abilityNameMap[211] = "POWER CONSTRUCT"
        abilityNameMap[212] = "CORROSION"
        abilityNameMap[213] = "COMATOSE"
        abilityNameMap[214] = "QUEENLY MAJESTY"
        abilityNameMap[215] = "INNARDS OUT"
        abilityNameMap[216] = "DANCER"
        abilityNameMap[217] = "BATTERY"
        abilityNameMap[218] = "FLUFFY"
        abilityNameMap[219] = "DAZZLING"
        abilityNameMap[220] = "SOUL-HEART"
        abilityNameMap[221] = "TANGLING HAIR"
        abilityNameMap[222] = "RECEIVER"
        abilityNameMap[223] = "POWER OF ALCHEMY"
        abilityNameMap[224] = "BEAST BOOST"
        abilityNameMap[225] = "RKS SYSTEM"
        abilityNameMap[226] = "ELECTRIC SURGE"
        abilityNameMap[227] = "PSYCHIC SURGE"
        abilityNameMap[228] = "MISTY SURGE"
        abilityNameMap[229] = "GRASSY SURGE"
        abilityNameMap[230] = "FULL METAL BODY"
        abilityNameMap[231] = "SHADOW SHIELD"
        abilityNameMap[232] = "PRISM ARMOR"
        abilityNameMap[233] = "NEUROFORCE"
        abilityNameMap[234] = "INTREPID SWORD"
        abilityNameMap[235] = "DAUNTLESS SHIELD"
        abilityNameMap[236] = "LIBERO"
        abilityNameMap[237] = "BALL FETCH"
        abilityNameMap[238] = "COTTON DOWN"
        abilityNameMap[239] = "PROPELLER TAIL"
        abilityNameMap[240] = "MIRROR ARMOR"
        abilityNameMap[241] = "GULP MISSILE"
        abilityNameMap[242] = "STALWART"
        abilityNameMap[243] = "STEAM ENGINE"
        abilityNameMap[244] = "PUNK ROCK"
        abilityNameMap[245] = "SAND SPIT"
        abilityNameMap[246] = "ICE SCALES"
        abilityNameMap[247] = "RIPEN"
        abilityNameMap[248] = "ICE FACE"
        abilityNameMap[249] = "POWER SPOT"
        abilityNameMap[250] = "MIMICRY"
        abilityNameMap[251] = "SCREEN CLEANER"
        abilityNameMap[252] = "STEELY SPIRIT"
        abilityNameMap[253] = "PERISH BODY"
        abilityNameMap[254] = "WANDERING SPIRIT"
        abilityNameMap[255] = "GORILLA TACTICS"
        abilityNameMap[256] = "NEUTRALIZING GAS"
        abilityNameMap[257] = "PASTEL VEIL"
        abilityNameMap[258] = "HUNGER SWITCH"
        abilityNameMap[259] = "QUICK DRAW"
        abilityNameMap[260] = "UNSEEN FIST"
        abilityNameMap[261] = "CURIOUS MEDICINE"
        abilityNameMap[262] = "TRANSISTOR"
        abilityNameMap[263] = "DRAGON'S MAW"
        abilityNameMap[264] = "CHILLING NEIGH"
        abilityNameMap[265] = "GRIM NEIGH"
        abilityNameMap[266] = "AS ONE"
        abilityNameMap[267] = "AS ONE"
        abilityNameMap[268] = "LINGERING AROMA"
        abilityNameMap[269] = "SEED SOWER"
        abilityNameMap[270] = "THERMAL EXCHANGE"
        abilityNameMap[271] = "ANGER SHELL"
        abilityNameMap[272] = "PURIFYING SALT"
        abilityNameMap[273] = "WELL-BAKED BODY"
        abilityNameMap[274] = "WIND RIDER"
        abilityNameMap[275] = "GUARD DOG"
        abilityNameMap[276] = "ROCKY PAYLOAD"
        abilityNameMap[277] = "WIND POWER"
        abilityNameMap[278] = "ZERO TO HERO"
        abilityNameMap[279] = "COMMANDER"
        abilityNameMap[280] = "ELECTROMORPHOSIS"
        abilityNameMap[281] = "PROTOSYNTHESIS"
        abilityNameMap[282] = "QUARK DRIVE"
        abilityNameMap[283] = "GOOD AS GOLD"
        abilityNameMap[284] = "VESSEL OF RUIN"
        abilityNameMap[285] = "SWORD OF RUIN"
        abilityNameMap[286] = "TABLETS OF RUIN"
        abilityNameMap[287] = "BEADS OF RUIN"
        abilityNameMap[288] = "ORICHALCUM PULSE"
        abilityNameMap[289] = "HADRON ENGINE"
        abilityNameMap[290] = "OPPORTUNIST"
        abilityNameMap[291] = "CUD CHEW"
        abilityNameMap[292] = "SHARPNESS"
        abilityNameMap[293] = "SUPREME OVERLORD"
        abilityNameMap[294] = "COSTAR"
        abilityNameMap[295] = "TOXIC DEBRIS"
        abilityNameMap[296] = "ARMOR TAIL"
        abilityNameMap[297] = "EARTH EATER"
        abilityNameMap[298] = "MYCELIUM MIGHT"
        abilityNameMap[299] = "HOSPITALITY"
        abilityNameMap[300] = "MIND'S EYE"
        abilityNameMap[301] = "EMBODY ASPECT"
        abilityNameMap[302] = "EMBODY ASPECT"
        abilityNameMap[303] = "EMBODY ASPECT"
        abilityNameMap[304] = "EMBODY ASPECT"
        abilityNameMap[305] = "TOXIC CHAIN"
        abilityNameMap[306] = "SUPERSWEET SYRUP"
        abilityNameMap[307] = "TERA SHIFT"
        abilityNameMap[308] = "TERA SHELL"
        abilityNameMap[309] = "TERAFORM ZERO"
        abilityNameMap[310] = "POISON PUPPETEER"
    }

    private fun registerSpeciesAbilityChunk1() {
        speciesAbilityMap[1] = arrayOf(65, null, 34)
        speciesAbilityMap[2] = arrayOf(65, null, 34)
        speciesAbilityMap[3] = arrayOf(65, null, 34)
        speciesAbilityMap[4] = arrayOf(66, null, 94)
        speciesAbilityMap[5] = arrayOf(66, null, 94)
        speciesAbilityMap[6] = arrayOf(66, null, 94)
        speciesAbilityMap[7] = arrayOf(67, null, 44)
        speciesAbilityMap[8] = arrayOf(67, null, 44)
        speciesAbilityMap[9] = arrayOf(67, null, 44)
        speciesAbilityMap[10] = arrayOf(19, null, 50)
        speciesAbilityMap[11] = arrayOf(61, null, null)
        speciesAbilityMap[12] = arrayOf(14, null, 110)
        speciesAbilityMap[13] = arrayOf(19, null, 50)
        speciesAbilityMap[14] = arrayOf(61, null, null)
        speciesAbilityMap[15] = arrayOf(68, null, 97)
        speciesAbilityMap[16] = arrayOf(51, 77, 145)
        speciesAbilityMap[17] = arrayOf(51, 77, 145)
        speciesAbilityMap[18] = arrayOf(51, 77, 145)
        speciesAbilityMap[19] = arrayOf(50, 62, 55)
        speciesAbilityMap[20] = arrayOf(50, 62, 55)
        speciesAbilityMap[21] = arrayOf(51, null, 97)
        speciesAbilityMap[22] = arrayOf(51, null, 97)
        speciesAbilityMap[23] = arrayOf(22, 61, 127)
        speciesAbilityMap[24] = arrayOf(22, 61, 127)
        speciesAbilityMap[25] = arrayOf(9, null, 31)
        speciesAbilityMap[26] = arrayOf(9, null, 31)
        speciesAbilityMap[27] = arrayOf(8, null, 146)
        speciesAbilityMap[28] = arrayOf(8, null, 146)
        speciesAbilityMap[29] = arrayOf(38, 79, 55)
        speciesAbilityMap[30] = arrayOf(38, 79, 55)
        speciesAbilityMap[31] = arrayOf(38, 79, 125)
        speciesAbilityMap[32] = arrayOf(38, 79, 55)
        speciesAbilityMap[33] = arrayOf(38, 79, 55)
        speciesAbilityMap[34] = arrayOf(38, 79, 125)
        speciesAbilityMap[35] = arrayOf(56, 98, 132)
        speciesAbilityMap[36] = arrayOf(56, 98, 109)
        speciesAbilityMap[37] = arrayOf(18, null, 70)
        speciesAbilityMap[38] = arrayOf(18, null, 70)
        speciesAbilityMap[39] = arrayOf(56, 172, 132)
        speciesAbilityMap[40] = arrayOf(56, 172, 119)
        speciesAbilityMap[41] = arrayOf(39, null, 151)
        speciesAbilityMap[42] = arrayOf(39, null, 151)
        speciesAbilityMap[43] = arrayOf(34, null, 50)
        speciesAbilityMap[44] = arrayOf(34, null, 1)
        speciesAbilityMap[45] = arrayOf(34, null, 27)
        speciesAbilityMap[46] = arrayOf(27, 87, 6)
        speciesAbilityMap[47] = arrayOf(27, 87, 6)
        speciesAbilityMap[48] = arrayOf(14, 110, 50)
        speciesAbilityMap[49] = arrayOf(19, 110, 147)
        speciesAbilityMap[50] = arrayOf(8, 71, 159)
        speciesAbilityMap[51] = arrayOf(8, 71, 159)
        speciesAbilityMap[52] = arrayOf(53, 101, 127)
        speciesAbilityMap[53] = arrayOf(7, 101, 127)
        speciesAbilityMap[54] = arrayOf(6, 13, 33)
        speciesAbilityMap[55] = arrayOf(6, 13, 33)
        speciesAbilityMap[56] = arrayOf(72, 83, 128)
        speciesAbilityMap[57] = arrayOf(72, 83, 128)
        speciesAbilityMap[58] = arrayOf(22, 18, 154)
        speciesAbilityMap[59] = arrayOf(22, 18, 154)
        speciesAbilityMap[60] = arrayOf(11, 6, 33)
        speciesAbilityMap[61] = arrayOf(11, 6, 33)
        speciesAbilityMap[62] = arrayOf(11, 6, 33)
        speciesAbilityMap[63] = arrayOf(28, 39, 98)
        speciesAbilityMap[64] = arrayOf(28, 39, 98)
        speciesAbilityMap[65] = arrayOf(28, 39, 98)
        speciesAbilityMap[66] = arrayOf(62, 99, 80)
        speciesAbilityMap[67] = arrayOf(62, 99, 80)
        speciesAbilityMap[68] = arrayOf(62, 99, 80)
        speciesAbilityMap[69] = arrayOf(34, null, 82)
        speciesAbilityMap[70] = arrayOf(34, null, 82)
        speciesAbilityMap[71] = arrayOf(34, null, 82)
        speciesAbilityMap[72] = arrayOf(29, 64, 44)
        speciesAbilityMap[73] = arrayOf(29, 64, 44)
        speciesAbilityMap[74] = arrayOf(69, 5, 8)
        speciesAbilityMap[75] = arrayOf(69, 5, 8)
        speciesAbilityMap[76] = arrayOf(69, 5, 8)
        speciesAbilityMap[77] = arrayOf(50, 18, 49)
        speciesAbilityMap[78] = arrayOf(50, 18, 49)
        speciesAbilityMap[79] = arrayOf(12, 20, 144)
        speciesAbilityMap[80] = arrayOf(12, 20, 144)
        speciesAbilityMap[81] = arrayOf(42, 5, 148)
        speciesAbilityMap[82] = arrayOf(42, 5, 148)
        speciesAbilityMap[83] = arrayOf(51, 39, 128)
        speciesAbilityMap[84] = arrayOf(50, 48, 77)
        speciesAbilityMap[85] = arrayOf(50, 48, 77)
        speciesAbilityMap[86] = arrayOf(47, 93, 115)
        speciesAbilityMap[87] = arrayOf(47, 93, 115)
        speciesAbilityMap[88] = arrayOf(1, 60, 143)
        speciesAbilityMap[89] = arrayOf(1, 60, 143)
        speciesAbilityMap[90] = arrayOf(75, 92, 142)
        speciesAbilityMap[91] = arrayOf(75, 92, 142)
        speciesAbilityMap[92] = arrayOf(26, null, null)
        speciesAbilityMap[93] = arrayOf(26, null, null)
        speciesAbilityMap[94] = arrayOf(26, 130, null)
        speciesAbilityMap[95] = arrayOf(69, 5, 133)
        speciesAbilityMap[96] = arrayOf(15, 108, 39)
        speciesAbilityMap[97] = arrayOf(15, 108, 39)
        speciesAbilityMap[98] = arrayOf(52, 75, 125)
        speciesAbilityMap[99] = arrayOf(52, 75, 125)
        speciesAbilityMap[100] = arrayOf(43, 9, 106)
        speciesAbilityMap[101] = arrayOf(43, 9, 106)
        speciesAbilityMap[102] = arrayOf(34, null, 139)
        speciesAbilityMap[103] = arrayOf(34, null, 139)
        speciesAbilityMap[104] = arrayOf(69, 31, 4)
        speciesAbilityMap[105] = arrayOf(69, 31, 4)
        speciesAbilityMap[106] = arrayOf(7, 120, 84)
        speciesAbilityMap[107] = arrayOf(51, 89, 39)
        speciesAbilityMap[108] = arrayOf(20, 12, 13)
        speciesAbilityMap[109] = arrayOf(26, 256, 1)
        speciesAbilityMap[110] = arrayOf(26, 256, 1)
        speciesAbilityMap[111] = arrayOf(31, 69, 120)
        speciesAbilityMap[112] = arrayOf(31, 69, 120)
        speciesAbilityMap[113] = arrayOf(30, 32, 131)
        speciesAbilityMap[114] = arrayOf(34, 102, 144)
        speciesAbilityMap[115] = arrayOf(48, 113, 39)
        speciesAbilityMap[116] = arrayOf(33, 97, 6)
        speciesAbilityMap[117] = arrayOf(38, 97, 6)
        speciesAbilityMap[118] = arrayOf(33, 41, 31)
        speciesAbilityMap[119] = arrayOf(33, 41, 31)
        speciesAbilityMap[120] = arrayOf(35, 30, 148)
        speciesAbilityMap[121] = arrayOf(35, 30, 148)
        speciesAbilityMap[122] = arrayOf(43, 111, 101)
        speciesAbilityMap[123] = arrayOf(68, 101, 80)
        speciesAbilityMap[124] = arrayOf(12, 108, 87)
        speciesAbilityMap[125] = arrayOf(9, null, 72)
        speciesAbilityMap[126] = arrayOf(49, null, 72)
        speciesAbilityMap[127] = arrayOf(52, 104, 153)
        speciesAbilityMap[128] = arrayOf(22, 83, 125)
        speciesAbilityMap[129] = arrayOf(33, null, 155)
        speciesAbilityMap[130] = arrayOf(22, null, 153)
        speciesAbilityMap[131] = arrayOf(11, 75, 93)
        speciesAbilityMap[132] = arrayOf(7, null, 150)
        speciesAbilityMap[133] = arrayOf(50, 91, 107)
        speciesAbilityMap[134] = arrayOf(11, 11, 93)
        speciesAbilityMap[135] = arrayOf(10, 10, 95)
        speciesAbilityMap[136] = arrayOf(18, 18, 62)
        speciesAbilityMap[137] = arrayOf(36, 88, 148)
        speciesAbilityMap[138] = arrayOf(33, 75, 133)
        speciesAbilityMap[139] = arrayOf(33, 75, 133)
        speciesAbilityMap[140] = arrayOf(33, 4, 133)
        speciesAbilityMap[141] = arrayOf(33, 4, 133)
        speciesAbilityMap[142] = arrayOf(69, 46, 127)
        speciesAbilityMap[143] = arrayOf(17, 47, 82)
        speciesAbilityMap[144] = arrayOf(46, null, 81)
        speciesAbilityMap[145] = arrayOf(46, null, 9)
        speciesAbilityMap[146] = arrayOf(46, null, 49)
        speciesAbilityMap[147] = arrayOf(61, null, 63)
        speciesAbilityMap[148] = arrayOf(61, null, 63)
        speciesAbilityMap[149] = arrayOf(39, null, 136)
        speciesAbilityMap[150] = arrayOf(46, null, 127)
        speciesAbilityMap[151] = arrayOf(28, null, null)
        speciesAbilityMap[152] = arrayOf(65, null, 102)
        speciesAbilityMap[153] = arrayOf(65, null, 102)
        speciesAbilityMap[154] = arrayOf(65, null, 102)
        speciesAbilityMap[155] = arrayOf(66, null, 18)
        speciesAbilityMap[156] = arrayOf(66, null, 18)
        speciesAbilityMap[157] = arrayOf(66, null, 18)
        speciesAbilityMap[158] = arrayOf(67, null, 125)
        speciesAbilityMap[159] = arrayOf(67, null, 125)
        speciesAbilityMap[160] = arrayOf(67, null, 125)
        speciesAbilityMap[161] = arrayOf(50, 51, 119)
        speciesAbilityMap[162] = arrayOf(50, 51, 119)
        speciesAbilityMap[163] = arrayOf(15, 51, 110)
        speciesAbilityMap[164] = arrayOf(15, 51, 110)
        speciesAbilityMap[165] = arrayOf(68, 48, 155)
        speciesAbilityMap[166] = arrayOf(68, 48, 89)
        speciesAbilityMap[167] = arrayOf(68, 15, 97)
        speciesAbilityMap[168] = arrayOf(68, 15, 97)
        speciesAbilityMap[169] = arrayOf(39, null, 151)
        speciesAbilityMap[170] = arrayOf(10, 35, 11)
        speciesAbilityMap[171] = arrayOf(10, 35, 11)
        speciesAbilityMap[172] = arrayOf(9, null, 31)
        speciesAbilityMap[173] = arrayOf(56, 98, 132)
        speciesAbilityMap[174] = arrayOf(56, 172, 132)
        speciesAbilityMap[175] = arrayOf(55, 32, 105)
        speciesAbilityMap[176] = arrayOf(55, 32, 105)
        speciesAbilityMap[177] = arrayOf(28, 48, 156)
        speciesAbilityMap[178] = arrayOf(28, 48, 156)
        speciesAbilityMap[179] = arrayOf(9, null, 57)
        speciesAbilityMap[180] = arrayOf(9, null, 57)
        speciesAbilityMap[181] = arrayOf(9, null, 57)
        speciesAbilityMap[182] = arrayOf(34, null, 131)
        speciesAbilityMap[183] = arrayOf(47, 37, 157)
        speciesAbilityMap[184] = arrayOf(47, 37, 157)
        speciesAbilityMap[185] = arrayOf(5, 69, 155)
        speciesAbilityMap[186] = arrayOf(11, 6, 2)
        speciesAbilityMap[187] = arrayOf(34, 102, 151)
        speciesAbilityMap[188] = arrayOf(34, 102, 151)
        speciesAbilityMap[189] = arrayOf(34, 102, 151)
        speciesAbilityMap[190] = arrayOf(50, 53, 92)
        speciesAbilityMap[191] = arrayOf(34, 94, 48)
        speciesAbilityMap[192] = arrayOf(34, 94, 48)
        speciesAbilityMap[193] = arrayOf(3, 14, 119)
        speciesAbilityMap[194] = arrayOf(6, 11, 109)
        speciesAbilityMap[195] = arrayOf(6, 11, 109)
        speciesAbilityMap[196] = arrayOf(28, 28, 156)
        speciesAbilityMap[197] = arrayOf(28, 28, 39)
        speciesAbilityMap[198] = arrayOf(15, 105, 158)
        speciesAbilityMap[199] = arrayOf(12, 20, 144)
        speciesAbilityMap[200] = arrayOf(26, null, null)
        speciesAbilityMap[201] = arrayOf(26, null, null)
        speciesAbilityMap[202] = arrayOf(23, null, 140)
        speciesAbilityMap[203] = arrayOf(39, 48, 157)
        speciesAbilityMap[204] = arrayOf(5, null, 142)
        speciesAbilityMap[205] = arrayOf(5, null, 142)
        speciesAbilityMap[206] = arrayOf(32, 50, 155)
        speciesAbilityMap[207] = arrayOf(52, 8, 17)
        speciesAbilityMap[208] = arrayOf(69, 5, 125)
        speciesAbilityMap[209] = arrayOf(22, 50, 155)
        speciesAbilityMap[210] = arrayOf(22, 95, 155)
        speciesAbilityMap[211] = arrayOf(38, 33, 22)
        speciesAbilityMap[212] = arrayOf(68, 101, 135)
        speciesAbilityMap[213] = arrayOf(5, 82, 126)
        speciesAbilityMap[214] = arrayOf(68, 62, 153)
        speciesAbilityMap[215] = arrayOf(39, 51, 124)
        speciesAbilityMap[216] = arrayOf(53, 95, 118)
        speciesAbilityMap[217] = arrayOf(62, 95, 127)
        speciesAbilityMap[218] = arrayOf(40, 49, 133)
        speciesAbilityMap[219] = arrayOf(40, 49, 133)
        speciesAbilityMap[220] = arrayOf(12, 81, 47)
        speciesAbilityMap[221] = arrayOf(12, 81, 47)
        speciesAbilityMap[222] = arrayOf(55, 30, 144)
        speciesAbilityMap[223] = arrayOf(55, 97, 141)
        speciesAbilityMap[224] = arrayOf(21, 97, 141)
        speciesAbilityMap[225] = arrayOf(72, 55, 15)
        speciesAbilityMap[226] = arrayOf(33, 11, 41)
        speciesAbilityMap[227] = arrayOf(51, 5, 133)
        speciesAbilityMap[228] = arrayOf(48, 18, 127)
        speciesAbilityMap[229] = arrayOf(48, 18, 127)
        speciesAbilityMap[230] = arrayOf(33, 97, 6)
        speciesAbilityMap[231] = arrayOf(53, null, 8)
        speciesAbilityMap[232] = arrayOf(5, null, 8)
        speciesAbilityMap[233] = arrayOf(36, 88, 148)
        speciesAbilityMap[234] = arrayOf(22, 119, 157)
        speciesAbilityMap[235] = arrayOf(20, 101, 141)
        speciesAbilityMap[236] = arrayOf(62, 80, 72)
        speciesAbilityMap[237] = arrayOf(22, 101, 80)
        speciesAbilityMap[238] = arrayOf(12, 108, 93)
        speciesAbilityMap[239] = arrayOf(9, null, 72)
        speciesAbilityMap[240] = arrayOf(49, null, 72)
        speciesAbilityMap[241] = arrayOf(47, 113, 157)
        speciesAbilityMap[242] = arrayOf(30, 32, 131)
        speciesAbilityMap[243] = arrayOf(46, null, 39)
        speciesAbilityMap[244] = arrayOf(46, null, 39)
        speciesAbilityMap[245] = arrayOf(46, null, 39)
        speciesAbilityMap[246] = arrayOf(62, null, 8)
        speciesAbilityMap[247] = arrayOf(61, null, null)
        speciesAbilityMap[248] = arrayOf(45, null, 127)
        speciesAbilityMap[249] = arrayOf(46, null, 136)
        speciesAbilityMap[250] = arrayOf(46, null, 144)
        speciesAbilityMap[251] = arrayOf(30, null, null)
        speciesAbilityMap[252] = arrayOf(65, null, 84)
        speciesAbilityMap[253] = arrayOf(65, null, 84)
        speciesAbilityMap[254] = arrayOf(65, null, 84)
        speciesAbilityMap[255] = arrayOf(66, null, 3)
        speciesAbilityMap[256] = arrayOf(66, null, 3)
        speciesAbilityMap[257] = arrayOf(66, null, 3)
        speciesAbilityMap[258] = arrayOf(67, null, 6)
        speciesAbilityMap[259] = arrayOf(67, null, 6)
        speciesAbilityMap[260] = arrayOf(67, null, 6)
        speciesAbilityMap[261] = arrayOf(50, 95, 155)
        speciesAbilityMap[262] = arrayOf(22, 95, 153)
        speciesAbilityMap[263] = arrayOf(53, 82, 95)
        speciesAbilityMap[264] = arrayOf(53, 82, 95)
        speciesAbilityMap[265] = arrayOf(19, null, 50)
        speciesAbilityMap[266] = arrayOf(61, null, null)
        speciesAbilityMap[267] = arrayOf(68, null, 79)
        speciesAbilityMap[268] = arrayOf(61, null, null)
        speciesAbilityMap[269] = arrayOf(19, null, 14)
        speciesAbilityMap[270] = arrayOf(33, 44, 20)
        speciesAbilityMap[271] = arrayOf(33, 44, 20)
        speciesAbilityMap[272] = arrayOf(33, 44, 20)
        speciesAbilityMap[273] = arrayOf(34, 48, 124)
        speciesAbilityMap[274] = arrayOf(34, 48, 124)
        speciesAbilityMap[275] = arrayOf(34, 274, 124)
        speciesAbilityMap[276] = arrayOf(62, null, 113)
        speciesAbilityMap[277] = arrayOf(62, null, 113)
        speciesAbilityMap[278] = arrayOf(51, 93, 44)
        speciesAbilityMap[279] = arrayOf(51, 2, 44)
        speciesAbilityMap[280] = arrayOf(28, 36, 140)
        speciesAbilityMap[281] = arrayOf(28, 36, 140)
        speciesAbilityMap[282] = arrayOf(28, 36, 140)
        speciesAbilityMap[283] = arrayOf(33, null, 44)
        speciesAbilityMap[284] = arrayOf(22, null, 127)
        speciesAbilityMap[285] = arrayOf(27, 90, 95)
        speciesAbilityMap[286] = arrayOf(27, 90, 101)
        speciesAbilityMap[287] = arrayOf(54, null, null)
        speciesAbilityMap[288] = arrayOf(72, null, null)
        speciesAbilityMap[289] = arrayOf(54, null, null)
        speciesAbilityMap[290] = arrayOf(14, null, 50)
        speciesAbilityMap[291] = arrayOf(3, null, 151)
        speciesAbilityMap[292] = arrayOf(25, null, null)
        speciesAbilityMap[293] = arrayOf(43, null, 155)
        speciesAbilityMap[294] = arrayOf(43, null, 113)
        speciesAbilityMap[295] = arrayOf(43, null, 113)
        speciesAbilityMap[296] = arrayOf(47, 62, 125)
        speciesAbilityMap[297] = arrayOf(47, 62, 125)
        speciesAbilityMap[298] = arrayOf(47, 37, 157)
        speciesAbilityMap[299] = arrayOf(5, 42, 159)
        speciesAbilityMap[300] = arrayOf(56, 96, 147)
        speciesAbilityMap[301] = arrayOf(56, 96, 147)
        speciesAbilityMap[302] = arrayOf(51, 100, 158)
        speciesAbilityMap[303] = arrayOf(52, 22, 125)
        speciesAbilityMap[304] = arrayOf(5, 69, 134)
        speciesAbilityMap[305] = arrayOf(5, 69, 134)
        speciesAbilityMap[306] = arrayOf(5, 69, 134)
        speciesAbilityMap[307] = arrayOf(74, null, 140)
        speciesAbilityMap[308] = arrayOf(74, null, 140)
        speciesAbilityMap[309] = arrayOf(9, 31, 58)
        speciesAbilityMap[310] = arrayOf(9, 31, 58)
        speciesAbilityMap[311] = arrayOf(57, null, 31)
        speciesAbilityMap[312] = arrayOf(58, null, 10)
        speciesAbilityMap[313] = arrayOf(35, 68, 158)
        speciesAbilityMap[314] = arrayOf(12, 110, 158)
        speciesAbilityMap[315] = arrayOf(30, 38, 102)
        speciesAbilityMap[316] = arrayOf(64, 60, 82)
        speciesAbilityMap[317] = arrayOf(64, 60, 82)
        speciesAbilityMap[318] = arrayOf(24, null, 3)
        speciesAbilityMap[319] = arrayOf(24, null, 3)
        speciesAbilityMap[320] = arrayOf(41, 12, 46)
        speciesAbilityMap[321] = arrayOf(41, 12, 46)
        speciesAbilityMap[322] = arrayOf(12, 86, 20)
        speciesAbilityMap[323] = arrayOf(40, 116, 83)
        speciesAbilityMap[324] = arrayOf(73, 70, 75)
        speciesAbilityMap[325] = arrayOf(47, 20, 82)
        speciesAbilityMap[326] = arrayOf(47, 20, 82)
        speciesAbilityMap[327] = arrayOf(20, 77, 126)
        speciesAbilityMap[328] = arrayOf(52, 71, 125)
        speciesAbilityMap[329] = arrayOf(26, 26, 26)
        speciesAbilityMap[330] = arrayOf(26, 26, 26)
        speciesAbilityMap[331] = arrayOf(8, null, 11)
        speciesAbilityMap[332] = arrayOf(8, null, 11)
        speciesAbilityMap[333] = arrayOf(30, null, 13)
        speciesAbilityMap[334] = arrayOf(30, null, 13)
        speciesAbilityMap[335] = arrayOf(17, null, 137)
        speciesAbilityMap[336] = arrayOf(61, null, 151)
        speciesAbilityMap[337] = arrayOf(26, null, null)
        speciesAbilityMap[338] = arrayOf(26, null, null)
        speciesAbilityMap[339] = arrayOf(12, 107, 93)
        speciesAbilityMap[340] = arrayOf(12, 107, 93)
        speciesAbilityMap[341] = arrayOf(52, 75, 91)
        speciesAbilityMap[342] = arrayOf(52, 75, 91)
        speciesAbilityMap[343] = arrayOf(26, null, null)
        speciesAbilityMap[344] = arrayOf(26, null, null)
        speciesAbilityMap[345] = arrayOf(21, null, 114)
        speciesAbilityMap[346] = arrayOf(21, null, 114)
        speciesAbilityMap[347] = arrayOf(4, null, 33)
        speciesAbilityMap[348] = arrayOf(4, null, 33)
        speciesAbilityMap[349] = arrayOf(33, 12, 91)
        speciesAbilityMap[350] = arrayOf(63, 172, 56)
        speciesAbilityMap[351] = arrayOf(59, null, null)
        speciesAbilityMap[352] = arrayOf(16, null, 168)
        speciesAbilityMap[353] = arrayOf(15, 119, 130)
        speciesAbilityMap[354] = arrayOf(15, 119, 130)
        speciesAbilityMap[355] = arrayOf(26, null, 119)
        speciesAbilityMap[356] = arrayOf(46, null, 119)
        speciesAbilityMap[357] = arrayOf(34, 94, 139)
        speciesAbilityMap[358] = arrayOf(26, null, null)
        speciesAbilityMap[359] = arrayOf(46, 105, 154)
        speciesAbilityMap[360] = arrayOf(23, null, 140)
        speciesAbilityMap[361] = arrayOf(39, 115, 141)
        speciesAbilityMap[362] = arrayOf(39, 115, 141)
        speciesAbilityMap[363] = arrayOf(47, 115, 12)
        speciesAbilityMap[364] = arrayOf(47, 115, 12)
        speciesAbilityMap[365] = arrayOf(47, 115, 12)
        speciesAbilityMap[366] = arrayOf(75, null, 155)
        speciesAbilityMap[367] = arrayOf(33, null, 41)
        speciesAbilityMap[368] = arrayOf(33, null, 93)
        speciesAbilityMap[369] = arrayOf(33, 69, 5)
        speciesAbilityMap[370] = arrayOf(33, null, 93)
        speciesAbilityMap[371] = arrayOf(69, null, 125)
        speciesAbilityMap[372] = arrayOf(69, null, 142)
        speciesAbilityMap[373] = arrayOf(22, null, 153)
        speciesAbilityMap[374] = arrayOf(29, null, 135)
        speciesAbilityMap[375] = arrayOf(29, null, 135)
        speciesAbilityMap[376] = arrayOf(29, null, 135)
        speciesAbilityMap[377] = arrayOf(29, null, 5)
        speciesAbilityMap[378] = arrayOf(29, null, 115)
        speciesAbilityMap[379] = arrayOf(29, null, 135)
        speciesAbilityMap[380] = arrayOf(26, null, null)
        speciesAbilityMap[381] = arrayOf(26, null, null)
        speciesAbilityMap[382] = arrayOf(2, null, null)
        speciesAbilityMap[383] = arrayOf(70, null, null)
        speciesAbilityMap[384] = arrayOf(76, null, null)
        speciesAbilityMap[385] = arrayOf(32, null, null)
        speciesAbilityMap[386] = arrayOf(46, null, null)
        speciesAbilityMap[387] = arrayOf(65, null, 75)
        speciesAbilityMap[388] = arrayOf(65, null, 75)
        speciesAbilityMap[389] = arrayOf(65, null, 75)
        speciesAbilityMap[390] = arrayOf(66, null, 89)
        speciesAbilityMap[391] = arrayOf(66, null, 89)
        speciesAbilityMap[392] = arrayOf(66, null, 89)
        speciesAbilityMap[393] = arrayOf(67, null, 172)
        speciesAbilityMap[394] = arrayOf(67, null, 172)
        speciesAbilityMap[395] = arrayOf(67, null, 172)
        speciesAbilityMap[396] = arrayOf(51, null, 120)
        speciesAbilityMap[397] = arrayOf(22, null, 120)
        speciesAbilityMap[398] = arrayOf(22, null, 120)
        speciesAbilityMap[399] = arrayOf(86, 109, 141)
        speciesAbilityMap[400] = arrayOf(86, 109, 141)
    }

    private fun registerSpeciesAbilityChunk2() {
        speciesAbilityMap[401] = arrayOf(61, null, 50)
        speciesAbilityMap[402] = arrayOf(68, null, 101)
        speciesAbilityMap[403] = arrayOf(79, 22, 62)
        speciesAbilityMap[404] = arrayOf(79, 22, 62)
        speciesAbilityMap[405] = arrayOf(79, 22, 62)
        speciesAbilityMap[406] = arrayOf(30, 38, 102)
        speciesAbilityMap[407] = arrayOf(30, 38, 101)
        speciesAbilityMap[408] = arrayOf(104, null, 125)
        speciesAbilityMap[409] = arrayOf(104, null, 125)
        speciesAbilityMap[410] = arrayOf(5, null, 43)
        speciesAbilityMap[411] = arrayOf(5, null, 43)
        speciesAbilityMap[412] = arrayOf(61, null, 142)
        speciesAbilityMap[413] = arrayOf(107, null, 142)
        speciesAbilityMap[414] = arrayOf(68, null, 110)
        speciesAbilityMap[415] = arrayOf(118, null, 55)
        speciesAbilityMap[416] = arrayOf(46, null, 127)
        speciesAbilityMap[417] = arrayOf(50, 53, 10)
        speciesAbilityMap[418] = arrayOf(33, null, 41)
        speciesAbilityMap[419] = arrayOf(33, null, 41)
        speciesAbilityMap[420] = arrayOf(34, null, null)
        speciesAbilityMap[421] = arrayOf(122, null, null)
        speciesAbilityMap[422] = arrayOf(60, 114, 159)
        speciesAbilityMap[423] = arrayOf(60, 114, 159)
        speciesAbilityMap[424] = arrayOf(101, 53, 92)
        speciesAbilityMap[425] = arrayOf(106, 84, 138)
        speciesAbilityMap[426] = arrayOf(106, 84, 138)
        speciesAbilityMap[427] = arrayOf(50, 103, 7)
        speciesAbilityMap[428] = arrayOf(56, 103, 7)
        speciesAbilityMap[429] = arrayOf(26, null, null)
        speciesAbilityMap[430] = arrayOf(15, 105, 153)
        speciesAbilityMap[431] = arrayOf(7, 20, 51)
        speciesAbilityMap[432] = arrayOf(47, 20, 128)
        speciesAbilityMap[433] = arrayOf(26, null, null)
        speciesAbilityMap[434] = arrayOf(1, 106, 51)
        speciesAbilityMap[435] = arrayOf(1, 106, 51)
        speciesAbilityMap[436] = arrayOf(26, 85, 134)
        speciesAbilityMap[437] = arrayOf(26, 85, 134)
        speciesAbilityMap[438] = arrayOf(5, 69, 155)
        speciesAbilityMap[439] = arrayOf(43, 111, 101)
        speciesAbilityMap[440] = arrayOf(30, 32, 132)
        speciesAbilityMap[441] = arrayOf(51, 77, 145)
        speciesAbilityMap[442] = arrayOf(46, null, 151)
        speciesAbilityMap[443] = arrayOf(8, null, 24)
        speciesAbilityMap[444] = arrayOf(8, null, 24)
        speciesAbilityMap[445] = arrayOf(8, null, 24)
        speciesAbilityMap[446] = arrayOf(53, 47, 82)
        speciesAbilityMap[447] = arrayOf(80, 39, 158)
        speciesAbilityMap[448] = arrayOf(80, 39, 154)
        speciesAbilityMap[449] = arrayOf(45, null, 159)
        speciesAbilityMap[450] = arrayOf(45, null, 159)
        speciesAbilityMap[451] = arrayOf(4, 97, 51)
        speciesAbilityMap[452] = arrayOf(4, 97, 51)
        speciesAbilityMap[453] = arrayOf(107, 87, 143)
        speciesAbilityMap[454] = arrayOf(107, 87, 143)
        speciesAbilityMap[455] = arrayOf(26, null, null)
        speciesAbilityMap[456] = arrayOf(33, 114, 41)
        speciesAbilityMap[457] = arrayOf(33, 114, 41)
        speciesAbilityMap[458] = arrayOf(33, 11, 41)
        speciesAbilityMap[459] = arrayOf(117, null, 43)
        speciesAbilityMap[460] = arrayOf(117, null, 43)
        speciesAbilityMap[461] = arrayOf(46, null, 124)
        speciesAbilityMap[462] = arrayOf(42, 5, 148)
        speciesAbilityMap[463] = arrayOf(20, 12, 13)
        speciesAbilityMap[464] = arrayOf(31, 116, 120)
        speciesAbilityMap[465] = arrayOf(34, 102, 144)
        speciesAbilityMap[466] = arrayOf(78, null, 72)
        speciesAbilityMap[467] = arrayOf(49, null, 72)
        speciesAbilityMap[468] = arrayOf(55, 32, 105)
        speciesAbilityMap[469] = arrayOf(3, 110, 119)
        speciesAbilityMap[470] = arrayOf(102, 102, 34)
        speciesAbilityMap[471] = arrayOf(81, 81, 115)
        speciesAbilityMap[472] = arrayOf(52, 8, 90)
        speciesAbilityMap[473] = arrayOf(12, 81, 47)
        speciesAbilityMap[474] = arrayOf(91, 88, 148)
        speciesAbilityMap[475] = arrayOf(80, 292, 154)
        speciesAbilityMap[476] = arrayOf(5, 42, 159)
        speciesAbilityMap[477] = arrayOf(46, null, 119)
        speciesAbilityMap[478] = arrayOf(81, null, 130)
        speciesAbilityMap[479] = arrayOf(26, null, null)
        speciesAbilityMap[480] = arrayOf(26, null, null)
        speciesAbilityMap[481] = arrayOf(26, null, null)
        speciesAbilityMap[482] = arrayOf(26, null, null)
        speciesAbilityMap[483] = arrayOf(46, null, 140)
        speciesAbilityMap[484] = arrayOf(46, null, 140)
        speciesAbilityMap[485] = arrayOf(18, null, 49)
        speciesAbilityMap[486] = arrayOf(112, null, null)
        speciesAbilityMap[487] = arrayOf(46, null, 140)
        speciesAbilityMap[488] = arrayOf(26, null, null)
        speciesAbilityMap[489] = arrayOf(93, null, null)
        speciesAbilityMap[490] = arrayOf(93, null, null)
        speciesAbilityMap[491] = arrayOf(123, null, null)
        speciesAbilityMap[492] = arrayOf(30, null, null)
        speciesAbilityMap[493] = arrayOf(121, null, null)
        speciesAbilityMap[494] = arrayOf(162, null, null)
        speciesAbilityMap[495] = arrayOf(65, null, 126)
        speciesAbilityMap[496] = arrayOf(65, null, 126)
        speciesAbilityMap[497] = arrayOf(65, null, 126)
        speciesAbilityMap[498] = arrayOf(66, null, 47)
        speciesAbilityMap[499] = arrayOf(66, null, 47)
        speciesAbilityMap[500] = arrayOf(66, null, 120)
        speciesAbilityMap[501] = arrayOf(67, null, 75)
        speciesAbilityMap[502] = arrayOf(67, null, 75)
        speciesAbilityMap[503] = arrayOf(67, null, 75)
        speciesAbilityMap[504] = arrayOf(50, 51, 148)
        speciesAbilityMap[505] = arrayOf(35, 51, 148)
        speciesAbilityMap[506] = arrayOf(72, 53, 50)
        speciesAbilityMap[507] = arrayOf(22, 146, 113)
        speciesAbilityMap[508] = arrayOf(22, 146, 113)
        speciesAbilityMap[509] = arrayOf(7, 84, 158)
        speciesAbilityMap[510] = arrayOf(7, 84, 158)
        speciesAbilityMap[511] = arrayOf(82, null, 65)
        speciesAbilityMap[512] = arrayOf(82, null, 65)
        speciesAbilityMap[513] = arrayOf(82, null, 66)
        speciesAbilityMap[514] = arrayOf(82, null, 66)
        speciesAbilityMap[515] = arrayOf(82, null, 67)
        speciesAbilityMap[516] = arrayOf(82, null, 67)
        speciesAbilityMap[517] = arrayOf(108, 28, 140)
        speciesAbilityMap[518] = arrayOf(108, 28, 140)
        speciesAbilityMap[519] = arrayOf(145, 105, 79)
        speciesAbilityMap[520] = arrayOf(145, 105, 79)
        speciesAbilityMap[521] = arrayOf(145, 105, 79)
        speciesAbilityMap[522] = arrayOf(31, 78, 157)
        speciesAbilityMap[523] = arrayOf(31, 78, 157)
        speciesAbilityMap[524] = arrayOf(5, 133, 159)
        speciesAbilityMap[525] = arrayOf(5, 133, 159)
        speciesAbilityMap[526] = arrayOf(5, 45, 159)
        speciesAbilityMap[527] = arrayOf(109, 103, 86)
        speciesAbilityMap[528] = arrayOf(109, 103, 86)
        speciesAbilityMap[529] = arrayOf(146, 159, 104)
        speciesAbilityMap[530] = arrayOf(146, 159, 104)
        speciesAbilityMap[531] = arrayOf(131, 144, 103)
        speciesAbilityMap[532] = arrayOf(62, 125, 89)
        speciesAbilityMap[533] = arrayOf(62, 125, 89)
        speciesAbilityMap[534] = arrayOf(62, 125, 89)
        speciesAbilityMap[535] = arrayOf(33, 93, 11)
        speciesAbilityMap[536] = arrayOf(33, 93, 11)
        speciesAbilityMap[537] = arrayOf(33, 143, 11)
        speciesAbilityMap[538] = arrayOf(62, 39, 104)
        speciesAbilityMap[539] = arrayOf(5, 39, 104)
        speciesAbilityMap[540] = arrayOf(68, 34, 142)
        speciesAbilityMap[541] = arrayOf(102, 34, 142)
        speciesAbilityMap[542] = arrayOf(68, 34, 142)
        speciesAbilityMap[543] = arrayOf(38, 68, 3)
        speciesAbilityMap[544] = arrayOf(38, 68, 3)
        speciesAbilityMap[545] = arrayOf(38, 68, 3)
        speciesAbilityMap[546] = arrayOf(158, 151, 34)
        speciesAbilityMap[547] = arrayOf(158, 151, 34)
        speciesAbilityMap[548] = arrayOf(34, 20, 102)
        speciesAbilityMap[549] = arrayOf(34, 20, 102)
        speciesAbilityMap[550] = arrayOf(120, 91, 104)
        speciesAbilityMap[551] = arrayOf(22, 153, 83)
        speciesAbilityMap[552] = arrayOf(22, 153, 83)
        speciesAbilityMap[553] = arrayOf(22, 153, 83)
        speciesAbilityMap[554] = arrayOf(55, null, 39)
        speciesAbilityMap[555] = arrayOf(125, null, 161)
        speciesAbilityMap[556] = arrayOf(11, 34, 114)
        speciesAbilityMap[557] = arrayOf(5, 75, 133)
        speciesAbilityMap[558] = arrayOf(5, 75, 133)
        speciesAbilityMap[559] = arrayOf(61, 153, 22)
        speciesAbilityMap[560] = arrayOf(61, 153, 22)
        speciesAbilityMap[561] = arrayOf(147, 98, 110)
        speciesAbilityMap[562] = arrayOf(152, null, null)
        speciesAbilityMap[563] = arrayOf(152, null, null)
        speciesAbilityMap[564] = arrayOf(116, 5, 33)
        speciesAbilityMap[565] = arrayOf(116, 5, 33)
        speciesAbilityMap[566] = arrayOf(129, null, null)
        speciesAbilityMap[567] = arrayOf(129, null, null)
        speciesAbilityMap[568] = arrayOf(1, 60, 106)
        speciesAbilityMap[569] = arrayOf(1, 133, 106)
        speciesAbilityMap[570] = arrayOf(149, null, null)
        speciesAbilityMap[571] = arrayOf(149, null, null)
        speciesAbilityMap[572] = arrayOf(56, 101, 92)
        speciesAbilityMap[573] = arrayOf(56, 101, 92)
        speciesAbilityMap[574] = arrayOf(119, 172, 23)
        speciesAbilityMap[575] = arrayOf(119, 172, 23)
        speciesAbilityMap[576] = arrayOf(119, 172, 23)
        speciesAbilityMap[577] = arrayOf(142, 98, 144)
        speciesAbilityMap[578] = arrayOf(142, 98, 144)
        speciesAbilityMap[579] = arrayOf(142, 98, 144)
        speciesAbilityMap[580] = arrayOf(51, 145, 93)
        speciesAbilityMap[581] = arrayOf(51, 145, 93)
        speciesAbilityMap[582] = arrayOf(115, 81, 133)
        speciesAbilityMap[583] = arrayOf(115, 81, 133)
        speciesAbilityMap[584] = arrayOf(115, 117, 133)
        speciesAbilityMap[585] = arrayOf(34, 157, 32)
        speciesAbilityMap[586] = arrayOf(34, 157, 32)
        speciesAbilityMap[587] = arrayOf(9, null, 78)
        speciesAbilityMap[588] = arrayOf(68, 61, 99)
        speciesAbilityMap[589] = arrayOf(68, 75, 142)
        speciesAbilityMap[590] = arrayOf(27, null, 144)
        speciesAbilityMap[591] = arrayOf(27, null, 144)
        speciesAbilityMap[592] = arrayOf(11, 130, 6)
        speciesAbilityMap[593] = arrayOf(11, 130, 6)
        speciesAbilityMap[594] = arrayOf(131, 93, 144)
        speciesAbilityMap[595] = arrayOf(14, 127, 68)
        speciesAbilityMap[596] = arrayOf(14, 127, 68)
        speciesAbilityMap[597] = arrayOf(160, null, null)
        speciesAbilityMap[598] = arrayOf(160, null, 107)
        speciesAbilityMap[599] = arrayOf(57, 58, 29)
        speciesAbilityMap[600] = arrayOf(57, 58, 29)
        speciesAbilityMap[601] = arrayOf(57, 58, 29)
        speciesAbilityMap[602] = arrayOf(26, null, null)
        speciesAbilityMap[603] = arrayOf(26, null, null)
        speciesAbilityMap[604] = arrayOf(26, null, null)
        speciesAbilityMap[605] = arrayOf(140, 28, 148)
        speciesAbilityMap[606] = arrayOf(140, 28, 148)
        speciesAbilityMap[607] = arrayOf(18, 49, 151)
        speciesAbilityMap[608] = arrayOf(18, 49, 151)
        speciesAbilityMap[609] = arrayOf(18, 49, 151)
        speciesAbilityMap[610] = arrayOf(79, 104, 127)
        speciesAbilityMap[611] = arrayOf(79, 104, 127)
        speciesAbilityMap[612] = arrayOf(79, 104, 127)
        speciesAbilityMap[613] = arrayOf(81, 202, 155)
        speciesAbilityMap[614] = arrayOf(81, 202, 33)
        speciesAbilityMap[615] = arrayOf(26, null, null)
        speciesAbilityMap[616] = arrayOf(93, 75, 142)
        speciesAbilityMap[617] = arrayOf(93, 60, 84)
        speciesAbilityMap[618] = arrayOf(9, 7, 8)
        speciesAbilityMap[619] = arrayOf(39, 144, 120)
        speciesAbilityMap[620] = arrayOf(39, 144, 120)
        speciesAbilityMap[621] = arrayOf(24, 125, 104)
        speciesAbilityMap[622] = arrayOf(89, 103, 99)
        speciesAbilityMap[623] = arrayOf(89, 103, 99)
        speciesAbilityMap[624] = arrayOf(128, 39, 46)
        speciesAbilityMap[625] = arrayOf(128, 39, 46)
        speciesAbilityMap[626] = arrayOf(120, 157, 43)
        speciesAbilityMap[627] = arrayOf(51, 125, 55)
        speciesAbilityMap[628] = arrayOf(51, 125, 128)
        speciesAbilityMap[629] = arrayOf(145, 142, 133)
        speciesAbilityMap[630] = arrayOf(145, 142, 133)
        speciesAbilityMap[631] = arrayOf(82, 18, 73)
        speciesAbilityMap[632] = arrayOf(68, 55, 54)
        speciesAbilityMap[633] = arrayOf(55, null, null)
        speciesAbilityMap[634] = arrayOf(55, null, null)
        speciesAbilityMap[635] = arrayOf(26, null, null)
        speciesAbilityMap[636] = arrayOf(49, null, 68)
        speciesAbilityMap[637] = arrayOf(49, null, 68)
        speciesAbilityMap[638] = arrayOf(154, null, null)
        speciesAbilityMap[639] = arrayOf(154, null, null)
        speciesAbilityMap[640] = arrayOf(154, null, null)
        speciesAbilityMap[641] = arrayOf(158, null, 128)
        speciesAbilityMap[642] = arrayOf(158, null, 128)
        speciesAbilityMap[643] = arrayOf(163, null, null)
        speciesAbilityMap[644] = arrayOf(164, null, null)
        speciesAbilityMap[645] = arrayOf(159, null, 125)
        speciesAbilityMap[646] = arrayOf(46, null, null)
        speciesAbilityMap[647] = arrayOf(154, null, null)
        speciesAbilityMap[648] = arrayOf(32, null, null)
        speciesAbilityMap[649] = arrayOf(88, null, null)
        speciesAbilityMap[650] = arrayOf(65, null, 171)
        speciesAbilityMap[651] = arrayOf(65, null, 171)
        speciesAbilityMap[652] = arrayOf(65, null, 171)
        speciesAbilityMap[653] = arrayOf(66, null, 170)
        speciesAbilityMap[654] = arrayOf(66, null, 170)
        speciesAbilityMap[655] = arrayOf(66, null, 170)
        speciesAbilityMap[656] = arrayOf(67, null, 168)
        speciesAbilityMap[657] = arrayOf(67, null, 168)
        speciesAbilityMap[658] = arrayOf(67, null, 168)
        speciesAbilityMap[659] = arrayOf(53, 167, 37)
        speciesAbilityMap[660] = arrayOf(53, 167, 37)
        speciesAbilityMap[661] = arrayOf(145, null, 177)
        speciesAbilityMap[662] = arrayOf(49, null, 177)
        speciesAbilityMap[663] = arrayOf(49, null, 177)
        speciesAbilityMap[664] = arrayOf(19, 14, 132)
        speciesAbilityMap[665] = arrayOf(61, null, 132)
        speciesAbilityMap[666] = arrayOf(19, 14, 132)
        speciesAbilityMap[667] = arrayOf(79, 127, 153)
        speciesAbilityMap[668] = arrayOf(79, 127, 153)
        speciesAbilityMap[669] = arrayOf(166, null, 180)
        speciesAbilityMap[670] = arrayOf(166, null, 180)
        speciesAbilityMap[671] = arrayOf(166, null, 180)
        speciesAbilityMap[672] = arrayOf(157, null, 179)
        speciesAbilityMap[673] = arrayOf(157, null, 179)
        speciesAbilityMap[674] = arrayOf(89, 104, 113)
        speciesAbilityMap[675] = arrayOf(89, 104, 113)
        speciesAbilityMap[676] = arrayOf(169, null, null)
        speciesAbilityMap[677] = arrayOf(51, 151, 20)
        speciesAbilityMap[678] = arrayOf(51, 151, 158)
        speciesAbilityMap[679] = arrayOf(99, null, null)
        speciesAbilityMap[680] = arrayOf(99, null, null)
        speciesAbilityMap[681] = arrayOf(176, null, null)
        speciesAbilityMap[682] = arrayOf(131, null, 165)
        speciesAbilityMap[683] = arrayOf(131, null, 165)
        speciesAbilityMap[684] = arrayOf(175, null, 84)
        speciesAbilityMap[685] = arrayOf(175, null, 84)
        speciesAbilityMap[686] = arrayOf(126, 21, 151)
        speciesAbilityMap[687] = arrayOf(126, 21, 151)
        speciesAbilityMap[688] = arrayOf(181, 97, 124)
        speciesAbilityMap[689] = arrayOf(181, 97, 124)
        speciesAbilityMap[690] = arrayOf(38, 143, 91)
        speciesAbilityMap[691] = arrayOf(38, 143, 91)
        speciesAbilityMap[692] = arrayOf(178, null, null)
        speciesAbilityMap[693] = arrayOf(178, null, null)
        speciesAbilityMap[694] = arrayOf(87, 8, 94)
        speciesAbilityMap[695] = arrayOf(87, 8, 94)
        speciesAbilityMap[696] = arrayOf(173, null, 5)
        speciesAbilityMap[697] = arrayOf(173, null, 69)
        speciesAbilityMap[698] = arrayOf(174, null, 117)
        speciesAbilityMap[699] = arrayOf(174, null, 117)
        speciesAbilityMap[700] = arrayOf(56, 56, 182)
        speciesAbilityMap[701] = arrayOf(7, 84, 104)
        speciesAbilityMap[702] = arrayOf(167, 53, 57)
        speciesAbilityMap[703] = arrayOf(29, null, 5)
        speciesAbilityMap[704] = arrayOf(157, 93, 183)
        speciesAbilityMap[705] = arrayOf(157, 93, 183)
        speciesAbilityMap[706] = arrayOf(157, 93, 183)
        speciesAbilityMap[707] = arrayOf(158, null, 170)
        speciesAbilityMap[708] = arrayOf(30, 119, 139)
        speciesAbilityMap[709] = arrayOf(30, 119, 139)
        speciesAbilityMap[710] = arrayOf(53, 119, 15)
        speciesAbilityMap[711] = arrayOf(53, 119, 15)
        speciesAbilityMap[712] = arrayOf(20, 115, 5)
        speciesAbilityMap[713] = arrayOf(20, 115, 5)
        speciesAbilityMap[714] = arrayOf(119, 151, 140)
        speciesAbilityMap[715] = arrayOf(119, 151, 140)
        speciesAbilityMap[716] = arrayOf(187, null, null)
        speciesAbilityMap[717] = arrayOf(186, null, null)
        speciesAbilityMap[718] = arrayOf(188, null, null)
        speciesAbilityMap[719] = arrayOf(29, null, null)
        speciesAbilityMap[720] = arrayOf(170, null, null)
        speciesAbilityMap[721] = arrayOf(11, null, null)
        speciesAbilityMap[722] = arrayOf(65, null, 203)
        speciesAbilityMap[723] = arrayOf(65, null, 203)
        speciesAbilityMap[724] = arrayOf(65, null, 203)
        speciesAbilityMap[725] = arrayOf(66, null, 22)
        speciesAbilityMap[726] = arrayOf(66, null, 22)
        speciesAbilityMap[727] = arrayOf(66, null, 22)
        speciesAbilityMap[728] = arrayOf(67, null, 204)
        speciesAbilityMap[729] = arrayOf(67, null, 204)
        speciesAbilityMap[730] = arrayOf(67, null, 204)
        speciesAbilityMap[731] = arrayOf(51, 92, 53)
        speciesAbilityMap[732] = arrayOf(51, 92, 53)
        speciesAbilityMap[733] = arrayOf(51, 92, 125)
        speciesAbilityMap[734] = arrayOf(198, 173, 91)
        speciesAbilityMap[735] = arrayOf(198, 173, 91)
        speciesAbilityMap[736] = arrayOf(68, null, null)
        speciesAbilityMap[737] = arrayOf(217, null, null)
        speciesAbilityMap[738] = arrayOf(26, null, null)
        speciesAbilityMap[739] = arrayOf(52, 89, 83)
        speciesAbilityMap[740] = arrayOf(52, 89, 83)
        speciesAbilityMap[741] = arrayOf(216, null, null)
        speciesAbilityMap[742] = arrayOf(118, 19, 175)
        speciesAbilityMap[743] = arrayOf(118, 19, 175)
        speciesAbilityMap[744] = arrayOf(51, 72, 80)
        speciesAbilityMap[745] = arrayOf(51, 146, 80)
        speciesAbilityMap[746] = arrayOf(208, null, null)
        speciesAbilityMap[747] = arrayOf(196, 7, 144)
        speciesAbilityMap[748] = arrayOf(196, 7, 144)
        speciesAbilityMap[749] = arrayOf(20, 192, 39)
        speciesAbilityMap[750] = arrayOf(20, 192, 39)
        speciesAbilityMap[751] = arrayOf(199, null, 11)
        speciesAbilityMap[752] = arrayOf(199, null, 11)
        speciesAbilityMap[753] = arrayOf(102, null, 126)
        speciesAbilityMap[754] = arrayOf(102, null, 126)
        speciesAbilityMap[755] = arrayOf(35, 27, 44)
        speciesAbilityMap[756] = arrayOf(35, 27, 44)
        speciesAbilityMap[757] = arrayOf(212, null, 12)
        speciesAbilityMap[758] = arrayOf(212, null, 12)
        speciesAbilityMap[759] = arrayOf(218, 103, 56)
        speciesAbilityMap[760] = arrayOf(218, 103, 127)
        speciesAbilityMap[761] = arrayOf(102, 12, 175)
        speciesAbilityMap[762] = arrayOf(102, 12, 175)
        speciesAbilityMap[763] = arrayOf(102, 214, 175)
        speciesAbilityMap[764] = arrayOf(166, 205, 30)
        speciesAbilityMap[765] = arrayOf(39, 140, 180)
        speciesAbilityMap[766] = arrayOf(222, null, 128)
        speciesAbilityMap[767] = arrayOf(193, null, null)
        speciesAbilityMap[768] = arrayOf(194, null, null)
        speciesAbilityMap[769] = arrayOf(195, null, 8)
        speciesAbilityMap[770] = arrayOf(195, null, 8)
        speciesAbilityMap[771] = arrayOf(215, null, 109)
        speciesAbilityMap[772] = arrayOf(4, null, null)
        speciesAbilityMap[773] = arrayOf(225, null, null)
        speciesAbilityMap[774] = arrayOf(197, null, null)
        speciesAbilityMap[775] = arrayOf(213, null, null)
        speciesAbilityMap[776] = arrayOf(75, null, null)
        speciesAbilityMap[777] = arrayOf(160, 31, 5)
        speciesAbilityMap[778] = arrayOf(209, null, null)
        speciesAbilityMap[779] = arrayOf(219, 173, 147)
        speciesAbilityMap[780] = arrayOf(201, 157, 13)
        speciesAbilityMap[781] = arrayOf(200, null, null)
        speciesAbilityMap[782] = arrayOf(171, 43, 142)
        speciesAbilityMap[783] = arrayOf(171, 43, 142)
        speciesAbilityMap[784] = arrayOf(171, 43, 142)
        speciesAbilityMap[785] = arrayOf(226, null, 140)
        speciesAbilityMap[786] = arrayOf(227, null, 140)
        speciesAbilityMap[787] = arrayOf(229, null, 140)
        speciesAbilityMap[788] = arrayOf(228, null, 140)
        speciesAbilityMap[789] = arrayOf(109, null, null)
        speciesAbilityMap[790] = arrayOf(5, null, null)
        speciesAbilityMap[791] = arrayOf(230, null, null)
        speciesAbilityMap[792] = arrayOf(231, null, null)
        speciesAbilityMap[793] = arrayOf(224, null, null)
        speciesAbilityMap[794] = arrayOf(224, null, null)
        speciesAbilityMap[795] = arrayOf(224, null, null)
        speciesAbilityMap[796] = arrayOf(224, null, null)
        speciesAbilityMap[797] = arrayOf(224, null, null)
        speciesAbilityMap[798] = arrayOf(224, null, null)
        speciesAbilityMap[799] = arrayOf(224, null, null)
        speciesAbilityMap[800] = arrayOf(232, null, null)
    }

    private fun registerSpeciesAbilityChunk3() {
        speciesAbilityMap[801] = arrayOf(220, null, null)
        speciesAbilityMap[802] = arrayOf(101, null, null)
        speciesAbilityMap[803] = arrayOf(224, null, null)
        speciesAbilityMap[804] = arrayOf(224, null, null)
        speciesAbilityMap[805] = arrayOf(224, null, null)
        speciesAbilityMap[806] = arrayOf(224, null, null)
        speciesAbilityMap[807] = arrayOf(10, null, null)
        speciesAbilityMap[808] = arrayOf(42, null, null)
        speciesAbilityMap[809] = arrayOf(89, null, null)
        speciesAbilityMap[810] = arrayOf(65, null, 229)
        speciesAbilityMap[811] = arrayOf(65, null, 229)
        speciesAbilityMap[812] = arrayOf(65, null, 229)
        speciesAbilityMap[813] = arrayOf(66, null, 236)
        speciesAbilityMap[814] = arrayOf(66, null, 236)
        speciesAbilityMap[815] = arrayOf(66, null, 236)
        speciesAbilityMap[816] = arrayOf(67, null, 97)
        speciesAbilityMap[817] = arrayOf(67, null, 97)
        speciesAbilityMap[818] = arrayOf(67, null, 97)
        speciesAbilityMap[819] = arrayOf(167, null, 82)
        speciesAbilityMap[820] = arrayOf(167, null, 82)
        speciesAbilityMap[821] = arrayOf(51, 127, 145)
        speciesAbilityMap[822] = arrayOf(51, 127, 145)
        speciesAbilityMap[823] = arrayOf(46, 127, 240)
        speciesAbilityMap[824] = arrayOf(68, 14, 140)
        speciesAbilityMap[825] = arrayOf(68, 14, 140)
        speciesAbilityMap[826] = arrayOf(68, 119, 140)
        speciesAbilityMap[827] = arrayOf(50, 84, 198)
        speciesAbilityMap[828] = arrayOf(50, 84, 198)
        speciesAbilityMap[829] = arrayOf(238, 144, 27)
        speciesAbilityMap[830] = arrayOf(238, 144, 27)
        speciesAbilityMap[831] = arrayOf(218, 50, 171)
        speciesAbilityMap[832] = arrayOf(218, 80, 171)
        speciesAbilityMap[833] = arrayOf(173, 75, 33)
        speciesAbilityMap[834] = arrayOf(173, 75, 33)
        speciesAbilityMap[835] = arrayOf(237, null, 155)
        speciesAbilityMap[836] = arrayOf(173, null, 172)
        speciesAbilityMap[837] = arrayOf(243, 85, 18)
        speciesAbilityMap[838] = arrayOf(243, 49, 18)
        speciesAbilityMap[839] = arrayOf(243, 49, 18)
        speciesAbilityMap[840] = arrayOf(247, 82, 171)
        speciesAbilityMap[841] = arrayOf(247, 82, 55)
        speciesAbilityMap[842] = arrayOf(247, 82, 47)
        speciesAbilityMap[843] = arrayOf(245, 61, 8)
        speciesAbilityMap[844] = arrayOf(245, 61, 8)
        speciesAbilityMap[845] = arrayOf(241, null, null)
        speciesAbilityMap[846] = arrayOf(33, null, 239)
        speciesAbilityMap[847] = arrayOf(33, null, 239)
        speciesAbilityMap[848] = arrayOf(155, 9, 103)
        speciesAbilityMap[849] = arrayOf(244, 57, 101)
        speciesAbilityMap[850] = arrayOf(18, 73, 49)
        speciesAbilityMap[851] = arrayOf(18, 73, 49)
        speciesAbilityMap[852] = arrayOf(7, null, 101)
        speciesAbilityMap[853] = arrayOf(7, null, 101)
        speciesAbilityMap[854] = arrayOf(133, null, 130)
        speciesAbilityMap[855] = arrayOf(133, null, 130)
        speciesAbilityMap[856] = arrayOf(131, 107, 156)
        speciesAbilityMap[857] = arrayOf(131, 107, 156)
        speciesAbilityMap[858] = arrayOf(131, 107, 156)
        speciesAbilityMap[859] = arrayOf(158, 119, 124)
        speciesAbilityMap[860] = arrayOf(158, 119, 124)
        speciesAbilityMap[861] = arrayOf(158, 119, 124)
        speciesAbilityMap[862] = arrayOf(120, 62, 128)
        speciesAbilityMap[863] = arrayOf(4, 181, 252)
        speciesAbilityMap[864] = arrayOf(133, null, 253)
        speciesAbilityMap[865] = arrayOf(80, null, 113)
        speciesAbilityMap[866] = arrayOf(77, 251, 115)
        speciesAbilityMap[867] = arrayOf(254, null, null)
        speciesAbilityMap[868] = arrayOf(175, null, 165)
        speciesAbilityMap[869] = arrayOf(175, null, 165)
        speciesAbilityMap[870] = arrayOf(4, null, 128)
        speciesAbilityMap[871] = arrayOf(31, null, 226)
        speciesAbilityMap[872] = arrayOf(19, null, 246)
        speciesAbilityMap[873] = arrayOf(19, null, 246)
        speciesAbilityMap[874] = arrayOf(249, null, null)
        speciesAbilityMap[875] = arrayOf(248, null, null)
        speciesAbilityMap[876] = arrayOf(39, 28, 227)
        speciesAbilityMap[877] = arrayOf(258, null, null)
        speciesAbilityMap[878] = arrayOf(125, null, 134)
        speciesAbilityMap[879] = arrayOf(125, null, 134)
        speciesAbilityMap[880] = arrayOf(10, 55, 146)
        speciesAbilityMap[881] = arrayOf(10, 9, 202)
        speciesAbilityMap[882] = arrayOf(11, 173, 146)
        speciesAbilityMap[883] = arrayOf(11, 115, 202)
        speciesAbilityMap[884] = arrayOf(135, 134, 242)
        speciesAbilityMap[885] = arrayOf(29, 151, 130)
        speciesAbilityMap[886] = arrayOf(29, 151, 130)
        speciesAbilityMap[887] = arrayOf(29, 151, 130)
        speciesAbilityMap[888] = arrayOf(234, null, null)
        speciesAbilityMap[889] = arrayOf(235, null, null)
        speciesAbilityMap[890] = arrayOf(46, null, null)
        speciesAbilityMap[891] = arrayOf(39, null, null)
        speciesAbilityMap[892] = arrayOf(260, null, null)
        speciesAbilityMap[893] = arrayOf(102, null, null)
        speciesAbilityMap[894] = arrayOf(262, null, null)
        speciesAbilityMap[895] = arrayOf(263, null, null)
        speciesAbilityMap[896] = arrayOf(264, null, null)
        speciesAbilityMap[897] = arrayOf(265, null, null)
        speciesAbilityMap[898] = arrayOf(127, null, null)
        speciesAbilityMap[899] = arrayOf(22, 119, 157)
        speciesAbilityMap[900] = arrayOf(68, 125, 292)
        speciesAbilityMap[901] = arrayOf(62, 171, 127)
        speciesAbilityMap[902] = arrayOf(33, 91, 104)
        speciesAbilityMap[903] = arrayOf(46, 84, 143)
        speciesAbilityMap[904] = arrayOf(38, 33, 22)
        speciesAbilityMap[905] = arrayOf(56, null, 126)
        speciesAbilityMap[956] = arrayOf(82, 55, 47)
        speciesAbilityMap[957] = arrayOf(82, 55, 47)
        speciesAbilityMap[958] = arrayOf(207, null, null)
        speciesAbilityMap[959] = arrayOf(81, null, 202)
        speciesAbilityMap[960] = arrayOf(81, null, 202)
        speciesAbilityMap[961] = arrayOf(81, null, 117)
        speciesAbilityMap[962] = arrayOf(81, null, 117)
        speciesAbilityMap[963] = arrayOf(8, 221, 159)
        speciesAbilityMap[964] = arrayOf(8, 221, 159)
        speciesAbilityMap[965] = arrayOf(53, 101, 155)
        speciesAbilityMap[966] = arrayOf(169, 101, 155)
        speciesAbilityMap[967] = arrayOf(42, 5, 206)
        speciesAbilityMap[968] = arrayOf(42, 5, 206)
        speciesAbilityMap[969] = arrayOf(42, 5, 206)
        speciesAbilityMap[970] = arrayOf(143, 82, 223)
        speciesAbilityMap[971] = arrayOf(143, 82, 223)
        speciesAbilityMap[972] = arrayOf(119, null, 139)
        speciesAbilityMap[973] = arrayOf(130, 31, 69)
        speciesAbilityMap[974] = arrayOf(53, 181, 127)
        speciesAbilityMap[975] = arrayOf(50, 257, 107)
        speciesAbilityMap[976] = arrayOf(50, 257, 107)
        speciesAbilityMap[977] = arrayOf(82, 20, 144)
        speciesAbilityMap[978] = arrayOf(259, 20, 144)
        speciesAbilityMap[979] = arrayOf(80, null, 113)
        speciesAbilityMap[980] = arrayOf(26, 256, 228)
        speciesAbilityMap[981] = arrayOf(72, 251, 115)
        speciesAbilityMap[982] = arrayOf(172, null, null)
        speciesAbilityMap[983] = arrayOf(128, null, null)
        speciesAbilityMap[984] = arrayOf(201, null, null)
        speciesAbilityMap[985] = arrayOf(261, 20, 144)
        speciesAbilityMap[986] = arrayOf(133, null, 130)
        speciesAbilityMap[987] = arrayOf(53, 82, 95)
        speciesAbilityMap[988] = arrayOf(53, 82, 95)
        speciesAbilityMap[989] = arrayOf(55, null, 39)
        speciesAbilityMap[990] = arrayOf(255, null, 161)
        speciesAbilityMap[991] = arrayOf(254, null, null)
        speciesAbilityMap[992] = arrayOf(250, null, null)
        speciesAbilityMap[993] = arrayOf(22, 18, 69)
        speciesAbilityMap[994] = arrayOf(22, 18, 69)
        speciesAbilityMap[995] = arrayOf(43, 9, 106)
        speciesAbilityMap[996] = arrayOf(43, 9, 106)
        speciesAbilityMap[997] = arrayOf(66, null, 119)
        speciesAbilityMap[998] = arrayOf(38, 33, 22)
        speciesAbilityMap[999] = arrayOf(39, 51, 124)
        speciesAbilityMap[1000] = arrayOf(67, null, 292)
        speciesAbilityMap[1001] = arrayOf(34, 55, 102)
        speciesAbilityMap[1002] = arrayOf(149, null, null)
        speciesAbilityMap[1003] = arrayOf(149, null, null)
        speciesAbilityMap[1004] = arrayOf(51, 125, 110)
        speciesAbilityMap[1005] = arrayOf(157, 75, 183)
        speciesAbilityMap[1006] = arrayOf(157, 75, 183)
        speciesAbilityMap[1007] = arrayOf(173, 115, 5)
        speciesAbilityMap[1008] = arrayOf(65, null, 113)
        speciesAbilityMap[1009] = arrayOf(9, null, 31)
        speciesAbilityMap[1010] = arrayOf(9, null, 31)
        speciesAbilityMap[1011] = arrayOf(9, null, 31)
        speciesAbilityMap[1012] = arrayOf(9, null, 31)
        speciesAbilityMap[1013] = arrayOf(9, null, 31)
        speciesAbilityMap[1014] = arrayOf(9, null, 31)
        speciesAbilityMap[1015] = arrayOf(9, null, 31)
        speciesAbilityMap[1016] = arrayOf(9, null, 31)
        speciesAbilityMap[1017] = arrayOf(9, null, 31)
        speciesAbilityMap[1018] = arrayOf(9, null, 31)
        speciesAbilityMap[1019] = arrayOf(9, null, 31)
        speciesAbilityMap[1020] = arrayOf(9, null, 31)
        speciesAbilityMap[1021] = arrayOf(9, null, 31)
        speciesAbilityMap[1022] = arrayOf(9, null, 31)
        speciesAbilityMap[1023] = arrayOf(9, null, null)
        speciesAbilityMap[1024] = arrayOf(26, null, null)
        speciesAbilityMap[1025] = arrayOf(26, null, null)
        speciesAbilityMap[1026] = arrayOf(26, null, null)
        speciesAbilityMap[1027] = arrayOf(26, null, null)
        speciesAbilityMap[1028] = arrayOf(26, null, null)
        speciesAbilityMap[1029] = arrayOf(26, null, null)
        speciesAbilityMap[1030] = arrayOf(26, null, null)
        speciesAbilityMap[1031] = arrayOf(26, null, null)
        speciesAbilityMap[1032] = arrayOf(26, null, null)
        speciesAbilityMap[1033] = arrayOf(26, null, null)
        speciesAbilityMap[1034] = arrayOf(26, null, null)
        speciesAbilityMap[1035] = arrayOf(26, null, null)
        speciesAbilityMap[1036] = arrayOf(26, null, null)
        speciesAbilityMap[1037] = arrayOf(26, null, null)
        speciesAbilityMap[1038] = arrayOf(26, null, null)
        speciesAbilityMap[1039] = arrayOf(26, null, null)
        speciesAbilityMap[1040] = arrayOf(26, null, null)
        speciesAbilityMap[1041] = arrayOf(26, null, null)
        speciesAbilityMap[1042] = arrayOf(26, null, null)
        speciesAbilityMap[1043] = arrayOf(26, null, null)
        speciesAbilityMap[1044] = arrayOf(26, null, null)
        speciesAbilityMap[1045] = arrayOf(26, null, null)
        speciesAbilityMap[1046] = arrayOf(26, null, null)
        speciesAbilityMap[1047] = arrayOf(26, null, null)
        speciesAbilityMap[1048] = arrayOf(26, null, null)
        speciesAbilityMap[1049] = arrayOf(26, null, null)
        speciesAbilityMap[1050] = arrayOf(26, null, null)
        speciesAbilityMap[1051] = arrayOf(59, null, null)
        speciesAbilityMap[1052] = arrayOf(59, null, null)
        speciesAbilityMap[1053] = arrayOf(59, null, null)
        speciesAbilityMap[1054] = arrayOf(46, null, null)
        speciesAbilityMap[1055] = arrayOf(46, null, null)
        speciesAbilityMap[1056] = arrayOf(46, null, null)
        speciesAbilityMap[1057] = arrayOf(61, null, 142)
        speciesAbilityMap[1058] = arrayOf(61, null, 142)
        speciesAbilityMap[1059] = arrayOf(107, null, 142)
        speciesAbilityMap[1060] = arrayOf(107, null, 142)
        speciesAbilityMap[1061] = arrayOf(122, null, null)
        speciesAbilityMap[1062] = arrayOf(60, 114, 159)
        speciesAbilityMap[1063] = arrayOf(60, 114, 159)
        speciesAbilityMap[1064] = arrayOf(26, null, null)
        speciesAbilityMap[1065] = arrayOf(26, null, null)
        speciesAbilityMap[1066] = arrayOf(26, null, null)
        speciesAbilityMap[1067] = arrayOf(26, null, null)
        speciesAbilityMap[1068] = arrayOf(26, null, null)
        speciesAbilityMap[1069] = arrayOf(46, null, 140)
        speciesAbilityMap[1070] = arrayOf(46, null, 140)
        speciesAbilityMap[1071] = arrayOf(26, null, null)
        speciesAbilityMap[1072] = arrayOf(32, null, null)
        speciesAbilityMap[1073] = arrayOf(121, null, null)
        speciesAbilityMap[1074] = arrayOf(121, null, null)
        speciesAbilityMap[1075] = arrayOf(121, null, null)
        speciesAbilityMap[1076] = arrayOf(121, null, null)
        speciesAbilityMap[1077] = arrayOf(121, null, null)
        speciesAbilityMap[1078] = arrayOf(121, null, null)
        speciesAbilityMap[1079] = arrayOf(121, null, null)
        speciesAbilityMap[1080] = arrayOf(121, null, null)
        speciesAbilityMap[1081] = arrayOf(121, null, null)
        speciesAbilityMap[1082] = arrayOf(121, null, null)
        speciesAbilityMap[1083] = arrayOf(121, null, null)
        speciesAbilityMap[1084] = arrayOf(121, null, null)
        speciesAbilityMap[1085] = arrayOf(121, null, null)
        speciesAbilityMap[1086] = arrayOf(121, null, null)
        speciesAbilityMap[1087] = arrayOf(121, null, null)
        speciesAbilityMap[1088] = arrayOf(121, null, null)
        speciesAbilityMap[1089] = arrayOf(121, null, null)
        speciesAbilityMap[1090] = arrayOf(69, 91, 104)
        speciesAbilityMap[1091] = arrayOf(155, 91, 104)
        speciesAbilityMap[1092] = arrayOf(125, null, 161)
        speciesAbilityMap[1093] = arrayOf(255, null, 161)
        speciesAbilityMap[1094] = arrayOf(34, 157, 32)
        speciesAbilityMap[1095] = arrayOf(34, 157, 32)
        speciesAbilityMap[1096] = arrayOf(34, 157, 32)
        speciesAbilityMap[1097] = arrayOf(34, 157, 32)
        speciesAbilityMap[1098] = arrayOf(34, 157, 32)
        speciesAbilityMap[1099] = arrayOf(34, 157, 32)
        speciesAbilityMap[1100] = arrayOf(144, null, 144)
        speciesAbilityMap[1101] = arrayOf(10, null, 10)
        speciesAbilityMap[1102] = arrayOf(22, null, null)
        speciesAbilityMap[1103] = arrayOf(142, null, null)
        speciesAbilityMap[1106] = arrayOf(154, null, null)
        speciesAbilityMap[1107] = arrayOf(32, null, null)
        speciesAbilityMap[1108] = arrayOf(88, null, null)
        speciesAbilityMap[1109] = arrayOf(88, null, null)
        speciesAbilityMap[1110] = arrayOf(88, null, null)
        speciesAbilityMap[1111] = arrayOf(88, null, null)
        speciesAbilityMap[1112] = arrayOf(210, null, null)
        speciesAbilityMap[1113] = arrayOf(210, null, null)
        speciesAbilityMap[1114] = arrayOf(19, 14, 132)
        speciesAbilityMap[1115] = arrayOf(19, 14, 132)
        speciesAbilityMap[1116] = arrayOf(19, 14, 132)
        speciesAbilityMap[1117] = arrayOf(19, 14, 132)
        speciesAbilityMap[1118] = arrayOf(19, 14, 132)
        speciesAbilityMap[1119] = arrayOf(19, 14, 132)
        speciesAbilityMap[1120] = arrayOf(19, 14, 132)
        speciesAbilityMap[1121] = arrayOf(19, 14, 132)
        speciesAbilityMap[1122] = arrayOf(19, 14, 132)
        speciesAbilityMap[1123] = arrayOf(19, 14, 132)
        speciesAbilityMap[1124] = arrayOf(19, 14, 132)
        speciesAbilityMap[1125] = arrayOf(19, 14, 132)
        speciesAbilityMap[1126] = arrayOf(19, 14, 132)
        speciesAbilityMap[1127] = arrayOf(19, 14, 132)
        speciesAbilityMap[1128] = arrayOf(19, 14, 132)
        speciesAbilityMap[1129] = arrayOf(19, 14, 132)
        speciesAbilityMap[1130] = arrayOf(19, 14, 132)
        speciesAbilityMap[1131] = arrayOf(19, 14, 132)
        speciesAbilityMap[1132] = arrayOf(19, 14, 132)
        speciesAbilityMap[1133] = arrayOf(166, null, 180)
        speciesAbilityMap[1134] = arrayOf(166, null, 180)
        speciesAbilityMap[1135] = arrayOf(166, null, 180)
        speciesAbilityMap[1136] = arrayOf(166, null, 180)
        speciesAbilityMap[1137] = arrayOf(166, null, 180)
        speciesAbilityMap[1138] = arrayOf(166, null, 180)
        speciesAbilityMap[1139] = arrayOf(166, null, 180)
        speciesAbilityMap[1140] = arrayOf(166, null, 180)
        speciesAbilityMap[1141] = arrayOf(166, null, 180)
        speciesAbilityMap[1142] = arrayOf(166, null, 180)
        speciesAbilityMap[1143] = arrayOf(166, null, 180)
        speciesAbilityMap[1144] = arrayOf(166, null, 180)
        speciesAbilityMap[1145] = arrayOf(166, null, 180)
        speciesAbilityMap[1146] = arrayOf(169, null, null)
        speciesAbilityMap[1147] = arrayOf(169, null, null)
        speciesAbilityMap[1148] = arrayOf(169, null, null)
        speciesAbilityMap[1149] = arrayOf(169, null, null)
        speciesAbilityMap[1150] = arrayOf(169, null, null)
        speciesAbilityMap[1151] = arrayOf(169, null, null)
        speciesAbilityMap[1152] = arrayOf(169, null, null)
        speciesAbilityMap[1153] = arrayOf(169, null, null)
        speciesAbilityMap[1154] = arrayOf(169, null, null)
        speciesAbilityMap[1155] = arrayOf(51, 151, 172)
        speciesAbilityMap[1156] = arrayOf(176, null, null)
        speciesAbilityMap[1157] = arrayOf(53, 119, 15)
        speciesAbilityMap[1158] = arrayOf(53, 119, 15)
        speciesAbilityMap[1159] = arrayOf(53, 119, 15)
        speciesAbilityMap[1160] = arrayOf(53, 119, 15)
        speciesAbilityMap[1161] = arrayOf(53, 119, 15)
        speciesAbilityMap[1162] = arrayOf(53, 119, 15)
        speciesAbilityMap[1163] = arrayOf(187, null, null)
        speciesAbilityMap[1164] = arrayOf(188, null, null)
        speciesAbilityMap[1165] = arrayOf(211, null, null)
        speciesAbilityMap[1166] = arrayOf(211, null, null)
        speciesAbilityMap[1167] = arrayOf(211, null, null)
        speciesAbilityMap[1168] = arrayOf(170, null, null)
        speciesAbilityMap[1169] = arrayOf(216, null, null)
        speciesAbilityMap[1170] = arrayOf(216, null, null)
        speciesAbilityMap[1171] = arrayOf(216, null, null)
        speciesAbilityMap[1172] = arrayOf(20, null, null)
        speciesAbilityMap[1173] = arrayOf(51, 72, 99)
        speciesAbilityMap[1174] = arrayOf(181, null, null)
        speciesAbilityMap[1175] = arrayOf(208, null, null)
        speciesAbilityMap[1176] = arrayOf(225, null, null)
        speciesAbilityMap[1177] = arrayOf(225, null, null)
        speciesAbilityMap[1178] = arrayOf(225, null, null)
        speciesAbilityMap[1179] = arrayOf(225, null, null)
        speciesAbilityMap[1180] = arrayOf(225, null, null)
        speciesAbilityMap[1181] = arrayOf(225, null, null)
        speciesAbilityMap[1182] = arrayOf(225, null, null)
        speciesAbilityMap[1183] = arrayOf(225, null, null)
        speciesAbilityMap[1184] = arrayOf(225, null, null)
        speciesAbilityMap[1185] = arrayOf(225, null, null)
        speciesAbilityMap[1186] = arrayOf(225, null, null)
        speciesAbilityMap[1187] = arrayOf(225, null, null)
        speciesAbilityMap[1188] = arrayOf(225, null, null)
        speciesAbilityMap[1189] = arrayOf(225, null, null)
        speciesAbilityMap[1190] = arrayOf(225, null, null)
        speciesAbilityMap[1191] = arrayOf(225, null, null)
        speciesAbilityMap[1192] = arrayOf(225, null, null)
        speciesAbilityMap[1193] = arrayOf(197, null, null)
        speciesAbilityMap[1194] = arrayOf(197, null, null)
        speciesAbilityMap[1195] = arrayOf(197, null, null)
        speciesAbilityMap[1196] = arrayOf(197, null, null)
        speciesAbilityMap[1197] = arrayOf(197, null, null)
        speciesAbilityMap[1198] = arrayOf(197, null, null)
        speciesAbilityMap[1199] = arrayOf(197, null, null)
        speciesAbilityMap[1200] = arrayOf(197, null, null)
        speciesAbilityMap[1201] = arrayOf(197, null, null)
        speciesAbilityMap[1202] = arrayOf(197, null, null)
        speciesAbilityMap[1203] = arrayOf(197, null, null)
        speciesAbilityMap[1204] = arrayOf(197, null, null)
        speciesAbilityMap[1205] = arrayOf(197, null, null)
        speciesAbilityMap[1206] = arrayOf(209, null, null)
        speciesAbilityMap[1210] = arrayOf(220, null, null)
        speciesAbilityMap[1211] = arrayOf(241, null, null)
        speciesAbilityMap[1212] = arrayOf(241, null, null)
        speciesAbilityMap[1213] = arrayOf(244, 58, 101)
        speciesAbilityMap[1214] = arrayOf(133, null, 130)
        speciesAbilityMap[1215] = arrayOf(133, null, 130)
        speciesAbilityMap[1216] = arrayOf(175, null, 165)
        speciesAbilityMap[1217] = arrayOf(175, null, 165)
        speciesAbilityMap[1218] = arrayOf(175, null, 165)
        speciesAbilityMap[1219] = arrayOf(175, null, 165)
        speciesAbilityMap[1220] = arrayOf(175, null, 165)
        speciesAbilityMap[1221] = arrayOf(175, null, 165)
        speciesAbilityMap[1222] = arrayOf(175, null, 165)
        speciesAbilityMap[1223] = arrayOf(175, null, 165)
        speciesAbilityMap[1224] = arrayOf(248, null, null)
        speciesAbilityMap[1225] = arrayOf(20, 28, 227)
        speciesAbilityMap[1226] = arrayOf(258, null, null)
        speciesAbilityMap[1227] = arrayOf(234, null, null)
        speciesAbilityMap[1228] = arrayOf(235, null, null)
        speciesAbilityMap[1229] = arrayOf(46, null, null)
        speciesAbilityMap[1230] = arrayOf(260, null, null)
        speciesAbilityMap[1231] = arrayOf(102, null, null)
        speciesAbilityMap[1234] = arrayOf(33, 91, 104)
        speciesAbilityMap[1235] = arrayOf(175, null, 165)
        speciesAbilityMap[1236] = arrayOf(175, null, 165)
        speciesAbilityMap[1237] = arrayOf(175, null, 165)
        speciesAbilityMap[1238] = arrayOf(175, null, 165)
        speciesAbilityMap[1239] = arrayOf(175, null, 165)
        speciesAbilityMap[1240] = arrayOf(175, null, 165)
        speciesAbilityMap[1241] = arrayOf(175, null, 165)
        speciesAbilityMap[1242] = arrayOf(175, null, 165)
        speciesAbilityMap[1243] = arrayOf(175, null, 165)
        speciesAbilityMap[1244] = arrayOf(175, null, 165)
        speciesAbilityMap[1245] = arrayOf(175, null, 165)
        speciesAbilityMap[1246] = arrayOf(175, null, 165)
        speciesAbilityMap[1247] = arrayOf(175, null, 165)
        speciesAbilityMap[1248] = arrayOf(175, null, 165)
        speciesAbilityMap[1249] = arrayOf(175, null, 165)
        speciesAbilityMap[1250] = arrayOf(175, null, 165)
        speciesAbilityMap[1251] = arrayOf(175, null, 165)
        speciesAbilityMap[1252] = arrayOf(175, null, 165)
        speciesAbilityMap[1253] = arrayOf(175, null, 165)
        speciesAbilityMap[1254] = arrayOf(175, null, 165)
        speciesAbilityMap[1255] = arrayOf(175, null, 165)
        speciesAbilityMap[1256] = arrayOf(175, null, 165)
        speciesAbilityMap[1257] = arrayOf(175, null, 165)
    }

    private fun registerSpeciesAbilityChunk4() {
        speciesAbilityMap[1258] = arrayOf(175, null, 165)
        speciesAbilityMap[1259] = arrayOf(175, null, 165)
        speciesAbilityMap[1260] = arrayOf(175, null, 165)
        speciesAbilityMap[1261] = arrayOf(175, null, 165)
        speciesAbilityMap[1262] = arrayOf(175, null, 165)
        speciesAbilityMap[1263] = arrayOf(175, null, 165)
        speciesAbilityMap[1264] = arrayOf(175, null, 165)
        speciesAbilityMap[1265] = arrayOf(175, null, 165)
        speciesAbilityMap[1266] = arrayOf(175, null, 165)
        speciesAbilityMap[1267] = arrayOf(175, null, 165)
        speciesAbilityMap[1268] = arrayOf(175, null, 165)
        speciesAbilityMap[1269] = arrayOf(175, null, 165)
        speciesAbilityMap[1270] = arrayOf(175, null, 165)
        speciesAbilityMap[1271] = arrayOf(175, null, 165)
        speciesAbilityMap[1272] = arrayOf(175, null, 165)
        speciesAbilityMap[1273] = arrayOf(175, null, 165)
        speciesAbilityMap[1274] = arrayOf(175, null, 165)
        speciesAbilityMap[1275] = arrayOf(175, null, 165)
        speciesAbilityMap[1276] = arrayOf(175, null, 165)
        speciesAbilityMap[1277] = arrayOf(175, null, 165)
        speciesAbilityMap[1278] = arrayOf(175, null, 165)
        speciesAbilityMap[1279] = arrayOf(175, null, 165)
        speciesAbilityMap[1280] = arrayOf(175, null, 165)
        speciesAbilityMap[1281] = arrayOf(175, null, 165)
        speciesAbilityMap[1282] = arrayOf(175, null, 165)
        speciesAbilityMap[1283] = arrayOf(175, null, 165)
        speciesAbilityMap[1284] = arrayOf(175, null, 165)
        speciesAbilityMap[1285] = arrayOf(175, null, 165)
        speciesAbilityMap[1286] = arrayOf(175, null, 165)
        speciesAbilityMap[1287] = arrayOf(175, null, 165)
        speciesAbilityMap[1288] = arrayOf(175, null, 165)
        speciesAbilityMap[1289] = arrayOf(65, null, 168)
        speciesAbilityMap[1290] = arrayOf(65, null, 168)
        speciesAbilityMap[1291] = arrayOf(65, null, 168)
        speciesAbilityMap[1292] = arrayOf(66, null, 109)
        speciesAbilityMap[1293] = arrayOf(66, null, 109)
        speciesAbilityMap[1294] = arrayOf(66, null, 109)
        speciesAbilityMap[1295] = arrayOf(67, null, 153)
        speciesAbilityMap[1296] = arrayOf(67, null, 153)
        speciesAbilityMap[1297] = arrayOf(67, null, 153)
        speciesAbilityMap[1298] = arrayOf(165, 82, 47)
        speciesAbilityMap[1299] = arrayOf(268, 82, 47)
        speciesAbilityMap[1300] = arrayOf(165, 82, 47)
        speciesAbilityMap[1301] = arrayOf(15, null, 198)
        speciesAbilityMap[1302] = arrayOf(15, null, 198)
        speciesAbilityMap[1303] = arrayOf(68, null, 110)
        speciesAbilityMap[1304] = arrayOf(68, null, 110)
        speciesAbilityMap[1305] = arrayOf(9, 30, 89)
        speciesAbilityMap[1306] = arrayOf(10, 30, 89)
        speciesAbilityMap[1307] = arrayOf(10, 30, 89)
        speciesAbilityMap[1308] = arrayOf(50, 53, 20)
        speciesAbilityMap[1309] = arrayOf(132, 167, 101)
        speciesAbilityMap[1310] = arrayOf(132, 167, 101)
        speciesAbilityMap[1311] = arrayOf(20, null, 103)
        speciesAbilityMap[1312] = arrayOf(273, null, 165)
        speciesAbilityMap[1313] = arrayOf(48, null, 139)
        speciesAbilityMap[1314] = arrayOf(48, null, 139)
        speciesAbilityMap[1315] = arrayOf(269, null, 139)
        speciesAbilityMap[1316] = arrayOf(22, 55, 62)
        speciesAbilityMap[1317] = arrayOf(22, 55, 62)
        speciesAbilityMap[1318] = arrayOf(22, 55, 125)
        speciesAbilityMap[1319] = arrayOf(22, 55, 125)
        speciesAbilityMap[1320] = arrayOf(272, 5, 29)
        speciesAbilityMap[1321] = arrayOf(272, 5, 29)
        speciesAbilityMap[1322] = arrayOf(272, 5, 29)
        speciesAbilityMap[1323] = arrayOf(18, null, 49)
        speciesAbilityMap[1324] = arrayOf(18, null, 133)
        speciesAbilityMap[1325] = arrayOf(18, null, 133)
        speciesAbilityMap[1326] = arrayOf(20, 9, 6)
        speciesAbilityMap[1327] = arrayOf(280, 9, 6)
        speciesAbilityMap[1328] = arrayOf(277, 10, 172)
        speciesAbilityMap[1329] = arrayOf(277, 10, 172)
        speciesAbilityMap[1330] = arrayOf(22, 50, 198)
        speciesAbilityMap[1331] = arrayOf(22, 275, 198)
        speciesAbilityMap[1332] = arrayOf(84, 124, 158)
        speciesAbilityMap[1333] = arrayOf(84, 143, 158)
        speciesAbilityMap[1334] = arrayOf(274, null, 151)
        speciesAbilityMap[1335] = arrayOf(274, null, 151)
        speciesAbilityMap[1336] = arrayOf(298, null, null)
        speciesAbilityMap[1337] = arrayOf(298, null, null)
        speciesAbilityMap[1338] = arrayOf(271, 75, 144)
        speciesAbilityMap[1339] = arrayOf(34, 15, 103)
        speciesAbilityMap[1340] = arrayOf(34, 15, 141)
        speciesAbilityMap[1341] = arrayOf(14, null, 61)
        speciesAbilityMap[1342] = arrayOf(28, null, 140)
        speciesAbilityMap[1343] = arrayOf(107, 119, 3)
        speciesAbilityMap[1344] = arrayOf(290, 119, 3)
        speciesAbilityMap[1345] = arrayOf(104, 20, 124)
        speciesAbilityMap[1346] = arrayOf(104, 20, 124)
        speciesAbilityMap[1347] = arrayOf(104, 20, 124)
        speciesAbilityMap[1348] = arrayOf(183, 155, 8)
        speciesAbilityMap[1349] = arrayOf(183, 155, 8)
        speciesAbilityMap[1350] = arrayOf(145, 51, 276)
        speciesAbilityMap[1351] = arrayOf(41, null, null)
        speciesAbilityMap[1352] = arrayOf(278, null, null)
        speciesAbilityMap[1353] = arrayOf(278, null, null)
        speciesAbilityMap[1354] = arrayOf(142, null, 112)
        speciesAbilityMap[1355] = arrayOf(142, null, 111)
        speciesAbilityMap[1356] = arrayOf(61, null, 144)
        speciesAbilityMap[1357] = arrayOf(297, null, 8)
        speciesAbilityMap[1358] = arrayOf(295, null, 212)
        speciesAbilityMap[1359] = arrayOf(295, null, 212)
        speciesAbilityMap[1360] = arrayOf(53, null, 218)
        speciesAbilityMap[1361] = arrayOf(146, null, 218)
        speciesAbilityMap[1362] = arrayOf(113, 77, 294)
        speciesAbilityMap[1363] = arrayOf(47, 81, 125)
        speciesAbilityMap[1364] = arrayOf(47, 202, 125)
        speciesAbilityMap[1365] = arrayOf(104, null, 292)
        speciesAbilityMap[1366] = arrayOf(109, 12, 41)
        speciesAbilityMap[1367] = arrayOf(279, null, 114)
        speciesAbilityMap[1368] = arrayOf(279, null, 114)
        speciesAbilityMap[1369] = arrayOf(279, null, 114)
        speciesAbilityMap[1370] = arrayOf(72, 39, 128)
        speciesAbilityMap[1371] = arrayOf(38, 11, 109)
        speciesAbilityMap[1372] = arrayOf(291, 296, 157)
        speciesAbilityMap[1373] = arrayOf(32, 50, 155)
        speciesAbilityMap[1374] = arrayOf(32, 50, 155)
        speciesAbilityMap[1375] = arrayOf(128, 293, 46)
        speciesAbilityMap[1376] = arrayOf(281, null, null)
        speciesAbilityMap[1377] = arrayOf(281, null, null)
        speciesAbilityMap[1378] = arrayOf(281, null, null)
        speciesAbilityMap[1379] = arrayOf(281, null, null)
        speciesAbilityMap[1380] = arrayOf(281, null, null)
        speciesAbilityMap[1381] = arrayOf(281, null, null)
        speciesAbilityMap[1382] = arrayOf(282, null, null)
        speciesAbilityMap[1383] = arrayOf(282, null, null)
        speciesAbilityMap[1384] = arrayOf(282, null, null)
        speciesAbilityMap[1385] = arrayOf(282, null, null)
        speciesAbilityMap[1386] = arrayOf(282, null, null)
        speciesAbilityMap[1387] = arrayOf(282, null, null)
        speciesAbilityMap[1388] = arrayOf(270, null, 115)
        speciesAbilityMap[1389] = arrayOf(270, null, 115)
        speciesAbilityMap[1390] = arrayOf(270, null, 115)
        speciesAbilityMap[1391] = arrayOf(155, null, null)
        speciesAbilityMap[1392] = arrayOf(50, null, null)
        speciesAbilityMap[1393] = arrayOf(283, null, null)
        speciesAbilityMap[1394] = arrayOf(286, null, null)
        speciesAbilityMap[1395] = arrayOf(285, null, null)
        speciesAbilityMap[1396] = arrayOf(284, null, null)
        speciesAbilityMap[1397] = arrayOf(287, null, null)
        speciesAbilityMap[1398] = arrayOf(281, null, null)
        speciesAbilityMap[1399] = arrayOf(282, null, null)
        speciesAbilityMap[1400] = arrayOf(288, null, null)
        speciesAbilityMap[1401] = arrayOf(289, null, null)
        speciesAbilityMap[1402] = arrayOf(22, 83, 291)
        speciesAbilityMap[1403] = arrayOf(22, 83, 291)
        speciesAbilityMap[1404] = arrayOf(22, 83, 291)
        speciesAbilityMap[1405] = arrayOf(38, 11, 109)
        speciesAbilityMap[1406] = arrayOf(281, null, null)
        speciesAbilityMap[1407] = arrayOf(282, null, null)
        speciesAbilityMap[1408] = arrayOf(306, 82, 60)
        speciesAbilityMap[1409] = arrayOf(299, null, 85)
        speciesAbilityMap[1410] = arrayOf(299, null, 85)
        speciesAbilityMap[1411] = arrayOf(299, null, 85)
        speciesAbilityMap[1412] = arrayOf(299, null, 85)
        speciesAbilityMap[1413] = arrayOf(305, null, 275)
        speciesAbilityMap[1414] = arrayOf(305, null, 119)
        speciesAbilityMap[1415] = arrayOf(305, null, 101)
        speciesAbilityMap[1416] = arrayOf(128, null, null)
        speciesAbilityMap[1417] = arrayOf(11, null, null)
        speciesAbilityMap[1418] = arrayOf(104, null, null)
        speciesAbilityMap[1419] = arrayOf(5, null, null)
        speciesAbilityMap[1424] = arrayOf(300, null, null)
        speciesAbilityMap[1425] = arrayOf(192, 5, 242)
        speciesAbilityMap[1426] = arrayOf(306, 144, 60)
        speciesAbilityMap[1427] = arrayOf(281, null, null)
        speciesAbilityMap[1428] = arrayOf(281, null, null)
        speciesAbilityMap[1429] = arrayOf(282, null, null)
        speciesAbilityMap[1430] = arrayOf(282, null, null)
        speciesAbilityMap[1431] = arrayOf(307, null, null)
        speciesAbilityMap[1432] = arrayOf(308, null, null)
        speciesAbilityMap[1433] = arrayOf(309, null, null)
        speciesAbilityMap[1434] = arrayOf(310, null, null)
        speciesAbilityMap[1436] = arrayOf(68, null, 110)
        speciesAbilityMap[1437] = arrayOf(68, null, 110)
        speciesAbilityMap[1438] = arrayOf(19, 14, 132)
        speciesAbilityMap[1439] = arrayOf(19, 14, 132)
        speciesAbilityMap[1440] = arrayOf(19, 14, 132)
        speciesAbilityMap[1441] = arrayOf(19, 14, 132)
        speciesAbilityMap[1442] = arrayOf(19, 14, 132)
        speciesAbilityMap[1443] = arrayOf(19, 14, 132)
        speciesAbilityMap[1444] = arrayOf(19, 14, 132)
        speciesAbilityMap[1445] = arrayOf(19, 14, 132)
        speciesAbilityMap[1446] = arrayOf(19, 14, 132)
        speciesAbilityMap[1447] = arrayOf(19, 14, 132)
        speciesAbilityMap[1448] = arrayOf(19, 14, 132)
        speciesAbilityMap[1449] = arrayOf(19, 14, 132)
        speciesAbilityMap[1450] = arrayOf(19, 14, 132)
        speciesAbilityMap[1451] = arrayOf(19, 14, 132)
        speciesAbilityMap[1452] = arrayOf(19, 14, 132)
        speciesAbilityMap[1453] = arrayOf(19, 14, 132)
        speciesAbilityMap[1454] = arrayOf(19, 14, 132)
        speciesAbilityMap[1455] = arrayOf(19, 14, 132)
        speciesAbilityMap[1456] = arrayOf(19, 14, 132)
        speciesAbilityMap[1457] = arrayOf(61, null, 132)
        speciesAbilityMap[1458] = arrayOf(61, null, 132)
        speciesAbilityMap[1459] = arrayOf(61, null, 132)
        speciesAbilityMap[1460] = arrayOf(61, null, 132)
        speciesAbilityMap[1461] = arrayOf(61, null, 132)
        speciesAbilityMap[1462] = arrayOf(61, null, 132)
        speciesAbilityMap[1463] = arrayOf(61, null, 132)
        speciesAbilityMap[1464] = arrayOf(61, null, 132)
        speciesAbilityMap[1465] = arrayOf(61, null, 132)
        speciesAbilityMap[1466] = arrayOf(61, null, 132)
        speciesAbilityMap[1467] = arrayOf(61, null, 132)
        speciesAbilityMap[1468] = arrayOf(61, null, 132)
        speciesAbilityMap[1469] = arrayOf(61, null, 132)
        speciesAbilityMap[1470] = arrayOf(61, null, 132)
        speciesAbilityMap[1471] = arrayOf(61, null, 132)
        speciesAbilityMap[1472] = arrayOf(61, null, 132)
        speciesAbilityMap[1473] = arrayOf(61, null, 132)
        speciesAbilityMap[1474] = arrayOf(61, null, 132)
        speciesAbilityMap[1475] = arrayOf(61, null, 132)
        speciesAbilityMap[1476] = arrayOf(47, null, null)
        speciesAbilityMap[1477] = arrayOf(91, null, null)
        speciesAbilityMap[1478] = arrayOf(26, null, null)
        speciesAbilityMap[1479] = arrayOf(102, null, null)
        speciesAbilityMap[1480] = arrayOf(212, null, null)
        speciesAbilityMap[1481] = arrayOf(209, null, null)
        speciesAbilityMap[1482] = arrayOf(142, null, null)
        speciesAbilityMap[1483] = arrayOf(69, null, null)
        speciesAbilityMap[1484] = arrayOf(175, null, null)
        speciesAbilityMap[1485] = arrayOf(199, null, null)
        speciesAbilityMap[1486] = arrayOf(5, null, null)
        speciesAbilityMap[1487] = arrayOf(9, null, 31)
        speciesAbilityMap[1488] = arrayOf(50, 91, 107)
        speciesAbilityMap[1523] = arrayOf(209, null, null)
    }

    private fun registerMoveChunk1() {
        registerMove(1, "Pound", PokemonType.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35)
        registerMove(2, "Karate Chop", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 50, 100, 25)
        registerMove(3, "Double Slap", PokemonType.NORMAL, MoveCategory.PHYSICAL, 15, 85, 10)
        registerMove(4, "Comet Punch", PokemonType.NORMAL, MoveCategory.PHYSICAL, 18, 85, 15)
        registerMove(5, "Mega Punch", PokemonType.NORMAL, MoveCategory.PHYSICAL, 80, 85, 20)
        registerMove(6, "Pay Day", PokemonType.NORMAL, MoveCategory.PHYSICAL, 40, 100, 20)
        registerMove(7, "Fire Punch", PokemonType.FIRE, MoveCategory.PHYSICAL, 75, 100, 15)
        registerMove(8, "Ice Punch", PokemonType.ICE, MoveCategory.PHYSICAL, 75, 100, 15)
        registerMove(9, "Thunder Punch", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 75, 100, 15)
        registerMove(10, "Scratch", PokemonType.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35)
        registerMove(11, "Vise Grip", PokemonType.NORMAL, MoveCategory.PHYSICAL, 55, 100, 30)
        registerMove(12, "Guillotine", PokemonType.NORMAL, MoveCategory.PHYSICAL, 1, 30, 5)
        registerMove(13, "Razor Wind", PokemonType.NORMAL, MoveCategory.SPECIAL, 80, 100, 10)
        registerMove(14, "Swords Dance", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 20)
        registerMove(15, "Cut", PokemonType.NORMAL, MoveCategory.PHYSICAL, 50, 95, 30)
        registerMove(16, "Gust", PokemonType.FLYING, MoveCategory.SPECIAL, 40, 100, 35)
        registerMove(17, "Wing Attack", PokemonType.FLYING, MoveCategory.PHYSICAL, 60, 100, 35)
        registerMove(18, "Whirlwind", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 20)
        registerMove(19, "Fly", PokemonType.FLYING, MoveCategory.PHYSICAL, 90, 95, 15)
        registerMove(20, "Bind", PokemonType.NORMAL, MoveCategory.PHYSICAL, 15, 85, 20)
        registerMove(21, "Slam", PokemonType.NORMAL, MoveCategory.PHYSICAL, 80, 75, 20)
        registerMove(22, "Vine Whip", PokemonType.GRASS, MoveCategory.PHYSICAL, 45, 100, 25)
        registerMove(23, "Stomp", PokemonType.NORMAL, MoveCategory.PHYSICAL, 65, 100, 20)
        registerMove(24, "Double Kick", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 30, 100, 30)
        registerMove(25, "Mega Kick", PokemonType.NORMAL, MoveCategory.PHYSICAL, 120, 75, 5)
        registerMove(26, "Jump Kick", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 100, 95, 10)
        registerMove(27, "Rolling Kick", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 60, 85, 15)
        registerMove(28, "Sand Attack", PokemonType.GROUND, MoveCategory.STATUS, 0, 100, 15)
        registerMove(29, "Headbutt", PokemonType.NORMAL, MoveCategory.PHYSICAL, 70, 100, 15)
        registerMove(30, "Horn Attack", PokemonType.NORMAL, MoveCategory.PHYSICAL, 65, 100, 25)
        registerMove(31, "Fury Attack", PokemonType.NORMAL, MoveCategory.PHYSICAL, 15, 85, 20)
        registerMove(32, "Horn Drill", PokemonType.NORMAL, MoveCategory.PHYSICAL, 1, 30, 5)
        registerMove(33, "Tackle", PokemonType.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35)
        registerMove(34, "Body Slam", PokemonType.NORMAL, MoveCategory.PHYSICAL, 85, 100, 15)
        registerMove(35, "Wrap", PokemonType.NORMAL, MoveCategory.PHYSICAL, 15, 90, 20)
        registerMove(36, "Take Down", PokemonType.NORMAL, MoveCategory.PHYSICAL, 90, 85, 20)
        registerMove(37, "Thrash", PokemonType.NORMAL, MoveCategory.PHYSICAL, 120, 100, 10)
        registerMove(38, "Double-Edge", PokemonType.NORMAL, MoveCategory.PHYSICAL, 120, 100, 15)
        registerMove(39, "Tail Whip", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 30)
        registerMove(40, "Poison Sting", PokemonType.POISON, MoveCategory.PHYSICAL, 30, 100, 35)
        registerMove(41, "Twineedle", PokemonType.BUG, MoveCategory.PHYSICAL, 25, 100, 20)
        registerMove(42, "Pin Missile", PokemonType.BUG, MoveCategory.PHYSICAL, 25, 95, 20)
        registerMove(43, "Leer", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 30)
        registerMove(44, "Bite", PokemonType.DARK, MoveCategory.PHYSICAL, 60, 100, 25)
        registerMove(45, "Growl", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 40)
        registerMove(46, "Roar", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 20)
        registerMove(47, "Sing", PokemonType.NORMAL, MoveCategory.STATUS, 0, 55, 15)
        registerMove(48, "Supersonic", PokemonType.NORMAL, MoveCategory.STATUS, 0, 55, 20)
        registerMove(49, "Sonic Boom", PokemonType.NORMAL, MoveCategory.SPECIAL, 1, 90, 20)
        registerMove(50, "Disable", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 20)
        registerMove(51, "Acid", PokemonType.POISON, MoveCategory.SPECIAL, 40, 100, 30)
        registerMove(52, "Ember", PokemonType.FIRE, MoveCategory.SPECIAL, 40, 100, 25)
        registerMove(53, "Flamethrower", PokemonType.FIRE, MoveCategory.SPECIAL, 90, 100, 15)
        registerMove(54, "Mist", PokemonType.ICE, MoveCategory.STATUS, 0, 0, 30)
        registerMove(55, "Water Gun", PokemonType.WATER, MoveCategory.SPECIAL, 40, 100, 25)
        registerMove(56, "Hydro Pump", PokemonType.WATER, MoveCategory.SPECIAL, 110, 80, 5)
        registerMove(57, "Surf", PokemonType.WATER, MoveCategory.SPECIAL, 90, 100, 15)
        registerMove(58, "Ice Beam", PokemonType.ICE, MoveCategory.SPECIAL, 90, 100, 10)
        registerMove(59, "Blizzard", PokemonType.ICE, MoveCategory.SPECIAL, 110, 70, 5)
        registerMove(60, "Psybeam", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 65, 100, 20)
        registerMove(61, "Bubble Beam", PokemonType.WATER, MoveCategory.SPECIAL, 65, 100, 20)
        registerMove(62, "Aurora Beam", PokemonType.ICE, MoveCategory.SPECIAL, 65, 100, 20)
        registerMove(63, "Hyper Beam", PokemonType.NORMAL, MoveCategory.SPECIAL, 150, 90, 5)
        registerMove(64, "Peck", PokemonType.FLYING, MoveCategory.PHYSICAL, 35, 100, 35)
        registerMove(65, "Drill Peck", PokemonType.FLYING, MoveCategory.PHYSICAL, 80, 100, 20)
        registerMove(66, "Submission", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 80, 80, 20)
        registerMove(67, "Low Kick", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 1, 100, 20)
        registerMove(68, "Counter", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 1, 100, 20)
        registerMove(69, "Seismic Toss", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 1, 100, 20)
        registerMove(70, "Strength", PokemonType.NORMAL, MoveCategory.PHYSICAL, 80, 100, 15)
        registerMove(71, "Absorb", PokemonType.GRASS, MoveCategory.SPECIAL, 20, 100, 25)
        registerMove(72, "Mega Drain", PokemonType.GRASS, MoveCategory.SPECIAL, 40, 100, 25)
        registerMove(73, "Leech Seed", PokemonType.GRASS, MoveCategory.STATUS, 0, 90, 10)
        registerMove(74, "Growth", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 20)
        registerMove(75, "Razor Leaf", PokemonType.GRASS, MoveCategory.PHYSICAL, 55, 95, 25)
        registerMove(76, "Solar Beam", PokemonType.GRASS, MoveCategory.SPECIAL, 120, 100, 10)
        registerMove(77, "Poison Powder", PokemonType.POISON, MoveCategory.STATUS, 0, 75, 35)
        registerMove(78, "Stun Spore", PokemonType.GRASS, MoveCategory.STATUS, 0, 75, 30)
        registerMove(79, "Sleep Powder", PokemonType.GRASS, MoveCategory.STATUS, 0, 75, 15)
        registerMove(80, "Petal Dance", PokemonType.GRASS, MoveCategory.SPECIAL, 120, 100, 10)
        registerMove(81, "String Shot", PokemonType.BUG, MoveCategory.STATUS, 0, 95, 40)
        registerMove(82, "Dragon Rage", PokemonType.DRAGON, MoveCategory.SPECIAL, 1, 100, 10)
        registerMove(83, "Fire Spin", PokemonType.FIRE, MoveCategory.SPECIAL, 35, 85, 15)
        registerMove(84, "Thunder Shock", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 40, 100, 30)
        registerMove(85, "Thunderbolt", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 90, 100, 15)
        registerMove(86, "Thunder Wave", PokemonType.ELECTRIC, MoveCategory.STATUS, 0, 90, 20)
        registerMove(87, "Thunder", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 110, 70, 10)
        registerMove(88, "Rock Throw", PokemonType.ROCK, MoveCategory.PHYSICAL, 50, 90, 15)
        registerMove(89, "Earthquake", PokemonType.GROUND, MoveCategory.PHYSICAL, 100, 100, 10)
        registerMove(90, "Fissure", PokemonType.GROUND, MoveCategory.PHYSICAL, 1, 30, 5)
        registerMove(91, "Dig", PokemonType.GROUND, MoveCategory.PHYSICAL, 80, 100, 10)
        registerMove(92, "Toxic", PokemonType.POISON, MoveCategory.STATUS, 0, 90, 10)
        registerMove(93, "Confusion", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 50, 100, 25)
        registerMove(94, "Psychic", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 90, 100, 10)
        registerMove(95, "Hypnosis", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 60, 20)
        registerMove(96, "Meditate", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 40)
        registerMove(97, "Agility", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 30)
        registerMove(98, "Quick Attack", PokemonType.NORMAL, MoveCategory.PHYSICAL, 40, 100, 30)
        registerMove(99, "Rage", PokemonType.NORMAL, MoveCategory.PHYSICAL, 20, 100, 20)
        registerMove(100, "Teleport", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 20)
        registerMove(101, "Night Shade", PokemonType.GHOST, MoveCategory.SPECIAL, 1, 100, 15)
        registerMove(102, "Mimic", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(103, "Screech", PokemonType.NORMAL, MoveCategory.STATUS, 0, 85, 40)
        registerMove(104, "Double Team", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 15)
        registerMove(105, "Recover", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 5)
        registerMove(106, "Harden", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 30)
        registerMove(107, "Minimize", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(108, "Smokescreen", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 20)
        registerMove(109, "Confuse Ray", PokemonType.GHOST, MoveCategory.STATUS, 0, 100, 10)
        registerMove(110, "Withdraw", PokemonType.WATER, MoveCategory.STATUS, 0, 0, 40)
        registerMove(111, "Defense Curl", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 40)
        registerMove(112, "Barrier", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 20)
        registerMove(113, "Light Screen", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 30)
        registerMove(114, "Haze", PokemonType.ICE, MoveCategory.STATUS, 0, 0, 30)
        registerMove(115, "Reflect", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 20)
        registerMove(116, "Focus Energy", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 30)
        registerMove(117, "Bide", PokemonType.NORMAL, MoveCategory.PHYSICAL, 1, 0, 10)
        registerMove(118, "Metronome", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(119, "Mirror Move", PokemonType.FLYING, MoveCategory.STATUS, 0, 0, 20)
        registerMove(120, "Self-Destruct", PokemonType.NORMAL, MoveCategory.PHYSICAL, 200, 100, 5)
        registerMove(121, "Egg Bomb", PokemonType.NORMAL, MoveCategory.PHYSICAL, 100, 75, 10)
        registerMove(122, "Lick", PokemonType.GHOST, MoveCategory.PHYSICAL, 30, 100, 30)
        registerMove(123, "Smog", PokemonType.POISON, MoveCategory.SPECIAL, 30, 70, 20)
        registerMove(124, "Sludge", PokemonType.POISON, MoveCategory.SPECIAL, 65, 100, 20)
        registerMove(125, "Bone Club", PokemonType.GROUND, MoveCategory.PHYSICAL, 65, 85, 20)
        registerMove(126, "Fire Blast", PokemonType.FIRE, MoveCategory.SPECIAL, 110, 85, 5)
        registerMove(127, "Waterfall", PokemonType.WATER, MoveCategory.PHYSICAL, 80, 100, 15)
        registerMove(128, "Clamp", PokemonType.WATER, MoveCategory.PHYSICAL, 35, 85, 15)
        registerMove(129, "Swift", PokemonType.NORMAL, MoveCategory.SPECIAL, 60, 0, 20)
        registerMove(130, "Skull Bash", PokemonType.NORMAL, MoveCategory.PHYSICAL, 130, 100, 10)
        registerMove(131, "Spike Cannon", PokemonType.NORMAL, MoveCategory.PHYSICAL, 20, 100, 15)
        registerMove(132, "Constrict", PokemonType.NORMAL, MoveCategory.PHYSICAL, 10, 100, 35)
        registerMove(133, "Amnesia", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 20)
        registerMove(134, "Kinesis", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 80, 15)
        registerMove(135, "Soft-Boiled", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 5)
        registerMove(136, "High Jump Kick", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 130, 90, 10)
        registerMove(137, "Glare", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 30)
        registerMove(138, "Dream Eater", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 100, 100, 15)
        registerMove(139, "Poison Gas", PokemonType.POISON, MoveCategory.STATUS, 0, 90, 40)
        registerMove(140, "Barrage", PokemonType.NORMAL, MoveCategory.PHYSICAL, 15, 85, 20)
        registerMove(141, "Leech Life", PokemonType.BUG, MoveCategory.PHYSICAL, 60, 100, 10)
        registerMove(142, "Lovely Kiss", PokemonType.NORMAL, MoveCategory.STATUS, 0, 75, 10)
        registerMove(143, "Sky Attack", PokemonType.FLYING, MoveCategory.PHYSICAL, 140, 90, 5)
        registerMove(144, "Transform", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(145, "Bubble", PokemonType.WATER, MoveCategory.SPECIAL, 40, 100, 30)
        registerMove(146, "Dizzy Punch", PokemonType.NORMAL, MoveCategory.PHYSICAL, 70, 100, 10)
        registerMove(147, "Spore", PokemonType.GRASS, MoveCategory.STATUS, 0, 100, 15)
        registerMove(148, "Flash", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 20)
        registerMove(149, "Psywave", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 1, 100, 15)
        registerMove(150, "Splash", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 40)
        registerMove(151, "Acid Armor", PokemonType.POISON, MoveCategory.STATUS, 0, 0, 20)
        registerMove(152, "Crabhammer", PokemonType.WATER, MoveCategory.PHYSICAL, 100, 90, 10)
        registerMove(153, "Explosion", PokemonType.NORMAL, MoveCategory.PHYSICAL, 250, 100, 5)
        registerMove(154, "Fury Swipes", PokemonType.NORMAL, MoveCategory.PHYSICAL, 18, 80, 15)
        registerMove(155, "Bonemerang", PokemonType.GROUND, MoveCategory.PHYSICAL, 50, 90, 10)
        registerMove(156, "Rest", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 5)
        registerMove(157, "Rock Slide", PokemonType.ROCK, MoveCategory.PHYSICAL, 75, 90, 10)
        registerMove(158, "Hyper Fang", PokemonType.NORMAL, MoveCategory.PHYSICAL, 80, 90, 15)
        registerMove(159, "Sharpen", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 30)
        registerMove(160, "Conversion", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 30)
        registerMove(161, "Tri Attack", PokemonType.NORMAL, MoveCategory.SPECIAL, 80, 100, 10)
        registerMove(162, "Super Fang", PokemonType.NORMAL, MoveCategory.PHYSICAL, 1, 90, 10)
        registerMove(163, "Slash", PokemonType.NORMAL, MoveCategory.PHYSICAL, 70, 100, 20)
        registerMove(164, "Substitute", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(165, "Struggle", PokemonType.NORMAL, MoveCategory.PHYSICAL, 50, 0, 1)
        registerMove(166, "Sketch", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 1)
        registerMove(167, "Triple Kick", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 10, 90, 10)
        registerMove(168, "Thief", PokemonType.DARK, MoveCategory.PHYSICAL, 60, 100, 25)
        registerMove(169, "Spider Web", PokemonType.BUG, MoveCategory.STATUS, 0, 0, 10)
        registerMove(170, "Mind Reader", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 5)
        registerMove(171, "Nightmare", PokemonType.GHOST, MoveCategory.STATUS, 0, 100, 15)
        registerMove(172, "Flame Wheel", PokemonType.FIRE, MoveCategory.PHYSICAL, 60, 100, 25)
        registerMove(173, "Snore", PokemonType.NORMAL, MoveCategory.SPECIAL, 50, 100, 15)
        registerMove(174, "Curse", PokemonType.GHOST, MoveCategory.STATUS, 0, 0, 10)
        registerMove(175, "Flail", PokemonType.NORMAL, MoveCategory.PHYSICAL, 1, 100, 15)
        registerMove(176, "Conversion 2", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 30)
        registerMove(177, "Aeroblast", PokemonType.FLYING, MoveCategory.SPECIAL, 100, 95, 5)
        registerMove(178, "Cotton Spore", PokemonType.GRASS, MoveCategory.STATUS, 0, 100, 40)
        registerMove(179, "Reversal", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 1, 100, 15)
        registerMove(180, "Spite", PokemonType.GHOST, MoveCategory.STATUS, 0, 100, 10)
        registerMove(181, "Powder Snow", PokemonType.ICE, MoveCategory.SPECIAL, 40, 100, 25)
        registerMove(182, "Protect", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(183, "Mach Punch", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 40, 100, 30)
        registerMove(184, "Scary Face", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 10)
        registerMove(185, "Feint Attack", PokemonType.DARK, MoveCategory.PHYSICAL, 60, 0, 20)
        registerMove(186, "Sweet Kiss", PokemonType.FAIRY, MoveCategory.STATUS, 0, 75, 10)
        registerMove(187, "Belly Drum", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(188, "Sludge Bomb", PokemonType.POISON, MoveCategory.SPECIAL, 90, 100, 10)
        registerMove(189, "Mud-Slap", PokemonType.GROUND, MoveCategory.SPECIAL, 20, 100, 10)
        registerMove(190, "Octazooka", PokemonType.WATER, MoveCategory.SPECIAL, 65, 85, 10)
        registerMove(191, "Spikes", PokemonType.GROUND, MoveCategory.STATUS, 0, 0, 20)
        registerMove(192, "Zap Cannon", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 120, 50, 5)
        registerMove(193, "Foresight", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 40)
        registerMove(194, "Destiny Bond", PokemonType.GHOST, MoveCategory.STATUS, 0, 0, 5)
        registerMove(195, "Perish Song", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 5)
        registerMove(196, "Icy Wind", PokemonType.ICE, MoveCategory.SPECIAL, 55, 95, 15)
        registerMove(197, "Detect", PokemonType.FIGHTING, MoveCategory.STATUS, 0, 0, 5)
        registerMove(198, "Bone Rush", PokemonType.GROUND, MoveCategory.PHYSICAL, 25, 90, 10)
        registerMove(199, "Lock-On", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 5)
        registerMove(200, "Outrage", PokemonType.DRAGON, MoveCategory.PHYSICAL, 120, 100, 10)
    }

    private fun registerMoveChunk2() {
        registerMove(201, "Sandstorm", PokemonType.ROCK, MoveCategory.STATUS, 0, 0, 10)
        registerMove(202, "Giga Drain", PokemonType.GRASS, MoveCategory.SPECIAL, 75, 100, 25)
        registerMove(203, "Endure", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(204, "Charm", PokemonType.FAIRY, MoveCategory.STATUS, 0, 100, 20)
        registerMove(205, "Rollout", PokemonType.ROCK, MoveCategory.PHYSICAL, 30, 90, 20)
        registerMove(206, "False Swipe", PokemonType.NORMAL, MoveCategory.PHYSICAL, 40, 100, 40)
        registerMove(207, "Swagger", PokemonType.NORMAL, MoveCategory.STATUS, 0, 85, 15)
        registerMove(208, "Milk Drink", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 5)
        registerMove(209, "Spark", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 65, 100, 20)
        registerMove(210, "Fury Cutter", PokemonType.BUG, MoveCategory.PHYSICAL, 40, 95, 20)
        registerMove(211, "Steel Wing", PokemonType.STEEL, MoveCategory.PHYSICAL, 70, 90, 25)
        registerMove(212, "Mean Look", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 5)
        registerMove(213, "Attract", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 15)
        registerMove(214, "Sleep Talk", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(215, "Heal Bell", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 5)
        registerMove(216, "Return", PokemonType.NORMAL, MoveCategory.PHYSICAL, 1, 100, 20)
        registerMove(217, "Present", PokemonType.NORMAL, MoveCategory.PHYSICAL, 1, 90, 15)
        registerMove(218, "Frustration", PokemonType.NORMAL, MoveCategory.PHYSICAL, 1, 100, 20)
        registerMove(219, "Safeguard", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 25)
        registerMove(220, "Pain Split", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 20)
        registerMove(221, "Sacred Fire", PokemonType.FIRE, MoveCategory.PHYSICAL, 100, 95, 5)
        registerMove(222, "Magnitude", PokemonType.GROUND, MoveCategory.PHYSICAL, 1, 100, 30)
        registerMove(223, "Dynamic Punch", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 100, 50, 5)
        registerMove(224, "Megahorn", PokemonType.BUG, MoveCategory.PHYSICAL, 120, 85, 10)
        registerMove(225, "Dragon Breath", PokemonType.DRAGON, MoveCategory.SPECIAL, 60, 100, 20)
        registerMove(226, "Baton Pass", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 40)
        registerMove(227, "Encore", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 5)
        registerMove(228, "Pursuit", PokemonType.DARK, MoveCategory.PHYSICAL, 40, 100, 20)
        registerMove(229, "Rapid Spin", PokemonType.NORMAL, MoveCategory.PHYSICAL, 50, 100, 40)
        registerMove(230, "Sweet Scent", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 20)
        registerMove(231, "Iron Tail", PokemonType.STEEL, MoveCategory.PHYSICAL, 100, 75, 15)
        registerMove(232, "Metal Claw", PokemonType.STEEL, MoveCategory.PHYSICAL, 50, 95, 35)
        registerMove(233, "Vital Throw", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 70, 0, 10)
        registerMove(234, "Morning Sun", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 5)
        registerMove(235, "Synthesis", PokemonType.GRASS, MoveCategory.STATUS, 0, 0, 5)
        registerMove(236, "Moonlight", PokemonType.FAIRY, MoveCategory.STATUS, 0, 0, 5)
        registerMove(237, "Hidden Power", PokemonType.NORMAL, MoveCategory.SPECIAL, 60, 100, 15)
        registerMove(238, "Cross Chop", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 100, 80, 5)
        registerMove(239, "Twister", PokemonType.DRAGON, MoveCategory.SPECIAL, 40, 100, 20)
        registerMove(240, "Rain Dance", PokemonType.WATER, MoveCategory.STATUS, 0, 0, 5)
        registerMove(241, "Sunny Day", PokemonType.FIRE, MoveCategory.STATUS, 0, 0, 5)
        registerMove(242, "Crunch", PokemonType.DARK, MoveCategory.PHYSICAL, 80, 100, 15)
        registerMove(243, "Mirror Coat", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 1, 100, 20)
        registerMove(244, "Psych Up", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(245, "Extreme Speed", PokemonType.NORMAL, MoveCategory.PHYSICAL, 80, 100, 5)
        registerMove(246, "Ancient Power", PokemonType.ROCK, MoveCategory.SPECIAL, 60, 100, 5)
        registerMove(247, "Shadow Ball", PokemonType.GHOST, MoveCategory.SPECIAL, 80, 100, 15)
        registerMove(248, "Future Sight", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 120, 100, 10)
        registerMove(249, "Rock Smash", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 40, 100, 15)
        registerMove(250, "Whirlpool", PokemonType.WATER, MoveCategory.SPECIAL, 35, 85, 15)
        registerMove(251, "Beat Up", PokemonType.DARK, MoveCategory.PHYSICAL, 1, 100, 10)
        registerMove(252, "Fake Out", PokemonType.NORMAL, MoveCategory.PHYSICAL, 40, 100, 10)
        registerMove(253, "Uproar", PokemonType.NORMAL, MoveCategory.SPECIAL, 90, 100, 10)
        registerMove(254, "Stockpile", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 20)
        registerMove(255, "Spit Up", PokemonType.NORMAL, MoveCategory.SPECIAL, 1, 100, 10)
        registerMove(256, "Swallow", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(257, "Heat Wave", PokemonType.FIRE, MoveCategory.SPECIAL, 95, 90, 10)
        registerMove(258, "Hail", PokemonType.ICE, MoveCategory.STATUS, 0, 0, 10)
        registerMove(259, "Torment", PokemonType.DARK, MoveCategory.STATUS, 0, 100, 15)
        registerMove(260, "Flatter", PokemonType.DARK, MoveCategory.STATUS, 0, 100, 15)
        registerMove(261, "Will-O-Wisp", PokemonType.FIRE, MoveCategory.STATUS, 0, 85, 15)
        registerMove(262, "Memento", PokemonType.DARK, MoveCategory.STATUS, 0, 100, 10)
        registerMove(263, "Facade", PokemonType.NORMAL, MoveCategory.PHYSICAL, 70, 100, 20)
        registerMove(264, "Focus Punch", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 150, 100, 20)
        registerMove(265, "Smelling Salts", PokemonType.NORMAL, MoveCategory.PHYSICAL, 70, 100, 10)
        registerMove(266, "Follow Me", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 20)
        registerMove(267, "Nature Power", PokemonType.NORMAL, MoveCategory.STATUS, 1, 0, 20)
        registerMove(268, "Charge", PokemonType.ELECTRIC, MoveCategory.STATUS, 0, 0, 20)
        registerMove(269, "Taunt", PokemonType.DARK, MoveCategory.STATUS, 0, 100, 20)
        registerMove(270, "Helping Hand", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 20)
        registerMove(271, "Trick", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 100, 10)
        registerMove(272, "Role Play", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(273, "Wish", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(274, "Assist", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 20)
        registerMove(275, "Ingrain", PokemonType.GRASS, MoveCategory.STATUS, 0, 0, 20)
        registerMove(276, "Superpower", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 120, 100, 5)
        registerMove(277, "Magic Coat", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 15)
        registerMove(278, "Recycle", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(279, "Revenge", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 60, 100, 10)
        registerMove(280, "Brick Break", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 75, 100, 15)
        registerMove(281, "Yawn", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(282, "Knock Off", PokemonType.DARK, MoveCategory.PHYSICAL, 65, 100, 20)
        registerMove(283, "Endeavor", PokemonType.NORMAL, MoveCategory.PHYSICAL, 1, 100, 5)
        registerMove(284, "Eruption", PokemonType.FIRE, MoveCategory.SPECIAL, 150, 100, 5)
        registerMove(285, "Skill Swap", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(286, "Imprison", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(287, "Refresh", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 20)
        registerMove(288, "Grudge", PokemonType.GHOST, MoveCategory.STATUS, 0, 0, 5)
        registerMove(289, "Snatch", PokemonType.DARK, MoveCategory.STATUS, 0, 0, 10)
        registerMove(290, "Secret Power", PokemonType.NORMAL, MoveCategory.PHYSICAL, 70, 100, 20)
        registerMove(291, "Dive", PokemonType.WATER, MoveCategory.PHYSICAL, 80, 100, 10)
        registerMove(292, "Arm Thrust", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 15, 100, 20)
        registerMove(293, "Camouflage", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 20)
        registerMove(294, "Tail Glow", PokemonType.BUG, MoveCategory.STATUS, 0, 0, 20)
        registerMove(295, "Luster Purge", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 95, 100, 5)
        registerMove(296, "Mist Ball", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 95, 100, 5)
        registerMove(297, "Feather Dance", PokemonType.FLYING, MoveCategory.STATUS, 0, 100, 15)
        registerMove(298, "Teeter Dance", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 20)
        registerMove(299, "Blaze Kick", PokemonType.FIRE, MoveCategory.PHYSICAL, 85, 90, 10)
        registerMove(300, "Mud Sport", PokemonType.GROUND, MoveCategory.STATUS, 0, 0, 15)
        registerMove(301, "Ice Ball", PokemonType.ICE, MoveCategory.PHYSICAL, 30, 90, 20)
        registerMove(302, "Needle Arm", PokemonType.GRASS, MoveCategory.PHYSICAL, 60, 100, 15)
        registerMove(303, "Slack Off", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 5)
        registerMove(304, "Hyper Voice", PokemonType.NORMAL, MoveCategory.SPECIAL, 90, 100, 10)
        registerMove(305, "Poison Fang", PokemonType.POISON, MoveCategory.PHYSICAL, 50, 100, 15)
        registerMove(306, "Crush Claw", PokemonType.NORMAL, MoveCategory.PHYSICAL, 75, 95, 10)
        registerMove(307, "Blast Burn", PokemonType.FIRE, MoveCategory.SPECIAL, 150, 90, 5)
        registerMove(308, "Hydro Cannon", PokemonType.WATER, MoveCategory.SPECIAL, 150, 90, 5)
        registerMove(309, "Meteor Mash", PokemonType.STEEL, MoveCategory.PHYSICAL, 90, 90, 10)
        registerMove(310, "Astonish", PokemonType.GHOST, MoveCategory.PHYSICAL, 30, 100, 15)
        registerMove(311, "Weather Ball", PokemonType.NORMAL, MoveCategory.SPECIAL, 50, 100, 10)
        registerMove(312, "Aromatherapy", PokemonType.GRASS, MoveCategory.STATUS, 0, 0, 5)
        registerMove(313, "Fake Tears", PokemonType.DARK, MoveCategory.STATUS, 0, 100, 20)
        registerMove(314, "Air Cutter", PokemonType.FLYING, MoveCategory.SPECIAL, 60, 95, 25)
        registerMove(315, "Overheat", PokemonType.FIRE, MoveCategory.SPECIAL, 130, 90, 5)
        registerMove(316, "Odor Sleuth", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 40)
        registerMove(317, "Rock Tomb", PokemonType.ROCK, MoveCategory.PHYSICAL, 60, 95, 15)
        registerMove(318, "Silver Wind", PokemonType.BUG, MoveCategory.SPECIAL, 60, 100, 5)
        registerMove(319, "Metal Sound", PokemonType.STEEL, MoveCategory.STATUS, 0, 85, 40)
        registerMove(320, "Grass Whistle", PokemonType.GRASS, MoveCategory.STATUS, 0, 55, 15)
        registerMove(321, "Tickle", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 20)
        registerMove(322, "Cosmic Power", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 20)
        registerMove(323, "Water Spout", PokemonType.WATER, MoveCategory.SPECIAL, 150, 100, 5)
        registerMove(324, "Signal Beam", PokemonType.BUG, MoveCategory.SPECIAL, 75, 100, 15)
        registerMove(325, "Shadow Punch", PokemonType.GHOST, MoveCategory.PHYSICAL, 60, 0, 20)
        registerMove(326, "Extrasensory", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 80, 100, 20)
        registerMove(327, "Sky Uppercut", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 85, 90, 15)
        registerMove(328, "Sand Tomb", PokemonType.GROUND, MoveCategory.PHYSICAL, 35, 85, 15)
        registerMove(329, "Sheer Cold", PokemonType.ICE, MoveCategory.SPECIAL, 1, 30, 5)
        registerMove(330, "Muddy Water", PokemonType.WATER, MoveCategory.SPECIAL, 90, 85, 10)
        registerMove(331, "Bullet Seed", PokemonType.GRASS, MoveCategory.PHYSICAL, 25, 100, 30)
        registerMove(332, "Aerial Ace", PokemonType.FLYING, MoveCategory.PHYSICAL, 60, 0, 20)
        registerMove(333, "Icicle Spear", PokemonType.ICE, MoveCategory.PHYSICAL, 25, 100, 30)
        registerMove(334, "Iron Defense", PokemonType.STEEL, MoveCategory.STATUS, 0, 0, 15)
        registerMove(335, "Block", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 5)
        registerMove(336, "Howl", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 40)
        registerMove(337, "Dragon Claw", PokemonType.DRAGON, MoveCategory.PHYSICAL, 80, 100, 15)
        registerMove(338, "Frenzy Plant", PokemonType.GRASS, MoveCategory.SPECIAL, 150, 90, 5)
        registerMove(339, "Bulk Up", PokemonType.FIGHTING, MoveCategory.STATUS, 0, 0, 20)
        registerMove(340, "Bounce", PokemonType.FLYING, MoveCategory.PHYSICAL, 85, 85, 5)
        registerMove(341, "Mud Shot", PokemonType.GROUND, MoveCategory.SPECIAL, 55, 95, 15)
        registerMove(342, "Poison Tail", PokemonType.POISON, MoveCategory.PHYSICAL, 50, 100, 25)
        registerMove(343, "Covet", PokemonType.NORMAL, MoveCategory.PHYSICAL, 60, 100, 25)
        registerMove(344, "Volt Tackle", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 120, 100, 15)
        registerMove(345, "Magical Leaf", PokemonType.GRASS, MoveCategory.SPECIAL, 60, 0, 20)
        registerMove(346, "Water Sport", PokemonType.WATER, MoveCategory.STATUS, 0, 0, 15)
        registerMove(347, "Calm Mind", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 20)
        registerMove(348, "Leaf Blade", PokemonType.GRASS, MoveCategory.PHYSICAL, 90, 100, 15)
        registerMove(349, "Dragon Dance", PokemonType.DRAGON, MoveCategory.STATUS, 0, 0, 20)
        registerMove(350, "Rock Blast", PokemonType.ROCK, MoveCategory.PHYSICAL, 25, 90, 10)
        registerMove(351, "Shock Wave", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 60, 0, 20)
        registerMove(352, "Water Pulse", PokemonType.WATER, MoveCategory.SPECIAL, 60, 100, 20)
        registerMove(353, "Doom Desire", PokemonType.STEEL, MoveCategory.SPECIAL, 140, 100, 5)
        registerMove(354, "Psycho Boost", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 140, 90, 5)
        registerMove(355, "Roost", PokemonType.FLYING, MoveCategory.STATUS, 0, 0, 5)
        registerMove(356, "Gravity", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 5)
        registerMove(357, "Miracle Eye", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 40)
        registerMove(358, "Wake-Up Slap", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 70, 100, 10)
        registerMove(359, "Hammer Arm", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 100, 90, 10)
        registerMove(360, "Gyro Ball", PokemonType.STEEL, MoveCategory.PHYSICAL, 1, 100, 5)
        registerMove(361, "Healing Wish", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(362, "Brine", PokemonType.WATER, MoveCategory.SPECIAL, 65, 100, 10)
        registerMove(363, "Natural Gift", PokemonType.NORMAL, MoveCategory.PHYSICAL, 1, 100, 15)
        registerMove(364, "Feint", PokemonType.NORMAL, MoveCategory.PHYSICAL, 30, 100, 10)
        registerMove(365, "Pluck", PokemonType.FLYING, MoveCategory.PHYSICAL, 60, 100, 20)
        registerMove(366, "Tailwind", PokemonType.FLYING, MoveCategory.STATUS, 0, 0, 15)
        registerMove(367, "Acupressure", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 30)
        registerMove(368, "Metal Burst", PokemonType.STEEL, MoveCategory.PHYSICAL, 1, 100, 10)
        registerMove(369, "U-Turn", PokemonType.BUG, MoveCategory.PHYSICAL, 70, 100, 20)
        registerMove(370, "Close Combat", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 120, 100, 5)
        registerMove(371, "Payback", PokemonType.DARK, MoveCategory.PHYSICAL, 50, 100, 10)
        registerMove(372, "Assurance", PokemonType.DARK, MoveCategory.PHYSICAL, 60, 100, 10)
        registerMove(373, "Embargo", PokemonType.DARK, MoveCategory.STATUS, 0, 100, 15)
        registerMove(374, "Fling", PokemonType.DARK, MoveCategory.PHYSICAL, 1, 100, 10)
        registerMove(375, "Psycho Shift", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 100, 10)
        registerMove(376, "Trump Card", PokemonType.NORMAL, MoveCategory.SPECIAL, 1, 0, 5)
        registerMove(377, "Heal Block", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 100, 15)
        registerMove(378, "Wring Out", PokemonType.NORMAL, MoveCategory.SPECIAL, 120, 100, 5)
        registerMove(379, "Power Trick", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(380, "Gastro Acid", PokemonType.POISON, MoveCategory.STATUS, 0, 100, 10)
        registerMove(381, "Lucky Chant", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 30)
        registerMove(382, "Me First", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 20)
        registerMove(383, "Copycat", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 20)
        registerMove(384, "Power Swap", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(385, "Guard Swap", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(386, "Punishment", PokemonType.DARK, MoveCategory.PHYSICAL, 60, 100, 5)
        registerMove(387, "Last Resort", PokemonType.NORMAL, MoveCategory.PHYSICAL, 140, 100, 5)
        registerMove(388, "Worry Seed", PokemonType.GRASS, MoveCategory.STATUS, 0, 100, 10)
        registerMove(389, "Sucker Punch", PokemonType.DARK, MoveCategory.PHYSICAL, 70, 100, 5)
        registerMove(390, "Toxic Spikes", PokemonType.POISON, MoveCategory.STATUS, 0, 0, 20)
        registerMove(391, "Heart Swap", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(392, "Aqua Ring", PokemonType.WATER, MoveCategory.STATUS, 0, 0, 20)
        registerMove(393, "Magnet Rise", PokemonType.ELECTRIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(394, "Flare Blitz", PokemonType.FIRE, MoveCategory.PHYSICAL, 120, 100, 15)
        registerMove(395, "Force Palm", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 60, 100, 10)
        registerMove(396, "Aura Sphere", PokemonType.FIGHTING, MoveCategory.SPECIAL, 80, 0, 20)
        registerMove(397, "Rock Polish", PokemonType.ROCK, MoveCategory.STATUS, 0, 0, 20)
        registerMove(398, "Poison Jab", PokemonType.POISON, MoveCategory.PHYSICAL, 80, 100, 20)
        registerMove(399, "Dark Pulse", PokemonType.DARK, MoveCategory.SPECIAL, 80, 100, 15)
        registerMove(400, "Night Slash", PokemonType.DARK, MoveCategory.PHYSICAL, 70, 100, 15)
    }

    private fun registerMoveChunk3() {
        registerMove(401, "Aqua Tail", PokemonType.WATER, MoveCategory.PHYSICAL, 90, 90, 10)
        registerMove(402, "Seed Bomb", PokemonType.GRASS, MoveCategory.PHYSICAL, 80, 100, 15)
        registerMove(403, "Air Slash", PokemonType.FLYING, MoveCategory.SPECIAL, 75, 95, 15)
        registerMove(404, "X-Scissor", PokemonType.BUG, MoveCategory.PHYSICAL, 80, 100, 15)
        registerMove(405, "Bug Buzz", PokemonType.BUG, MoveCategory.SPECIAL, 90, 100, 10)
        registerMove(406, "Dragon Pulse", PokemonType.DRAGON, MoveCategory.SPECIAL, 85, 100, 10)
        registerMove(407, "Dragon Rush", PokemonType.DRAGON, MoveCategory.PHYSICAL, 100, 75, 10)
        registerMove(408, "Power Gem", PokemonType.ROCK, MoveCategory.SPECIAL, 80, 100, 20)
        registerMove(409, "Drain Punch", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 75, 100, 10)
        registerMove(410, "Vacuum Wave", PokemonType.FIGHTING, MoveCategory.SPECIAL, 40, 100, 30)
        registerMove(411, "Focus Blast", PokemonType.FIGHTING, MoveCategory.SPECIAL, 120, 70, 5)
        registerMove(412, "Energy Ball", PokemonType.GRASS, MoveCategory.SPECIAL, 90, 100, 10)
        registerMove(413, "Brave Bird", PokemonType.FLYING, MoveCategory.PHYSICAL, 120, 100, 15)
        registerMove(414, "Earth Power", PokemonType.GROUND, MoveCategory.SPECIAL, 90, 100, 10)
        registerMove(415, "Switcheroo", PokemonType.DARK, MoveCategory.STATUS, 0, 100, 10)
        registerMove(416, "Giga Impact", PokemonType.NORMAL, MoveCategory.PHYSICAL, 150, 90, 5)
        registerMove(417, "Nasty Plot", PokemonType.DARK, MoveCategory.STATUS, 0, 0, 20)
        registerMove(418, "Bullet Punch", PokemonType.STEEL, MoveCategory.PHYSICAL, 40, 100, 30)
        registerMove(419, "Avalanche", PokemonType.ICE, MoveCategory.PHYSICAL, 60, 100, 10)
        registerMove(420, "Ice Shard", PokemonType.ICE, MoveCategory.PHYSICAL, 40, 100, 30)
        registerMove(421, "Shadow Claw", PokemonType.GHOST, MoveCategory.PHYSICAL, 70, 100, 15)
        registerMove(422, "Thunder Fang", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 65, 95, 15)
        registerMove(423, "Ice Fang", PokemonType.ICE, MoveCategory.PHYSICAL, 65, 95, 15)
        registerMove(424, "Fire Fang", PokemonType.FIRE, MoveCategory.PHYSICAL, 65, 95, 15)
        registerMove(425, "Shadow Sneak", PokemonType.GHOST, MoveCategory.PHYSICAL, 40, 100, 30)
        registerMove(426, "Mud Bomb", PokemonType.GROUND, MoveCategory.SPECIAL, 65, 85, 10)
        registerMove(427, "Psycho Cut", PokemonType.PSYCHIC, MoveCategory.PHYSICAL, 70, 100, 20)
        registerMove(428, "Zen Headbutt", PokemonType.PSYCHIC, MoveCategory.PHYSICAL, 80, 90, 15)
        registerMove(429, "Mirror Shot", PokemonType.STEEL, MoveCategory.SPECIAL, 65, 85, 10)
        registerMove(430, "Flash Cannon", PokemonType.STEEL, MoveCategory.SPECIAL, 80, 100, 10)
        registerMove(431, "Rock Climb", PokemonType.NORMAL, MoveCategory.PHYSICAL, 90, 85, 20)
        registerMove(432, "Defog", PokemonType.FLYING, MoveCategory.STATUS, 0, 0, 15)
        registerMove(433, "Trick Room", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 5)
        registerMove(434, "Draco Meteor", PokemonType.DRAGON, MoveCategory.SPECIAL, 130, 90, 5)
        registerMove(435, "Discharge", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 80, 100, 15)
        registerMove(436, "Lava Plume", PokemonType.FIRE, MoveCategory.SPECIAL, 80, 100, 15)
        registerMove(437, "Leaf Storm", PokemonType.GRASS, MoveCategory.SPECIAL, 130, 90, 5)
        registerMove(438, "Power Whip", PokemonType.GRASS, MoveCategory.PHYSICAL, 120, 85, 10)
        registerMove(439, "Rock Wrecker", PokemonType.ROCK, MoveCategory.PHYSICAL, 150, 90, 5)
        registerMove(440, "Cross Poison", PokemonType.POISON, MoveCategory.PHYSICAL, 70, 100, 20)
        registerMove(441, "Gunk Shot", PokemonType.POISON, MoveCategory.PHYSICAL, 120, 80, 5)
        registerMove(442, "Iron Head", PokemonType.STEEL, MoveCategory.PHYSICAL, 80, 100, 15)
        registerMove(443, "Magnet Bomb", PokemonType.STEEL, MoveCategory.PHYSICAL, 60, 0, 20)
        registerMove(444, "Stone Edge", PokemonType.ROCK, MoveCategory.PHYSICAL, 100, 80, 5)
        registerMove(445, "Captivate", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 20)
        registerMove(446, "Stealth Rock", PokemonType.ROCK, MoveCategory.STATUS, 0, 0, 20)
        registerMove(447, "Grass Knot", PokemonType.GRASS, MoveCategory.SPECIAL, 1, 100, 20)
        registerMove(448, "Chatter", PokemonType.FLYING, MoveCategory.SPECIAL, 65, 100, 20)
        registerMove(449, "Judgment", PokemonType.NORMAL, MoveCategory.SPECIAL, 100, 100, 10)
        registerMove(450, "Bug Bite", PokemonType.BUG, MoveCategory.PHYSICAL, 60, 100, 20)
        registerMove(451, "Charge Beam", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 50, 90, 10)
        registerMove(452, "Wood Hammer", PokemonType.GRASS, MoveCategory.PHYSICAL, 120, 100, 15)
        registerMove(453, "Aqua Jet", PokemonType.WATER, MoveCategory.PHYSICAL, 40, 100, 20)
        registerMove(454, "Attack Order", PokemonType.BUG, MoveCategory.PHYSICAL, 90, 100, 15)
        registerMove(455, "Defend Order", PokemonType.BUG, MoveCategory.STATUS, 0, 0, 10)
        registerMove(456, "Heal Order", PokemonType.BUG, MoveCategory.STATUS, 0, 0, 10)
        registerMove(457, "Head Smash", PokemonType.ROCK, MoveCategory.PHYSICAL, 150, 80, 5)
        registerMove(458, "Double Hit", PokemonType.NORMAL, MoveCategory.PHYSICAL, 35, 90, 10)
        registerMove(459, "Roar Of Time", PokemonType.DRAGON, MoveCategory.SPECIAL, 150, 90, 5)
        registerMove(460, "Spacial Rend", PokemonType.DRAGON, MoveCategory.SPECIAL, 100, 95, 5)
        registerMove(461, "Lunar Dance", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(462, "Crush Grip", PokemonType.NORMAL, MoveCategory.PHYSICAL, 120, 100, 5)
        registerMove(463, "Magma Storm", PokemonType.FIRE, MoveCategory.SPECIAL, 100, 75, 5)
        registerMove(464, "Dark Void", PokemonType.DARK, MoveCategory.STATUS, 0, 50, 10)
        registerMove(465, "Seed Flare", PokemonType.GRASS, MoveCategory.SPECIAL, 120, 85, 5)
        registerMove(466, "Ominous Wind", PokemonType.GHOST, MoveCategory.SPECIAL, 60, 100, 5)
        registerMove(467, "Shadow Force", PokemonType.GHOST, MoveCategory.PHYSICAL, 120, 100, 5)
        registerMove(468, "Hone Claws", PokemonType.DARK, MoveCategory.STATUS, 0, 0, 15)
        registerMove(469, "Wide Guard", PokemonType.ROCK, MoveCategory.STATUS, 0, 0, 10)
        registerMove(470, "Guard Split", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(471, "Power Split", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(472, "Wonder Room", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(473, "Psyshock", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 80, 100, 10)
        registerMove(474, "Venoshock", PokemonType.POISON, MoveCategory.SPECIAL, 65, 100, 10)
        registerMove(475, "Autotomize", PokemonType.STEEL, MoveCategory.STATUS, 0, 0, 15)
        registerMove(476, "Rage Powder", PokemonType.BUG, MoveCategory.STATUS, 0, 0, 20)
        registerMove(477, "Telekinesis", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 15)
        registerMove(478, "Magic Room", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(479, "Smack Down", PokemonType.ROCK, MoveCategory.PHYSICAL, 50, 100, 15)
        registerMove(480, "Storm Throw", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 60, 100, 10)
        registerMove(481, "Flame Burst", PokemonType.FIRE, MoveCategory.SPECIAL, 70, 100, 15)
        registerMove(482, "Sludge Wave", PokemonType.POISON, MoveCategory.SPECIAL, 95, 100, 10)
        registerMove(483, "Quiver Dance", PokemonType.BUG, MoveCategory.STATUS, 0, 0, 20)
        registerMove(484, "Heavy Slam", PokemonType.STEEL, MoveCategory.PHYSICAL, 1, 100, 10)
        registerMove(485, "Synchronoise", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 120, 100, 10)
        registerMove(486, "Electro Ball", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 1, 100, 10)
        registerMove(487, "Soak", PokemonType.WATER, MoveCategory.STATUS, 0, 100, 20)
        registerMove(488, "Flame Charge", PokemonType.FIRE, MoveCategory.PHYSICAL, 50, 100, 20)
        registerMove(489, "Coil", PokemonType.POISON, MoveCategory.STATUS, 0, 0, 20)
        registerMove(490, "Low Sweep", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 65, 100, 20)
        registerMove(491, "Acid Spray", PokemonType.POISON, MoveCategory.SPECIAL, 40, 100, 20)
        registerMove(492, "Foul Play", PokemonType.DARK, MoveCategory.PHYSICAL, 95, 100, 15)
        registerMove(493, "Simple Beam", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 15)
        registerMove(494, "Entrainment", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 15)
        registerMove(495, "After You", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 15)
        registerMove(496, "Round", PokemonType.NORMAL, MoveCategory.SPECIAL, 60, 100, 15)
        registerMove(497, "Echoed Voice", PokemonType.NORMAL, MoveCategory.SPECIAL, 40, 100, 15)
        registerMove(498, "Chip Away", PokemonType.NORMAL, MoveCategory.PHYSICAL, 70, 100, 20)
        registerMove(499, "Clear Smog", PokemonType.POISON, MoveCategory.SPECIAL, 50, 0, 15)
        registerMove(500, "Stored Power", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 20, 100, 10)
        registerMove(501, "Quick Guard", PokemonType.FIGHTING, MoveCategory.STATUS, 0, 0, 15)
        registerMove(502, "Ally Switch", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 15)
        registerMove(503, "Scald", PokemonType.WATER, MoveCategory.SPECIAL, 80, 100, 15)
        registerMove(504, "Shell Smash", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 15)
        registerMove(505, "Heal Pulse", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(506, "Hex", PokemonType.GHOST, MoveCategory.SPECIAL, 65, 100, 10)
        registerMove(507, "Sky Drop", PokemonType.FLYING, MoveCategory.PHYSICAL, 60, 100, 10)
        registerMove(508, "Shift Gear", PokemonType.STEEL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(509, "Circle Throw", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 60, 90, 10)
        registerMove(510, "Incinerate", PokemonType.FIRE, MoveCategory.SPECIAL, 60, 100, 15)
        registerMove(511, "Quash", PokemonType.DARK, MoveCategory.STATUS, 0, 100, 15)
        registerMove(512, "Acrobatics", PokemonType.FLYING, MoveCategory.PHYSICAL, 55, 100, 15)
        registerMove(513, "Reflect Type", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 15)
        registerMove(514, "Retaliate", PokemonType.NORMAL, MoveCategory.PHYSICAL, 70, 100, 5)
        registerMove(515, "Final Gambit", PokemonType.FIGHTING, MoveCategory.SPECIAL, 1, 100, 5)
        registerMove(516, "Bestow", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 15)
        registerMove(517, "Inferno", PokemonType.FIRE, MoveCategory.SPECIAL, 100, 50, 5)
        registerMove(518, "Water Pledge", PokemonType.WATER, MoveCategory.SPECIAL, 80, 100, 10)
        registerMove(519, "Fire Pledge", PokemonType.FIRE, MoveCategory.SPECIAL, 80, 100, 10)
        registerMove(520, "Grass Pledge", PokemonType.GRASS, MoveCategory.SPECIAL, 80, 100, 10)
        registerMove(521, "Volt Switch", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 70, 100, 20)
        registerMove(522, "Struggle Bug", PokemonType.BUG, MoveCategory.SPECIAL, 50, 100, 20)
        registerMove(523, "Bulldoze", PokemonType.GROUND, MoveCategory.PHYSICAL, 60, 100, 20)
        registerMove(524, "Frost Breath", PokemonType.ICE, MoveCategory.SPECIAL, 60, 90, 10)
        registerMove(525, "Dragon Tail", PokemonType.DRAGON, MoveCategory.PHYSICAL, 60, 90, 10)
        registerMove(526, "Work Up", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 30)
        registerMove(527, "Electroweb", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 55, 95, 15)
        registerMove(528, "Wild Charge", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 90, 100, 15)
        registerMove(529, "Drill Run", PokemonType.GROUND, MoveCategory.PHYSICAL, 80, 95, 10)
        registerMove(530, "Dual Chop", PokemonType.DRAGON, MoveCategory.PHYSICAL, 40, 90, 15)
        registerMove(531, "Heart Stamp", PokemonType.PSYCHIC, MoveCategory.PHYSICAL, 60, 100, 25)
        registerMove(532, "Horn Leech", PokemonType.GRASS, MoveCategory.PHYSICAL, 75, 100, 10)
        registerMove(533, "Sacred Sword", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 90, 100, 15)
        registerMove(534, "Razor Shell", PokemonType.WATER, MoveCategory.PHYSICAL, 75, 95, 10)
        registerMove(535, "Heat Crash", PokemonType.FIRE, MoveCategory.PHYSICAL, 1, 100, 10)
        registerMove(536, "Leaf Tornado", PokemonType.GRASS, MoveCategory.SPECIAL, 65, 90, 10)
        registerMove(537, "Steamroller", PokemonType.BUG, MoveCategory.PHYSICAL, 65, 100, 20)
        registerMove(538, "Cotton Guard", PokemonType.GRASS, MoveCategory.STATUS, 0, 0, 10)
        registerMove(539, "Night Daze", PokemonType.DARK, MoveCategory.SPECIAL, 85, 95, 10)
        registerMove(540, "Psystrike", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 100, 100, 10)
        registerMove(541, "Tail Slap", PokemonType.NORMAL, MoveCategory.PHYSICAL, 25, 85, 10)
        registerMove(542, "Hurricane", PokemonType.FLYING, MoveCategory.SPECIAL, 110, 70, 10)
        registerMove(543, "Head Charge", PokemonType.NORMAL, MoveCategory.PHYSICAL, 120, 100, 15)
        registerMove(544, "Gear Grind", PokemonType.STEEL, MoveCategory.PHYSICAL, 50, 85, 15)
        registerMove(545, "Searing Shot", PokemonType.FIRE, MoveCategory.SPECIAL, 100, 100, 5)
        registerMove(546, "Techno Blast", PokemonType.NORMAL, MoveCategory.SPECIAL, 120, 100, 5)
        registerMove(547, "Relic Song", PokemonType.NORMAL, MoveCategory.SPECIAL, 75, 100, 10)
        registerMove(548, "Secret Sword", PokemonType.FIGHTING, MoveCategory.SPECIAL, 85, 100, 10)
        registerMove(549, "Glaciate", PokemonType.ICE, MoveCategory.SPECIAL, 65, 95, 10)
        registerMove(550, "Bolt Strike", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 130, 85, 5)
        registerMove(551, "Blue Flare", PokemonType.FIRE, MoveCategory.SPECIAL, 130, 85, 5)
        registerMove(552, "Fiery Dance", PokemonType.FIRE, MoveCategory.SPECIAL, 80, 100, 10)
        registerMove(553, "Freeze Shock", PokemonType.ICE, MoveCategory.PHYSICAL, 140, 90, 5)
        registerMove(554, "Ice Burn", PokemonType.ICE, MoveCategory.SPECIAL, 140, 90, 5)
        registerMove(555, "Snarl", PokemonType.DARK, MoveCategory.SPECIAL, 55, 95, 15)
        registerMove(556, "Icicle Crash", PokemonType.ICE, MoveCategory.PHYSICAL, 85, 90, 10)
        registerMove(557, "V-create", PokemonType.FIRE, MoveCategory.PHYSICAL, 180, 95, 5)
        registerMove(558, "Fusion Flare", PokemonType.FIRE, MoveCategory.SPECIAL, 100, 100, 5)
        registerMove(559, "Fusion Bolt", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 100, 100, 5)
        registerMove(560, "Flying Press", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 100, 95, 10)
        registerMove(561, "Mat Block", PokemonType.FIGHTING, MoveCategory.STATUS, 0, 0, 15)
        registerMove(562, "Belch", PokemonType.POISON, MoveCategory.SPECIAL, 120, 90, 10)
        registerMove(563, "Rototiller", PokemonType.GROUND, MoveCategory.STATUS, 0, 0, 10)
        registerMove(564, "Sticky Web", PokemonType.BUG, MoveCategory.STATUS, 0, 0, 20)
        registerMove(565, "Fell Stinger", PokemonType.BUG, MoveCategory.PHYSICAL, 50, 100, 25)
        registerMove(566, "Phantom Force", PokemonType.GHOST, MoveCategory.PHYSICAL, 90, 100, 10)
        registerMove(567, "Trick-Or-Treat", PokemonType.GHOST, MoveCategory.STATUS, 0, 100, 20)
        registerMove(568, "Noble Roar", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 30)
        registerMove(569, "Ion Deluge", PokemonType.ELECTRIC, MoveCategory.STATUS, 0, 0, 25)
        registerMove(570, "Parabolic Charge", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 65, 100, 20)
        registerMove(571, "Forest's Curse", PokemonType.GRASS, MoveCategory.STATUS, 0, 100, 20)
        registerMove(572, "Petal Blizzard", PokemonType.GRASS, MoveCategory.PHYSICAL, 90, 100, 15)
        registerMove(573, "Freeze-Dry", PokemonType.ICE, MoveCategory.SPECIAL, 70, 100, 20)
        registerMove(574, "Disarming Voice", PokemonType.FAIRY, MoveCategory.SPECIAL, 40, 0, 15)
        registerMove(575, "Parting Shot", PokemonType.DARK, MoveCategory.STATUS, 0, 100, 20)
        registerMove(576, "Topsy-Turvy", PokemonType.DARK, MoveCategory.STATUS, 0, 0, 20)
        registerMove(577, "Draining Kiss", PokemonType.FAIRY, MoveCategory.SPECIAL, 50, 100, 10)
        registerMove(578, "Crafty Shield", PokemonType.FAIRY, MoveCategory.STATUS, 0, 0, 10)
        registerMove(579, "Flower Shield", PokemonType.FAIRY, MoveCategory.STATUS, 0, 0, 10)
        registerMove(580, "Grassy Terrain", PokemonType.GRASS, MoveCategory.STATUS, 0, 0, 10)
        registerMove(581, "Misty Terrain", PokemonType.FAIRY, MoveCategory.STATUS, 0, 0, 10)
        registerMove(582, "Electrify", PokemonType.ELECTRIC, MoveCategory.STATUS, 0, 0, 20)
        registerMove(583, "Play Rough", PokemonType.FAIRY, MoveCategory.PHYSICAL, 90, 90, 10)
        registerMove(584, "Fairy Wind", PokemonType.FAIRY, MoveCategory.SPECIAL, 40, 100, 30)
        registerMove(585, "Moonblast", PokemonType.FAIRY, MoveCategory.SPECIAL, 95, 100, 15)
        registerMove(586, "Boomburst", PokemonType.NORMAL, MoveCategory.SPECIAL, 140, 100, 10)
        registerMove(587, "Fairy Lock", PokemonType.FAIRY, MoveCategory.STATUS, 0, 0, 10)
        registerMove(588, "King's Shield", PokemonType.STEEL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(589, "Play Nice", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 20)
        registerMove(590, "Confide", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 20)
        registerMove(591, "Diamond Storm", PokemonType.ROCK, MoveCategory.PHYSICAL, 100, 95, 5)
        registerMove(592, "Steam Eruption", PokemonType.WATER, MoveCategory.SPECIAL, 110, 95, 5)
        registerMove(593, "Hyperspace Hole", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 80, 0, 5)
        registerMove(594, "Water Shuriken", PokemonType.WATER, MoveCategory.SPECIAL, 15, 100, 20)
        registerMove(595, "Mystical Fire", PokemonType.FIRE, MoveCategory.SPECIAL, 75, 100, 10)
        registerMove(596, "Spiky Shield", PokemonType.GRASS, MoveCategory.STATUS, 0, 0, 10)
        registerMove(597, "Aromatic Mist", PokemonType.FAIRY, MoveCategory.STATUS, 0, 0, 20)
        registerMove(598, "Eerie Impulse", PokemonType.ELECTRIC, MoveCategory.STATUS, 0, 100, 15)
        registerMove(599, "Venom Drench", PokemonType.POISON, MoveCategory.STATUS, 0, 100, 20)
        registerMove(600, "Powder", PokemonType.BUG, MoveCategory.STATUS, 0, 100, 20)
    }

    private fun registerMoveChunk4() {
        registerMove(601, "Geomancy", PokemonType.FAIRY, MoveCategory.STATUS, 0, 0, 10)
        registerMove(602, "Magnetic Flux", PokemonType.ELECTRIC, MoveCategory.STATUS, 0, 0, 20)
        registerMove(603, "Happy Hour", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 30)
        registerMove(604, "Electric Terrain", PokemonType.ELECTRIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(605, "Dazzling Gleam", PokemonType.FAIRY, MoveCategory.SPECIAL, 80, 100, 10)
        registerMove(606, "Celebrate", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 40)
        registerMove(607, "Hold Hands", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 40)
        registerMove(608, "Baby-Doll Eyes", PokemonType.FAIRY, MoveCategory.STATUS, 0, 100, 30)
        registerMove(609, "Nuzzle", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 20, 100, 20)
        registerMove(610, "Hold Back", PokemonType.NORMAL, MoveCategory.PHYSICAL, 40, 100, 40)
        registerMove(611, "Infestation", PokemonType.BUG, MoveCategory.SPECIAL, 20, 100, 20)
        registerMove(612, "Power-Up Punch", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 40, 100, 20)
        registerMove(613, "Oblivion Wing", PokemonType.FLYING, MoveCategory.SPECIAL, 80, 100, 10)
        registerMove(614, "Thousand Arrows", PokemonType.GROUND, MoveCategory.PHYSICAL, 90, 100, 10)
        registerMove(615, "Thousand Waves", PokemonType.GROUND, MoveCategory.PHYSICAL, 90, 100, 10)
        registerMove(616, "Land's Wrath", PokemonType.GROUND, MoveCategory.PHYSICAL, 90, 100, 10)
        registerMove(617, "Light Of Ruin", PokemonType.FAIRY, MoveCategory.SPECIAL, 140, 90, 5)
        registerMove(618, "Origin Pulse", PokemonType.WATER, MoveCategory.SPECIAL, 110, 85, 10)
        registerMove(619, "Precipice Blades", PokemonType.GROUND, MoveCategory.PHYSICAL, 120, 85, 10)
        registerMove(620, "Dragon Ascent", PokemonType.FLYING, MoveCategory.PHYSICAL, 120, 100, 5)
        registerMove(621, "Hyperspace Fury", PokemonType.DARK, MoveCategory.PHYSICAL, 100, 0, 5)
        registerMove(622, "Shore Up", PokemonType.GROUND, MoveCategory.STATUS, 0, 0, 5)
        registerMove(623, "First Impression", PokemonType.BUG, MoveCategory.PHYSICAL, 90, 100, 10)
        registerMove(624, "Baneful Bunker", PokemonType.POISON, MoveCategory.STATUS, 0, 0, 10)
        registerMove(625, "Spirit Shackle", PokemonType.GHOST, MoveCategory.PHYSICAL, 80, 100, 10)
        registerMove(626, "Darkest Lariat", PokemonType.DARK, MoveCategory.PHYSICAL, 85, 100, 10)
        registerMove(627, "Sparkling Aria", PokemonType.WATER, MoveCategory.SPECIAL, 90, 100, 10)
        registerMove(628, "Ice Hammer", PokemonType.ICE, MoveCategory.PHYSICAL, 100, 90, 10)
        registerMove(629, "Floral Healing", PokemonType.FAIRY, MoveCategory.STATUS, 0, 0, 10)
        registerMove(630, "High Horsepower", PokemonType.GROUND, MoveCategory.PHYSICAL, 95, 95, 10)
        registerMove(631, "Strength Sap", PokemonType.GRASS, MoveCategory.STATUS, 0, 100, 10)
        registerMove(632, "Solar Blade", PokemonType.GRASS, MoveCategory.PHYSICAL, 125, 100, 10)
        registerMove(633, "Leafage", PokemonType.GRASS, MoveCategory.PHYSICAL, 40, 100, 40)
        registerMove(634, "Spotlight", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 15)
        registerMove(635, "Toxic Thread", PokemonType.POISON, MoveCategory.STATUS, 0, 100, 20)
        registerMove(636, "Laser Focus", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 30)
        registerMove(637, "Gear Up", PokemonType.STEEL, MoveCategory.STATUS, 0, 0, 20)
        registerMove(638, "Throat Chop", PokemonType.DARK, MoveCategory.PHYSICAL, 80, 100, 15)
        registerMove(639, "Pollen Puff", PokemonType.BUG, MoveCategory.SPECIAL, 90, 100, 15)
        registerMove(640, "Anchor Shot", PokemonType.STEEL, MoveCategory.PHYSICAL, 80, 100, 20)
        registerMove(641, "Psychic Terrain", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(642, "Lunge", PokemonType.BUG, MoveCategory.PHYSICAL, 80, 100, 15)
        registerMove(643, "Fire Lash", PokemonType.FIRE, MoveCategory.PHYSICAL, 80, 100, 15)
        registerMove(644, "Power Trip", PokemonType.DARK, MoveCategory.PHYSICAL, 20, 100, 10)
        registerMove(645, "Burn Up", PokemonType.FIRE, MoveCategory.SPECIAL, 130, 100, 5)
        registerMove(646, "Speed Swap", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(647, "Smart Strike", PokemonType.STEEL, MoveCategory.PHYSICAL, 70, 0, 10)
        registerMove(648, "Purify", PokemonType.POISON, MoveCategory.STATUS, 0, 0, 20)
        registerMove(649, "Revelation Dance", PokemonType.NORMAL, MoveCategory.SPECIAL, 90, 100, 15)
        registerMove(650, "Core Enforcer", PokemonType.DRAGON, MoveCategory.SPECIAL, 100, 100, 10)
        registerMove(651, "Trop Kick", PokemonType.GRASS, MoveCategory.PHYSICAL, 70, 100, 15)
        registerMove(652, "Instruct", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 15)
        registerMove(653, "Beak Blast", PokemonType.FLYING, MoveCategory.PHYSICAL, 100, 100, 15)
        registerMove(654, "Clanging Scales", PokemonType.DRAGON, MoveCategory.SPECIAL, 110, 100, 5)
        registerMove(655, "Dragon Hammer", PokemonType.DRAGON, MoveCategory.PHYSICAL, 90, 100, 15)
        registerMove(656, "Brutal Swing", PokemonType.DARK, MoveCategory.PHYSICAL, 60, 100, 20)
        registerMove(657, "Aurora Veil", PokemonType.ICE, MoveCategory.STATUS, 0, 0, 20)
        registerMove(658, "Shell Trap", PokemonType.FIRE, MoveCategory.SPECIAL, 150, 100, 5)
        registerMove(659, "Fleur Cannon", PokemonType.FAIRY, MoveCategory.SPECIAL, 130, 90, 5)
        registerMove(660, "Psychic Fangs", PokemonType.PSYCHIC, MoveCategory.PHYSICAL, 85, 100, 15)
        registerMove(661, "Stomping Tantrum", PokemonType.GROUND, MoveCategory.PHYSICAL, 75, 100, 10)
        registerMove(662, "Shadow Bone", PokemonType.GHOST, MoveCategory.PHYSICAL, 85, 100, 10)
        registerMove(663, "Accelerock", PokemonType.ROCK, MoveCategory.PHYSICAL, 40, 100, 20)
        registerMove(664, "Liquidation", PokemonType.WATER, MoveCategory.PHYSICAL, 85, 100, 10)
        registerMove(665, "Prismatic Laser", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 160, 100, 10)
        registerMove(666, "Spectral Thief", PokemonType.GHOST, MoveCategory.PHYSICAL, 90, 100, 10)
        registerMove(667, "Sunsteel Strike", PokemonType.STEEL, MoveCategory.PHYSICAL, 100, 100, 5)
        registerMove(668, "Moongeist Beam", PokemonType.GHOST, MoveCategory.SPECIAL, 100, 100, 5)
        registerMove(669, "Tearful Look", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 20)
        registerMove(670, "Zing Zap", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 80, 100, 10)
        registerMove(671, "Nature's Madness", PokemonType.FAIRY, MoveCategory.SPECIAL, 1, 90, 10)
        registerMove(672, "Multi-Attack", PokemonType.NORMAL, MoveCategory.PHYSICAL, 120, 100, 10)
        registerMove(673, "Mind Blown", PokemonType.FIRE, MoveCategory.SPECIAL, 150, 100, 5)
        registerMove(674, "Plasma Fists", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 100, 100, 15)
        registerMove(675, "Photon Geyser", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 100, 100, 5)
        registerMove(676, "Zippy Zap", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 80, 100, 10)
        registerMove(677, "Splishy Splash", PokemonType.WATER, MoveCategory.SPECIAL, 90, 100, 15)
        registerMove(678, "Floaty Fall", PokemonType.FLYING, MoveCategory.PHYSICAL, 90, 95, 15)
        registerMove(679, "Pika Papow", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 1, 0, 20)
        registerMove(680, "Bouncy Bubble", PokemonType.WATER, MoveCategory.SPECIAL, 60, 100, 20)
        registerMove(681, "Buzzy Buzz", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 60, 100, 20)
        registerMove(682, "Sizzly Slide", PokemonType.FIRE, MoveCategory.PHYSICAL, 60, 100, 20)
        registerMove(683, "Glitzy Glow", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 80, 95, 15)
        registerMove(684, "Baddy Bad", PokemonType.DARK, MoveCategory.SPECIAL, 80, 95, 15)
        registerMove(685, "Sappy Seed", PokemonType.GRASS, MoveCategory.PHYSICAL, 100, 90, 10)
        registerMove(686, "Freezy Frost", PokemonType.ICE, MoveCategory.SPECIAL, 100, 90, 10)
        registerMove(687, "Sparkly Swirl", PokemonType.FAIRY, MoveCategory.SPECIAL, 120, 85, 5)
        registerMove(688, "Veevee Volley", PokemonType.NORMAL, MoveCategory.PHYSICAL, 1, 0, 20)
        registerMove(689, "Double Iron Bash", PokemonType.STEEL, MoveCategory.PHYSICAL, 60, 100, 5)
        registerMove(690, "Dynamax Cannon", PokemonType.DRAGON, MoveCategory.SPECIAL, 100, 100, 5)
        registerMove(691, "Snipe Shot", PokemonType.WATER, MoveCategory.SPECIAL, 80, 100, 15)
        registerMove(692, "Jaw Lock", PokemonType.DARK, MoveCategory.PHYSICAL, 80, 100, 10)
        registerMove(693, "Stuff Cheeks", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(694, "No Retreat", PokemonType.FIGHTING, MoveCategory.STATUS, 0, 0, 5)
        registerMove(695, "Tar Shot", PokemonType.ROCK, MoveCategory.STATUS, 0, 100, 15)
        registerMove(696, "Magic Powder", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 100, 20)
        registerMove(697, "Dragon Darts", PokemonType.DRAGON, MoveCategory.PHYSICAL, 50, 100, 10)
        registerMove(698, "Teatime", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(699, "Octolock", PokemonType.FIGHTING, MoveCategory.STATUS, 0, 100, 15)
        registerMove(700, "Bolt Beak", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 85, 100, 10)
        registerMove(701, "Fishious Rend", PokemonType.WATER, MoveCategory.PHYSICAL, 85, 100, 10)
        registerMove(702, "Court Change", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 10)
        registerMove(703, "Clangorous Soul", PokemonType.DRAGON, MoveCategory.STATUS, 0, 100, 5)
        registerMove(704, "Body Press", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 80, 100, 10)
        registerMove(705, "Decorate", PokemonType.FAIRY, MoveCategory.STATUS, 0, 0, 15)
        registerMove(706, "Drum Beating", PokemonType.GRASS, MoveCategory.PHYSICAL, 80, 100, 10)
        registerMove(707, "Snap Trap", PokemonType.GRASS, MoveCategory.PHYSICAL, 35, 100, 15)
        registerMove(708, "Pyro Ball", PokemonType.FIRE, MoveCategory.PHYSICAL, 120, 90, 5)
        registerMove(709, "Behemoth Blade", PokemonType.STEEL, MoveCategory.PHYSICAL, 100, 100, 5)
        registerMove(710, "Behemoth Bash", PokemonType.STEEL, MoveCategory.PHYSICAL, 100, 100, 5)
        registerMove(711, "Aura Wheel", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 110, 100, 10)
        registerMove(712, "Breaking Swipe", PokemonType.DRAGON, MoveCategory.PHYSICAL, 60, 100, 15)
        registerMove(713, "Branch Poke", PokemonType.GRASS, MoveCategory.PHYSICAL, 40, 100, 40)
        registerMove(714, "Overdrive", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 80, 100, 10)
        registerMove(715, "Apple Acid", PokemonType.GRASS, MoveCategory.SPECIAL, 80, 100, 10)
        registerMove(716, "Grav Apple", PokemonType.GRASS, MoveCategory.PHYSICAL, 80, 100, 10)
        registerMove(717, "Spirit Break", PokemonType.FAIRY, MoveCategory.PHYSICAL, 75, 100, 15)
        registerMove(718, "Strange Steam", PokemonType.FAIRY, MoveCategory.SPECIAL, 90, 95, 10)
        registerMove(719, "Life Dew", PokemonType.WATER, MoveCategory.STATUS, 0, 0, 10)
        registerMove(720, "Obstruct", PokemonType.DARK, MoveCategory.STATUS, 0, 100, 10)
        registerMove(721, "False Surrender", PokemonType.DARK, MoveCategory.PHYSICAL, 80, 0, 10)
        registerMove(722, "Meteor Assault", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 150, 100, 5)
        registerMove(723, "Eternabeam", PokemonType.DRAGON, MoveCategory.SPECIAL, 160, 90, 5)
        registerMove(724, "Steel Beam", PokemonType.STEEL, MoveCategory.SPECIAL, 140, 95, 5)
        registerMove(725, "Expanding Force", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 80, 100, 10)
        registerMove(726, "Steel Roller", PokemonType.STEEL, MoveCategory.PHYSICAL, 130, 100, 5)
        registerMove(727, "Scale Shot", PokemonType.DRAGON, MoveCategory.PHYSICAL, 25, 90, 20)
        registerMove(728, "Meteor Beam", PokemonType.ROCK, MoveCategory.SPECIAL, 120, 90, 10)
        registerMove(729, "Shell Side Arm", PokemonType.POISON, MoveCategory.SPECIAL, 90, 100, 10)
        registerMove(730, "Misty Explosion", PokemonType.FAIRY, MoveCategory.SPECIAL, 100, 100, 5)
        registerMove(731, "Grassy Glide", PokemonType.GRASS, MoveCategory.PHYSICAL, 55, 100, 20)
        registerMove(732, "Rising Voltage", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 70, 100, 20)
        registerMove(733, "Terrain Pulse", PokemonType.NORMAL, MoveCategory.SPECIAL, 50, 100, 10)
        registerMove(734, "Skitter Smack", PokemonType.BUG, MoveCategory.PHYSICAL, 70, 90, 10)
        registerMove(735, "Burning Jealousy", PokemonType.FIRE, MoveCategory.SPECIAL, 70, 100, 5)
        registerMove(736, "Lash Out", PokemonType.DARK, MoveCategory.PHYSICAL, 75, 100, 5)
        registerMove(737, "Poltergeist", PokemonType.GHOST, MoveCategory.PHYSICAL, 110, 90, 5)
        registerMove(738, "Corrosive Gas", PokemonType.POISON, MoveCategory.STATUS, 0, 100, 40)
        registerMove(739, "Coaching", PokemonType.FIGHTING, MoveCategory.STATUS, 0, 0, 10)
        registerMove(740, "Flip Turn", PokemonType.WATER, MoveCategory.PHYSICAL, 60, 100, 20)
        registerMove(741, "Triple Axel", PokemonType.ICE, MoveCategory.PHYSICAL, 20, 90, 10)
        registerMove(742, "Dual Wingbeat", PokemonType.FLYING, MoveCategory.PHYSICAL, 40, 90, 10)
        registerMove(743, "Scorching Sands", PokemonType.GROUND, MoveCategory.SPECIAL, 70, 100, 10)
        registerMove(744, "Jungle Healing", PokemonType.GRASS, MoveCategory.STATUS, 0, 0, 10)
        registerMove(745, "Wicked Blow", PokemonType.DARK, MoveCategory.PHYSICAL, 75, 100, 5)
        registerMove(746, "Surging Strikes", PokemonType.WATER, MoveCategory.PHYSICAL, 25, 100, 5)
        registerMove(747, "Thunder Cage", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 80, 90, 15)
        registerMove(748, "Dragon Energy", PokemonType.DRAGON, MoveCategory.SPECIAL, 150, 100, 5)
        registerMove(749, "Freezing Glare", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 90, 100, 10)
        registerMove(750, "Fiery Wrath", PokemonType.DARK, MoveCategory.SPECIAL, 90, 100, 10)
        registerMove(751, "Thunderous Kick", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 90, 100, 10)
        registerMove(752, "Glacial Lance", PokemonType.ICE, MoveCategory.PHYSICAL, 120, 100, 5)
        registerMove(753, "Astral Barrage", PokemonType.GHOST, MoveCategory.SPECIAL, 120, 100, 5)
        registerMove(754, "Eerie Spell", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 80, 100, 5)
        registerMove(755, "Dire Claw", PokemonType.POISON, MoveCategory.PHYSICAL, 80, 100, 15)
        registerMove(756, "Psyshield Bash", PokemonType.PSYCHIC, MoveCategory.PHYSICAL, 70, 90, 10)
        registerMove(757, "Power Shift", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(758, "Stone Axe", PokemonType.ROCK, MoveCategory.PHYSICAL, 65, 90, 15)
        registerMove(759, "Springtide Storm", PokemonType.FAIRY, MoveCategory.SPECIAL, 100, 80, 5)
        registerMove(760, "Mystical Power", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 70, 90, 10)
        registerMove(761, "Raging Fury", PokemonType.FIRE, MoveCategory.PHYSICAL, 120, 100, 10)
        registerMove(762, "Wave Crash", PokemonType.WATER, MoveCategory.PHYSICAL, 120, 100, 10)
        registerMove(763, "Chloroblast", PokemonType.GRASS, MoveCategory.SPECIAL, 150, 95, 5)
        registerMove(764, "Mountain Gale", PokemonType.ICE, MoveCategory.PHYSICAL, 100, 85, 10)
        registerMove(765, "Victory Dance", PokemonType.FIGHTING, MoveCategory.STATUS, 0, 0, 20)
        registerMove(766, "Headlong Rush", PokemonType.GROUND, MoveCategory.PHYSICAL, 120, 100, 5)
        registerMove(767, "Barb Barrage", PokemonType.POISON, MoveCategory.PHYSICAL, 60, 100, 10)
        registerMove(768, "Esper Wing", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 80, 100, 10)
        registerMove(769, "Bitter Malice", PokemonType.GHOST, MoveCategory.SPECIAL, 75, 100, 15)
        registerMove(770, "Shelter", PokemonType.STEEL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(771, "Triple Arrows", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 90, 100, 10)
        registerMove(772, "Infernal Parade", PokemonType.GHOST, MoveCategory.SPECIAL, 60, 100, 15)
        registerMove(773, "Ceaseless Edge", PokemonType.DARK, MoveCategory.PHYSICAL, 65, 90, 15)
        registerMove(774, "Bleakwind Storm", PokemonType.FLYING, MoveCategory.SPECIAL, 100, 80, 10)
        registerMove(775, "Wildbolt Storm", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 100, 80, 10)
        registerMove(776, "Sandsear Storm", PokemonType.GROUND, MoveCategory.SPECIAL, 100, 80, 10)
        registerMove(777, "Lunar Blessing", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(778, "Take Heart", PokemonType.PSYCHIC, MoveCategory.STATUS, 0, 0, 10)
        registerMove(779, "Tera Blast", PokemonType.NORMAL, MoveCategory.SPECIAL, 80, 100, 10)
        registerMove(780, "Silk Trap", PokemonType.BUG, MoveCategory.STATUS, 0, 0, 10)
        registerMove(781, "Axe Kick", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 120, 90, 10)
        registerMove(782, "Last Respects", PokemonType.GHOST, MoveCategory.PHYSICAL, 50, 100, 10)
        registerMove(783, "Lumina Crash", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 80, 100, 10)
        registerMove(784, "Order Up", PokemonType.DRAGON, MoveCategory.PHYSICAL, 80, 100, 10)
        registerMove(785, "Jet Punch", PokemonType.WATER, MoveCategory.PHYSICAL, 60, 100, 15)
        registerMove(786, "Spicy Extract", PokemonType.GRASS, MoveCategory.STATUS, 0, 0, 15)
        registerMove(787, "Spin Out", PokemonType.STEEL, MoveCategory.PHYSICAL, 100, 100, 5)
        registerMove(788, "Population Bomb", PokemonType.NORMAL, MoveCategory.PHYSICAL, 20, 90, 10)
        registerMove(789, "Ice Spinner", PokemonType.ICE, MoveCategory.PHYSICAL, 80, 100, 15)
        registerMove(790, "Glaive Rush", PokemonType.DRAGON, MoveCategory.PHYSICAL, 120, 100, 5)
        registerMove(791, "Revival Blessing", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 1)
        registerMove(792, "Salt Cure", PokemonType.ROCK, MoveCategory.PHYSICAL, 40, 100, 15)
        registerMove(793, "Triple Dive", PokemonType.WATER, MoveCategory.PHYSICAL, 30, 95, 10)
        registerMove(794, "Mortal Spin", PokemonType.POISON, MoveCategory.PHYSICAL, 30, 100, 15)
        registerMove(795, "Doodle", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 10)
        registerMove(796, "Fillet Away", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(797, "Kowtow Cleave", PokemonType.DARK, MoveCategory.PHYSICAL, 85, 0, 10)
        registerMove(798, "Flower Trick", PokemonType.GRASS, MoveCategory.PHYSICAL, 70, 0, 10)
        registerMove(799, "Torch Song", PokemonType.FIRE, MoveCategory.SPECIAL, 80, 100, 10)
        registerMove(800, "Aqua Step", PokemonType.WATER, MoveCategory.PHYSICAL, 80, 100, 10)
    }

    private fun registerMoveChunk5() {
        registerMove(801, "Raging Bull", PokemonType.NORMAL, MoveCategory.PHYSICAL, 90, 100, 10)
        registerMove(802, "Make It Rain", PokemonType.STEEL, MoveCategory.SPECIAL, 120, 100, 5)
        registerMove(803, "Ruination", PokemonType.DARK, MoveCategory.SPECIAL, 1, 90, 10)
        registerMove(804, "Collision Course", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 100, 100, 5)
        registerMove(805, "Electro Drift", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 100, 100, 5)
        registerMove(806, "Shed Tail", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(807, "Chilly Reception", PokemonType.ICE, MoveCategory.STATUS, 0, 0, 10)
        registerMove(808, "Tidy Up", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(809, "Snowscape", PokemonType.ICE, MoveCategory.STATUS, 0, 0, 10)
        registerMove(810, "Pounce", PokemonType.BUG, MoveCategory.PHYSICAL, 50, 100, 20)
        registerMove(811, "Trailblaze", PokemonType.GRASS, MoveCategory.PHYSICAL, 50, 100, 20)
        registerMove(812, "Chilling Water", PokemonType.WATER, MoveCategory.SPECIAL, 50, 100, 20)
        registerMove(813, "Hyper Drill", PokemonType.NORMAL, MoveCategory.PHYSICAL, 100, 100, 5)
        registerMove(814, "Twin Beam", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 40, 100, 10)
        registerMove(815, "Rage Fist", PokemonType.GHOST, MoveCategory.PHYSICAL, 50, 100, 10)
        registerMove(816, "Armor Cannon", PokemonType.FIRE, MoveCategory.SPECIAL, 120, 100, 5)
        registerMove(817, "Bitter Blade", PokemonType.FIRE, MoveCategory.PHYSICAL, 90, 100, 10)
        registerMove(818, "Double Shock", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 120, 100, 5)
        registerMove(819, "Gigaton Hammer", PokemonType.STEEL, MoveCategory.PHYSICAL, 160, 100, 5)
        registerMove(820, "Comeuppance", PokemonType.DARK, MoveCategory.PHYSICAL, 1, 100, 10)
        registerMove(821, "Aqua Cutter", PokemonType.WATER, MoveCategory.PHYSICAL, 70, 100, 20)
        registerMove(822, "Blazing Torque", PokemonType.FIRE, MoveCategory.PHYSICAL, 80, 100, 10)
        registerMove(823, "Wicked Torque", PokemonType.DARK, MoveCategory.PHYSICAL, 80, 100, 10)
        registerMove(824, "Noxious Torque", PokemonType.POISON, MoveCategory.PHYSICAL, 100, 100, 10)
        registerMove(825, "Combat Torque", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 100, 100, 10)
        registerMove(826, "Magical Torque", PokemonType.FAIRY, MoveCategory.PHYSICAL, 100, 100, 10)
        registerMove(827, "Psyblade", PokemonType.PSYCHIC, MoveCategory.PHYSICAL, 80, 100, 15)
        registerMove(828, "Hydro Steam", PokemonType.WATER, MoveCategory.SPECIAL, 80, 100, 15)
        registerMove(829, "Blood Moon", PokemonType.NORMAL, MoveCategory.SPECIAL, 140, 100, 5)
        registerMove(830, "Matcha Gotcha", PokemonType.GRASS, MoveCategory.SPECIAL, 80, 90, 15)
        registerMove(831, "Syrup Bomb", PokemonType.GRASS, MoveCategory.SPECIAL, 60, 85, 10)
        registerMove(832, "Ivy Cudgel", PokemonType.GRASS, MoveCategory.PHYSICAL, 100, 100, 10)
        registerMove(833, "Electro Shot", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 130, 100, 10)
        registerMove(834, "Tera Starstorm", PokemonType.NORMAL, MoveCategory.SPECIAL, 120, 100, 5)
        registerMove(835, "Fickle Beam", PokemonType.DRAGON, MoveCategory.SPECIAL, 80, 100, 5)
        registerMove(836, "Burning Bulwark", PokemonType.FIRE, MoveCategory.STATUS, 0, 0, 10)
        registerMove(837, "Thunderclap", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 70, 100, 5)
        registerMove(838, "Mighty Cleave", PokemonType.ROCK, MoveCategory.PHYSICAL, 95, 100, 5)
        registerMove(839, "Tachyon Cutter", PokemonType.STEEL, MoveCategory.SPECIAL, 50, 0, 10)
        registerMove(840, "Hard Press", PokemonType.STEEL, MoveCategory.PHYSICAL, 100, 100, 10)
        registerMove(841, "Dragon Cheer", PokemonType.DRAGON, MoveCategory.STATUS, 0, 0, 15)
        registerMove(842, "Alluring Voice", PokemonType.FAIRY, MoveCategory.SPECIAL, 80, 100, 10)
        registerMove(843, "Temper Flare", PokemonType.FIRE, MoveCategory.PHYSICAL, 75, 100, 10)
        registerMove(844, "Supercell Slam", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 100, 95, 15)
        registerMove(845, "Psychic Noise", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 75, 100, 10)
        registerMove(846, "Upper Hand", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 65, 100, 15)
        registerMove(847, "Malignant Chain", PokemonType.POISON, MoveCategory.SPECIAL, 100, 100, 5)
        registerMove(848, "Breakneck Blitz", PokemonType.NORMAL, MoveCategory.PHYSICAL, 1, 0, 1)
        registerMove(849, "All-Out Pummeling", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 1, 0, 1)
        registerMove(850, "Supersonic Skystrike", PokemonType.FLYING, MoveCategory.PHYSICAL, 1, 0, 1)
        registerMove(851, "Acid Downpour", PokemonType.POISON, MoveCategory.PHYSICAL, 1, 0, 1)
        registerMove(852, "Tectonic Rage", PokemonType.GROUND, MoveCategory.PHYSICAL, 1, 0, 1)
        registerMove(853, "Continental Crush", PokemonType.ROCK, MoveCategory.PHYSICAL, 1, 0, 1)
        registerMove(854, "Savage Spin-Out", PokemonType.BUG, MoveCategory.PHYSICAL, 1, 0, 1)
        registerMove(855, "Never-Ending Nightmare", PokemonType.GHOST, MoveCategory.PHYSICAL, 1, 0, 1)
        registerMove(856, "Corkscrew Crash", PokemonType.STEEL, MoveCategory.PHYSICAL, 1, 0, 1)
        registerMove(857, "Inferno Overdrive", PokemonType.FIRE, MoveCategory.PHYSICAL, 1, 0, 1)
        registerMove(858, "Hydro Vortex", PokemonType.WATER, MoveCategory.PHYSICAL, 1, 0, 1)
        registerMove(859, "Bloom Doom", PokemonType.GRASS, MoveCategory.PHYSICAL, 1, 0, 1)
        registerMove(860, "Gigavolt Havoc", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 1, 0, 1)
        registerMove(861, "Shattered Psyche", PokemonType.PSYCHIC, MoveCategory.PHYSICAL, 1, 0, 1)
        registerMove(862, "Subzero Slammer", PokemonType.ICE, MoveCategory.PHYSICAL, 1, 0, 1)
        registerMove(863, "Devastating Drake", PokemonType.DRAGON, MoveCategory.PHYSICAL, 1, 0, 1)
        registerMove(864, "Black Hole Eclipse", PokemonType.DARK, MoveCategory.PHYSICAL, 1, 0, 1)
        registerMove(865, "Twinkle Tackle", PokemonType.FAIRY, MoveCategory.PHYSICAL, 1, 0, 1)
        registerMove(866, "Catastropika", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 210, 0, 1)
        registerMove(867, "10,000,000 Volt Thunderbolt", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 195, 0, 1)
        registerMove(868, "Stoked Sparksurfer", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 175, 0, 1)
        registerMove(869, "Extreme Evoboost", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 1)
        registerMove(870, "Pulverizing Pancake", PokemonType.NORMAL, MoveCategory.PHYSICAL, 210, 0, 1)
        registerMove(871, "Genesis Supernova", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 185, 0, 1)
        registerMove(872, "Sinister Arrow Raid", PokemonType.GHOST, MoveCategory.PHYSICAL, 180, 0, 1)
        registerMove(873, "Malicious Moonsault", PokemonType.DARK, MoveCategory.PHYSICAL, 180, 0, 1)
        registerMove(874, "Oceanic Operetta", PokemonType.WATER, MoveCategory.SPECIAL, 195, 0, 1)
        registerMove(875, "Splintered Stormshards", PokemonType.ROCK, MoveCategory.PHYSICAL, 190, 0, 1)
        registerMove(876, "Let's Snuggle Forever", PokemonType.FAIRY, MoveCategory.PHYSICAL, 190, 0, 1)
        registerMove(877, "Clangorous Soulblaze", PokemonType.DRAGON, MoveCategory.SPECIAL, 185, 0, 1)
        registerMove(878, "Guardian Of Alola", PokemonType.FAIRY, MoveCategory.SPECIAL, 1, 0, 1)
        registerMove(879, "Searing Sunraze Smash", PokemonType.STEEL, MoveCategory.PHYSICAL, 200, 0, 1)
        registerMove(880, "Menacing Moonraze Maelstrom", PokemonType.GHOST, MoveCategory.SPECIAL, 200, 0, 1)
        registerMove(881, "Light That Burns The Sky", PokemonType.PSYCHIC, MoveCategory.SPECIAL, 200, 0, 1)
        registerMove(882, "Soul-Stealing 7-Star Strike", PokemonType.GHOST, MoveCategory.PHYSICAL, 195, 0, 1)
        registerMove(883, "Max Guard", PokemonType.NORMAL, MoveCategory.STATUS, 0, 0, 10)
        registerMove(884, "Max Strike", PokemonType.NORMAL, MoveCategory.PHYSICAL, 1, 0, 10)
        registerMove(885, "Max Knuckle", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 1, 0, 10)
        registerMove(886, "Max Airstream", PokemonType.FLYING, MoveCategory.PHYSICAL, 1, 0, 10)
        registerMove(887, "Max Ooze", PokemonType.POISON, MoveCategory.PHYSICAL, 1, 0, 10)
        registerMove(888, "Max Quake", PokemonType.GROUND, MoveCategory.PHYSICAL, 1, 0, 10)
        registerMove(889, "Max Rockfall", PokemonType.ROCK, MoveCategory.PHYSICAL, 1, 0, 10)
        registerMove(890, "Max Flutterby", PokemonType.BUG, MoveCategory.PHYSICAL, 1, 0, 10)
        registerMove(891, "Max Phantasm", PokemonType.GHOST, MoveCategory.PHYSICAL, 1, 0, 10)
        registerMove(892, "Max Steelspike", PokemonType.STEEL, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(893, "Max Flare", PokemonType.FIRE, MoveCategory.PHYSICAL, 1, 0, 10)
        registerMove(894, "Max Geyser", PokemonType.WATER, MoveCategory.PHYSICAL, 1, 0, 10)
        registerMove(895, "Max Overgrowth", PokemonType.GRASS, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(896, "Max Lightning", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 1, 0, 10)
        registerMove(897, "Max Mindstorm", PokemonType.PSYCHIC, MoveCategory.PHYSICAL, 1, 0, 10)
        registerMove(898, "Max Hailstorm", PokemonType.ICE, MoveCategory.PHYSICAL, 1, 0, 10)
        registerMove(899, "Max Wyrmwind", PokemonType.DRAGON, MoveCategory.PHYSICAL, 1, 0, 10)
        registerMove(900, "Max Darkness", PokemonType.DARK, MoveCategory.PHYSICAL, 1, 0, 10)
        registerMove(901, "Max Starfall", PokemonType.FAIRY, MoveCategory.PHYSICAL, 1, 0, 10)
        registerMove(902, "G-Max Vine Lash", PokemonType.GRASS, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(903, "G-Max Wildfire", PokemonType.FIRE, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(904, "G-Max Cannonade", PokemonType.WATER, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(905, "G-Max Befuddle", PokemonType.BUG, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(906, "G-Max Volt Crash", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(907, "G-Max Gold Rush", PokemonType.NORMAL, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(908, "G-Max Chi Strike", PokemonType.FIGHTING, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(909, "G-Max Terror", PokemonType.GHOST, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(910, "G-Max Foam Burst", PokemonType.WATER, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(911, "G-Max Resonance", PokemonType.ICE, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(912, "G-Max Cuddle", PokemonType.NORMAL, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(913, "G-Max Replenish", PokemonType.NORMAL, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(914, "G-Max Malodor", PokemonType.POISON, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(915, "G-Max Meltdown", PokemonType.STEEL, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(916, "G-Max Drum Solo", PokemonType.GRASS, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(917, "G-Max Fireball", PokemonType.FIRE, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(918, "G-Max Hydrosnipe", PokemonType.WATER, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(919, "G-Max Wind Rage", PokemonType.FLYING, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(920, "G-Max Gravitas", PokemonType.PSYCHIC, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(921, "G-Max Stonesurge", PokemonType.WATER, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(922, "G-Max Volcalith", PokemonType.ROCK, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(923, "G-Max Tartness", PokemonType.GRASS, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(924, "G-Max Sweetness", PokemonType.GRASS, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(925, "G-Max Sandblast", PokemonType.GROUND, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(926, "G-Max Stun Shock", PokemonType.ELECTRIC, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(927, "G-Max Centiferno", PokemonType.FIRE, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(928, "G-Max Smite", PokemonType.FAIRY, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(929, "G-Max Snooze", PokemonType.DARK, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(930, "G-Max Finale", PokemonType.FAIRY, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(931, "G-Max Steelsurge", PokemonType.STEEL, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(932, "G-Max Depletion", PokemonType.DRAGON, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(933, "G-Max One Blow", PokemonType.DARK, MoveCategory.PHYSICAL, 10, 0, 10)
        registerMove(934, "G-Max Rapid Flow", PokemonType.WATER, MoveCategory.PHYSICAL, 10, 0, 10)
    }

}
