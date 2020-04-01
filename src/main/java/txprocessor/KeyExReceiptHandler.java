package txprocessor;

import client.SawtoothUtils;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.protobuf.ByteString;
import keyexchange.KeyExchangeReceipt;
import keyexchange.ReceiptType;
import sawtooth.sdk.processor.Context;
import sawtooth.sdk.processor.TransactionHandler;
import sawtooth.sdk.processor.Utils;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.protobuf.TpProcessRequest;
import sawtooth.sdk.signing.PublicKey;
import sawtooth.sdk.signing.Secp256k1Context;
import sawtooth.sdk.signing.Secp256k1PublicKey;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public class KeyExReceiptHandler implements TransactionHandler {

    private static final String TRANSACTION_FAMILY_NAME = "KeyExchangeReceipt";
    private static final String TRANSACTION_FAMILY_VERSION = "0.1";
    private String namespace = "ac0cab";

    public KeyExReceiptHandler() {
        // Convention
        //namespace = SawtoothUtils.hash(transactionFamilyName()).substring(0, 6);
        print("Starting KeyExchangeReceiptTP with namespace '" + namespace + "'");
    }

    private void print(String s) {
        System.out.println("[KeyExchangeReceiptTP] " + s);
    }

    public String transactionFamilyName() {
        return TRANSACTION_FAMILY_NAME;
    }

    public String getVersion() {
        return TRANSACTION_FAMILY_VERSION;
    }

    public Collection<String> getNameSpaces() {
        return Collections.singletonList(namespace);
    }

    public void apply(TpProcessRequest transactionRequest, Context state) throws InvalidTransactionException, InternalError {
        // Payload is expected to be of KeyExchangeReceipt.class
        String s = transactionRequest.getPayload().toString(UTF_8);
        print("Got payload: " + s);
        if (s == null || s.isEmpty()) {
            print("Empty payload!");
            throw new InvalidTransactionException("Empty payload!");
        }

        KeyExchangeReceipt receipt;
        try {
            receipt = new Gson().fromJson(s, KeyExchangeReceipt.class);
        } catch (JsonSyntaxException e) {
            print("Malformed payload: " + s);
            throw new InvalidTransactionException("Malformed payload: " + s);
        }

        // Verify the member that shared a key is the one that submitted the KeyExchangeReceipt
        if (!transactionRequest.getHeader().getSignerPublicKey().equals(receipt.getMemberPublicKey())) {
            print("Member and signer key are different!\nsigner: "
                    + transactionRequest.getHeader().getSignerPublicKey()
                    + "\nreceipt: " + receipt.getMemberPublicKey());

            throw new InvalidTransactionException("Member and signer key are different!");
        }

        // Check integrity of receipt
        PublicKey publicKey = new Secp256k1PublicKey(
                SawtoothUtils.hexDecode(
                        receipt.getMemberPublicKey()
                ));

        if (receipt.getSignature() == null) {
            print("Unsigned receipt!");
            throw new InvalidTransactionException("Unsigned receipt!");
        }

        Secp256k1Context context = new Secp256k1Context();
        boolean verified = context.verify(
                receipt.getSignature(),
                receipt.getSignablePayload().getBytes(UTF_8),
                publicKey
        );

        if (!verified) {
            print("Signature verification failed!");
            throw new InvalidTransactionException("Receipt signature is invalid");
        }

        // Prepare the address to write to: hash(pubMember, pubJoiner, group), if JOIN_NETWORK without group
        String toHash = receipt.getMemberPublicKey() + receipt.getApplicantPublicKey();
        if (receipt.getReceiptType() == ReceiptType.JOIN_GROUP) {
            toHash += receipt.getGroup();
        }

        String hash = SawtoothUtils.hash(toHash);
        String address = namespace + hash.substring(hash.length() - 64);
        print("toHash for address: " + toHash);
        print("Calculated Address: " + address);

        // Write the payload (receipt) to the address
        ByteString writeToState = ByteString.copyFrom(receipt.toString(), UTF_8);
        Map.Entry<String, ByteString> entry = new AbstractMap.SimpleEntry<>(address, writeToState);
        Collection<Map.Entry<String, ByteString>> addressValues = Arrays.asList(entry);
        Collection<String> returnedAddresses = state.setState(addressValues);

        // Check if successful
        if (returnedAddresses.isEmpty()) {
            throw new InvalidTransactionException("Set state error");
        }

        // Update the entry that has the keys which are in the given group
        if (receipt.getReceiptType() == ReceiptType.JOIN_GROUP) {
            print("Receipt is of JOIN_GROUP, updating group entry...");
            String groupAddress = SawtoothUtils.namespaceHashAddress(namespace, receipt.getGroup());
            print("Group address: " + groupAddress);
            // Get entries form address
            Map<String, ByteString> entries = state.getState(
                    Collections.singletonList(groupAddress));

            ByteString bsEntry = entries.get(groupAddress);
            List<String> lEntries = new ArrayList<>(Arrays.asList(bsEntry.toString().split(",")));
            print("Entries at address: " + lEntries.toString());

            lEntries.add(receipt.getApplicantPublicKey());
            lEntries.add(receipt.getMemberPublicKey()); // double check, gets removed here vvv if it is already in it
            String strToWrite = lEntries.stream().distinct().reduce("", (s1, s2) -> s1 += s2 + ",");
            strToWrite = strToWrite.substring(0, (strToWrite.length() - 1)); // remove trailing ','
            print("Writing updated entry: " + strToWrite);
            ByteString toWrite = ByteString.copyFrom(strToWrite.getBytes(UTF_8));
            Map.Entry<String, ByteString> entry2 = new AbstractMap.SimpleEntry<>(address, toWrite);
            Collection<Map.Entry<String, ByteString>> addressValues2 = Collections.singletonList(entry2);
            Collection<String> returnedAddresses2 = state.setState(addressValues2);

            // Check if successful
            if (returnedAddresses.isEmpty()) {
                throw new InvalidTransactionException("Set state error");
            }

        }
    }

}

