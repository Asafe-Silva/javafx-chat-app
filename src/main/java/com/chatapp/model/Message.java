package com.chatapp.model;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * Classe que representa mensagens trocadas entre cliente e servidor.
     * O campo {@code type} define o propósito da mensagem (login, pedido de
     * lista de salas, mensagem de chat, etc.). Outros campos são usados
     * condicionalmente conforme o tipo: username, roomName, content, data.
     *
     * Observações:
     * - A classe implementa Serializable para ser transmitida via
     *   ObjectOutputStream/ObjectInputStream entre processos.
     * - O campo {@code data} é usado para carregar objetos auxiliares
     *   como listas de {@link com.chatapp.model.Room} (ex: resposta ROOMS_LIST).
     */
    
    public enum Type {
        LOGIN,
        LOGOUT,
        GET_ROOMS,
        ROOMS_LIST,
        JOIN_ROOM,
        LEAVE_ROOM,
        CHAT_MESSAGE,
        ROOM_JOINED,
        ROOM_LEFT,
        ERROR,
        USER_JOINED,
        USER_LEFT
    }
    
    private Type type;
    private String username;
    private String roomName;
    private String content;
    private Object data;
    
    public Message(Type type) {
        this.type = type;
    }
    
    public Message(Type type, String content) {
        this.type = type;
        this.content = content;
    }
    
    // Getters and Setters
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
