package voting;

import java.util.List;

public class MockVotingProcess implements VotingProcess {
    @Override
    public boolean vote(List<String> args) {
        System.out.println("Starting vote with args: " + args.toString());
        try {
            // Very complicated voting things
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return true;
    }
}
