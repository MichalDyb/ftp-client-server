package kpu.krosno;

import javax.sound.sampled.Port;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Scanner;

public class Config {
    public static final int MAX_HOST = 50; // Maksymalna liczba hostów
    public static final int PORT = 40000; // Znany port serwera
    public static final int BUFFER_SIZE = 1024; // Maksymalny rozmiar bufforu używany podczas przesyłania plików
    public static final int MAX_BUFFER_SIZE = 65507; // Maksymalny rozmiar bufforu, dla przesyłania informacji i komend pomiędzy klientem i serwerm
    public static final int TIMEOUT_MILLISECONDS = 1000; // Czas oczekiwania gniazda UDP na wiadomość zwrotną
    public static final int TIMEOUT_TIMES = 4; // Ilość prób ponownego przesłania wiaomości w przypadku braku odpowiedzi
    public static Scanner scanner = new Scanner(System.in); // Statyczna zmienna dal wejścia klawiatury

    public static String stringFromDatagram(DatagramPacket receiveDatagram) // Metoda konwertująca byte z datagramu na String
    {
        if(receiveDatagram == null)
            return null;
        return new String(receiveDatagram.getData(), 0, receiveDatagram.getLength(), StandardCharsets.UTF_8);
    }
}

