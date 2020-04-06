package txprocessor;

import com.google.protobuf.ByteString;
import sawtooth.sdk.processor.Context;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;

import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public class TPUtils {

    public static boolean writeToAddress(String toWrite, String address, Context state) {
        print("Writing '" + toWrite + "' to " + address);
        ByteString byteString = ByteString.copyFrom(toWrite.getBytes(UTF_8));
        Map.Entry<String, ByteString> entry = new AbstractMap.SimpleEntry<>(address, byteString);
        Collection<Map.Entry<String, ByteString>> addressValues = Collections.singletonList(entry);
        try {
            Collection<String> returnedAddresses = state.setState(addressValues);
            // Check if successful
            return !returnedAddresses.isEmpty();
        } catch (InternalError internalError) {
            internalError.printStackTrace();
        } catch (InvalidTransactionException e) {
            e.printStackTrace();
        }
        return false;
    }

    // TODO WIP
    public static void checkStateAtAddress(String address, Context context) {
        Collection<String> checkAddr = new ArrayList<>();
        checkAddr.add(address);
        Map<String, ByteString> alreadySetValues = null;
        try {
            alreadySetValues = context.getState(checkAddr);
        } catch (InternalError internalError) {
            internalError.printStackTrace();
        } catch (InvalidTransactionException e) {
            e.printStackTrace();
        }
        if (alreadySetValues != null) {
            alreadySetValues.forEach((key, value) ->
                    print("Values at address before: "
                            + key + "=" + value.toString(UTF_8)));
            print("... will be overwritten.");
        } else {
            print("address is empty.");
        }
    }

    private static void print(String message) {
        System.out.println("[TPUtils] " + message);
    }
}
