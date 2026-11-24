import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        paper: '#F7F8F5',
        sidebar: '#FAFAF8',
        line: '#E5E8E2',
        pine: {
          50: '#F2F6F3',
          100: '#E5EEE7',
          200: '#C9DDD0',
          500: '#3D6A4E',
          600: '#335B42',
          700: '#2D523A',
          800: '#23422E',
          900: '#1B3324',
        },
        sage: {
          50: '#F4F7F4',
          100: '#EAF0EB',
          200: '#D8E4DA',
          500: '#4F8A6B',
          600: '#3D7A5A',
        },
        ink: {
          900: '#1E3326',
          700: '#364A3E',
          500: '#6B776E',
          400: '#8A958D',
        },
        terracotta: {
          50: '#FCF0EC',
          200: '#F3D2C8',
          600: '#C85A42',
          700: '#AE4731',
        },
        ochre: {
          50: '#FDF6E9',
          200: '#F0DCB3',
          600: '#B87D28',
        },
        stonewarm: {
          50: '#F3F4F0',
          100: '#EAECE6',
          500: '#6E7B72',
        },
      },
      fontFamily: {
        sans: [
          '"PingFang SC"',
          '"Hiragino Sans GB"',
          '"Noto Sans SC"',
          '-apple-system',
          'BlinkMacSystemFont',
          '"Segoe UI"',
          'sans-serif',
        ],
        mono: ['"JetBrains Mono"', '"SF Mono"', 'ui-monospace', 'monospace'],
      },
      boxShadow: {
        editorial: '0 4px 20px rgba(24, 46, 33, 0.04)',
        composer: '0 8px 30px rgba(24, 46, 33, 0.06)',
      },
    },
  },
  plugins: [],
} satisfies Config;
