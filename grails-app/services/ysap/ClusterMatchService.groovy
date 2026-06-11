package ysap

import ysap.helpers.BoxBuilder

/**
 * Cluster Mode referee — match/lobby lifecycle, team membership, and role + true/decoy assignment.
 * One bounded concern (match truth); information/abilities live in ClusterRoleService (later pieces),
 * occupancy enforcement in CoordinateStateService. Role IS the player's ethnicity (avatarSilhouette);
 * identity is reused, not reinvented. All DB access is wrapped in withTransaction (telnet-thread rule).
 *
 * Additive: nothing here runs unless a player types `cluster ...`; Node/FFA play is untouched.
 */
class ClusterMatchService {

    static final int TEAM_CAP = 7

    // A full team = 2 Lambdas + one of each of the other 5 roles (race once per team, Lambda twice).
    private static final List<String> CANON_TEAM = [
        'CLASSIC_LAMBDA', 'CLASSIC_LAMBDA', 'CIRCUIT_PATTERN', 'GEOMETRIC_ENTITY',
        'FLOWING_CURRENT', 'DIGITAL_GHOST', 'BINARY_FORM'
    ].asImmutable()

    // The 6 ethnicities ARE the 6 Cluster roles. One source of the role-name mapping (DRY).
    private static final Map<String, String> ROLE_LABEL = [
        'CLASSIC_LAMBDA' : 'Lambda (Collector)',
        'CIRCUIT_PATTERN': 'Circuit (Tracker)',
        'GEOMETRIC_ENTITY': 'Geo (Scout)',
        'FLOWING_CURRENT': 'Current (Disruptor)',
        'DIGITAL_GHOST'  : 'Ghost (Saboteur)',
        'BINARY_FORM'    : 'Binary (Trapper)'
    ].asImmutable()

    /** Single source of the role-name mapping (used by the panel renderer AND the firewall). */
    static String roleLabel(String role) { ROLE_LABEL[role] ?: role }

    // O(1) sub-command dispatch — no switch/if-ladder (Doctrine #2).
    private final Map<String, Closure> clusterSubcommands = [
        'create': { LambdaPlayer p -> createMatch(p) },
        'join'  : { LambdaPlayer p -> joinMatch(p) },
        'start' : { LambdaPlayer p -> startMatch(p) },
        'status': { LambdaPlayer p -> getClusterStatus(p) },
        'leave' : { LambdaPlayer p -> leaveMatch(p) },
    ]

    /** One-line delegate target for commandHandlers['cluster']. */
    String handleClusterCommand(String command, LambdaPlayer player, PrintWriter writer) {
        def parts = command?.trim()?.toLowerCase()?.split(/\s+/) ?: []
        def sub = parts.length > 1 ? parts[1] : ''
        def handler = clusterSubcommands[sub]
        return handler ? (handler.call(player) as String) : clusterUsage()
    }

    String createMatch(LambdaPlayer player) {
        String result
        ClusterMatch.withTransaction {
            if (findActiveMembership(player.username)) {
                result = warn("You are already in a cluster match — 'cluster status' or 'cluster leave'.")
                return
            }
            def match = new ClusterMatch(matchId: genMatchId())
            def alpha = new ClusterTeam(name: 'ALPHA')
            def beta = new ClusterTeam(name: 'BETA')
            match.addToTeams(alpha); match.addToTeams(beta)
            match.save(failOnError: true)
            def m = new ClusterMembership(username: player.username, role: player.avatarSilhouette)
            alpha.addToMembers(m); alpha.save(failOnError: true)
            result = renderMembershipPanel(m, 'Cluster Match Created')
        }
        return result
    }

