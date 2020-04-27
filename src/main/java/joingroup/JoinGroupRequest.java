package joingroup;

import com.google.gson.Gson;

import java.util.List;

public class JoinGroupRequest {

    private final String applicantPublicKey;
    private final String contactPublicKey;
    private final String groupName;

    private final String address;
    private final int port;
    private final List<String> votingArgs;

    public JoinGroupRequest(String applicantPublicKey, String contactPublicKey, String groupName, List<String> votingArgs, String address, int port) {
        this.applicantPublicKey = applicantPublicKey;
        this.contactPublicKey = contactPublicKey;
        this.groupName = groupName;
        this.votingArgs = votingArgs;
        this.address = address;
        this.port = port;
    }

    public String getApplicantPublicKey() {
        return applicantPublicKey;
    }

    public String getContactPublicKey() {
        return contactPublicKey;
    }

    public String getGroupName() {
        return groupName;
    }

    public List<String> getVotingArgs() {
        return votingArgs;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
