package com.chatapp.server;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import com.chatapp.model.Room;

public class ServerTab {
    /**
     * Aba da interface que permite controlar o servidor e visualizar
     * informações como salas ativas e o log de eventos.
     *
     * Responsabilidades:
     * - Fornecer controle para criar salas (invoca ChatServer.createRoom)
     * - Mostrar lista de salas e usuários conectados (updateRoomList)
     * - Exibir logs de operação do servidor (log)
     * - Iniciar/encerrar a instância de ChatServer em threads separadas
     *   (startServer/stopServer).
     *
     * Observação sobre threading: atualizações na UI são feitas via
     * Platform.runLater para garantir execução na JavaFX Application Thread.
     */
    
    private VBox view;
    private ListView<String> roomListView;
    private TextArea logArea;
    private ChatServer server;
    private int port;
    
    public ServerTab(int port) {
        this.port = port;
        initializeView();
    }
    
    private void initializeView() {
        view = new VBox(15);
        view.setPadding(new Insets(20));
        view.setAlignment(Pos.TOP_CENTER);
        
        // Title
        Label titleLabel = new Label("SERVIDOR DE CHAT");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        
        // Room creation section
        HBox roomCreationBox = new HBox(10);
        roomCreationBox.setAlignment(Pos.CENTER);
        
        TextField roomNameField = new TextField();
        roomNameField.setPromptText("Nome da sala");
        roomNameField.setPrefWidth(200);
        
        Button createRoomButton = new Button("Criar Sala");
        createRoomButton.setOnAction(e -> {
            String roomName = roomNameField.getText().trim();
            if (!roomName.isEmpty()) {
                if (server != null && server.createRoom(roomName)) {
                    roomNameField.clear();
                    log("Sala criada: " + roomName);
                } else {
                    log("Erro: Não foi possível criar a sala (máximo 5 salas)");
                }
            }
        });
        
        roomCreationBox.getChildren().addAll(
            new Label("Nova Sala:"), 
            roomNameField, 
            createRoomButton
        );
        
        // Room list
        Label roomsLabel = new Label("Salas Ativas:");
        roomsLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        roomListView = new ListView<>();
        roomListView.setPrefHeight(200);
        
        // Server log
        Label logLabel = new Label("Log do Servidor:");
        logLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(250);
        logArea.setWrapText(true);
        
        view.getChildren().addAll(
            titleLabel,
            new Separator(),
            roomCreationBox,
            new Separator(),
            roomsLabel,
            roomListView,
            logLabel,
            logArea
        );
    }
    
    public void startServer() {
        server = new ChatServer(port, this);
        new Thread(() -> server.start()).start();
        log("Servidor iniciado na porta " + port);
    }
    
    public void stopServer() {
        if (server != null) {
            server.stop();
            log("Servidor encerrado");
        }
    }
    
    public void updateRoomList(java.util.List<Room> rooms) {
        Platform.runLater(() -> {
            roomListView.getItems().clear();
            for (Room room : rooms) {
                String roomInfo = room.getName() + " - " + room.getUserCount() + " usuário(s)";
                if (room.getUserCount() > 0) {
                    roomInfo += " [" + String.join(", ", room.getUsers()) + "]";
                }
                roomListView.getItems().add(roomInfo);
            }
        });
    }
    
    public void log(String message) {
        Platform.runLater(() -> {
            logArea.appendText("[" + java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
            ) + "] " + message + "\n");
        });
    }
    
    public VBox getView() {
        return view;
    }
}
