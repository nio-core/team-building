import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Crypto {
    private Logger log = Logger.getLogger(getClass().getName());
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private static final int KEY_LENGTH = 32; // in bytes = 256bit
    private static final String PKCS_12 = "pkcs12";
    private static String DEFAULT_KEYSTORE_PATH = "keystore.jks";
    private char[] _keyStorePass;
    private String _pathToKeyStore;
    private HyperZMQ _hyperZMQ;

    private static final int KEY_SIZE_BITS = 128;
    private static final int GCM_TAG_SIZE_BITS = 128;
    private static final int GCM_IV_SIZE_BYTES = 12;

    private Map<String, SecretKey> _keys = new HashMap<>();

    /**
     * Create a instance which loads the KeyStore from the given path (which should include <filename>.jks.
     *
     * @param keystorePath path the keystore
     * @param password     password of the keystore
     * @param createNew    whether to create a new keystore
     */
    Crypto(HyperZMQ hyperZMQ, String keystorePath, char[] password, boolean createNew) {
        this._keyStorePass = password;
        this._pathToKeyStore = keystorePath;
        this._hyperZMQ = hyperZMQ;
        if (createNew) {
            createNewKeyStore();
        } else {
            // load existing
            loadKeyStore(keystorePath, password);
        }
    }

    /**
     * Create a instance which loads the KeyStore from the default path.
     *
     * @param password  password of the keystore
     * @param createNew whether to create a new keystore
     */
    Crypto(HyperZMQ hyperZMQ, char[] password, boolean createNew) {
        this(hyperZMQ, DEFAULT_KEYSTORE_PATH, password, createNew);
    }

    private void createNewKeyStore() {
        try {
            KeyStore ks = KeyStore.getInstance(PKCS_12);
            ks.load(null, _keyStorePass); // stream = null -> make a new one

            try (FileOutputStream fos = new FileOutputStream(_pathToKeyStore)) {
                ks.store(fos, _keyStorePass);
            }
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
        }
    }

    /* *//**
     * Decode the given String in Base64, then decrypt the result using AES256 with the groups keys.
     *
     * @param groupName  groupName
     * @param cipherText cipherText, which is a Base64 encoded String
     * @return clearText or null upon error
     *//*
    public byte[] decrypt(String groupName, String cipherText) {
        log.info("[DECRYPT] group " + groupName + " ciphertext: " + cipherText);

        SecretKey key = _keys.get(groupName);
        log.info("DECRYPT KEY: " + Base64.getEncoder().encodeToString(key.getEncoded()));
        byte[] cipherTextBytes = cipherText.getBytes(UTF8);
        //Base64.getDecoder().decode(cipherText);
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, cipherTextBytes, 0, 12);
        byte[] cipherBytes = Arrays.copyOfRange(cipherTextBytes, IV_LENGTH, cipherTextBytes.length);

        try {
            Cipher cipher = Cipher.getInstance(CRYPT_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec);
            cipher.update(cipherBytes);
            byte[] decryptedBytes = cipher.doFinal(gcmParameterSpec.getIV());
            return decryptedBytes;
            //return new String(decryptedBytes, UTF8);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }
        return null;
    }

    */

    /**
     * Encrypts the clearText using AES256 with the groups key, then encodes the result as Base64
     *
     * @param
     * @param
     * @return encrypted text as Base64 String or null upon error
     *//*
    public String encrypt(String groupName, String clearText) {
        //return encrypt(groupName, clearText.getBytes(UTF8));
        byte[] clearTextBytes = clearText.getBytes(UTF8);
        //Base64.getDecoder().decode(clearText);
        SecretKey key = _keys.get(groupName);
        log.info("ENCRYPT KEY: " + Base64.getEncoder().encodeToString(key.getEncoded()));

        final byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, iv, 0, 12);

        try {
            Cipher cipher = Cipher.getInstance(CRYPT_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec);
            byte[] cipherText = cipher.doFinal(clearTextBytes);

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (IllegalBlockSizeException | BadPaddingException | NoSuchAlgorithmException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchPaddingException e) {
            e.printStackTrace();
        }

        return null;
    }

*/
    SecretKey generateKey() throws NoSuchAlgorithmException {
        SecureRandom random = SecureRandom.getInstanceStrong();
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(KEY_SIZE_BITS, random);
        return keyGen.generateKey();
    }

    String encrypt(String plainText, String group) throws GeneralSecurityException, IllegalStateException {
        SecretKey key = _keys.get(group);
        if (key == null) throw new IllegalStateException("No key found for group=" + group);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        byte[] iv = generateRandomIV();

        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_SIZE_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        byte[] bytePassword = plainText.getBytes(UTF_8);
        byte[] ivCTAndTag = new byte[GCM_IV_SIZE_BYTES + cipher.getOutputSize(bytePassword.length)];
        System.arraycopy(iv, 0, ivCTAndTag, 0, GCM_IV_SIZE_BYTES);

        cipher.doFinal(bytePassword, 0, bytePassword.length, ivCTAndTag, GCM_IV_SIZE_BYTES);

        return Base64.getEncoder().encodeToString(ivCTAndTag);
    }

    private static byte[] generateRandomIV() {
        byte[] iv = new byte[GCM_IV_SIZE_BYTES];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        return iv;
    }

    private SecretKey generateSecretKey() {
        final byte[] raw = new byte[KEY_LENGTH];
        new SecureRandom().nextBytes(raw);
        return new SecretKeySpec(raw, "AES");
    }

    String decrypt(String encryptedText, String group) throws GeneralSecurityException, IllegalStateException {
        SecretKey key = _keys.get(group);
        if (key == null) throw new IllegalStateException("No key found for group=" + group);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        byte[] ivAndCTWithTag = Base64.getDecoder().decode(encryptedText);

        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_SIZE_BITS, ivAndCTWithTag, 0, GCM_IV_SIZE_BYTES);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        byte[] plaintext = cipher.doFinal(ivAndCTWithTag, GCM_IV_SIZE_BYTES, ivAndCTWithTag.length - GCM_IV_SIZE_BYTES);

        return new String(plaintext, UTF_8);
    }

    List<String> getGroupNames() {
        return new ArrayList<>(_keys.keySet());
    }

    boolean hasKeyForGroup(String groupName) {
        return _keys.containsKey(groupName);
    }

    String getKeyForGroup(String groupName) {
        if (_keys.containsKey(groupName)) {
            return new String(Base64.getEncoder().encode(_keys.get(groupName).getEncoded()), UTF_8);
        }
        return null;
    }

    void createGroup(String name) throws IllegalArgumentException {
        if (_keys.containsKey(name)) {
            throw new IllegalArgumentException("Name already in use");
        }
        _keys.put(name, generateSecretKey());
        saveKeyStore();
        //log.info("created group " + name + " with key (b64) " + Base64.getEncoder().encodeToString(_keys.get(name).getEncoded()));
    }

    void addGroup(String groupName, String key) {
        byte[] keyBytes = Base64.getDecoder().decode(key);
        if (keyBytes.length != KEY_LENGTH) {
            throw new IllegalArgumentException("Key size is invalid! Expected 32, got " + keyBytes.length);
        }
        _keys.put(groupName, new SecretKeySpec(Base64.getDecoder().decode(key), "AES"));
        saveKeyStore();
        //log.info("Added group " + groupName + " with key (b64) " + Base64.getEncoder().encodeToString(_keys.get(groupName).getEncoded()));
    }

    void removeGroup(String groupName) {
        _keys.remove(groupName);
    }

    private void loadKeyStore(char[] password) {
        loadKeyStore(DEFAULT_KEYSTORE_PATH, password);
    }

    private void loadKeyStore(String keystorePath, char[] password) {
        try {
            KeyStore ks = KeyStore.getInstance(PKCS_12);
            ks.load(new FileInputStream(keystorePath), password);
            char[] pw = {'x'};
            // TODO add even more passwords?
            KeyStore.PasswordProtection protParam = new KeyStore.PasswordProtection(pw);

            List<String> groupNames = Collections.list(ks.aliases());
            for (String s : groupNames) {
                try {
                    SecretKey key = (SecretKey) ks.getKey(s, pw);
                    // The groupName is the key alias
                    _keys.put(s, key);
                } catch (UnrecoverableEntryException e) {
                    e.printStackTrace();
                }
            }
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
            throw new InternalError("Loading KeyStore failed with error: " + e.getLocalizedMessage());
        }
    }

    private void saveKeyStore() throws InternalError {
        try {
            KeyStore ks = KeyStore.getInstance(PKCS_12);
            ks.load(null, _keyStorePass);
            char[] pw = {'x'};
            _keys.forEach((k, v) -> {
                KeyStore.SecretKeyEntry entry = new KeyStore.SecretKeyEntry(v);
                // TODO add even more passwords?
                KeyStore.PasswordProtection protParam = new KeyStore.PasswordProtection(pw);
                try {
                    // The groupName is the key alias
                    ks.setEntry(k, entry, protParam);
                } catch (KeyStoreException e) {
                    e.printStackTrace();
                }
            });

            FileOutputStream fos = new FileOutputStream(DEFAULT_KEYSTORE_PATH);
            ks.store(fos, _keyStorePass);
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
            throw new InternalError("Saving the keystore failed with exception: " + e.getLocalizedMessage());
        }
    }

    public byte[] hexStringToByteArray(String hex) {
        // Adding one byte to get the right conversion
        // Values starting with "0" can be converted
        byte[] bArray = new BigInteger("10" + hex, 16).toByteArray();

        // Copy all the REAL bytes, not the "first"
        byte[] ret = new byte[bArray.length - 1];
        for (int i = 0; i < ret.length; i++)
            ret[i] = bArray[i + 1];
        return ret;
    }

    public char[] byteArrayToHexChars(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return hexChars;
    }

    public void clearCharArray(char[] ar) {
        for (char c : ar) {
            c = '0';
        }
    }
}
