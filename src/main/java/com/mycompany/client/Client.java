package com.mycompany.client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        String host = "127.0.0.1"; // chạy local
        int port = 9090;

        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner sc = new Scanner(System.in)) {

            System.out.println("Server: " + in.readLine());

            while (true) {
                System.out.print("> ");
                String input = sc.nextLine();
                out.println(input);

                String resp = in.readLine();
                if (resp == null) break;
                System.out.println("Server: " + resp);

                if ("BYE".equalsIgnoreCase(resp))
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