    String joinMatch(LambdaPlayer player) {
        String result
        ClusterMatch.withTransaction {
            if (findActiveMembership(player.username)) {
                result = warn("You are already in a cluster match — 'cluster status' or 'cluster leave'.")
                return
            }
            def match = ClusterMatch.findByState('LOBBY', [sort: 'createdDate', order: 'desc'])
            if (!match) { result = warn("No open cluster lobby. Use 'cluster create' to start one."); return }
            def alpha = match.teams.find { it.name == 'ALPHA' }
            def beta = match.teams.find { it.name == 'BETA' }
            // PIECE 1 seating: fill ALPHA, then BETA. Balanced, role-unique seating arrives with
            // bot-fill in PIECE 2; for now this just needs to form a team and seat the player.
            def team = (alpha.members?.size() ?: 0) < TEAM_CAP ? alpha : beta
            def m = new ClusterMembership(username: player.username, role: player.avatarSilhouette)
            team.addToMembers(m); team.save(failOnError: true)
            assignTrueDecoy(team)
            result = renderMembershipPanel(m, 'Joined Cluster Match')
        }
        return result
    }

    String getClusterStatus(LambdaPlayer player) {
        String result
        ClusterMatch.withTransaction {
            def m = findActiveMembership(player.username)
            result = m ? renderMembershipPanel(m, 'Cluster Status')
                       : warn("Not in a cluster match. Use 'cluster create' or 'cluster join'.")
        }
        return result
    }

    String leaveMatch(LambdaPlayer player) {
        String result
        ClusterMatch.withTransaction {
            def m = findActiveMembership(player.username)
            if (!m) { result = info("You are not in a cluster match."); return }
            def team = m.team
            team.removeFromMembers(m); m.delete(failOnError: true)
            result = info("Left the cluster match.")
        }
        return result
    }

    // Fill empty seats with bots so a solo human still gets a real 7v7, then lock in true/decoy per
    // team and flip the match ACTIVE. This is the match-start gate (LOBBY → ACTIVE).
    String startMatch(LambdaPlayer player) {
        String result
        ClusterMatch.withTransaction {
            def m = findActiveMembership(player.username)
            if (!m) { result = warn("Not in a cluster match. Use 'cluster create' first."); return }
            def match = m.team.match
            if (match.state != 'LOBBY') { result = warn("Match ${match.matchId} is already ${match.state}."); return }

            match.teams.each { team -> fillTeamWithBots(team) }   // humans keep their seats; bots fill the rest
            match.teams.each { team -> assignTrueDecoy(team) }    // exactly one true Lambda per full team
            match.state = 'ACTIVE'
            match.save(failOnError: true)

            result = renderMembershipPanel(m, 'Match Started — ACTIVE')
        }
        return result
    }

    // --- internals -------------------------------------------------------------------------------

    // Add bot members until the team matches the canonical composition (multiset difference vs the
    // human/bot seats already present). isBot=true; role drives the bot's behavior in later pieces.
    private void fillTeamWithBots(ClusterTeam team) {
        def need = new ArrayList<String>(CANON_TEAM)
        team.members?.each { mem -> need.remove(mem.role) }   // remove ONE canon slot per existing seat
        int n = 0
        need.each { role ->
            team.addToMembers(new ClusterMembership(
                username: "bot_${team.name.toLowerCase()}_${role.toLowerCase()}_${++n}",
                role: role, isBot: true))
        }
        team.save(failOnError: true)
    }

    private ClusterMembership findActiveMembership(String username) {
        ClusterMembership.findAllByUsername(username).find { it.team?.match?.state != 'ENDED' }
    }

    // Once a team has its 2 Lambdas, flag exactly one as the true Lambda (the other is the decoy).
    // Fixed at this point; data-driven pick, no conditional ladder.
    private void assignTrueDecoy(ClusterTeam team) {
        def lambdas = team.members?.findAll { it.role == 'CLASSIC_LAMBDA' } ?: []
        if (lambdas.size() >= 2 && !lambdas.any { it.isTrueLambda }) {
            def chosen = lambdas[new Random().nextInt(lambdas.size())]
            chosen.isTrueLambda = true
            chosen.save(failOnError: true)
        }
    }

    private String genMatchId() {
        "CM_${System.currentTimeMillis().toString().takeRight(8)}_${new Random().nextInt(900) + 100}"
    }

