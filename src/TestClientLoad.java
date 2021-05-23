import java.io.*;
import java.net.*;
import java.util.Arrays;

public class TestClientLoad {
    public static void main(String[] args){
        try{
            InetAddress localhost = InetAddress.getLocalHost();
            Socket socket = new Socket(localhost, 4323);
            Connection connection = new Connection(socket);
            connection.write("client");

            String filename = args[1];

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            connection.write("LOAD " + args[1]);

            String response = reader.readLine();
            System.out.println(response);

            String[] parts = response.split(" ");
            int portNumber = Integer.parseInt(parts[1]);
            int fileSize = Integer.parseInt(parts[2]);

            Socket dsocket = new Socket(localhost, portNumber);
            Connection dstoreConnection = new Connection(dsocket);

            dstoreConnection.write("LOAD_DATA " + filename);

            byte[] fileContent = new byte[fileSize];
            dstoreConnection.readBytes(fileContent);

            for(;;) {
                if (dstoreConnection.readLine() == null) {
                    connection.write("RELOAD " + filename);
                    fileContent = new byte[fileSize];

                    String newResponse = reader.readLine();
                    System.out.println("newResponse: " + newResponse);
                    String[] newParts = newResponse.split(" ");
                    int newPort = Integer.parseInt(newParts[1]);
                    Socket newDsocket = new Socket(localhost, newPort);
                    Connection newDstoreConnection = new Connection(newDsocket);

                    System.out.println(filename);
                    newDstoreConnection.write("LOAD_DATA " + filename);
                    newDstoreConnection.readBytes(fileContent);
                } else {
                    break;
                }
            }

            File file = new File(filename);
            file.createNewFile();

            FileOutputStream fos = new FileOutputStream(file);

            if(fileContent != null) {
                fos.write(fileContent);
                fos.flush();
            }

            dstoreConnection.close();
            connection.close();

        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
