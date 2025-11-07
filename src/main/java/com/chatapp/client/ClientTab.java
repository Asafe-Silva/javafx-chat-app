package com.chatapp.client;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    // lista de clientes salvos (favoritos)
    private ListView<String> savedClientsList;
    
    // Chat view
    private VBox chatView;
    // messagesBox holds each message as a Label so we can style them individually
    private ScrollPane messagesScroll;
    private VBox messagesBox;
    private TextField messageField;
    private Label currentRoomLabel;
    // status label exibido na view de login (mantido como campo para acesso fácil)
    private Label statusLabel;
    // Color picker para escolher cor das próprias mensagens
    private javafx.scene.control.ColorPicker colorPicker;
    private String selectedColor = "#FFD700"; // default dourado
    private String previousColor = selectedColor;
    // typing and erasing indicators
    private Label typingIndicator;
    private Label erasingIndicator;
    // scheduler para debounce de typing/erasing
    private final ScheduledExecutorService typingScheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> typingFuture;
    private volatile boolean isTyping = false;
    private ScheduledFuture<?> erasingFuture;
    private volatile boolean isErasing = false;
    private static final long TYPING_DEBOUNCE_MS = 500;
    
    // Construtor legado (mantido para compatibilidade)
    public ClientTab(int clientId, String host, int port) {
        this.clientId = clientId;
        this.host = host;
        this.port = port;
        initializeViews();
        showLoginView();
    }

    // Novo construtor: única aba de cliente (usuário escolhe qual cliente usar dentro da aba)
    public ClientTab(String host, int port) {
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
        loginView = new VBox(10);
        loginView.setPadding(new Insets(20));
        loginView.setAlignment(Pos.CENTER);

        HBox container = new HBox(20);

    // Saved clients list (favoritos)
        VBox savedBox = new VBox(8);
        savedBox.setPrefWidth(220);
        Label savedLabel = new Label("Clientes Salvos");
        savedClientsList = new ListView<>();
        savedClientsList.setPrefHeight(200);
        Button removeSaved = new Button("Remover");
        removeSaved.setPrefWidth(120);
        removeSaved.setOnAction(e -> {
            String sel = savedClientsList.getSelectionModel().getSelectedItem();
            if (sel != null) savedClientsList.getItems().remove(sel);
        });
        savedClientsList.setOnMouseClicked(e -> {
            String sel = savedClientsList.getSelectionModel().getSelectedItem();
            if (sel != null) usernameField.setText(sel);
        });
        // populate from global profile store
        java.util.List<String> profiles = ProfileStore.loadProfiles();
        savedClientsList.getItems().addAll(profiles);
        savedBox.getChildren().addAll(savedLabel, savedClientsList, removeSaved);

        // Login form
        VBox loginForm = new VBox(8);
        Label titleLabel = new Label((clientId > 0) ? "CLIENTE " + clientId : "CLIENTE");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 20));
        Label instructionLabel = new Label("Digite seu nome de usuário:");
        instructionLabel.setFont(Font.font("System", 13));
        usernameField = new TextField();
        usernameField.setPromptText("Nome de usuário");
        usernameField.setMaxWidth(300);
        Button loginButton = new Button("Entrar");
        loginButton.setPrefWidth(120);
        loginButton.setOnAction(e -> handleLogin());
        usernameField.setOnAction(e -> handleLogin());
        Button starButton = new Button("★ Salvar");
        starButton.setPrefWidth(120);
        starButton.setOnAction(e -> {
            String u = usernameField.getText().trim();
            if (!u.isEmpty() && !savedClientsList.getItems().contains(u)) {
                savedClientsList.getItems().add(u);
                ProfileStore.addProfile(u);
            }
        });
        // cria o statusLabel como campo da classe para que outros métodos possam atualizá-lo
        this.statusLabel = new Label("");
        this.statusLabel.setStyle("-fx-text-fill: red;");
        loginForm.getChildren().addAll(titleLabel, instructionLabel, usernameField, loginButton, starButton, this.statusLabel);

        container.getChildren().addAll(savedBox, loginForm);
        loginView.getChildren().add(container);
    }
    
    private void initializeRoomSelectionView() {
        roomSelectionView = new VBox(15);
        roomSelectionView.setPadding(new Insets(20));
        Label titleLabel = new Label("SELECIONE UMA SALA");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 18));

        Label usernameLabel = new Label("");
        usernameLabel.setFont(Font.font("System", FontWeight.NORMAL, 13));

        roomListView = new ListView<>();
        roomListView.setPrefHeight(320);

        Button refreshButton = new Button("Atualizar");
        refreshButton.setPrefWidth(100);
        refreshButton.setOnAction(e -> refreshRooms());

        Button enterButton = new Button("Entrar");
        enterButton.setPrefWidth(100);
        enterButton.setOnAction(e -> handleEnterRoom());

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().addAll(refreshButton, enterButton);

        roomSelectionView.getChildren().addAll(titleLabel, usernameLabel, new Label("Salas disponíveis:"), roomListView, buttonBox);
    }
    
    private void initializeChatView() {
    chatView = new VBox(10);
        chatView.setPadding(new Insets(20));
        
    currentRoomLabel = new Label("");
        currentRoomLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        Button backButton = new Button("← Voltar");
        backButton.setOnAction(e -> handleLeaveRoom());
        backButton.setPrefWidth(120);
    Button reportRoomButton = new Button("Denunciar Sala");
        reportRoomButton.setPrefWidth(120);
        reportRoomButton.setOnAction(e -> {
            if (currentRoomLabel.getText() != null && !currentRoomLabel.getText().isEmpty()) {
                String roomName = currentRoomLabel.getText().replaceFirst("Sala: ", "");
                if (client != null) client.reportRoom(roomName, "Usuário denunciou a sala");
                appendChatMessage("*** Denúncia enviada para a sala: " + roomName + " ***");
            }
        });
        
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        // typing indicator
        typingIndicator = new Label("");
        typingIndicator.setStyle("-fx-font-style: italic; -fx-text-fill: gray;");

        // messages area (VBox inside ScrollPane) to allow colored messages
        messagesBox = new VBox(6);
        messagesBox.setPrefWidth(800);
        messagesScroll = new ScrollPane(messagesBox);
        messagesScroll.setFitToWidth(true);
        messagesScroll.setPrefHeight(450);
        
        HBox messageBox = new HBox(10);
        messageField = new TextField();
        messageField.setPromptText("Digite sua mensagem...");
        messageField.setPrefWidth(600);
        messageField.setOnAction(e -> handleSendMessage());
        // debounce: key pressed for erase detection, key typed for normal typing
        messageField.setOnKeyPressed(e -> {
            if (client == null) return;
            if (currentRoomLabel.getText() == null || currentRoomLabel.getText().isEmpty()) return;
            String rn = currentRoomLabel.getText().replaceFirst("Sala: ", "");
            if (e.getCode() == KeyCode.BACK_SPACE || e.getCode() == KeyCode.DELETE) {
                // send erasing start
                if (!isErasing) {
                    client.sendErasing(rn, true);
                    isErasing = true;
                    showErasingIndicator(usernameField.getText().trim());
                }
                // schedule stop erasing
                if (erasingFuture != null) erasingFuture.cancel(false);
                erasingFuture = typingScheduler.schedule(() -> {
                    client.sendErasing(rn, false);
                    isErasing = false;
                    clearErasingIndicator(usernameField.getText().trim());
                }, TYPING_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
            }
        });

        messageField.setOnKeyTyped(e -> {
            if (client == null) return;
            if (currentRoomLabel.getText() == null || currentRoomLabel.getText().isEmpty()) return;
            String rn = currentRoomLabel.getText().replaceFirst("Sala: ", "");
            // start typing if not already
            if (!isTyping) {
                client.sendTyping(rn, true);
                isTyping = true;
                showTypingIndicator(usernameField.getText().trim());
            }
            // reschedule stop typing
            if (typingFuture != null) typingFuture.cancel(false);
            typingFuture = typingScheduler.schedule(() -> {
                client.sendTyping(rn, false);
                isTyping = false;
                clearTypingIndicator(usernameField.getText().trim());
            }, TYPING_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        });
        
        Button sendButton = new Button("Enviar");
        sendButton.setPrefWidth(120);
        sendButton.setOnAction(e -> handleSendMessage());

        // Color picker for user's text color
        colorPicker = new javafx.scene.control.ColorPicker(Color.web(selectedColor));
        colorPicker.setOnAction(e -> {
            Color c = colorPicker.getValue();
            String hex = String.format("#%02x%02x%02x", (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255));
            // prevent pure black which is unreadable on black background
            if ( (int)(c.getRed()*255) < 8 && (int)(c.getGreen()*255) < 8 && (int)(c.getBlue()*255) < 8) {
                // revert to previous
                colorPicker.setValue(Color.web(previousColor));
                Alert a = new Alert(Alert.AlertType.WARNING, "Cor preta não permitida (fundo escuro). Escolha outra cor.", ButtonType.OK);
                a.showAndWait();
                return;
            }
            previousColor = hex;
            selectedColor = hex;
        });
        
        messageBox.getChildren().addAll(messageField, sendButton, colorPicker);
        // contexto para denunciar mensagem selecionada
        headerBox.getChildren().addAll(backButton, currentRoomLabel, reportRoomButton);
        // erasing indicator (separate from typing)
        erasingIndicator = new Label("");
        erasingIndicator.setStyle("-fx-font-style: italic; -fx-text-fill: gray;");
        HBox infoBox = new HBox(10);
        infoBox.getChildren().addAll(typingIndicator, erasingIndicator);
        chatView.getChildren().addAll(headerBox, infoBox, new Separator(), messagesScroll, messageBox);
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
        if (client != null && currentRoomLabel.getText() != null && !currentRoomLabel.getText().isEmpty()) {
            String rn = currentRoomLabel.getText().replaceFirst("Sala: ", "");
            client.sendTyping(rn, false);
        }
        client.leaveRoom();
        typingIndicator.setText("");
    }
    
    private void handleSendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            client.sendChatMessage(message, selectedColor);
            // notify stopped typing after send
            if (currentRoomLabel.getText() != null && !currentRoomLabel.getText().isEmpty()) {
                String rn = currentRoomLabel.getText().replaceFirst("Sala: ", "");
                client.sendTyping(rn, false);
            }
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
            messagesBox.getChildren().clear();
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
    
    // nova versão: exibe mensagem com cor e botão de report no rótulo
    public void appendChatMessage(String username, String message, String color) {
        Platform.runLater(() -> {
            String text = (username != null && !username.isEmpty()) ? (username + ": " + message) : message;
            Label lbl = new Label(text);
            String useColor = color;
            try {
                // se for mensagem do sistema e não foi passada cor, force branco
                if ((username == null || username.isEmpty()) && (useColor == null || useColor.isEmpty())) {
                    useColor = "#FFFFFF";
                }
                if (useColor != null) lbl.setStyle("-fx-text-fill: " + useColor + ";");
            } catch (Exception ex) { /* ignore styling errors */ }
            // context menu per message to allow reporting
            ContextMenu cm = new ContextMenu();
            MenuItem reportMsg = new MenuItem("Denunciar mensagem");
            // attach author for reporting
            lbl.setUserData(username);
            reportMsg.setOnAction(e -> {
                String target = (String) lbl.getUserData();
                if (client != null && target != null) client.reportMessage(target, message);
                appendChatMessage(null, "*** Mensagem denunciada ao servidor ***", "#808080");
            });
            cm.getItems().add(reportMsg);
            lbl.setContextMenu(cm);
            messagesBox.getChildren().add(lbl);
            // scroll to bottom
            messagesScroll.layout();
            messagesScroll.setVvalue(1.0);
        });
    }

    public void appendChatMessage(String message) {
        // mensagens automáticas do sistema devem ser sempre brancas para
        // garantir visibilidade no fundo escuro.
        appendChatMessage(null, message, "#FFFFFF");
    }

    public void showTypingIndicator(String username) {
        Platform.runLater(() -> {
            typingIndicator.setText(username + " está digitando...");
        });
    }

    public void clearTypingIndicator(String username) {
        Platform.runLater(() -> {
            // apenas limpar mensagem (implementação simples)
            typingIndicator.setText("");
        });
    }

    public void showErasingIndicator(String username) {
        Platform.runLater(() -> {
            erasingIndicator.setText(username + " está apagando...");
        });
    }

    public void clearErasingIndicator(String username) {
        Platform.runLater(() -> {
            erasingIndicator.setText("");
        });
    }
    
    public void showError(String error) {
        Platform.runLater(() -> {
            if (this.statusLabel != null) {
                this.statusLabel.setText(error);
            }
        });
    }
    
    public VBox getView() {
        return view;
    }
}