    // Single renderer shared by create/join/status (DRY). `m` is the VIEWER's membership, so the
    // true/decoy line is self-only; the roster lists names+roles but never another player's bit.
    private String renderMembershipPanel(ClusterMembership m, String title) {
        def team = m.team
        def match = team.match
        def box = new BoxBuilder(70)
            .addCenteredLine(TerminalFormatter.formatText("⬡ ${title}", 'bold', 'cyan'))
            .addSeparator()
            .addLine("  Match: ${match.matchId}    State: ${match.state}")
            .addLine("  Team:  ${team.name}")
            .addLine("  Role:  ${roleLabel(m.role)}")

        if (m.role == 'CLASSIC_LAMBDA') {
            def lambdas = team.members.findAll { it.role == 'CLASSIC_LAMBDA' }
            if (lambdas.any { it.isTrueLambda }) {
                box.addLine(m.isTrueLambda
                    ? "  ${TerminalFormatter.formatText('You are the TRUE Lambda — only you can win with the symbols.', 'bold', 'green')}"
                    : "  ${TerminalFormatter.formatText("You are the DECOY — draw their fire; you can't cash symbols in.", 'bold', 'yellow')}")
            } else {
                box.addLine("  Lambda — awaiting the second Lambda before true/decoy is set.")
            }
        }

        box.addEmptyLine().addLine("  Team roster:")
        team.members.sort { it.username }.each { mem ->
            box.addLine("   - ${mem.username}  -  ${roleLabel(mem.role)}")
        }
        return box.build() + "\r\n"
    }

    private String clusterUsage() {
        def box = new BoxBuilder(70)
            .addCenteredLine(TerminalFormatter.formatText("⬡ CLUSTER MODE", 'bold', 'cyan'))
            .addSeparator()
            .addLine("  cluster create   - start a new 7v7 match (you join TEAM ALPHA)")
            .addLine("  cluster join     - join an open cluster lobby")
            .addLine("  cluster start    - fill empty seats with bots and begin the match")
            .addLine("  cluster status   - your match, team, role, and (Lambda) identity")
            .addLine("  cluster leave    - leave your current match")
        return box.build() + "\r\n"
    }

    private String warn(String msg) { TerminalFormatter.formatText(msg, 'bold', 'yellow') + "\r\n" }
    private String info(String msg) { TerminalFormatter.formatText(msg, 'italic', 'cyan') + "\r\n" }

    // --- package-visible test seams (mirrors telnetServerService.playerSessions assertions) -------

    /** The player's active cluster membership as a plain map, or null. */
    Map clusterStateFor(String username) {
        Map out = null
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            if (m) out = [matchId: m.team.match.matchId, team: m.team.name, role: m.role,
                          isTrueLambda: m.isTrueLambda, state: m.team.match.state]
        }
        return out
    }

    /** Match-level summary for assertions: state, per-team size, per-team true-Lambda count, bot count. */
    Map matchSummaryFor(String username) {
        Map out = null
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            if (m) {
                def match = m.team.match
                out = [state: match.state,
                       teamSizes: match.teams.collectEntries { [(it.name): it.members.size()] },
                       trueLambdasPerTeam: match.teams.collectEntries { [(it.name): it.members.count { it.isTrueLambda }] },
                       bots: match.teams.sum { it.members.count { it.isBot } } as int]
            }
        }
        return out
    }

    /** Count of TRUE Lambdas on the player's team — must be exactly 1 once two Lambdas exist. */
    int trueLambdaCountOnTeamOf(String username) {
        int n = 0
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            if (m) n = (m.team.members.count { it.isTrueLambda }) as int
        }
        return n
    }

    /** A member username on the OPPOSING team (firewall enemy-view tests), or null. */
    String anEnemyMemberUsername(String username) {
        String out = null
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            def enemyTeam = m?.team?.match?.teams?.find { it.id != m.team.id }
            out = enemyTeam?.members?.collect { it.username }?.sort()?.find { true }
        }
        return out
    }

    /** The Lambda usernames on the player's team (the true + decoy), for firewall tests. */
    List<String> lambdaUsernamesOnTeamOf(String username) {
        List<String> out = []
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            if (m) out = m.team.members.findAll { it.role == 'CLASSIC_LAMBDA' }.collect { it.username }.sort()
        }
        return out
    }
}
