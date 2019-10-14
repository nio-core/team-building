import org.json.JSONObject;
import sawtooth.sdk.signing.PrivateKey;
import sawtooth.sdk.signing.Secp256k1Context;
import sawtooth.sdk.signing.Signer;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.KeyStore.SecretKeyEntry;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Crypto {

    private static final int KEY_LENGTH = 32; // in bytes = 256bit
    private static final int GCM_TAG_SIZE_BITS = 128;
    private static final int GCM_IV_SIZE_BYTES = 12;
    private static final String PKCS_12 = "pkcs12";
    private static final String DEFAULT_KEYSTORE_PATH = "keystore.jks";
    public static final String DEFAULT_DATA_PATH = "data.dat";
    private static final String DATA_ENCRYPTION_KEY_ALIAS = "data_encryption_key";
    public static final String SAWTOOTHER_SIGNER_KEY = "sawtooth_signer_key";

    private Logger log = Logger.getLogger(getClass().getName());
    private char[] _keyStorePass;
    private String _pathToKeyStore;
    private HyperZMQ _hyperZMQ;
    private Map<String, SecretKey> _keys = new HashMap<>();
    private SecretKey _dataEncryptionKey;
    private Secp256k1Context _context;
    private PrivateKey _privateKey;
    private Signer _signer;

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
            createNewCryptoMaterial();
        } else {
            // load existing
            loadData(keystorePath, password);
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

    /**
     * If no keystore is loaded, create a new keystore, new signing key and new data encryption key
     */
    private void createNewCryptoMaterial() {
        try {
            KeyStore ks = KeyStore.getInstance(PKCS_12);
            ks.load(null, _keyStorePass); // stream = null -> make a new one

            // Generate a new signer for the Blockchain if nothing is loaded
            // THIS EQUALS A NEW IDENTITY ON THE BLOCKHAIN !!!
            _context = new Secp256k1Context();
            _privateKey = _context.newRandomPrivateKey();
            _signer = new Signer(_context, _privateKey);

            _dataEncryptionKey = generateSecretKey();
            try (FileOutputStream fos = new FileOutputStream(_pathToKeyStore)) {
                ks.store(fos, _keyStorePass);
            }
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
        }
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

    String encrypt(String plainText, String group) throws GeneralSecurityException, IllegalStateException {
        SecretKey key = _keys.get(group);
        if (key == null) throw new IllegalStateException("No key found for group=" + group);
        return encrypt(plainText, key);
    }

    private String encrypt(String plainText, SecretKey key) throws GeneralSecurityException {
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

    String decrypt(String encryptedText, String group) throws GeneralSecurityException, IllegalStateException {
        SecretKey key = _keys.get(group);
        if (key == null) throw new IllegalStateException("No key found for group=" + group);
        return decrypt(encryptedText, key);
    }

    private String decrypt(String encryptedText, SecretKey key) throws GeneralSecurityException {
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
        saveData();
        //log.info("created group " + name + " with key (b64) " + Base64.getEncoder().encodeToString(_keys.get(name).getEncoded()));
    }

    void addGroup(String groupName, String key) {
        byte[] keyBytes = Base64.getDecoder().decode(key);
        if (keyBytes.length != KEY_LENGTH) {
            throw new IllegalArgumentException("Key size is invalid! Expected 32, got " + keyBytes.length);
        }
        _keys.put(groupName, new SecretKeySpec(Base64.getDecoder().decode(key), "AES"));
        saveData();
        //log.info("Added group " + groupName + " with key (b64) " + Base64.getEncoder().encodeToString(_keys.get(groupName).getEncoded()));
    }

    void removeGroup(String groupName) {
        _keys.remove(groupName);
    }

    private void loadData(char[] password) {
        loadData(DEFAULT_KEYSTORE_PATH, password);
    }

    private void loadData(String keystorePath, char[] password) {
        try {
            KeyStore ks = KeyStore.getInstance(PKCS_12);
            ks.load(new FileInputStream(keystorePath), password);
            char[] pw = {'x'};
            // TODO add even more passwords?
            KeyStore.PasswordProtection protParam = new KeyStore.PasswordProtection(pw);

            List<String> groupNames = Collections.list(ks.aliases());
            for (String group : groupNames) {
                try {
                    SecretKey key = (SecretKey) ks.getKey(group, pw);
                    _keys.put(group, key);
                } catch (UnrecoverableEntryException e) {
                    e.printStackTrace();
                }
            }

            Key key = ks.getKey(DATA_ENCRYPTION_KEY_ALIAS, pw);
            if (key != null) {
                _dataEncryptionKey = (SecretKey) key;
                // Load the data file if present
            } else {
                log.info("No data encryption key found - data cannot be loaded if there is any.");
            }
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException | UnrecoverableKeyException e) {
            e.printStackTrace();
            throw new InternalError("Loading KeyStore failed with error: " + e.getLocalizedMessage());
        }
    }

    private void saveData() throws InternalError {
        try {
            KeyStore ks = KeyStore.getInstance(PKCS_12);
            ks.load(null, _keyStorePass);
            // TODO add even more passwords?
            KeyStore.PasswordProtection protParam = new KeyStore.PasswordProtection(new char[]{'x'});
            _keys.forEach((groupName, key) -> {
                SecretKeyEntry entry = new SecretKeyEntry(key);
                try {
                    ks.setEntry(groupName, entry, protParam);
                } catch (KeyStoreException e) {
                    e.printStackTrace();
                }
            });

            ks.setEntry(DATA_ENCRYPTION_KEY_ALIAS, new SecretKeyEntry(_dataEncryptionKey), protParam);

            FileOutputStream fos = new FileOutputStream(DEFAULT_KEYSTORE_PATH);
            ks.store(fos, _keyStorePass);
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            //e.printStackTrace();
            throw new InternalError("Saving the keystore failed with exception: " + e.getLocalizedMessage());
        }

        // Save all other data to the data file
        JSONObject data = new JSONObject();
        data.put(SAWTOOTHER_SIGNER_KEY, _signer.getPublicKey().hex());
        // TODO put other data in sub object

        try {
            String toWrite = encrypt(data.toString(), _dataEncryptionKey);
        } catch (GeneralSecurityException e) {
            //e.printStackTrace();
            throw new InternalError("Saving the data failed with exception: " + e.getLocalizedMessage());
        }

    }

    Signer getSigner() {
        return _signer;
    }
}
