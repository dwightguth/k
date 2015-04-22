package org.kframework.utils.errorsystem;

import java.util.List;

/**
 * Thrown to indicate that the K Exception manager has terminated the application due to an error.
 *
 * @author dwightguth
 */
public class KEMException extends RuntimeException {
    public final KException exception;

    KEMException(KException e) {
        super(e.toString(), e.getException());
        this.exception = e;
    }

    @Override
    public String getMessage() {
        return exception.toString();
    }

    public void register(List<KException> kem) {
        synchronized (kem) {
            kem.add(exception);
        }
    }
}
