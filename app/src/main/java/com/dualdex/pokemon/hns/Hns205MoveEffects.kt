package com.dualdex.pokemon.hns

/**
 * Exact H&S 2.0.5 move ID -> `enum BattleMoveEffects` symbol, plus the
 * source-derived ordinary-damage move set.
 *
 * Provenance:
 *   Repository: PokemonHnS-Development/pokehns-expansion
 *   Tag: Release-v2.0.5
 *   Commit: 1f42b74dff0e9fe942419845d040663dd829a973
 *   Extraction: raw designated initializers from src/data/moves_info.h
 *   ROM dependency: NONE
 *
 * `effectById` omits a move whose own effect is conditional or computed: the
 * capability gate treats an unknown effect as unsupported rather than guess the
 * build configuration.
 *
 * `ordinaryMoveIds` is the subset whose damage the generation III pipeline is proven
 * to reproduce: `EFFECT_HIT` with no multi-hit, explosion, always-crit or
 * unmodelled state-dependent damage flag. `ignoresTargetAbility` is delegated to
 * the request-local Group C ability layer.
 *
 * `targetClassByMoveId` maps move IDs to the INTERNAL `SpreadTargetClass`
 * values (see Hns205MoveEffects.SpreadTargetClass below). Those are the EXACT
 * values of the pinned H&S `enum MoveTarget` members, carried verbatim; the
 * map is NOT the pinned enum itself and must never be renumbered.
 * Only spread classes (BOTH=6, FOES_AND_ALLY=11) affect the damage formula's
 * spread reduction. Other classes are preserved with their exact enum values;
 * unsupported/ambiguous classes fail closed on the consumer side.
 *
 * DO NOT EDIT DIRECTLY. Regenerate using:
 *   python3 tools/hns-move-mechanics/generate_hns_move_effects.py
 */
