-- RESET SCHEMA
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;


---------------------------------------------------------
--  USERS (player accounts)
---------------------------------------------------------
CREATE TABLE public.users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    password TEXT NOT NULL,

    wins INT DEFAULT 0,
    total_matches INT DEFAULT 0,

    created_at TIMESTAMP DEFAULT NOW()
);

INSERT INTO public.users (username, password) VALUES
('test1', 'test123'),
('test2', 'test123'),
('test3', 'test123')
ON CONFLICT (username) DO NOTHING;



---------------------------------------------------------
-- OPTIONAL: SOUNDS TABLE (nếu bạn muốn sync từ Excel)
-- Không bắt buộc! Game vẫn dùng CSV load vào memory.
---------------------------------------------------------
CREATE TABLE public.sounds (
    id SERIAL PRIMARY KEY,
    sound_key VARCHAR(100) UNIQUE NOT NULL,          -- ví dụ "dog", "cat", "piano"
    display_name VARCHAR(100) NOT NULL,              -- "Con chó"
    category VARCHAR(50) CHECK (category IN ('instrument', 'animal', 'vehicle')),
    file_path TEXT NOT NULL                          -- "/sounds/animals/dog.mp3"
);




---------------------------------------------------------
-- MATCHES (lịch sử 1 trận đấu)
---------------------------------------------------------
CREATE TABLE public.matches (
    id SERIAL PRIMARY KEY,

    player1 VARCHAR(100) NOT NULL REFERENCES public.users(username),
    player2 VARCHAR(100) NOT NULL REFERENCES public.users(username),

    score1 INT NOT NULL DEFAULT 0,
    score2 INT NOT NULL DEFAULT 0,

    winner VARCHAR(20) NOT NULL CHECK (winner IN ('player1', 'player2', 'draw')),

    tie_break_used BOOLEAN NOT NULL DEFAULT FALSE,

    total_rounds INT NOT NULL DEFAULT 10,

    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_matches_date ON public.matches(finished_at DESC);



---------------------------------------------------------
-- MATCH_ROUNDS (chi tiết từng câu)
---------------------------------------------------------
CREATE TABLE public.match_rounds (
    id SERIAL PRIMARY KEY,
    match_id INT NOT NULL REFERENCES public.matches(id) ON DELETE CASCADE,

    round_no INT NOT NULL,                           -- 1..10 hoặc 11 nếu tie-break

    sound_key VARCHAR(100) NOT NULL,                 -- giống id trong Excel
    category VARCHAR(50) NOT NULL CHECK (category IN ('instrument', 'animal', 'vehicle')),

    question_text TEXT NOT NULL,
    correct_answer VARCHAR(100) NOT NULL,

    -- Player 1
    p1_answer VARCHAR(100),
    p1_time_ms INT,
    p1_correct BOOLEAN,

    -- Player 2
    p2_answer VARCHAR(100),
    p2_time_ms INT,
    p2_correct BOOLEAN,

    winner_round VARCHAR(20) CHECK (winner_round IN ('player1','player2','draw')),
    is_tiebreak BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_match_rounds_match ON public.match_rounds(match_id);



---------------------------------------------------------
-- LEADERBOARD VIEW (bảng xếp hạng)
---------------------------------------------------------
CREATE VIEW public.leaderboard AS
SELECT
    username,
    wins,
    total_matches,
    ROUND(
        CASE WHEN total_matches = 0 THEN 0
             ELSE (wins * 100.0 / total_matches)
        END, 2
    ) AS win_rate
FROM public.users
ORDER BY win_rate DESC, wins DESC;



---------------------------------------------------------
---------------------------------------------------------
GRANT ALL ON SCHEMA public TO soundduel;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO soundduel;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO soundduel;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO soundduel;
