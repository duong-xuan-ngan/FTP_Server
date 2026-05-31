import java.io.*;
import java.net.*;

public class FTPConnection implements Closeable {
    private Socket socket;

    public FTPConnection(String host, int port) throws IOException
    {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000); // 5s connect timeout
        socket.setSoTimeout(30000); // 30s read timeout
    }

    public InputStream getInputStream() throws IOException
    {
        return socket.getInputStream();
    }

    public OutputStream getOutputStream() throws IOException
    {
        return socket.getOutputStream();
    }

    @Override
    public void close() throws IOException
    {
        if (socket != null && !socket.isClosed())
        {
            socket.close();
        }
    }
}
