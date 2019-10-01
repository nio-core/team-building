public class Main implements SubscriptionCallback {

    public static void main(String args[]) {
        HyperZMQ t = new HyperZMQ();

        t.subscribe();
        t.sendTransaction();
    }

    @Override
    public void stateChange(String s) {

    }
}
