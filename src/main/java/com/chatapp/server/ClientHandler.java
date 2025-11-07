package com.chatapp.server;

import com.chatapp.model.Message;
import com.chatapp.model.Room;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {
    /**
     * Handler responsável por comunicar-se com um cliente conectado.
     * Cada instância gerencia os streams (in/out) do socket do cliente,
     * processa mensagens recebidas e envia respostas.
     *
     * Ciclo de vida:
     * - Criado pelo ChatServer quando uma conexão é aceita;
     * - run() faz leitura contínua de Message e despacha para handleMessage;
     * - Em caso de erro/encerramento, chama server.removeClient e fecha o socket.
     *
     * Observações:
     * - O handler depende do ChatServer para operações que alteram estado global
     *   (como adicionar/remover cliente, entrar/saír de sala e broadcasting).
     */
    
    private Socket socket;
    private ChatServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;
    private String currentRoom;
    
    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }
    
    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            
            while (true) {
                Message message = (Message) in.readObject();
                handleMessage(message);
            }
        } catch (Exception e) {
            if (username != null) {
                server.removeClient(username);
            }
        } finally {
            disconnect();
        }
    }
    
    private void handleMessage(Message message) {
        switch (message.getType()) {
            case LOGIN:
                handleLogin(message);
                break;
            case GET_ROOMS:
                handleGetRooms();
                break;
            case JOIN_ROOM:
                handleJoinRoom(message);
                break;
            case LEAVE_ROOM:
                handleLeaveRoom(message);
                break;
            case CHAT_MESSAGE:
                handleChatMessage(message);
                break;
            case TYPING:
            case STOP_TYPING:
            case ERASING:
            case STOP_ERASING:
                handleActivity(message);
                break;
            case REPORT_MESSAGE:
                // message.username contains target username (the one being reported)
                String target = message.getUsername();
                server.getServerTab().log("Denúncia: " + username + " denunciou " + target + ": " + message.getContent());
                if (target != null) {
                    ClientHandler tgt = server.getClientHandler(target);
                    if (tgt != null) {
                        Message notify = new Message(Message.Type.REPORT_NOTIFICATION);
                        notify.setContent("Você foi denunciado por: " + username + " - Mensagem: " + message.getContent());
                        tgt.sendMessage(notify);
                    }
                }
                break;
            case REPORT_ROOM:
                server.getServerTab().log("Denúncia de sala de " + username + ": " + message.getRoomName() + " - " + message.getContent());
                break;
            case LOGOUT:
                disconnect();
                break;
        }
    }
    
    private void handleLogin(Message message) {
        username = message.getUsername();
        boolean ok = server.addClient(username, this);
        if (!ok) {
            Message response = new Message(Message.Type.ERROR);
            response.setContent("Usuário já conectado (apenas uma sessão por conta permitida)");
            sendMessage(response);
            // do not register this handler as active; close connection shortly
            disconnect();
            return;
        }
        Message response = new Message(Message.Type.LOGIN);
        response.setContent("Login realizado com sucesso");
        sendMessage(response);
    }
    
    private void handleGetRooms() {
        List<Room> rooms = server.getRoomsList();
        Message response = new Message(Message.Type.ROOMS_LIST);
        response.setData(rooms);
        sendMessage(response);
    }
    
    private void handleJoinRoom(Message message) {
        String roomName = message.getRoomName();
        boolean success = server.joinRoom(username, roomName);
        
        if (success) {
            currentRoom = roomName;
            Message response = new Message(Message.Type.ROOM_JOINED);
            response.setRoomName(roomName);
            sendMessage(response);
        } else {
            Message response = new Message(Message.Type.ERROR, "Não foi possível entrar na sala");
            sendMessage(response);
        }
    }
    
    private void handleLeaveRoom(Message message) {
        if (currentRoom != null) {
            server.leaveRoom(username, currentRoom);
            currentRoom = null;
            
            Message response = new Message(Message.Type.ROOM_LEFT);
            sendMessage(response);
        }
    }
    
    private void handleChatMessage(Message message) {
        if (currentRoom != null) {
            // preserve color information when broadcasting
            server.broadcastToRoom(currentRoom, username, message.getContent(), message.getColor());
        }
    }


    private void handleActivity(Message message) {
        if (currentRoom != null) {
            Message.Type t = message.getType();
            server.notifyActivity(currentRoom, username, t);
        }
    }
    
    public void sendMessage(Message message) {
        try {
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            server.getServerTab().log("Erro ao enviar mensagem para " + username);
        }
    }
    
    public void disconnect() {
        try {
            if (username != null) {
                server.removeClient(username);
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
    }
}
