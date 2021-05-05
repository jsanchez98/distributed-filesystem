import java.io.IOException;
import java.io.*;
import java.net.*;

public class Controller {
    static int cport;
    static int R;
    static int timeout;
    static int rebalancePeriod;

    Controller(String[] args){
        cport = Integer.parseInt(args[0]);
        R = Integer.parseInt(args[1]);
        timeout = Integer.parseInt(args[2]);
        rebalancePeriod = Integer.parseInt(args[3]);
    }

    public static void main(String[] args){
        new Controller(args);

        try{
            ServerSocket ss = new ServerSocket(4323);
            for(;;){
                System.out.println("Waiting for connection");
                Socket client = ss.accept();
                System.out.println("connected");
                InputStream in = client.getInputStream();
                byte[] buf = new byte[1000]; int buflen = 0;
                String firstBuffer = new String(buf,0, buflen);
                int firstSpace=firstBuffer.indexOf(" ");
                String command=firstBuffer.substring(0,firstSpace);
                System.out.println("command " + command);
            }
        } catch (Exception e){
            System.out.println(e.getMessage());
        }
    }
}
