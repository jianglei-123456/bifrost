<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Download, EditPen, PictureFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import BookCover from '@/components/BookCover.vue'
import BookEditDialog from '@/components/BookEditDialog.vue'
import { deleteBookCover, fetchBook, uploadBookCover } from '@/api/books'
import type { Book } from '@/api/types'
import { useBookStore } from '@/stores/book'
import { formatBytes, formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const bookStore = useBookStore()
const id = Number(route.params.id)

const book = ref<Book | null>(null)
const loading = ref(true)

const edit = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)
const fileInput = ref<HTMLInputElement>()

const ALLOWED_TYPES = ['image/jpeg', 'image/jpg', 'image/png', 'image/gif']
const MAX_SIZE = 5 * 1024 * 1024

async function load() {
  loading.value = true
  try {
    book.value = await fetchBook(id)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
  void bookStore.loadRoots().catch(() => {})
})

function rootName(): string {
  if (!book.value) return ''
  return bookStore.roots.find((r) => r.id === book.value!.libraryRootId)?.name ?? ''
}

function downloadFile() {
  window.open(`${window.location.origin}/opds/v1.2/catalog/${id}/file`, '_blank')
}

function openCover() {
  if (!book.value?.coverUrl) return
  window.open(`${window.location.origin}${book.value.coverUrl}`, '_blank')
}

function pickCover() {
  fileInput.value?.click()
}

async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  const type = (file.type || '').toLowerCase()
  if (!ALLOWED_TYPES.includes(type)) {
    ElMessage.error('仅支持 JPG / PNG / GIF 图片')
    return
  }
  if (file.size > MAX_SIZE) {
    ElMessage.error('图片超过 5MB')
    return
  }
  uploading.value = true
  uploadProgress.value = 0
  try {
    await uploadBookCover(id, file, (ev) => {
      if (ev.total) uploadProgress.value = Math.round((ev.loaded / ev.total) * 100)
    })
    ElMessage.success('封面已更新（UPLOADED，优先于内嵌封面）')
    await load()
  } catch {
    // 拦截器已弹错
  } finally {
    uploading.value = false
    uploadProgress.value = 0
  }
}

