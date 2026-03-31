export const useApi = () => {
    const config = useRuntimeConfig()
    const apiBase = config.public.apiBase
  
    const generateSVG = async (file: File, offset = 10, smooth = true) => {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('offset', offset.toString())
      formData.append('smooth', smooth ? 'true' : 'false')
  
      const res = await fetch(`${apiBase}/generate`, {
        method: 'POST',
        body: formData
      })
  
      if (!res.ok) throw new Error('生成失敗')
  
      const blob = await res.blob()
      return URL.createObjectURL(blob)
    }
  
    return { generateSVG }
  }
  