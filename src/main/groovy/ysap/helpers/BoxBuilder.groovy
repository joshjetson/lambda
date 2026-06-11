package ysap.helpers

class BoxBuilder {
    static final int DEFAULT_WIDTH = 80

    // Box drawing characters
    static final String TOP_LEFT = "╔"
    static final String TOP_RIGHT = "╗"
    static final String BOTTOM_LEFT = "╚"
    static final String BOTTOM_RIGHT = "╝"
    static final String HORIZONTAL = "═"
    static final String VERTICAL = "║"
    static final String CROSS_LEFT = "╠"
    static final String CROSS_RIGHT = "╣"

    private List<String> lines = []
    private int width

    BoxBuilder(int width = DEFAULT_WIDTH) {
        this.width = width
    }

    // Add raw ASCII art (doesn't pad, assumes it's already formatted)
    BoxBuilder addAsciiArt(String asciiArt) {
        asciiArt.split('\n').each { line ->
            // Trim any trailing spaces but preserve the line
            lines.add(line.stripTrailing())
        }
        return this
    }

    // Strip ANSI SGR escapes (ESC [ ... m) so width math counts VISIBLE characters, not color codes.
    private static String stripAnsi(String s) {
        return s == null ? '' : s.replaceAll(/\x1b\[[0-9;]*m/, '')
    }

    // Visible width of a line (ignoring ANSI color codes).
    private static int visibleLength(String s) {
        return stripAnsi(s).length()
    }

    // Add a centered line
    BoxBuilder addCenteredLine(String text) {
        def padding = Math.max(0, width - visibleLength(text))
        def leftPad = (padding / 2) as int
        def rightPad = padding - leftPad
        lines.add("${' ' * leftPad}${text}${' ' * rightPad}")
        return this
    }

    // Add a left-aligned line
    BoxBuilder addLine(String text) {
        def visible = visibleLength(text)
        lines.add(visible < width ? text + (' ' * (width - visible)) : text)
        return this
    }

    // Add an empty line
    BoxBuilder addEmptyLine() {
        lines.add(' ' * width)
        return this
    }

    // Add a separator line
    BoxBuilder addSeparator() {
        lines.add("SEPARATOR")
        return this
    }

    // Build the final box
    String build() {
        def result = new StringBuilder()

        // Top border
        result.append(TOP_LEFT).append(HORIZONTAL * width).append(TOP_RIGHT).append("\r\n")

        // Content lines — pad to the box's VISIBLE width (color codes do not occupy columns).
        lines.each { line ->
            if (line == "SEPARATOR") {
                result.append(CROSS_LEFT).append(HORIZONTAL * width).append(CROSS_RIGHT).append("\r\n")
            } else {
                def visible = visibleLength(line)
                def paddedLine = visible < width ? line + (' ' * (width - visible)) : line
                result.append(VERTICAL).append(paddedLine).append(VERTICAL).append("\r\n")
            }
        }

        // Bottom border
        result.append(BOTTOM_LEFT).append(HORIZONTAL * width).append(BOTTOM_RIGHT).append("\r\n")

        return result.toString()
    }
}