internal object Hns205MoveEffects {
    /** Pinned move ID -> the build's own effect symbol. */
    val effectById: Map<Int, String> = buildMap {
        put(1, "EFFECT_HIT")
        put(2, "EFFECT_HIT")
        put(3, "EFFECT_HIT")
        put(4, "EFFECT_HIT")
        put(5, "EFFECT_HIT")
        put(6, "EFFECT_HIT")
        put(7, "EFFECT_HIT")
        put(8, "EFFECT_HIT")
        put(9, "EFFECT_HIT")
        put(10, "EFFECT_HIT")
        put(11, "EFFECT_HIT")
        put(12, "EFFECT_OHKO")
        put(13, "EFFECT_TWO_TURNS_ATTACK")
        put(14, "EFFECT_ATTACK_UP_2")
        put(15, "EFFECT_HIT")
        put(16, "EFFECT_HIT")
        put(17, "EFFECT_HIT")
        put(18, "EFFECT_ROAR")
        put(19, "EFFECT_SEMI_INVULNERABLE")
        put(20, "EFFECT_HIT")
        put(21, "EFFECT_HIT")
        put(22, "EFFECT_HIT")
        put(23, "EFFECT_HIT")
        put(24, "EFFECT_HIT")
        put(25, "EFFECT_HIT")
        put(26, "EFFECT_RECOIL_IF_MISS")
        put(27, "EFFECT_HIT")
        put(28, "EFFECT_ACCURACY_DOWN")
        put(29, "EFFECT_HIT")
        put(30, "EFFECT_HIT")
        put(31, "EFFECT_HIT")
        put(32, "EFFECT_OHKO")
        put(33, "EFFECT_HIT")
        put(34, "EFFECT_HIT")
        put(35, "EFFECT_HIT")
        put(36, "EFFECT_RECOIL")
        put(37, "EFFECT_HIT")
        put(38, "EFFECT_RECOIL")
        put(39, "EFFECT_DEFENSE_DOWN")
        put(40, "EFFECT_HIT")
        put(41, "EFFECT_HIT")
        put(42, "EFFECT_HIT")
        put(43, "EFFECT_DEFENSE_DOWN")
        put(44, "EFFECT_HIT")
        put(45, "EFFECT_ATTACK_DOWN")
        put(46, "EFFECT_ROAR")
        put(47, "EFFECT_NON_VOLATILE_STATUS")
        put(48, "EFFECT_CONFUSE")
        put(49, "EFFECT_FIXED_HP_DAMAGE")
        put(50, "EFFECT_DISABLE")
        put(51, "EFFECT_HIT")
        put(52, "EFFECT_HIT")
        put(53, "EFFECT_HIT")
        put(54, "EFFECT_MIST")
        put(55, "EFFECT_HIT")
        put(56, "EFFECT_HIT")
        put(57, "EFFECT_HIT")
        put(58, "EFFECT_HIT")
        put(59, "EFFECT_HIT")
        put(60, "EFFECT_HIT")
        put(61, "EFFECT_HIT")
        put(62, "EFFECT_HIT")
        put(63, "EFFECT_HIT")
        put(64, "EFFECT_HIT")
        put(65, "EFFECT_HIT")
        put(66, "EFFECT_RECOIL")
        put(68, "EFFECT_REFLECT_DAMAGE")
        put(69, "EFFECT_LEVEL_DAMAGE")
        put(70, "EFFECT_HIT")
        put(71, "EFFECT_ABSORB")
        put(72, "EFFECT_ABSORB")
        put(73, "EFFECT_LEECH_SEED")
        put(75, "EFFECT_HIT")
        put(76, "EFFECT_SOLAR_BEAM")
        put(77, "EFFECT_NON_VOLATILE_STATUS")
        put(78, "EFFECT_NON_VOLATILE_STATUS")
        put(79, "EFFECT_NON_VOLATILE_STATUS")
        put(80, "EFFECT_HIT")
        put(82, "EFFECT_FIXED_HP_DAMAGE")
        put(83, "EFFECT_HIT")
        put(84, "EFFECT_HIT")
        put(85, "EFFECT_HIT")
        put(86, "EFFECT_NON_VOLATILE_STATUS")
        put(87, "EFFECT_HIT")
        put(88, "EFFECT_HIT")
        put(89, "EFFECT_EARTHQUAKE")
        put(90, "EFFECT_OHKO")
        put(91, "EFFECT_SEMI_INVULNERABLE")
        put(92, "EFFECT_NON_VOLATILE_STATUS")
        put(93, "EFFECT_HIT")
        put(94, "EFFECT_HIT")
        put(95, "EFFECT_NON_VOLATILE_STATUS")
        put(96, "EFFECT_ATTACK_UP")
        put(97, "EFFECT_SPEED_UP_2")
        put(98, "EFFECT_HIT")
        put(99, "EFFECT_HIT")
        put(100, "EFFECT_TELEPORT")
        put(101, "EFFECT_LEVEL_DAMAGE")
        put(102, "EFFECT_MIMIC")
        put(103, "EFFECT_DEFENSE_DOWN_2")
        put(104, "EFFECT_EVASION_UP")
        put(105, "EFFECT_RESTORE_HP")
        put(106, "EFFECT_DEFENSE_UP")
        put(107, "EFFECT_MINIMIZE")
        put(108, "EFFECT_ACCURACY_DOWN")
        put(109, "EFFECT_CONFUSE")
        put(110, "EFFECT_DEFENSE_UP")
        put(111, "EFFECT_DEFENSE_CURL")
        put(112, "EFFECT_DEFENSE_UP_2")
        put(113, "EFFECT_LIGHT_SCREEN")
        put(114, "EFFECT_HAZE")
        put(115, "EFFECT_REFLECT")
        put(116, "EFFECT_FOCUS_ENERGY")
        put(117, "EFFECT_BIDE")
        put(118, "EFFECT_METRONOME")
        put(119, "EFFECT_MIRROR_MOVE")
        put(120, "EFFECT_HIT")
        put(121, "EFFECT_HIT")
        put(122, "EFFECT_HIT")
        put(123, "EFFECT_HIT")
        put(124, "EFFECT_HIT")
        put(125, "EFFECT_HIT")
        put(126, "EFFECT_HIT")
        put(127, "EFFECT_HIT")
        put(128, "EFFECT_HIT")
        put(129, "EFFECT_HIT")
        put(130, "EFFECT_TWO_TURNS_ATTACK")
        put(131, "EFFECT_HIT")
        put(132, "EFFECT_HIT")
        put(133, "EFFECT_SPECIAL_DEFENSE_UP_2")
        put(134, "EFFECT_ACCURACY_DOWN")
        put(135, "EFFECT_SOFTBOILED")
        put(136, "EFFECT_RECOIL_IF_MISS")
        put(137, "EFFECT_NON_VOLATILE_STATUS")
        put(138, "EFFECT_DREAM_EATER")
        put(139, "EFFECT_NON_VOLATILE_STATUS")
        put(140, "EFFECT_HIT")
        put(141, "EFFECT_ABSORB")
        put(142, "EFFECT_NON_VOLATILE_STATUS")
        put(143, "EFFECT_TWO_TURNS_ATTACK")
        put(144, "EFFECT_TRANSFORM")
        put(145, "EFFECT_HIT")
        put(146, "EFFECT_HIT")
        put(147, "EFFECT_NON_VOLATILE_STATUS")
        put(148, "EFFECT_ACCURACY_DOWN")
        put(149, "EFFECT_PSYWAVE")
        put(150, "EFFECT_DO_NOTHING")
        put(151, "EFFECT_DEFENSE_UP_2")
        put(152, "EFFECT_HIT")
        put(153, "EFFECT_HIT")
        put(154, "EFFECT_HIT")
        put(155, "EFFECT_HIT")
        put(156, "EFFECT_REST")
        put(157, "EFFECT_HIT")
        put(158, "EFFECT_HIT")
        put(159, "EFFECT_ATTACK_UP")
        put(160, "EFFECT_CONVERSION")
        put(161, "EFFECT_HIT")
        put(162, "EFFECT_FIXED_PERCENT_DAMAGE")
        put(163, "EFFECT_HIT")
        put(164, "EFFECT_SUBSTITUTE")
        put(166, "EFFECT_SKETCH")
        put(167, "EFFECT_TRIPLE_KICK")
        put(168, "EFFECT_STEAL_ITEM")
        put(169, "EFFECT_MEAN_LOOK")
        put(170, "EFFECT_LOCK_ON")
        put(171, "EFFECT_NIGHTMARE")
        put(172, "EFFECT_HIT")
        put(173, "EFFECT_SNORE")
        put(174, "EFFECT_CURSE")
        put(175, "EFFECT_FLAIL")
        put(176, "EFFECT_CONVERSION_2")
        put(177, "EFFECT_HIT")
        put(178, "EFFECT_SPEED_DOWN_2")
        put(179, "EFFECT_FLAIL")
        put(180, "EFFECT_SPITE")
        put(181, "EFFECT_HIT")
        put(182, "EFFECT_PROTECT")
        put(183, "EFFECT_HIT")
        put(184, "EFFECT_SPEED_DOWN_2")
        put(185, "EFFECT_HIT")
        put(186, "EFFECT_CONFUSE")
        put(187, "EFFECT_BELLY_DRUM")
        put(188, "EFFECT_HIT")
        put(189, "EFFECT_HIT")
        put(190, "EFFECT_HIT")
        put(191, "EFFECT_SPIKES")
        put(192, "EFFECT_HIT")
        put(193, "EFFECT_FORESIGHT")
        put(194, "EFFECT_DESTINY_BOND")
        put(195, "EFFECT_PERISH_SONG")
        put(196, "EFFECT_HIT")
        put(197, "EFFECT_PROTECT")
        put(198, "EFFECT_HIT")
        put(199, "EFFECT_LOCK_ON")
        put(200, "EFFECT_HIT")
        put(201, "EFFECT_WEATHER")
        put(202, "EFFECT_ABSORB")
        put(203, "EFFECT_ENDURE")
        put(204, "EFFECT_ATTACK_DOWN_2")
        put(205, "EFFECT_ROLLOUT")
        put(206, "EFFECT_FALSE_SWIPE")
        put(207, "EFFECT_SWAGGER")
        put(208, "EFFECT_SOFTBOILED")
        put(209, "EFFECT_HIT")
        put(210, "EFFECT_FURY_CUTTER")
        put(211, "EFFECT_HIT")
        put(212, "EFFECT_MEAN_LOOK")
        put(213, "EFFECT_ATTRACT")
        put(214, "EFFECT_SLEEP_TALK")
        put(215, "EFFECT_HEAL_BELL")
        put(216, "EFFECT_RETURN")
        put(217, "EFFECT_PRESENT")
        put(218, "EFFECT_FRUSTRATION")
        put(219, "EFFECT_SAFEGUARD")
        put(220, "EFFECT_PAIN_SPLIT")
        put(221, "EFFECT_HIT")
        put(222, "EFFECT_MAGNITUDE")
        put(223, "EFFECT_HIT")
        put(224, "EFFECT_HIT")
        put(225, "EFFECT_HIT")
        put(226, "EFFECT_BATON_PASS")
        put(227, "EFFECT_ENCORE")
        put(228, "EFFECT_PURSUIT")
        put(229, "EFFECT_RAPID_SPIN")
        put(231, "EFFECT_HIT")
        put(232, "EFFECT_HIT")
        put(233, "EFFECT_HIT")
        put(234, "EFFECT_MORNING_SUN")
        put(235, "EFFECT_SYNTHESIS")
        put(236, "EFFECT_MOONLIGHT")
        put(237, "EFFECT_HIDDEN_POWER")
        put(238, "EFFECT_HIT")
        put(239, "EFFECT_HIT")
        put(240, "EFFECT_WEATHER")
        put(241, "EFFECT_WEATHER")
        put(242, "EFFECT_HIT")
        put(243, "EFFECT_REFLECT_DAMAGE")
        put(244, "EFFECT_PSYCH_UP")
        put(245, "EFFECT_HIT")
        put(246, "EFFECT_HIT")
        put(247, "EFFECT_HIT")
        put(248, "EFFECT_FUTURE_SIGHT")
        put(249, "EFFECT_HIT")
        put(250, "EFFECT_HIT")
        put(251, "EFFECT_BEAT_UP")
        put(252, "EFFECT_FIRST_TURN_ONLY")
        put(253, "EFFECT_UPROAR")
        put(254, "EFFECT_STOCKPILE")
        put(255, "EFFECT_SPIT_UP")
        put(256, "EFFECT_SWALLOW")
        put(257, "EFFECT_HIT")
        put(258, "EFFECT_WEATHER")
        put(259, "EFFECT_TORMENT")
        put(260, "EFFECT_FLATTER")
        put(261, "EFFECT_NON_VOLATILE_STATUS")
        put(262, "EFFECT_MEMENTO")
        put(263, "EFFECT_FACADE")
        put(264, "EFFECT_FOCUS_PUNCH")
        put(265, "EFFECT_DOUBLE_POWER_ON_ARG_STATUS")
        put(266, "EFFECT_FOLLOW_ME")
        put(267, "EFFECT_NATURE_POWER")
        put(268, "EFFECT_CHARGE")
        put(269, "EFFECT_TAUNT")
        put(270, "EFFECT_HELPING_HAND")
        put(271, "EFFECT_TRICK")
        put(272, "EFFECT_ROLE_PLAY")
        put(273, "EFFECT_WISH")
        put(274, "EFFECT_ASSIST")
        put(275, "EFFECT_INGRAIN")
        put(276, "EFFECT_HIT")
        put(277, "EFFECT_MAGIC_COAT")
        put(278, "EFFECT_RECYCLE")
        put(279, "EFFECT_REVENGE")
        put(280, "EFFECT_HIT")
        put(281, "EFFECT_YAWN")
        put(282, "EFFECT_KNOCK_OFF")
        put(283, "EFFECT_ENDEAVOR")
        put(284, "EFFECT_POWER_BASED_ON_USER_HP")
        put(285, "EFFECT_SKILL_SWAP")
        put(286, "EFFECT_IMPRISON")
        put(287, "EFFECT_REFRESH")
        put(288, "EFFECT_GRUDGE")
        put(289, "EFFECT_SNATCH")
        put(290, "EFFECT_HIT")
        put(291, "EFFECT_SEMI_INVULNERABLE")
        put(292, "EFFECT_HIT")
        put(293, "EFFECT_CAMOUFLAGE")
        put(295, "EFFECT_HIT")
        put(296, "EFFECT_HIT")
        put(297, "EFFECT_ATTACK_DOWN_2")
        put(298, "EFFECT_CONFUSE")
        put(299, "EFFECT_HIT")
        put(300, "EFFECT_MUD_SPORT")
        put(301, "EFFECT_ROLLOUT")
        put(302, "EFFECT_HIT")
        put(303, "EFFECT_RESTORE_HP")
        put(304, "EFFECT_HIT")
        put(305, "EFFECT_HIT")
        put(306, "EFFECT_HIT")
        put(307, "EFFECT_HIT")
        put(308, "EFFECT_HIT")
        put(309, "EFFECT_HIT")
        put(310, "EFFECT_HIT")
        put(311, "EFFECT_WEATHER_BALL")
        put(312, "EFFECT_HEAL_BELL")
        put(313, "EFFECT_SPECIAL_DEFENSE_DOWN_2")
        put(314, "EFFECT_HIT")
        put(315, "EFFECT_HIT")
        put(316, "EFFECT_FORESIGHT")
        put(317, "EFFECT_HIT")
        put(318, "EFFECT_HIT")
        put(319, "EFFECT_SPECIAL_DEFENSE_DOWN_2")
        put(320, "EFFECT_NON_VOLATILE_STATUS")
        put(321, "EFFECT_TICKLE")
        put(322, "EFFECT_COSMIC_POWER")
        put(323, "EFFECT_POWER_BASED_ON_USER_HP")
        put(324, "EFFECT_HIT")
        put(325, "EFFECT_HIT")
        put(326, "EFFECT_HIT")
        put(327, "EFFECT_HIT")
        put(328, "EFFECT_HIT")
        put(329, "EFFECT_OHKO")
        put(330, "EFFECT_HIT")
        put(331, "EFFECT_HIT")
        put(332, "EFFECT_HIT")
        put(333, "EFFECT_HIT")
        put(334, "EFFECT_DEFENSE_UP_2")
        put(335, "EFFECT_MEAN_LOOK")
        put(336, "EFFECT_ATTACK_UP")
        put(337, "EFFECT_HIT")
        put(338, "EFFECT_HIT")
        put(339, "EFFECT_BULK_UP")
        put(340, "EFFECT_SEMI_INVULNERABLE")
        put(341, "EFFECT_HIT")
        put(342, "EFFECT_HIT")
        put(343, "EFFECT_STEAL_ITEM")
        put(344, "EFFECT_RECOIL")
        put(345, "EFFECT_HIT")
        put(346, "EFFECT_WATER_SPORT")
        put(347, "EFFECT_CALM_MIND")
        put(348, "EFFECT_HIT")
        put(349, "EFFECT_DRAGON_DANCE")
        put(350, "EFFECT_HIT")
        put(351, "EFFECT_HIT")
        put(352, "EFFECT_HIT")
        put(353, "EFFECT_FUTURE_SIGHT")
        put(354, "EFFECT_HIT")
        put(355, "EFFECT_ROOST")
        put(356, "EFFECT_GRAVITY")
        put(357, "EFFECT_MIRACLE_EYE")
        put(358, "EFFECT_DOUBLE_POWER_ON_ARG_STATUS")
        put(359, "EFFECT_HIT")
        put(360, "EFFECT_GYRO_BALL")
        put(361, "EFFECT_HEALING_WISH")
        put(362, "EFFECT_BRINE")
        put(363, "EFFECT_NATURAL_GIFT")
        put(364, "EFFECT_HIT")
        put(365, "EFFECT_HIT")
        put(366, "EFFECT_TAILWIND")
        put(367, "EFFECT_ACUPRESSURE")
        put(368, "EFFECT_REFLECT_DAMAGE")
        put(369, "EFFECT_HIT_ESCAPE")
        put(370, "EFFECT_HIT")
        put(371, "EFFECT_PAYBACK")
        put(372, "EFFECT_ASSURANCE")
        put(373, "EFFECT_EMBARGO")
        put(374, "EFFECT_FLING")
        put(375, "EFFECT_PSYCHO_SHIFT")
        put(376, "EFFECT_TRUMP_CARD")
        put(377, "EFFECT_HEAL_BLOCK")
        put(378, "EFFECT_POWER_BASED_ON_TARGET_HP")
        put(379, "EFFECT_POWER_TRICK")
        put(380, "EFFECT_GASTRO_ACID")
        put(381, "EFFECT_LUCKY_CHANT")
        put(382, "EFFECT_ME_FIRST")
        put(383, "EFFECT_COPYCAT")
        put(384, "EFFECT_POWER_SWAP")
        put(385, "EFFECT_GUARD_SWAP")
        put(386, "EFFECT_PUNISHMENT")
        put(387, "EFFECT_LAST_RESORT")
        put(388, "EFFECT_OVERWRITE_ABILITY")
        put(389, "EFFECT_SUCKER_PUNCH")
        put(390, "EFFECT_TOXIC_SPIKES")
        put(391, "EFFECT_HEART_SWAP")
        put(392, "EFFECT_AQUA_RING")
        put(393, "EFFECT_MAGNET_RISE")
        put(394, "EFFECT_RECOIL")
        put(395, "EFFECT_HIT")
        put(396, "EFFECT_HIT")
        put(397, "EFFECT_SPEED_UP_2")
        put(398, "EFFECT_HIT")
        put(399, "EFFECT_HIT")
        put(400, "EFFECT_HIT")
        put(401, "EFFECT_HIT")
        put(402, "EFFECT_HIT")
        put(403, "EFFECT_HIT")
        put(404, "EFFECT_HIT")
        put(405, "EFFECT_HIT")
        put(406, "EFFECT_HIT")
        put(407, "EFFECT_HIT")
        put(408, "EFFECT_HIT")
        put(409, "EFFECT_ABSORB")
        put(410, "EFFECT_HIT")
        put(411, "EFFECT_HIT")
        put(412, "EFFECT_HIT")
        put(413, "EFFECT_RECOIL")
        put(414, "EFFECT_HIT")
        put(415, "EFFECT_TRICK")
        put(416, "EFFECT_HIT")
        put(417, "EFFECT_SPECIAL_ATTACK_UP_2")
        put(418, "EFFECT_HIT")
        put(419, "EFFECT_REVENGE")
        put(420, "EFFECT_HIT")
        put(421, "EFFECT_HIT")
        put(422, "EFFECT_HIT")
        put(423, "EFFECT_HIT")
        put(424, "EFFECT_HIT")
        put(425, "EFFECT_HIT")
        put(426, "EFFECT_HIT")
        put(427, "EFFECT_HIT")
        put(428, "EFFECT_HIT")
        put(429, "EFFECT_HIT")
        put(430, "EFFECT_HIT")
        put(431, "EFFECT_HIT")
        put(432, "EFFECT_DEFOG")
        put(433, "EFFECT_TRICK_ROOM")
        put(434, "EFFECT_HIT")
        put(435, "EFFECT_HIT")
        put(436, "EFFECT_HIT")
        put(437, "EFFECT_HIT")
        put(438, "EFFECT_HIT")
        put(439, "EFFECT_HIT")
        put(440, "EFFECT_HIT")
        put(441, "EFFECT_HIT")
        put(442, "EFFECT_HIT")
        put(443, "EFFECT_HIT")
        put(444, "EFFECT_HIT")
        put(445, "EFFECT_CAPTIVATE")
        put(446, "EFFECT_STEALTH_ROCK")
        put(447, "EFFECT_LOW_KICK")
        put(448, "EFFECT_HIT")
        put(449, "EFFECT_CHANGE_TYPE_ON_ITEM")
        put(450, "EFFECT_HIT")
        put(451, "EFFECT_HIT")
        put(452, "EFFECT_RECOIL")
        put(453, "EFFECT_HIT")
        put(454, "EFFECT_HIT")
        put(455, "EFFECT_COSMIC_POWER")
        put(456, "EFFECT_RESTORE_HP")
        put(457, "EFFECT_RECOIL")
        put(458, "EFFECT_HIT")
        put(459, "EFFECT_HIT")
        put(460, "EFFECT_HIT")
        put(461, "EFFECT_LUNAR_DANCE")
        put(462, "EFFECT_POWER_BASED_ON_TARGET_HP")
        put(463, "EFFECT_HIT")
        put(464, "EFFECT_DARK_VOID")
        put(465, "EFFECT_HIT")
        put(466, "EFFECT_HIT")
        put(467, "EFFECT_SEMI_INVULNERABLE")
        put(468, "EFFECT_ATTACK_ACCURACY_UP")
        put(469, "EFFECT_PROTECT")
        put(470, "EFFECT_GUARD_SPLIT")
        put(471, "EFFECT_POWER_SPLIT")
        put(472, "EFFECT_WONDER_ROOM")
        put(473, "EFFECT_PSYSHOCK")
        put(474, "EFFECT_DOUBLE_POWER_ON_ARG_STATUS")
        put(475, "EFFECT_AUTOTOMIZE")
        put(476, "EFFECT_FOLLOW_ME")
        put(477, "EFFECT_TELEKINESIS")
        put(478, "EFFECT_MAGIC_ROOM")
        put(479, "EFFECT_SMACK_DOWN")
        put(480, "EFFECT_HIT")
        put(481, "EFFECT_HIT")
        put(482, "EFFECT_HIT")
        put(483, "EFFECT_QUIVER_DANCE")
        put(484, "EFFECT_HEAT_CRASH")
        put(485, "EFFECT_SYNCHRONOISE")
        put(486, "EFFECT_ELECTRO_BALL")
        put(487, "EFFECT_SOAK")
        put(488, "EFFECT_HIT")
        put(489, "EFFECT_COIL")
        put(490, "EFFECT_HIT")
        put(491, "EFFECT_HIT")
        put(492, "EFFECT_FOUL_PLAY")
        put(493, "EFFECT_OVERWRITE_ABILITY")
        put(494, "EFFECT_ENTRAINMENT")
        put(495, "EFFECT_AFTER_YOU")
        put(496, "EFFECT_ROUND")
        put(497, "EFFECT_ECHOED_VOICE")
        put(498, "EFFECT_HIT")
        put(499, "EFFECT_HIT")
        put(500, "EFFECT_STORED_POWER")
        put(501, "EFFECT_PROTECT")
        put(502, "EFFECT_ALLY_SWITCH")
        put(503, "EFFECT_HIT")
        put(504, "EFFECT_SHELL_SMASH")
        put(505, "EFFECT_HEAL_PULSE")
        put(506, "EFFECT_DOUBLE_POWER_ON_ARG_STATUS")
        put(507, "EFFECT_SKY_DROP")
        put(508, "EFFECT_SHIFT_GEAR")
        put(509, "EFFECT_HIT_SWITCH_TARGET")
        put(510, "EFFECT_HIT")
        put(511, "EFFECT_QUASH")
        put(512, "EFFECT_ACROBATICS")
        put(513, "EFFECT_REFLECT_TYPE")
        put(514, "EFFECT_RETALIATE")
        put(515, "EFFECT_FINAL_GAMBIT")
        put(516, "EFFECT_BESTOW")
        put(517, "EFFECT_HIT")
        put(518, "EFFECT_PLEDGE")
        put(519, "EFFECT_PLEDGE")
        put(520, "EFFECT_PLEDGE")
        put(521, "EFFECT_HIT_ESCAPE")
        put(522, "EFFECT_HIT")
        put(523, "EFFECT_EARTHQUAKE")
        put(524, "EFFECT_HIT")
        put(525, "EFFECT_HIT_SWITCH_TARGET")
        put(526, "EFFECT_ATTACK_SPATK_UP")
        put(527, "EFFECT_HIT")
        put(528, "EFFECT_RECOIL")
        put(529, "EFFECT_HIT")
        put(530, "EFFECT_HIT")
        put(531, "EFFECT_HIT")
        put(532, "EFFECT_ABSORB")
        put(533, "EFFECT_HIT")
        put(534, "EFFECT_HIT")
        put(535, "EFFECT_HEAT_CRASH")
        put(536, "EFFECT_HIT")
        put(537, "EFFECT_HIT")
        put(538, "EFFECT_DEFENSE_UP_3")
        put(539, "EFFECT_HIT")
        put(540, "EFFECT_PSYSHOCK")
        put(541, "EFFECT_HIT")
        put(542, "EFFECT_HIT")
        put(543, "EFFECT_RECOIL")
        put(544, "EFFECT_HIT")
        put(545, "EFFECT_HIT")
        put(546, "EFFECT_CHANGE_TYPE_ON_ITEM")
        put(547, "EFFECT_HIT")
        put(548, "EFFECT_PSYSHOCK")
        put(549, "EFFECT_HIT")
        put(550, "EFFECT_HIT")
        put(551, "EFFECT_HIT")
        put(552, "EFFECT_HIT")
        put(553, "EFFECT_TWO_TURNS_ATTACK")
        put(554, "EFFECT_TWO_TURNS_ATTACK")
        put(555, "EFFECT_HIT")
        put(556, "EFFECT_HIT")
        put(557, "EFFECT_HIT")
        put(558, "EFFECT_FUSION_COMBO")
        put(559, "EFFECT_FUSION_COMBO")
        put(560, "EFFECT_TWO_TYPED_MOVE")
        put(561, "EFFECT_MAT_BLOCK")
        put(562, "EFFECT_BELCH")
        put(563, "EFFECT_ROTOTILLER")
        put(564, "EFFECT_STICKY_WEB")
        put(565, "EFFECT_FELL_STINGER")
        put(566, "EFFECT_SEMI_INVULNERABLE")
        put(567, "EFFECT_THIRD_TYPE")
        put(568, "EFFECT_NOBLE_ROAR")
        put(569, "EFFECT_ION_DELUGE")
        put(570, "EFFECT_ABSORB")
        put(571, "EFFECT_THIRD_TYPE")
        put(572, "EFFECT_HIT")
        put(573, "EFFECT_SUPER_EFFECTIVE_ON_ARG")
        put(574, "EFFECT_HIT")
        put(575, "EFFECT_PARTING_SHOT")
        put(576, "EFFECT_TOPSY_TURVY")
        put(577, "EFFECT_ABSORB")
        put(578, "EFFECT_PROTECT")
        put(579, "EFFECT_FLOWER_SHIELD")
        put(580, "EFFECT_GRASSY_TERRAIN")
        put(581, "EFFECT_MISTY_TERRAIN")
        put(582, "EFFECT_ELECTRIFY")
        put(583, "EFFECT_HIT")
        put(584, "EFFECT_HIT")
        put(585, "EFFECT_HIT")
        put(586, "EFFECT_HIT")
        put(587, "EFFECT_FAIRY_LOCK")
        put(588, "EFFECT_PROTECT")
        put(589, "EFFECT_ATTACK_DOWN")
        put(590, "EFFECT_SPECIAL_ATTACK_DOWN")
        put(591, "EFFECT_HIT")
        put(592, "EFFECT_HIT")
        put(593, "EFFECT_HIT")
        put(594, "EFFECT_SPECIES_POWER_OVERRIDE")
        put(595, "EFFECT_HIT")
        put(596, "EFFECT_PROTECT")
        put(597, "EFFECT_AROMATIC_MIST")
        put(598, "EFFECT_SPECIAL_ATTACK_DOWN_2")
        put(599, "EFFECT_VENOM_DRENCH")
        put(600, "EFFECT_POWDER")
        put(601, "EFFECT_GEOMANCY")
        put(602, "EFFECT_MAGNETIC_FLUX")
        put(603, "EFFECT_HAPPY_HOUR")
        put(604, "EFFECT_ELECTRIC_TERRAIN")
        put(605, "EFFECT_HIT")
        put(606, "EFFECT_CELEBRATE")
        put(607, "EFFECT_HOLD_HANDS")
        put(608, "EFFECT_ATTACK_DOWN")
        put(609, "EFFECT_HIT")
        put(610, "EFFECT_FALSE_SWIPE")
        put(611, "EFFECT_HIT")
        put(612, "EFFECT_HIT")
        put(613, "EFFECT_ABSORB")
        put(614, "EFFECT_SMACK_DOWN")
        put(615, "EFFECT_HIT")
        put(616, "EFFECT_HIT")
        put(617, "EFFECT_RECOIL")
        put(618, "EFFECT_HIT")
        put(619, "EFFECT_HIT")
        put(620, "EFFECT_HIT")
        put(621, "EFFECT_HYPERSPACE_FURY")
        put(622, "EFFECT_SHORE_UP")
        put(623, "EFFECT_FIRST_TURN_ONLY")
        put(624, "EFFECT_PROTECT")
        put(625, "EFFECT_HIT")
        put(626, "EFFECT_HIT")
        put(627, "EFFECT_HIT")
        put(628, "EFFECT_HIT")
        put(629, "EFFECT_HEAL_PULSE")
        put(630, "EFFECT_HIT")
        put(631, "EFFECT_STRENGTH_SAP")
        put(632, "EFFECT_SOLAR_BEAM")
        put(633, "EFFECT_HIT")
        put(634, "EFFECT_FOLLOW_ME")
        put(635, "EFFECT_TOXIC_THREAD")
        put(636, "EFFECT_LASER_FOCUS")
        put(637, "EFFECT_GEAR_UP")
        put(638, "EFFECT_HIT")
        put(639, "EFFECT_HIT_ENEMY_HEAL_ALLY")
        put(640, "EFFECT_HIT")
        put(641, "EFFECT_PSYCHIC_TERRAIN")
        put(642, "EFFECT_HIT")
        put(643, "EFFECT_HIT")
        put(644, "EFFECT_STORED_POWER")
        put(645, "EFFECT_FAIL_IF_NOT_ARG_TYPE")
        put(646, "EFFECT_SPEED_SWAP")
        put(647, "EFFECT_HIT")
        put(648, "EFFECT_PURIFY")
        put(649, "EFFECT_REVELATION_DANCE")
        put(650, "EFFECT_HIT")
        put(651, "EFFECT_HIT")
        put(652, "EFFECT_INSTRUCT")
        put(653, "EFFECT_BEAK_BLAST")
        put(654, "EFFECT_HIT")
        put(655, "EFFECT_HIT")
        put(656, "EFFECT_HIT")
        put(657, "EFFECT_AURORA_VEIL")
        put(658, "EFFECT_SHELL_TRAP")
        put(659, "EFFECT_HIT")
        put(660, "EFFECT_HIT")
        put(661, "EFFECT_STOMPING_TANTRUM")
        put(662, "EFFECT_HIT")
        put(663, "EFFECT_HIT")
        put(664, "EFFECT_HIT")
        put(665, "EFFECT_HIT")
        put(666, "EFFECT_HIT")
        put(667, "EFFECT_HIT")
        put(668, "EFFECT_HIT")
        put(669, "EFFECT_NOBLE_ROAR")
        put(670, "EFFECT_HIT")
        put(671, "EFFECT_FIXED_PERCENT_DAMAGE")
        put(672, "EFFECT_CHANGE_TYPE_ON_ITEM")
        put(673, "EFFECT_MAX_HP_50_RECOIL")
        put(674, "EFFECT_HIT")
        put(675, "EFFECT_PHOTON_GEYSER")
        put(676, "EFFECT_HIT")
        put(677, "EFFECT_HIT")
        put(678, "EFFECT_HIT")
        put(679, "EFFECT_RETURN")
        put(680, "EFFECT_ABSORB")
        put(681, "EFFECT_HIT")
        put(682, "EFFECT_HIT")
        put(683, "EFFECT_HIT")
        put(684, "EFFECT_HIT")
        put(685, "EFFECT_HIT")
        put(686, "EFFECT_HIT")
        put(687, "EFFECT_HIT")
        put(688, "EFFECT_RETURN")
        put(689, "EFFECT_HIT")
        put(690, "EFFECT_DYNAMAX_DOUBLE_DMG")
        put(691, "EFFECT_SNIPE_SHOT")
        put(692, "EFFECT_HIT")
        put(693, "EFFECT_STUFF_CHEEKS")
        put(694, "EFFECT_NO_RETREAT")
        put(695, "EFFECT_TAR_SHOT")
        put(696, "EFFECT_SOAK")
        put(697, "EFFECT_HIT")
        put(698, "EFFECT_TEATIME")
        put(699, "EFFECT_OCTOLOCK")
        put(700, "EFFECT_BOLT_BEAK")
        put(701, "EFFECT_BOLT_BEAK")
        put(702, "EFFECT_COURT_CHANGE")
        put(703, "EFFECT_CLANGOROUS_SOUL")
        put(704, "EFFECT_BODY_PRESS")
        put(705, "EFFECT_DECORATE")
        put(706, "EFFECT_HIT")
        put(707, "EFFECT_HIT")
        put(708, "EFFECT_HIT")
        put(709, "EFFECT_DYNAMAX_DOUBLE_DMG")
        put(710, "EFFECT_DYNAMAX_DOUBLE_DMG")
        put(711, "EFFECT_AURA_WHEEL")
        put(712, "EFFECT_HIT")
        put(713, "EFFECT_HIT")
        put(714, "EFFECT_HIT")
        put(715, "EFFECT_HIT")
        put(716, "EFFECT_GRAV_APPLE")
        put(717, "EFFECT_HIT")
        put(718, "EFFECT_HIT")
        put(719, "EFFECT_LIFE_DEW")
        put(720, "EFFECT_PROTECT")
        put(721, "EFFECT_HIT")
        put(722, "EFFECT_HIT")
        put(723, "EFFECT_HIT")
        put(724, "EFFECT_MAX_HP_50_RECOIL")
        put(725, "EFFECT_TERRAIN_BOOST")
        put(726, "EFFECT_STEEL_ROLLER")
        put(727, "EFFECT_HIT")
        put(728, "EFFECT_TWO_TURNS_ATTACK")
        put(729, "EFFECT_SHELL_SIDE_ARM")
        put(730, "EFFECT_TERRAIN_BOOST")
        put(731, "EFFECT_GRASSY_GLIDE")
        put(732, "EFFECT_TERRAIN_BOOST")
        put(733, "EFFECT_TERRAIN_PULSE")
        put(734, "EFFECT_HIT")
        put(735, "EFFECT_HIT")
        put(736, "EFFECT_LASH_OUT")
        put(737, "EFFECT_POLTERGEIST")
        put(738, "EFFECT_CORROSIVE_GAS")
        put(739, "EFFECT_COACHING")
        put(740, "EFFECT_HIT_ESCAPE")
        put(741, "EFFECT_TRIPLE_KICK")
        put(742, "EFFECT_HIT")
        put(743, "EFFECT_HIT")
        put(744, "EFFECT_JUNGLE_HEALING")
        put(745, "EFFECT_HIT")
        put(746, "EFFECT_HIT")
        put(747, "EFFECT_HIT")
        put(748, "EFFECT_POWER_BASED_ON_USER_HP")
        put(749, "EFFECT_HIT")
        put(750, "EFFECT_HIT")
        put(751, "EFFECT_HIT")
        put(752, "EFFECT_HIT")
        put(753, "EFFECT_HIT")
        put(754, "EFFECT_HIT")
        put(755, "EFFECT_HIT")
        put(756, "EFFECT_HIT")
        put(757, "EFFECT_POWER_TRICK")
        put(758, "EFFECT_STONE_AXE")
        put(759, "EFFECT_HIT")
        put(760, "EFFECT_HIT")
        put(761, "EFFECT_HIT")
        put(762, "EFFECT_RECOIL")
        put(763, "EFFECT_CHLOROBLAST")
        put(764, "EFFECT_HIT")
        put(765, "EFFECT_VICTORY_DANCE")
        put(766, "EFFECT_HIT")
        put(767, "EFFECT_DOUBLE_POWER_ON_ARG_STATUS")
        put(768, "EFFECT_HIT")
        put(769, "EFFECT_HIT")
        put(770, "EFFECT_DEFENSE_UP_2")
        put(771, "EFFECT_HIT")
        put(772, "EFFECT_DOUBLE_POWER_ON_ARG_STATUS")
        put(773, "EFFECT_CEASELESS_EDGE")
        put(774, "EFFECT_HIT")
        put(775, "EFFECT_HIT")
        put(776, "EFFECT_HIT")
        put(777, "EFFECT_JUNGLE_HEALING")
        put(778, "EFFECT_TAKE_HEART")
        put(779, "EFFECT_TERA_BLAST")
        put(780, "EFFECT_PROTECT")
        put(781, "EFFECT_RECOIL_IF_MISS")
        put(782, "EFFECT_LAST_RESPECTS")
        put(783, "EFFECT_HIT")
        put(784, "EFFECT_HIT")
        put(785, "EFFECT_HIT")
        put(786, "EFFECT_SPICY_EXTRACT")
        put(787, "EFFECT_HIT")
        put(788, "EFFECT_POPULATION_BOMB")
        put(789, "EFFECT_ICE_SPINNER")
        put(790, "EFFECT_HIT")
        put(791, "EFFECT_REVIVAL_BLESSING")
        put(792, "EFFECT_HIT")
        put(793, "EFFECT_HIT")
        put(794, "EFFECT_RAPID_SPIN")
        put(795, "EFFECT_DOODLE")
        put(796, "EFFECT_FILLET_AWAY")
        put(797, "EFFECT_HIT")
        put(798, "EFFECT_HIT")
        put(799, "EFFECT_HIT")
        put(800, "EFFECT_HIT")
        put(801, "EFFECT_RAGING_BULL")
        put(802, "EFFECT_HIT")
        put(803, "EFFECT_FIXED_PERCENT_DAMAGE")
        put(804, "EFFECT_COLLISION_COURSE")
        put(805, "EFFECT_COLLISION_COURSE")
        put(806, "EFFECT_SHED_TAIL")
        put(807, "EFFECT_WEATHER_AND_SWITCH")
        put(808, "EFFECT_TIDY_UP")
        put(809, "EFFECT_WEATHER")
        put(810, "EFFECT_HIT")
        put(811, "EFFECT_HIT")
        put(812, "EFFECT_HIT")
        put(813, "EFFECT_HIT")
        put(814, "EFFECT_HIT")
        put(815, "EFFECT_RAGE_FIST")
        put(816, "EFFECT_HIT")
        put(817, "EFFECT_ABSORB")
        put(818, "EFFECT_FAIL_IF_NOT_ARG_TYPE")
        put(819, "EFFECT_HIT")
        put(820, "EFFECT_REFLECT_DAMAGE")
        put(821, "EFFECT_HIT")
        put(822, "EFFECT_HIT")
        put(823, "EFFECT_HIT")
        put(824, "EFFECT_HIT")
        put(825, "EFFECT_HIT")
        put(826, "EFFECT_HIT")
        put(827, "EFFECT_TERRAIN_BOOST")
        put(828, "EFFECT_HYDRO_STEAM")
        put(829, "EFFECT_HIT")
        put(830, "EFFECT_ABSORB")
        put(831, "EFFECT_HIT")
        put(832, "EFFECT_IVY_CUDGEL")
        put(833, "EFFECT_TWO_TURNS_ATTACK")
        put(834, "EFFECT_TERA_STARSTORM")
        put(835, "EFFECT_FICKLE_BEAM")
        put(836, "EFFECT_PROTECT")
        put(837, "EFFECT_SUCKER_PUNCH")
        put(838, "EFFECT_HIT")
        put(839, "EFFECT_HIT")
        put(840, "EFFECT_POWER_BASED_ON_TARGET_HP")
        put(841, "EFFECT_DRAGON_CHEER")
        put(842, "EFFECT_HIT")
        put(843, "EFFECT_STOMPING_TANTRUM")
        put(844, "EFFECT_RECOIL_IF_MISS")
        put(845, "EFFECT_HIT")
        put(846, "EFFECT_UPPER_HAND")
        put(847, "EFFECT_HIT")
        put(848, "EFFECT_HIT")
        put(849, "EFFECT_HIT")
        put(850, "EFFECT_HIT")
        put(851, "EFFECT_HIT")
        put(852, "EFFECT_HIT")
        put(853, "EFFECT_HIT")
        put(854, "EFFECT_HIT")
        put(855, "EFFECT_HIT")
        put(856, "EFFECT_HIT")
        put(857, "EFFECT_HIT")
        put(858, "EFFECT_HIT")
        put(859, "EFFECT_HIT")
        put(860, "EFFECT_HIT")
        put(861, "EFFECT_HIT")
        put(862, "EFFECT_HIT")
        put(863, "EFFECT_HIT")
        put(864, "EFFECT_HIT")
        put(865, "EFFECT_HIT")
        put(866, "EFFECT_HIT")
        put(867, "EFFECT_HIT")
        put(868, "EFFECT_HIT")
        put(869, "EFFECT_EXTREME_EVOBOOST")
        put(870, "EFFECT_HIT")
        put(871, "EFFECT_HIT")
        put(872, "EFFECT_HIT")
        put(873, "EFFECT_HIT")
        put(874, "EFFECT_HIT")
        put(875, "EFFECT_ICE_SPINNER")
        put(876, "EFFECT_HIT")
        put(877, "EFFECT_HIT")
        put(878, "EFFECT_FIXED_PERCENT_DAMAGE")
        put(879, "EFFECT_HIT")
        put(880, "EFFECT_HIT")
        put(881, "EFFECT_PHOTON_GEYSER")
        put(882, "EFFECT_HIT")
        put(883, "EFFECT_PROTECT")
        put(884, "EFFECT_MAX_MOVE")
        put(885, "EFFECT_MAX_MOVE")
        put(886, "EFFECT_MAX_MOVE")
        put(887, "EFFECT_MAX_MOVE")
        put(888, "EFFECT_MAX_MOVE")
        put(889, "EFFECT_MAX_MOVE")
        put(890, "EFFECT_MAX_MOVE")
        put(891, "EFFECT_MAX_MOVE")
        put(892, "EFFECT_MAX_MOVE")
        put(893, "EFFECT_MAX_MOVE")
        put(894, "EFFECT_MAX_MOVE")
        put(895, "EFFECT_MAX_MOVE")
        put(896, "EFFECT_MAX_MOVE")
        put(897, "EFFECT_MAX_MOVE")
        put(898, "EFFECT_MAX_MOVE")
        put(899, "EFFECT_MAX_MOVE")
        put(900, "EFFECT_MAX_MOVE")
        put(901, "EFFECT_MAX_MOVE")
        put(902, "EFFECT_MAX_MOVE")
        put(903, "EFFECT_MAX_MOVE")
        put(904, "EFFECT_MAX_MOVE")
        put(905, "EFFECT_MAX_MOVE")
        put(906, "EFFECT_MAX_MOVE")
        put(907, "EFFECT_MAX_MOVE")
        put(908, "EFFECT_MAX_MOVE")
        put(909, "EFFECT_MAX_MOVE")
        put(910, "EFFECT_MAX_MOVE")
        put(911, "EFFECT_MAX_MOVE")
        put(912, "EFFECT_MAX_MOVE")
        put(913, "EFFECT_MAX_MOVE")
        put(914, "EFFECT_MAX_MOVE")
        put(915, "EFFECT_MAX_MOVE")
        put(916, "EFFECT_MAX_MOVE")
        put(917, "EFFECT_MAX_MOVE")
        put(918, "EFFECT_MAX_MOVE")
        put(919, "EFFECT_MAX_MOVE")
        put(920, "EFFECT_MAX_MOVE")
        put(921, "EFFECT_MAX_MOVE")
        put(922, "EFFECT_MAX_MOVE")
        put(923, "EFFECT_MAX_MOVE")
        put(924, "EFFECT_MAX_MOVE")
        put(925, "EFFECT_MAX_MOVE")
        put(926, "EFFECT_MAX_MOVE")
        put(927, "EFFECT_MAX_MOVE")
        put(928, "EFFECT_MAX_MOVE")
        put(929, "EFFECT_MAX_MOVE")
        put(930, "EFFECT_MAX_MOVE")
        put(931, "EFFECT_MAX_MOVE")
        put(932, "EFFECT_MAX_MOVE")
        put(933, "EFFECT_MAX_MOVE")
        put(934, "EFFECT_MAX_MOVE")
    }
    /** Pinned MoveMakesContact(move), with unresolved initializers kept distinct from false. */
    val makesContactById: Map<Int, Boolean> by lazy { buildMap {
        put(1, true)
        put(2, true)
        put(3, true)
        put(4, true)
        put(5, true)
        put(6, false)
        put(7, true)
        put(8, true)
        put(9, true)
        put(10, true)
        put(11, true)
        put(12, true)
        put(13, false)
        put(14, false)
        put(15, true)
        put(16, false)
        put(17, true)
        put(18, false)
        put(19, true)
        put(20, true)
        put(21, true)
        put(22, true)
        put(23, true)
        put(24, true)
        put(25, true)
        put(26, true)
        put(27, true)
        put(28, false)
        put(29, true)
        put(30, true)
        put(31, true)
        put(32, true)
        put(33, true)
        put(34, true)
        put(35, true)
        put(36, true)
        put(37, true)
        put(38, true)
        put(39, false)
        put(40, false)
        put(41, false)
        put(42, false)
        put(43, false)
        put(44, true)
        put(45, false)
        put(46, false)
        put(47, false)
        put(48, false)
        put(49, false)
        put(50, false)
        put(51, false)
        put(52, false)
        put(53, false)
        put(54, false)
        put(55, false)
        put(56, false)
        put(57, false)
        put(58, false)
        put(59, false)
        put(60, false)
        put(61, false)
        put(62, false)
        put(63, false)
        put(64, true)
        put(65, true)
        put(66, true)
        put(67, true)
        put(68, true)
        put(69, true)
        put(70, true)
        put(71, false)
        put(72, false)
        put(73, false)
        put(74, false)
        put(75, false)
        put(76, false)
        put(77, false)
        put(78, false)
        put(79, false)
        put(80, true)
        put(81, false)
        put(82, false)
        put(83, false)
        put(84, false)
        put(85, false)
        put(86, false)
        put(87, false)
        put(88, false)
        put(89, false)
        put(90, false)
        put(91, true)
        put(92, false)
        put(93, false)
        put(94, false)
        put(95, false)
        put(96, false)
        put(97, false)
        put(98, true)
        put(99, true)
        put(100, false)
        put(101, false)
        put(102, false)
        put(103, false)
        put(104, false)
        put(105, false)
        put(106, false)
        put(107, false)
        put(108, false)
        put(109, false)
        put(110, false)
        put(111, false)
        put(112, false)
        put(113, false)
        put(114, false)
        put(115, false)
        put(116, false)
        put(117, true)
        put(118, false)
        put(119, false)
        put(120, false)
        put(121, false)
        put(122, true)
        put(123, false)
        put(124, false)
        put(125, false)
        put(126, false)
        put(127, true)
        put(128, true)
        put(129, false)
        put(130, true)
        put(131, false)
        put(132, true)
        put(133, false)
        put(134, false)
        put(135, false)
        put(136, true)
        put(137, false)
        put(138, false)
        put(139, false)
        put(140, false)
        put(141, true)
        put(142, false)
        put(143, false)
        put(144, false)
        put(145, false)
        put(146, true)
        put(147, false)
        put(148, false)
        put(149, false)
        put(150, false)
        put(151, false)
        put(152, true)
        put(153, false)
        put(154, true)
        put(155, false)
        put(156, false)
        put(157, false)
        put(158, true)
        put(159, false)
        put(160, false)
        put(161, false)
        put(162, true)
        put(163, true)
        put(164, false)
        put(165, true)
        put(166, false)
        put(167, true)
        put(168, true)
        put(169, false)
        put(170, false)
        put(171, false)
        put(172, true)
        put(173, false)
        put(174, false)
        put(175, true)
        put(176, false)
        put(177, false)
        put(178, false)
        put(179, true)
        put(180, false)
        put(181, false)
        put(182, false)
        put(183, true)
        put(184, false)
        put(185, false)
        put(186, false)
        put(187, false)
        put(188, false)
        put(189, false)
        put(190, false)
        put(191, false)
        put(192, false)
        put(193, false)
        put(194, false)
        put(195, false)
        put(196, false)
        put(197, false)
        put(198, false)
        put(199, false)
        put(200, true)
        put(201, false)
        put(202, false)
        put(203, false)
        put(204, false)
        put(205, true)
        put(206, true)
        put(207, false)
        put(208, false)
        put(209, true)
        put(210, true)
        put(211, true)
        put(212, false)
        put(213, false)
        put(214, false)
        put(215, false)
        put(216, true)
        put(217, false)
        put(218, true)
        put(219, false)
        put(220, false)
        put(221, false)
        put(222, false)
        put(223, true)
        put(224, true)
        put(225, false)
        put(226, false)
        put(227, false)
        put(228, true)
        put(229, true)
        put(230, false)
        put(231, true)
        put(232, true)
        put(233, true)
        put(234, false)
        put(235, false)
        put(236, false)
        put(237, false)
        put(238, true)
        put(239, false)
        put(240, false)
        put(241, false)
        put(242, true)
        put(243, false)
        put(244, false)
        put(245, true)
        put(246, false)
        put(247, false)
        put(248, false)
        put(249, true)
        put(250, false)
        put(251, false)
        put(252, false)
        put(253, false)
        put(254, false)
        put(255, false)
        put(256, false)
        put(257, false)
        put(258, false)
        put(259, false)
        put(260, false)
        put(261, false)
        put(262, false)
        put(263, true)
        put(264, true)
        put(265, true)
        put(266, false)
        put(267, false)
        put(268, false)
        put(269, false)
        put(270, false)
        put(271, false)
        put(272, false)
        put(273, false)
        put(274, false)
        put(275, false)
        put(276, true)
        put(277, false)
        put(278, false)
        put(279, true)
        put(280, true)
        put(281, false)
        put(282, true)
        put(283, true)
        put(284, false)
        put(285, false)
        put(286, false)
        put(287, false)
        put(288, false)
        put(289, false)
        put(290, false)
        put(291, true)
        put(292, true)
        put(293, false)
        put(294, false)
        put(295, false)
        put(296, false)
        put(297, false)
        put(298, false)
        put(299, true)
        put(300, false)
        put(301, true)
        put(302, true)
        put(303, false)
        put(304, false)
        put(305, true)
        put(306, true)
        put(307, false)
        put(308, false)
        put(309, true)
        put(310, true)
        put(311, false)
        put(312, false)
        put(313, false)
        put(314, false)
        put(315, false)
        put(316, false)
        put(317, false)
        put(318, false)
        put(319, false)
        put(320, false)
        put(321, false)
        put(322, false)
        put(323, false)
        put(324, false)
        put(325, true)
        put(326, false)
        put(327, true)
        put(328, false)
        put(329, false)
        put(330, false)
        put(331, false)
        put(332, true)
        put(333, false)
        put(334, false)
        put(335, false)
        put(336, false)
        put(337, true)
        put(338, false)
        put(339, false)
        put(340, true)
        put(341, false)
        put(342, true)
        put(343, false)
        put(344, true)
        put(345, false)
        put(346, false)
        put(347, false)
        put(348, true)
        put(349, false)
        put(350, false)
        put(351, false)
        put(352, false)
        put(353, false)
        put(354, false)
        put(355, false)
        put(356, false)
        put(357, false)
        put(358, true)
        put(359, true)
        put(360, true)
        put(361, false)
        put(362, false)
        put(363, false)
        put(364, false)
        put(365, true)
        put(366, false)
        put(367, false)
        put(368, false)
        put(369, true)
        put(370, true)
        put(371, true)
        put(372, true)
        put(373, false)
        put(374, false)
        put(375, false)
        put(376, true)
        put(377, false)
        put(378, true)
        put(379, false)
        put(380, false)
        put(381, false)
        put(382, false)
        put(383, false)
        put(384, false)
        put(385, false)
        put(386, true)
        put(387, true)
        put(388, false)
        put(389, true)
        put(390, false)
        put(391, false)
        put(392, false)
        put(393, false)
        put(394, true)
        put(395, true)
        put(396, false)
        put(397, false)
        put(398, true)
        put(399, false)
        put(400, true)
        put(401, true)
        put(402, false)
        put(403, false)
        put(404, true)
        put(405, false)
        put(406, false)
        put(407, true)
        put(408, false)
        put(409, true)
        put(410, false)
        put(411, false)
        put(412, false)
        put(413, true)
        put(414, false)
        put(415, false)
        put(416, true)
        put(417, false)
        put(418, true)
        put(419, true)
        put(420, false)
        put(421, true)
        put(422, true)
        put(423, true)
        put(424, true)
        put(425, true)
        put(426, false)
        put(427, false)
        put(428, true)
        put(429, false)
        put(430, false)
        put(431, true)
        put(432, false)
        put(433, false)
        put(434, false)
        put(435, false)
        put(436, false)
        put(437, false)
        put(438, true)
        put(439, false)
        put(440, true)
        put(441, false)
        put(442, true)
        put(443, false)
        put(444, false)
        put(445, false)
        put(446, false)
        put(447, true)
        put(448, false)
        put(449, false)
        put(450, true)
        put(451, false)
        put(452, true)
        put(453, true)
        put(454, false)
        put(455, false)
        put(456, false)
        put(457, true)
        put(458, true)
        put(459, false)
        put(460, false)
        put(461, false)
        put(462, true)
        put(463, false)
        put(464, false)
        put(465, false)
        put(466, false)
        put(467, true)
        put(468, false)
        put(469, false)
        put(470, false)
        put(471, false)
        put(472, false)
        put(473, false)
        put(474, false)
        put(475, false)
        put(476, false)
        put(477, false)
        put(478, false)
        put(479, false)
        put(480, true)
        put(481, false)
        put(482, false)
        put(483, false)
        put(484, true)
        put(485, false)
        put(486, false)
        put(487, false)
        put(488, true)
        put(489, false)
        put(490, true)
        put(491, false)
        put(492, true)
        put(493, false)
        put(494, false)
        put(495, false)
        put(496, false)
        put(497, false)
        put(498, true)
        put(499, false)
        put(500, false)
        put(501, false)
        put(502, false)
        put(503, false)
        put(504, false)
        put(505, false)
        put(506, false)
        put(507, true)
        put(508, false)
        put(509, true)
        put(510, false)
        put(511, false)
        put(512, true)
        put(513, false)
        put(514, true)
        put(515, false)
        put(516, false)
        put(517, false)
        put(518, false)
        put(519, false)
        put(520, false)
        put(521, false)
        put(522, false)
        put(523, false)
        put(524, false)
        put(525, true)
        put(526, false)
        put(527, false)
        put(528, true)
        put(529, true)
        put(530, true)
        put(531, true)
        put(532, true)
        put(533, true)
        put(534, true)
        put(535, true)
        put(536, false)
        put(537, true)
        put(538, false)
        put(539, false)
        put(540, false)
        put(541, true)
        put(542, false)
        put(543, true)
        put(544, true)
        put(545, false)
        put(546, false)
        put(547, false)
        put(548, false)
        put(549, false)
        put(550, true)
        put(551, false)
        put(552, false)
        put(553, false)
        put(554, false)
        put(555, false)
        put(556, false)
        put(557, true)
        put(558, false)
        put(559, false)
        put(560, true)
        put(561, false)
        put(562, false)
        put(563, false)
        put(564, false)
        put(565, true)
        put(566, true)
        put(567, false)
        put(568, false)
        put(569, false)
        put(570, false)
        put(571, false)
        put(572, false)
        put(573, false)
        put(574, false)
        put(575, false)
        put(576, false)
        put(577, true)
        put(578, false)
        put(579, false)
        put(580, false)
        put(581, false)
        put(582, false)
        put(583, true)
        put(584, false)
        put(585, false)
        put(586, false)
        put(587, false)
        put(588, false)
        put(589, false)
        put(590, false)
        put(591, false)
        put(592, false)
        put(593, false)
        put(594, false)
        put(595, false)
        put(596, false)
        put(597, false)
        put(598, false)
        put(599, false)
        put(600, false)
        put(601, false)
        put(602, false)
        put(603, false)
        put(604, false)
        put(605, false)
        put(606, false)
        put(607, false)
        put(608, false)
        put(609, true)
        put(610, true)
        put(611, true)
        put(612, true)
        put(613, false)
        put(614, false)
        put(615, false)
        put(616, false)
        put(617, false)
        put(618, false)
        put(619, false)
        put(620, true)
        put(621, false)
        put(622, false)
        put(623, true)
        put(624, false)
        put(625, false)
        put(626, true)
        put(627, false)
        put(628, true)
        put(629, false)
        put(630, true)
        put(631, false)
        put(632, true)
        put(633, false)
        put(634, false)
        put(635, false)
        put(636, false)
        put(637, false)
        put(638, true)
        put(639, false)
        put(640, true)
        put(641, false)
        put(642, true)
        put(643, true)
        put(644, true)
        put(645, false)
        put(646, false)
        put(647, true)
        put(648, false)
        put(649, false)
        put(650, false)
        put(651, true)
        put(652, false)
        put(653, false)
        put(654, false)
        put(655, true)
        put(656, true)
        put(657, false)
        put(658, false)
        put(659, false)
        put(660, true)
        put(661, true)
        put(662, false)
        put(663, true)
        put(664, true)
        put(665, false)
        put(666, true)
        put(667, true)
        put(668, false)
        put(669, false)
        put(670, true)
        put(671, false)
        put(672, true)
        put(673, false)
        put(674, true)
        put(675, false)
        put(676, true)
        put(677, false)
        put(678, true)
        put(679, false)
        put(680, false)
        put(681, false)
        put(682, true)
        put(683, false)
        put(684, false)
        put(685, false)
        put(686, false)
        put(687, false)
        put(688, true)
        put(689, true)
        put(690, false)
        put(691, false)
        put(692, true)
        put(693, false)
        put(694, false)
        put(695, false)
        put(696, false)
        put(697, false)
        put(698, false)
        put(699, false)
        put(700, true)
        put(701, true)
        put(702, false)
        put(703, false)
        put(704, true)
        put(705, false)
        put(706, false)
        put(707, true)
        put(708, false)
        put(709, true)
        put(710, true)
        put(711, false)
        put(712, true)
        put(713, true)
        put(714, false)
        put(715, false)
        put(716, false)
        put(717, true)
        put(718, false)
        put(719, false)
        put(720, false)
        put(721, true)
        put(722, false)
        put(723, false)
        put(724, false)
        put(725, false)
        put(726, true)
        put(727, false)
        put(728, false)
        put(729, false)
        put(730, false)
        put(731, true)
        put(732, false)
        put(733, false)
        put(734, true)
        put(735, false)
        put(736, true)
        put(737, false)
        put(738, false)
        put(739, false)
        put(740, true)
        put(741, true)
        put(742, true)
        put(743, false)
        put(744, false)
        put(745, true)
        put(746, true)
        put(747, false)
        put(748, false)
        put(749, false)
        put(750, false)
        put(751, true)
        put(752, false)
        put(753, false)
        put(754, false)
        put(755, true)
        put(756, true)
        put(757, false)
        put(758, true)
        put(759, false)
        put(760, false)
        put(761, false)
        put(762, true)
        put(763, false)
        put(764, false)
        put(765, false)
        put(766, true)
        put(767, false)
        put(768, false)
        put(769, false)
        put(770, false)
        put(771, false)
        put(772, false)
        put(773, true)
        put(774, false)
        put(775, false)
        put(776, false)
        put(777, false)
        put(778, false)
        put(779, false)
        put(780, false)
        put(781, true)
        put(782, false)
        put(783, false)
        put(784, false)
        put(785, true)
        put(786, false)
        put(787, true)
        put(788, true)
        put(789, true)
        put(790, true)
        put(791, false)
        put(792, false)
        put(793, true)
        put(794, true)
        put(795, false)
        put(796, false)
        put(797, true)
        put(798, false)
        put(799, false)
        put(800, true)
        put(801, true)
        put(802, false)
        put(803, false)
        put(804, true)
        put(805, true)
        put(806, false)
        put(807, false)
        put(808, false)
        put(809, false)
        put(810, true)
        put(811, true)
        put(812, false)
        put(813, true)
        put(814, false)
        put(815, true)
        put(816, false)
        put(817, true)
        put(818, true)
        put(819, false)
        put(820, true)
        put(821, false)
        put(822, false)
        put(823, false)
        put(824, false)
        put(825, false)
        put(826, false)
        put(827, true)
        put(828, false)
        put(829, false)
        put(830, false)
        put(831, false)
        put(832, false)
        put(833, false)
        put(834, false)
        put(835, false)
        put(836, false)
        put(837, false)
        put(838, true)
        put(839, false)
        put(840, true)
        put(841, false)
        put(842, false)
        put(843, true)
        put(844, true)
        put(845, false)
        put(846, true)
        put(847, false)
        put(848, false)
        put(849, false)
        put(850, false)
        put(851, false)
        put(852, false)
        put(853, false)
        put(854, false)
        put(855, false)
        put(856, false)
        put(857, false)
        put(858, false)
        put(859, false)
        put(860, false)
        put(861, false)
        put(862, false)
        put(863, false)
        put(864, false)
        put(865, false)
        put(866, false)
        put(867, false)
        put(868, false)
        put(869, false)
        put(870, false)
        put(871, false)
        put(872, false)
        put(873, false)
        put(874, false)
        put(875, false)
        put(876, false)
        put(877, false)
        put(878, false)
        put(879, false)
        put(880, false)
        put(881, false)
        put(882, false)
        put(883, false)
        put(884, false)
        put(885, false)
        put(886, false)
        put(887, false)
        put(888, false)
        put(889, false)
        put(890, false)
        put(891, false)
        put(892, false)
        put(893, false)
        put(894, false)
        put(895, false)
        put(896, false)
        put(897, false)
        put(898, false)
        put(899, false)
        put(900, false)
        put(901, false)
        put(902, false)
        put(903, false)
        put(904, false)
        put(905, false)
        put(906, false)
        put(907, false)
        put(908, false)
        put(909, false)
        put(910, false)
        put(911, false)
        put(912, false)
        put(913, false)
        put(914, false)
        put(915, false)
        put(916, false)
        put(917, false)
        put(918, false)
        put(919, false)
        put(920, false)
        put(921, false)
        put(922, false)
        put(923, false)
        put(924, false)
        put(925, false)
        put(926, false)
        put(927, false)
        put(928, false)
        put(929, false)
        put(930, false)
        put(931, false)
        put(932, false)
        put(933, false)
        put(934, false)
    } }
    val unknownContactMoveIds: Set<Int> = setOf(
        185,
        246,
        252,
        315,
        343,
    )
    /** Pinned MoveIsAffectedBySheerForce(move), derived from every AdditionalEffect. */
    val sheerForceAffectedById: Map<Int, Boolean> by lazy { buildMap {
        put(1, false)
        put(2, false)
        put(3, false)
        put(4, false)
        put(5, false)
        put(6, false)
        put(7, true)
        put(8, true)
        put(9, true)
        put(10, false)
        put(11, false)
        put(12, false)
        put(13, false)
        put(14, false)
        put(15, false)
        put(16, false)
        put(17, false)
        put(18, false)
        put(19, false)
        put(20, false)
        put(21, false)
        put(22, false)
        put(23, true)
        put(24, false)
        put(25, false)
        put(26, false)
        put(27, true)
        put(28, false)
        put(29, true)
        put(30, false)
        put(31, false)
        put(32, false)
        put(33, false)
        put(34, true)
        put(35, false)
        put(36, false)
        put(37, false)
        put(38, false)
        put(39, false)
        put(40, true)
        put(41, true)
        put(42, false)
        put(43, false)
        put(44, true)
        put(45, false)
        put(46, false)
        put(47, false)
        put(48, false)
        put(49, false)
        put(50, false)
        put(51, true)
        put(52, true)
        put(53, true)
        put(54, false)
        put(55, false)
        put(56, false)
        put(57, false)
        put(58, true)
        put(59, true)
        put(60, true)
        put(61, true)
        put(62, true)
        put(63, false)
        put(64, false)
        put(65, false)
        put(66, false)
        put(68, false)
        put(69, false)
        put(70, false)
        put(71, false)
        put(72, false)
        put(73, false)
        put(74, false)
        put(75, false)
        put(76, false)
        put(77, false)
        put(78, false)
        put(79, false)
        put(80, false)
        put(81, false)
        put(82, false)
        put(83, false)
        put(84, true)
        put(85, true)
        put(86, false)
        put(87, true)
        put(88, false)
        put(89, false)
        put(90, false)
        put(91, false)
        put(92, false)
        put(93, true)
        put(94, true)
        put(95, false)
        put(96, false)
        put(97, false)
        put(98, false)
        put(99, false)
        put(100, false)
        put(101, false)
        put(102, false)
        put(103, false)
        put(104, false)
        put(105, false)
        put(106, false)
        put(107, false)
        put(108, false)
        put(109, false)
        put(110, false)
        put(111, false)
        put(112, false)
        put(113, false)
        put(114, false)
        put(115, false)
        put(116, false)
        put(117, false)
        put(118, false)
        put(119, false)
        put(120, false)
        put(121, false)
        put(122, true)
        put(123, true)
        put(124, true)
        put(125, true)
        put(126, true)
        put(128, false)
        put(129, false)
        put(131, false)
        put(132, true)
        put(133, false)
        put(134, false)
        put(135, false)
        put(136, false)
        put(137, false)
        put(138, false)
        put(139, false)
        put(140, false)
        put(141, false)
        put(142, false)
        put(144, false)
        put(145, true)
        put(147, false)
        put(148, false)
        put(149, false)
        put(150, false)
        put(151, false)
        put(152, false)
        put(153, false)
        put(154, false)
        put(155, false)
        put(156, false)
        put(158, true)
        put(159, false)
        put(160, false)
        put(162, false)
        put(163, false)
        put(164, false)
        put(166, false)
        put(167, false)
        put(168, false)
        put(169, false)
        put(170, false)
        put(171, false)
        put(172, true)
        put(173, true)
        put(174, false)
        put(175, false)
        put(176, false)
        put(177, false)
        put(178, false)
        put(179, false)
        put(180, false)
        put(181, true)
        put(182, false)
        put(183, false)
        put(184, false)
        put(185, false)
        put(186, false)
        put(187, false)
        put(188, true)
        put(189, true)
        put(190, true)
        put(191, false)
        put(192, true)
        put(193, false)
        put(194, false)
        put(195, false)
        put(196, true)
        put(197, false)
        put(198, false)
        put(199, false)
        put(200, false)
        put(201, false)
        put(202, false)
        put(203, false)
        put(204, false)
        put(205, false)
        put(206, false)
        put(207, false)
        put(208, false)
        put(209, true)
        put(210, false)
        put(211, true)
        put(212, false)
        put(213, false)
        put(214, false)
        put(215, false)
        put(216, false)
        put(217, false)
        put(218, false)
        put(219, false)
        put(220, false)
        put(221, true)
        put(222, false)
        put(223, true)
        put(224, false)
        put(225, true)
        put(226, false)
        put(227, false)
        put(228, false)
        put(230, false)
        put(231, true)
        put(232, true)
        put(233, false)
        put(234, false)
        put(235, false)
        put(236, false)
        put(237, false)
        put(238, false)
        put(239, true)
        put(240, false)
        put(241, false)
        put(243, false)
        put(244, false)
        put(245, false)
        put(246, true)
        put(247, true)
        put(248, false)
        put(249, true)
        put(250, false)
        put(251, false)
        put(252, true)
        put(253, false)
        put(254, false)
        put(255, false)
        put(256, false)
        put(257, true)
        put(258, false)
        put(259, false)
        put(260, false)
        put(261, false)
        put(262, false)
        put(263, false)
        put(264, false)
        put(265, false)
        put(266, false)
        put(267, false)
        put(268, false)
        put(269, false)
        put(270, false)
        put(271, false)
        put(272, false)
        put(273, false)
        put(274, false)
        put(275, false)
        put(276, false)
        put(277, false)
        put(278, false)
        put(279, false)
        put(280, false)
        put(281, false)
        put(282, false)
        put(283, false)
        put(284, false)
        put(285, false)
        put(286, false)
        put(287, false)
        put(288, false)
        put(289, false)
        put(290, true)
        put(291, false)
        put(292, false)
        put(293, false)
        put(294, false)
        put(295, true)
        put(296, true)
        put(297, false)
        put(298, false)
        put(299, true)
        put(300, false)
        put(301, false)
        put(302, true)
        put(303, false)
        put(304, false)
        put(305, true)
        put(306, true)
        put(307, false)
        put(308, false)
        put(309, true)
        put(310, true)
        put(311, false)
        put(312, false)
        put(313, false)
        put(314, false)
        put(315, false)
        put(316, false)
        put(317, true)
        put(318, true)
        put(319, false)
        put(320, false)
        put(321, false)
        put(322, false)
        put(323, false)
        put(324, true)
        put(325, false)
        put(326, true)
        put(327, false)
        put(328, false)
        put(329, false)
        put(330, true)
        put(331, false)
        put(332, false)
        put(333, false)
        put(334, false)
        put(335, false)
        put(336, false)
        put(337, false)
        put(338, false)
        put(339, false)
        put(340, true)
        put(341, true)
        put(342, true)
        put(343, false)
        put(344, true)
        put(345, false)
        put(346, false)
        put(347, false)
        put(348, false)
        put(349, false)
        put(350, false)
        put(351, false)
        put(352, true)
        put(353, false)
        put(354, false)
        put(355, false)
        put(356, false)
        put(357, false)
        put(358, false)
        put(359, false)
        put(360, false)
        put(361, false)
        put(362, false)
        put(363, false)
        put(364, false)
        put(365, false)
        put(366, false)
        put(367, false)
        put(368, false)
        put(369, false)
        put(370, false)
        put(371, false)
        put(372, false)
        put(373, false)
        put(374, false)
        put(375, false)
        put(376, false)
        put(377, false)
        put(378, false)
        put(379, false)
        put(380, false)
        put(381, false)
        put(382, false)
        put(383, false)
        put(384, false)
        put(385, false)
        put(386, false)
        put(387, false)
        put(388, false)
        put(389, false)
        put(390, false)
        put(391, false)
        put(392, false)
        put(393, false)
        put(394, true)
        put(395, true)
        put(396, false)
        put(397, false)
        put(398, true)
        put(399, true)
        put(400, false)
        put(401, false)
        put(402, false)
        put(403, true)
        put(404, false)
        put(405, true)
        put(406, false)
        put(407, true)
        put(408, false)
        put(409, false)
        put(410, false)
        put(411, true)
        put(412, true)
        put(413, false)
        put(414, true)
        put(415, false)
        put(416, false)
        put(417, false)
        put(418, false)
        put(419, false)
        put(420, false)
        put(421, false)
        put(422, true)
        put(423, true)
        put(424, true)
        put(425, false)
        put(426, true)
        put(427, false)
        put(428, true)
        put(429, true)
        put(430, true)
        put(431, true)
        put(432, false)
        put(433, false)
        put(434, false)
        put(435, true)
        put(436, true)
        put(437, false)
        put(438, false)
        put(439, false)
        put(440, true)
        put(441, true)
        put(442, true)
        put(443, false)
        put(444, false)
        put(445, false)
        put(446, false)
        put(447, false)
        put(449, false)
        put(450, false)
        put(451, true)
        put(452, false)
        put(453, false)
        put(454, false)
        put(455, false)
        put(456, false)
        put(457, false)
        put(458, false)
        put(459, false)
        put(460, false)
        put(461, false)
        put(462, false)
        put(463, false)
        put(464, false)
        put(465, true)
        put(466, true)
        put(467, false)
        put(468, false)
        put(469, false)
        put(470, false)
        put(471, false)
        put(472, false)
        put(473, false)
        put(474, false)
        put(475, false)
        put(476, false)
        put(477, false)
        put(478, false)
        put(479, false)
        put(480, false)
        put(481, false)
        put(482, true)
        put(483, false)
        put(484, false)
        put(485, false)
        put(486, false)
        put(487, false)
        put(488, true)
        put(489, false)
        put(490, true)
        put(491, true)
        put(492, false)
        put(493, false)
        put(494, false)
        put(495, false)
        put(496, false)
        put(497, false)
        put(498, false)
        put(499, false)
        put(500, false)
        put(501, false)
        put(502, false)
        put(503, true)
        put(504, false)
        put(505, false)
        put(506, false)
        put(507, false)
        put(508, false)
        put(509, false)
        put(510, false)
        put(511, false)
        put(512, false)
        put(513, false)
        put(514, false)
        put(515, false)
        put(516, false)
        put(517, true)
        put(518, false)
        put(519, false)
        put(520, false)
        put(521, false)
        put(522, true)
        put(523, true)
        put(524, false)
        put(525, false)
        put(526, false)
        put(527, true)
        put(528, false)
        put(529, false)
        put(530, false)
        put(531, true)
        put(532, false)
        put(533, false)
        put(534, true)
        put(535, false)
        put(536, true)
        put(537, true)
        put(538, false)
        put(539, true)
        put(540, false)
        put(541, false)
        put(542, true)
        put(543, false)
        put(544, false)
        put(545, true)
        put(546, false)
        put(547, true)
        put(548, false)
        put(549, true)
        put(550, true)
        put(551, true)
        put(552, true)
        put(553, true)
        put(554, true)
        put(555, true)
        put(556, true)
        put(557, false)
        put(558, false)
        put(559, false)
        put(560, false)
        put(561, false)
        put(562, false)
        put(563, false)
        put(564, false)
        put(565, false)
        put(566, false)
        put(567, false)
        put(568, false)
        put(569, false)
        put(570, false)
        put(571, false)
        put(572, false)
        put(573, true)
        put(574, false)
        put(575, false)
        put(576, false)
        put(577, false)
        put(578, false)
        put(579, false)
        put(580, false)
        put(581, false)
        put(582, false)
        put(583, true)
        put(584, false)
        put(585, true)
        put(586, false)
        put(587, false)
        put(588, false)
        put(589, false)
        put(590, false)
        put(591, true)
        put(592, true)
        put(593, false)
        put(594, false)
        put(595, true)
        put(596, false)
        put(597, false)
        put(598, false)
        put(599, false)
        put(600, false)
        put(601, false)
        put(602, false)
        put(603, false)
        put(604, false)
        put(605, false)
        put(606, false)
        put(607, false)
        put(608, false)
        put(609, true)
        put(610, false)
        put(611, false)
        put(612, true)
        put(613, false)
        put(614, false)
        put(615, false)
        put(616, false)
        put(617, false)
        put(618, false)
        put(619, false)
        put(620, false)
        put(621, false)
        put(622, false)
        put(623, false)
        put(624, false)
        put(625, true)
        put(626, false)
        put(627, true)
        put(628, false)
        put(629, false)
        put(630, false)
        put(631, false)
        put(632, false)
        put(633, false)
        put(634, false)
        put(635, false)
        put(636, false)
        put(637, false)
        put(638, true)
        put(639, false)
        put(640, true)
        put(641, false)
        put(642, true)
        put(643, true)
        put(644, false)
        put(645, false)
        put(646, false)
        put(647, false)
        put(648, false)
        put(649, false)
        put(650, false)
        put(651, true)
        put(652, false)
        put(653, false)
        put(654, false)
        put(655, false)
        put(656, false)
        put(657, false)
        put(658, false)
        put(659, false)
        put(660, false)
        put(661, false)
        put(662, true)
        put(663, false)
        put(664, true)
        put(665, false)
        put(666, false)
        put(667, false)
        put(668, false)
        put(669, false)
        put(670, true)
        put(671, false)
        put(672, false)
        put(673, false)
        put(674, false)
        put(675, false)
        put(677, true)
        put(678, false)
        put(679, false)
        put(680, false)
        put(681, false)
        put(682, false)
        put(683, false)
        put(684, false)
        put(685, false)
        put(686, false)
        put(687, false)
        put(688, false)
        put(689, true)
        put(690, false)
        put(691, false)
        put(692, false)
        put(693, false)
        put(694, false)
        put(695, false)
        put(696, false)
        put(697, false)
        put(698, false)
        put(699, false)
        put(700, false)
        put(701, false)
        put(702, false)
        put(703, false)
        put(704, false)
        put(705, false)
        put(706, true)
        put(707, false)
        put(708, true)
        put(709, false)
        put(710, false)
        put(711, true)
        put(712, true)
        put(713, false)
        put(714, false)
        put(715, true)
        put(716, true)
        put(717, true)
        put(718, true)
        put(719, false)
        put(720, false)
        put(721, false)
        put(722, false)
        put(723, false)
        put(724, false)
        put(725, false)
        put(726, false)
        put(727, false)
        put(728, false)
        put(729, true)
        put(730, false)
        put(731, false)
        put(732, false)
        put(733, false)
        put(734, true)
        put(735, true)
        put(736, false)
        put(737, false)
        put(738, false)
        put(739, false)
        put(740, false)
        put(741, false)
        put(742, false)
        put(743, true)
        put(744, false)
        put(745, false)
        put(746, false)
        put(747, false)
        put(748, false)
        put(749, true)
        put(750, true)
        put(751, true)
        put(752, false)
        put(753, false)
        put(754, true)
        put(755, true)
        put(756, true)
        put(757, false)
        put(758, true)
        put(759, true)
        put(760, true)
        put(761, false)
        put(762, false)
        put(763, false)
        put(764, true)
        put(765, false)
        put(766, false)
        put(767, true)
        put(768, true)
        put(769, true)
        put(770, false)
        put(771, true)
        put(772, true)
        put(773, true)
        put(774, true)
        put(775, true)
        put(776, true)
        put(777, false)
        put(778, false)
        put(779, false)
        put(780, false)
        put(781, true)
        put(782, false)
        put(783, true)
        put(784, true)
        put(785, false)
        put(786, false)
        put(787, false)
        put(788, false)
        put(789, false)
        put(790, false)
        put(791, false)
        put(792, true)
        put(793, false)
        put(794, true)
        put(795, false)
        put(796, false)
        put(797, false)
        put(798, false)
        put(799, true)
        put(800, true)
        put(801, false)
        put(802, false)
        put(803, false)
        put(804, false)
        put(805, false)
        put(806, false)
        put(807, false)
        put(808, false)
        put(809, false)
        put(810, true)
        put(811, true)
        put(812, true)
        put(813, false)
        put(814, false)
        put(815, false)
        put(816, false)
        put(817, false)
        put(818, false)
        put(819, false)
        put(820, false)
        put(821, false)
        put(822, true)
        put(823, true)
        put(824, true)
        put(825, true)
        put(826, true)
        put(827, false)
        put(828, false)
        put(829, false)
        put(830, true)
        put(831, true)
        put(832, false)
        put(833, true)
        put(834, false)
        put(835, false)
        put(836, false)
        put(837, false)
        put(838, false)
        put(839, false)
        put(840, false)
        put(841, false)
        put(842, true)
        put(843, false)
        put(844, false)
        put(845, true)
        put(846, true)
        put(847, true)
        put(848, false)
        put(849, false)
        put(850, false)
        put(851, false)
        put(852, false)
        put(853, false)
        put(854, false)
        put(855, false)
        put(856, false)
        put(857, false)
        put(858, false)
        put(859, false)
        put(860, false)
        put(861, false)
        put(862, false)
        put(863, false)
        put(864, false)
        put(865, false)
        put(866, false)
        put(867, false)
        put(868, true)
        put(869, false)
        put(870, false)
        put(871, true)
        put(872, false)
        put(873, false)
        put(874, false)
        put(875, false)
        put(876, false)
        put(877, true)
        put(878, false)
        put(879, false)
        put(880, false)
        put(881, false)
        put(882, false)
        put(883, false)
        put(884, false)
        put(885, false)
        put(886, false)
        put(887, false)
        put(888, false)
        put(889, false)
        put(890, false)
        put(891, false)
        put(892, false)
        put(893, false)
        put(894, false)
        put(895, false)
        put(896, false)
        put(897, false)
        put(898, false)
        put(899, false)
        put(900, false)
        put(901, false)
        put(902, false)
        put(903, false)
        put(904, false)
        put(905, false)
        put(906, false)
        put(907, false)
        put(908, false)
        put(909, false)
        put(910, false)
        put(911, false)
        put(912, false)
        put(913, false)
        put(914, false)
        put(915, false)
        put(916, false)
        put(917, false)
        put(918, false)
        put(919, false)
        put(920, false)
        put(921, false)
        put(922, false)
        put(923, false)
        put(924, false)
        put(925, false)
        put(926, false)
        put(927, false)
        put(928, false)
        put(929, false)
        put(930, false)
        put(931, false)
        put(932, false)
        put(933, false)
        put(934, false)
    } }
    val unknownSheerForceMoveIds: Set<Int> = setOf(
        67,
        127,
        130,
        143,
        146,
        157,
        161,
        165,
        229,
        242,
        448,
        676,
    )
    /** Exact source-derived flags used by Iron Fist, Strong Jaw, Mega Launcher, and Sharpness. */
    val abilityMoveFlagsById: Map<Int, Set<String>> = buildMap {
        put(4, setOf("punchingMove"))
        put(5, setOf("punchingMove"))
        put(7, setOf("punchingMove"))
        put(8, setOf("punchingMove"))
        put(9, setOf("punchingMove"))
        put(15, setOf("slicingMove"))
        put(44, setOf("bitingMove"))
        put(75, setOf("slicingMove"))
        put(146, setOf("punchingMove"))
        put(158, setOf("bitingMove"))
        put(163, setOf("slicingMove"))
        put(183, setOf("punchingMove"))
        put(210, setOf("slicingMove"))
        put(223, setOf("punchingMove"))
        put(242, setOf("bitingMove"))
        put(264, setOf("punchingMove"))
        put(305, setOf("bitingMove"))
        put(309, setOf("punchingMove"))
        put(314, setOf("slicingMove"))
        put(325, setOf("punchingMove"))
        put(327, setOf("punchingMove"))
        put(332, setOf("slicingMove"))
        put(348, setOf("slicingMove"))
        put(352, setOf("pulseMove"))
        put(359, setOf("punchingMove"))
        put(396, setOf("pulseMove"))
        put(399, setOf("pulseMove"))
        put(400, setOf("slicingMove"))
        put(403, setOf("slicingMove"))
        put(404, setOf("slicingMove"))
        put(406, setOf("pulseMove"))
        put(409, setOf("punchingMove"))
        put(418, setOf("punchingMove"))
        put(422, setOf("bitingMove"))
        put(423, setOf("bitingMove"))
        put(424, setOf("bitingMove"))
        put(427, setOf("slicingMove"))
        put(440, setOf("slicingMove"))
        put(505, setOf("pulseMove"))
        put(533, setOf("slicingMove"))
        put(534, setOf("slicingMove"))
        put(548, setOf("slicingMove"))
        put(612, setOf("punchingMove"))
        put(618, setOf("pulseMove"))
        put(628, setOf("punchingMove"))
        put(632, setOf("slicingMove"))
        put(660, setOf("bitingMove"))
        put(674, setOf("punchingMove"))
        put(689, setOf("punchingMove"))
        put(692, setOf("bitingMove"))
        put(701, setOf("bitingMove"))
        put(709, setOf("slicingMove"))
        put(733, setOf("pulseMove"))
        put(745, setOf("punchingMove"))
        put(746, setOf("punchingMove"))
        put(758, setOf("slicingMove"))
        put(766, setOf("punchingMove"))
        put(773, setOf("slicingMove"))
        put(785, setOf("punchingMove"))
        put(788, setOf("slicingMove"))
        put(797, setOf("slicingMove"))
        put(815, setOf("punchingMove"))
        put(817, setOf("slicingMove"))
        put(821, setOf("slicingMove"))
        put(827, setOf("slicingMove"))
        put(838, setOf("slicingMove"))
        put(839, setOf("slicingMove"))
    }
    /** Conditional/config-derived ability flags are not treated as false. */
    val unknownAbilityMoveFlagsById: Map<Int, Set<String>> = buildMap {
    }


