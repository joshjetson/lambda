package ysap

class TerminalFormatter {
    static String formatText(String text, String style = "default", String color = "default", String special = "none") {
        def ansiCode = generateAnsiCode(style, color, special)
        return "${ansiCode}${text}${ansiReset()}"
    }

    private static String generateAnsiCode(String style, String color, String special) {
        def styleCode = getStyleCode(style)
        def colorCode = getColorCode(color)
        def specialCode = getSpecialCode(special)

        return "\033[${styleCode};${colorCode};${specialCode}m"
    }

    private static int getStyleCode(String style) {
        switch (style) {
            case "bold":          return 1
            case "underline":     return 4
            case "italic":        return 3
            case "strikethrough": return 9
            default:              return 0 // Default style
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
            default:        return 39 // Default color
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
            default:                 return 0 // No special effect
        }
    }

    private static String ansiReset() {
        return "\033[0m"
    }
}
