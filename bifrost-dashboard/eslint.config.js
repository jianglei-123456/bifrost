import pluginVue from 'eslint-plugin-vue'
import vueTsEslintConfig from '@vue/eslint-config-typescript'
import configPrettier from 'eslint-config-prettier'

export default [
  {
    name: 'app/files-to-lint',
    files: ['**/*.{ts,mts,tsx,vue}'],
  },
  {
    name: 'app/files-to-ignore',
    ignores: ['**/dist/**', '**/coverage/**', 'src/auto-imports.d.ts', 'src/components.d.ts'],
  },
  ...pluginVue.configs['flat/essential'],
  ...vueTsEslintConfig(),
  configPrettier,
]