    /** Moves proven to be ordinary fixed-base-power attacks in the pinned source. */
    val ordinaryMoveIds: Set<Int> = setOf(
        1,
        2,
        5,
        6,
        7,
        8,
        9,
        10,
        11,
        15,
        16,
        17,
        20,
        21,
        22,
        23,
        25,
        27,
        29,
        30,
        33,
        34,
        35,
        37,
        40,
        44,
        51,
        52,
        53,
        55,
        56,
        58,
        59,
        60,
        61,
        62,
        63,
        64,
        65,
        70,
        75,
        80,
        83,
        84,
        85,
        87,
        88,
        93,
        94,
        98,
        99,
        121,
        122,
        123,
        124,
        125,
        126,
        127,
        128,
        129,
        132,
        145,
        146,
        152,
        157,
        158,
        161,
        163,
        172,
        177,
        181,
        183,
        185,
        188,
        189,
        190,
        192,
        196,
        200,
        209,
        211,
        221,
        223,
        224,
        225,
        231,
        232,
        233,
        238,
        242,
        245,
        246,
        247,
        249,
        257,
        276,
        280,
        290,
        295,
        296,
        299,
        302,
        304,
        305,
        306,
        307,
        308,
        309,
        310,
        314,
        315,
        317,
        318,
        324,
        325,
        326,
        328,
        330,
        332,
        337,
        338,
        341,
        342,
        345,
        348,
        351,
        352,
        354,
        359,
        364,
        365,
        370,
        395,
        396,
        398,
        399,
        400,
        401,
        402,
        403,
        404,
        405,
        406,
        407,
        408,
        410,
        411,
        412,
        414,
        416,
        418,
        420,
        421,
        422,
        423,
        424,
        425,
        426,
        427,
        428,
        429,
        430,
        431,
        434,
        435,
        436,
        437,
        438,
        439,
        440,
        441,
        442,
        443,
        444,
        448,
        450,
        451,
        453,
        454,
        459,
        460,
        463,
        465,
        466,
        481,
        482,
        488,
        490,
        491,
        499,
        503,
        510,
        517,
        522,
        527,
        529,
        531,
        534,
        536,
        539,
        545,
        547,
        549,
        550,
        551,
        552,
        555,
        556,
        557,
        572,
        574,
        583,
        584,
        585,
        586,
        591,
        592,
        593,
        595,
        605,
        609,
        611,
        612,
        615,
        616,
        618,
        619,
        620,
        625,
        627,
        628,
        630,
        633,
        638,
        640,
        642,
        643,
        647,
        650,
        651,
        654,
        655,
        656,
        659,
        660,
        662,
        663,
        664,
        665,
        666,
        667,
        668,
        670,
        674,
        677,
        678,
        681,
        682,
        683,
        684,
        685,
        686,
        687,
        692,
        706,
        707,
        708,
        712,
        713,
        714,
        715,
        717,
        718,
        721,
        722,
        723,
        734,
        735,
        743,
        747,
        749,
        750,
        751,
        752,
        753,
        754,
        755,
        756,
        759,
        760,
        761,
        764,
        766,
        768,
        769,
        771,
        774,
        775,
        776,
        783,
        784,
        785,
        787,
        790,
        792,
        797,
        799,
        800,
        802,
        810,
        811,
        812,
        813,
        816,
        819,
        821,
        822,
        823,
        824,
        825,
        826,
        829,
        831,
        838,
        842,
        845,
        847,
        848,
        849,
        850,
        851,
        852,
        853,
        854,
        855,
        856,
        857,
        858,
        859,
        860,
        861,
        862,
        863,
        864,
        865,
        866,
        867,
        868,
        870,
        871,
        872,
        874,
        876,
        877,
        879,
        880,
        882,
    )

