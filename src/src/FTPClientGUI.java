import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.*;
import java.util.*;

/**
 * FTPClientGUI — a JavaFX front-end over FTPClient. Buttons call FTPClient
 * methods on a background thread; UI updates go through Platform.runLater.
 */
public class FTPClientGUI extends Application {

    private FTPClient client;
    private boolean connected = false;
    private boolean busy = false;

    private File currentLocalDir = new File(System.getProperty("user.dir"));

    private TextField hostField;
    private TextField portField;
    private TextField userField;
    private PasswordField passField;
    private CheckBox anonymousCheck;
    private Button connectBtn;
    private Button disconnectBtn;

    private TableView<FileItem> remoteTable;
    private TableView<FileItem> localTable;
    private Label remotePathLabel;
    private Label localPathLabel;
    private Button refreshBtn;
    private Button mkdirBtn;
    private Button deleteBtn;
    private Button downloadBtn;
    private Button uploadBtn;
    private ToggleGroup transferMode;
    private RadioButton binaryRadio;
    private RadioButton asciiRadio;

    private TextArea logArea;
    private Label statusLabel;

    // Public so PropertyValueFactory reflection can see the getters.
    public static class FileItem {
        private final String name;
        private final String size;
        private final String date;
        private final boolean dir;

        public FileItem(String name, String size, String date, boolean dir) {
            this.name = name;
            this.size = size;
            this.date = date;
            this.dir = dir;
        }

        public String getName()  { return dir ? name + "/" : name; }
        public String getRawName() { return name; }
        public String getSize()  { return size; }
        public String getDate()  { return date; }
        public boolean isDir()   { return dir; }
    }

    private interface FtpTask { void run() throws Exception; }

    @Override
    public void start(Stage stage) {
        redirectConsoleToLog();

        BorderPane root = new BorderPane();
        root.setTop(buildConnectionBar());
        root.setCenter(buildCenter());
        root.setBottom(buildBottom());
        root.setPadding(new Insets(8));

        Scene scene = new Scene(root, 940, 660);
        stage.setTitle("Java FTP Client");
        stage.setScene(scene);
        stage.show();

        refreshLocal();
        updateButtons();
        log("Ready. Enter server details and click Connect.");
        log("Defaults point at a local pyftpdlib server (127.0.0.1:2121, test/test).");
    }

