import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 2121;

        Scanner scanner = new Scanner(System.in);
        FTPClient client = new FTPClient();

        try {
            client.connect(host, port);
            System.out.print("Username: ");
            String user = scanner.nextLine().trim();
            System.out.print("Password: ");
            String pass = scanner.nextLine().trim();
            client.login(user, pass);
        } catch (Exception e) {
            System.out.println("Connect/login failed: " + e.getMessage());
            return;
        }

        printHelp();

        boolean running = true;
        while (running) {
            System.out.print("ftp> ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] tokens = line.split("\\s+");
            String cmd = tokens[0].toLowerCase();

            try {
                switch (cmd) {
                    case "pwd":
                        System.out.println(client.pwd());
                        break;
                    case "cd":
                        if (tokens.length < 2) { System.out.println("Usage: cd <path>"); break; }
                        client.cd(tokens[1]);
                        System.out.println("Directory changed.");
                        break;
                    case "ls":
                        System.out.print(client.ls());
                        break;
                    case "get":
                        if (tokens.length < 2) { System.out.println("Usage: get <remote> [local]"); break; }
                        client.get(tokens[1], tokens.length >= 3 ? tokens[2] : tokens[1]);
                        System.out.println("Downloaded.");
                        break;
                    case "put":
                        if (tokens.length < 2) { System.out.println("Usage: put <local> [remote]"); break; }
                        client.put(tokens[1], tokens.length >= 3 ? tokens[2] : tokens[1]);
                        System.out.println("Uploaded.");
                        break;
                    case "delete":
                        if (tokens.length < 2) { System.out.println("Usage: delete <path>"); break; }
                        client.delete(tokens[1]);
                        System.out.println("Deleted.");
                        break;
                    case "mkdir":
                        if (tokens.length < 2) { System.out.println("Usage: mkdir <path>"); break; }
                        client.mkdir(tokens[1]);
                        System.out.println("Created.");
                        break;
                    case "rmdir":
                        if (tokens.length < 2) { System.out.println("Usage: rmdir <path>"); break; }
                        client.rmdir(tokens[1]);
                        System.out.println("Removed.");
                        break;
                    case "help":
                        printHelp();
                        break;
                    case "quit":
                    case "exit":
                        client.quit();
                        running = false;
                        break;
                    default:
                        System.out.println("Unknown command: " + cmd + "  (type 'help')");
                }
            } catch (FTPException e) {
                System.out.println("Server refused: " + e.getMessage());
            } catch (IOException e) {
                System.out.println("I/O error: " + e.getMessage());
            }
        }

        scanner.close();
        System.out.println("Session closed.");
    }

    private static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  pwd                   show working directory");
        System.out.println("  cd <path>             change directory");
        System.out.println("  ls                    list directory");
        System.out.println("  get <remote> [local]  download a file");
        System.out.println("  put <local> [remote]  upload a file");
        System.out.println("  delete <path>         delete a file");
        System.out.println("  mkdir <path>          make directory");
        System.out.println("  rmdir <path>          remove empty directory");
        System.out.println("  help                  show this help");
        System.out.println("  quit | exit           close session");
    }
}
