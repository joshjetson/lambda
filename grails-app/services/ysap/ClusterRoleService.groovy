package ysap

/**
 * Cluster Mode information + abilities: the enemy-view identity firewall (here) and, in later pieces,
 * the role-keyed team verbs (scan all / spam / lock / deploy bot / transfer). Bounded to "what an
 * observer is allowed to know / do" — match truth lives in ClusterMatchService.
 *
 * THE FIREWALL (load-bearing invariant): to an enemy, the two Lambdas are indistinguishable — same
 * role-class, no true/decoy bit. Only a teammate sees which of their own Lambdas is the true one.
 * Every enemy-facing readout of a member MUST go through firewallViewOf so the bit can never leak.
 */
class ClusterRoleService {

    def clusterMatchService

    /**
     * What `viewerUsername` is allowed to see about `targetUsername`. Role-class is always visible
     * (Circuit's tracking reveals it); the Lambda true/decoy bit is visible ONLY to a teammate.
     * For an enemy, both Lambdas return the identical view (lambda: 'HIDDEN') — they cannot be told
     * apart by any direct channel. Returns null if either entity is not in an active match.
     */
    Map firewallViewOf(String viewerUsername, String targetUsername) {
        def viewer = clusterMatchService.clusterStateFor(viewerUsername)
        def target = clusterMatchService.clusterStateFor(targetUsername)
        if (!viewer || !target) return null

        boolean sameTeam = (viewer.matchId == target.matchId) && (viewer.team == target.team)
        def view = [role: ClusterMatchService.roleLabel(target.role)]   // role-class: always visible
        if (target.role == 'CLASSIC_LAMBDA') {
            // Identity leaks to teammates only; enemies see a generic, indistinguishable Λ.
            view.lambda = sameTeam ? (target.isTrueLambda ? 'TRUE' : 'DECOY') : 'HIDDEN'
        }
        return view
    }
}
