import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.*;

public class Crypto {
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private static final String CRYPT_ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH = 32; // in bytes = 256bit
    private static final int IV_LENGTH = 12;
    private static final String PKCS_12 = "pkcs12";
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static String DEFAULT_KEYSTORE_PATH = "keystore.jks";
    private char[] _keyStorePass;
    private String _pathToKeyStore;
    private HyperZMQ _hyperZMQ;

    // public static final Builder Builder = new Builder();

    private Map<String, SecretKey> _keys = new HashMap<>();

/*
    private Crypto() {
    }

    public Crypto(Builder builder) {
        this.keyStorePass = builder.keyStorePass;
        this.pathToKeyStore = builder.pathToKeyStore;
    }

    private static class Builder {
        private static String DEFAULT_KEYSTORE_PATH = "keystore.jks";
        private char[] keyStorePass = {Character.MIN_VALUE};
        private String pathToKeyStore = DEFAULT_KEYSTORE_PATH;

        public Builder setKeyStorePass(char[] keyStorePass) {
            this.keyStorePass = keyStorePass;
            return this;
        }

        public Builder setpathToKeyStore(String pathToKeyStore) {
            this.pathToKeyStore = pathToKeyStore;
            return this;
        }

        public Crypto build() {
            return new Crypto(this);
        }
    }
*/

    /**
     * Create a instance which loads the KeyStore from the given path (which should include <filename>.jks.
     *
     * @param keystorePath path the keystore
     * @param password     password of the keystore
     * @param createNew    whether to create a new keystore
     */
    public Crypto(HyperZMQ hyperZMQ, String keystorePath, char[] password, boolean createNew) {
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
    public Crypto(HyperZMQ hyperZMQ, char[] password, boolean createNew) {
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

    /**
     * Decode the given String in Base64, then decrypt the result using AES256 with the groups keys.
     *
     * @param groupName  groupName
     * @param cipherText cipherText, which is a Base64 encoded String
     * @return clearText or null upon error
     */
    public String decrypt(String groupName, String cipherText) {
        return decrypt(groupName, cipherText.getBytes(UTF8));
    }

    /**
     * Decode the given String in Base64, then decrypt the result using AES256 with the groups keys.
     *
     * @param groupName  groupName
     * @param cipherText cipherText, the RAW payload
     * @return clearText or null upon error
     */
    public String decrypt(String groupName, byte[] cipherText) {
        if (!_keys.containsKey(groupName)) {
            throw new IllegalArgumentException("Unknown Group Name: " + groupName);
        }

        try {
            return new String(decrypt(_keys.get(groupName), cipherText), UTF8);
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Encrypts the clearText using AES256 with the groups key, then encodes the result as Base64
     *
     * @param groupName groupName
     * @param clearText clearText
     * @return encrypted text as Base64 String or null upon error
     */
    public String encrypt(String groupName, String clearText) {
        return encrypt(groupName, clearText.getBytes(UTF8));
    }

    /**
     * Encrypts the clearText using AES256 with the groups key, then encodes the result as Base64
     *
     * @param groupName groupName
     * @param clearText clearText
     * @return encrypted text as Base64 String or null upon error
     */
    public String encrypt(String groupName, byte[] clearText) {
        if (!_keys.containsKey(groupName)) {
            throw new IllegalArgumentException("Unknown Group Name: " + groupName);
        }
        try {
            return new String(encrypt(_keys.get(groupName), clearText), UTF8);
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<String> getGroupNames() {
        return new ArrayList<>(_keys.keySet());
    }

    public String getKeyForGroup(String groupName) {
        if (_keys.containsKey(groupName)) {
            return new String(Base64.getEncoder().encode(_keys.get(groupName).getEncoded()), UTF8);
        }
        return null;
        //return _keys.get(groupName);
    }

    public void removeGroup(String groupName) {
        _keys.remove(groupName);
    }

    public void addGroup(String groupName, String key) {
        byte[] keyBytes = Base64.getDecoder().decode(key);
        if (keyBytes.length != KEY_LENGTH) {
            throw new IllegalArgumentException("Key size is invalid! Expected 32, got " + keyBytes.length);
        }
        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
        _keys.put(groupName, secretKey);
        saveKeyStore();
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

    public void createGroup(String name) throws GeneralSecurityException, IOException {
        if (_keys.containsKey(name)) {
            throw new IllegalArgumentException("Name already in use");
        }
        _keys.put(name, generateSecretKey());
        saveKeyStore();
    }

    private byte[] encrypt(SecretKey secretKey, GCMParameterSpec iv, byte[] plainText)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        Cipher cipher = Cipher.getInstance(CRYPT_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
        return cipher.doFinal(plainText);
    }

    private byte[] decrypt(SecretKey secretKey, GCMParameterSpec iv, byte[] cipherText)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        Cipher cipher = Cipher.getInstance(CRYPT_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);
        return cipher.doFinal(cipherText);
    }

    private byte[] encrypt(SecretKey secretKey, byte[] plaintext)
            throws NoSuchPaddingException, BadPaddingException, InvalidKeyException, NoSuchAlgorithmException,
            IllegalBlockSizeException, InvalidAlgorithmParameterException {
        final byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec params = new GCMParameterSpec(128, iv, 0, 12);
        byte[] cipherText = encrypt(secretKey, params, plaintext);
        byte[] combined = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
        return combined;
    }

    private byte[] decrypt(SecretKey secretKey, byte[] cipherText)
            throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException {
        //byte[] iv = Arrays.copyOfRange(cipherText, 0, IV_LENGTH);
        GCMParameterSpec params = new GCMParameterSpec(128, cipherText, 0, 12);
        byte[] cipher = Arrays.copyOfRange(cipherText, IV_LENGTH, cipherText.length);
        return decrypt(secretKey, params, cipher);
    }

    private SecretKey generateSecretKey()
            throws GeneralSecurityException, IOException {
        final byte[] raw = new byte[KEY_LENGTH];
        new SecureRandom().nextBytes(raw);
        // Even if we just generated the key, always read it back to ensure we
        // can read it successfully.
        //return byteArrayToHexChars(key.getEncoded());
        return new SecretKeySpec(raw, "AES");
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
