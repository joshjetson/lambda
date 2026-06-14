package ysap.helpers

import ysap.TerminalFormatter

/**
 * The system error catalogue — errno-style codes that make the game read like a computer system.
 *
 * A pure static registry (no Spring), mirroring PlayerHelp / BoxBuilder / TerminalFormatter. Emitters
 * format an error via the per-code helpers (e.g. sectorDamaged) or the generic line(); players look one
 * up with the `errno <n>` command. To extend coverage, add a row to CODES and (optionally) a per-code
 * body formatter — the catalogue stays the single source of every error's name / summary / detail / remedy.
 */
class ErrorCodes {

    // code → [name, summary, detail, remedy]. The immutable single source of the error catalogue.
    static final Map<Integer, Map> CODES = [
        5: [
            name   : 'SECTOR_DAMAGED',
            summary: 'Sector wiped by defrag processes — entry blocked until repaired.',
            detail : 'The sector health has decayed to 0 (defrag_wiped=true). A Lambda entity cannot occupy a damaged sector, so navigation into it is refused.',
            remedy : "Move to an ADJACENT sector, then run 'repair <x> <y>' to start the repair mini-game and restore it."
        ]
    ].asImmutable()

    /** "errno N: <body>" — the one-line error prefix shared by every emitter. */
    static String line(int code, String body) {
        return "errno ${code}: ${body}"
    }

    /**
     * The errno-5 sector-damaged line with live coordinates. The single source of the SECTOR_DAMAGED
     * body shape, so every site that refuses entry to a wiped sector reads identically.
     */
    static String sectorDamaged(int x, int y) {
        return line(5, "Sector (${x},${y}) DAMAGED, defrag_wiped=true repair_required=true") + " — run 'errno 5'"
    }

    /** Man-page block for `errno <n>`. Unknown code → a clean not-found line. */
    static String lookup(Integer code) {
        def e = code == null ? null : CODES[code]
        if (!e) {
            return TerminalFormatter.formatText("errno ${code}: no such error code. Known: ${CODES.keySet().sort().join(', ')}", 'bold', 'yellow') + "\r\n"
        }
        def sb = new StringBuilder()
        sb.append(TerminalFormatter.formatText("errno ${code}: ${e.name}", 'bold', 'red')).append("\r\n")
        sb.append("  ${e.summary}\r\n\r\n")
        sb.append("  ${e.detail}\r\n\r\n")
        sb.append(TerminalFormatter.formatText("  remedy: ${e.remedy}", 'bold', 'green')).append("\r\n")
        return sb.toString()
    }

    /** `errno` with no / non-numeric arg → list the catalogue. */
    static String index() {
        def sb = new StringBuilder()
        sb.append(TerminalFormatter.formatText("=== SYSTEM ERROR CODES ===", 'bold', 'cyan')).append("\r\n")
        CODES.sort { it.key }.each { code, e ->
            sb.append("  errno ${code}: ${e.name} — ${e.summary}\r\n")
        }
        sb.append("  Use 'errno <n>' for details.\r\n")
        return sb.toString()
    }
}
