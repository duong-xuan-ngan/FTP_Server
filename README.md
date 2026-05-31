# FTP Client — README

A Java FTP client built directly over TCP sockets, with both a command-line
interface and a JavaFX graphical front-end. Uses only `java.io`,
`java.net`, and `java.util` for the FTP protocol itself; JavaFX is used
only for the optional GUI.

## Project layout

```
FTP_Server/
├── src/src/                   all .java source files
│   ├── FTPClient.java         control connection + protocol logic
│   ├── FTPConnection.java     short-lived data connections
│   ├── FTPException.java     4xx/5xx server-error exception
│   ├── Main.java              CLI front-end (read-eval loop)
│   └── FTPClientGUI.java      JavaFX front-end (optional)
├── report/report.md           short architecture report
└── README.md
```

## Requirements

- **JDK 8+** for the CLI client
- **JavaFX SDK 21** (separate download) if you also want the GUI on JDK 11+.
  On JDK 8 JavaFX is bundled.

JavaFX SDK download: https://gluonhq.com/products/javafx/ — pick the
version matching your JDK, OS, and architecture, and unzip somewhere
permanent (e.g. `D:\javafx-sdk-21\lib`).

## Build

```bash
cd src/src
javac *.java
```

If `javac` complains about `javafx.*` imports, point it at the JavaFX
`lib` folder:

```bash
javac --module-path "D:\javafx-sdk-21\lib" --add-modules javafx.controls *.java
```

## Run — CLI

```bash
java Main [host] [port]
```

Defaults: `127.0.0.1` and `2121` (matches the local pyftpdlib test
server). After connecting, the program asks for a username and password,
then drops you into an `ftp>` prompt.

## Run — GUI (optional)

```bash
java --module-path "D:\javafx-sdk-21\lib" --add-modules javafx.controls FTPClientGUI
```

The window shows the remote listing on the left, the local listing on
the right, action buttons in the middle, and a live protocol log at the
bottom. Connection details are entered at the top.

## Supported commands

| Command                | FTP verb(s)         | Description                       |
| ---------------------- | ------------------- | --------------------------------- |
| `pwd`                  | `PWD`               | Print current remote directory    |
| `cd <path>`            | `CWD`               | Change remote directory           |
| `ls`                   | `PASV` + `LIST`     | List remote directory contents    |
| `get <remote> [local]` | `TYPE I` + `PASV` + `RETR` | Download a file (binary)   |
| `put <local> [remote]` | `TYPE I` + `PASV` + `STOR` | Upload a file (binary)     |
| `delete <path>`        | `DELE`              | Delete a remote file              |
| `mkdir <path>`         | `MKD`               | Create a remote directory         |
| `rmdir <path>`         | `RMD`               | Remove an empty remote directory  |
| `quit` / `exit`        | `QUIT`              | Close session and exit            |
| `help`                 | —                   | Show the command list             |

## Testing against a local server

The defaults assume a local [pyftpdlib](https://pypi.org/project/pyftpdlib/)
server:

```bash
python -m pyftpdlib -p 2121 -u test -P test -d D:\ftp_root -w
```

This starts an FTP server on port 2121 with username `test`, password
`test`, serving `D:\ftp_root` with write access.

## Example session (CLI) — ftp.gnu.org

```
$ java Main ftp.gnu.org 21
220 GNU FTP server ready.
Username: anonymous
Password: student@example.com
> USER anonymous
230-NOTICE (Updated October 15 2021):
230-
230-If you maintain scripts used to access ftp.gnu.org over FTP,
230-we strongly encourage you to change them to use HTTPS instead.
230- ...
230 Login successful.
> PASS ********
230 Already logged in.
ftp> pwd
> PWD
257 "/" is the current directory
/
ftp> ls
> PASV
227 Entering Passive Mode (209,51,188,20,102,252).
> LIST
150 Here comes the directory listing.
226 Directory send OK.
lrwxrwxrwx    1 0        0               8 Aug 20  2004 CRYPTO.README -> .message
-rw-r--r--    1 0        0           17864 Oct 23  2003 MISSING-FILES
-rw-r--r--    1 0        0            2814 Dec 31 09:18 README
drwxrwxr-x  326 0        3003        12288 Jan 21 19:58 gnu
drwxrwxr-x    3 0        3003         4096 Mar 10  2011 gnu+linux-distros
drwxr-xr-x    3 0        0            4096 Apr 20  2005 mirrors
drwxr-xr-x   99 0        0            4096 May 08  2023 old-gnu
...
ftp> cd gnu
> CWD gnu
250-If you have problems downloading and are seeing "Access denied" ...
250 Directory successfully changed.
Directory changed.
ftp> quit
> QUIT
221 Goodbye.
Session closed.
```

Two protocol details worth noting from this real session:

- **Anonymous auto-login:** `ftp.gnu.org` accepted `USER anonymous` with a
  `230` (success) immediately — before seeing the password. The client still
  sent `PASS`, and the server replied `230 Already logged in.` Because
  `readReply()` accepts any code below 400 without special-casing, this
  non-standard flow was handled correctly with no code changes.

- **Multiline replies:** both the login notice (`230-...` / `230 Login
  successful.`) and the `cd gnu` response (`250-...` / `250 Directory
  successfully changed.`) are multiline FTP replies. `readReply()` consumed
  them correctly, waiting for the closing `code + " "` line before returning.