async function removeCover() {
  if (!book.value?.coverUrl) return
  await ElMessageBox.confirm('删除封面后，若文件内嵌封面，下次扫描会自动重新抽取。确定删除？', '删除封面', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await deleteBookCover(id)
  ElMessage.success('封面已删除')
  await load()
}

async function removeBook() {
  if (!book.value) return
  await ElMessageBox.confirm(
    `删除图书「${book.value.title}」？仅移除数据库记录，不会删除文件。`,
    '删除图书',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
  )
  await bookStore.deleteBook(id)
  ElMessage.success('图书已删除')
  router.replace({ name: 'books' })
}

function onSaved(updated: Book) {
  book.value = updated
}
</script>

<template>
  <div class="page">
    <div v-loading="loading">
      <template v-if="book">
        <div class="page-header">
          <div class="head-main">
            <el-button link :icon="ArrowLeft" @click="router.back()">返回</el-button>
            <h2 class="page-title">{{ book.title }}</h2>
          </div>
          <div class="page-actions">
            <el-button :icon="Download" @click="downloadFile">下载文件</el-button>
            <el-button type="primary" :icon="EditPen" @click="edit = true">
              编辑元数据
            </el-button>
            <el-button type="danger" plain @click="removeBook">删除图书</el-button>
          </div>
        </div>

        <section class="card book-hero">
          <div class="cover-col">
            <BookCover
              :cover-url="book.coverUrl"
              :title="book.title"
              :width="220"
              :height="300"
              class="hero-cover"
            />
            <div class="cover-actions">
              <el-button size="small" :icon="PictureFilled" :loading="uploading" @click="pickCover">
                {{ uploading ? `上传中 ${uploadProgress}%` : '替换封面' }}
              </el-button>
              <el-button
                v-if="book.coverUrl"
                size="small"
                plain
                type="danger"
                @click="removeCover"
              >
                删除封面
              </el-button>
            </div>
            <input
              ref="fileInput"
              type="file"
              accept="image/jpeg,image/png,image/gif"
              hidden
              @change="onFileChange"
            />
            <el-button v-if="book.coverUrl" link type="primary" size="small" @click="openCover">
              查看原图 ↗
            </el-button>
          </div>

          <div class="hero-meta">
            <div class="hero-tags">
              <span v-if="book.authors" class="tag">{{ book.authors }}</span>
              <span v-if="book.pubDate" class="tag data-mono">{{ book.pubDate }}</span>
              <span class="tag data-mono">{{ book.format }}</span>
              <span class="tag data-mono">.{{ book.extension }}</span>
              <span class="tag tag-dim">封面：{{ book.coverSource || '无' }}</span>
              <span class="avail" :class="book.isAvailable ? 'on' : 'off'">
                {{ book.isAvailable ? '可用' : '不可用' }}
              </span>
            </div>

            <dl class="kv">
              <div v-if="book.authors" class="kv-row">
                <dt>作者</dt>
                <dd>{{ book.authors }}</dd>
              </div>
              <div v-if="book.language" class="kv-row">
                <dt>语言</dt>
                <dd class="data-mono">{{ book.language }}</dd>
              </div>
              <div v-if="book.publisher" class="kv-row">
                <dt>出版社</dt>
                <dd>{{ book.publisher }}</dd>
              </div>
              <div v-if="book.series || book.seriesIndex != null" class="kv-row">
                <dt>系列</dt>
                <dd>
                  {{ book.series || '—' }}
                  <span v-if="book.seriesIndex != null" class="data-mono series-idx">
                    #{{ book.seriesIndex }}
                  </span>
                </dd>
              </div>
              <div v-if="book.identifier" class="kv-row">
                <dt>标识符</dt>
                <dd class="data-mono id">{{ book.identifier }}</dd>
              </div>
              <div v-if="book.subject" class="kv-row">
                <dt>主题</dt>
                <dd>{{ book.subject }}</dd>
              </div>
              <div v-if="book.rights" class="kv-row">
                <dt>版权</dt>
                <dd>{{ book.rights }}</dd>
              </div>
            </dl>

            <el-button
              v-if="!book.authors && !book.language && !book.publisher"
              link
              type="primary"
              @click="edit = true"
            >
              补充元数据 →
            </el-button>
          </div>
        </section>

        <section v-if="book.description" class="card desc-card">
          <h3 class="block-title">简介</h3>
          <p class="desc">{{ book.description }}</p>
        </section>

        <section class="card file-card">
          <h3 class="block-title">文件信息</h3>
          <dl class="kv file-kv">
            <div class="kv-row">
              <dt>格式</dt>
              <dd class="data-mono">{{ book.format }}</dd>
            </div>
            <div class="kv-row">
              <dt>扩展名</dt>
              <dd class="data-mono">.{{ book.extension }}</dd>
            </div>
            <div class="kv-row">
              <dt>大小</dt>
              <dd class="data-mono">{{ formatBytes(book.fileSize) }}</dd>
            </div>
            <div class="kv-row">
              <dt>修改时间</dt>
              <dd class="data-mono">{{ formatDateTime(book.fileLastModified) }}</dd>
            </div>
            <div class="kv-row">
              <dt>入库时间</dt>
              <dd class="data-mono">{{ formatDateTime(book.createdAt) }}</dd>
            </div>
            <div class="kv-row">
              <dt>所属图书目录</dt>
              <dd>
                <router-link
                  v-if="rootName()"
                  :to="{ name: 'book-roots' }"
                  class="root-link"
                >
                  {{ rootName() }}
                </router-link>
                <span v-else class="data-mono dim">#{{ book.libraryRootId }}</span>
              </dd>
            </div>
          </dl>
        </section>

        <BookEditDialog v-model="edit" :book="book" @saved="onSaved" />
      </template>
    </div>
  </div>
</template>

<style scoped>
.head-main {
  display: flex;
  align-items: center;
  gap: 6px;
}

.book-hero {
  display: flex;
  gap: 26px;
  padding: 24px;
  margin-bottom: 16px;
}

.cover-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.hero-cover {
  border-radius: var(--radius);
}

.cover-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}

.hero-meta {
  flex: 1;
  min-width: 0;
}

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.tag {
  font-size: 12px;
  color: var(--text-dim);
  background: var(--panel-raised);
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 2px 10px;
}

.tag-dim {
  color: #5d6a82;
}

.avail {
  font-size: 12px;
  border-radius: 999px;
  padding: 0 10px;
  line-height: 22px;
  display: inline-block;
}

.avail.on {
  color: var(--success);
  background: rgba(61, 220, 151, 0.12);
  border: 1px solid rgba(61, 220, 151, 0.3);
}

.avail.off {
  color: var(--text-dim);
  background: var(--panel-raised);
  border: 1px solid var(--line);
}

.kv {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  max-width: 760px;
}

.kv-row {
  display: flex;
  gap: 14px;
  align-items: baseline;
}

.kv-row dt {
  width: 64px;
  flex-shrink: 0;
  font-size: 12px;
  color: var(--text-dim);
}

.kv-row dd {
  margin: 0;
  min-width: 0;
  word-break: break-word;
}

.series-idx {
  color: var(--info);
  margin-left: 4px;
}

.id {
  font-size: 12.5px;
  color: var(--text-dim);
}

.desc-card,
.file-card {
  padding: 18px 22px;
  margin-bottom: 16px;
}

.block-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 12px;
}

.desc {
  margin: 0;
  white-space: pre-wrap;
  color: var(--text-dim);
  font-size: 13.5px;
}

.file-kv {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 6px 24px;
}

.root-link {
  color: var(--info);
  text-decoration: none;
}

.root-link:hover {
  text-decoration: underline;
}

.dim {
  color: var(--text-dim);
}

@media (max-width: 720px) {
  .book-hero {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .hero-tags {
    justify-content: center;
  }
}
</style>
