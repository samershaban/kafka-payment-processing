import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5175,
    strictPort: true, // fail instead of silently picking another port if 5175 is taken
    proxy: {
      // The browser only talks to localhost:5175; Vite forwards API calls to the Order Service.
      // That's why the Order Service doesn't need any CORS config.
      '/orders': 'http://localhost:8081',
      '/users': 'http://localhost:8081',
    },
  },
})
