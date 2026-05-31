import java.io.*;
import java.net.*;
import java.util.*;

public class FTPClient {
    private Socket controlSocket;
    private BufferedReader reader;
    private PrintWriter writer;

    public void connect(String host, int port) throws IOException, FTPException {
        controlSocket = new Socket(host, port);
        reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
        writer = new PrintWriter(controlSocket.getOutputStream(), true);
        readReply();
    }

    private void sendCommand(String command) {
        if (command.startsWith("PASS")) {
            System.out.println("> PASS ********");
        } else {
            System.out.println("> " + command);
        }
        writer.print(command + "\r\n");
        writer.flush();
    }

    private String readReply() throws IOException, FTPException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("Server closed the connection unexpectedly");
        }
        System.out.println(line);

        if (line.length() >= 4 && line.charAt(3) == '-') {
            String endMarker = line.substring(0, 3) + " ";
            while (true) {
                String next = reader.readLine();
                if (next == null) {
                    throw new IOException("Server closed connection mid-reply");
                }
                System.out.println(next);
                if (next.startsWith(endMarker)) {
                    line = next;
                    break;
                }
            }
        }

        int code = Integer.parseInt(line.substring(0, 3));
        if (code >= 400) {
            throw new FTPException(code, line);
        }
        return line;
    }

    public void login(String username, String password) throws IOException, FTPException {
        sendCommand("USER " + username);
        readReply();
        sendCommand("PASS " + password);
        readReply();
    }

    public String pwd() throws IOException, FTPException {
        sendCommand("PWD");
        String reply = readReply();
        int first = reply.indexOf('"');
        int last  = reply.lastIndexOf('"');
        if (first >= 0 && last > first) {
            return reply.substring(first + 1, last);
        }
        return reply;
    }

    public void cd(String path) throws IOException, FTPException {
        sendCommand("CWD " + path);
        readReply();
    }

    private FTPConnection openDataConnection() throws IOException, FTPException {
        sendCommand("PASV");
        String reply = readReply();
        int open  = reply.indexOf('(');
        int close = reply.indexOf(')', open);
        if (open < 0 || close < 0) {
            throw new IOException("Malformed PASV reply: " + reply);
        }
        String[] parts = reply.substring(open + 1, close).split(",");
        if (parts.length != 6) {
            throw new IOException("PASV reply did not contain 6 numbers: " + reply);
        }
        String host = parts[0].trim() + "." + parts[1].trim() + "."
                + parts[2].trim() + "." + parts[3].trim();
        int p1 = Integer.parseInt(parts[4].trim());
        int p2 = Integer.parseInt(parts[5].trim());
        int port = p1 * 256 + p2;
        return new FTPConnection(host, port);
    }

    public String ls() throws IOException, FTPException {
        StringBuilder listing = new StringBuilder();
        try (FTPConnection data = openDataConnection()) {
            sendCommand("LIST");
            readReply();
            BufferedReader dataReader = new BufferedReader(
                    new InputStreamReader(data.getInputStream())
            );
            String dataLine;
            while ((dataLine = dataReader.readLine()) != null) {
                listing.append(dataLine).append("\n");
            }
        }
        readReply();
        return listing.toString();
    }

    public void get(String remoteFile, String localFile) throws IOException, FTPException {
        sendCommand("TYPE I");
        readReply();
        try (FTPConnection data = openDataConnection();
             FileOutputStream fileOut = new FileOutputStream(localFile)) {
            sendCommand("RETR " + remoteFile);
            readReply();
            InputStream dataIn = data.getInputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = dataIn.read(buffer)) != -1) {
                fileOut.write(buffer, 0, bytesRead);
            }
        }
        readReply();
    }

    public void put(String localFile, String remoteFile) throws IOException, FTPException {
        sendCommand("TYPE I");
        readReply();
        try (FTPConnection data = openDataConnection();
             FileInputStream fileIn = new FileInputStream(localFile)) {
            sendCommand("STOR " + remoteFile);
            readReply();   // 125 or 150
            OutputStream dataOut = data.getOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                dataOut.write(buffer, 0, bytesRead);
            }
            dataOut.flush();
        }
        readReply();
    }

    public void delete(String path) throws IOException, FTPException {
        sendCommand("DELE " + path);
        readReply();
    }

    public void mkdir(String path) throws IOException, FTPException {
        sendCommand("MKD " + path);
        readReply();
    }

    public void rmdir(String path) throws IOException, FTPException {
        sendCommand("RMD " + path);
        readReply();
    }

    public void quit() throws IOException, FTPException {
        sendCommand("QUIT");
        readReply();
        controlSocket.close();
    }
}
