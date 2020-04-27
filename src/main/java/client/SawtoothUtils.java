package client;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.protobuf.ByteString;
import sawtooth.sdk.processor.Context;
import sawtooth.sdk.processor.Utils;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.signing.*;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
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
    private static boolean doPrint = false;

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

    public static String sign(String message, String privateKey) {
        PrivateKey privateKey1 = new Secp256k1PrivateKey(hexDecode(privateKey));
        return sign(message, privateKey1);
    }

    public static String sign(String message, PrivateKey privateKey) {
        System.out.println("Signing " + message);
        return new Secp256k1Context().sign(message.getBytes(), privateKey);
    }

    public static boolean verify(String message, String signature, String publicKey) {
        System.out.println("verifying " + message + " with signature=" + signature + ", pub=" + publicKey);
        PublicKey publicKey1 = new Secp256k1PublicKey(hexDecode(publicKey));
        System.out.println("Publickey: " + publicKey1.hex());
        return verify(message, signature, publicKey1);
    }

    public static boolean verify(String message, String signature, PublicKey publicKey) {
        return new Secp256k1Context().verify(signature, message.getBytes(), publicKey);
    }

    /**
     * Encapsulate Gson object creation from Json
     *
     * @param message string to deserialize
     * @return Message object or null
     */
    public static <T> T deserializeMessage(String message, Class<T> classOfT) {
        try {
            return new Gson().fromJson(message, classOfT);
        } catch (JsonSyntaxException e) {
            //e.printStackTrace();
            System.out.println("Cannot deserialize message: " + message + " to " + classOfT.toString());
            return null;
        }
    }

    public static String generateNonce(int characterCount) {
        int leftLimit = 48; // '0'
        int rightLimit = 122; // 'z'

        return new SecureRandom().ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(characterCount)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private static void print(String message) {
        if (doPrint)
            System.out.println("[SawtoothUtils] " + message);
    }

}
