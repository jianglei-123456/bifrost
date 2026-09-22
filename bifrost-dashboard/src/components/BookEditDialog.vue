<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

import type { Book } from '@/api/types'
import { useBookStore } from '@/stores/book'

const props = defineProps<{
  modelValue: boolean
  book: Book | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: [book: Book]
}>()

const bookStore = useBookStore()
const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

/** authors 存 " & "、subject 存 "; "（后端 join 分隔符）；编辑时按此拆回 tag */
function splitValues(raw: string | null, separator: string): string[] {
  if (!raw) return []
  return raw
    .split(separator)
    .map((s) => s.trim())
    .filter(Boolean)
}

interface FormModel {
  title: string
  authors: string[]
  language: string
  publisher: string
  pubDate: number | null
  description: string
  subject: string[]
  identifier: string
  series: string
  seriesIndex: number | null
  rights: string
}

const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive<FormModel>({
  title: '',
  authors: [],
  language: '',
  publisher: '',
  pubDate: null,
  description: '',
  subject: [],
  identifier: '',
  series: '',
  seriesIndex: null,
  rights: '',
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入书名', trigger: 'blur' }],
}

watch(
  () => props.book,
  (b) => {
    if (!b) return
    form.title = b.title
    form.authors = splitValues(b.authors, '&')
    form.language = b.language ?? ''
    form.publisher = b.publisher ?? ''
    form.pubDate = b.pubDate ?? null
    form.description = b.description ?? ''
    form.subject = splitValues(b.subject, ';')
    form.identifier = b.identifier ?? ''
    form.series = b.series ?? ''
    form.seriesIndex = b.seriesIndex ?? null
    form.rights = b.rights ?? ''
  },
  { immediate: true },
)

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !props.book) return

  // 只发白名单字段；null=跳过不改、''/[]=清空（后端语义，见 BookController.update）
  const patch: Record<string, unknown> = {
    title: form.title,
    authors: form.authors,
    language: form.language,
    publisher: form.publisher,
    description: form.description,
    subject: form.subject,
    identifier: form.identifier,
    series: form.series,
    rights: form.rights,
  }
  // pubDate/seriesIndex 后端无"清空"语义（null=跳过），有值才提交
  if (form.pubDate != null) patch.pubDate = form.pubDate
  if (form.seriesIndex != null) patch.seriesIndex = form.seriesIndex

  saving.value = true
  try {
    const updated = await bookStore.updateBook(props.book.id, patch)
    ElMessage.success('图书元数据已保存')
    emit('saved', updated)
    visible.value = false
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="编辑元数据"
    width="640px"
    destroy-on-close
    @closed="formRef?.resetFields()"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="edit-form">
      <div class="form-grid">
        <el-form-item label="书名" prop="title" class="span-2">
          <el-input v-model="form.title" placeholder="必填" />
        </el-form-item>

        <el-form-item label="作者（回车添加一个作者）" class="span-2">
          <el-select
            v-model="form.authors"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入后回车添加作者"
          />
        </el-form-item>

        <el-form-item label="语言（BCP-47）" prop="language">
          <el-input v-model="form.language" placeholder="如 en / zh-CN" />
        </el-form-item>

        <el-form-item label="出版社" prop="publisher">
          <el-input v-model="form.publisher" placeholder="可选" />
        </el-form-item>

        <el-form-item label="出版年" prop="pubDate">
          <el-input-number
            v-model="form.pubDate"
            :min="1"
            :max="9999"
            :step="1"
            controls-position="right"
            placeholder="如 2024"
            class="fill"
          />
        </el-form-item>

        <el-form-item label="系列序号" prop="seriesIndex">
          <el-input-number
            v-model="form.seriesIndex"
            :min="0"
            :max="9999"
            :precision="1"
            :step="0.5"
            controls-position="right"
            placeholder="如 1.5"
            class="fill"
          />
        </el-form-item>

        <el-form-item label="系列" prop="series" class="span-2">
          <el-input v-model="form.series" placeholder="可选" />
        </el-form-item>

        <el-form-item label="主题（回车添加一个主题）" class="span-2">
          <el-select
            v-model="form.subject"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入后回车添加主题"
          />
        </el-form-item>

        <el-form-item label="标识符" prop="identifier">
          <el-input v-model="form.identifier" placeholder="如 urn:uuid:..." />
        </el-form-item>

        <el-form-item label="版权" prop="rights">
          <el-input v-model="form.rights" placeholder="可选" />
        </el-form-item>

        <el-form-item label="简介" prop="description" class="span-2">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="可选"
          />
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.edit-form :deep(.el-select) {
  width: 100%;
}

.fill {
  width: 100%;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.span-2 {
  grid-column: 1 / -1;
}
</style>
