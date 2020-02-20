package client;

import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.crypto.SecretKey;
import java.io.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class Storage {

    private static final String PKCS_12 = "pkcs12";
    static final String DEFAULT_KEYSTORE_PATH = "keystore.jks";
    static final String DEFAULT_DATA_PATH = "data.dat";
    static final String DATA_ENCRYPTION_KEY_ALIAS = "data_encryption_key";
    static final String SAWTOOTHER_SIGNER_KEY = "sawtooth_signer_key";
    private String _keystorePath;
    private char[] _keystorePassword;
    private String _datafilePath;
    private SecretKey _dataEncryptionKey;

    Storage(String _keystorePath, @Nonnull char[] _keystorePassword, String _datafilePath) {
        this._keystorePath = _keystorePath != null ? _keystorePath : DEFAULT_KEYSTORE_PATH;
        this._keystorePassword = _keystorePassword;
        this._datafilePath = _datafilePath != null ? _datafilePath : DEFAULT_DATA_PATH;
        //System.out.println("Storage with keystore path=" + _keystorePath + " and data file path=" + _datafilePath);
    }

    void saveData(Data data) {
        _dataEncryptionKey = data.keys.get(DATA_ENCRYPTION_KEY_ALIAS);
        saveKeystore(data.keys);
        saveDataFile(data.data);
    }

    Data loadData() {
        Map<String, SecretKey> keys = loadKeystore(_keystorePath, _keystorePassword);
        return new Data(keys, loadDataFile());
    }

    private List<String> readFromFile() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(_datafilePath));
        List<String> lines = reader.lines().collect(Collectors.toList());
        reader.close();
        return lines;
    }

    private void writeToFile(List<String> lines) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(_datafilePath));
        for (String line : lines) {
            writer.write(line);
        }
        writer.close();
    }

    private void saveDataFile(Map<String, String> data) {
        if (_dataEncryptionKey == null) {
            throw new InternalError("Data encryption key is null, cannot proceed.");
        }
        JSONObject jsonObject = new JSONObject(data);
        //System.out.println("String to write: " + jsonObject.toString());
        String toWrite;
        try {
            toWrite = Crypto.encrypt(jsonObject.toString(), _dataEncryptionKey);
            writeToFile(Collections.singletonList(toWrite));
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            throw new InternalError("Saving the data failed with exception: " + e.getLocalizedMessage());
        }
    }

    private Map<String, String> loadDataFile() {
        if (_dataEncryptionKey == null) {
            throw new InternalError("Data encryption key is null, cannot proceed.");
        }
        Map<String, String> ret = new HashMap<>();
        // Each string *could* be a json object, for now its just one so put everything in ret
        List<String> strings;
        try {
            strings = readFromFile();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        for (String s : strings) {
            try {
                String decrypted = Crypto.decrypt(s, _dataEncryptionKey);
                //System.out.println("Strings from file " + decrypted);
                JSONObject jsonObject = new JSONObject(decrypted);
                for (String key : jsonObject.keySet()) {
                    ret.put(key, jsonObject.getString(key));
                }
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
            }
        }
        return ret;
    }

    private void saveKeystore(Map<String, SecretKey> data) {
        try {
            KeyStore ks = KeyStore.getInstance(PKCS_12);
            ks.load(null, _keystorePassword);
            // TODO add even more passwords?
            KeyStore.PasswordProtection protParam = new KeyStore.PasswordProtection(new char[]{'x'});
            data.forEach((groupName, key) -> {
                KeyStore.SecretKeyEntry entry = new KeyStore.SecretKeyEntry(key);
                try {
                    ks.setEntry(groupName, entry, protParam);
                } catch (KeyStoreException e) {
                    e.printStackTrace();
                }
            });

            ks.setEntry(DATA_ENCRYPTION_KEY_ALIAS, new KeyStore.SecretKeyEntry(_dataEncryptionKey), protParam);
            //System.out.println("saved data enc key as: " + Base64.getEncoder().encodeToString(_dataEncryptionKey.getEncoded()));
            FileOutputStream fos = new FileOutputStream(_keystorePath);
            ks.store(fos, _keystorePassword);
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            //e.printStackTrace();
            throw new InternalError("Saving the keystore failed with exception: " + e.getLocalizedMessage());
        }
    }

    private Map<String, SecretKey> loadKeystore(String path, char[] password) {
        Map<String, SecretKey> ret = new HashMap<>();
        try {
            KeyStore ks = KeyStore.getInstance(PKCS_12);
            ks.load(new FileInputStream(path), password);
            char[] pw = {'x'};
            // TODO add even more passwords?
            //KeyStore.PasswordProtection protParam = new KeyStore.PasswordProtection(pw);
            List<String> groupNames = Collections.list(ks.aliases());
            for (String group : groupNames) {
                try {
                    SecretKey key = (SecretKey) ks.getKey(group, pw);
                    ret.put(group, key);
                } catch (UnrecoverableEntryException e) {
                    e.printStackTrace();
                }
            }
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
            throw new InternalError("Loading KeyStore failed with error: " + e.getLocalizedMessage());
        }
        SecretKey key = ret.remove(DATA_ENCRYPTION_KEY_ALIAS);
        if (key == null) {
            throw new InternalError("client.Data Encryption Key could not be loaded");
        }
        _dataEncryptionKey = key;
        //System.out.println("Loaded data enc key as: " + Base64.getEncoder().encodeToString(_dataEncryptionKey.getEncoded()));
        return ret;
    }
}
