package kpu.krosno;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class Client
{
    public static void main(String[] args)
    {
        // Deklaracja i inicjalizacja zmiennych globalnych
        String state = "welcome";
        String command = "";
        String startPath = Paths.get("").toAbsolutePath().toString();
        String currentPath = startPath;

        DatagramSocket socket = null;
        InetSocketAddress address = null;
        byte[] sendData, receiveData;
        DatagramPacket sendDatagram, receiveDatagram;
        boolean connectionState = false;
        String currentIp = "";

        FileOutputStream fileOutputStream = null;
        File file = null;
        long currentPacket = -1;
        long numberOfPackets = -1;
        long fileSize = -1;  // Bytes
        String streamState = "off"; // off, download or upload

        while(true)
        {
            // Wywołanie garbage colectora
            System.gc();
            switch(state)
            {
                case "welcome":
                    System.out.print("Witaj w aplikacji, aby zobaczyć listę dostępnych komend wpisz help.");
                    state = "options";
                    break;

                case "options":
                    System.out.print("\nWpisz komende: ");
                    command = Config.scanner.nextLine();

                    if (command.equals("help"))
                    state = "help";
                    else if(command.equals("dir"))
                        state = "dir";
                    else if(command.equals("pwd"))
                        state = "pwd";
                    else if(command.matches("^cd .*$"))
                        state = "cd";
                    else if(command.matches("^path .*$"))
                        state = "path";
                    else if(command.matches("^connect .*$"))
                        state = "connect";
                    else if(command.equals("address"))
                        state = "address";
                    else if(command.equals("status"))
                        state = "status";
                    else if(command.equals("sdir"))
                        state = "sdir";
                    else if(command.matches("^download .*$"))
                        state = "download";
                    else if(command.matches("^upload .*$"))
                        state = "upload";
                    else if (command.equals("exit"))
                        state = "exit";
                    else if (command.equals("help help"))
                        System.out.print("Wyświetla listę dostępnych komend.");
                    else if (command.equals("help dir"))
                        System.out.print("Wyświetla listę plików i katalogów w aktualnej ścieżce, a także informacje czy dany plik jest katalogiem.");
                    else if (command.equals("help pwd"))
                        System.out.print("Wyświetla ścieżkę aktualnego katalogu roboczego, do którego będą pobierane pliki z serwera lub z którego pliki można wysłać na serwer.");
                    else if (command.equals("help cd"))
                    {
                        System.out.println("cd .. -- polecenie to przechodzi do katalogu nadrzednego jeśli to możliwe");
                        System.out.println("cd ~ -- polecenie to przechodzi do startowego katalogu roboczego");
                        System.out.print("cd 'nazwa_katalogu' -- polecenie to przechodzi do podkatalogu o podanej nazwie.");
                    }
                    else if (command.equals("help path"))
                        System.out.print("path 'nazwa_sciezki' -- przechodzi do podanej sciezki, jesli jest ona katalogiem");
                    else if (command.equals("help connect"))
                        System.out.print("'adres_ip_serwera' -- próbuje nawiązać połączenie z serwerem o podanym adresie ip");
                    else if (command.equals("help address"))
                        System.out.print("Wyświetla informację na temat aktualnego adresu ip serwera.");
                    else if (command.equals("help status"))
                        System.out.print("Wyświetla informację na temat aktualnego statusu połączenia z serwerem.");
                    else if (command.equals("help sdir"))
                        System.out.print("Pobiera z serwera listę katalogów i plików w aktualnym folderze roboczym.");
                    else if (command.equals("help download"))
                        System.out.print("download 'nazwa_pliku' -- pobiera podany plik z serwera do aktualnego folderu roboczego. !Uwaga, jeśli plik o takiej nazwie już istnieje, zostanie on nadpisany.");
                    else if (command.equals("help upload"))
                        System.out.print("upload 'nazwa_pliku' -- wysyła podany plik z katalogu roboczego na serwer. !Uwaga, jeśli plik o takiej nazwie już istnieje, zostanie on nadpisany.");
                    else if (command.equals("help exit"))
                        System.out.println("Kończy pracę programu, jeśli aplikacja jest połączona z serwerem, polecenie kończy uprzednio połączenie z serwerem.");
                    else
                        System.out.print("Komenda '" + command + "' jest nie prawidłowa, aby zobaczyć listę dostępnych komend wpisz help.");
                    break;

                case "dir":
                    File directory = new File(currentPath);
                    File files[] = directory.listFiles();

                    for(int i = 0; i < files.length; i ++)
                    {
                        System.out.print(files[i].getName());
                        if(files[i].isDirectory())
                            System.out.print("\tkatalog");
                            System.out.print("\t" + files[i].length() + "B");
                        if(!files[i].canRead())
                            System.out.print("\tCan't read");;
                        if(i != files.length - 1)
                            System.out.print("\n");
                    }
                    state = "options";
                    break;

                case "pwd":
                    System.out.print("Aktualna ścieżka katalogu roboczego: " + currentPath);
                    state = "options";
                    break;

                case "cd":
                    if(command.equals("cd .."))
                    {
                        String dirs[] = currentPath.split("\\\\");
                        if(dirs.length <= 1)
                            System.out.print("Nie można przejść do wyższego katalogu.");
                        else {
                            currentPath = "";
                            for(int i = 0; i < dirs.length - 1; i++)
                            {
                                currentPath = Path.of(currentPath, dirs[i]).toString();
                            }
                        }
                        System.out.print("Aktualna ścieżka katalogu roboczego: " + currentPath);
                    }
                    else if(command.equals("cd ~"))
                    {
                        currentPath = startPath;
                        System.out.print("Aktualna ścieżka katalogu roboczego: " + currentPath);
                    }
                    else {
                        String dirName = command.substring(command.indexOf(' ') + 1);
                        if (!dirName.equals(".") && new File(Path.of(currentPath, dirName).toString()).isDirectory())
                        {
                            currentPath = Path.of(currentPath, dirName).toString();
                            System.out.print("Aktualna ścieżka katalogu roboczego: " + currentPath);
                        }
                        else
                        {
                            System.out.println("Katalog o nazwie '" + dirName + "', nie istnieje lub nie jest katalogiem.");
                            System.out.print("Aktualna ścieżka katalogu roboczego: " + currentPath);
                        }
                    }
                    state = "options";
                    break;

                case "path":
                    String newPath = command.substring(command.indexOf(' ') + 1);
                    if (new File(Path.of(newPath).toString()).exists() && new File(Path.of(newPath).toString()).isDirectory())
                    {
                        currentPath = Path.of(newPath).toString();
                        System.out.print("Aktualna ścieżka katalogu roboczego: " + currentPath);
                    }
                    else
                    {
                        System.out.println("Ściezka '" + newPath + "', nie istnieje lub nie jest katalogiem.");
                        System.out.print("Aktualna ścieżka katalogu roboczego: " + currentPath);
                    }
                    state = "options";
                    break;

                case "connect":
                {
                    if (socket == null) {
                        try {
                            socket = new DatagramSocket();
                            socket.setSoTimeout(Config.TIMEOUT_MILLISECONDS);
                        }
                        catch (SocketException exception)
                        {
                            System.out.print("Nie udało utworzyć się gniazda klienta dla połączenia z serwerem.");
                            connectionState = false;
                            state = "options";
                            break;
                        }
                    }
                    currentIp = command.substring(command.indexOf(' ') + 1);
                    try
                    {
                        address = new InetSocketAddress(InetAddress.getByName(currentIp), Config.PORT);
                    }
                    catch (UnknownHostException exception)
                    {
                        System.out.print("Podano nieprawidłowy adres ip serwera.");
                        address = null;
                        connectionState = false;
                        state = "options";
                        break;
                    }
                    String msg = "CONNECT";
                    sendData = msg.getBytes(StandardCharsets.UTF_8);
                    sendDatagram = new DatagramPacket(sendData, sendData.length, address);
                    receiveData = new byte[Config.MAX_BUFFER_SIZE];
                    receiveDatagram = new DatagramPacket(receiveData, receiveData.length);

                    boolean errorFlag = true;
                    for(int i = 0; i < Config.TIMEOUT_TIMES; i++)
                    {
                        try {
                            socket.send(sendDatagram);
                            socket.receive(receiveDatagram);
                            errorFlag = false;
                            break;
                        }
                        catch (IOException exception) {
                        }
                    }
                    if(errorFlag)
                    {
                        System.out.print("Nie udało nawiązać się połączenia z serwerem.");
                        connectionState = false;
                    }
                    else
                    {
                        msg = Config.stringFromDatagram(receiveDatagram);
                        if(msg.equals("SERVER_FULL"))
                        {
                            System.out.print("Nie udało nawiązać się połączenia z serwerem. - Serwer jest przepełniony.");
                            connectionState = false;
                        }
                        else if(msg.equals("ALREADY_CONNECTED")) {
                            System.out.print("Powiązanie z tym serwerem już zostało nawiązane.");
                            connectionState = true;
                        }
                        else if(msg.equals("CONNECTED"))
                        {
                            System.out.print("Powiązanie z serwerem zostało nawiązane.");
                            connectionState = true;
                        }
                    }
                }
                    state = "options";
                    break;

                case "address":
                    if(currentIp == null || currentIp.isEmpty())
                        System.out.print("Nie podano adresu ip serwera.");
                    else {
                        try
                        {
                            System.out.print(InetAddress.getByName(currentIp).getHostAddress());
                        }
                        catch (UnknownHostException exception)
                        {
                            System.out.println("Podany adres ip serwera jest nieprawidłowy.");
                        }
                        finally
                        {
                            System.out.print("Adres ip serwera: " + currentIp + ", port serwera: " + Config.PORT);
                        }
                    }
                    state = "options";
                    break;

                case "status":
                    if(!connectionState)
                    {
                        System.out.print("Aktualnie nie jesteś połączony z żadnym serwerem.");
                    }
                    else
                    {
                        String msg = "STATUS";
                        sendData = msg.getBytes(StandardCharsets.UTF_8);
                        sendDatagram = new DatagramPacket(sendData, sendData.length, address);
                        receiveData = new byte[Config.MAX_BUFFER_SIZE];
                        receiveDatagram = new DatagramPacket(receiveData, receiveData.length);

                        boolean errorFlag = true;
                        for(int i = 0; i < Config.TIMEOUT_TIMES; i++)
                        {
                            try {
                                socket.send(sendDatagram);
                                socket.receive(receiveDatagram);
                                errorFlag = false;
                                break;
                            }
                            catch (IOException exception) {
                            }
                        }
                        if(errorFlag)
                        {
                            System.out.print("Aktualnie nie jesteś połączony z żadnym serwerem. -- brak odpowiedzi od serwera.");
                            connectionState = false;
                        }
                        else
                        {
                            msg = Config.stringFromDatagram(receiveDatagram);
                            if(msg.equals("CONNECTED"))
                                System.out.print("Aktualnie jesteś połączony z serwerem o adresie: " + currentIp + ":" + Config.PORT);
                            else if(msg.equals("NOT_CONNECTED"))
                            {
                                System.out.print("Aktualnie nie jesteś połączony z żadnym serwerem.");
                                connectionState = false;
                            }
                        }
                    }
                    state = "options";
                    break;

                case "sdir":
                    if(!connectionState)
                    {
                        System.out.print("Aktualnie nie jesteś połączony z żadnym serwerem.");
                    }
                    else
                    {
                        String msg = "SDIR";
                        sendData = msg.getBytes(StandardCharsets.UTF_8);
                        sendDatagram = new DatagramPacket(sendData, sendData.length, address);
                        receiveData = new byte[Config.MAX_BUFFER_SIZE];
                        receiveDatagram = new DatagramPacket(receiveData, receiveData.length);

                        boolean errorFlag = true;
                        for(int i = 0; i < Config.TIMEOUT_TIMES; i++)
                        {
                            try {
                                socket.send(sendDatagram);
                                socket.receive(receiveDatagram);
                                errorFlag = false;
                                break;
                            }
                            catch (IOException exception) {
                            }
                        }
                        if(errorFlag)
                        {
                            System.out.print("Aktualnie nie jesteś połączony z żadnym serwerem. -- brak odpowiedzi od serwera.");
                            connectionState = false;
                        }
                        else
                        {
                            msg = Config.stringFromDatagram(receiveDatagram);
                            System.out.print(msg);
                        }
                        }
                    state = "options";
                    break;

                case "download":
                    if(!connectionState)
                    {
                        System.out.print("Aktualnie nie jesteś połączony z żadnym serwerem.");
                    }
                    else
                    {
                        String msg = "DOWNLOAD " + command.substring(command.indexOf(' ') + 1);
                        sendData = msg.getBytes(StandardCharsets.UTF_8);
                        sendDatagram = new DatagramPacket(sendData, sendData.length, address);
                        receiveData = new byte[Config.MAX_BUFFER_SIZE];
                        receiveDatagram = new DatagramPacket(receiveData, receiveData.length);

                        boolean errorFlag = true;
                        for(int i = 0; i < Config.TIMEOUT_TIMES; i++)
                        {
                            try {
                                socket.send(sendDatagram);
                                socket.receive(receiveDatagram);
                                errorFlag = false;
                                break;
                            }
                            catch (IOException exception) {
                            }
                        }
                        if(errorFlag)
                        {
                            System.out.print("Aktualnie nie jesteś połączony z żadnym serwerem. -- brak odpowiedzi od serwera.");
                            connectionState = false;
                        }
                        else
                        {
                            msg = Config.stringFromDatagram(receiveDatagram);
                            if(msg.equals("NOT_EXIST"))
                                System.out.print("Podany plik nie istnieje.");
                            else if(msg.equals("CAN'T_READ"))
                                System.out.print("Brak uprawnień do odczytu podanego pliku.");
                            else if(msg.equals("DIRECTORY"))
                                System.out.print("Podany plik jest katalogiem. Można pobierać tylko pojedyncze pliki.");
                            else if(msg.matches("^[0-9]+ [0-9]+ [0-9]+$"))
                            {
                                file = new File(Path.of(currentPath, command.substring(command.indexOf(' ') + 1)).toString());
                                fileSize = -1; // Bytes
                                currentPacket = -1;
                                numberOfPackets = -1;
                                streamState = "off";

                                try
                                {
                                    file.delete();
                                    file.createNewFile();
                                    if(!file.canWrite())
                                        throw new IOException();
                                    fileOutputStream = new FileOutputStream(file);
                                }
                                catch (IOException exception)
                                {
                                    try
                                    {
                                        msg = "-1";
                                        sendData = msg.getBytes(StandardCharsets.UTF_8);
                                        sendDatagram = new DatagramPacket(sendData, sendData.length, address);
                                        socket.send(sendDatagram);
                                        socket.receive(receiveDatagram);
                                        if(fileOutputStream != null)
                                            fileOutputStream.close();
                                        fileOutputStream = null;
                                        errorFlag = false;
                                        break;
                                    }
                                    catch (IOException subexception) { }
                                    System.out.print("Nie można utworzyć takiego pliku -- Odmowa dostępu.");
                                    state = "options";
                                    break;
                                }

                                String tokens[] = msg.split(" ");
                                currentPacket = Long.parseLong(tokens[0]);
                                numberOfPackets = Long.parseLong(tokens[1]);
                                fileSize = Long.parseLong(tokens[2]); // Bytes
                                streamState = "download";
                                System.out.println("Pobieranie pliku o nazwie: " + file.getName());

                                while (true)
                                {
                                    msg = currentPacket + " " + numberOfPackets + " " + fileSize;
                                    sendData = msg.getBytes(StandardCharsets.UTF_8);
                                    sendDatagram = new DatagramPacket(sendData, sendData.length, address);
                                    receiveData = new byte[Config.BUFFER_SIZE];
                                    receiveDatagram = new DatagramPacket(receiveData, receiveData.length);

                                    errorFlag = true;
                                    for (int i = 0; i < Config.TIMEOUT_TIMES * 2; i++)
                                    {
                                        try
                                        {
                                            socket.send(sendDatagram);
                                            socket.receive(receiveDatagram);
                                            errorFlag = false;
                                            break;
                                        } catch (IOException exception) { }
                                    }
                                    if (errorFlag)
                                    {
                                        if(file != null)
                                            file.delete();
                                        file = null;
                                        fileSize = -1; // Bytes
                                        currentPacket = -1;
                                        numberOfPackets = -1;
                                        streamState = "off";
                                        System.out.print("Aktualnie nie jesteś połączony z żadnym serwerem. -- brak odpowiedzi od serwera.");
                                        connectionState = false;
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

                                        byte[] data = receiveDatagram.getData();
                                        int len = Config.BUFFER_SIZE;
                                        if(currentPacket + 1 == numberOfPackets)
                                        {
                                            len = (int) (fileSize - (numberOfPackets - 1) * Config.BUFFER_SIZE);
                                            data = Arrays.copyOfRange(data, 0, len);
                                        }

                                        if(len != receiveDatagram.getLength())
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
                                                sendDatagram = new DatagramPacket(sendData, sendData.length, address);
                                                socket.send(sendDatagram);
                                                socket.receive(receiveDatagram);
                                                errorFlag = false;
                                                break;
                                            }
                                            catch (IOException subexception) { }
                                            if (file != null)
                                                file.delete();
                                            file = null;
                                            fileSize = -1; // Bytes
                                            currentPacket = -1;
                                            numberOfPackets = -1;
                                            streamState = "off";
                                            System.out.print("Wystąpił błąd podczas zapisu pobieranego pliku.");
                                            break;
                                        }

                                        currentPacket++;
                                        if(currentPacket == numberOfPackets)
                                        {
                                            file = null;
                                            fileSize = -1; // Bytes
                                            currentPacket = -1;
                                            numberOfPackets = -1;
                                            streamState = "off";
                                            try
                                            {
                                                msg = "0";
                                                sendData = msg.getBytes(StandardCharsets.UTF_8);
                                                sendDatagram = new DatagramPacket(sendData, sendData.length, address);
                                                socket.send(sendDatagram);
                                                socket.receive(receiveDatagram);
                                                errorFlag = false;
                                                if(fileOutputStream != null)
                                                    fileOutputStream.close();
                                                fileOutputStream = null;
                                                break;
                                            }
                                            catch (IOException subexception) { }
                                            System.out.print("Pobieranie pliku zakończyło się pomyślnie.");
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    state = "options";
                    break;

                case "upload":
                    if(!connectionState)
                    {
                        System.out.print("Aktualnie nie jesteś połączony z żadnym serwerem.");
                    }
                    else
                    {
                        String fileName = command.substring(command.indexOf(' ') + 1);
                        file = new File(Path.of(currentPath, fileName).toString());
                        if(!file.exists())
                        {
                            System.out.print("Nie można wysłać pliku: " + fileName + " -- podany plik nie istnieje.");
                            file = null;
                            state = "options";
                            break;
                        }
                        else if(file.isDirectory())
                        {
                            System.out.print("Nie można wysłać pliku: " + fileName + " -- podany plik jest katalogiem.");
                            file = null;
                            state = "options";
                            break;
                        }
                        else if(!file.canRead())
                        {
                            System.out.print("Nie można wysłać pliku: " + fileName + " -- odmowa dostępu.");
                            file = null;
                            state = "options";
                            break;
                        }

                        String msg = "UPLOAD " + fileName;
                        sendData = msg.getBytes(StandardCharsets.UTF_8);
                        sendDatagram = new DatagramPacket(sendData, sendData.length, address);
                        receiveData = new byte[Config.MAX_BUFFER_SIZE];
                        receiveDatagram = new DatagramPacket(receiveData, receiveData.length);
                        byte[] fileData = new byte[Config.BUFFER_SIZE];

                        boolean errorFlag = true;
                        for(int i = 0; i < Config.TIMEOUT_TIMES; i++)
                        {
                            try {
                                socket.send(sendDatagram);
                                socket.receive(receiveDatagram);
                                errorFlag = false;
                                break;
                            }
                            catch (IOException exception) {
                            }
                        }
                        if(errorFlag)
                        {
                            System.out.print("Aktualnie nie jesteś połączony z żadnym serwerem. -- brak odpowiedzi od serwera.");
                            file = null;
                            connectionState = false;
                        }
                        else
                        {
                            msg = Config.stringFromDatagram(receiveDatagram);
                            if(msg.equals("CAN'T_WRITE")) {
                                System.out.print("Brak uprawnień do odczytu podanego pliku.");
                                file = null;
                            }
                            else if(msg.equals("WAITING")) {
                                currentPacket = -1;
                                numberOfPackets = (long) Math.ceil(file.length() / (double)Config.BUFFER_SIZE);
                                fileSize = file.length();
                                streamState = "upload";
                                msg = "0 " + numberOfPackets + " " + fileSize;
                                sendDatagram = new DatagramPacket(msg.getBytes(StandardCharsets.UTF_8), msg.getBytes(StandardCharsets.UTF_8).length, receiveDatagram.getSocketAddress());
                                FileInputStream fileInputStream = null;
                                try {
                                    fileInputStream = new FileInputStream(file);
                                    socket.send(sendDatagram);
                                    System.out.println("Wysłanie informacji do serwera o rozmiarze wysyłanego pliku: " + fileName);
                                    System.out.println("Rozpoczęcie wysyłania pliku o nazwie: " + fileName);
                                }
                                catch (IOException exception)
                                {
                                    currentPacket = -1;
                                    numberOfPackets = -1;
                                    fileSize = -1;
                                    streamState = "off";
                                    try {
                                        fileInputStream.close();
                                    } catch (IOException ignored) {}
                                    file = null;
                                    System.out.print("Wystąpił błąd podczas wysyłania pliku: " + fileName);
                                    break;
                                }
                                while(true)
                                {
                                    errorFlag = true;
                                    for(int i = 0; i < Config.TIMEOUT_TIMES; i++)
                                    {
                                        try {
                                            socket.send(sendDatagram);
                                            socket.receive(receiveDatagram);
                                            errorFlag = false;
                                            break;
                                        }
                                        catch (IOException exception) {
                                        }
                                    }
                                    if(errorFlag)
                                    {
                                        currentPacket = -1;
                                        numberOfPackets = -1;
                                        fileSize = -1;
                                        streamState = "off";
                                        try {
                                            fileInputStream.close();
                                        } catch (IOException ignored) {}
                                        file = null;
                                        connectionState = false;
                                        System.out.print("Aktualnie nie jesteś połączony z żadnym serwerem. -- brak odpowiedzi od serwera.");
                                        break;
                                    }
                                    else
                                    {
                                        msg = Config.stringFromDatagram(receiveDatagram);
                                        if(msg.matches("^[0-9]+ [0-9]+ [0-9]+$"))
                                        {
                                            try
                                            {
                                                String tokens[] = msg.split(" ");
                                                long receiveCurrentPacket = Long.parseLong(tokens[0]);
                                                long receiveNumberOfPackets = Long.parseLong(tokens[1]);
                                                long receiveFileSize = Long.parseLong(tokens[2]);
                                                int len = Config.BUFFER_SIZE;

                                                if (receiveCurrentPacket + 1 == receiveNumberOfPackets)
                                                {
                                                    len = (int) (receiveFileSize - (receiveNumberOfPackets - 1) * Config.BUFFER_SIZE);
                                                }

                                                if (currentPacket == -1) {
                                                    fileData = new byte[len];
                                                    fileInputStream.read(fileData, 0, len);
                                                    currentPacket = 0;
                                                }

                                                if (currentPacket < receiveCurrentPacket) {
                                                    fileData = new byte[len];
                                                    fileInputStream.read(fileData, 0, len);
                                                    currentPacket = receiveCurrentPacket;
                                                }

                                                sendDatagram = new DatagramPacket(fileData, fileData.length, receiveDatagram.getSocketAddress());
                                            }
                                            catch (IOException exception)
                                            {
                                                currentPacket = -1;
                                                numberOfPackets = -1;
                                                fileSize = -1;
                                                streamState = "off";
                                                try {
                                                    fileInputStream.close();
                                                } catch (IOException ignored) {}
                                                file = null;
                                                System.out.print("Błąd podczas próby odczyty pliku: " + fileName);
                                                break;
                                            }
                                        }
                                        else if(msg.equals("0") || msg.equals("-1"))
                                        {
                                            if(msg.equals("0"))
                                                System.out.print("Plik: " + fileName + " został wysłany poprawnie.");
                                            else if(msg.equals("-1"))
                                                System.out.print("Plik: " + fileName + " nie został wysłany poprawnie - błąd po stronie serwera.");
                                            try
                                            {
                                                fileInputStream.close();
                                                fileInputStream = null;
                                            }
                                            catch (IOException ignored) {}
                                            file = null;
                                            currentPacket = -1;
                                            numberOfPackets = -1;
                                            fileSize = -1;
                                            streamState = "off";
                                            break;
                                        }
                                        else if(msg.equals("WAITING"))
                                        {
                                            currentPacket = -1;
                                            numberOfPackets = (long) Math.ceil(file.length() / (double) Config.BUFFER_SIZE);
                                            fileSize = file.length();
                                            streamState = "upload";
                                            msg = "0 " + numberOfPackets + " " + fileSize;
                                            sendDatagram = new DatagramPacket(msg.getBytes(StandardCharsets.UTF_8), msg.getBytes(StandardCharsets.UTF_8).length, receiveDatagram.getSocketAddress());
                                            fileInputStream = null;
                                            try {
                                                fileInputStream = new FileInputStream(file);
                                                socket.send(sendDatagram);
                                                System.out.println("Wysłanie informacji do serwera o rozmiarze wysyłanego pliku: " + fileName);
                                                System.out.println("Rozpoczęcie wysyłania pliku o nazwie: " + fileName);
                                            } catch (IOException exception) {
                                                currentPacket = -1;
                                                numberOfPackets = -1;
                                                fileSize = -1;
                                                streamState = "off";
                                                try {
                                                    fileInputStream.close();
                                                } catch (IOException ignored) {
                                                }
                                                file = null;
                                                System.out.print("Wystąpił błąd podczas wysyłania pliku: " + fileName);
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    state = "options";
                    break;

                case "exit":
                    if(connectionState)
                        {
                        String msg = "DISCONNECT";
                        sendData = msg.getBytes(StandardCharsets.UTF_8);
                        sendDatagram = new DatagramPacket(sendData, sendData.length, address);
                        try
                        {
                            socket.send(sendDatagram);
                        }
                        catch (IOException exception) {
                        }
                        System.out.println("Połączneie z serwerem zostało zakończone.");
                    }
                    System.out.println("Aplikacja zostanie wyłączona.");
                    System.exit(0);
                    break;

                case "help":
                    state = "options";
                    System.out.println("Dostępne komendy:");
                    System.out.println("help -- wyświetla listę komend");
                    System.out.println("help 'nazwa_komendy' -- wyświetla informację na temat danej komendy");
                    System.out.println("dir -- wyświetla listę plików i katalogów w aktualnej ścieżce");
                    System.out.println("pwd -- wyświetla aktualną scieżkę");
                    System.out.println("cd -- przechodzi lub wychodzi z katalogu");
                    System.out.println("path 'nowa_sciezka' -- przechodzi do podanej scieżki");
                    System.out.println("connect 'adres_ip_serwera' -- nawiązuje połączenie z serwerem o podanym adresie");
                    System.out.println("address -- wyświetla informacje na temat aktualnego adresu ip serwera");
                    System.out.println("status -- wyświetla informacje na temat aktualnego połączenia z serwerem");
                    System.out.println("sdir -- wyświetla listę plików możliwych do pobrania z serwerze");
                    System.out.println("download 'nazwa_pliku' -- pobiera plik z serwera do aktualnego katalogu roboczego");
                    System.out.println("upload 'nazwa_pliku' -- wysyla plik z aktualnego katalogu roboczego na serwer");
                    System.out.print("exit -- wychodzi z programu");
                    break;

                default:
                    state = "options";
                    System.out.print("\nKomenda '" + command + "' jest nie prawidłowa, aby zobaczyć listę dostępnych komend wpisz help.");
                    break;
            }
        }

    }
}
