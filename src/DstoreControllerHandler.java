import java.net.*;

public class DstoreControllerHandler implements Runnable {
    private Socket controllerSocket;
    Dstore dstore;

    DstoreControllerHandler(Socket socket, Dstore dstore){
        this.controllerSocket = socket;
        this.dstore = dstore;
    }

    @Override
    public void run() {
        Connection controllerConnection = new Connection(controllerSocket);
        controllerConnection.write("dstore " + dstore.port);
        System.out.println(dstore.port);

        try {
            String message = controllerConnection.readLine();

            while(message != null){
                if(message.startsWith("REMOVE")){
                    System.out.println(message);
                    String[] parts = message.split(" ");
                    String filename = parts[1];

                    boolean result = dstore.removeFile(filename);
                    System.out.println(result);
                    if(result){
                        controllerConnection.write("REMOVE_ACK " + filename);
                    } else {
                        controllerConnection.write("ERROR_FILE_DOES_NOT_EXIST " + filename);
                    }
                }
                message = controllerConnection.readLine();
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
