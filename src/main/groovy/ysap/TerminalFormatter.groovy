package ysap

class TerminalFormatter {
    static String formatText(String text, String style = "default", String color = "default", String special = "none") {
        def ansiCode = generateAnsiCode(style, color, special)
        return "${ansiCode}${text}${ansiReset()}"
    }

    private static String generateAnsiCode(String style, String color, String special) {
        def codes = []

        def styleCode = getStyleCode(style)
        def colorCode = getColorCode(color)
        def specialCode = getSpecialCode(special)

        if (styleCode != 0) codes << styleCode
        if (colorCode != 39) codes << colorCode
        if (specialCode != 0) codes << specialCode

        if (codes.isEmpty()) {
            return "\033[0m"
        }
        return "\033[${codes.join(';')}m"
    }

    private static int getStyleCode(String style) {
        switch (style) {
            case "bold":          return 1
            case "underline":     return 4
            case "italic":        return 3
            case "strikethrough": return 9
            default:              return 0
        }
    }

    private static int getColorCode(String color) {
        switch (color) {
            case "black":   return 30
            case "red":     return 31
            case "green":   return 32
            case "yellow":  return 33
            case "blue":    return 34
            case "magenta": return 35
            case "cyan":    return 36
            case "white":   return 37
            case "brightBlack":   return 90
            case "brightRed":     return 91
            case "brightGreen":   return 92
            case "brightYellow":  return 93
            case "brightBlue":    return 94
            case "brightMagenta": return 95
            case "brightCyan":    return 96
            case "brightWhite":   return 97
            default:        return 39
        }
    }

    private static int getSpecialCode(String special) {
        switch (special) {
            case "blink":            return 5
            case "reverse":          return 7
            case "conceal":          return 8
            case "doubleunderline":  return 21
            case "framed":           return 51
            case "encircled":        return 52
            default:                 return 0
        }
    }

    private static String ansiReset() {
        return "\033[0m"
    }
}
