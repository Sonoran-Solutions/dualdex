"""Strict bounded decoders for the exact R.O.W.E. text fields under study.

This intentionally supports only the ordinary Gen III glyphs observed in the
name tables and party captures. Unknown glyphs and control sequences keep their
raw bytes and refuse to render.
"""
from dataclasses import dataclass


VALID_TEXT = "VALID_TEXT"
EMPTY_OR_UNSET = "EMPTY_OR_UNSET"
UNKNOWN_GLYPH = "UNKNOWN_GLYPH"
MALFORMED_CONTROL = "MALFORMED_CONTROL"
INVALID_TERMINATION = "INVALID_TERMINATION"
UNSUPPORTED_MODE = "UNSUPPORTED_MODE"

WIDTHS = {
    "species_name": 13,
    "move_name": 17,
    "item_name": 18,
    "party_nickname": 12,
}
VARIABLE_WIDTHS = {"map_section_name": 64}

_GLYPHS = {
    0x00: " ", 0x1B: "é", 0xAB: "!", 0xAC: "?", 0xAD: ".", 0xAE: "-",
    0xB0: "…", 0xB1: "“", 0xB2: "”", 0xB3: "‘", 0xB4: "’",
    0xB5: "♂", 0xB6: "♀", 0xB8: ",", 0xBA: "/", 0xF0: ":",
}
_GLYPHS.update({0xA1 + i: str(i) for i in range(10)})
_GLYPHS.update({0xBB + i: chr(ord("A") + i) for i in range(26)})
_GLYPHS.update({0xD5 + i: chr(ord("a") + i) for i in range(26)})
_CONTROLS = {0xFA, 0xFB, 0xFC, 0xFD, 0xFE}
_PADDING = {0x00, 0xFF}


@dataclass(frozen=True)
class TextResult:
    status: str
    text: str | None
    raw_hex: str
    terminator_index: int | None
    detail: str | None = None


def decode_text(raw: bytes, domain: str) -> TextResult:
    """Decode one fixed-width field without discarding unknown bytes.

    A 12-byte party nickname may use all twelve slots and therefore have no
    0xFF terminator. Other supported fields require an in-width terminator.
    """
    raw = bytes(raw)
    raw_hex = raw.hex()
    width = WIDTHS.get(domain)
    if width is None and domain in VARIABLE_WIDTHS:
        if not raw or len(raw) > VARIABLE_WIDTHS[domain] or raw[-1] != 0xFF:
            return TextResult(INVALID_TERMINATION, None, raw_hex, None,
                              "bounded map name lacks an in-range terminator")
        width = len(raw)
    if width is None:
        return TextResult(UNSUPPORTED_MODE, None, raw_hex, None, "unsupported text domain")
    if domain in VARIABLE_WIDTHS:
        if len(raw) != width:
            return TextResult(INVALID_TERMINATION, None, raw_hex, None, "wrong map-name span")
    elif len(raw) != width:
        return TextResult(INVALID_TERMINATION, None, raw_hex, None, "wrong field width")
    if not raw or all(byte == 0 for byte in raw):
        return TextResult(EMPTY_OR_UNSET, "", raw_hex, None, "all-zero field")

    try:
        terminator = raw.index(0xFF)
    except ValueError:
        terminator = None

    if terminator is None:
        if domain != "party_nickname":
            return TextResult(INVALID_TERMINATION, None, raw_hex, None, "missing 0xFF terminator")
        content = raw
    else:
        if any(byte not in _PADDING for byte in raw[terminator + 1:]):
            return TextResult(INVALID_TERMINATION, None, raw_hex, terminator,
                              "non-padding byte follows terminator")
        if terminator == 0:
            return TextResult(EMPTY_OR_UNSET, "", raw_hex, terminator, "empty field")
        content = raw[:terminator]

    decoded = []
    for index, byte in enumerate(content):
        if byte in _CONTROLS:
            if byte in (0xFC, 0xFD) and index + 1 == len(content):
                return TextResult(MALFORMED_CONTROL, None, raw_hex, terminator,
                                  f"control 0x{byte:02x} has no operand")
            return TextResult(UNSUPPORTED_MODE, None, raw_hex, terminator,
                              f"control 0x{byte:02x} is not decoded in {domain}")
        glyph = _GLYPHS.get(byte)
        if glyph is None:
            return TextResult(UNKNOWN_GLYPH, None, raw_hex, terminator,
                              f"unknown glyph 0x{byte:02x} at byte {index}")
        decoded.append(glyph)

    return TextResult(VALID_TEXT, "".join(decoded), raw_hex, terminator,
                      "full-width nickname" if terminator is None else None)
