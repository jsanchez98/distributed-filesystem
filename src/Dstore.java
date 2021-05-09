import java.net.*;

public class Dstore {
    int port;
    int cport;
    int timeout;
    int rebalance_period;

    Dstore(String[] args){
        this.port = Integer.parseInt(args[0]);
        this.cport = Integer.parseInt(args[1]);
        this.timeout = Integer.parseInt(args[2]);
        this.rebalance_period = Integer.parseInt(args[3]);
    }

    public static void main(String[] args) throws Exception {
        Dstore d = new Dstore(args);
        d.start();
    }

    public void start(){
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            Socket socket = new Socket(localhost, 4323);
            Connection controllerConnection = new Connection(socket);
            controllerConnection.write("dstore " + Integer.toString(port));

        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public int getPort(){
        return port;
    }
}