    /** Fixed single-hit EFFECT_RECOIL; separate from the ordinary safety boundary. */
    val fixedSingleHitRecoilMoveIds: Set<Int> = setOf(
        36,
        38,
        66,
        344,
        394,
        413,
        452,
        457,
        528,
        543,
        617,
        762,
    )

    /** Fixed single-hit drain, requiring authoritative Heal Block execution state. */
    val fixedSingleHitDrainMoveIds: Set<Int> = setOf(71, 72, 141, 202, 409, 532, 577)
    val absorbPercentageById: Map<Int, Int> = mapOf(71 to 50, 72 to 50, 141 to 50, 202 to 50, 409 to 50, 532 to 50, 577 to 75)

    val fixedSingleHitEarthquakeMoveIds: Set<Int> = setOf(89, 523)
    val earthquakeDamagesUndergroundById: Map<Int, Boolean> = mapOf(89 to true, 523 to false)
    /** Singles explosion: Damp gate, HP=0 at damage, modern Defense, Parental Bond banned. */
    val fixedSingleHitExplosionMoveIds: Set<Int> = setOf(120, 153)
    val explosionPowerById: Map<Int, Int> = mapOf(120 to 200, 153 to 250)
    val fixedSingleHitGyroBallMoveIds: Set<Int> = setOf(360)
    val fixedSingleHitElectroBallMoveIds: Set<Int> = setOf(486)
    val fixedSingleHitEscapeMoveIds: Set<Int> = setOf(369, 521, 740)
    val fixedSingleHitRolloutMoveIds: Set<Int> = setOf(205, 301)
    val fixedSingleHitSpeedPowerMoveIds: Set<Int> = fixedSingleHitGyroBallMoveIds + fixedSingleHitElectroBallMoveIds
    val reviewedVariablePowerMoveIds: Set<Int> = fixedSingleHitSpeedPowerMoveIds
    val electroBallPowerTable: List<Int> = listOf(40, 60, 80, 120, 150)
    val fixedSingleHitBrineMoveIds: Set<Int> = setOf(362)
    val fixedSingleHitStatusDoubleMoveIds: Set<Int> = setOf(265, 358, 474, 506, 767, 772)
    val statusDoublePowerMaskById: Map<Int, Int> = mapOf(265 to 64, 358 to 7, 474 to 136, 506 to 4351, 767 to 136, 772 to 4351)
    const val STATUS1_SLEEP: Int = 7
    const val STATUS1_POISON: Int = 8
    const val STATUS1_BURN: Int = 16
    const val STATUS1_FREEZE: Int = 32
    const val STATUS1_PARALYSIS: Int = 64
    const val STATUS1_TOXIC_POISON: Int = 128
    const val STATUS1_TOXIC_COUNTER: Int = 3840
    const val STATUS1_FROSTBITE: Int = 4096
    const val STATUS1_PSN_ANY: Int = 136
    const val STATUS1_ANY: Int = 4351
    /** Frozen Singles Surf/Whirlpool; neutral or underwater selected hit only. */
    val fixedSingleHitUnderwaterMoveIds: Set<Int> = setOf(57, 250)
    val underwaterPowerById: Map<Int, Int> = mapOf(57 to 90, 250 to 35)
    val dampBannedMoveIds: Set<Int> = setOf(120, 153, 673, 730)
    val unknownDampBanMoveIds: Set<Int> = setOf()
    /** Recoil moves which clear Freeze/Frostbite before the selected hit. */
    val recoilThawsUserMoveIds: Set<Int> = setOf(394)

