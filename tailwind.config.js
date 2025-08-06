module.exports = {
  content: [
    './src/main/**/*.cljs',
  ],
  theme: {
    extend: {
      colors: {
      },
    },
  },
  variants: {
    extend: {},
  },
  plugins: [
     require('@tailwindcss/forms')
  ],
  safelist: [
    'hidden',
    "bg-info',
    "divider-horizontal",
  ],
}

