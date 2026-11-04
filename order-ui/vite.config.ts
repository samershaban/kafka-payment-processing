import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5175,
    strictPort: true, // fail instead of silently picking another port if 5175 is taken
    proxy: {
      // The browser only talks to localhost:5175. API calls use the /api prefix, which Vite strips before
      // forwarding to the Order Service: /api/users -> http://localhost:8081/users. That's why the Order Service
      // needs no CORS config, and why page URLs like /users (the Users page) don't clash with the API.
      '/api': {
        target: 'http://localhost:8081',
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
