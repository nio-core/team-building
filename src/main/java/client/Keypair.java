package client;

import com.google.gson.Gson;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class Keypair {

    /*
    public static final String CLIENT_PRIVATE = "N4Wt:S%oO6E{=Xk7YiN*nM+VR*Rfn/&pUDFmA5I=";
    public static final String CLIENT_PUBLIC = "YKHb-A)4EHX/uWF/kYPgl2[#0TE5uNM:O5uZt#N%";
    public static final String SERVER_PRIVATE = "gesR9P{KF{h5#VJ-)em5/GK%lR%Hp.p$zgH)B[Q$";
    public static final String SERVER_PUBLIC = "mnoq)mdV7(-y.JkJhbXUi$(hTB))-fUJ?nKp[qDg";
    */
    /**
     * This data structure can be used to hold both client and server keys.
     * In the ZMQ sense 'client' and 'server' where for the client the private key is known (because we generated it)
     * and for the server only the public key is known (got it from somewhere else) which is used to identify the other entity.
     */

    public String privateKey;
    public String publicKey;
    public final String alias;

    public Keypair(@Nonnull String alias, @Nullable String privateKey, @Nonnull String publicKey) {
        this.alias = alias;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
