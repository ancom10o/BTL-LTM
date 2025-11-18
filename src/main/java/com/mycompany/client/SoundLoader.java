package com.mycompany.client;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class SoundLoader {
    private static List<SoundEntry> sounds = null;

    public static List<SoundEntry> loadSounds() {
        if (sounds != null) {
            return sounds; // Cache
        }

        sounds = new ArrayList<>();
        try {
            InputStream is = SoundLoader.class.getResourceAsStream("/Sound.csv");
            if (is == null) {
                System.err.println("[SoundLoader] Cannot find Sound.csv");
                return sounds;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            boolean firstLine = true;
            
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false; // Skip header
                    continue;
                }
                
                line = line.trim();
                if (line.isEmpty()) continue;
                
                // Parse CSV: id,display_name,category,audio_file
                String[] parts = line.split(",");
                if (parts.length >= 4) {
                    String id = parts[0].trim();
                    String displayName = parts[1].trim();
                    String category = parts[2].trim();
                    String audioFile = parts[3].trim();
                    
                    sounds.add(new SoundEntry(id, displayName, category, audioFile));
                }
            }
            reader.close();
            
            System.out.println("[SoundLoader] Loaded " + sounds.size() + " sounds");
        } catch (Exception e) {
            System.err.println("[SoundLoader] Error loading sounds: " + e.getMessage());
            e.printStackTrace();
        }
        
        return sounds;
    }

    public static List<SoundEntry> getSoundsByCategory(String category) {
        List<SoundEntry> allSounds = loadSounds();
        List<SoundEntry> filtered = new ArrayList<>();
        for (SoundEntry sound : allSounds) {
            if (sound.getCategory().equals(category)) {
                filtered.add(sound);
            }
        }
        return filtered;
    }
}

