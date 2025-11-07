package com.chatapp.client;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class ClientTab {
    /**
     * Representa a aba de um cliente na interface JavaFX.
     * Cada aba encapsula três "views" principais:
     *  - loginView: esse é o formulário inicial onde o usuário informa o nome;
     *  - roomSelectionView: lista de salas disponíveis e controles para entrar;
     *  - chatView: visualização da conversa dentro de uma sala (mensagens + envio).
     *
     * Esta classe cria e gerencia a UI local e delega a lógica de rede para
     * {@link com.chatapp.client.ChatClient}. Quando eventos de UI ocorrem
     * (login, entrar em sala, enviar mensagem) a classe invoca métodos do
     * ChatClient. Do lado oposto, o ChatClient chama callbacks como
     * updateRoomList/appendChatMessage para atualizar a UI via Platform.runLater().
     *
     * Observações:
     * - A UI é atualizada sempre em JavaFX Application Thread mediante
     *   chamadas a Platform.runLater para evitar problemas de threading.
     * - Validações simples (nome vazio, seleção de sala, mensagem vazia) são
     *   realizadas localmente antes de enviar solicitações ao servidor.
     */
    
    private VBox view;
    private int clientId;
    private String host;
    private int port;
    
    private ChatClient client;
    
    // Login view
    private VBox loginView;
    private TextField usernameField;
    
    // Room selection view
    private VBox roomSelectionView;
    private ListView<String> roomListView;
    
    // Chat view
    private VBox chatView;
    private TextArea chatArea;
    private TextField messageField;
    private Label currentRoomLabel;
    
    public ClientTab(int clientId, String host, int port) {
        this.clientId = clientId;
        this.host = host;
        this.port = port;
        initializeViews();
        showLoginView();
    }
    
    private void initializeViews() {
        view = new VBox();
        
        // Initialize login view
        initializeLoginView();
        
        // Initialize room selection view
        initializeRoomSelectionView();
        
        // Initialize chat view
        initializeChatView();
    }
    
    private void initializeLoginView() {
        loginView = new VBox(20);
        loginView.setPadding(new Insets(50));
        loginView.setAlignment(Pos.CENTER);
        
        Label titleLabel = new Label("CLIENTE " + clientId);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        
        Label instructionLabel = new Label("Digite seu nome de usuário:");
        instructionLabel.setFont(Font.font("System", 14));
        
        usernameField = new TextField();
        usernameField.setPromptText("Nome de usuário");
        usernameField.setMaxWidth(300);
        
        Button loginButton = new Button("Entrar");
        loginButton.setPrefWidth(150);
        loginButton.setOnAction(e -> handleLogin());
        
        usernameField.setOnAction(e -> handleLogin());
        
        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: red;");
        
        loginView.getChildren().addAll(
            titleLabel,
            instructionLabel,
            usernameField,
            loginButton,
            statusLabel
        );
    }
    
    private void initializeRoomSelectionView() {
        roomSelectionView = new VBox(15);
        roomSelectionView.setPadding(new Insets(20));
        
        Label titleLabel = new Label("SELECIONE UMA SALA");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 20));
        
        Label usernameLabel = new Label("");
        usernameLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
        
        roomListView = new ListView<>();
        roomListView.setPrefHeight(400);
        
        Button refreshButton = new Button("Atualizar Salas");
        refreshButton.setOnAction(e -> refreshRooms());
        
        Button enterButton = new Button("Entrar na Sala");
        enterButton.setPrefWidth(150);
        enterButton.setOnAction(e -> handleEnterRoom());
        
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().addAll(refreshButton, enterButton);
        
        roomSelectionView.getChildren().addAll(
            titleLabel,
            usernameLabel,
            new Label("Salas disponíveis:"),
            roomListView,
            buttonBox
        );
    }
    
    private void initializeChatView() {
        chatView = new VBox(10);
        chatView.setPadding(new Insets(20));
        
        currentRoomLabel = new Label("");
        currentRoomLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        
        Button backButton = new Button("← Voltar");
        backButton.setOnAction(e -> handleLeaveRoom());
        
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.getChildren().addAll(backButton, currentRoomLabel);
        
        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setPrefHeight(450);
        
        HBox messageBox = new HBox(10);
        messageField = new TextField();
        messageField.setPromptText("Digite sua mensagem...");
        messageField.setPrefWidth(700);
        messageField.setOnAction(e -> handleSendMessage());
        
        Button sendButton = new Button("Enviar");
        sendButton.setPrefWidth(100);
        sendButton.setOnAction(e -> handleSendMessage());
        
        messageBox.getChildren().addAll(messageField, sendButton);
        
        chatView.getChildren().addAll(
            headerBox,
            new Separator(),
            chatArea,
            messageBox
        );
    }
    
    private void handleLogin() {
        String username = usernameField.getText().trim();
        if (username.isEmpty()) {
            showError("Por favor, digite um nome de usuário");
            return;
        }
        
        client = new ChatClient(host, port, username, this);
        if (client.connect()) {
            showRoomSelectionView(username);
            refreshRooms();
        } else {
            showError("Não foi possível conectar ao servidor");
        }
    }
    
    private void handleEnterRoom() {
        String selectedRoom = roomListView.getSelectionModel().getSelectedItem();
        if (selectedRoom != null) {
            String roomName = selectedRoom.split(" - ")[0];
            client.joinRoom(roomName);
        }
    }
    
    private void handleLeaveRoom() {
        client.leaveRoom();
    }
    
    private void handleSendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            client.sendChatMessage(message);
            messageField.clear();
        }
    }
    
    private void refreshRooms() {
        if (client != null) {
            client.requestRooms();
        }
    }
    
    public void showLoginView() {
        Platform.runLater(() -> {
            view.getChildren().clear();
            view.getChildren().add(loginView);
        });
    }
    
    public void showRoomSelectionView(String username) {
        Platform.runLater(() -> {
            Label usernameLabel = (Label) roomSelectionView.getChildren().get(1);
            usernameLabel.setText("Usuário: " + username);
            view.getChildren().clear();
            view.getChildren().add(roomSelectionView);
        });
    }
    
    public void showChatView(String roomName) {
        Platform.runLater(() -> {
            currentRoomLabel.setText("Sala: " + roomName);
            chatArea.clear();
            view.getChildren().clear();
            view.getChildren().add(chatView);
        });
    }
    
    public void updateRoomList(java.util.List<com.chatapp.model.Room> rooms) {
        Platform.runLater(() -> {
            roomListView.getItems().clear();
            for (com.chatapp.model.Room room : rooms) {
                roomListView.getItems().add(room.getName() + " - " + room.getUserCount() + " usuário(s)");
            }
        });
    }
    
    public void appendChatMessage(String message) {
        Platform.runLater(() -> {
            chatArea.appendText(message + "\n");
        });
    }
    
    private void showError(String error) {
        Platform.runLater(() -> {
            Label statusLabel = (Label) loginView.getChildren().get(4);
            statusLabel.setText(error);
        });
    }
    
    public VBox getView() {
        return view;
    }
}
