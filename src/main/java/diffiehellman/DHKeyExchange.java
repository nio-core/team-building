package diffiehellman;

import client.SawtoothUtils;
import sawtooth.sdk.signing.Signer;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.Callable;


public class DHKeyExchange implements Callable<EncryptedStream> {

    private static final String DH = "DH";
    private final String AES = "AES";
    private String myID;
    private Signer signer;
    private String theirPublicKey;
    private String address;
    private boolean isServer;
    private int port;
    private PrintWriter out;
    private BufferedReader in;
    boolean doPrint = false;

    public DHKeyExchange(String myID, Signer mySigner, String theirPublicKey, String address, int port, boolean isServer) {
        this.myID = myID;
        this.signer = mySigner;
        this.theirPublicKey = theirPublicKey;
        this.address = address;
        this.port = port;
        this.isServer = isServer;
    }

    // TODO: improve error handling

    @Override
    public EncryptedStream call() throws Exception {
        // Setup sockets and streams
        Socket socket;
        if (isServer) {
            print("Setting up server");
            ServerSocket serverSocket = new ServerSocket(port);
            socket = serverSocket.accept();
        } else {
            print("Setting up client");
            socket = new Socket(address, port);
        }
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        return (isServer ? doServer() : doClient());
    }

    private EncryptedStream doServer() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidAlgorithmParameterException, InvalidKeyException, SignatureException {
        PublicKey publicKey = retrievePublicKey();
        if (publicKey == null) return null; // TODO
        DHParameterSpec dhParameterSpec = ((DHPublicKey) publicKey).getParams();

        // Create own key using the received one
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(DH);
        keyPairGenerator.initialize(dhParameterSpec);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // Create key agreement object
        KeyAgreement keyAgreement = KeyAgreement.getInstance(DH);
        keyAgreement.init(keyPair.getPrivate());

        // Send public key to other party
        DHMessage message = makeDHMessage(keyPair.getPublic());
        out.println(message.toString());
        print("Sent message: " + message.toString());

        // Create the shared secret key
        keyAgreement.doPhase(publicKey, true);
        // Shorten to 32 byte size for max AES length
        byte[] encodedKey = Arrays.copyOfRange(keyAgreement.generateSecret(), 0, 32);
        SecretKey secretKey = new SecretKeySpec(encodedKey, 0, encodedKey.length, AES);
        print("SecretKey created: " + Base64.getEncoder().encodeToString(secretKey.getEncoded()));
        return new EncryptedStream(in, out, secretKey);
    }

    private DHMessage makeDHMessage(PublicKey publicKey) {
        byte[] bytes = publicKey.getEncoded();
        String strPublicKey = Base64.getEncoder().encodeToString(bytes);
        DHMessage message = new DHMessage(strPublicKey, myID);
        message.setSignature(signer.sign(message.getSignablePayload().getBytes(StandardCharsets.UTF_8)));
        return message;
    }

    private EncryptedStream doClient() throws NoSuchAlgorithmException, InvalidKeyException, IOException, InvalidKeySpecException, SignatureException {
        // The client does the first move by sending its part to the server
        // While the server waits for the agreement object to create its own

        // Generate a DH key pair
        print("Generating keypair");
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(DH);
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // Now make a DH KeyAgreement object
        print("Initialization...");
        KeyAgreement agreement = KeyAgreement.getInstance(DH);
        agreement.init(keyPair.getPrivate());

        // Get the public key which we want to exchange with the other party
        DHMessage message = makeDHMessage(keyPair.getPublic());
        // Send the message
        out.println(message.toString());
        print("Message sent: " + message.toString());

        PublicKey publicKey = retrievePublicKey();
        if (publicKey == null) return null; //TODO
        // Combine the two parts and generate the secret key
        agreement.doPhase(publicKey, true);
        // Shorten to 32 byte size for max AES length
        byte[] encodedKey = Arrays.copyOfRange(agreement.generateSecret(), 0, 32);
        SecretKey secretKey = new SecretKeySpec(encodedKey, 0, encodedKey.length, AES);
        print("SecretKey created: " + Base64.getEncoder().encodeToString(secretKey.getEncoded()));
        return new EncryptedStream(in, out, secretKey);
    }

    /**
     * Retrieve the message from the input stream (BLOCKING). Verify it and recreate the public key it contains
     *
     * @return public key object or null if there was nothing or something malformed received
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     */
    private PublicKey retrievePublicKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException {
        // Receive, verify and recreate
        String s = in.readLine();
        DHMessage received = SawtoothUtils.deserializeMessage(s, DHMessage.class);
        if (received == null) return null;

        print("message received, verifying...");
        boolean verified = SawtoothUtils.verify(received.getSignablePayload(), received.getSignature(), theirPublicKey);
        if (!verified) {
            throw new SignatureException("Received message has invalid signature: " + received.toString());
        }

        print("message verified, recreating key...");
        KeyFactory keyFactory = KeyFactory.getInstance(DH);
        byte[] receivedBytes = Base64.getDecoder().decode(received.getPublicKey());
        X509EncodedKeySpec spec = new X509EncodedKeySpec(receivedBytes);
        return keyFactory.generatePublic(spec);
    }

    private void print(String message) {
        if (doPrint)
            System.out.println("[DHKE-" + myID + "] " + message);
    }
}
