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
    private BorderPane mainPane;
    private VBox centerBox;
    private VBox clientsPane;
    
    public ServerTab(int port) {
        this.port = port;
        initializeView();
    }

    private void openClientsView() {
        if (server == null) return;
        if (clientsPane == null) buildClientsPane();
        mainPane.setCenter(clientsPane);
    }

    private void buildClientsPane() {
        clientsPane = new VBox(10);
        clientsPane.setPadding(new Insets(10));

        HBox root = new HBox(10);

        // Left: detalhes do cliente selecionado
        VBox leftBox = new VBox(8);
        leftBox.setPrefWidth(250);
        Label detailsTitle = new Label("Informações do Cliente");
        TextArea detailsArea = new TextArea();
        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        leftBox.getChildren().addAll(detailsTitle, detailsArea);

        // Center: ativos
        VBox center = new VBox(8);
        Label activeLabel = new Label("Ativos");
        ListView<String> activeList = new ListView<>();
        activeList.setPrefWidth(300);
        activeList.getItems().addAll(server.getActiveClientsInfo());
        center.getChildren().addAll(activeLabel, activeList);

        // Right: inativos
        VBox rightBox = new VBox(8);
        Label inactiveLabel = new Label("Inativos");
        ListView<String> inactiveList = new ListView<>();
        inactiveList.setPrefWidth(300);
        inactiveList.getItems().addAll(server.getInactiveClientsInfo());
        rightBox.getChildren().addAll(inactiveLabel, inactiveList);

        // Interações: expulsar usuário
        Button kickButton = new Button("Expulsar Usuário");
        kickButton.setPrefWidth(140);
        kickButton.setOnAction(e -> {
            String sel = activeList.getSelectionModel().getSelectedItem();
            if (sel != null) {
                String user = sel.split("\\|")[0].trim();
                server.kickUser(user);
                activeList.getItems().setAll(server.getActiveClientsInfo());
            }
        });

        activeList.setOnMouseClicked(e -> {
            String sel = activeList.getSelectionModel().getSelectedItem();
            if (sel != null) detailsArea.setText(sel);
        });

        root.getChildren().addAll(leftBox, center, rightBox);

        Button backToRooms = new Button("Voltar");
        backToRooms.setPrefWidth(140);
        backToRooms.setOnAction(e -> mainPane.setCenter(centerBox));

        clientsPane.getChildren().addAll(root, kickButton, backToRooms);
    }
    
    private void initializeView() {
        view = new VBox(10);
        view.setPadding(new Insets(10));
        view.setAlignment(Pos.TOP_CENTER);

        // Title
        Label titleLabel = new Label("SERVIDOR DE CHAT");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 24));

        mainPane = new BorderPane();

        // centerBox: criação de salas e lista
        centerBox = new VBox(12);
        centerBox.setPadding(new Insets(10));

        HBox roomCreationBox = new HBox(12);
        roomCreationBox.setAlignment(Pos.CENTER_LEFT);

        Label newRoomLabel = new Label("Nova Sala:");
        TextField roomNameField = new TextField();
        roomNameField.setPromptText("Nome da sala");
        roomNameField.setPrefWidth(300);

        Button createRoomButton = new Button("Criar Sala");
        createRoomButton.setPrefWidth(140);
        createRoomButton.setOnAction(e -> {
            String roomName = roomNameField.getText().trim();
            if (!roomName.isEmpty()) {
                if (server != null && server.createRoom(roomName)) {
                    roomNameField.clear();
                    log("Sala criada: " + roomName);
                } else {
                    log("Erro: Não foi possível criar a sala (máximo 15 salas)");
                }
            }
        });

        Button deleteRoomButton = new Button("Deletar Sala");
        deleteRoomButton.setPrefWidth(140);
        deleteRoomButton.setOnAction(e -> {
            String selected = roomListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                String roomName = selected.split(" - ")[0];
                if (server != null && server.deleteRoom(roomName)) {
                    log("Sala deletada: " + roomName);
                }
            }
        });

        Button clientsButton = new Button("Clientes");
        clientsButton.setPrefWidth(140);
        clientsButton.setOnAction(e -> openClientsView());

        roomCreationBox.getChildren().addAll(newRoomLabel, roomNameField, createRoomButton, deleteRoomButton, clientsButton);

        Label roomsLabel = new Label("Salas Ativas:");
        roomsLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        roomListView = new ListView<>();
        roomListView.setPrefHeight(320);

        centerBox.getChildren().addAll(roomCreationBox, roomsLabel, roomListView);
        mainPane.setCenter(centerBox);

        // right: log area (ocultável)
        VBox rightBox = new VBox(8);
        rightBox.setPadding(new Insets(10));
        rightBox.setPrefWidth(320);

        Label logLabel = new Label("Log do Servidor:");
        logLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        ToggleButton toggleLog = new ToggleButton("Ocultar Log");
        toggleLog.setOnAction(e -> {
            boolean hide = toggleLog.isSelected();
            logArea.setVisible(!hide);
            toggleLog.setText(hide ? "Mostrar Log" : "Ocultar Log");
        });

        Button clearLog = new Button("Limpar Log");
        clearLog.setPrefWidth(140);
        clearLog.setOnAction(e -> logArea.clear());

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefHeight(500);

        rightBox.getChildren().addAll(logLabel, toggleLog, clearLog, logArea);
        mainPane.setRight(rightBox);

        view.getChildren().addAll(titleLabel, new Separator(), mainPane);
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
