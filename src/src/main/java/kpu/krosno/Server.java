package kpu.krosno;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

class Connection {
    public String connectionName;
    public String startPath;
    public String currentPath = "";

    public FileInputStream threadFileInputStream = null;
    public File file = null;
    public byte[] fileData = null;
    public long fileSize = -1; // Bytes
    public long currentPacket = -1;
    public long numberOfPackets = -1;
    public String streamState = "off"; // off, download or upload

    public Connection(String connectionName, String startPath) {
        this.connectionName = connectionName;
        this.startPath = startPath;
    }

    public void disconnect()
    {
        if(this.threadFileInputStream != null)
        {
            try {
                this.threadFileInputStream.close();
            }
            catch (IOException exception) {}
            this.threadFileInputStream = null;
        }
        if(this.file != null && this.streamState.equals("upload") && this.currentPacket + 1 < this.numberOfPackets)
            this.file.delete();
        this.file = null;
        System.out.println("SERWER-MSG(THREAD:" + this.connectionName + "): Zakończono połączenie z klientem.");
    }

}

class ServerThread implements Runnable
{
    Thread thread;
    String threadName;
    Connection threadConnection;
    ArrayList<Connection> connections;
    DatagramSocket threadSocket;
    DatagramPacket receiveDatagram;
    String startPath;
    boolean foundFlag;
    String lastMsg;

    ServerThread (String threadName, ArrayList<Connection> connections, DatagramPacket receiveDatagram, String startPath) throws SocketException
    {
        this.threadName = threadName;
        this.connections = connections;
        this.receiveDatagram = receiveDatagram;
        this.threadSocket = new DatagramSocket();
        this.startPath = startPath;
        this.foundFlag = false;
        this.lastMsg = Config.stringFromDatagram(receiveDatagram);
        this.thread = new Thread(this, threadName);
        this.thread.start();
    }

