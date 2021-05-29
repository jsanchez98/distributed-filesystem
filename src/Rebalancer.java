public class Rebalancer implements Runnable {
    private boolean newDstore;
    private int rebalance_period;
    private Controller mainController;

    Rebalancer(int rebalance_period, Controller mainController){
        this.rebalance_period = rebalance_period;
        this.mainController = mainController;
        newDstore = false;
    }

    @Override
    public void run() {
        long previous = System.currentTimeMillis();
        for(;;){
                if (newDstore || System.currentTimeMillis() - previous > rebalance_period) {
                    try {
                        mainController.rebalanceOperation();

                    } catch (Exception e) {
                        ControllerLogger.getInstance().log(e.getMessage());
                    }

                    setNewDstoreFalse();
                    previous = System.currentTimeMillis();
                }

        }
    }

    public void setNewDstoreTrue(){
        newDstore = true;
    }

    public void setNewDstoreFalse(){
        newDstore = false;
    }
}
