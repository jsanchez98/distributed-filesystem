import java.io.*;
import java.net.*;

public class TestClient {
    public static void main(String[] args){
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            Socket socket = new Socket(localhost, 4323);
            OutputStream out = socket.getOutputStream();
            out.write((args[0] + " " + args[1] + " " + args[2]).getBytes());
            out.close();
        } catch (Exception e){
            System.out.println(e.getMessage());
        }
    }
}
