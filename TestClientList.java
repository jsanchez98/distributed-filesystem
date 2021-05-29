import java.net.*;

public class TestClientList {
    public static void main(String[] args){
        try{
            InetAddress localhost = InetAddress.getLocalHost();
            Socket socket = new Socket(localhost, 12345);
            Connection connection = new Connection(socket);

            connection.write("client");

            connection.write("LIST");

            System.out.println(connection.readLine());
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}