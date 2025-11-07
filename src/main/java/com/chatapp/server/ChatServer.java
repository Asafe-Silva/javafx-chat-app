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
    
    // Aumentado para permitir 10 salas a mais (total 15)
    private static final int MAX_ROOMS = 15;
    
    private int port;
    private ServerSocket serverSocket;
    private boolean running;
    private ServerTab serverTab;
    
    private Map<String, ClientHandler> clients;
    private Map<String, Room> rooms;
    // track login times and last room for active users
    private Map<String, Long> loginTimes;
    private Map<String, String> lastRoomMap;
    // track inactive users (last seen timestamp and last name)
    private Map<String, Long> inactiveLastSeen;
    private Map<String, String> inactiveLastName;
    
    public ChatServer(int port, ServerTab serverTab) {
        this.port = port;
        this.serverTab = serverTab;
        this.clients = new ConcurrentHashMap<>();
        this.rooms = new ConcurrentHashMap<>();
        this.loginTimes = new ConcurrentHashMap<>();
        this.lastRoomMap = new ConcurrentHashMap<>();
        this.inactiveLastSeen = new ConcurrentHashMap<>();
        this.inactiveLastName = new ConcurrentHashMap<>();
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
    
    public synchronized boolean addClient(String username, ClientHandler handler) {
        // don't allow two clients with same username
        if (clients.containsKey(username)) {
            return false;
        }
        clients.put(username, handler);
        serverTab.log("Cliente conectado: " + username);
        loginTimes.put(username, System.currentTimeMillis());
        return true;
    }
    
    public synchronized void removeClient(String username) {
        clients.remove(username);
        // move to inactive lists
        inactiveLastSeen.put(username, System.currentTimeMillis());
        inactiveLastName.put(username, username);
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
        lastRoomMap.put(username, roomName);
        updateRoomList();
        serverTab.log(username + " entrou na sala: " + roomName);
        notifyRoomUsers(roomName, username + " entrou na sala");
        return true;
    }

    /**
     * Deleta uma sala. Notifica usuários presentes antes de remover.
     */
    public synchronized boolean deleteRoom(String roomName) {
        Room room = rooms.get(roomName);
        if (room == null) return false;

        // Avisar usuários
        Message notification = new Message(Message.Type.ROOM_DELETED, "Sala '" + roomName + "' foi removida pelo servidor");
        notification.setRoomName(roomName);
        for (String user : room.getUsers()) {
            ClientHandler ch = clients.get(user);
            if (ch != null) ch.sendMessage(notification);
        }

        rooms.remove(roomName);
        updateRoomList();
        serverTab.log("Sala deletada: " + roomName);
        return true;
    }

    /**
     * Expulsa um usuário: envia notificação e desconecta.
     */
    public synchronized boolean kickUser(String username) {
        ClientHandler ch = clients.get(username);
        if (ch == null) return false;
        Message notification = new Message(Message.Type.USER_KICKED, "Você foi expulso pelo servidor");
        ch.sendMessage(notification);
        ch.disconnect();
        serverTab.log("Usuário expulso: " + username);
        return true;
    }

    /**
     * Retorna informações sumárias dos clientes ativos para UI.
     */
    public synchronized java.util.List<String> getActiveClientsInfo() {
        java.util.List<String> info = new ArrayList<>();
        for (String user : clients.keySet()) {
            long login = loginTimes.getOrDefault(user, 0L);
            String lastRoom = lastRoomMap.getOrDefault(user, "-");
            long onlineSecs = (login == 0) ? 0 : (System.currentTimeMillis() - login) / 1000;
            info.add(user + " | Sala: " + lastRoom + " | Online: " + onlineSecs + "s");
        }
        return info;
    }

    /**
     * Retorna informações sumárias dos clientes inativos para UI.
     */
    public synchronized java.util.List<String> getInactiveClientsInfo() {
        java.util.List<String> info = new ArrayList<>();
        for (String user : inactiveLastSeen.keySet()) {
            long last = inactiveLastSeen.getOrDefault(user, 0L);
            String lastName = inactiveLastName.getOrDefault(user, user);
            long offlineSecs = (last == 0) ? 0 : (System.currentTimeMillis() - last) / 1000;
            info.add(user + " | Último nome: " + lastName + " | Offline: " + offlineSecs + "s");
        }
        return info;
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
    
    public synchronized void broadcastToRoom(String roomName, String username, String message, String color) {
        Room room = rooms.get(roomName);
        if (room == null) return;
        
        Message msg = new Message(Message.Type.CHAT_MESSAGE);
        msg.setUsername(username);
        msg.setRoomName(roomName);
        msg.setContent(message);
        msg.setColor(color);
        
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
        // also notify all connected clients with the updated room list
        Message roomsMsg = new Message(Message.Type.ROOMS_LIST);
        roomsMsg.setData(getRoomsList());
        for (ClientHandler ch : clients.values()) {
            ch.sendMessage(roomsMsg);
        }
    }
    /**
     * Notifica usuários de uma sala que alguém está digitando/apagando/ parou.
     */
    public synchronized void notifyActivity(String roomName, String username, Message.Type activity) {
        Room room = rooms.get(roomName);
        if (room == null) return;
        Message.Type outType;
        switch (activity) {
            case TYPING: outType = Message.Type.USER_TYPING; break;
            case STOP_TYPING: outType = Message.Type.USER_STOPPED_TYPING; break;
            case ERASING: outType = Message.Type.USER_ERASING; break;
            case STOP_ERASING: outType = Message.Type.USER_STOPPED_ERASING; break;
            default: return;
        }
        Message msg = new Message(outType);
        msg.setRoomName(roomName);
        msg.setContent(username);
        for (String user : room.getUsers()) {
            ClientHandler ch = clients.get(user);
            if (ch != null) ch.sendMessage(msg);
        }
    }

    // back-compat wrapper for previous notifyTyping API
    public synchronized void notifyTyping(String roomName, String username, boolean typing) {
        notifyActivity(roomName, username, typing ? Message.Type.TYPING : Message.Type.STOP_TYPING);
    }
    
    public ServerTab getServerTab() {
        return serverTab;
    }

    public synchronized ClientHandler getClientHandler(String username) {
        return clients.get(username);
    }
}
