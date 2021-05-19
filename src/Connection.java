import java.io.*;
import java.net.*;

public class Connection {
    public InputStream in;
    public OutputStream out;
    public BufferedReader reader;
    public PrintWriter writer;
    public Socket socket;

    Connection(Socket socket){
        try {
            this.socket = socket;
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
            this.reader = new BufferedReader(
                                new InputStreamReader(in));
            this.writer = new PrintWriter(
                                new OutputStreamWriter(out));
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void write(String message){
        writer.println(message);
        writer.flush();
    }

    public void writeBytes(byte[] bytes) throws IOException {
        out.write(bytes);
        out.flush();
    }

    public String readLine() throws IOException {
        return reader.readLine();
    }

    /**
     *
     * @param bytes
     * @return bytelen
     * @throws IOException
     */
    public int readBytes(byte[] bytes) throws IOException {
        return in.read(bytes);
    }

    public Socket getSocket(){
        return socket;
    }

    public void close() throws IOException {
        out.close();
        in.close();
        reader.close();
        writer.close();
    }
}
