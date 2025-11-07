package com.chatapp;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
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
    
    // Aumentado para suportar mais 15 clientes além do valor original
    private static final int MAX_CLIENTS = 20;
    private static final int PORT = 8888;
    
    @Override
    public void start(Stage primaryStage) {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Create Server Tab
        ServerTab serverTab = new ServerTab(PORT);
        Tab serverTabUI = new Tab("SERVIDOR", serverTab.getView());
        tabPane.getTabs().add(serverTabUI);
        
        // Create Client Tabs
        for (int i = 1; i <= MAX_CLIENTS; i++) {
            ClientTab clientTab = new ClientTab(i, "localhost", PORT);
            Tab clientTabUI = new Tab("CLIENTE " + i, clientTab.getView());
            tabPane.getTabs().add(clientTabUI);
        }
        
        Scene scene = new Scene(tabPane, 1000, 700);
        // Aplicar a paleta de cores (arquivo CSS em resources)
        try {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        } catch (Exception e) {
            // Se o CSS não for encontrado, segue sem stylesheet (não quebra a inicialização)
        }

        // Novo título solicitado
        primaryStage.setTitle("Fala Me!");
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
