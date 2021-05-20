import java.io.*;
import java.net.*;

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

            connection.write(args[0] + " " + args[1]);

            String response = reader.readLine();

            String[] parts = response.split(" ");
            int portNumber = Integer.parseInt(parts[1]);
            int fileSize = Integer.parseInt(parts[2]);

            Socket dsocket = new Socket(localhost, portNumber);
            Connection dstoreConnection = new Connection(dsocket);

            dstoreConnection.write("LOAD_DATA " + filename);

            byte[] fileContent = new byte[fileSize];
            dstoreConnection.readBytes(fileContent);

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
