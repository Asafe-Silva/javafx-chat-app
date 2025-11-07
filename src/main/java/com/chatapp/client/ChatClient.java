package com.chatapp.client;

import com.chatapp.model.Message;
import com.chatapp.model.Room;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class ChatClient {
    /**
     * Cliente do chat responsável por:
     * - manter a conexão socket com o servidor;
     * - enviar mensagens do usuário (login, join/leave, chat);
     * - receber mensagens do servidor e repassar para a UI (ClientTab).
     *
     * Comunicação:
     * - Utiliza ObjectOutputStream/ObjectInputStream para enviar/receber
     *   objetos do tipo {@link com.chatapp.model.Message}.
     * - Ao conectar, envia uma mensagem de LOGIN com o nome de usuário.
     * - Cria uma thread que fica escutando mensagens do servidor.
     *
     * Observações sobre concorrência/erros:
     * - Quando ocorre IOException a flag "connected" é atualizada para false
     *   e a thread de escuta finaliza silenciosamente (tratamento simples).
     * - A UI (ClientTab) é responsável por atualizar a interface quando
     *   receber callbacks deste cliente.
     */
    
    private String host;
    private int port;
    private String username;
    private ClientTab clientTab;
    
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean connected;
    
    public ChatClient(String host, int port, String username, ClientTab clientTab) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.clientTab = clientTab;
    }
    
    public boolean connect() {
        // Tenta estabelecer conexão TCP com o servidor e inicializar os streams
        // de leitura/escrita de objetos. Em seguida envia uma mensagem de LOGIN
        // contendo o nome de usuário para que o servidor registre o cliente.
        // Se tudo ocorrer bem, inicia uma thread que escuta mensagens vindas
        // do servidor (listenForMessages).
        try {
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            
            // Send login message
            Message loginMsg = new Message(Message.Type.LOGIN);
            loginMsg.setUsername(username);
            sendMessage(loginMsg);
            
            connected = true;
            
            // Start listening for messages
            new Thread(this::listenForMessages).start();
            
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    private void listenForMessages() {
        // Loop contínuo que aguarda mensagens do servidor.
        // A leitura bloqueia em in.readObject(). Se ocorrer qualquer exceção
        // (por exemplo, conexão encerrada), marca connected=false para finalizar
        // a thread de escuta.
        try {
            while (connected) {
                Message message = (Message) in.readObject();
                handleMessage(message);
            }
        } catch (Exception e) {
            // Qualquer erro encerra a recepção de mensagens.
            connected = false;
        }
    }
    
    private void handleMessage(Message message) {
        // Despacha o processamento conforme o tipo da mensagem recebida.
        // Tipos comuns: atualização de lista de salas, confirmação de entrada/saída
        // de sala, mensagens de chat e notificações de usuário.
        switch (message.getType()) {
            case ROOMS_LIST:
                @SuppressWarnings("unchecked")
                List<Room> rooms = (List<Room>) message.getData();
                clientTab.updateRoomList(rooms);
                break;
            case ROOM_JOINED:
                clientTab.showChatView(message.getRoomName());
                break;
            case ROOM_LEFT:
                clientTab.showRoomSelectionView(username);
                requestRooms();
                break;
            case ROOM_DELETED:
                clientTab.appendChatMessage("*** A sala '" + message.getRoomName() + "' foi removida pelo servidor ***");
                clientTab.showRoomSelectionView(username);
                requestRooms();
                break;
            case USER_KICKED:
                clientTab.appendChatMessage("*** Você foi expulso do servidor: " + message.getContent() + " ***");
                disconnect();
                clientTab.showLoginView();
                break;
            case CHAT_MESSAGE:
                // preserve color information and delegate to UI for styled display
                clientTab.appendChatMessage(message.getUsername(), message.getContent(), message.getColor());
                break;
            case USER_TYPING:
                // message.content holds the username who is typing
                clientTab.showTypingIndicator(message.getContent());
                break;
            case USER_STOPPED_TYPING:
                clientTab.clearTypingIndicator(message.getContent());
                break;
            case USER_ERASING:
                clientTab.showErasingIndicator(message.getContent());
                break;
            case USER_STOPPED_ERASING:
                clientTab.clearErasingIndicator(message.getContent());
                break;
            case REPORT_NOTIFICATION:
                clientTab.appendChatMessage("*** DENÚNCIA: " + message.getContent() + " ***");
                break;
            case USER_JOINED:
            case USER_LEFT:
                clientTab.appendChatMessage("*** " + message.getContent() + " ***");
                break;
            case ERROR:
                // show error prominently on the login/room UI
                clientTab.showError(message.getContent());
                break;
        }
    }
    
    public void requestRooms() {
        Message msg = new Message(Message.Type.GET_ROOMS);
        sendMessage(msg);
    }
    
    public void joinRoom(String roomName) {
        Message msg = new Message(Message.Type.JOIN_ROOM);
        msg.setRoomName(roomName);
        sendMessage(msg);
    }
    
    public void leaveRoom() {
        Message msg = new Message(Message.Type.LEAVE_ROOM);
        sendMessage(msg);
    }
    
    public void sendChatMessage(String content) {
        sendChatMessage(content, null);
    }

    public void sendChatMessage(String content, String color) {
        Message msg = new Message(Message.Type.CHAT_MESSAGE);
        msg.setContent(content);
        msg.setColor(color);
        sendMessage(msg);
    }

    public void reportMessage(String content) {
        Message msg = new Message(Message.Type.REPORT_MESSAGE);
        msg.setContent(content);
        sendMessage(msg);
    }

    // report a specific user (targetUsername)
    public void reportMessage(String targetUsername, String content) {
        Message msg = new Message(Message.Type.REPORT_MESSAGE);
        msg.setUsername(targetUsername);
        msg.setContent(content);
        sendMessage(msg);
    }

    public void sendErasing(String roomName, boolean erasing) {
        Message msg = new Message(erasing ? Message.Type.ERASING : Message.Type.STOP_ERASING);
        msg.setRoomName(roomName);
        sendMessage(msg);
    }

    public void reportRoom(String roomName, String reason) {
        Message msg = new Message(Message.Type.REPORT_ROOM);
        msg.setRoomName(roomName);
        msg.setContent(reason);
        sendMessage(msg);
    }

    public void sendTyping(String roomName, boolean typing) {
        Message msg = new Message(typing ? Message.Type.TYPING : Message.Type.STOP_TYPING);
        msg.setRoomName(roomName);
        sendMessage(msg);
    }
    
    private void sendMessage(Message message) {
        try {
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            connected = false;
        }
    }
    
    public void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
    }
}
