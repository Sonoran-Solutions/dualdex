package com.dualdex.companion.ui

/**
 * Small proportional bitmap font for the in-game-style party slots (issue #151).
 *
 * Original DualDex artwork drawn for this project; it is not extracted from, traced from, or
 * derived from any game's font data. Glyphs sit on a [HEIGHT]-row grid: capitals and digits use
 * rows 0..6 ([CAP_HEIGHT]), lowercase x-height is rows 2..6, descenders use rows 7..8. Glyphs are
 * drawn one GBA pixel per cell and advance by their own width plus one pixel of spacing.
 */
object PixelFont {
    const val HEIGHT = 9
    const val CAP_HEIGHT = 7
    private const val SPACING = 1

    /** Rows of a glyph, top first; '#' is ink. Short glyphs are padded with blank rows. */
    class Glyph(val width: Int, val rows: List<String>) {
        fun isInk(x: Int, y: Int): Boolean = y in rows.indices && x in 0 until rows[y].length && rows[y][x] == '#'
    }

    private val source: Map<Char, String> = mapOf(
        'A' to ".###. #...# #...# ##### #...# #...# #...#",
        'B' to "####. #...# #...# ####. #...# #...# ####.",
        'C' to ".###. #...# #.... #.... #.... #...# .###.",
        'D' to "####. #...# #...# #...# #...# #...# ####.",
        'E' to "##### #.... #.... ####. #.... #.... #####",
        'F' to "##### #.... #.... ####. #.... #.... #....",
        'G' to ".###. #...# #.... #.### #...# #...# .####",
        'H' to "#...# #...# #...# ##### #...# #...# #...#",
        'I' to "### .#. .#. .#. .#. .#. ###",
        'J' to "..### ...#. ...#. ...#. #..#. #..#. .##..",
        'K' to "#...# #..#. #.#.. ##... #.#.. #..#. #...#",
        'L' to "#.... #.... #.... #.... #.... #.... #####",
        'M' to "#...# ##.## #.#.# #.#.# #...# #...# #...#",
        'N' to "#...# ##..# #.#.# #.#.# #..## #...# #...#",
        'O' to ".###. #...# #...# #...# #...# #...# .###.",
        'P' to "####. #...# #...# ####. #.... #.... #....",
        'Q' to ".###. #...# #...# #...# #.#.# #..#. .##.#",
        'R' to "####. #...# #...# ####. #.#.. #..#. #...#",
        'S' to ".#### #.... #.... .###. ....# ....# ####.",
        'T' to "##### ..#.. ..#.. ..#.. ..#.. ..#.. ..#..",
        'U' to "#...# #...# #...# #...# #...# #...# .###.",
        'V' to "#...# #...# #...# #...# #...# .#.#. ..#..",
        'W' to "#...# #...# #...# #.#.# #.#.# ##.## #...#",
        'X' to "#...# #...# .#.#. ..#.. .#.#. #...# #...#",
        'Y' to "#...# #...# .#.#. ..#.. ..#.. ..#.. ..#..",
        'Z' to "##### ....# ...#. ..#.. .#... #.... #####",
        'a' to "..... ..... .###. ....# .#### #...# .####",
        'b' to "#.... #.... ####. #...# #...# #...# ####.",
        'c' to ".... .... .### #... #... #... .###",
        'd' to "....# ....# .#### #...# #...# #...# .####",
        'e' to "..... ..... .###. #...# ##### #.... .###.",
        'f' to "..## .#.. #### .#.. .#.. .#.. .#..",
        'g' to "..... ..... .#### #...# #...# #...# .#### ....# .###.",
        'h' to "#.... #.... ####. #...# #...# #...# #...#",
        'i' to ".#. ... ##. .#. .#. .#. ###",
        'j' to "..# ... .## ..# ..# ..# ..# #.# .#.",
        'k' to "#... #... #..# #.#. ##.. #.#. #..#",
        'l' to "##. .#. .#. .#. .#. .#. ###",
        'm' to "..... ..... ##.#. #.#.# #.#.# #.#.# #.#.#",
        'n' to ".... .... ###. #..# #..# #..# #..#",
        'o' to "..... ..... .###. #...# #...# #...# .###.",
        'p' to "..... ..... ####. #...# #...# #...# ####. #.... #....",
        'q' to "..... ..... .#### #...# #...# #...# .#### ....# ....#",
        'r' to ".... .... #.## ##.. #... #... #...",
        's' to ".... .... .### #... .##. ...# ###.",
        't' to ".#.. .#.. #### .#.. .#.. .#.. ..##",
        'u' to ".... .... #..# #..# #..# #..# .###",
        'v' to "..... ..... #...# #...# #...# .#.#. ..#..",
        'w' to "..... ..... #...# #...# #.#.# #.#.# .#.#.",
        'x' to "..... ..... #...# .#.#. ..#.. .#.#. #...#",
        'y' to ".... .... #..# #..# #..# #..# .### ...# .##.",
        'z' to "..... ..... ##### ...#. ..#.. .#... #####",
        '0' to ".##. #..# #..# #..# #..# #..# .##.",
        '1' to ".#. ##. .#. .#. .#. .#. ###",
        '2' to ".##. #..# ...# ..#. .#.. #... ####",
        '3' to "###. ...# ...# .##. ...# ...# ###.",
        '4' to "..#. .##. #.#. #.#. #### ..#. ..#.",
        '5' to "#### #... ###. ...# ...# #..# .##.",
        '6' to ".##. #... #... ###. #..# #..# .##.",
        '7' to "#### ...# ..#. ..#. .#.. .#.. .#..",
        '8' to ".##. #..# #..# .##. #..# #..# .##.",
        '9' to ".##. #..# #..# .### ...# ...# .##.",
        ' ' to "... ... ... ... ... ... ...",
        '/' to "...# ..#. ..#. .#.. .#.. #... #...",
        '.' to ". . . . . . #",
        ',' to ". . . . . . # #",
        '-' to ".... .... .... #### .... .... ....",
        '\'' to "# # . . . . .",
        '!' to "# # # # # . #",
        '?' to ".##. #..# ...# ..#. .#.. .... .#..",
        ':' to ". . # . . # .",
        '(' to ".# #. #. #. #. #. .#",
        ')' to "#. .# .# .# .# .# #.",
        '+' to "..... ..#.. ..#.. ##### ..#.. ..#.. .....",
        '♂' to "..#### ....## .###.# #...#. #...#. #...#. .###..",
        '♀' to ".###. #...# #...# .###. ..#.. ##### ..#..",
        '★' to "..#.. ..#.. .###. ##### .###. .#.#. #...#",
        'é' to "...#. ..#.. .###. #...# ##### #.... .###.",
    )

