package com.chatapp;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import com.chatapp.server.ServerTab;
import com.chatapp.client.ClientTab;

public class ChatApplication extends Application {
    /**
     * Aplicação JavaFX principal que monta a interface gráfica com abas.
     * Uma aba é dedicada ao servidor (controle/visualização das salas e logs)
     * e várias abas simulam clientes distintos conectando ao mesmo servidor.
     *
     * Fluxo principal:
     * - Cria o ServerTab (GUI + instância de ChatServer) e inicia o servidor.
     * - Cria N abas de cliente (ClientTab) apontando para o servidor local.
     * - Ao fechar a janela, encerra o servidor e finaliza a aplicação.
     *
     * Notas:
     * - Esta classe só monta a UI e delega comportamento ao ServerTab/ClientTab.
     * - As constantes MAX_CLIENTS e PORT controlam quantos clientes virtuais
     *   serão visíveis e em qual porta o servidor escuta.
     */
    
    // Mantemos apenas duas abas: SERVIDOR e CLIENTE (a seleção de usuários
    // fica dentro da aba CLIENTE)
    private static final int PORT = 8888;
    
    @Override
    public void start(Stage primaryStage) {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Create Server Tab
        ServerTab serverTab = new ServerTab(PORT);
        Tab serverTabUI = new Tab("SERVIDOR", serverTab.getView());
        tabPane.getTabs().add(serverTabUI);
        
        // Create a Clients container tab which allows adding multiple client instances
        BorderPane clientsContainer = new BorderPane();
        // Top controls: botão para criar novo cliente
        HBox topControls = new HBox(10);
        Button addClientBtn = new Button("Adicionar Cliente");
        topControls.getChildren().add(addClientBtn);
        clientsContainer.setTop(topControls);

        // Inner TabPane to hold multiple clients
        TabPane clientsPane = new TabPane();
        clientsPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        clientsContainer.setCenter(clientsPane);

        // Counter para identificar clientes
        final int[] clientCounter = {0};
        addClientBtn.setOnAction(e -> {
            clientCounter[0]++;
            ClientTab ct = new ClientTab(clientCounter[0], "localhost", PORT);
            Tab t = new Tab("CLIENTE " + clientCounter[0], ct.getView());
            clientsPane.getTabs().add(t);
            clientsPane.getSelectionModel().select(t);
        });

        // adiciona a aba de clientes ao TabPane principal
        Tab clientsTab = new Tab("CLIENTES", clientsContainer);
        tabPane.getTabs().add(clientsTab);
        
        Scene scene = new Scene(tabPane, 1000, 700);
        // Aplicar a paleta de cores (arquivo CSS em resources)
        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception e) {
            // Se o CSS não for encontrado, segue sem stylesheet (não quebra a inicialização)
        }

    // Título do projeto
    primaryStage.setTitle("Fale Me!");
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // Start server when application starts
        serverTab.startServer();
        
        // Cleanup on close
        primaryStage.setOnCloseRequest(event -> {
            serverTab.stopServer();
            System.exit(0);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
