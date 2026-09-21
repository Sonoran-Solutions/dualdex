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
 * state-dependent damage flag. See generate_hns_move_effects.py for the exact rule.
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
