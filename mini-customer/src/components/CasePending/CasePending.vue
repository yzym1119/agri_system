<script setup lang="ts">
import type { PendingCase } from '@agri/mobile-contracts/customer-case-submission';
defineProps<{ pending: PendingCase | null; blocked: boolean; busy: boolean; canOpen?: boolean }>();
defineEmits(['check', 'retry', 'open']);
</script>
<template>
  <view class="notice" role="status">
    <text class="status">{{ blocked ? '原发送记录需要核对' : '发送结果还没确认' }}</text>
    <text class="paragraph">请保留这次内容，先核对结果，不要另发一遍。</text>
    <text v-if="pending" class="paragraph case-original">{{ pending.operation === 'CREATE' ? pending.command.question : pending.command.body }}</text>
    <button class="button" hover-class="button-pressed" :disabled="busy" @tap="$emit('check')">核对原发送结果</button>
    <button v-if="pending" class="button button-secondary" hover-class="button-pressed" :disabled="busy || blocked" @tap="$emit('retry')">继续发送原内容</button>
    <button v-if="pending && canOpen" class="button button-secondary" hover-class="button-pressed" :disabled="busy" @tap="$emit('open')">查看原内容</button>
  </view>
</template>
