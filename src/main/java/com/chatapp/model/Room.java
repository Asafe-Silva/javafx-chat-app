package com.chatapp.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Room implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * Modelo simples de sala de chat.
     * Contém o nome da sala e a lista de usuários (nomes) presentes.
     *
     * Observações:
     * - A lista de usuários é uma ArrayList local; operações de leitura/atualização
     *   nas salas no servidor são sincronizadas pela lógica do ChatServer,
     *   que usa coleções concorrentes para armazenar instâncias Room.
     * - toString fornece uma representação amigável para debugging.
     */
    
    private String name;
    private List<String> users;
    
    public Room(String name) {
        this.name = name;
        this.users = new ArrayList<>();
    }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public List<String> getUsers() { return users; }
    public void setUsers(List<String> users) { this.users = users; }
    
    public void addUser(String username) {
        if (!users.contains(username)) {
            users.add(username);
        }
    }
    
    public void removeUser(String username) {
        users.remove(username);
    }
    
    public int getUserCount() {
        return users.size();
    }
    
    @Override
    public String toString() {
        return name + " (" + users.size() + " usuários)";
    }
}