    private val glyphs: Map<Char, Glyph> = source.mapValues { (_, spec) ->
        val rows = spec.split(' ')
        Glyph(rows.maxOf { it.length }, rows + List(HEIGHT - rows.size) { "" })
    }

    private val fallback: Glyph = glyphs.getValue('?')

    val supportedCharacters: Set<Char> get() = glyphs.keys

    /** The glyph for [c]; unsupported characters render as '?' rather than disappearing. */
    fun glyph(c: Char): Glyph = glyphs[c] ?: fallback

    /** Advance width in font pixels, without trailing spacing. */
    fun measure(text: String): Int =
        if (text.isEmpty()) 0 else text.sumOf { glyph(it).width + SPACING } - SPACING

    /** The longest prefix of [text] that fits in [maxWidth] font pixels. */
    fun fit(text: String, maxWidth: Int): String {
        var end = text.length
        while (end > 0 && measure(text.substring(0, end)) > maxWidth) end--
        return text.substring(0, end)
    }

    /** Calls [plot] for every ink pixel of [text] drawn with its top-left at ([x], [y]). */
    inline fun forEachInk(text: String, x: Int, y: Int, plot: (Int, Int) -> Unit) {
        var cursor = x
        for (c in text) {
            val g = glyph(c)
            for (row in 0 until HEIGHT) for (col in 0 until g.width) {
                if (g.isInk(col, row)) plot(cursor + col, y + row)
            }
            cursor += g.width + 1
        }
    }
}