    public void run()
    {
        if(!connections.isEmpty())
        {
            for(int i = 0; i < connections.size(); i++)
            {
                if(this.threadName.equals(connections.get(i).connectionName))
                {
                    this.threadConnection = connections.get(i);
                    this.foundFlag = true;
                    break;
                }
            }
        }

        if(!this.foundFlag && connections.size() > Config.MAX_HOST)
        {
            String msg = "SERVER_FULL";
            DatagramPacket sendDatagram = new DatagramPacket(msg.getBytes(StandardCharsets.UTF_8), msg.getBytes(StandardCharsets.UTF_8).length, this.receiveDatagram.getSocketAddress());
            try
            {
                this.threadSocket.send(sendDatagram);
                System.out.println("SERWER-MSG(THREAD:" + this.threadName + "): Odrzucono połączenie z klientem z powodu braku miejsca w kolejce.");
            } catch (IOException ignored) {}
            this.threadSocket.close();
        }

        if(this.lastMsg.equals("CONNECT"))
        {
            String msg = "ALREADY_CONNECTED";
            if(!this.foundFlag)
            {
                msg = "CONNECTED";
                this.threadConnection = new Connection(this.threadName, startPath);
                connections.add(this.threadConnection);
                this.foundFlag = true;
            }
            DatagramPacket sendDatagram = new DatagramPacket(msg.getBytes(StandardCharsets.UTF_8), msg.getBytes(StandardCharsets.UTF_8).length, this.receiveDatagram.getSocketAddress());
            try
            {
                this.threadSocket.send(sendDatagram);
                System.out.println("SERWER-MSG(THREAD:" + this.threadName + "): Nawiązano nowe połączenie z klientem.");
            } catch (IOException ignored) {}
            this.threadSocket.close();
        }
        if(this.lastMsg.equals("DISCONNECT"))
        {
            if(this.foundFlag)
            {
                this.threadConnection.disconnect();
                this.connections.remove(this.threadConnection);
            }
            this.threadSocket.close();
        }
        else if(this.lastMsg.equals("STATUS"))
        {
            String msg = "NOT_CONNECTED";
            if(this.foundFlag)
            {
                msg = "CONNECTED";
            }
            DatagramPacket sendDatagram = new DatagramPacket(msg.getBytes(StandardCharsets.UTF_8), msg.getBytes(StandardCharsets.UTF_8).length, this.receiveDatagram.getSocketAddress());
            try
            {
                this.threadSocket.send(sendDatagram);
                System.out.println("SERWER-MSG(THREAD:" + this.threadName + "): Wysłano do klienta informację na temat statusu połączenia.");
            } catch (IOException ignored) {}
            this.threadSocket.close();
        }
        else if(this.lastMsg.equals("SDIR"))
        {
            if (this.foundFlag)
            {
                File dirname = new File(Path.of(this.threadConnection.startPath, this.threadConnection.currentPath).toString());
                File files[] = dirname.listFiles();
                StringBuilder stringBuilder = new StringBuilder();
                if(files.length < 1)
                    stringBuilder.append("Brak plików.");
                else
                {
                    for(int i = 0; i < files.length; i ++)
                    {
                        stringBuilder.append(files[i].getName());
                        if(files[i].isDirectory())
                            stringBuilder.append("\tkatalog");
                        stringBuilder.append("\t" + files[i].length() + "B");
                        if(!files[i].canRead())
                            stringBuilder.append("\tCan't read");
                        if(i != files.length - 1)
                            stringBuilder.append("\n");
                    }
                    String msg = stringBuilder.toString();
                    DatagramPacket sendDatagram = new DatagramPacket(msg.getBytes(StandardCharsets.UTF_8), msg.getBytes(StandardCharsets.UTF_8).length, this.receiveDatagram.getSocketAddress());
                    try
                    {
                        this.threadSocket.send(sendDatagram);
                        System.out.println("SERWER-MSG(THREAD:" + this.threadName + "): Wysłano listę plików do klienta.");
                    } catch (IOException ignored) {}
                    this.threadSocket.close();
                }
            }
        }
        else if(this.lastMsg.matches("DOWNLOAD .*"))
        {
            if(this.foundFlag)
            {
                String fileName = Config.stringFromDatagram(this.receiveDatagram);
                fileName = fileName.substring(fileName.indexOf(' ') + 1);
                this.threadConnection.file = new File(Path.of(Path.of(this.threadConnection.startPath, this.threadConnection.currentPath).toString(), fileName).toString());

                String msg = "";
                if(!this.threadConnection.file.exists())
                {
                    this.threadConnection.file = null;
                    this.threadConnection.currentPacket = -1;
                    this.threadConnection.numberOfPackets = -1;
                    this.threadConnection.fileSize = -1;
                    this.threadConnection.streamState = "off";
                    msg = "NOT_EXIST";
                    System.out.println("SERWER-MSG(THREAD:" + this.threadName + "): Odmowa wysłania pliku(Plik nie istnieje): " + fileName);
                }
                else if(this.threadConnection.file.isDirectory())
                {
                    this.threadConnection.file = null;
                    this.threadConnection.currentPacket = -1;
                    this.threadConnection.numberOfPackets = -1;
                    this.threadConnection.fileSize = -1;
                    this.threadConnection.streamState = "off";
                    msg = "DIRECTORY";
                    System.out.println("SERWER-MSG(THREAD:" + this.threadName + "): Odmowa wysłania pliku(Plik jest katalogiem): " + fileName);
                }
                else if(!this.threadConnection.file.canRead())
                {
                    this.threadConnection.file = null;
                    this.threadConnection.currentPacket = -1;
                    this.threadConnection.numberOfPackets = -1;
                    this.threadConnection.fileSize = -1;
                    this.threadConnection.streamState = "off";
                    msg = "CAN'T_READ";
                    System.out.println("SERWER-MSG(THREAD:" + this.threadName + "): Odmowa wysłania pliku(Brak uprawnień odczytu pliku przez serwer): " + fileName);
                }
                else
                {
                    this.threadConnection.currentPacket = -1;
                    this.threadConnection.numberOfPackets = (long) Math.ceil(this.threadConnection.file.length() / (double)Config.BUFFER_SIZE);
                    this.threadConnection.fileSize = this.threadConnection.file.length();
                    this.threadConnection.streamState = "download";
                    msg = "0 " + this.threadConnection.numberOfPackets + " " + this.threadConnection.fileSize;
                }
                DatagramPacket sendDatagram = new DatagramPacket(msg.getBytes(StandardCharsets.UTF_8), msg.getBytes(StandardCharsets.UTF_8).length, this.receiveDatagram.getSocketAddress());
                try
                {
                    this.threadSocket.send(sendDatagram);
                    this.threadConnection.threadFileInputStream = new FileInputStream(this.threadConnection.file);
                    System.out.println("SERWER-MSG(THREAD:" + this.threadName + "): Wysłanie informacji do klienta o rozmiarze wysyłanego pliku: " + fileName);
                    System.out.println("SERWER-MSG(THREAD:" + this.threadName + "): Rozpoczęcie wysyłania pliku o nazwie: " + fileName);
                } catch (IOException ignored) {}
                this.threadSocket.close();
            }
        }
        else if(this.lastMsg.equals("0") || this.lastMsg.equals("-1"))
        {
            if(this.foundFlag)
            {
                if(this.threadConnection.streamState == "download")
                {
                    if(this.lastMsg.equals("0"))
                        System.out.println("SERWER-MSG(THREAD:" + this.threadName + "): Plik: " + this.threadConnection.file.getName() + " został wysłany poprawnie.");
                    else if(this.lastMsg.equals("-1"))
                        System.out.println("SERWER-MSG(THREAD:" + this.threadName + "): Plik: " + this.threadConnection.file.getName() + " nie został wysłany poprawnie, błąd po stronie klienta.");
                    try
                    {
                        this.threadConnection.threadFileInputStream.close();
                        this.threadConnection.threadFileInputStream = null;
                    }
                    catch (IOException ignored) {}
                    this.threadConnection.file = null;
                    this.threadConnection.currentPacket = -1;
                    this.threadConnection.numberOfPackets = -1;
                    this.threadConnection.fileSize = -1;
                    this.threadConnection.streamState = "off";
                }
            }
        }
        else if(this.lastMsg.matches("^[0-9]+ [0-9]+ [0-9]+$"))
        {
            if(this.foundFlag)
            {
                try
                {
                    String tokens[] = lastMsg.split(" ");
                    long currentPacket = Long.parseLong(tokens[0]);
                    long numberOfPackets = Long.parseLong(tokens[1]);
                    long fileSize = Long.parseLong(tokens[2]);
                    int len = Config.BUFFER_SIZE;

                    if(currentPacket + 1 == numberOfPackets)
                    {
                        len = (int) (fileSize - (numberOfPackets - 1) * Config.BUFFER_SIZE);
                    }

                    if(this.threadConnection.currentPacket == -1)
                    {
                        this.threadConnection.fileData = new byte[len];
                        this.threadConnection.threadFileInputStream.read(this.threadConnection.fileData, 0 , len);
                        this.threadConnection.currentPacket = 0;
                    }

                    if(currentPacket > this.threadConnection.currentPacket)
                    {
                        this.threadConnection.fileData = new byte[len];
                        this.threadConnection.threadFileInputStream.read(this.threadConnection.fileData, 0 , len);
                        this.threadConnection.currentPacket = currentPacket;
                    }

                    DatagramPacket sendDatagram = new DatagramPacket(this.threadConnection.fileData, this.threadConnection.fileData.length, this.receiveDatagram.getSocketAddress());
                    this.threadSocket.send(sendDatagram);
                }
                catch (IOException ignored)
                { }
                this.threadSocket.close();
            }
        }
        else if(this.lastMsg.matches("UPLOAD .*"))
        {
            if(this.foundFlag)
            {
                this.threadConnection.streamState = "off";
                String fileName = Config.stringFromDatagram(this.receiveDatagram);
                fileName = fileName.substring(fileName.indexOf(' ') + 1);
                this.threadConnection.file = new File(Path.of(Path.of(this.threadConnection.startPath, this.threadConnection.currentPath).toString(), fileName).toString());
                String msg = "";
                FileOutputStream fileOutputStream = null;
                DatagramPacket sendDatagram = null;
                byte[] sendData = new byte[Config.MAX_BUFFER_SIZE];
                boolean errorFlag = true;
                try
                {
                    this.threadConnection.file.delete();
                    this.threadConnection.file.createNewFile();
                    if(!this.threadConnection.file.canWrite())
                        throw new IOException();
                    fileOutputStream = new FileOutputStream(this.threadConnection.file);
                    errorFlag = false;
                }
                catch (IOException exception)
                {
                    try
                    {
                        msg = "CAN'T_WRITE";
                        sendData = msg.getBytes(StandardCharsets.UTF_8);
                        sendDatagram = new DatagramPacket(sendData, sendData.length, this.receiveDatagram.getSocketAddress());
                        this.threadSocket.send(sendDatagram);
                        if(fileOutputStream != null)
                            fileOutputStream.close();
                        fileOutputStream = null;
                    }
                    catch (IOException subexception) { }
                    System.out.println("SERWER-MSG(THREAD:" + this.threadName + "): Nie można pobrać pliku: " + fileName + " od klienta -- odmowa dostępu.");
                }
                if(!errorFlag)
                {
                    try
                    {
                        msg = msg = "WAITING";
                        sendData = msg.getBytes(StandardCharsets.UTF_8);
                        sendDatagram = new DatagramPacket(sendData, sendData.length, this.receiveDatagram.getSocketAddress());
                        this.threadSocket.send(sendDatagram);
                        this.threadSocket.connect(this.receiveDatagram.getSocketAddress());

                        if(this.threadConnection.streamState.equals("off"))
                        {
                            errorFlag = true;
                            for (int i = 0; i < Config.TIMEOUT_TIMES; i++)
                            {
                                try
                                {
                                    this.threadSocket.receive(this.receiveDatagram);
                                    msg = Config.stringFromDatagram(this.receiveDatagram);
                                    if (msg.matches("^[0-9]+ [0-9]+ [0-9]+$"))
                                    {
                                        String tokens[] = msg.split(" ");
                                        this.threadConnection.currentPacket = 0;
                                        this.threadConnection.numberOfPackets = Long.parseLong(tokens[1]);
                                        this.threadConnection.fileSize = Long.parseLong(tokens[2]); // Bytes
                                        this.threadConnection.streamState = "upload";
                                        System.out.println("SERWER-MSG(THREAD:" + this.threadName + "): Rozpoczęcie pobierania pliku o nazwie: " + fileName);
                                        errorFlag = false;
                                        break;
                                    }
                                    this.threadSocket.send(sendDatagram);
                                }
                                catch (IOException exception) { }
                            }
                        }

                        if(!errorFlag)
                            while (true)
                            {
                                msg = this.threadConnection.currentPacket + " " + this.threadConnection.numberOfPackets + " " + this.threadConnection.fileSize;
                                sendData = msg.getBytes(StandardCharsets.UTF_8);
                                sendDatagram = new DatagramPacket(sendData, sendData.length);
                                byte[] receiveData = new byte[Config.BUFFER_SIZE];
                                this.receiveDatagram = new DatagramPacket(receiveData, receiveData.length);

                                errorFlag = true;
                                for (int i = 0; i < Config.TIMEOUT_TIMES * 2; i++)
                                {
                                    try
                                    {
                                        this.threadSocket.send(sendDatagram);
                                        this.threadSocket.receive(this.receiveDatagram);
                                        errorFlag = false;
                                        break;
                                    } catch (IOException exception) { }
                                }
                                if (errorFlag)
                                {
                                    if(this.threadConnection.file != null)
                                        this.threadConnection.file.delete();
                                    this.threadConnection.file = null;
                                    this.threadConnection.fileSize = -1; // Bytes
                                    this.threadConnection.currentPacket = -1;
                                    this.threadConnection.numberOfPackets = -1;
                                    this.threadConnection.streamState = "off";
                                    System.out.println("SERWER-MSG(THREAD:" + this.threadName + "): Wystąpił błąd podczas pobierania pliku: " + fileName + " -- klient nie odpowiada.");
                                    try
                                    {
                                        if(fileOutputStream != null)
                                            fileOutputStream.close();
                                        fileOutputStream = null;
                                    }
                                    catch (IOException ignored) {}
                                    break;
                                }
                                else
                                {
                                    int len = Config.BUFFER_SIZE;
                                    byte[] data = this.receiveDatagram.getData();
                                    if(this.threadConnection.currentPacket + 1 == this.threadConnection.numberOfPackets)
                                    {
                                        len = (int) (this.threadConnection.fileSize - (this.threadConnection.numberOfPackets - 1) * Config.BUFFER_SIZE);
                                        data = Arrays.copyOfRange(data, 0, len);
                                    }

                                    if(len != this.receiveDatagram.getLength())
                                        continue;

                                    try
                                    {
                                        fileOutputStream.write(data);
                                    }
                                    catch (IOException e) {
                                        try
                                        {
                                            msg = "-1";
                                            sendData = msg.getBytes(StandardCharsets.UTF_8);
                                            sendDatagram = new DatagramPacket(sendData, sendData.length);
                                            this.threadSocket.send(sendDatagram);
                                            this.threadSocket.receive(this.receiveDatagram);
                                            errorFlag = false;
                                            break;
                                        }
                                        catch (IOException ignored) { }
                                        if (this.threadConnection.file != null)
                                            this.threadConnection.file.delete();
                                        this.threadConnection.file = null;
                                        this.threadConnection.fileSize = -1; // Bytes
                                        this.threadConnection.currentPacket = -1;
                                        this.threadConnection.numberOfPackets = -1;
                                        this.threadConnection.streamState = "off";
                                        System.out.println("SERWER-MSG(THREAD:" + this.threadName + "): Wystąpił błąd podczas zapisu pobieranego pliku: " + fileName);
                                        break;
                                    }

                                    this.threadConnection.currentPacket++;
                                    if(this.threadConnection.currentPacket == this.threadConnection.numberOfPackets)
                                    {
                                        this.threadConnection.file = null;
                                        this.threadConnection.fileSize = -1; // Bytes
                                        this.threadConnection.currentPacket = -1;
                                        this.threadConnection.numberOfPackets = -1;
                                        this.threadConnection.streamState = "off";
                                        try
                                        {
                                            msg = "0";
                                            sendData = msg.getBytes(StandardCharsets.UTF_8);
                                            sendDatagram = new DatagramPacket(sendData, sendData.length);
                                            this.threadSocket.send(sendDatagram);
                                            this.threadSocket.receive(this.receiveDatagram);
                                            errorFlag = false;
                                            if(fileOutputStream != null)
                                                fileOutputStream.close();
                                            fileOutputStream = null;
                                            System.out.println("SERWER-MSG(THREAD:" + this.threadName + "): Pobieranie pliku o nazwie: " + fileName + " zakończyło się pomyślnie.");
                                            break;
                                        }
                                        catch (IOException subexception) { }
                                        break;
                                    }
                                }
                            }
                    }
                    catch (IOException ignored) {
                        this.threadSocket.disconnect();
                    }
                }
                this.threadSocket.close();
            }
        }
        System.gc();
    }
}

public class Server
{
    public static void main(String[] args) {
        String startPath = Paths.get("").toAbsolutePath().toString();
        DatagramSocket socket = null;
        byte[] receiveData;
        DatagramPacket receiveDatagram;
        ArrayList<Connection> connections = new ArrayList<Connection>();

        try {
            socket = new DatagramSocket(Config.PORT);
        }
        catch (SocketException exception)
        {
            System.out.println("SERWER-ERROR: Nie udało utworzyć się głównego gniazda UDP dla serwera.");
            System.out.println(exception.getMessage());
            System.exit(-1);
        }
        System.out.println("SERWER-MSG: Serwer został uruchomiony poprawnie.");

        while(true)
        {
            receiveData = new byte[Config.MAX_BUFFER_SIZE];
            receiveDatagram = new DatagramPacket(receiveData, receiveData.length);
            try {
                socket.receive(receiveDatagram);
                new ServerThread(receiveDatagram.getSocketAddress().toString(), connections, receiveDatagram, startPath);
            }
            catch (IOException exception)
            {
                System.out.println("SERWER-ERROR: Nie udało odebrać się wiadomości od klienta.");
                System.out.println(exception.getMessage());
            }
        }
    }
}
