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

public class Crypto {

    private static final int KEY_LENGTH = 32; // in bytes = 256bit
    private static final int GCM_TAG_SIZE_BITS = 128;
    private static final int GCM_IV_SIZE_BYTES = 12;

    private char[] keyStorePass;
    private String pathToKeyStore;
    private HyperZMQ hyperZMQ;
    private Map<String, SecretKey> groupKeys = new HashMap<>();
    private List<Keypair> curveKeys = new ArrayList<>();
    private SecretKey dataEncryptionKey;
    private Secp256k1Context context;
    private PrivateKey privateKey;
    private Signer signer;
    private Storage storage;

    /**
     * Create a instance which loads the KeyStore and DataFile from the given path (which should include <filename>.jks. and <filename>.dat)
     *
     * @param keystorePath path the keystore
     * @param password     password of the keystore
     * @param createNew    whether to create a new keystore
     */
    Crypto(HyperZMQ hyperZMQ, String keystorePath, char[] password, String dataFilePath, boolean createNew) {
        this.keyStorePass = password;
        this.pathToKeyStore = keystorePath;
        this.hyperZMQ = hyperZMQ;
        this.storage = new Storage(pathToKeyStore, keyStorePass, dataFilePath);
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
        context = new Secp256k1Context();
        privateKey = context.newRandomPrivateKey();
        signer = new Signer(context, privateKey);

        dataEncryptionKey = generateSecretKey();
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

    public void setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
        this.signer = new Signer(new Secp256k1Context(), privateKey);
    }

    String getSawtoothPublicKey() {
        return signer.getPublicKey().hex();
    }

    String encrypt(String plainText, String group) throws GeneralSecurityException, IllegalStateException {
        SecretKey key = groupKeys.get(group);
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
    public static String encrypt(String plainText, SecretKey key) throws GeneralSecurityException {
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
        SecretKey key = groupKeys.get(group);
        if (key == null) throw new IllegalStateException("No key found for group=" + group);
        return decrypt(encryptedText, key);
    }

    public static String decrypt(String encryptedText, SecretKey key) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        byte[] ivAndCTWithTag = Base64.getDecoder().decode(encryptedText);

        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_SIZE_BITS, ivAndCTWithTag, 0, GCM_IV_SIZE_BYTES);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        byte[] plaintext = cipher.doFinal(ivAndCTWithTag, GCM_IV_SIZE_BYTES, ivAndCTWithTag.length - GCM_IV_SIZE_BYTES);

        return new String(plaintext, UTF_8);
    }

    List<String> getGroupNames() {
        return new ArrayList<>(groupKeys.keySet());
    }

    boolean hasKeyForGroup(String groupName) {
        return groupKeys.containsKey(groupName);
    }

    String getKeyForGroup(String groupName) {
        if (groupKeys.containsKey(groupName)) {
            return new String(Base64.getEncoder().encode(groupKeys.get(groupName).getEncoded()), UTF_8);
        }
        return null;
    }

    void createGroup(String name) throws IllegalArgumentException {
        if (groupKeys.containsKey(name)) {
            throw new IllegalArgumentException("Name already in use");
        }
        groupKeys.put(name, generateSecretKey());
        save();
        //log.info("created group " + name + " with key (b64) " + Base64.getEncoder().encodeToString(_keys.get(name).getEncoded()));
    }

    void addGroup(String groupName, String key) {
        byte[] keyBytes = Base64.getDecoder().decode(key);
        if (keyBytes.length != KEY_LENGTH) {
            throw new IllegalArgumentException("Key size is invalid! Expected 32, got " + keyBytes.length);
        }
        groupKeys.put(groupName, new SecretKeySpec(Base64.getDecoder().decode(key), "AES"));
        save();
        //log.info("Added group " + groupName + " with key (b64) " + Base64.getEncoder().encodeToString(_keys.get(groupName).getEncoded()));
    }

    void removeGroup(String groupName) {
        groupKeys.remove(groupName);
    }

    private void save() {
        // Prepare the other 'non-key' data
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put(SAWTOOTHER_SIGNER_KEY, privateKey.hex());
        // Since the curve keys have the alias built in, the maps key is not needed
        for (int i = 0; i < curveKeys.size(); i++) {
            dataMap.put(String.valueOf(i), curveKeys.get(i).toString());
        }

        //
        groupKeys.put(DATA_ENCRYPTION_KEY_ALIAS, dataEncryptionKey);
        storage.saveData(new Data(groupKeys, dataMap));
    }

    private void load() {
        Data data = storage.loadData();
        dataEncryptionKey = data.keys.remove(DATA_ENCRYPTION_KEY_ALIAS);

        groupKeys = data.keys;
        context = new Secp256k1Context();
        PrivateKey key = new Secp256k1PrivateKey(Utils.HEX.decode(data.getSigningKeyHex()));
        signer = new Signer(context, key);
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
        curveKeys = tmp;
    }

    Signer getSigner() {
        return signer;
    }

    public void addKeypair(Keypair kp) {
        curveKeys.add(kp);
        save();
    }

    public Keypair getKeypair(String alias) {
        return curveKeys.stream().
                filter(key -> alias.equals(key.alias)).
                findAny().
                orElse(null);
    }

    public boolean removeKeypair(String alias) {
        boolean b = curveKeys.removeIf(kp -> alias.equals(kp.alias));
        save();
        return b;
    }
}
