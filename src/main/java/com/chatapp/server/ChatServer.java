package com.chatapp.server;

import com.chatapp.model.Message;
import com.chatapp.model.Room;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    /**
     * Servidor de chat responsável por:
     * - aceitar conexões de clientes (sockets);
     * - manter mapeamentos de usuários -> ClientHandler;
     * - manter as salas (Room) e permitir criação/entrada/saída;
     * - encaminhar mensagens de chat para todos os usuários de uma sala.
     *
     * Implementação e concorrência:
     * - Usa ServerSocket para aceitar conexões e cria um ClientHandler por
     *   conexão, cada um rodando em sua própria thread.
     * - As coleções clients e rooms são ConcurrentHashMap para acesso seguro
     *   entre threads; métodos que atualizam estado crítico são sincronizados
     *   para garantir consistência (por exemplo createRoom/joinRoom).
     *
     * Limitações práticas:
     * - Limita o número máximo de salas (MAX_ROOMS).
     * - Tratamento de erros é simples; falhas de I/O são logadas no ServerTab.
     */
    
    private static final int MAX_ROOMS = 5;
    
    private int port;
    private ServerSocket serverSocket;
    private boolean running;
    private ServerTab serverTab;
    
    private Map<String, ClientHandler> clients;
    private Map<String, Room> rooms;
    
    public ChatServer(int port, ServerTab serverTab) {
        this.port = port;
        this.serverTab = serverTab;
        this.clients = new ConcurrentHashMap<>();
        this.rooms = new ConcurrentHashMap<>();
    }
    
    public void start() {
        running = true;
        try {
            serverSocket = new ServerSocket(port);
            serverTab.log("Servidor aguardando conexões...");
            
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    new Thread(handler).start();
                } catch (SocketException e) {
                    if (!running) break;
                }
            }
        } catch (IOException e) {
            serverTab.log("Erro no servidor: " + e.getMessage());
        }
    }
    
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            for (ClientHandler client : clients.values()) {
                client.disconnect();
            }
        } catch (IOException e) {
            serverTab.log("Erro ao encerrar servidor: " + e.getMessage());
        }
    }
    
    public synchronized boolean createRoom(String roomName) {
        if (rooms.size() >= MAX_ROOMS) {
            return false;
        }
        if (rooms.containsKey(roomName)) {
            return false;
        }
        rooms.put(roomName, new Room(roomName));
        updateRoomList();
        return true;
    }
    
    public synchronized void addClient(String username, ClientHandler handler) {
        clients.put(username, handler);
        serverTab.log("Cliente conectado: " + username);
    }
    
    public synchronized void removeClient(String username) {
        clients.remove(username);
        // Remove user from all rooms
        for (Room room : rooms.values()) {
            if (room.getUsers().contains(username)) {
                room.removeUser(username);
                notifyRoomUsers(room.getName(), username + " saiu da sala");
            }
        }
        updateRoomList();
        serverTab.log("Cliente desconectado: " + username);
    }
    
    public synchronized boolean joinRoom(String username, String roomName) {
        Room room = rooms.get(roomName);
        if (room == null) return false;
        
        // Remove user from other rooms
        for (Room r : rooms.values()) {
            if (r.getUsers().contains(username)) {
                r.removeUser(username);
            }
        }
        
        room.addUser(username);
        updateRoomList();
        serverTab.log(username + " entrou na sala: " + roomName);
        notifyRoomUsers(roomName, username + " entrou na sala");
        return true;
    }
    
    public synchronized void leaveRoom(String username, String roomName) {
        Room room = rooms.get(roomName);
        if (room != null) {
            room.removeUser(username);
            updateRoomList();
            serverTab.log(username + " saiu da sala: " + roomName);
            notifyRoomUsers(roomName, username + " saiu da sala");
        }
    }
    
    public synchronized void broadcastToRoom(String roomName, String username, String message) {
        Room room = rooms.get(roomName);
        if (room == null) return;
        
        Message msg = new Message(Message.Type.CHAT_MESSAGE);
        msg.setUsername(username);
        msg.setRoomName(roomName);
        msg.setContent(message);
        
        for (String user : room.getUsers()) {
            ClientHandler client = clients.get(user);
            if (client != null) {
                client.sendMessage(msg);
            }
        }
    }
    
    private void notifyRoomUsers(String roomName, String notification) {
        Room room = rooms.get(roomName);
        if (room == null) return;
        
        Message msg = new Message(Message.Type.USER_JOINED, notification);
        msg.setRoomName(roomName);
        
        for (String user : room.getUsers()) {
            ClientHandler client = clients.get(user);
            if (client != null) {
                client.sendMessage(msg);
            }
        }
    }
    
    public List<Room> getRoomsList() {
        return new ArrayList<>(rooms.values());
    }
    
    private void updateRoomList() {
        serverTab.updateRoomList(getRoomsList());
    }
    
    public ServerTab getServerTab() {
        return serverTab;
    }
}