    /** Exact pinned MoveInfo flags used by Group C immunity and suppression rules. */
    val immunityFlagsById: Map<Int, Set<String>> = buildMap {
        put(16, setOf("windMove"))
        put(18, setOf("windMove"))
        put(45, setOf("soundMove"))
        put(46, setOf("soundMove"))
        put(47, setOf("soundMove"))
        put(48, setOf("soundMove"))
        put(59, setOf("windMove"))
        put(71, setOf("healingMove"))
        put(72, setOf("healingMove"))
        put(103, setOf("soundMove"))
        put(105, setOf("healingMove"))
        put(121, setOf("ballisticMove"))
        put(135, setOf("healingMove"))
        put(140, setOf("ballisticMove"))
        put(141, setOf("healingMove"))
        put(156, setOf("healingMove"))
        put(173, setOf("soundMove"))
        put(177, setOf("windMove"))
        put(188, setOf("ballisticMove"))
        put(190, setOf("ballisticMove"))
        put(192, setOf("ballisticMove"))
        put(195, setOf("soundMove"))
        put(196, setOf("windMove"))
        put(201, setOf("windMove"))
        put(202, setOf("healingMove"))
        put(208, setOf("healingMove"))
        put(215, setOf("soundMove"))
        put(234, setOf("healingMove"))
        put(235, setOf("healingMove"))
        put(236, setOf("healingMove"))
        put(239, setOf("windMove"))
        put(247, setOf("ballisticMove"))
        put(253, setOf("soundMove"))
        put(256, setOf("healingMove"))
        put(257, setOf("windMove"))
        put(273, setOf("healingMove"))
        put(296, setOf("ballisticMove"))
        put(301, setOf("ballisticMove"))
        put(303, setOf("healingMove"))
        put(304, setOf("soundMove"))
        put(311, setOf("ballisticMove"))
        put(314, setOf("windMove"))
        put(319, setOf("soundMove"))
        put(320, setOf("soundMove"))
        put(331, setOf("ballisticMove"))
        put(355, setOf("healingMove"))
        put(360, setOf("ballisticMove"))
        put(361, setOf("healingMove"))
        put(366, setOf("windMove"))
        put(396, setOf("ballisticMove"))
        put(402, setOf("ballisticMove"))
        put(405, setOf("soundMove"))
        put(409, setOf("healingMove"))
        put(411, setOf("ballisticMove"))
        put(412, setOf("ballisticMove"))
        put(426, setOf("ballisticMove"))
        put(439, setOf("ballisticMove"))
        put(443, setOf("ballisticMove"))
        put(448, setOf("soundMove"))
        put(456, setOf("healingMove"))
        put(461, setOf("healingMove"))
        put(486, setOf("ballisticMove"))
        put(491, setOf("ballisticMove"))
        put(496, setOf("soundMove"))
        put(497, setOf("soundMove"))
        put(505, setOf("healingMove"))
        put(532, setOf("healingMove"))
        put(542, setOf("windMove"))
        put(545, setOf("ballisticMove"))
        put(547, setOf("soundMove"))
        put(555, setOf("soundMove"))
        put(568, setOf("soundMove"))
        put(572, setOf("windMove"))
        put(574, setOf("soundMove"))
        put(575, setOf("soundMove"))
        put(577, setOf("healingMove"))
        put(584, setOf("windMove"))
        put(586, setOf("soundMove"))
        put(590, setOf("soundMove"))
        put(622, setOf("healingMove"))
        put(627, setOf("soundMove"))
        put(629, setOf("healingMove"))
        put(639, setOf("ballisticMove"))
        put(648, setOf("healingMove"))
        put(653, setOf("ballisticMove"))
        put(654, setOf("soundMove"))
        put(667, setOf("ignoresTargetAbility"))
        put(668, setOf("ignoresTargetAbility"))
        put(675, setOf("ignoresTargetAbility"))
        put(703, setOf("soundMove"))
        put(708, setOf("ballisticMove"))
        put(714, setOf("soundMove"))
        put(719, setOf("healingMove"))
        put(744, setOf("healingMove"))
        put(754, setOf("soundMove"))
        put(759, setOf("windMove"))
        put(774, setOf("windMove"))
        put(775, setOf("windMove"))
        put(776, setOf("windMove"))
        put(777, setOf("healingMove"))
        put(791, setOf("healingMove"))
        put(799, setOf("soundMove"))
        put(817, setOf("healingMove"))
        put(830, setOf("healingMove"))
        put(831, setOf("ballisticMove"))
        put(842, setOf("soundMove"))
        put(845, setOf("soundMove"))
        put(877, setOf("soundMove"))
        put(879, setOf("ignoresTargetAbility"))
        put(880, setOf("ignoresTargetAbility"))
        put(881, setOf("ignoresTargetAbility"))
        put(916, setOf("ignoresTargetAbility"))
        put(917, setOf("ignoresTargetAbility"))
        put(918, setOf("ignoresTargetAbility"))
    }
    /** Conditional/config-derived flag initializers fail closed here. */
    val unknownImmunityFlagsById: Map<Int, Set<String>> = buildMap {
        put(13, setOf("windMove"))
        put(138, setOf("healingMove"))
        put(318, setOf("windMove"))
        put(336, setOf("soundMove"))
        put(350, setOf("ballisticMove"))
        put(466, setOf("windMove"))
        put(570, setOf("healingMove"))
        put(613, setOf("healingMove"))
        put(631, setOf("healingMove"))
        put(680, setOf("healingMove"))
    }

