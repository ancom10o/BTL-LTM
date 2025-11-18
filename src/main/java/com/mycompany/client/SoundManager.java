package com.mycompany.client;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

/**
 * Quản lý trạng thái âm thanh toàn cục cho toàn bộ ứng dụng
 */
public class SoundManager {
    private static SoundManager instance;
    private boolean soundEnabled = true; // Mặc định bật âm thanh
    private MediaPlayer backgroundMusicPlayer;
    
    private SoundManager() {
        // Singleton pattern
    }
    
    public static SoundManager getInstance() {
        if (instance == null) {
            instance = new SoundManager();
        }
        return instance;
    }
    
    /**
     * Kiểm tra xem âm thanh có được bật không
     */
    public boolean isSoundEnabled() {
        return soundEnabled;
    }
    
    /**
     * Bật/tắt âm thanh
     */
    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
        
        // Nếu tắt âm thanh, dừng nhạc nền
        if (!enabled && backgroundMusicPlayer != null) {
            backgroundMusicPlayer.stop();
        }
        // Nếu bật âm thanh và có nhạc nền, phát lại
        else if (enabled && backgroundMusicPlayer != null) {
            backgroundMusicPlayer.play();
        }
    }
    
    /**
     * Phát nhạc nền (lặp lại)
     */
    public void playBackgroundMusic(String audioFile) {
        // Dừng nhạc nền cũ nếu có
        stopBackgroundMusic();
        
        if (!soundEnabled) {
            return; // Không phát nếu âm thanh bị tắt
        }
        
        try {
            String resourcePath = audioFile.startsWith("/") ? audioFile : "/" + audioFile;
            java.net.URL resourceUrl = getClass().getResource(resourcePath);
            if (resourceUrl == null) {
                System.err.println("Cannot find background music: " + resourcePath);
                return;
            }
            
            Media media = new Media(resourceUrl.toExternalForm());
            backgroundMusicPlayer = new MediaPlayer(media);
            backgroundMusicPlayer.setVolume(0.5); // Volume nhạc nền thấp hơn
            backgroundMusicPlayer.setCycleCount(MediaPlayer.INDEFINITE); // Lặp lại vô hạn
            backgroundMusicPlayer.setOnError(() -> {
                System.err.println("Background music error: " + backgroundMusicPlayer.getError());
            });
            backgroundMusicPlayer.play();
        } catch (Exception e) {
            System.err.println("Error playing background music: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Dừng nhạc nền
     */
    public void stopBackgroundMusic() {
        if (backgroundMusicPlayer != null) {
            backgroundMusicPlayer.stop();
            backgroundMusicPlayer.dispose();
            backgroundMusicPlayer = null;
        }
    }
    
    /**
     * Phát âm thanh (kiểm tra trạng thái bật/tắt)
     */
    public MediaPlayer playSound(String audioFile, double volume) {
        if (!soundEnabled) {
            return null; // Không phát nếu âm thanh bị tắt
        }
        
        try {
            String resourcePath = audioFile.startsWith("/") ? audioFile : "/" + audioFile;
            java.net.URL resourceUrl = getClass().getResource(resourcePath);
            if (resourceUrl == null) {
                System.err.println("Cannot find sound resource: " + resourcePath);
                return null;
            }
            
            Media media = new Media(resourceUrl.toExternalForm());
            MediaPlayer player = new MediaPlayer(media);
            player.setVolume(volume);
            player.setOnError(() -> {
                System.err.println("MediaPlayer error: " + player.getError());
            });
            player.play();
            return player;
        } catch (Exception e) {
            System.err.println("Error playing sound: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}

