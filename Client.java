import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class Client {
    private Socket requestSocket;           //socket connect to the server
    private ObjectOutputStream out;         //stream write to the socket
    private ObjectInputStream in;          //stream read from the socket
    private Path currentRelativePath = Paths.get("");
    private String currentDir = currentRelativePath.toAbsolutePath().toString() + "/";

    public static void main(String[] args) {
        String host = "localhost";
        int sPort = 8000;
        if (args.length < 2) {
            System.out.println("Command line arguments were not provided. Setting to default values: localhost,8000");
        } else {
            host = args[0];
            sPort = Integer.parseInt(args[1]);
        }
        Client client = new Client();
        client.run(host, sPort);
    }

    private void run(String host, int port) {
        String input;
        try {
            requestSocket = new Socket(host, port);
            System.out.println("Connected to " + host + " in port " + port);
            out = new ObjectOutputStream(requestSocket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(requestSocket.getInputStream());

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                System.out.println("#################");
                System.out.print("Enter the FTP command: ");
                input = bufferedReader.readLine();

                String[] cmd = input.split(" ");

                if (cmd.length < 1) {
                    continue;
                } else if (cmd[0].equals("ftpclient")) {
                    if (cmd.length != 3) {
                        System.out.println("invalid arguments for command: ftpclient <ip> <port>");
                    } else {
                        try {
                            host = cmd[1];
                            port = Integer.parseInt(cmd[2]);
                            System.out.println("Closing existing connection!!");
                            in.close();
                            out.close();
                            requestSocket.close();
                            System.out.println("Trying to make new connection");
                            requestSocket = new Socket(host, port);
                            System.out.println("Connected to " + host + " in port " + port);
                            out = new ObjectOutputStream(requestSocket.getOutputStream());
                            out.flush();
                            in = new ObjectInputStream(requestSocket.getInputStream());
                            bufferedReader = new BufferedReader(new InputStreamReader(System.in));
                        } catch (Exception e) {
                            System.out.println("error while coonecting to the server:" + e.getMessage());
                        }
                    }
                    continue;
                } else if (cmd[0].equals("exit")) {
                    sendMessage(cmd[0]);
                    System.exit(0);
                } else if (cmd[0].equals("upload")) {
                    if (cmd.length != 2) {
                        System.out.println("invalid arguments for command: upload <file>");
                        continue;
                    }
                    sendMessage(input);
                    String msg = sendFile(cmd[1]);
                    System.out.println("Server Reply: " + msg);
                    continue;
                } else if (cmd[0].equals("move")) {
                    if (cmd.length != 2) {
                        System.out.println("invalid arguments for command: move <file>");
                        continue;
                    }
                    sendMessage("upload " + cmd[1]);
                    moveFile(cmd[1]);
                    System.out.println("Server Reply: " + readMessage());
                    continue;
                } else if (cmd[0].equals("get")) {
                    if (cmd.length < 2) {
                        System.out.println("invalid arguments for command: get <file>");
                        continue;
                    }
                    sendMessage(input);
                    String msg = receiveFile(cmd[1]);
                    System.out.println("Server says: " + msg);
                    continue;
                }
                //Send the sentence to the server
                sendMessage(input);
                //Receive the upperCase sentence from the server
                System.out.println("Server Reply: " + readMessage());
            }
        } catch (ConnectException e) {
            System.err.println("Connection refused. You need to initiate a server first.");
            System.exit(0);
        } catch (UnknownHostException unknownHost) {
            System.err.println("You are trying to connect to an unknown host!");
            System.exit(0);
        } catch (IOException ioException) {
            System.err.println("Cannot connect with the server: " + ioException.getMessage());
            System.exit(0);
        } finally {
            //Close connections
            try {
                in.close();
                out.close();
                requestSocket.close();
            } catch (IOException ioException) {
                System.out.println("Unable to close the connection gracefully: " + ioException.getMessage());
            }
        }
    }

    //send a message to the output stream
    private void sendMessage(String msg) {
        try {
            out.writeObject(msg);
            out.flush();
            System.out.println(">>>> " + msg);
        } catch (Exception e) {
            System.err.println("Cannot connect with the server: " + e.getMessage());
            System.exit(0);
        }
    }

    private String readMessage() {
        String str = "";
        try {
            str = (String) in.readObject();
        } catch (Exception e) {
            System.out.println("Unable to read message from the server:" + e.getMessage());
        }
        return str;
    }

    private String sendFile(String filename) {
        try {
            String msg = readMessage();
            if (!msg.equals("ok")) {
                return msg;
            }
            Path path = Paths.get(currentDir + filename);
            Files.copy(path, out);
            out.flush();
            out.writeObject("send_complete");
            return readMessage();
        } catch (Exception e) {
            System.out.println("Exception occurred while sending file to the server:" + e.getMessage());
            sendMessage(System.lineSeparator());
            return readMessage();
        }
    }

    private void moveFile(String filename) {
        try {
            String msg = readMessage();
            if (!msg.equals("ok")) {
                System.out.println(msg);
                return;
            }
            Path path = Paths.get(currentDir + filename);
            Files.copy(path, out);
            out.flush();
            out.writeObject("send_complete");
            deleteFile(filename);
        } catch (Exception e) {
            System.out.println("Exception occurred while sending file to the server:" + e.getMessage());
            sendMessage(System.lineSeparator());
        }
    }

    private String receiveFile(String filename) {
        try {
            String msg = readMessage();
            if (!msg.equals("ok")) {
                return msg;
            }
            Path path = Paths.get(currentDir + filename);
            Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            if (readMessage().equals("send_success")) {
                System.out.println("file was received from the server successfully");
            } else {
                System.out.println("unable to fetch the file from the server");
                System.out.println("Server says: " + readMessage());
                deleteFile(filename);
            }
            return readMessage();
        } catch (Exception e) {
            System.out.println("Exception occurred while receiving file from the server:" + e.getMessage());
            return readMessage();
        }
    }

    private String deleteFile(String filename) {
        try {
            Path path = Paths.get(currentDir + filename);
            Files.delete(path);
        } catch (Exception e) {
            System.out.println("Exception occurred while deleting file: " + e.getMessage());
            return "error while deleting file";
        }
        return "file was removed successfully";
    }
}
