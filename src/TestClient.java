import java.io.*;
import java.net.*;

public class TestClient {
    public static void main(String[] args){
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            Socket socket = new Socket(localhost, 4323);
            Connection connection = new Connection(socket);
            connection.write("client");

            String filename = args[1];
            String filesize = args[2];

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            connection.write(args[0] + " " + args[1] + " " + args[2]);
            String response = reader.readLine();
            System.out.println("Response: " + response);

            File file = new File(filename);
            InputStream in = new FileInputStream(file);
            byte[] fileContent = in.readAllBytes();

            for(String d : getDstorePorts(response)){
                Socket dsocket = new Socket(localhost, Integer.parseInt(d));
                Connection dstoreConnection = new Connection(dsocket);
                dstoreConnection.write("STORE " + filename + " " + filesize);
                sendContent(dstoreConnection, fileContent);
                dstoreConnection.close();
            }

            String controllerAck = "Controller ACK: " + connection.readLine();

            System.out.println(controllerAck);

            connection.close();

        } catch (Exception e){
            System.out.println(e.getMessage());
        }
    }

    public static void sendContent(Connection dstoreConnection, byte[] fileContent) throws IOException {
        if(dstoreConnection.readLine().equals("ACK")){
            dstoreConnection.writeBytes(fileContent);
        }
    }

    public static String[] getDstorePorts(String response){
        String ports = response.substring(9);
        return ports.split(" ");
    }
}