    private Node buildConnectionBar() {
        hostField = new TextField("127.0.0.1");
        hostField.setPrefColumnCount(12);
        portField = new TextField("2121");
        portField.setPrefColumnCount(4);
        userField = new TextField("test");
        userField.setPrefColumnCount(8);
        passField = new PasswordField();
        passField.setText("test");
        passField.setPrefColumnCount(8);

        anonymousCheck = new CheckBox("Anonymous");
        anonymousCheck.setOnAction(e -> updateButtons());

        connectBtn = new Button("Connect");
        connectBtn.setOnAction(e -> doConnect());
        disconnectBtn = new Button("Disconnect");
        disconnectBtn.setOnAction(e -> doDisconnect());

        HBox bar = new HBox(8,
                new Label("Host:"), hostField,
                new Label("Port:"), portField,
                new Label("User:"), userField,
                new Label("Pass:"), passField,
                anonymousCheck,
                connectBtn, disconnectBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 8, 0));
        return bar;
    }

    private Node buildCenter() {
        remotePathLabel = new Label("/");
        remoteTable = makeFileTable("Remote is empty");
        remoteTable.setRowFactory(tv -> {
            TableRow<FileItem> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    FileItem it = row.getItem();
                    if (it.isDir()) navigateRemote(it.getRawName());
                }
            });
            return row;
        });
        VBox left = new VBox(4, new Label("Remote"), remotePathLabel, remoteTable);
        VBox.setVgrow(remoteTable, Priority.ALWAYS);

        localPathLabel = new Label(currentLocalDir.getAbsolutePath());
        localTable = makeFileTable("Local folder is empty");
        localTable.setRowFactory(tv -> {
            TableRow<FileItem> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    FileItem it = row.getItem();
                    if (it.isDir()) navigateLocal(it.getRawName());
                }
            });
            return row;
        });
        VBox right = new VBox(4, new Label("Local"), localPathLabel, localTable);
        VBox.setVgrow(localTable, Priority.ALWAYS);

        refreshBtn  = new Button("Refresh");
        mkdirBtn    = new Button("Create folder");
        deleteBtn   = new Button("Delete");
        downloadBtn = new Button("Download >");
        uploadBtn   = new Button("< Upload");
        for (Button b : new Button[]{refreshBtn, mkdirBtn, deleteBtn, downloadBtn, uploadBtn}) {
            b.setMaxWidth(Double.MAX_VALUE);
        }
        refreshBtn.setOnAction(e -> { refreshRemote(); refreshLocal(); });
        mkdirBtn.setOnAction(e -> doCreateFolder());
        deleteBtn.setOnAction(e -> doDeleteRemote());
        downloadBtn.setOnAction(e -> doDownload());
        uploadBtn.setOnAction(e -> doUpload());

        transferMode = new ToggleGroup();
        binaryRadio = new RadioButton("Binary");
        binaryRadio.setToggleGroup(transferMode);
        binaryRadio.setSelected(true);
        asciiRadio = new RadioButton("ASCII");
        asciiRadio.setToggleGroup(transferMode);
        asciiRadio.setOnAction(e ->
                log("Note: transfers use binary mode (TYPE I); ASCII is shown for completeness."));

        VBox transferBox = new VBox(4, new Label("Transfer"), binaryRadio, asciiRadio);

        VBox controls = new VBox(8,
                refreshBtn, mkdirBtn, deleteBtn,
                new Separator(),
                downloadBtn, uploadBtn,
                new Separator(),
                transferBox);
        controls.setAlignment(Pos.TOP_CENTER);
        controls.setPadding(new Insets(24, 8, 0, 8));
        controls.setPrefWidth(140);
        controls.setMinWidth(140);

        HBox center = new HBox(8, left, controls, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        return center;
    }

    private TableView<FileItem> makeFileTable(String placeholder) {
        TableView<FileItem> table = new TableView<>();
        table.setPlaceholder(new Label(placeholder));

        TableColumn<FileItem, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(220);

        TableColumn<FileItem, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(new PropertyValueFactory<>("size"));
        sizeCol.setPrefWidth(80);

        TableColumn<FileItem, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        dateCol.setPrefWidth(150);

        table.getColumns().add(nameCol);
        table.getColumns().add(sizeCol);
        table.getColumns().add(dateCol);
        return table;
    }

    private Node buildBottom() {
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(8);
        logArea.setWrapText(false);
        logArea.setStyle("-fx-font-family: 'Consolas','monospace'; -fx-font-size: 12px;");

        statusLabel = new Label("Disconnected");
        statusLabel.setPadding(new Insets(4, 0, 0, 2));

        VBox bottom = new VBox(4, new Label("Log"), logArea, statusLabel);
        bottom.setPadding(new Insets(8, 0, 0, 0));
        VBox.setVgrow(logArea, Priority.ALWAYS);
        return bottom;
    }

    private void doConnect() {
        if (connected || busy) return;

        final String host = hostField.getText().trim();
        final int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            log("Invalid port number.");
            return;
        }
        final boolean anon = anonymousCheck.isSelected();
        final String user = anon ? "anonymous" : userField.getText().trim();
        final String pass = anon ? "anonymous@example.com" : passField.getText();

        busy = true;
        updateButtons();
        setStatus("Connecting to " + host + ":" + port + " ...");

        Thread th = new Thread(() -> {
            FTPClient c = new FTPClient();
            try {
                c.connect(host, port);
                c.login(user, pass);
                String pwd = c.pwd();
                List<FileItem> items = fetchRemoteItems(c);
                Platform.runLater(() -> {
                    client = c;
                    connected = true;
                    remotePathLabel.setText(pwd);
                    remoteTable.setItems(FXCollections.observableArrayList(items));
                    setStatus("Connected to " + host);
                    log("--- connected ---");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    client = null;
                    connected = false;
                    log("Connect failed: " + ex.getMessage());
                    setStatus("Connection failed");
                });
            } finally {
                Platform.runLater(() -> { busy = false; updateButtons(); });
            }
        });
        th.setDaemon(true);
        th.start();
    }

    private void doDisconnect() {
        if (!connected || busy) return;
        busy = true;
        updateButtons();
        setStatus("Disconnecting ...");
        final FTPClient c = client;
        Thread th = new Thread(() -> {
            try { if (c != null) c.quit(); }
            catch (Exception ignored) { }
            Platform.runLater(() -> {
                client = null;
                connected = false;
                busy = false;
                remoteTable.getItems().clear();
                remotePathLabel.setText("/");
                setStatus("Disconnected");
                log("--- disconnected ---");
                updateButtons();
            });
        });
        th.setDaemon(true);
        th.start();
    }

    private void refreshRemote() {
        runAsync(() -> {
            String pwd = client.pwd();
            List<FileItem> items = fetchRemoteItems(client);
            Platform.runLater(() -> {
                remotePathLabel.setText(pwd);
                remoteTable.setItems(FXCollections.observableArrayList(items));
            });
        }, "Listing remote directory ...");
    }

    private void navigateRemote(String name) {
        runAsync(() -> {
            client.cd(name);
            String pwd = client.pwd();
            List<FileItem> items = fetchRemoteItems(client);
            Platform.runLater(() -> {
                remotePathLabel.setText(pwd);
                remoteTable.setItems(FXCollections.observableArrayList(items));
            });
        }, "Changing directory ...");
    }

    private void doCreateFolder() {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Create folder");
        dlg.setHeaderText("Create a new folder on the server");
        dlg.setContentText("Folder name:");
        Optional<String> r = dlg.showAndWait();
        if (!r.isPresent() || r.get().trim().isEmpty()) return;
        final String name = r.get().trim();
        runAsync(() -> {
            client.mkdir(name);
            String pwd = client.pwd();
            List<FileItem> items = fetchRemoteItems(client);
            Platform.runLater(() -> {
                remotePathLabel.setText(pwd);
                remoteTable.setItems(FXCollections.observableArrayList(items));
                log("Created folder: " + name);
            });
        }, "Creating folder ...");
    }

    private void doDeleteRemote() {
        FileItem sel = remoteTable.getSelectionModel().getSelectedItem();
        if (sel == null) { log("Select a remote item to delete."); return; }
        if ("..".equals(sel.getRawName())) return;
        final String name = sel.getRawName();
        final boolean dir = sel.isDir();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + (dir ? "directory" : "file") + " \"" + name + "\"?",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        Optional<ButtonType> res = confirm.showAndWait();
        if (!res.isPresent() || res.get() != ButtonType.OK) return;

        runAsync(() -> {
            if (dir) client.rmdir(name); else client.delete(name);
            String pwd = client.pwd();
            List<FileItem> items = fetchRemoteItems(client);
            Platform.runLater(() -> {
                remotePathLabel.setText(pwd);
                remoteTable.setItems(FXCollections.observableArrayList(items));
                log("Deleted: " + name);
            });
        }, "Deleting " + name + " ...");
    }

    private void doDownload() {
        FileItem sel = remoteTable.getSelectionModel().getSelectedItem();
        if (sel == null || sel.isDir()) { log("Select a remote file to download."); return; }
        final String remote = sel.getRawName();
        final File local = new File(currentLocalDir, remote);
        runAsync(() -> {
            client.get(remote, local.getAbsolutePath());
            Platform.runLater(() -> {
                log("Downloaded " + remote + "  ->  " + local.getAbsolutePath());
                refreshLocal();
            });
        }, "Downloading " + remote + " ...");
    }

    private void doUpload() {
        FileItem sel = localTable.getSelectionModel().getSelectedItem();
        if (sel == null || sel.isDir()) { log("Select a local file to upload."); return; }
        final String name = sel.getRawName();
        final File local = new File(currentLocalDir, name);
        runAsync(() -> {
            client.put(local.getAbsolutePath(), name);
            String pwd = client.pwd();
            List<FileItem> items = fetchRemoteItems(client);
            Platform.runLater(() -> {
                remotePathLabel.setText(pwd);
                remoteTable.setItems(FXCollections.observableArrayList(items));
                log("Uploaded " + name);
            });
        }, "Uploading " + name + " ...");
    }

    private void navigateLocal(String name) {
        File target = "..".equals(name)
                ? currentLocalDir.getParentFile()
                : new File(currentLocalDir, name);
        if (target != null && target.isDirectory()) {
            currentLocalDir = target;
            refreshLocal();
        }
    }

    private void refreshLocal() {
        File dir = currentLocalDir;
        localPathLabel.setText(dir.getAbsolutePath());
        List<FileItem> items = new ArrayList<>();
        if (dir.getParentFile() != null) {
            items.add(new FileItem("..", "", "", true));
        }
        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            for (File f : files) {
                items.add(new FileItem(
                        f.getName(),
                        f.isDirectory() ? "" : formatSize(f.length()),
                        new Date(f.lastModified()).toString(),
                        f.isDirectory()));
            }
        }
        localTable.setItems(FXCollections.observableArrayList(items));
    }

    // Run an FTP call off the UI thread so the window stays responsive.
    private void runAsync(FtpTask task, String statusMsg) {
        if (!connected || busy || client == null) {
            if (!connected) log("Not connected.");
            return;
        }
        busy = true;
        updateButtons();
        setStatus(statusMsg);

        Thread th = new Thread(() -> {
            try {
                task.run();
                Platform.runLater(() -> setStatus("Ready"));
            } catch (FTPException e) {
                Platform.runLater(() -> { log("Server refused: " + e.getMessage()); setStatus("Error"); });
            } catch (IOException e) {
                Platform.runLater(() -> { log("I/O error: " + e.getMessage()); setStatus("Error"); });
            } catch (Exception e) {
                Platform.runLater(() -> { log("Error: " + e.getMessage()); setStatus("Error"); });
            } finally {
                Platform.runLater(() -> { busy = false; updateButtons(); });
            }
        });
        th.setDaemon(true);
        th.start();
    }

    private List<FileItem> fetchRemoteItems(FTPClient c) throws IOException, FTPException {
        String listing = c.ls();
        List<FileItem> items = new ArrayList<>();
        items.add(new FileItem("..", "", "", true));
        for (String line : listing.split("\n")) {
            FileItem fi = parseListLine(line);
            if (fi != null) items.add(fi);
        }
        return items;
    }

    // Parse one Unix-style LIST line:
    //   drwxrwxrwx 1 owner group   0 May 29 16:15 test_dir
    private FileItem parseListLine(String line) {
        if (line == null) return null;
        line = line.trim();
        if (line.isEmpty() || line.startsWith("total")) return null;

        String[] t = line.split("\\s+");
        boolean unixFormat = t.length >= 9
                && (t[0].length() == 10 || t[0].length() == 11)
                && (t[0].charAt(0) == '-' || t[0].charAt(0) == 'd' || t[0].charAt(0) == 'l');

        if (unixFormat) {
            boolean dir = t[0].charAt(0) == 'd';
            String size = t[4];
            String date = t[5] + " " + t[6] + " " + t[7];
            StringBuilder nb = new StringBuilder();
            for (int i = 8; i < t.length; i++) {
                if (i > 8) nb.append(" ");
                nb.append(t[i]);
            }
            return new FileItem(nb.toString(), size, date, dir);
        }
        return new FileItem(line, "", "", false);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return (bytes / (1024L * 1024 * 1024)) + " GB";
    }

    private void updateButtons() {
        boolean canOp = connected && !busy;
        connectBtn.setDisable(connected || busy);
        disconnectBtn.setDisable(!connected || busy);

        refreshBtn.setDisable(!canOp);
        mkdirBtn.setDisable(!canOp);
        deleteBtn.setDisable(!canOp);
        downloadBtn.setDisable(!canOp);
        uploadBtn.setDisable(!canOp);

        boolean lockFields = connected || busy;
        hostField.setDisable(lockFields);
        portField.setDisable(lockFields);
        anonymousCheck.setDisable(lockFields);
        boolean lockCreds = lockFields || anonymousCheck.isSelected();
        userField.setDisable(lockCreds);
        passField.setDisable(lockCreds);
    }

    private void setStatus(String msg) { statusLabel.setText(msg); }

    private void log(String msg) {
        if (Platform.isFxApplicationThread()) {
            logArea.appendText(msg + "\n");
        } else {
            Platform.runLater(() -> logArea.appendText(msg + "\n"));
        }
    }

    // Send FTPClient's System.out trace to the log pane.
    private void redirectConsoleToLog() {
        OutputStream sink = new OutputStream() {
            private final StringBuilder buf = new StringBuilder();
            @Override public synchronized void write(int b) {
                if (b == '\r') return;
                if (b == '\n') {
                    final String line = buf.toString();
                    buf.setLength(0);
                    Platform.runLater(() -> logArea.appendText(line + "\n"));
                } else {
                    buf.append((char) b);
                }
            }
        };
        System.setOut(new PrintStream(sink, true));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
