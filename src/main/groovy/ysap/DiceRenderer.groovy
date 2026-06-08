package ysap

/**
 * Renders a pair of six-sided dice as ASCII for the `dados` roll. Left die = Y axis, right die = X.
 * Each face is 3 rows of pips; the budget caption (die + any movement bonus) is shown below.
 */
class DiceRenderer {

    private static final Map<Integer, List<String>> FACES = [
        1: ['     ', '  *  ', '     '],
        2: ['*    ', '     ', '    *'],
        3: ['*    ', '  *  ', '    *'],
        4: ['*   *', '     ', '*   *'],
        5: ['*   *', '  *  ', '*   *'],
        6: ['*   *', '*   *', '*   *']
    ]

    /** Two dice side by side. `caption` (nullable) goes underneath (e.g. "Y=6  X=4"). Lines use \r\n. */
    static String pair(int leftY, int rightX, String caption) {
        def L = FACES[leftY] ?: FACES[1]
        def R = FACES[rightX] ?: FACES[1]
        def sb = new StringBuilder()
        sb.append("   .-------.   .-------.\r\n")
        for (int i = 0; i < 3; i++) {
            sb.append("   | ${L[i]} |   | ${R[i]} |\r\n")
        }
        sb.append("   '-------'   '-------'\r\n")
        if (caption) sb.append("   ${caption}\r\n")
        return sb.toString()
    }

    /** Number of terminal lines `pair()` emits (for in-place animation cursor math). */
    static int lineCount(boolean withCaption) {
        return withCaption ? 6 : 5
    }
}
