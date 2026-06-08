package ysap

import java.util.regex.Pattern

/**
 * A scripted telnet "player bot" for driving the Lambda game over a real socket.
 *
 * The game server (TelnetServerService) speaks raw telnet: it negotiates
 * character-at-a-time mode with IAC sequences, server-echoes keystrokes, and wraps
 * output in ANSI colour codes. This client transparently:
 *   - strips telnet IAC command/subnegotiation sequences from the input stream,
 *   - strips ANSI/CSI escape sequences so assertions can match plain text,
 *   - accumulates everything into a buffer you can read with {@link #text()},
 *   - blocks until an expected token / the game prompt appears.
 *
 * Usage (see GameplayHarnessSpec):
 *   def bot = new LambdaTelnetClient('localhost', 2323)
 *   bot.createCharacter('botuser', 'BotUser', 1)   // drives login handshake
 *   String out = bot.command('cc 1,1')              // sends + waits for next prompt
 *   assert out =~ /1:\(1,1\)\s*>/
 *
 * It can also be pointed at a live dev server on port 23 for manual exploration.
 */
class LambdaTelnetClient {

    /** Matches the in-game prompt after ANSI stripping, e.g. "1:(0,0) >". */
    static final Pattern PROMPT = ~/\d+:\(\d+,\d+\)\s*>/

    private final Socket socket
    private final InputStream rawIn
    private final OutputStream out
    private final ByteArrayOutputStream acc = new ByteArrayOutputStream()

    long defaultTimeoutMs = 12000

    LambdaTelnetClient(String host = 'localhost', int port) {
        socket = new Socket(host, port)
        socket.soTimeout = 150   // short read timeout so pump() polls without blocking forever
        rawIn = new BufferedInputStream(socket.getInputStream())
        out = socket.getOutputStream()
    }

    /** Drain whatever bytes are available, filtering telnet IAC sequences into oblivion. */
    private void pump() {
        try {
            int b
            while ((b = rawIn.read()) != -1) {
                if (b == 255) {            // IAC
                    int cmd = rawIn.read()
                    if (cmd == 250) {      // SB ... IAC SE  (subnegotiation)
                        int x
                        while ((x = rawIn.read()) != -1) {
                            if (x == 255 && rawIn.read() == 240) break   // IAC SE
                        }
                    } else if (cmd >= 251 && cmd <= 254) {
                        rawIn.read()       // WILL/WONT/DO/DONT + option byte
                    }
                    continue
                }
                acc.write(b)
            }
        } catch (SocketTimeoutException ignored) {
            // no more bytes right now — expected
        }
    }

    /** Everything received so far, decoded UTF-8 with ANSI escapes stripped. */
    String text() {
        stripAnsi(new String(acc.toByteArray(), 'UTF-8'))
    }

    static String stripAnsi(String s) {
        s.replaceAll(/\[[0-9;?]*[ -\/]*[@-~]/, '')   // CSI sequences (colours, cursor moves)
         .replaceAll(/[()][AB0-2]/, '')              // charset designators
         .replaceAll(/[=>]/, '')                      // keypad mode
    }

    /** Forget all accumulated output — call before a command to scope the next assertion. */
    void clear() { acc.reset() }

    /** Block until {@code pattern} appears in the accumulated text, or throw on timeout. */
    String readUntil(Pattern pattern, long timeoutMs = defaultTimeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            pump()
            if (pattern.matcher(text()).find()) return text()
            Thread.sleep(40)
        }
        throw new IllegalStateException("Timed out after ${timeoutMs}ms waiting for /${pattern}/.\n--- last output ---\n${tail(text(), 1200)}")
    }

    String readUntil(String token, long timeoutMs = defaultTimeoutMs) {
        readUntil(Pattern.compile(Pattern.quote(token)), timeoutMs)
    }

    /** Send a line (server expects a CR/LF terminated line). */
    void send(String s) {
        out.write((s + "\r\n").getBytes('UTF-8'))
        out.flush()
    }

    /**
     * Drive the new-entity creation handshake and return once the game prompt is showing.
     */
    void createCharacter(String username, String displayName, int avatar = 1) {
        readUntil('Enter username', 20000)   // includes the welcome animation
        send('new')
        readUntil('Choose username')
        send(username)
        readUntil('Choose display name')
        send(displayName)
        readUntil('Select your digital form')
        send(String.valueOf(avatar))
        readUntil(PROMPT)                     // dashboard + first prompt
    }

    /** Log in as an existing entity and return once the game prompt is showing. */
    void login(String username) {
        readUntil('Enter username', 20000)
        send(username)
        readUntil(PROMPT)
    }

    /**
     * Send a game command and return the output produced up to (and including) the next prompt.
     * The returned text includes the server's echo of the command itself.
     */
    String command(String cmd, long timeoutMs = defaultTimeoutMs) {
        clear()
        send(cmd)
        readUntil(PROMPT, timeoutMs)
    }

    void close() {
        try { send('quit') } catch (ignored) { }
        try { socket.close() } catch (ignored) { }
    }

    private static String tail(String s, int n) { s.length() <= n ? s : s.substring(s.length() - n) }
}
