package txprocessor;

/**
 * Any message implementing this should return a String composed of all data fields
 * that should be signed/verified (= for which integrity will be provided)
 */

public interface ISignablePayload {
    String getSignablePayload();
}
