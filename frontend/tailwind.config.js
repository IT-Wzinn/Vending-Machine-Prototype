/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          primary: '#123D32',
          'primary-hover': '#0B2921',
          'primary-light': '#1E584A',
          bg: '#F7F8F5',
          'bg-card': '#FFFFFF',
          accent: '#D8F36A',
          'accent-hover': '#C6E354',
          'accent-dark': '#B5D440',
          text: '#252A28',
          'text-muted': '#5F6864',
          'text-subtle': '#8A9490',
          border: '#E2E6E3',
        },
      },
      fontFamily: {
        sans: ['Plus Jakarta Sans', 'Inter', 'system-ui', '-apple-system', 'sans-serif'],
      },
      boxShadow: {
        'card': '0 4px 20px -2px rgba(18, 61, 50, 0.06), 0 2px 6px -1px rgba(18, 61, 50, 0.04)',
        'card-hover': '0 12px 28px -4px rgba(18, 61, 50, 0.12), 0 4px 10px -2px rgba(18, 61, 50, 0.06)',
        'floating': '0 20px 35px -5px rgba(18, 61, 50, 0.2), 0 10px 15px -5px rgba(18, 61, 50, 0.1)',
      },
    },
  },
  plugins: [],
}

