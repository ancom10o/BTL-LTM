package com.mycompany.client;

public class SoundEntry {
    private String id;
    private String displayName;
    private String category;
    private String audioFile;

    public SoundEntry(String id, String displayName, String category, String audioFile) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.audioFile = audioFile;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getCategory() { return category; }
    public String getAudioFile() { return audioFile; }
}

