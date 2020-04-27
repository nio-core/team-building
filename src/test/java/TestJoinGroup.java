import client.HyperZMQ;
import joingroup.JoinGroupStatusCallback;
import org.junit.Assert;
import org.junit.Test;
import sawtooth.sdk.signing.PrivateKey;
import sawtooth.sdk.signing.Secp256k1Context;
import voting.MockVotingProcess;

public class TestJoinGroup implements JoinGroupStatusCallback {

    final String PRIVATEKEY_MEMBER = "ba58429bc686bcf14725c60b11bee7b09d1d66c1b4f38acf9fe51d91ab6cc060";
    final String PUBLICKEY_MEMBER = "022fb56a55248087549e1595baf214445f81b0f40c47197846bc873becc1c4bd83";

    final String PRIVATEKEY_APPLICANT = "d17f08a4b8b4e7f75f326d8c15f0445ec27a6561171a169bb03245ff7625ba41";
    final String PUBLICKEY_APPLICANT = "02fb9e47838133e9d4d1ad3c5ba69b607d7b3377e0f2d8dd03b610d1f15b9ea3c8";

    @Test
    public void testJoinGroup() throws InterruptedException {
        HyperZMQ member = new HyperZMQ("member", "password", true);
        HyperZMQ applicant = new HyperZMQ("applicant", "sadasdsa", true);
        // Used fixed keys for comparisons
        member.setPrivateKey(PRIVATEKEY_MEMBER);
        applicant.setPrivateKey(PRIVATEKEY_APPLICANT);

        member.createGroup("testgroup");
        member.setVotingProcess(new MockVotingProcess());

        applicant.tryJoinGroup("testgroup", this);

        Assert.assertEquals(member.getKeyForGroup("testgroup"),
                applicant.getKeyForGroup("testgroup"));

        Thread.sleep(5000);
    }

    @Override
    public void joinGroupStatusCallback(String status) {
        System.out.println("[GroupJoinCallback] " + status);
    }
}
