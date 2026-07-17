import {createApp} from 'vue'
import App from './App.vue'
import router from './router'
import pinia from './store'
import {registerPermissionDirective} from '@/directives/permission'
import 'element-plus/dist/index.css'
import './styles/index.scss'

const app = createApp(App)

app.use(pinia)
app.use(router)
registerPermissionDirective(app)

app.mount('#app')

