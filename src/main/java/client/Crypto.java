package client;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.bitcoinj.core.Utils;
import sawtooth.sdk.signing.PrivateKey;
import sawtooth.sdk.signing.Secp256k1Context;
import sawtooth.sdk.signing.Secp256k1PrivateKey;
import sawtooth.sdk.signing.Signer;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.*;

import static client.Storage.*;
import static java.nio.charset.StandardCharsets.UTF_8;

class Crypto {

    private static final int KEY_LENGTH = 32; // in bytes = 256bit
    private static final int GCM_TAG_SIZE_BITS = 128;
    private static final int GCM_IV_SIZE_BYTES = 12;

    private char[] _keyStorePass;
    private String _pathToKeyStore;
    private HyperZMQ _hyperZMQ;
    private Map<String, SecretKey> _groupKeys = new HashMap<>();
    private List<Keypair> _curveKeys = new ArrayList<>();
    private SecretKey _dataEncryptionKey;
    private Secp256k1Context _context;
    private PrivateKey _privateKey;
    private Signer _signer;
    private Storage _storage;

    /**
     * Create a instance which loads the KeyStore and DataFile from the given path (which should include <filename>.jks. and <filename>.dat)
     *
     * @param keystorePath path the keystore
     * @param password     password of the keystore
     * @param createNew    whether to create a new keystore
     */
    Crypto(HyperZMQ hyperZMQ, String keystorePath, char[] password, String dataFilePath, boolean createNew) {
        this._keyStorePass = password;
        this._pathToKeyStore = keystorePath;
        this._hyperZMQ = hyperZMQ;
        this._storage = new Storage(_pathToKeyStore, _keyStorePass, dataFilePath);
        if (createNew) {
            createNewCryptoMaterial();
        } else {
            load();
        }
    }

    /**
     * Create a instance which loads the KeyStore and DataFile from the default path.
     *
     * @param password  password of the keystore
     * @param createNew whether to create a new keystore
     */
    Crypto(HyperZMQ hyperZMQ, char[] password, boolean createNew) {
        this(hyperZMQ, DEFAULT_KEYSTORE_PATH, password, null, createNew);
    }

    /**
     * If no keystore is loaded, create a new keystore, signing key and data encryption key.
     * Saves the data afterwards.
     */
    private void createNewCryptoMaterial() {
        _context = new Secp256k1Context();
        _privateKey = _context.newRandomPrivateKey();
        _signer = new Signer(_context, _privateKey);

        _dataEncryptionKey = generateSecretKey();
        save();
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

    String getSawtoothPublicKey() {
        return _signer.getPublicKey().hex();
    }

    String encrypt(String plainText, String group) throws GeneralSecurityException, IllegalStateException {
        SecretKey key = _groupKeys.get(group);
        if (key == null) throw new IllegalStateException("No key found for group=" + group);
        return encrypt(plainText, key);
    }

    /**
     * Decrypt the data with the given key and returns a BASE64 ENCODED STRING
     *
     * @param plainText
     * @param key       AES-GCM 256bit key
     * @return ciphertext IN BASE64 ENCODING
     * @throws GeneralSecurityException
     */
    static String encrypt(String plainText, SecretKey key) throws GeneralSecurityException {
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
        SecretKey key = _groupKeys.get(group);
        if (key == null) throw new IllegalStateException("No key found for group=" + group);
        return decrypt(encryptedText, key);
    }

    static String decrypt(String encryptedText, SecretKey key) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        byte[] ivAndCTWithTag = Base64.getDecoder().decode(encryptedText);

        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_SIZE_BITS, ivAndCTWithTag, 0, GCM_IV_SIZE_BYTES);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        byte[] plaintext = cipher.doFinal(ivAndCTWithTag, GCM_IV_SIZE_BYTES, ivAndCTWithTag.length - GCM_IV_SIZE_BYTES);

        return new String(plaintext, UTF_8);
    }

    List<String> getGroupNames() {
        return new ArrayList<>(_groupKeys.keySet());
    }

    boolean hasKeyForGroup(String groupName) {
        return _groupKeys.containsKey(groupName);
    }

    String getKeyForGroup(String groupName) {
        if (_groupKeys.containsKey(groupName)) {
            return new String(Base64.getEncoder().encode(_groupKeys.get(groupName).getEncoded()), UTF_8);
        }
        return null;
    }

    void createGroup(String name) throws IllegalArgumentException {
        if (_groupKeys.containsKey(name)) {
            throw new IllegalArgumentException("Name already in use");
        }
        _groupKeys.put(name, generateSecretKey());
        save();
        //log.info("created group " + name + " with key (b64) " + Base64.getEncoder().encodeToString(_keys.get(name).getEncoded()));
    }

    void addGroup(String groupName, String key) {
        byte[] keyBytes = Base64.getDecoder().decode(key);
        if (keyBytes.length != KEY_LENGTH) {
            throw new IllegalArgumentException("Key size is invalid! Expected 32, got " + keyBytes.length);
        }
        _groupKeys.put(groupName, new SecretKeySpec(Base64.getDecoder().decode(key), "AES"));
        save();
        //log.info("Added group " + groupName + " with key (b64) " + Base64.getEncoder().encodeToString(_keys.get(groupName).getEncoded()));
    }

    void removeGroup(String groupName) {
        _groupKeys.remove(groupName);
    }

    private void save() {
        // Prepare the other 'non-key' data
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put(SAWTOOTHER_SIGNER_KEY, _privateKey.hex());
        // Since the curve keys have the alias built in, the maps key is not needed
        for (int i = 0; i < _curveKeys.size(); i++) {
            dataMap.put(String.valueOf(i), _curveKeys.get(i).toString());
        }

        //
        _groupKeys.put(DATA_ENCRYPTION_KEY_ALIAS, _dataEncryptionKey);
        _storage.saveData(new Data(_groupKeys, dataMap));
    }

    private void load() {
        Data data = _storage.loadData();
        _dataEncryptionKey = data.keys.remove(DATA_ENCRYPTION_KEY_ALIAS);

        _groupKeys = data.keys;
        _context = new Secp256k1Context();
        PrivateKey key = new Secp256k1PrivateKey(Utils.HEX.decode(data.getSigningKeyHex()));
        _signer = new Signer(_context, key);
        data.data.remove(SAWTOOTHER_SIGNER_KEY);
        // restore the curve keys, the data map does not contain the data encryption key anymore
        ArrayList<Keypair> tmp = new ArrayList<>();
        data.data.forEach((k, v) -> {
            try {
                tmp.add(new Gson().fromJson(v, Keypair.class));
            } catch (JsonSyntaxException e) {
                // TODO
            }

        });
        _curveKeys = tmp;
    }

    Signer getSigner() {
        return _signer;
    }

    public void addKeypair(Keypair kp) {
        _curveKeys.add(kp);
        save();
    }

    public Keypair getKeypair(String alias) {
        return _curveKeys.stream().
                filter(key -> alias.equals(key.alias)).
                findAny().
                orElse(null);
    }

    public boolean removeKeypair(String alias) {
        boolean b = _curveKeys.removeIf(kp -> alias.equals(kp.alias));
        save();
        return b;
    }
}
