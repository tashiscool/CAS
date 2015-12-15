package controllers.cas

/**
 * Created by tash on 11/24/15.
 */
trait PrincipalResolver {
/**
 * Resolves a principal from the given credential using an arbitrary strategy.
 *
 * @param credential Source credential.
 *
 * @return Resolved principal, or null if the principal could not be resolved.
 */
def resolve (credential: Credentials): Principal

/**
 * Determines whether this instance supports principal resolution from the given credential. This method SHOULD
 * be called prior to {@link #resolve(org.jasig.cas.authentication.Credential)}.
 *
 * @param credential The credential to check for support.
 *
 * @return True if credential is supported, false otherwise.
 */
def supports (credential: Credentials): Boolean
}
