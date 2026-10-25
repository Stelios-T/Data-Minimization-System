package dataminimization;

/** How one provided datum relates to what the service needs. */
public enum Status {
    /** Named by the service as required. */
    REQUIRED,
    /** Named by the service as optional. */
    OPTIONAL,
    /** Not named by the service, but discloses only data the service needs (e.g. a specialisation). */
    SATISFIES,
    /** Discloses data the service needs, but also data it does not. A narrower datum should be requested instead. */
    SUPERSET,
    /** Discloses nothing the service needs. Must be rejected. */
    EXCESSIVE,
    /** Not a personal data type known to the knowledge graph. Must be rejected. */
    UNKNOWN;

    /** True when the datum is acceptable under the minimization principle. */
    public boolean isMinimal() {
        return this == REQUIRED || this == OPTIONAL || this == SATISFIES;
    }
}
