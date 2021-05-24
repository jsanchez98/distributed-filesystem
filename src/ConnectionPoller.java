import java.net.*;


public class ConnectionPoller implements Runnable {
    private Connection connection;

    ConnectionPoller(Connection connection){
        this.connection = connection;
    }

    @Override
    public void run() {
        try{
            while(connection.readLine() != null){
            }
            connection.isOpen = false;
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
