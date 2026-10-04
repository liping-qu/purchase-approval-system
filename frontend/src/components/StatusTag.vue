<template><el-tag :type="tagType" effect="light" size="small">{{ label }}</el-tag></template>
<script setup>
import { computed } from 'vue'; const props = defineProps({ status: { type: String, default: '' } }); const map = { DRAFT: ['草稿', 'info'], PENDING: ['待审批', 'warning'], APPROVED: ['已通过', 'success'], REJECTED: ['已驳回', 'danger'], PURCHASING: ['采购中', 'primary'], DELIVERED: ['待入库', 'warning'], COMPLETED: ['已完成', 'success'], ACTIVE: ['正常', 'success'], INACTIVE: ['停用', 'info'] }; const label = computed(() => map[props.status]?.[0] || props.status || '未知'); const tagType = computed(() => map[props.status]?.[1] || 'info')
</script>
