import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import {resolve} from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import {ElementPlusResolver} from 'unplugin-vue-components/resolvers'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      imports: ['vue', 'vue-router', 'pinia'],
      dts: true
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: true
    })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    open: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        ws: true
      },
      '/auth': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        ws: true
      }
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        api: 'modern-compiler',
        additionalData: `@use "@/styles/variables.scss" as *;`
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined
          }
          if (id.includes('@element-plus/icons-vue')) {
            return 'element-plus-icons'
          }
          if (id.includes('element-plus')) {
            if (
              id.includes('/table') ||
              id.includes('/pagination') ||
              id.includes('/scrollbar')
            ) {
              return 'element-plus-data'
            }
            if (
              id.includes('/form') ||
              id.includes('/input') ||
              id.includes('/select') ||
              id.includes('/radio') ||
              id.includes('/checkbox') ||
              id.includes('/input-number') ||
              id.includes('/cascader') ||
              id.includes('/date-picker') ||
              id.includes('/time-picker') ||
              id.includes('/tree-select')
            ) {
              return 'element-plus-form'
            }
            if (
              id.includes('/dialog') ||
              id.includes('/drawer') ||
              id.includes('/overlay') ||
              id.includes('/image-viewer') ||
              id.includes('/message-box') ||
              id.includes('/notification') ||
              id.includes('/loading')
            ) {
              return 'element-plus-feedback'
            }
            return 'element-plus-base'
          }
          if (id.includes('vue-router') || id.includes('pinia') || id.includes('pinia-plugin-persistedstate')) {
            return 'app-core'
          }
          if (id.includes('/vue/') || id.includes('@vue')) {
            return 'vue-core'
          }
          return 'vendor'
        }
      }
    }
  }
})