    /** Literal MoveInfo priority; omitted entries have conditional/computed priority. */
    val basePriorityById: Map<Int, Int> = buildMap {
        put(1, 0)
        put(2, 0)
        put(3, 0)
        put(4, 0)
        put(5, 0)
        put(6, 0)
        put(7, 0)
        put(8, 0)
        put(9, 0)
        put(10, 0)
        put(11, 0)
        put(12, 0)
        put(13, 0)
        put(14, 0)
        put(15, 0)
        put(16, 0)
        put(17, 0)
        put(19, 0)
        put(20, 0)
        put(21, 0)
        put(22, 0)
        put(23, 0)
        put(24, 0)
        put(25, 0)
        put(26, 0)
        put(27, 0)
        put(28, 0)
        put(29, 0)
        put(30, 0)
        put(31, 0)
        put(32, 0)
        put(33, 0)
        put(34, 0)
        put(35, 0)
        put(36, 0)
        put(37, 0)
        put(38, 0)
        put(39, 0)
        put(40, 0)
        put(41, 0)
        put(42, 0)
        put(43, 0)
        put(44, 0)
        put(45, 0)
        put(47, 0)
        put(48, 0)
        put(49, 0)
        put(50, 0)
        put(51, 0)
        put(52, 0)
        put(53, 0)
        put(54, 0)
        put(55, 0)
        put(56, 0)
        put(57, 0)
        put(58, 0)
        put(59, 0)
        put(60, 0)
        put(61, 0)
        put(62, 0)
        put(63, 0)
        put(64, 0)
        put(65, 0)
        put(66, 0)
        put(67, 0)
        put(68, -5)
        put(69, 0)
        put(70, 0)
        put(71, 0)
        put(72, 0)
        put(73, 0)
        put(74, 0)
        put(75, 0)
        put(76, 0)
        put(77, 0)
        put(78, 0)
        put(79, 0)
        put(80, 0)
        put(81, 0)
        put(82, 0)
        put(83, 0)
        put(84, 0)
        put(85, 0)
        put(86, 0)
        put(87, 0)
        put(88, 0)
        put(89, 0)
        put(90, 0)
        put(91, 0)
        put(92, 0)
        put(93, 0)
        put(94, 0)
        put(95, 0)
        put(96, 0)
        put(97, 0)
        put(98, 1)
        put(99, 0)
        put(101, 0)
        put(102, 0)
        put(103, 0)
        put(104, 0)
        put(105, 0)
        put(106, 0)
        put(107, 0)
        put(108, 0)
        put(109, 0)
        put(110, 0)
        put(111, 0)
        put(112, 0)
        put(113, 0)
        put(114, 0)
        put(115, 0)
        put(116, 0)
        put(118, 0)
        put(119, 0)
        put(120, 0)
        put(121, 0)
        put(122, 0)
        put(123, 0)
        put(124, 0)
        put(125, 0)
        put(126, 0)
        put(127, 0)
        put(128, 0)
        put(129, 0)
        put(130, 0)
        put(131, 0)
        put(132, 0)
        put(133, 0)
        put(134, 0)
        put(135, 0)
        put(136, 0)
        put(137, 0)
        put(138, 0)
        put(139, 0)
        put(140, 0)
        put(141, 0)
        put(142, 0)
        put(143, 0)
        put(144, 0)
        put(145, 0)
        put(146, 0)
        put(147, 0)
        put(148, 0)
        put(149, 0)
        put(150, 0)
        put(151, 0)
        put(152, 0)
        put(153, 0)
        put(154, 0)
        put(155, 0)
        put(156, 0)
        put(157, 0)
        put(158, 0)
        put(159, 0)
        put(160, 0)
        put(161, 0)
        put(162, 0)
        put(163, 0)
        put(164, 0)
        put(165, 0)
        put(166, 0)
        put(167, 0)
        put(168, 0)
        put(169, 0)
        put(170, 0)
        put(171, 0)
        put(172, 0)
        put(173, 0)
        put(174, 0)
        put(175, 0)
        put(176, 0)
        put(177, 0)
        put(178, 0)
        put(179, 0)
        put(180, 0)
        put(181, 0)
        put(183, 1)
        put(184, 0)
        put(185, 0)
        put(186, 0)
        put(187, 0)
        put(188, 0)
        put(189, 0)
        put(190, 0)
        put(191, 0)
        put(192, 0)
        put(193, 0)
        put(194, 0)
        put(195, 0)
        put(196, 0)
        put(198, 0)
        put(199, 0)
        put(200, 0)
        put(201, 0)
        put(202, 0)
        put(204, 0)
        put(205, 0)
        put(206, 0)
        put(207, 0)
        put(208, 0)
        put(209, 0)
        put(210, 0)
        put(211, 0)
        put(212, 0)
        put(213, 0)
        put(214, 0)
        put(215, 0)
        put(216, 0)
        put(217, 0)
        put(218, 0)
        put(219, 0)
        put(220, 0)
        put(221, 0)
        put(222, 0)
        put(223, 0)
        put(224, 0)
        put(225, 0)
        put(226, 0)
        put(227, 0)
        put(228, 0)
        put(229, 0)
        put(230, 0)
        put(231, 0)
        put(232, 0)
        put(233, -1)
        put(234, 0)
        put(235, 0)
        put(236, 0)
        put(237, 0)
        put(238, 0)
        put(239, 0)
        put(240, 0)
        put(241, 0)
        put(242, 0)
        put(243, -5)
        put(244, 0)
        put(246, 0)
        put(247, 0)
        put(248, 0)
        put(249, 0)
        put(250, 0)
        put(251, 0)
        put(253, 0)
        put(254, 0)
        put(255, 0)
        put(256, 0)
        put(257, 0)
        put(258, 0)
        put(259, 0)
        put(260, 0)
        put(261, 0)
        put(262, 0)
        put(263, 0)
        put(264, -3)
        put(265, 0)
        put(267, 0)
        put(268, 0)
        put(269, 0)
        put(270, 5)
        put(271, 0)
        put(272, 0)
        put(273, 0)
        put(274, 0)
        put(275, 0)
        put(276, 0)
        put(277, 4)
        put(278, 0)
        put(279, -4)
        put(280, 0)
        put(281, 0)
        put(282, 0)
        put(283, 0)
        put(284, 0)
        put(285, 0)
        put(286, 0)
        put(287, 0)
        put(288, 0)
        put(289, 4)
        put(290, 0)
        put(291, 0)
        put(292, 0)
        put(293, 0)
        put(294, 0)
        put(295, 0)
        put(296, 0)
        put(297, 0)
        put(298, 0)
        put(299, 0)
        put(300, 0)
        put(301, 0)
        put(302, 0)
        put(303, 0)
        put(304, 0)
        put(305, 0)
        put(306, 0)
        put(307, 0)
        put(308, 0)
        put(309, 0)
        put(310, 0)
        put(311, 0)
        put(312, 0)
        put(313, 0)
        put(314, 0)
        put(315, 0)
        put(316, 0)
        put(317, 0)
        put(318, 0)
        put(319, 0)
        put(320, 0)
        put(321, 0)
        put(322, 0)
        put(323, 0)
        put(324, 0)
        put(325, 0)
        put(326, 0)
        put(327, 0)
        put(328, 0)
        put(329, 0)
        put(330, 0)
        put(331, 0)
        put(332, 0)
        put(333, 0)
        put(334, 0)
        put(335, 0)
        put(336, 0)
        put(337, 0)
        put(338, 0)
        put(339, 0)
        put(340, 0)
        put(341, 0)
        put(342, 0)
        put(343, 0)
        put(344, 0)
        put(345, 0)
        put(346, 0)
        put(347, 0)
        put(348, 0)
        put(349, 0)
        put(350, 0)
        put(351, 0)
        put(352, 0)
        put(353, 0)
        put(354, 0)
        put(355, 0)
        put(356, 0)
        put(357, 0)
        put(358, 0)
        put(359, 0)
        put(360, 0)
        put(361, 0)
        put(362, 0)
        put(363, 0)
        put(364, 2)
        put(365, 0)
        put(366, 0)
        put(367, 0)
        put(368, 0)
        put(369, 0)
        put(370, 0)
        put(371, 0)
        put(372, 0)
        put(373, 0)
        put(374, 0)
        put(375, 0)
        put(376, 0)
        put(377, 0)
        put(378, 0)
        put(379, 0)
        put(380, 0)
        put(381, 0)
        put(382, 0)
        put(383, 0)
        put(384, 0)
        put(385, 0)
        put(386, 0)
        put(387, 0)
        put(388, 0)
        put(389, 1)
        put(390, 0)
        put(391, 0)
        put(392, 0)
        put(393, 0)
        put(394, 0)
        put(395, 0)
        put(396, 0)
        put(397, 0)
        put(398, 0)
        put(399, 0)
        put(400, 0)
        put(401, 0)
        put(402, 0)
        put(403, 0)
        put(404, 0)
        put(405, 0)
        put(406, 0)
        put(407, 0)
        put(408, 0)
        put(409, 0)
        put(410, 1)
        put(411, 0)
        put(412, 0)
        put(413, 0)
        put(414, 0)
        put(415, 0)
        put(416, 0)
        put(417, 0)
        put(418, 1)
        put(419, -4)
        put(420, 1)
        put(421, 0)
        put(422, 0)
        put(423, 0)
        put(424, 0)
        put(425, 1)
        put(426, 0)
        put(427, 0)
        put(428, 0)
        put(429, 0)
        put(430, 0)
        put(431, 0)
        put(432, 0)
        put(433, -7)
        put(434, 0)
        put(435, 0)
        put(436, 0)
        put(437, 0)
        put(438, 0)
        put(439, 0)
        put(440, 0)
        put(441, 0)
        put(442, 0)
        put(443, 0)
        put(444, 0)
        put(445, 0)
        put(446, 0)
        put(447, 0)
        put(448, 0)
        put(449, 0)
        put(450, 0)
        put(451, 0)
        put(452, 0)
        put(453, 1)
        put(454, 0)
        put(455, 0)
        put(456, 0)
        put(457, 0)
        put(458, 0)
        put(459, 0)
        put(460, 0)
        put(461, 0)
        put(462, 0)
        put(463, 0)
        put(464, 0)
        put(465, 0)
        put(466, 0)
        put(467, 0)
        put(468, 0)
        put(469, 3)
        put(470, 0)
        put(471, 0)
        put(473, 0)
        put(474, 0)
        put(475, 0)
        put(477, 0)
        put(479, 0)
        put(480, 0)
        put(481, 0)
        put(482, 0)
        put(483, 0)
        put(484, 0)
        put(485, 0)
        put(486, 0)
        put(487, 0)
        put(488, 0)
        put(489, 0)
        put(490, 0)
        put(491, 0)
        put(492, 0)
        put(493, 0)
        put(494, 0)
        put(495, 0)
        put(496, 0)
        put(497, 0)
        put(498, 0)
        put(499, 0)
        put(500, 0)
        put(501, 3)
        put(503, 0)
        put(504, 0)
        put(505, 0)
        put(506, 0)
        put(507, 0)
        put(508, 0)
        put(509, -6)
        put(510, 0)
        put(511, 0)
        put(512, 0)
        put(513, 0)
        put(514, 0)
        put(515, 0)
        put(516, 0)
        put(517, 0)
        put(518, 0)
        put(519, 0)
        put(520, 0)
        put(521, 0)
        put(522, 0)
        put(523, 0)
        put(524, 0)
        put(525, -6)
        put(526, 0)
        put(527, 0)
        put(528, 0)
        put(529, 0)
        put(530, 0)
        put(531, 0)
        put(532, 0)
        put(533, 0)
        put(534, 0)
        put(535, 0)
        put(536, 0)
        put(537, 0)
        put(538, 0)
        put(539, 0)
        put(540, 0)
        put(541, 0)
        put(542, 0)
        put(543, 0)
        put(544, 0)
        put(545, 0)
        put(546, 0)
        put(547, 0)
        put(548, 0)
        put(549, 0)
        put(550, 0)
        put(551, 0)
        put(552, 0)
        put(553, 0)
        put(554, 0)
        put(555, 0)
        put(556, 0)
        put(557, 0)
        put(558, 0)
        put(559, 0)
        put(560, 0)
        put(561, 0)
        put(562, 0)
        put(563, 0)
        put(564, 0)
        put(565, 0)
        put(566, 0)
        put(567, 0)
        put(568, 0)
        put(569, 1)
        put(570, 0)
        put(571, 0)
        put(572, 0)
        put(573, 0)
        put(574, 0)
        put(575, 0)
        put(576, 0)
        put(577, 0)
        put(578, 3)
        put(579, 0)
        put(580, 0)
        put(581, 0)
        put(582, 0)
        put(583, 0)
        put(584, 0)
        put(585, 0)
        put(586, 0)
        put(587, 0)
        put(588, 4)
        put(589, 0)
        put(590, 0)
        put(591, 0)
        put(592, 0)
        put(593, 0)
        put(594, 1)
        put(595, 0)
        put(596, 4)
        put(597, 0)
        put(598, 0)
        put(599, 0)
        put(600, 1)
        put(601, 0)
        put(602, 0)
        put(603, 0)
        put(604, 0)
        put(605, 0)
        put(606, 0)
        put(607, 0)
        put(608, 1)
        put(609, 0)
        put(610, 0)
        put(611, 0)
        put(612, 0)
        put(613, 0)
        put(614, 0)
        put(615, 0)
        put(616, 0)
        put(617, 0)
        put(618, 0)
        put(619, 0)
        put(620, 0)
        put(621, 0)
        put(622, 0)
        put(623, 2)
        put(624, 4)
        put(625, 0)
        put(626, 0)
        put(627, 0)
        put(628, 0)
        put(629, 0)
        put(630, 0)
        put(631, 0)
        put(632, 0)
        put(633, 0)
        put(634, 3)
        put(635, 0)
        put(636, 0)
        put(637, 0)
        put(638, 0)
        put(639, 0)
        put(640, 0)
        put(641, 0)
        put(642, 0)
        put(643, 0)
        put(644, 0)
        put(645, 0)
        put(646, 0)
        put(647, 0)
        put(648, 0)
        put(649, 0)
        put(650, 0)
        put(651, 0)
        put(652, 0)
        put(653, -3)
        put(654, 0)
        put(655, 0)
        put(656, 0)
        put(657, 0)
        put(658, -3)
        put(659, 0)
        put(660, 0)
        put(661, 0)
        put(662, 0)
        put(663, 1)
        put(664, 0)
        put(665, 0)
        put(666, 0)
        put(667, 0)
        put(668, 0)
        put(669, 0)
        put(670, 0)
        put(671, 0)
        put(672, 0)
        put(673, 0)
        put(674, 0)
        put(675, 0)
        put(676, 2)
        put(677, 0)
        put(678, 0)
        put(679, 0)
        put(680, 0)
        put(681, 0)
        put(682, 0)
        put(683, 0)
        put(684, 0)
        put(685, 0)
        put(686, 0)
        put(687, 0)
        put(688, 0)
        put(689, 0)
        put(690, 0)
        put(691, 0)
        put(692, 0)
        put(693, 0)
        put(694, 0)
        put(695, 0)
        put(696, 0)
        put(697, 0)
        put(698, 0)
        put(699, 0)
        put(700, 0)
        put(701, 0)
        put(702, 0)
        put(703, 0)
        put(704, 0)
        put(705, 0)
        put(706, 0)
        put(707, 0)
        put(708, 0)
        put(709, 0)
        put(710, 0)
        put(711, 0)
        put(712, 0)
        put(713, 0)
        put(714, 0)
        put(715, 0)
        put(716, 0)
        put(717, 0)
        put(718, 0)
        put(719, 0)
        put(720, 4)
        put(721, 0)
        put(722, 0)
        put(723, 0)
        put(724, 0)
        put(725, 0)
        put(726, 0)
        put(727, 0)
        put(728, 0)
        put(729, 0)
        put(730, 0)
        put(731, 0)
        put(732, 0)
        put(733, 0)
        put(734, 0)
        put(735, 0)
        put(736, 0)
        put(737, 0)
        put(738, 0)
        put(739, 0)
        put(740, 0)
        put(741, 0)
        put(742, 0)
        put(743, 0)
        put(744, 0)
        put(745, 0)
        put(746, 0)
        put(747, 0)
        put(748, 0)
        put(749, 0)
        put(750, 0)
        put(751, 0)
        put(752, 0)
        put(753, 0)
        put(754, 0)
        put(755, 0)
        put(756, 0)
        put(757, 0)
        put(758, 0)
        put(759, 0)
        put(760, 0)
        put(761, 0)
        put(762, 0)
        put(763, 0)
        put(764, 0)
        put(765, 0)
        put(766, 0)
        put(767, 0)
        put(768, 0)
        put(769, 0)
        put(770, 0)
        put(771, 0)
        put(772, 0)
        put(773, 0)
        put(774, 0)
        put(775, 0)
        put(776, 0)
        put(777, 0)
        put(778, 0)
        put(779, 0)
        put(780, 4)
        put(781, 0)
        put(782, 0)
        put(783, 0)
        put(784, 0)
        put(785, 1)
        put(786, 0)
        put(787, 0)
        put(788, 0)
        put(789, 0)
        put(790, 0)
        put(791, 0)
        put(792, 0)
        put(793, 0)
        put(794, 0)
        put(795, 0)
        put(796, 0)
        put(797, 0)
        put(798, 0)
        put(799, 0)
        put(800, 0)
        put(801, 0)
        put(802, 0)
        put(803, 0)
        put(804, 0)
        put(805, 0)
        put(806, 0)
        put(807, 0)
        put(808, 0)
        put(809, 0)
        put(810, 0)
        put(811, 0)
        put(812, 0)
        put(813, 0)
        put(814, 0)
        put(815, 0)
        put(816, 0)
        put(817, 0)
        put(818, 0)
        put(819, 0)
        put(820, 0)
        put(821, 0)
        put(822, 0)
        put(823, 0)
        put(824, 0)
        put(825, 0)
        put(826, 0)
        put(827, 0)
        put(828, 0)
        put(829, 0)
        put(830, 0)
        put(831, 0)
        put(832, 0)
        put(833, 0)
        put(834, 0)
        put(835, 0)
        put(836, 4)
        put(837, 1)
        put(838, 0)
        put(839, 0)
        put(840, 0)
        put(841, 0)
        put(842, 0)
        put(843, 0)
        put(844, 0)
        put(845, 0)
        put(846, 3)
        put(847, 0)
        put(848, 0)
        put(849, 0)
        put(850, 0)
        put(851, 0)
        put(852, 0)
        put(853, 0)
        put(854, 0)
        put(855, 0)
        put(856, 0)
        put(857, 0)
        put(858, 0)
        put(859, 0)
        put(860, 0)
        put(861, 0)
        put(862, 0)
        put(863, 0)
        put(864, 0)
        put(865, 0)
        put(866, 0)
        put(867, 0)
        put(868, 0)
        put(869, 0)
        put(870, 0)
        put(871, 0)
        put(872, 0)
        put(873, 0)
        put(874, 0)
        put(875, 0)
        put(876, 0)
        put(877, 0)
        put(878, 0)
        put(879, 0)
        put(880, 0)
        put(881, 0)
        put(882, 0)
        put(883, 4)
        put(884, 0)
        put(885, 0)
        put(886, 0)
        put(887, 0)
        put(888, 0)
        put(889, 0)
        put(890, 0)
        put(891, 0)
        put(892, 0)
        put(893, 0)
        put(894, 0)
        put(895, 0)
        put(896, 0)
        put(897, 0)
        put(898, 0)
        put(899, 0)
        put(900, 0)
        put(901, 0)
        put(902, 0)
        put(903, 0)
        put(904, 0)
        put(905, 0)
        put(906, 0)
        put(907, 0)
        put(908, 0)
        put(909, 0)
        put(910, 0)
        put(911, 0)
        put(912, 0)
        put(913, 0)
        put(914, 0)
        put(915, 0)
        put(916, 0)
        put(917, 0)
        put(918, 0)
        put(919, 0)
        put(920, 0)
        put(921, 0)
        put(922, 0)
        put(923, 0)
        put(924, 0)
        put(925, 0)
        put(926, 0)
        put(927, 0)
        put(928, 0)
        put(929, 0)
        put(930, 0)
        put(931, 0)
        put(932, 0)
        put(933, 0)
        put(934, 0)
    }
    val unknownPriorityMoveIds: Set<Int> = setOf(
        18,
        46,
        100,
        117,
        182,
        197,
        203,
        245,
        252,
        266,
        472,
        476,
        478,
        502,
    )

