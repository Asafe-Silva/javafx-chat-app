package com.chatapp.client;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ProfileStore {
    private static final Path STORE_DIR = Paths.get(System.getProperty("user.home"), ".javafx-chat-app");
    private static final Path STORE_FILE = STORE_DIR.resolve("profiles.txt");

    public static synchronized List<String> loadProfiles() {
        try {
            if (!Files.exists(STORE_FILE)) return new ArrayList<>();
            List<String> lines = Files.readAllLines(STORE_FILE);
            List<String> out = new ArrayList<>();
            for (String l : lines) {
                String s = l.trim();
                if (!s.isEmpty()) out.add(s);
            }
            return out;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public static synchronized void addProfile(String name) {
        try {
            if (!Files.exists(STORE_DIR)) Files.createDirectories(STORE_DIR);
            List<String> existing = loadProfiles();
            if (!existing.contains(name)) {
                existing.add(name);
                Files.write(STORE_FILE, existing);
            }
        } catch (IOException e) {
            // Ignore errors for now
        }
    }
}
