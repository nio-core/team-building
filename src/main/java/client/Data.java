package client;

import javax.crypto.SecretKey;
import java.util.Map;

class Data {

    Map<String, SecretKey> keys;
    Map<String, String> data;

    Data(Map<String, SecretKey> keys, Map<String, String> data) {
        this.keys = keys;
        this.data = data;
    }

    String getSigningKeyHex() {
       return data.get(Storage.SAWTOOTHER_SIGNER_KEY);
    }
}
