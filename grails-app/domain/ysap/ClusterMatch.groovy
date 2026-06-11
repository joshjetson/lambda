package ysap

/**
 * A Cluster Mode match: two teams racing to collect the 4 elemental symbols and defeat the Logic
 * Daemon with their TRUE Lambda. Data + constraints only — lifecycle lives in ClusterMatchService.
 */
class ClusterMatch {

    String matchId
    String state = 'LOBBY'      // LOBBY (filling) → ACTIVE (playing) → ENDED
    Integer totalMaps = 1       // how many maps/rounds the match runs (creator's choice; 1-10)
    String winner               // winning team name once ENDED (ALPHA/BETA)
    Date createdDate = new Date()

    static hasMany = [teams: ClusterTeam]

    static constraints = {
        matchId blank: false, unique: true, maxSize: 50
        state inList: ['LOBBY', 'ACTIVE', 'ENDED']
        totalMaps min: 1, max: 10
        winner nullable: true, inList: ['ALPHA', 'BETA']
    }

    String toString() { "${matchId} (${state})" }
}
