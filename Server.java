import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.net.InetAddress;

public class Server {
    ServerSocket sSocket;
    int threadIndex;
    Map<String, String> userDb;
    Path currentRelativePath = Paths.get("");
    String currentDir = currentRelativePath.toAbsolutePath().toString() + "/";

    public static void main(String[] args) {
        String host = "localhost";
        int sPort = 8000;
        if (args.length < 2) {
            System.out.println("Command line arguements were not provided. Setting to default values: localhost,8000");
        } else {
            host = args[0];
            sPort = Integer.parseInt(args[1]);
        }
        Server server = new Server();
        server.init();
        System.out.println("starting server in address:" + host + " port:" + sPort);
        server.run(host,sPort);
    }

    void init() {
        userDb = new HashMap<>();
        userDb.put("u1", "p1");
        userDb.put("u2", "p2");
        userDb.put("u3", "p3");
    }

    void run(String host, int sPort) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            sSocket = new ServerSocket(sPort, 10, addr);
            while (true) {
                Thread.sleep(500);
                System.out.println("waiting for new connection..");
                Socket skt = sSocket.accept();
                threadIndex++;
                System.out.println("connection accepted!");
                FtpServer ftpServer = new FtpServer(skt, threadIndex);
                ftpServer.start();
            }
        } catch (IOException ioException) {
            System.out.println("Failed to start the server:" + ioException.getMessage());
        } catch (InterruptedException ie) {
            System.out.println("Failed to start the server:" + ie.getMessage());
        } finally {
            try {
                sSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


    class FtpServer extends Thread {
        Socket connection;   //serversocket used to listen on port number 8000
        int threadIndex;
        ObjectOutputStream out;  //stream write to the socket
        ObjectInputStream in;    //stream read from the socket
        boolean isAuthenticated;    //stream read from the socket

        public FtpServer(Socket connection, int threadIndex) {
            this.connection = connection;
            this.threadIndex = threadIndex;
        }

        public void run() {
            System.out.println("client index: " + threadIndex);

            try {
                out = new ObjectOutputStream(connection.getOutputStream());
                out.flush();
                in = new ObjectInputStream(connection.getInputStream());
                while (true) {
                    String message = readMessage();
                    if (message.equals("")) {
                        return;
                    } else if (message.equals("exit")) {
                        System.out.println("client-" + threadIndex + " is closing connction");
                    }
                    System.out.println("*****" + message + "******");
                    System.out.println("___________________" + threadIndex);
                    System.out.println("client-" + threadIndex + " request: " + message);
                    String output = processCmd(message);
                    sendMessage(output);
                    System.out.println("client- " + threadIndex + " response: " + output);
                    System.out.println("+++++++++++++++++++" + threadIndex);
                }
            } catch (IOException e) {
                System.out.println("Connection error for client-" + threadIndex + ": " + e.getMessage());
            } finally {
                try {
                    in.close();
                    out.close();
                    connection.close();
                } catch (IOException e) {
                }
            }
        }

        private String processCmd(String message) {
            String[] cmd = message.split(" ");
            if (cmd.length == 0) {
                return "invalid command !!";
            }
            switch (cmd[0]) {
                case "auth":
                    if (cmd.length != 3) {
                        return "invalid arguments: auth <usrname> <password>";
                    }
                    if (userDb.containsKey(cmd[1]) && userDb.get(cmd[1]).equals(cmd[2])) {
                        isAuthenticated = true;
                        return "authentication successfull!!";
                    } else {
                        return "authentication failed: invalid credentials";
                    }
                case "dir":
                    if (!isAuthenticated) {
                        return "please authenticate before proceeding";
                    }
                    return listFiles();
                case "upload":
                    if (!isAuthenticated) {
                        return "please authenticate before proceeding";
                    } else if (cmd.length != 2) {
                        return "invalid arguments: upload <file>";
                    }
                    sendMessage("ok");
                    return recieveFile(cmd[1]);
                case "del":
                    if (!isAuthenticated) {
                        return "please authenticate before proceeding";
                    }
                    if (cmd.length != 2) {
                        return "invalid arguments: del <file>";
                    }
                    return deleteFile(cmd[1]);
                case "get":
                    if (!isAuthenticated) {
                        return "please authenticate before proceeding";
                    } else if (cmd.length != 2) {
                        return "invalid arguments: get <file>";
                    }
                    sendMessage("ok");
                    return sendFile(cmd[1]);
                default:
                    return "specified command not found; available commands are: auth,get,upload,del,dir,ftpclient";
            }
        }

        private void sendMessage(String msg) {
            try {
                out.writeObject(msg);
                out.flush();
            } catch (Exception e) {
                System.out.println("Exception occurred while sending meesage to the client" + threadIndex + ": " + e.getMessage());
            }
        }

        public String sendFile(String filename) {
            try {
                Path path = Paths.get(currentDir + filename);
                Files.copy(path, out);
                out.flush();
                sendMessage("send_success");
                return "send comleted";
            } catch (Exception e) {
                sendMessage(System.lineSeparator());
                sendMessage("failed to send the file");
                System.out.println("Exception occurred while sending file to the client" + threadIndex + ": " + e.getMessage());
                return "send failed";
            }
        }

        public String recieveFile(String filename) {
            try {
                Path path = Paths.get(currentDir + filename);
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
                if (readMessage().equals("send_complete")) {
                    System.out.println("file from client-" + threadIndex + " recieved successfully");
                    return "file recieved successfully";
                } else {
                    System.out.println("file from client-" + threadIndex + " was corrupted");
                    deleteFile(filename);
                    return "failed to recieve the file";
                }
            } catch (Exception e) {
                System.out.println("Error occured while recieving file from client-" + threadIndex + ": " + e.getMessage());
                return "failed to recieve the file";
            }
        }

        public String deleteFile(String filename) {
            try {
                Path path = Paths.get(currentDir + filename);
                Files.delete(path);
            } catch (Exception e) {
                System.out.println("Exception occurred while deleting file: " + e.getMessage());
                return "error while deleting file";
            }
            return "file was removed successfully";
        }


        private String readMessage() {
            String str = "";
            try {
                str = (String) in.readObject();
            } catch (ClassNotFoundException ce) {
                System.err.println("Unable to parse message from the client:" + threadIndex + ": " + ce.getMessage());
            } catch (SocketException e) {
                System.err.println("Connection error with client-" + threadIndex + ": " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Unable to read message from the client:" + threadIndex + ": " + e.getMessage());
            }
            return str;
        }
    }

    private String listFiles() {
        String output = "";
        File[] filesList = new File(currentDir).listFiles();
        for (File f : filesList) {
            if (f.isDirectory())
                output += "\n" + f.getName() + "/";
            if (f.isFile()) {
                output += "\n" + f.getName();
            }
        }

        if (output.equals("")) {
            return "***  empty ****";
        }
        return output;
    }
}
