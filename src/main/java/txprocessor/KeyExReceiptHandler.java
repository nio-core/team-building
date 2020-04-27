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
        // TODO undo comment around verifications

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
        // TODO uncomment <<
        // Prepare the address to write to: hash(pubMember, pubJoiner, group), if JOIN_NETWORK without group
        String toHash = receipt.getMemberPublicKey() + receipt.getApplicantPublicKey();
        if (receipt.getReceiptType() == ReceiptType.JOIN_GROUP) {
            toHash += receipt.getGroup();
        }

        String address = SawtoothUtils.namespaceHashAddress(this.namespace, toHash);
        //print("toHash for address: " + toHash);
        print("Calculated Address: " + address);

        if (!TPUtils.writeToAddress(receipt.toString(), address, state)) {
            throw new InvalidTransactionException("Unable to write receipt to state");
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
            List<String> lEntries = new ArrayList<>(Arrays.asList(bsEntry.toStringUtf8().split(",")));
            print("Entries at address before update: " + lEntries.toString());
            print("Adding " + receipt.getApplicantPublicKey());
            lEntries.add(receipt.getApplicantPublicKey());
            lEntries.add(receipt.getMemberPublicKey()); // double check, gets removed here vvv if it is already in it
            String strToWrite = lEntries.stream().distinct().reduce("", (s1, s2) -> {
                if (s2.isEmpty()) return s1;
                return s1 += s2 + ",";
            });
            strToWrite = strToWrite.substring(0, (strToWrite.length() - 1)); // remove trailing ','

            if (!TPUtils.writeToAddress(strToWrite, groupAddress, state)) {
                throw new InvalidTransactionException("Unable to update group member entry");
            }
        }
    }
}

