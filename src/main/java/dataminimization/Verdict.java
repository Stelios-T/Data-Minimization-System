package dataminimization;

/** Overall outcome of a minimization check. */
public enum Verdict {
    /** All required data are covered and nothing excessive is provided. */
    COMPLIANT,
    /** All required data are covered but some provided data exceed what the service needs. */
    EXCESSIVE,
    /** Nothing excessive is provided but some required data are missing. */
    INCOMPLETE,
    /** Some required data are missing and some provided data are excessive. */
    INCOMPLETE_AND_EXCESSIVE;

    public static Verdict of(boolean complete, boolean minimal) {
        if (complete) {
            return minimal ? COMPLIANT : EXCESSIVE;
        }
        return minimal ? INCOMPLETE : INCOMPLETE_AND_EXCESSIVE;
    }
}
