// JARVIS Cloud AI gateway — Cloudflare Worker.
// Secrets: OPENAI_API_KEY, ANTHROPIC_API_KEY, GEMINI_API_KEY.
// Settings: JARVIS_PROVIDER=openai|anthropic|gemini.
// Keep all provider credentials in Worker secrets, never in Android or Git.
const SYSTEM_PROMPT = `Eres JARVIS, un asistente personal inteligente. Hablas siempre en español claro y natural, con tono sereno, preciso y servicial. Responde directamente a lo que te preguntan, explica cuando haga falta y reconoce honestamente cuando no sabes algo. No inventes datos ni afirmes haber ejecutado acciones en el teléfono. Si una petición requiere acceso al dispositivo, explica qué permiso o integración sería necesaria.`;

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
  "Content-Type": "application/json; charset=utf-8"
};

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { headers: cors });
    if (request.method !== "POST") return json({ error: "Método no permitido." }, 405);

    try {
      const url = new URL(request.url);
      const isVoice = url.pathname.endsWith("/voice");
      const body = await request.json();

      if (isVoice) {
        // Voice transcription remains on OpenAI. The selected chat provider is
        // independent, so Claude/Gemini can answer after transcription.
        if (!env.OPENAI_API_KEY) return json({ error: "Configura OPENAI_API_KEY para transcribir la voz." }, 503);
        if (typeof body.audioBase64 !== "string" || body.audioBase64.length > 12_000_000) {
          return json({ error: "Audio ausente o demasiado grande." }, 400);
        }
        const binary = atob(body.audioBase64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        const form = new FormData();
        form.append("file", new File([bytes], "jarvis-question.wav", { type: "audio/wav" }));
        form.append("model", env.OPENAI_TRANSCRIBE_MODEL || "gpt-4o-mini-transcribe");
        form.append("language", "es");
        const response = await fetch("https://api.openai.com/v1/audio/transcriptions", {
          method: "POST",
          headers: { "Authorization": `Bearer ${env.OPENAI_API_KEY}` },
          body: form
        });
        const data = await response.json();
        if (!response.ok) return json({ error: data.error?.message || "No se pudo transcribir el audio." }, 502);
        const transcript = (data.text || "").replace(/^\\s*jarvis[\\s,.:;!?¡¿-]*/i, "").trim();
        if (!transcript) return json({ error: "Escuché «Jarvis», pero no recibí una pregunta. Inténtalo otra vez." }, 422);
        return json({ transcript });
      }

      const messages = Array.isArray(body.messages) ? body.messages.slice(-12) : [];
      if (messages.some(m =>
        !["user", "assistant"].includes(m.role) ||
        typeof m.content !== "string" || m.content.length > 5000
      )) return json({ error: "Formato de conversación no válido." }, 400);
      if (!messages.length) return json({ error: "La conversación está vacía." }, 400);

      const provider = (env.JARVIS_PROVIDER || "openai").toLowerCase();
      const answer = await askProvider(provider, messages, env);
      if (!answer) return json({ error: "La IA no generó texto." }, 502);
      return json({ answer, provider });
    } catch (error) {
      return json({ error: error instanceof Error ? error.message : "Error interno." }, 500);
    }
  }
};

async function askProvider(provider, messages, env) {
  if (provider === "openai") {
    if (!env.OPENAI_API_KEY) throw new Error("Falta configurar OPENAI_API_KEY en los secretos del Worker.");
    const response = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: { "Authorization": `Bearer ${env.OPENAI_API_KEY}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: env.OPENAI_MODEL || "gpt-5.5",
        instructions: SYSTEM_PROMPT,
        input: messages.map(m => ({ role: m.role, content: m.content })),
        store: false,
        max_output_tokens: 900
      })
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error?.message || "OpenAI rechazó la solicitud.");
    return data.output_text || (data.output || []).flatMap(x => x.content || [])
      .filter(x => x.type === "output_text").map(x => x.text).join("\\n");
  }

  if (provider === "anthropic") {
    if (!env.ANTHROPIC_API_KEY) throw new Error("Falta configurar ANTHROPIC_API_KEY en los secretos del Worker.");
    const response = await fetch("https://api.anthropic.com/v1/messages", {
      method: "POST",
      headers: {
        "x-api-key": env.ANTHROPIC_API_KEY,
        "anthropic-version": "2023-06-01",
        "content-type": "application/json"
      },
      body: JSON.stringify({
        model: env.ANTHROPIC_MODEL || "claude-sonnet-4-5-20250929",
        system: SYSTEM_PROMPT,
        messages: messages.map(m => ({ role: m.role, content: m.content })),
        max_tokens: 900
      })
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error?.message || "Anthropic rechazó la solicitud.");
    return (data.content || []).filter(x => x.type === "text").map(x => x.text).join("\\n");
  }

  if (provider === "gemini") {
    if (!env.GEMINI_API_KEY) throw new Error("Falta configurar GEMINI_API_KEY en los secretos del Worker.");
    const model = env.GEMINI_MODEL || "gemini-2.5-flash";
    const contents = messages.map(m => ({
      role: m.role === "assistant" ? "model" : "user",
      parts: [{ text: m.content }]
    }));
    const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${env.GEMINI_API_KEY}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
        contents,
        generationConfig: { maxOutputTokens: 900 }
      })
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error?.message || "Gemini rechazó la solicitud.");
    return (data.candidates?.[0]?.content?.parts || []).map(x => x.text || "").join("\\n");
  }

  throw new Error("Proveedor no válido. Usa openai, anthropic o gemini.");
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: cors });
}
