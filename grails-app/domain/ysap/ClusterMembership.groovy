package ysap

/**
 * A seat on a team: which entity holds it, in what role, and (for Lambdas) whether it is the TRUE
 * Lambda or the decoy. References the player by username so LambdaPlayer gains no match-coupling
 * fields. `role` IS the player's existing ethnicity (avatarSilhouette) — identity is reused, not
 * reinvented. The isTrueLambda bit is the hidden truth: known to self/team, never shown to enemies.
 */
class ClusterMembership {

    String username
    String role                 // = the player's avatarSilhouette (the 6 ethnicities = the 6 roles)
    Boolean isTrueLambda = false
    Boolean isBot = false

    static belongsTo = [team: ClusterTeam]

    static constraints = {
        username blank: false, maxSize: 50
        role blank: false, maxSize: 30
    }

    String toString() { "${username} (${role}${isTrueLambda ? ', TRUE Λ' : ''})" }
}
