
import java.io.*;
import java.net.*;

public class TestClientRemove {
    public static void main(String[] args){
        try{
            InetAddress localhost = InetAddress.getLocalHost();
            Socket socket = new Socket(localhost, 4323);
            Connection connection = new Connection(socket);

            connection.write("client");

            String filename = args[1];

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            connection.write("REMOVE " + filename);

            System.out.println(connection.readLine());
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}