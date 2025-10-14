-- Reset lại schema public (xóa toàn bộ bảng cũ)
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;

-- Tạo bảng users với cột username, password dạng text
CREATE TABLE public.users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    password TEXT NOT NULL
);

-- Thêm 3 tài khoản test
INSERT INTO public.users (username, password)
VALUES
    ('test1', 'test123'),
    ('test2', 'test123'),
    ('test3', 'test123')
ON CONFLICT (username) DO NOTHING;

-- Kiểm tra lại
SELECT * FROM public.users;

GRANT ALL ON SCHEMA public TO soundduel;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO soundduel;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO soundduel;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO soundduel;