    /**
     * Internal spread/target classes, carried with the EXACT values of the pinned
     * H&S 2.0.5 `enum MoveTarget` (1f42b74d). This is not the pinned enum itself;
     * it exists so the boundary can dispatch `GetMoveTargetCount` semantics without
     * importing the game's full target vocabulary.
     */
    object SpreadTargetClass {
        const val TARGET_ALLY = 8
        const val TARGET_ALL_BATTLERS = 14
        const val TARGET_BOTH = 6
        const val TARGET_DEPENDS = 3
        const val TARGET_FIELD = 12
        const val TARGET_FOES_AND_ALLY = 11
        const val TARGET_NONE = 0
        const val TARGET_OPPONENT = 4
        const val TARGET_OPPONENTS_FIELD = 13
        const val TARGET_RANDOM = 5
        const val TARGET_SELECTED = 1
        const val TARGET_SMART = 2
        const val TARGET_USER = 7
        const val TARGET_USER_AND_ALLY = 9
        const val TARGET_USER_OR_ALLY = 10
    }

    /**
     * Pinned move ID -> the move's static target class from `gMovesInfo[move].target`,
     * as a [SpreadTargetClass] value (exact pinned `enum MoveTarget` number).
     * Only spread classes (BOTH=6, FOES_AND_ALLY=11) affect the damage formula;
     * every other class fails closed on the consumer side.
     */
    val targetClassByMoveId: Map<Int, Int> = buildMap {
        put(1, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(2, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(3, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(4, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(5, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(6, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(7, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(8, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(9, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(10, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(11, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(12, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(13, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(14, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(15, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(16, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(17, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(18, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(19, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(20, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(21, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(22, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(23, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(24, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(25, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(26, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(27, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(28, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(29, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(30, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(31, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(32, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(33, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(34, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(35, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(36, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(37, SpreadTargetClass.TARGET_RANDOM) // pinned enum value 5
        put(38, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(39, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(40, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(41, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(42, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(43, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(44, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(45, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(46, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(47, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(48, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(49, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(50, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(51, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(52, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(53, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(54, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(55, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(56, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(57, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(58, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(59, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(60, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(61, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(62, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(63, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(64, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(65, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(66, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(68, SpreadTargetClass.TARGET_DEPENDS) // pinned enum value 3
        put(69, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(70, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(71, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(72, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(73, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(75, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(76, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(77, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(78, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(79, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(80, SpreadTargetClass.TARGET_RANDOM) // pinned enum value 5
        put(82, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(83, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(84, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(85, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(86, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(87, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(88, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(89, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(90, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(91, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(92, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(93, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(94, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(95, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(96, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(97, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(98, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(99, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(100, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(101, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(102, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(103, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(104, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(105, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(106, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(107, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(108, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(109, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(110, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(111, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(112, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(113, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(114, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(115, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(116, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(117, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(118, SpreadTargetClass.TARGET_DEPENDS) // pinned enum value 3
        put(119, SpreadTargetClass.TARGET_DEPENDS) // pinned enum value 3
        put(120, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(121, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(122, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(123, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(124, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(125, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(126, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(127, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(128, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(129, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(130, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(131, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(132, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(133, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(134, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(135, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(136, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(137, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(138, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(140, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(141, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(142, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(143, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(144, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(145, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(146, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(147, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(148, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(149, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(150, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(151, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(152, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(153, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(154, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(155, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(156, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(157, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(158, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(159, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(160, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(161, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(162, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(163, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(164, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(166, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(167, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(168, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(169, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(170, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(171, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(172, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(173, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(174, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(175, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(177, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(179, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(180, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(181, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(182, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(183, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(184, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(185, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(186, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(187, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(188, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(189, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(190, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(191, SpreadTargetClass.TARGET_OPPONENTS_FIELD) // pinned enum value 13
        put(192, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(193, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(194, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(195, SpreadTargetClass.TARGET_ALL_BATTLERS) // pinned enum value 14
        put(196, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(197, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(198, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(199, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(200, SpreadTargetClass.TARGET_RANDOM) // pinned enum value 5
        put(201, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(202, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(203, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(204, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(205, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(206, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(207, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(208, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(209, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(210, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(211, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(212, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(213, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(214, SpreadTargetClass.TARGET_DEPENDS) // pinned enum value 3
        put(215, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(216, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(217, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(218, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(219, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(220, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(221, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(222, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(223, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(224, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(225, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(226, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(227, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(228, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(229, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(231, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(232, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(233, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(234, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(235, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(236, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(237, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(238, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(239, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(240, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(241, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(242, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(243, SpreadTargetClass.TARGET_DEPENDS) // pinned enum value 3
        put(244, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(245, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(246, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(247, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(248, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(249, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(250, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(251, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(252, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(253, SpreadTargetClass.TARGET_RANDOM) // pinned enum value 5
        put(254, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(255, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(256, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(257, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(258, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(259, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(260, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(261, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(262, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(263, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(264, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(265, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(266, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(268, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(269, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(271, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(272, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(273, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(274, SpreadTargetClass.TARGET_DEPENDS) // pinned enum value 3
        put(275, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(276, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(277, SpreadTargetClass.TARGET_DEPENDS) // pinned enum value 3
        put(278, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(279, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(280, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(281, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(282, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(283, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(284, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(285, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(286, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(287, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(288, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(289, SpreadTargetClass.TARGET_DEPENDS) // pinned enum value 3
        put(290, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(291, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(292, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(293, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(295, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(296, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(297, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(298, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(299, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(300, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(301, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(302, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(303, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(304, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(305, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(306, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(307, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(308, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(309, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(310, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(311, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(312, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(313, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(314, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(315, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(316, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(317, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(318, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(319, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(320, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(321, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(322, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(323, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(324, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(325, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(326, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(327, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(328, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(329, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(330, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(331, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(332, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(333, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(334, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(335, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(337, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(338, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(339, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(340, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(341, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(342, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(343, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(344, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(345, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(346, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(347, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(348, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(349, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(350, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(351, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(352, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(353, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(354, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(355, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(356, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(357, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(358, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(359, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(360, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(361, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(362, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(363, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(364, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(365, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(366, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(367, SpreadTargetClass.TARGET_USER_OR_ALLY) // pinned enum value 10
        put(368, SpreadTargetClass.TARGET_DEPENDS) // pinned enum value 3
        put(369, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(370, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(371, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(372, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(373, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(374, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(375, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(376, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(377, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(378, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(379, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(380, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(381, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(382, SpreadTargetClass.TARGET_OPPONENT) // pinned enum value 4
        put(383, SpreadTargetClass.TARGET_DEPENDS) // pinned enum value 3
        put(384, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(385, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(386, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(387, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(388, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(389, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(390, SpreadTargetClass.TARGET_OPPONENTS_FIELD) // pinned enum value 13
        put(391, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(392, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(393, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(394, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(395, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(396, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(397, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(398, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(399, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(400, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(401, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(402, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(403, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(404, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(405, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(406, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(407, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(408, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(409, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(410, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(411, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(412, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(413, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(414, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(415, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(416, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(417, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(418, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(419, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(420, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(421, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(422, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(423, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(424, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(425, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(426, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(427, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(428, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(429, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(430, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(431, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(432, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(433, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(434, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(435, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(436, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(437, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(438, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(439, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(440, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(441, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(442, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(443, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(444, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(445, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(446, SpreadTargetClass.TARGET_OPPONENTS_FIELD) // pinned enum value 13
        put(447, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(448, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(449, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(450, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(451, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(452, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(453, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(454, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(455, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(456, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(457, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(458, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(459, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(460, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(461, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(462, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(463, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(464, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(465, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(466, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(467, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(468, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(469, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(470, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(471, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(472, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(473, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(474, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(475, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(476, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(477, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(478, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(479, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(480, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(481, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(482, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(483, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(484, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(485, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(486, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(487, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(488, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(489, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(490, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(491, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(492, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(493, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(494, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(495, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(496, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(497, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(498, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(499, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(500, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(501, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(502, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(503, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(504, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(505, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(506, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(507, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(508, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(509, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(510, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(511, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(512, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(513, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(514, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(515, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(516, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(517, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(518, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(519, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(520, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(521, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(522, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(523, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(524, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(525, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(526, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(527, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(528, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(529, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(530, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(531, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(532, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(533, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(534, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(535, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(536, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(537, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(538, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(539, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(540, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(541, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(542, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(543, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(544, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(545, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(546, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(547, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(548, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(549, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(550, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(551, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(552, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(553, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(554, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(555, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(556, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(557, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(558, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(559, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(560, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(561, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(562, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(563, SpreadTargetClass.TARGET_ALL_BATTLERS) // pinned enum value 14
        put(564, SpreadTargetClass.TARGET_OPPONENTS_FIELD) // pinned enum value 13
        put(565, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(566, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(567, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(568, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(569, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(570, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(571, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(572, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(573, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(574, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(575, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(576, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(577, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(578, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(579, SpreadTargetClass.TARGET_ALL_BATTLERS) // pinned enum value 14
        put(580, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(581, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(582, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(583, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(584, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(585, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(586, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(587, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(588, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(589, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(590, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(591, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(592, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(593, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(594, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(595, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(596, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(597, SpreadTargetClass.TARGET_ALLY) // pinned enum value 8
        put(598, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(599, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(600, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(601, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(602, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(603, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(604, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(605, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(606, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(607, SpreadTargetClass.TARGET_ALLY) // pinned enum value 8
        put(608, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(609, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(610, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(611, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(612, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(613, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(614, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(615, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(616, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(617, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(618, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(619, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(620, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(621, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(622, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(623, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(624, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(625, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(626, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(627, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(628, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(629, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(630, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(631, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(632, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(633, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(634, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(635, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(636, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(637, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(638, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(639, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(640, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(641, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(642, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(643, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(644, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(645, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(646, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(647, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(648, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(649, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(650, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(651, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(652, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(653, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(654, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(655, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(656, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(657, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(658, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(659, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(660, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(661, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(662, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(663, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(664, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(665, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(666, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(667, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(668, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(669, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(670, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(671, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(672, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(673, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(674, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(675, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(676, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(677, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(678, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(679, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(680, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(681, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(682, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(683, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(684, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(685, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(686, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(687, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(688, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(689, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(690, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(691, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(692, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(693, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(694, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(695, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(696, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(697, SpreadTargetClass.TARGET_SMART) // pinned enum value 2
        put(698, SpreadTargetClass.TARGET_ALL_BATTLERS) // pinned enum value 14
        put(699, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(700, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(701, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(702, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(703, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(704, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(705, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(706, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(707, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(708, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(709, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(710, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(711, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(712, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(713, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(714, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(715, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(716, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(717, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(718, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(719, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(720, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(721, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(722, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(723, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(724, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(725, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(726, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(727, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(728, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(729, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(730, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(731, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(732, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(733, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(734, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(735, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(736, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(737, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(738, SpreadTargetClass.TARGET_FOES_AND_ALLY) // pinned enum value 11
        put(739, SpreadTargetClass.TARGET_ALLY) // pinned enum value 8
        put(740, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(741, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(742, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(743, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(744, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(745, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(746, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(747, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(748, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(749, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(750, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(751, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(752, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(753, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(754, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(755, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(756, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(757, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(758, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(759, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(760, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(761, SpreadTargetClass.TARGET_RANDOM) // pinned enum value 5
        put(762, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(763, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(764, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(765, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(766, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(767, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(768, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(769, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(770, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(771, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(772, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(773, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(774, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(775, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(776, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(777, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(778, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(779, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(780, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(781, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(782, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(783, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(784, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(785, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(786, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(787, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(788, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(789, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(790, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(791, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(792, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(793, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(794, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(795, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(796, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(797, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(798, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(799, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(800, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(801, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(802, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(803, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(804, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(805, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(806, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(807, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(808, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(809, SpreadTargetClass.TARGET_FIELD) // pinned enum value 12
        put(810, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(811, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(812, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(813, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(814, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(815, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(816, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(817, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(818, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(819, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(820, SpreadTargetClass.TARGET_DEPENDS) // pinned enum value 3
        put(821, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(822, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(823, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(824, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(825, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(826, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(827, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(828, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(829, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(830, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(831, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(832, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(833, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(834, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(835, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(836, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(837, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(838, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(839, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(840, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(841, SpreadTargetClass.TARGET_ALLY) // pinned enum value 8
        put(842, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(843, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(844, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(845, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(846, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(847, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(848, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(849, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(850, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(851, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(852, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(853, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(854, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(855, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(856, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(857, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(858, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(859, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(860, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(861, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(862, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(863, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(864, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(865, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(866, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(867, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(868, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(869, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(870, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(871, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(872, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(873, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(874, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(875, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(876, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(877, SpreadTargetClass.TARGET_BOTH) // pinned enum value 6
        put(878, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(879, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(880, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(881, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(882, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(883, SpreadTargetClass.TARGET_USER) // pinned enum value 7
        put(884, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(885, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(886, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(887, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(888, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(889, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(890, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(891, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(892, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(893, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(894, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(895, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(896, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(897, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(898, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(899, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(900, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(901, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(902, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(903, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(904, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(905, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(906, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(907, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(908, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(909, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(910, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(911, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(912, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(913, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(914, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(915, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(916, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(917, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(918, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(919, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(920, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(921, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(922, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(923, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(924, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(925, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(926, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(927, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(928, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(929, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(930, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(931, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(932, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(933, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
        put(934, SpreadTargetClass.TARGET_SELECTED) // pinned enum value 1
    }
}
