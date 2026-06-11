package ysap

/**
 * One side of a Cluster match (ALPHA or BETA). Holds its roster. Data + constraints only.
 */
class ClusterTeam {

    String name      // ALPHA or BETA

    static belongsTo = [match: ClusterMatch]
    static hasMany = [members: ClusterMembership]

    static constraints = {
        name inList: ['ALPHA', 'BETA']
    }

    String toString() { "${name}" }
}
