package client;

import com.google.protobuf.ByteString;
import sawtooth.sdk.processor.Context;
import sawtooth.sdk.processor.Utils;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Collect all Sawtooth related helper methods here,
 * instead of confusing the sawtooth or bitoinj packages
 */
public class SawtoothUtils {

    //private static final String HEX_CHARACTERS = "0123456789ABCDEF";
    private static final String HEX_CHARACTERS = "0123456789abcdef";

    public static String hash(String toHash) {
        return Utils.hash512(toHash.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Build a Sawtooth address from the namespace and a hashable
     *
     * @param namespace namespace of the transaction family the address is for
     * @param toHash    hashable object which will make up the address
     * @return the address build from the namespace and the last 64 characters of the hash value
     */
    public static String namespaceHashAddress(String namespace, String toHash) {
        print("Hashing: " + toHash);
        String hash = hash(toHash);
        return namespace + hash.substring(hash.length() - 64);
    }

    public static byte[] hexDecode(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public static String hexEncode(byte[] data) {
        final StringBuilder hex = new StringBuilder(2 * data.length);
        for (final byte b : data) {
            hex.append(HEX_CHARACTERS.charAt((b & 0xF0) >> 4)).append(HEX_CHARACTERS.charAt((b & 0x0F)));
        }
        return hex.toString();
    }

    private static void print(String message) {
        System.out.println("[SawtoothUtils] " + message);
    }

}
