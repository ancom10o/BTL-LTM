package com.mycompany.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class QuestionGenerator {
    private final long seed;
    private final List<SoundEntry> allSounds;

    public QuestionGenerator(long seed) {
        this.seed = seed;
        this.allSounds = SoundLoader.loadSounds();
    }

    public Question generateQuestion(int round) {
        if (allSounds.isEmpty()) {
            // Fallback nếu không load được sounds
            return new Question(
                "Đây là âm thanh của con vật nào?",
                new String[]{"Sư tử", "Khỉ", "Chó", "Trâu"},
                0,
                null
            );
        }

        // Dùng seed + round để tạo Random riêng cho mỗi round
        // Đảm bảo cả 2 client có cùng câu hỏi cho cùng round
        Random roundRandom = new Random(seed + round);
        
        // Bốc ngẫu nhiên một sound (dùng roundRandom để đồng bộ)
        SoundEntry correctSound = allSounds.get(roundRandom.nextInt(allSounds.size()));
        String category = correctSound.getCategory();
        String correctAnswer = correctSound.getDisplayName();

        // Tạo câu hỏi dựa trên category
        String questionText;
        switch (category) {
            case "ANIMAL":
                questionText = "Đây là âm thanh của con vật nào?";
                break;
            case "VEHICLE":
                questionText = "Đây là âm thanh của phương tiện nào?";
                break;
            case "INSTRUMENT":
                questionText = "Đây là âm thanh của nhạc cụ nào?";
                break;
            default:
                questionText = "Đây là âm thanh gì?";
        }

        // Lấy tất cả sounds cùng category
        List<SoundEntry> sameCategorySounds = SoundLoader.getSoundsByCategory(category);
        
        // Loại bỏ đáp án đúng
        List<SoundEntry> wrongOptions = new ArrayList<>();
        for (SoundEntry sound : sameCategorySounds) {
            if (!sound.getId().equals(correctSound.getId())) {
                wrongOptions.add(sound);
            }
        }

        // Bốc ngẫu nhiên 3 đáp án sai (dùng roundRandom để đồng bộ)
        Collections.shuffle(wrongOptions, roundRandom);
        List<String> answers = new ArrayList<>();
        answers.add(correctAnswer);
        
        int wrongCount = Math.min(3, wrongOptions.size());
        for (int i = 0; i < wrongCount; i++) {
            answers.add(wrongOptions.get(i).getDisplayName());
        }
        
        // Nếu không đủ 3 đáp án sai, thêm placeholder
        while (answers.size() < 4) {
            answers.add("Không xác định");
        }

        // Xáo trộn thứ tự đáp án (dùng roundRandom để đồng bộ)
        Collections.shuffle(answers, roundRandom);
        
        // Tìm vị trí đáp án đúng
        int correctIndex = answers.indexOf(correctAnswer);
        if (correctIndex == -1) {
            correctIndex = 0; // Fallback
        }

        return new Question(questionText, answers.toArray(new String[4]), correctIndex, correctSound);
    }

    public static class Question {
        private final String questionText;
        private final String[] answers; // 4 đáp án
        private final int correctIndex; // 0-3
        private final SoundEntry sound; // Sound để phát

        public Question(String questionText, String[] answers, int correctIndex, SoundEntry sound) {
            this.questionText = questionText;
            this.answers = answers;
            this.correctIndex = correctIndex;
            this.sound = sound;
        }

        public String getQuestionText() { return questionText; }
        public String[] getAnswers() { return answers; }
        public int getCorrectIndex() { return correctIndex; }
        public SoundEntry getSound() { return sound; }
    }
}

