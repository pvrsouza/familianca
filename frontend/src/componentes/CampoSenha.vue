<script setup lang="ts">
import { ref } from 'vue'
import { Eye, EyeOff } from 'lucide-vue-next'

defineProps<{ modelValue: string; id: string; autocomplete: string; invalido?: boolean; minimo?: number }>()
const emit = defineEmits<{ 'update:modelValue': [string] }>()
const visivel = ref(false)
</script>

<template>
  <div class="senha">
    <input
      :id="id"
      class="entrada"
      :type="visivel ? 'text' : 'password'"
      :autocomplete="autocomplete"
      :value="modelValue"
      :minlength="minimo"
      maxlength="64"
      required
      :aria-invalid="invalido || undefined"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
    <button type="button" class="botao botao-sutil botao-icone botao-pequeno ver"
            :aria-label="visivel ? 'Esconder senha' : 'Mostrar senha'" :aria-pressed="visivel"
            @click="visivel = !visivel">
      <EyeOff v-if="visivel" aria-hidden="true" />
      <Eye v-else aria-hidden="true" />
    </button>
  </div>
</template>

<style scoped>
.senha {
  position: relative;
}

.senha .entrada {
  padding-right: 3rem;
}

.ver {
  position: absolute;
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
}

.ver:active {
  transform: translateY(-50%);
}
</style>
