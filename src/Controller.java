import java.io.IOException;
import java.net.ServerSocket;

public class Controller {
    public static void main(String[] args){
        String command = args[0];
        String fileName = args[1];
        try{
            ServerSocket ss = new ServerSocket(4323);
            for(;;){

            }
        } catch (Exception e){
            System.out.println(e.getMessage());
        }
    }
}
